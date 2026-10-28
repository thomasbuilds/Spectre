package dev.thomasbuilds.spectre.scanner.cellular

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.location.LocationManager
import android.os.SystemClock
import android.telephony.CellIdentity
import android.telephony.CellIdentityGsm
import android.telephony.CellIdentityLte
import android.telephony.CellIdentityNr
import android.telephony.CellIdentityWcdma
import android.telephony.CellInfo
import android.telephony.CellInfoGsm
import android.telephony.CellInfoLte
import android.telephony.CellInfoNr
import android.telephony.CellInfoWcdma
import android.telephony.CellSignalStrengthGsm
import android.telephony.CellSignalStrengthLte
import android.telephony.CellSignalStrengthNr
import android.telephony.CellSignalStrengthWcdma
import android.telephony.SignalStrength
import android.telephony.SubscriptionManager
import android.telephony.TelephonyCallback
import android.telephony.TelephonyDisplayInfo
import android.telephony.TelephonyManager
import android.util.Log
import dev.thomasbuilds.spectre.analysis.Distance
import dev.thomasbuilds.spectre.hasPermission
import dev.thomasbuilds.spectre.model.CellNetworkType
import dev.thomasbuilds.spectre.model.CellSignal
import dev.thomasbuilds.spectre.model.CellularSourceState
import dev.thomasbuilds.spectre.model.DetailEntry
import dev.thomasbuilds.spectre.model.DistanceConfidence
import dev.thomasbuilds.spectre.model.ScannerStatus
import dev.thomasbuilds.spectre.scanner.ReadinessTracker
import dev.thomasbuilds.spectre.scanner.daemonExecutor
import dev.thomasbuilds.spectre.scanner.repeatEvery
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.log10
import kotlin.math.roundToInt

class CellularScanner(
  private val context: Context
) {
  private val telephony: TelephonyManager? =
    context.getSystemService(Context.TELEPHONY_SERVICE) as? TelephonyManager

  private val subscriptionManager: SubscriptionManager? =
    context.getSystemService(Context.TELEPHONY_SUBSCRIPTION_SERVICE) as? SubscriptionManager

  private val locationManager: LocationManager? =
    context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager

  @Volatile private var lastDisplayInfo: TelephonyDisplayInfo? = null

  private val _state = MutableStateFlow(CellularSourceState())
  val state: StateFlow<CellularSourceState> = _state.asStateFlow()

  private val cellCache = ConcurrentHashMap<String, CellSignal>()
  private val cellSeenAt = ConcurrentHashMap<String, Long>()

  // Latest per-sub SignalStrength; the serving cell's strength source when CellInfo omits it
  // (Tensor intermittently reports the registered LTE cell with rsrp=UNAVAILABLE and a
  // sentinel rssi while SignalStrength carries real values).
  private val latestSignalStrength = ConcurrentHashMap<Int, SignalStrength>()

  private val readiness = ReadinessTracker(CELL_WARMUP_MS)

  private var heartbeatJob: Job? = null

  private val callbackExecutor = daemonExecutor("spectre-cell")

  private val connectionLabel: String?
    get() {
      val info = lastDisplayInfo ?: return null
      val base = describeNetworkType(info)
      if (info.networkType == TelephonyManager.NETWORK_TYPE_UNKNOWN) return base
      return if (!isMobileDataEnabled()) "$base · data off" else base
    }

  private data class CachedRssi(
    val rssi: Int,
    val timestampMs: Long
  )

  private val lteRssiCache = ConcurrentHashMap<Int, CachedRssi>()

  private class SubMonitor(
    val subId: Int,
    val tm: TelephonyManager,
    val callback: TelephonyCallback,
    val withDisplayInfo: Boolean
  )

  private val subMonitors = ConcurrentHashMap<Int, SubMonitor>()

  @Volatile private var subsChangeListener: SubscriptionManager.OnSubscriptionsChangedListener? = null

  @Volatile private var stopped = false

  private fun makeCellCallback(
    subId: Int,
    withDisplayInfo: Boolean
  ): TelephonyCallback =
    if (withDisplayInfo) {
      object :
        TelephonyCallback(),
        TelephonyCallback.CellInfoListener,
        TelephonyCallback.SignalStrengthsListener,
        TelephonyCallback.DisplayInfoListener {
        override fun onCellInfoChanged(cellInfo: List<CellInfo>) {
          ingestCellList(cellInfo, subId)
          publishNow()
        }

        override fun onSignalStrengthsChanged(signalStrength: SignalStrength) {
          latestSignalStrength[subId] = signalStrength
        }

        override fun onDisplayInfoChanged(info: TelephonyDisplayInfo) {
          lastDisplayInfo = info
          publishNow()
        }
      }
    } else {
      object :
        TelephonyCallback(),
        TelephonyCallback.CellInfoListener,
        TelephonyCallback.SignalStrengthsListener {
        override fun onCellInfoChanged(cellInfo: List<CellInfo>) {
          ingestCellList(cellInfo, subId)
          publishNow()
        }

        override fun onSignalStrengthsChanged(signalStrength: SignalStrength) {
          latestSignalStrength[subId] = signalStrength
        }
      }
    }

  @SuppressLint("MissingPermission")
  private fun isMobileDataEnabled(): Boolean {
    val tm = telephony ?: return true
    return runCatching { tm.isDataEnabled }.getOrDefault(true)
  }

  fun hasPermission(): Boolean =
    context.hasPermission(Manifest.permission.READ_PHONE_STATE) &&
      context.hasPermission(Manifest.permission.ACCESS_FINE_LOCATION)

  fun status(): ScannerStatus {
    if (!hasPermission()) return ScannerStatus.NO_PERMISSION
    if (locationManager?.isLocationEnabled != true) return ScannerStatus.LOCATION_OFF
    if (!hasAnyActiveSim()) return ScannerStatus.NO_SIM
    return ScannerStatus.OK
  }

  @SuppressLint("MissingPermission")
  private fun hasAnyActiveSim(): Boolean {
    val subs = runCatching { subscriptionManager?.activeSubscriptionInfoList }.getOrNull()
    if (subs != null) return subs.isNotEmpty()
    val sim = runCatching { telephony?.simState }.getOrNull()
    return sim != TelephonyManager.SIM_STATE_ABSENT
  }

  fun start(scope: CoroutineScope) {
    if (hasPermission()) {
      syncSubscriptions()
      registerSubscriptionChangeListener()
    }
    if (heartbeatJob?.isActive != true) {
      heartbeatJob =
        scope.repeatEvery(CELL_HEARTBEAT_MS) {
          if (status() == ScannerStatus.OK) {
            // start() runs once; ticking recovers late permission grants and data-SIM changes.
            syncSubscriptions()
            registerSubscriptionChangeListener()
            requestCellInfoRefresh()
          }
          publishNow()
        }
    }
    if (!initialKickFired) {
      initialKickFired = true
      requestCellInfoRefresh()
    }
    publishNow()
  }

  @Synchronized
  fun stop() {
    stopped = true
    subsChangeListener?.let { l -> runCatching { subscriptionManager?.removeOnSubscriptionsChangedListener(l) } }
    subsChangeListener = null
    subMonitors.values.forEach { mon ->
      runCatching { mon.tm.unregisterTelephonyCallback(mon.callback) }
    }
    subMonitors.clear()
  }

  @Synchronized
  @SuppressLint("MissingPermission")
  private fun registerSubscriptionChangeListener() {
    if (stopped || subsChangeListener != null) return
    val sm = subscriptionManager ?: return
    val listener =
      object : SubscriptionManager.OnSubscriptionsChangedListener() {
        override fun onSubscriptionsChanged() {
          syncSubscriptions()
          publishNow()
        }
      }
    runCatching { sm.addOnSubscriptionsChangedListener(callbackExecutor, listener) }
      .onSuccess { subsChangeListener = listener }
  }

  @Synchronized
  @SuppressLint("MissingPermission")
  private fun syncSubscriptions() {
    if (stopped || !hasPermission()) return
    val targetSubIds = activeSubIds().toSet()
    val dataSubId = SubscriptionManager.getDefaultDataSubscriptionId()

    subMonitors.values.toList().forEach { mon ->
      val active = mon.subId in targetSubIds
      // A stale DisplayInfo role (data SIM moved) also drops the monitor for re-registration below.
      if (active && mon.withDisplayInfo == (mon.subId == dataSubId)) return@forEach
      subMonitors.remove(mon.subId)
      runCatching { mon.tm.unregisterTelephonyCallback(mon.callback) }
      if (mon.withDisplayInfo) lastDisplayInfo = null
      if (!active) purgeSub(mon.subId)
    }

    targetSubIds.forEach { subId ->
      if (subMonitors.containsKey(subId)) return@forEach
      val tm = telephony?.createForSubscriptionId(subId) ?: return@forEach
      val withDisplayInfo = subId == dataSubId
      val callback = makeCellCallback(subId, withDisplayInfo)
      runCatching { tm.registerTelephonyCallback(callbackExecutor, callback) }
        .onSuccess { subMonitors[subId] = SubMonitor(subId, tm, callback, withDisplayInfo) }
    }
  }

  @SuppressLint("MissingPermission")
  private fun activeSubIds(): List<Int> {
    val subs = runCatching { subscriptionManager?.activeSubscriptionInfoList }.getOrNull()
    if (subs != null) return subs.map { it.subscriptionId }
    val default = SubscriptionManager.getDefaultSubscriptionId()
    return if (default != SubscriptionManager.INVALID_SUBSCRIPTION_ID) listOf(default) else emptyList()
  }

  private fun purgeSub(subId: Int) {
    latestSignalStrength.remove(subId)
    val prefix = "$subId:"
    cellCache.keys.filter { it.startsWith(prefix) }.forEach {
      cellCache.remove(it)
      cellSeenAt.remove(it)
    }
  }

  private fun describeNetworkType(info: TelephonyDisplayInfo): String =
    when (info.overrideNetworkType) {
      TelephonyDisplayInfo.OVERRIDE_NETWORK_TYPE_NR_ADVANCED -> {
        "5G+ NSA (LTE anchor)"
      }

      TelephonyDisplayInfo.OVERRIDE_NETWORK_TYPE_NR_NSA -> {
        "5G NSA (LTE anchor)"
      }

      TelephonyDisplayInfo.OVERRIDE_NETWORK_TYPE_LTE_ADVANCED_PRO -> {
        "4G LTE-A Pro"
      }

      TelephonyDisplayInfo.OVERRIDE_NETWORK_TYPE_LTE_CA -> {
        "4G LTE-CA"
      }

      else -> {
        when (info.networkType) {
          TelephonyManager.NETWORK_TYPE_NR -> "5G SA"

          TelephonyManager.NETWORK_TYPE_LTE -> "4G LTE"

          TelephonyManager.NETWORK_TYPE_UMTS,
          TelephonyManager.NETWORK_TYPE_HSDPA,
          TelephonyManager.NETWORK_TYPE_HSUPA,
          TelephonyManager.NETWORK_TYPE_HSPA,
          TelephonyManager.NETWORK_TYPE_HSPAP
          -> "3G UMTS"

          TelephonyManager.NETWORK_TYPE_EDGE,
          TelephonyManager.NETWORK_TYPE_GPRS,
          TelephonyManager.NETWORK_TYPE_GSM
          -> "2G GSM"

          TelephonyManager.NETWORK_TYPE_UNKNOWN -> "Not connected"

          else -> "Unknown"
        }
      }
    }

  @Volatile private var initialKickFired = false

  @SuppressLint("MissingPermission")
  private fun requestCellInfoRefresh() {
    subMonitors.values.forEach { mon ->
      runCatching {
        mon.tm.requestCellInfoUpdate(
          callbackExecutor,
          object : TelephonyManager.CellInfoCallback() {
            override fun onCellInfo(activeCellInfo: MutableList<CellInfo>) {
              ingestCellList(activeCellInfo, mon.subId)
              publishNow()
            }

            override fun onError(
              errorCode: Int,
              detail: Throwable?
            ) {
              Log.w(TAG, "cell-info refresh failed sub=${mon.subId} code=$errorCode")
            }
          }
        )
      }
    }
  }

  private fun ingestCellList(
    cells: List<CellInfo>,
    subId: Int
  ) {
    // identifier is prefixed with subId so the two SIMs' cells never collide, but channelKey
    // is intentionally left un-prefixed so the exposure power-sum still dedups a channel that
    // both SIMs observe.
    val now = SystemClock.elapsedRealtime()
    val fresh =
      cells
        .mapNotNull { info ->
          // The registry replays its last-known list to fresh listeners; an unregistered
          // measurement the expiry sweep would already have aged out must not re-enter as
          // current. Registered cells are exempt (the live connection corroborates them),
          // as are the zero timestamps some RILs report, which would read as infinitely old.
          if (!info.isRegistered && info.timestampMillis > 0 && now - info.timestampMillis > STALE_TTL_MS) {
            return@mapNotNull null
          }
          runCatching {
            when (info) {
              is CellInfoNr -> parseNr(info)
              is CellInfoLte -> parseLte(info, subId)
              is CellInfoWcdma -> parseWcdma(info)
              is CellInfoGsm -> parseGsm(info)
              else -> null
            }
          }.getOrNull()
        }.map { it.copy(identifier = "$subId:${it.identifier}") }
    // Timestamp first, so the expiry sweep's conditional remove can't drop a concurrent refresh.
    fresh.forEach {
      cellSeenAt[it.identifier] = now
      cellCache[it.identifier] = it
    }
  }

  private fun publishNow() {
    val now = SystemClock.elapsedRealtime()
    // Expiring here, not on ingest, ages towers out even when the radio goes silent.
    cellSeenAt.forEach { (key, seenAt) ->
      if (now - seenAt > STALE_TTL_MS && cellSeenAt.remove(key, seenAt)) {
        cellCache.remove(key)
      }
    }
    val status = status()
    val signals =
      if (status == ScannerStatus.OK) {
        cellCache.values.sortedBy { it.identifier }
      } else {
        emptyList()
      }
    val ready = readiness.compute(status == ScannerStatus.OK, signals.isNotEmpty(), now)
    _state.value =
      CellularSourceState(
        signals = signals,
        status = status,
        ready = ready,
        connectionLabel = connectionLabel
      )
  }

  private fun sanitizeDbm(raw: Int?): Int? {
    if (raw == null || raw >= 0) return null
    return raw
  }

  private fun sanitizeNci(raw: Long?): Long? {
    if (raw == null) return null
    if (raw == CellInfo.UNAVAILABLE_LONG) return null
    if (raw < 0L) return null
    return raw
  }

  private fun sanitizeCellId(raw: Int?): Int? {
    if (raw == null) return null
    if (raw == CellInfo.UNAVAILABLE) return null
    if (raw < 0) return null
    return raw
  }

  private fun operatorName(id: CellIdentity?): String? = id?.operatorAlphaLong?.toString()?.takeIf { it.isNotBlank() }

  private fun parseNr(info: CellInfoNr): CellSignal? {
    val ss = info.cellSignalStrength as? CellSignalStrengthNr
    val id = info.cellIdentity as? CellIdentityNr
    // Tensor emits placeholder cells during NSA/RAT transitions: identity all zeros on a
    // 0 Hz carrier (nrarfcn 0), signal frozen at the valid-range ceiling (ssRsrp -44).
    // Nothing in them is measured.
    if (id != null && id.nrarfcn == 0 && id.nci == 0L && id.pci == 0 && id.tac == 0) return null
    val dbm = sanitizeDbm(ss?.dbm) ?: return null
    val exposureDbm = dbm + NR_SSRSRP_TO_RSSI_OFFSET_DB
    val operator = operatorName(id)
    val details =
      buildList {
        add(DetailEntry("Status", if (info.isRegistered) "Serving" else "Neighbor"))
        id?.mccString?.let { add(DetailEntry("MCC", it)) }
        id?.mncString?.let { add(DetailEntry("MNC", it)) }
        if (id != null && id.nci != CellInfo.UNAVAILABLE_LONG) add(DetailEntry("NCI", id.nci.toString()))
        if (id != null && id.pci != Int.MAX_VALUE) add(DetailEntry("PCI", id.pci.toString()))
        if (id != null && id.tac != Int.MAX_VALUE) add(DetailEntry("TAC", id.tac.toString()))
        if (id != null && id.nrarfcn != Int.MAX_VALUE) {
          add(DetailEntry("NR-ARFCN", id.nrarfcn.toString()))
          CellChannels.nrArfcnToMhz(id.nrarfcn)?.let { add(DetailEntry("Frequency", CellChannels.label(it))) }
        }
        id
          ?.bands
          ?.takeIf { it.isNotEmpty() }
          ?.let { bands -> add(DetailEntry("Bands", bands.joinToString { "n$it" })) }
        ss?.let {
          if (it.ssRsrq != CellInfo.UNAVAILABLE) add(DetailEntry("SS-RSRQ", "${it.ssRsrq} dB"))
          if (it.ssSinr != CellInfo.UNAVAILABLE) add(DetailEntry("SS-SINR", "${it.ssSinr} dB"))
          if (it.csiRsrp != CellInfo.UNAVAILABLE) add(DetailEntry("CSI-RSRP", "${it.csiRsrp} dBm"))
        }
      }
    return CellSignal(
      type = CellNetworkType.NR_5G,
      dbm = dbm,
      exposureDbm = exposureDbm,
      channelKey = id?.nrarfcn?.takeIf { it != Int.MAX_VALUE && it > 0 }?.let { "NR-$it" },
      distanceMeters = null,
      distanceConfidence = DistanceConfidence.NONE,
      // Neighbors usually lack a cell id; fall back to physical-layer identity so they don't collide.
      identifier = "NR-${sanitizeNci(id?.nci) ?: "pci${id?.pci}@${id?.nrarfcn}"}",
      operator = operator,
      isConnected = info.isRegistered,
      details = details
    )
  }

  private fun parseLte(
    info: CellInfoLte,
    subId: Int
  ): CellSignal? {
    val ss: CellSignalStrengthLte = info.cellSignalStrength
    val id: CellIdentityLte = info.cellIdentity
    var dbm = sanitizeDbm(ss.dbm)
    var rssi = ss.rssi
    if (dbm == null && info.isRegistered) {
      // Tensor intermittently reports the registered cell with rsrp=UNAVAILABLE and a
      // sentinel rssi (-113) while SignalStrength still carries the real measurement;
      // without this the serving tower vanishes and only neighbors stay visible.
      val sys =
        latestSignalStrength[subId]
          ?.getCellSignalStrengths(CellSignalStrengthLte::class.java)
          ?.firstOrNull()
      if (sys != null) {
        val sysRsrp = sanitizeDbm(sys.rsrp)
        if (sysRsrp != null) {
          dbm = sysRsrp
          rssi = sys.rssi
        }
      }
    }
    if (dbm == null) return null
    val bandwidthKhz = id.bandwidth.takeIf { it != Int.MAX_VALUE && it > 0 }
    val earfcn = id.earfcn.takeIf { it != Int.MAX_VALUE && it > 0 }
    val exposureDbm = lteExposureDbm(rssi, dbm, bandwidthKhz, earfcn)
    val ta = ss.timingAdvance
    val distance: Double? = if (ta in 1..1282) Distance.fromLteTimingAdvance(ta) else null
    val operator = operatorName(id)

    val details =
      buildList {
        add(DetailEntry("Status", if (info.isRegistered) "Serving" else "Neighbor"))
        id.mccString?.let { add(DetailEntry("MCC", it)) }
        id.mncString?.let { add(DetailEntry("MNC", it)) }
        if (id.ci != Int.MAX_VALUE) add(DetailEntry("CI", id.ci.toString()))
        if (id.pci != Int.MAX_VALUE) add(DetailEntry("PCI", id.pci.toString()))
        if (id.tac != Int.MAX_VALUE) add(DetailEntry("TAC", id.tac.toString()))
        if (id.earfcn != Int.MAX_VALUE) {
          add(DetailEntry("EARFCN", id.earfcn.toString()))
          CellChannels.earfcnToMhz(id.earfcn)?.let { add(DetailEntry("Frequency", CellChannels.label(it))) }
        }
        if (id.bandwidth != Int.MAX_VALUE) add(DetailEntry("Bandwidth", "${id.bandwidth / 1000} MHz"))
        id.bands.takeIf { it.isNotEmpty() }?.let { bands -> add(DetailEntry("Bands", bands.joinToString { "B$it" })) }
        if (ss.rsrq != CellInfo.UNAVAILABLE) add(DetailEntry("RSRQ", "${ss.rsrq} dB"))
        if (ss.rssnr != CellInfo.UNAVAILABLE) add(DetailEntry("SNR", "${ss.rssnr} dB"))
        if (ss.cqi != CellInfo.UNAVAILABLE) add(DetailEntry("CQI", ss.cqi.toString()))
        if (distance != null) add(DetailEntry("TA", ta.toString()))
      }
    return CellSignal(
      type = CellNetworkType.LTE_4G,
      dbm = dbm,
      exposureDbm = exposureDbm,
      channelKey = earfcn?.let { "LTE-$it" },
      distanceMeters = distance,
      distanceConfidence = if (distance != null) DistanceConfidence.CALIBRATED else DistanceConfidence.NONE,
      identifier = "LTE-${sanitizeCellId(id.ci) ?: "pci${id.pci}@${id.earfcn}"}",
      operator = operator,
      isConnected = info.isRegistered,
      details = details
    )
  }

  private fun parseWcdma(info: CellInfoWcdma): CellSignal? {
    val ss: CellSignalStrengthWcdma = info.cellSignalStrength
    val id: CellIdentityWcdma = info.cellIdentity
    // Same zero-identity placeholder pattern as NR, seen during LTE→UMTS transitions
    // (cid/psc/lac/uarfcn all 0, rscp pinned at the -120 floor).
    if (id.uarfcn == 0 && id.cid == 0 && id.psc == 0 && id.lac == 0) return null
    val dbm = sanitizeDbm(ss.dbm) ?: return null
    val ecNo = ss.ecNo.takeIf { it != CellInfo.UNAVAILABLE }
    // Some modems (seen on Tensor while camped on UMTS) pad the cell list with dozens of
    // unmeasured neighbor entries, stamped with a constant RSCP fill of -116 and Ec/No
    // pinned at its -24 floor. An unregistered cell at the Ec/No floor carries no detected
    // pilot, and one at or below the RSCP threshold is indistinguishable from that padding.
    if (!info.isRegistered && ((ecNo != null && ecNo <= WCDMA_ECNO_FLOOR_DB) || dbm <= WCDMA_NEIGHBOR_FLOOR_DBM)) {
      return null
    }
    val exposureDbm = if (ecNo != null) dbm - ecNo else dbm + WCDMA_RSCP_TO_RSSI_OFFSET_DB
    val operator = operatorName(id)
    val details =
      buildList {
        add(DetailEntry("Status", if (info.isRegistered) "Serving" else "Neighbor"))
        id.mccString?.let { add(DetailEntry("MCC", it)) }
        id.mncString?.let { add(DetailEntry("MNC", it)) }
        if (id.cid != Int.MAX_VALUE) add(DetailEntry("CID", id.cid.toString()))
        if (id.psc != Int.MAX_VALUE) add(DetailEntry("PSC", id.psc.toString()))
        if (id.lac != Int.MAX_VALUE) add(DetailEntry("LAC", id.lac.toString()))
        if (id.uarfcn != Int.MAX_VALUE) {
          add(DetailEntry("UARFCN", id.uarfcn.toString()))
          CellChannels.uarfcnToMhz(id.uarfcn)?.let { add(DetailEntry("Frequency", CellChannels.label(it))) }
        }
      }
    return CellSignal(
      type = CellNetworkType.WCDMA_3G,
      dbm = dbm,
      exposureDbm = exposureDbm,
      channelKey = id.uarfcn.takeIf { it != Int.MAX_VALUE && it > 0 }?.let { "WCDMA-$it" },
      distanceMeters = null,
      distanceConfidence = DistanceConfidence.NONE,
      identifier = "WCDMA-${sanitizeCellId(id.cid) ?: "psc${id.psc}@${id.uarfcn}"}",
      operator = operator,
      isConnected = info.isRegistered,
      details = details
    )
  }

  private fun parseGsm(info: CellInfoGsm): CellSignal? {
    val ss: CellSignalStrengthGsm = info.cellSignalStrength
    val id: CellIdentityGsm = info.cellIdentity
    val dbm = sanitizeDbm(ss.dbm) ?: return null
    val ta = ss.timingAdvance
    val distance: Double? = if (ta in 1..219) Distance.fromGsmTimingAdvance(ta) else null
    val operator = operatorName(id)

    val details =
      buildList {
        add(DetailEntry("Status", if (info.isRegistered) "Serving" else "Neighbor"))
        id.mccString?.let { add(DetailEntry("MCC", it)) }
        id.mncString?.let { add(DetailEntry("MNC", it)) }
        if (id.cid != Int.MAX_VALUE) add(DetailEntry("CID", id.cid.toString()))
        if (id.lac != Int.MAX_VALUE) add(DetailEntry("LAC", id.lac.toString()))
        if (id.arfcn != Int.MAX_VALUE) {
          add(DetailEntry("ARFCN", id.arfcn.toString()))
          CellChannels.arfcnToMhz(id.arfcn)?.let { add(DetailEntry("Frequency", CellChannels.label(it))) }
        }
        if (id.bsic != Int.MAX_VALUE) add(DetailEntry("BSIC", id.bsic.toString()))
        if (ss.bitErrorRate != CellInfo.UNAVAILABLE) {
          add(DetailEntry("BER", ss.bitErrorRate.toString()))
        }
        if (distance != null) add(DetailEntry("TA", ta.toString()))
      }
    return CellSignal(
      type = CellNetworkType.GSM_2G,
      dbm = dbm,
      exposureDbm = dbm,
      channelKey = id.arfcn.takeIf { it != Int.MAX_VALUE && it > 0 }?.let { "GSM-$it" },
      distanceMeters = distance,
      distanceConfidence = if (distance != null) DistanceConfidence.CALIBRATED else DistanceConfidence.NONE,
      identifier = "GSM-${sanitizeCellId(id.cid) ?: "bsic${id.bsic}@${id.arfcn}"}",
      operator = operator,
      isConnected = info.isRegistered,
      details = details
    )
  }

  private fun lteExposureDbm(
    rssi: Int,
    rsrpDbm: Int,
    bandwidthKhz: Int?,
    earfcn: Int?
  ): Int {
    val now = SystemClock.elapsedRealtime()
    // RSSI physically sits at or above RSRP; readings outside 0..40 dB over RSRP are modem garbage.
    if (rssi != CellInfo.UNAVAILABLE && (rssi - rsrpDbm) in 0..MAX_LTE_RSSI_OVER_RSRP_DB) {
      if (earfcn != null) lteRssiCache[earfcn] = CachedRssi(rssi, now)
      return rssi
    }
    if (earfcn != null) {
      val cached = lteRssiCache[earfcn]
      if (cached != null && now - cached.timestampMs < LTE_RSSI_STALENESS_MS) {
        return cached.rssi
      }
    }
    val nRb = bandwidthKhz?.let(::lteResourceBlocks)
    return if (nRb != null) {
      rsrpDbm + (10.0 * log10(12.0 * nRb)).roundToInt()
    } else {
      rsrpDbm + LTE_RSRP_FALLBACK_OFFSET_DB
    }
  }

  private fun lteResourceBlocks(bwKhz: Int): Int? =
    when (bwKhz) {
      in 1300..1500 -> 6
      in 2900..3100 -> 15
      in 4900..5100 -> 25
      in 9900..10100 -> 50
      in 14900..15100 -> 75
      in 19900..20100 -> 100
      else -> null
    }

  private companion object {
    const val TAG = "CellularScanner"

    const val STALE_TTL_MS = 60_000L

    const val NR_SSRSRP_TO_RSSI_OFFSET_DB = 30
    const val WCDMA_RSCP_TO_RSSI_OFFSET_DB = 10

    // Bottom of the valid Ec/No reporting range [-24, 1].
    const val WCDMA_ECNO_FLOOR_DB = -24

    // Just above UMTS reference sensitivity (≈ -117 dBm): a neighbor this weak is unusable
    // and indistinguishable from modem padding.
    const val WCDMA_NEIGHBOR_FLOOR_DBM = -115
    const val LTE_RSRP_FALLBACK_OFFSET_DB = 30
    const val LTE_RSSI_STALENESS_MS = 30_000L

    const val MAX_LTE_RSSI_OVER_RSRP_DB = 40

    const val CELL_HEARTBEAT_MS = 5_000L
    const val CELL_WARMUP_MS = 8_000L
  }
}
