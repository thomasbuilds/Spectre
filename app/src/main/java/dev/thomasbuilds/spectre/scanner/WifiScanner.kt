package dev.thomasbuilds.spectre.scanner

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.LocationManager
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.net.wifi.ScanResult
import android.net.wifi.WifiManager
import androidx.core.content.ContextCompat
import dev.thomasbuilds.spectre.analysis.Distance
import dev.thomasbuilds.spectre.model.DetailEntry
import dev.thomasbuilds.spectre.model.DistanceConfidence
import dev.thomasbuilds.spectre.model.ScannerStatus
import dev.thomasbuilds.spectre.model.WifiBand
import dev.thomasbuilds.spectre.model.WifiSecurity
import dev.thomasbuilds.spectre.model.WifiSignal
import dev.thomasbuilds.spectre.model.WifiSourceState
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

class WifiScanner(
  private val context: Context
) {
  private val wifi: WifiManager? =
    context.applicationContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager
  private val locationManager: LocationManager? =
    context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager
  private val connectivityManager: ConnectivityManager? =
    context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
  private val rttRanger = WifiRttRanger(context)

  private val _state = MutableStateFlow(WifiSourceState())
  val state: StateFlow<WifiSourceState> = _state.asStateFlow()

  private data class ApState(
    val signal: WifiSignal,
    val smoothedRssi: Double,
    val sampleCount: Int,
    val firstSeenMs: Long,
    val lastSeenMs: Long,
    val lastScanTimestampMicros: Long
  )

  private val apCache = ConcurrentHashMap<String, ApState>()

  @Volatile private var liveConnectedRssi: Int? = null

  private val readiness = ReadinessTracker(WIFI_WARMUP_MS, WIFI_STALENESS_MS)

  private var scanLoopJob: Job? = null
  private var heartbeatJob: Job? = null

  private val callbackExecutor =
    Executors.newSingleThreadExecutor { r ->
      Thread(r, "spectre-wifi").apply { isDaemon = true }
    }

  private val scanCallback =
    object : WifiManager.ScanResultsCallback() {
      override fun onScanResultsAvailable() {
        ingest()
        publishNow()
      }
    }

  private val networkCallback =
    object : ConnectivityManager.NetworkCallback() {
      override fun onCapabilitiesChanged(
        network: Network,
        caps: NetworkCapabilities
      ) {
        val rssi = runCatching { caps.signalStrength }.getOrDefault(Int.MIN_VALUE)
        val sanitized = if (rssi == Int.MIN_VALUE) null else sanitizeRssi(rssi)
        if (sanitized != liveConnectedRssi) {
          liveConnectedRssi = sanitized
          callbackExecutor.execute { publishNow() }
        }
      }

      override fun onLost(network: Network) {
        if (liveConnectedRssi != null) {
          liveConnectedRssi = null
          callbackExecutor.execute { publishNow() }
        }
      }
    }

  private var registered = false
  private var networkCallbackRegistered = false

  fun hasPermission(): Boolean =
    ContextCompat.checkSelfPermission(
      context,
      Manifest.permission.ACCESS_FINE_LOCATION
    ) == PackageManager.PERMISSION_GRANTED

  fun status(): ScannerStatus {
    if (!hasPermission()) return ScannerStatus.NO_PERMISSION
    if (locationManager?.isLocationEnabled != true) return ScannerStatus.LOCATION_OFF
    if (wifi?.isWifiEnabled != true) return ScannerStatus.RADIO_OFF
    return ScannerStatus.OK
  }

  fun start(scope: CoroutineScope) {
    if (!hasPermission()) return
    val w = wifi ?: return
    if (!registered) {
      runCatching { w.registerScanResultsCallback(callbackExecutor, scanCallback) }
        .onSuccess { registered = true }
      ingest()
    }
    if (!networkCallbackRegistered) {
      val request =
        NetworkRequest
          .Builder()
          .addTransportType(NetworkCapabilities.TRANSPORT_WIFI)
          .build()
      runCatching { connectivityManager?.registerNetworkCallback(request, networkCallback) }
        .onSuccess { networkCallbackRegistered = true }
    }
    if (scanLoopJob?.isActive != true) {
      scanLoopJob =
        scope.launch {
          while (isActive) {
            val interval =
              if (isScanThrottlingEnabled()) {
                WIFI_PROBE_INTERVAL_THROTTLED_MS
              } else {
                WIFI_PROBE_INTERVAL_UNTHROTTLED_MS
              }
            delay(interval)
            if (status() == ScannerStatus.OK) startScan()
          }
        }
    }
    if (heartbeatJob?.isActive != true) {
      heartbeatJob =
        scope.launch {
          while (isActive) {
            delay(WIFI_HEARTBEAT_MS)
            publishNow()
          }
        }
    }
    publishNow()
  }

  fun stop() {
    if (registered) {
      wifi?.unregisterScanResultsCallback(scanCallback)
      registered = false
    }
    if (networkCallbackRegistered) {
      runCatching { connectivityManager?.unregisterNetworkCallback(networkCallback) }
      networkCallbackRegistered = false
    }
  }

  @SuppressLint("MissingPermission")
  private fun startScan(): Boolean {
    if (!hasPermission()) return false
    val w = wifi ?: return false
    if (!w.isWifiEnabled) return false
    @Suppress("DEPRECATION")
    return runCatching { w.startScan() }.getOrDefault(false)
  }

  private fun isScanThrottlingEnabled(): Boolean =
    runCatching {
      wifi?.isScanThrottleEnabled ?: true
    }.getOrDefault(true)

  private fun enforceSizeCap() {
    val overflow = apCache.size - MAX_TRACKED_APS
    if (overflow <= 0) return
    apCache.entries
      .sortedBy { it.value.lastSeenMs }
      .take(overflow)
      .forEach { apCache.remove(it.key) }
  }

  private fun publishNow() {
    val status = status()
    enforceSizeCap()
    val now = System.currentTimeMillis()
    val signals =
      if (status != ScannerStatus.OK) {
        emptyList()
      } else {
        val connectedBssid = wifi?.let { currentConnectedBssid(it) }
        val liveRssi = liveConnectedRssi
        apCache.entries
          .sortedBy {
            it.value.signal.bssid
              .ifEmpty { it.value.signal.ssid }
          }.map { (key, state) ->
            val isStale = now - state.lastSeenMs > STALE_TTL_MS
            val base =
              if (state.signal.isStale == isStale) {
                state.signal
              } else {
                state.signal.copy(isStale = isStale).also { apCache[key] = state.copy(signal = it) }
              }
            overlayLiveData(base, connectedBssid, liveRssi)
          }
      }
    val ready = readiness.compute(status == ScannerStatus.OK, signals.any { !it.isStale }, now)
    _state.value =
      WifiSourceState(
        signals = signals,
        status = status,
        ready = ready,
        scanThrottlingOn = isScanThrottlingEnabled()
      )
  }

  private fun overlayLiveData(
    base: WifiSignal,
    connectedBssid: String?,
    liveRssi: Int?
  ): WifiSignal {
    val matches = connectedBssid != null && base.bssid.equals(connectedBssid, ignoreCase = true)
    val effectiveLiveRssi = if (matches) liveRssi else null
    val ftm = rttRanger.fresh(base.bssid)
    if (ftm == null && effectiveLiveRssi == null && matches == base.isConnected) return base

    var details = base.details
    if (effectiveLiveRssi != null) {
      details =
        details.map { entry ->
          if (entry.label == "Signal") DetailEntry("Signal", "$effectiveLiveRssi dBm (live)") else entry
        }
    }

    return base.copy(
      rssi = effectiveLiveRssi ?: base.rssi,
      isConnected = matches,
      distanceMeters = ftm?.distanceM ?: base.distanceMeters,
      distanceConfidence = if (ftm != null) DistanceConfidence.CALIBRATED else base.distanceConfidence,
      details = details
    )
  }

  @SuppressLint("MissingPermission")
  private fun ingest() {
    if (!hasPermission()) return
    val w = wifi ?: return
    val results: List<ScanResult> =
      runCatching { w.scanResults ?: emptyList() }
        .getOrDefault(emptyList())
    val connectedBssid = currentConnectedBssid(w)
    val now = System.currentTimeMillis()

    results.forEach { sr ->
      val key = (sr.BSSID ?: "").ifEmpty { sr.wifiSsid?.toString().orEmpty() }
      if (key.isEmpty()) return@forEach

      val sanitized = sanitizeRssi(sr.level)
      if (sanitized == null) return@forEach

      val previous = apCache[key]
      // getScanResults() keeps returning recently-cached APs the latest scan didn't actually
      // re-detect; skip those (chipset timestamp not advanced) so a departed AP ages out and
      // its EMA and sample count aren't inflated by re-counting one measurement.
      val tsMicros = sr.timestamp
      if (previous != null && tsMicros > 0L && tsMicros <= previous.lastScanTimestampMicros) {
        return@forEach
      }
      val sampleCount = (previous?.sampleCount ?: 0) + 1
      val smoothedRssi =
        if (previous == null) {
          sanitized.toDouble()
        } else {
          EMA_ALPHA * sanitized + (1 - EMA_ALPHA) * previous.smoothedRssi
        }

      val isConnected =
        connectedBssid != null &&
          sr.BSSID?.equals(connectedBssid, ignoreCase = true) == true
      val firstSeenMs = previous?.firstSeenMs ?: now
      apCache[key] =
        ApState(
          signal = mapScanResult(sr, smoothedRssi, sampleCount, isConnected, sanitized, firstSeenMs),
          smoothedRssi = smoothedRssi,
          sampleCount = sampleCount,
          firstSeenMs = firstSeenMs,
          lastSeenMs = now,
          lastScanTimestampMicros = tsMicros
        )
    }

    rttRanger.requestRanging(results)
  }

  // No public non-deprecated replacement: WifiInfo from a NetworkCallback's
  // transportInfo has its BSSID redacted unless the request opts in via the
  // @SystemApi setIncludeLocationInfo, which a normal app cannot call. So
  // getConnectionInfo() is the only public way to read the connected BSSID.
  @SuppressLint("MissingPermission")
  @Suppress("DEPRECATION")
  private fun currentConnectedBssid(w: WifiManager): String? {
    if (!w.isWifiEnabled) return null
    val info = runCatching { w.connectionInfo }.getOrNull() ?: return null
    val bssid = info.bssid ?: return null
    if (bssid == "02:00:00:00:00:00") return null
    return bssid
  }

  private fun mapScanResult(
    sr: ScanResult,
    smoothedRssi: Double,
    sampleCount: Int,
    isConnected: Boolean,
    sanitizedRssi: Int,
    firstSeenMs: Long
  ): WifiSignal {
    val freq = sr.frequency
    val band =
      when {
        freq < 3000 -> WifiBand.GHZ_2_4
        freq < 5950 -> WifiBand.GHZ_5
        else -> WifiBand.GHZ_6
      }
    val rawSsid =
      sr.wifiSsid
        ?.toString()
        ?.trim('"')
        .orEmpty()
    val ssid = if (rawSsid.isBlank()) "Hidden" else rawSsid
    val width = channelWidthMhz(sr.channelWidth)
    val channel = channelNumber(freq)
    val securityLabel = parseSecurity(sr.capabilities)

    val (distance, confidence) =
      if (sampleCount < MIN_SAMPLES_FOR_DISTANCE) {
        null to DistanceConfidence.PENDING
      } else {
        Distance.fromWifiRssi(smoothedRssi.toInt(), freq) to DistanceConfidence.APPROXIMATE
      }

    val details =
      buildList {
        add(DetailEntry("BSSID", sr.BSSID ?: "—"))
        add(DetailEntry("Signal", "$sanitizedRssi dBm"))
        add(DetailEntry("Frequency", "$freq MHz"))
        if (channel != null) add(DetailEntry("Channel", channel.toString()))
        add(DetailEntry("Width", "$width MHz"))
        if (sr.centerFreq0 > 0 && sr.centerFreq0 != freq) {
          add(DetailEntry("Center 0", "${sr.centerFreq0} MHz"))
        }
        if (sr.centerFreq1 > 0) {
          add(DetailEntry("Center 1", "${sr.centerFreq1} MHz"))
        }
        add(DetailEntry("Security", securityLabel))
        securityRisk(sr.capabilities)?.let { add(DetailEntry("⚠ Risk", it)) }
        add(DetailEntry("WPS", hasWps(sr.capabilities).toString()))
        add(DetailEntry("MFP (802.11w)", mfpStatus(sr.capabilities)))
        add(DetailEntry("802.11mc FTM", sr.is80211mcResponder.toString()))
        if (sr.isPasspointNetwork) add(DetailEntry("Passpoint", "true"))
      }
    return WifiSignal(
      ssid = ssid,
      bssid = sr.BSSID ?: "",
      rssi = sanitizedRssi,
      frequencyMhz = freq,
      band = band,
      channelWidthMhz = width,
      distanceMeters = distance,
      distanceConfidence = confidence,
      isConnected = isConnected,
      details = details,
      firstSeenMs = firstSeenMs,
      securityTypes = parseSecurityTypes(sr.capabilities),
      hasWps = hasWps(sr.capabilities)
    )
  }

  private fun channelWidthMhz(channelWidthConst: Int): Int =
    when (channelWidthConst) {
      ScanResult.CHANNEL_WIDTH_20MHZ -> 20
      ScanResult.CHANNEL_WIDTH_40MHZ -> 40
      ScanResult.CHANNEL_WIDTH_80MHZ -> 80
      ScanResult.CHANNEL_WIDTH_160MHZ -> 160
      ScanResult.CHANNEL_WIDTH_80MHZ_PLUS_MHZ -> 160
      else -> 20
    }

  private fun channelNumber(freqMhz: Int): Int? =
    when {
      freqMhz in 2412..2472 -> (freqMhz - 2407) / 5
      freqMhz == 2484 -> 14
      freqMhz in 5160..5885 -> (freqMhz - 5000) / 5
      freqMhz in 5955..7115 -> (freqMhz - 5950) / 5
      else -> null
    }

  private fun parseSecurityTypes(capabilities: String?): Set<WifiSecurity> {
    val caps = capabilities.orEmpty()
    val types = linkedSetOf<WifiSecurity>()
    if ("WPA3" in caps || "SAE" in caps) types += WifiSecurity.WPA3
    if ("WPA2" in caps || "RSN" in caps) types += WifiSecurity.WPA2
    if ("WPA-" in caps || "WPA/" in caps || "WPA]" in caps) types += WifiSecurity.WPA
    if ("PSK" in caps) types += WifiSecurity.PSK
    if ("EAP" in caps) types += WifiSecurity.EAP
    if ("OWE" in caps) types += WifiSecurity.OWE
    if ("WEP" in caps) types += WifiSecurity.WEP
    if (types.isEmpty() && caps.contains("[ESS]")) types += WifiSecurity.OPEN
    return types
  }

  private fun parseSecurity(capabilities: String?): String {
    val labels = parseSecurityTypes(capabilities).map { it.label }
    return if (labels.isEmpty()) "—" else labels.joinToString(" / ")
  }

  private fun securityRisk(capabilities: String?): String? {
    val caps = capabilities.orEmpty()
    return when {
      "WEP" in caps -> "WEP (broken, trivially crackable)"
      !caps.contains("WPA") && !caps.contains("RSN") && !caps.contains("SAE") && !caps.contains("OWE") -> "Open (unencrypted)"
      "TKIP" in caps -> "TKIP cipher (deprecated)"
      "WPA-" in caps && "WPA2" !in caps && "WPA3" !in caps -> "WPA-original (superseded by WPA2/3)"
      else -> null
    }
  }

  private fun hasWps(capabilities: String?): Boolean = capabilities.orEmpty().let { "[WPS]" in it || "WPS]" in it }

  private fun mfpStatus(capabilities: String?): String {
    val caps = capabilities.orEmpty()
    return when {
      "MFPR" in caps -> "Required"
      "MFPC" in caps -> "Capable"
      else -> "Not advertised"
    }
  }

  private fun sanitizeRssi(raw: Int): Int? {
    if (raw >= 0) return null
    if (raw < -150) return null
    return raw
  }

  private companion object {
    const val EMA_ALPHA = 0.3
    const val MIN_SAMPLES_FOR_DISTANCE = 2
    const val STALE_TTL_MS = 180_000L
    const val WIFI_PROBE_INTERVAL_UNTHROTTLED_MS = 5_000L

    const val WIFI_PROBE_INTERVAL_THROTTLED_MS = 31_000L
    const val WIFI_HEARTBEAT_MS = 5_000L
    const val WIFI_WARMUP_MS = 35_000L
    const val WIFI_STALENESS_MS = 130_000L

    const val MAX_TRACKED_APS = 10_000
  }
}
