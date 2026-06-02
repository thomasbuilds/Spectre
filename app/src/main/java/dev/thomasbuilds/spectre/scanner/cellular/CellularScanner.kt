package dev.thomasbuilds.spectre.scanner.cellular

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.LocationManager
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
import android.telephony.SubscriptionManager
import android.telephony.TelephonyCallback
import android.telephony.TelephonyDisplayInfo
import android.telephony.TelephonyManager
import android.util.Log
import androidx.core.content.ContextCompat
import dev.thomasbuilds.spectre.analysis.Distance
import dev.thomasbuilds.spectre.model.CellNetworkType
import dev.thomasbuilds.spectre.model.CellSignal
import dev.thomasbuilds.spectre.model.CellularSourceState
import dev.thomasbuilds.spectre.model.DetailEntry
import dev.thomasbuilds.spectre.model.DistanceConfidence
import dev.thomasbuilds.spectre.model.ScannerStatus
import dev.thomasbuilds.spectre.scanner.ReadinessTracker
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors
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

  private val readiness = ReadinessTracker(CELL_WARMUP_MS, CELL_STALENESS_MS)

  private var heartbeatJob: Job? = null

  private val callbackExecutor =
    Executors.newSingleThreadExecutor { r ->
      Thread(r, "spectre-cell").apply { isDaemon = true }
    }

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
    val callback: TelephonyCallback
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
        TelephonyCallback.DisplayInfoListener {
        override fun onCellInfoChanged(cellInfo: List<CellInfo>) {
          ingestCellList(cellInfo, subId)
          publishNow()
        }

        override fun onDisplayInfoChanged(info: TelephonyDisplayInfo) {
          lastDisplayInfo = info
          publishNow()
        }
      }
    } else {
      object :
        TelephonyCallback(),
        TelephonyCallback.CellInfoListener {
        override fun onCellInfoChanged(cellInfo: List<CellInfo>) {
          ingestCellList(cellInfo, subId)
          publishNow()
        }
      }
    }

  @SuppressLint("MissingPermission")
  private fun isMobileDataEnabled(): Boolean {
    val tm = telephony ?: return true
    return runCatching { tm.isDataEnabled }.getOrDefault(true)
  }

  fun hasPermission(): Boolean {
    val phone =
      ContextCompat.checkSelfPermission(
        context,
        Manifest.permission.READ_PHONE_STATE
      ) == PackageManager.PERMISSION_GRANTED
    val location =
      ContextCompat.checkSelfPermission(
        context,
        Manifest.permission.ACCESS_FINE_LOCATION
      ) == PackageManager.PERMISSION_GRANTED
    return phone && location
  }

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
        scope.launch {
          while (isActive) {
            delay(CELL_HEARTBEAT_MS)
            if (status() == ScannerStatus.OK) {
              // Recover if the first sync registered no subscriptions (e.g. the SIM was not yet
              // readable right after the permission grant). start() runs only once, so without this
              // the card stays at zero until the service is recreated.
              if (subMonitors.isEmpty()) {
                syncSubscriptions()
                registerSubscriptionChangeListener()
              }
              requestCellInfoRefresh()
            }
            publishNow()
          }
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
    if (subsChangeListener != null) return
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

    subMonitors.keys
      .filter { it !in targetSubIds }
      .forEach { subId ->
        subMonitors.remove(subId)?.let { mon ->
          runCatching { mon.tm.unregisterTelephonyCallback(mon.callback) }
          purgeSub(subId)
        }
      }

    targetSubIds.forEach { subId ->
      if (subMonitors.containsKey(subId)) return@forEach
      val tm = telephony?.createForSubscriptionId(subId) ?: return@forEach
      val callback = makeCellCallback(subId, withDisplayInfo = subId == dataSubId)
      runCatching { tm.registerTelephonyCallback(callbackExecutor, callback) }
        .onSuccess { subMonitors[subId] = SubMonitor(subId, tm, callback) }
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
    val fresh =
      cells
        .mapNotNull { info ->
          runCatching {
            when (info) {
              is CellInfoNr -> parseNr(info)
              is CellInfoLte -> parseLte(info)
              is CellInfoWcdma -> parseWcdma(info)
              is CellInfoGsm -> parseGsm(info)
              else -> null
            }
          }.getOrNull()
        }.map { it.copy(identifier = "$subId:${it.identifier}") }
    val now = System.currentTimeMillis()
    fresh.forEach {
      cellCache[it.identifier] = it
      cellSeenAt[it.identifier] = now
    }
    val expired =
      cellSeenAt.entries
        .filter { now - it.value > STALE_TTL_MS }
        .map { it.key }
    expired.forEach {
      cellCache.remove(it)
      cellSeenAt.remove(it)
    }
  }

  private fun publishNow() {
    val status = status()
    val signals =
      if (status == ScannerStatus.OK) {
        cellCache.values.sortedBy { it.identifier }
      } else {
        emptyList()
      }
    val now = System.currentTimeMillis()
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
    if (raw == Long.MAX_VALUE) return null
    if (raw < 0L) return null
    return raw
  }

  private fun sanitizeCellId(raw: Int?): Int? {
    if (raw == null) return null
    if (raw == Int.MAX_VALUE) return null
    if (raw == CellInfo.UNAVAILABLE) return null
    if (raw < 0) return null
    return raw
  }

  private fun parseNr(info: CellInfoNr): CellSignal? {
    val ss = info.cellSignalStrength as? CellSignalStrengthNr
    val id = info.cellIdentity as? CellIdentityNr
    val rawDbm = ss?.dbm
    val dbm = sanitizeDbm(rawDbm) ?: return null
    val exposureDbm = dbm + NR_SSRSRP_TO_RSSI_OFFSET_DB
    val nci = sanitizeNci(id?.nci) ?: 0L
    val operator = id?.operatorAlphaLong?.toString()?.takeIf { it.isNotBlank() }
    val details =
      buildList {
        add(DetailEntry("Status", if (info.isRegistered) "Serving" else "Neighbor"))
        id?.mccString?.let { add(DetailEntry("MCC", it)) }
        id?.mncString?.let { add(DetailEntry("MNC", it)) }
        if (id != null && id.nci != Long.MAX_VALUE) add(DetailEntry("NCI", id.nci.toString()))
        if (id != null && id.pci != Int.MAX_VALUE) add(DetailEntry("PCI", id.pci.toString()))
        if (id != null && id.tac != Int.MAX_VALUE) add(DetailEntry("TAC", id.tac.toString()))
        if (id != null && id.nrarfcn != Int.MAX_VALUE) add(DetailEntry("NR-ARFCN", id.nrarfcn.toString()))
        id
          ?.bands
          ?.takeIf { it.isNotEmpty() }
          ?.let { add(DetailEntry("Bands", it.joinToString { "n$it" })) }
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
      identifier = "NR-$nci",
      operator = operator,
      isConnected = info.isRegistered,
      details = details
    )
  }

  private fun parseLte(info: CellInfoLte): CellSignal? {
    val ss: CellSignalStrengthLte = info.cellSignalStrength
    val id: CellIdentityLte = info.cellIdentity
    val rawDbm = ss.dbm
    val dbm = sanitizeDbm(rawDbm) ?: return null
    val bandwidthKhz = id.bandwidth.takeIf { it != Int.MAX_VALUE && it > 0 }
    val earfcn = id.earfcn.takeIf { it != Int.MAX_VALUE && it > 0 }
    val exposureDbm = lteExposureDbm(ss.rssi, dbm, bandwidthKhz, earfcn)
    val ci = sanitizeCellId(id.ci) ?: 0
    val ta = ss.timingAdvance
    val distance: Double? = if (ta in 1..1282) Distance.fromLteTimingAdvance(ta) else null
    val operator = id.operatorAlphaLong?.toString()?.takeIf { it.isNotBlank() }

    val details =
      buildList {
        add(DetailEntry("Status", if (info.isRegistered) "Serving" else "Neighbor"))
        id.mccString?.let { add(DetailEntry("MCC", it)) }
        id.mncString?.let { add(DetailEntry("MNC", it)) }
        if (id.ci != Int.MAX_VALUE) add(DetailEntry("CI", id.ci.toString()))
        if (id.pci != Int.MAX_VALUE) add(DetailEntry("PCI", id.pci.toString()))
        if (id.tac != Int.MAX_VALUE) add(DetailEntry("TAC", id.tac.toString()))
        if (id.earfcn != Int.MAX_VALUE) add(DetailEntry("EARFCN", id.earfcn.toString()))
        if (id.bandwidth != Int.MAX_VALUE) add(DetailEntry("Bandwidth", "${id.bandwidth / 1000} MHz"))
        id.bands.takeIf { it.isNotEmpty() }?.let { add(DetailEntry("Bands", it.joinToString { "B$it" })) }
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
      identifier = "LTE-$ci",
      operator = operator,
      isConnected = info.isRegistered,
      details = details
    )
  }

  private fun parseWcdma(info: CellInfoWcdma): CellSignal? {
    val ss: CellSignalStrengthWcdma = info.cellSignalStrength
    val id: CellIdentityWcdma = info.cellIdentity
    val rawDbm = ss.dbm
    val dbm = sanitizeDbm(rawDbm) ?: return null
    val ecNo = ss.ecNo.takeIf { it != CellInfo.UNAVAILABLE }
    val exposureDbm = if (ecNo != null) dbm - ecNo else dbm + WCDMA_RSCP_TO_RSSI_OFFSET_DB
    val cid = sanitizeCellId(id.cid) ?: 0
    val operator = id.operatorAlphaLong?.toString()?.takeIf { it.isNotBlank() }
    val details =
      buildList {
        add(DetailEntry("Status", if (info.isRegistered) "Serving" else "Neighbor"))
        id.mccString?.let { add(DetailEntry("MCC", it)) }
        id.mncString?.let { add(DetailEntry("MNC", it)) }
        if (id.cid != Int.MAX_VALUE) add(DetailEntry("CID", id.cid.toString()))
        if (id.psc != Int.MAX_VALUE) add(DetailEntry("PSC", id.psc.toString()))
        if (id.lac != Int.MAX_VALUE) add(DetailEntry("LAC", id.lac.toString()))
        if (id.uarfcn != Int.MAX_VALUE) add(DetailEntry("UARFCN", id.uarfcn.toString()))
      }
    return CellSignal(
      type = CellNetworkType.WCDMA_3G,
      dbm = dbm,
      exposureDbm = exposureDbm,
      channelKey = id.uarfcn.takeIf { it != Int.MAX_VALUE && it > 0 }?.let { "WCDMA-$it" },
      distanceMeters = null,
      distanceConfidence = DistanceConfidence.NONE,
      identifier = "WCDMA-$cid",
      operator = operator,
      isConnected = info.isRegistered,
      details = details
    )
  }

  private fun parseGsm(info: CellInfoGsm): CellSignal? {
    val ss: CellSignalStrengthGsm = info.cellSignalStrength
    val id: CellIdentityGsm = info.cellIdentity
    val rawDbm = ss.dbm
    val dbm = sanitizeDbm(rawDbm) ?: return null
    val exposureDbm = dbm
    val cid = sanitizeCellId(id.cid) ?: 0
    val ta = ss.timingAdvance
    val distance: Double? = if (ta in 1..219) Distance.fromGsmTimingAdvance(ta) else null
    val operator = id.operatorAlphaLong?.toString()?.takeIf { it.isNotBlank() }

    val details =
      buildList {
        add(DetailEntry("Status", if (info.isRegistered) "Serving" else "Neighbor"))
        id.mccString?.let { add(DetailEntry("MCC", it)) }
        id.mncString?.let { add(DetailEntry("MNC", it)) }
        if (id.cid != Int.MAX_VALUE) add(DetailEntry("CID", id.cid.toString()))
        if (id.lac != Int.MAX_VALUE) add(DetailEntry("LAC", id.lac.toString()))
        if (id.arfcn != Int.MAX_VALUE) add(DetailEntry("ARFCN", id.arfcn.toString()))
        if (id.bsic != Int.MAX_VALUE) add(DetailEntry("BSIC", id.bsic.toString()))
        if (ss.bitErrorRate != CellInfo.UNAVAILABLE) {
          add(DetailEntry("BER", ss.bitErrorRate.toString()))
        }
        if (distance != null) add(DetailEntry("TA", ta.toString()))
      }
    return CellSignal(
      type = CellNetworkType.GSM_2G,
      dbm = dbm,
      exposureDbm = exposureDbm,
      channelKey = id.arfcn.takeIf { it != Int.MAX_VALUE && it > 0 }?.let { "GSM-$it" },
      distanceMeters = distance,
      distanceConfidence = if (distance != null) DistanceConfidence.CALIBRATED else DistanceConfidence.NONE,
      identifier = "GSM-$cid",
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
    val now = System.currentTimeMillis()
    val rssiAvailable = rssi != CellInfo.UNAVAILABLE
    if (rssiAvailable && (rssi - rsrpDbm) <= MAX_LTE_RSSI_OVER_RSRP_DB) {
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
    const val LTE_RSRP_FALLBACK_OFFSET_DB = 30
    const val LTE_RSSI_STALENESS_MS = 30_000L

    const val MAX_LTE_RSSI_OVER_RSRP_DB = 40

    const val CELL_HEARTBEAT_MS = 5_000L
    const val CELL_WARMUP_MS = 8_000L
    const val CELL_STALENESS_MS = 15_000L
  }
}
