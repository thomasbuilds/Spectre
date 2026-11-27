package dev.thomasbuilds.spectre.wifi

import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.net.wifi.ScanResult
import android.net.wifi.WifiManager
import android.net.wifi.rtt.WifiRttManager
import android.os.Build
import android.os.PowerManager
import android.os.SystemClock
import android.provider.Settings
import dev.thomasbuilds.spectre.core.OuiLookup
import dev.thomasbuilds.spectre.core.Source
import dev.thomasbuilds.spectre.core.SourceState
import dev.thomasbuilds.spectre.core.SourceStatus
import dev.thomasbuilds.spectre.core.callbackExecutor
import dev.thomasbuilds.spectre.core.hasPermissions
import dev.thomasbuilds.spectre.core.isLocationEnabled
import dev.thomasbuilds.spectre.core.smoothRssi
import dev.thomasbuilds.spectre.core.statusFlow
import dev.thomasbuilds.spectre.core.ticker
import dev.thomasbuilds.spectre.core.whileOk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import java.util.TreeMap
import kotlin.math.log10
import kotlin.math.pow

private const val WARMUP_MS = 35_000L
private const val PUBLISH_INTERVAL_MS = 5_000L
private const val THROTTLED_SCAN_INTERVAL_MS = 120_000L / 4 + 1_000L
private const val UNTHROTTLED_SCAN_INTERVAL_MS = 5_000L
private const val STALE_AFTER_MISSED_SCANS = 3
private const val MIN_SAMPLES_FOR_DISTANCE = 2
private const val MAX_TRACKED_ACCESS_POINTS = 10_000
private const val REDACTED_BSSID = "02:00:00:00:00:00"
private const val ASSUMED_EIRP_DBM = 20.0
private const val MIN_DISTANCE_M = 0.1
private val VALID_LINK_RSSI_DBM = -126..-1

class WifiSource(
  private val context: Context,
  private val oui: OuiLookup
) {
  private val wifi: WifiManager? = context.getSystemService(WifiManager::class.java)
  private val rtt: WifiRttManager? =
    context
      .getSystemService(WifiRttManager::class.java)
      ?.takeIf { context.packageManager.hasSystemFeature(PackageManager.FEATURE_WIFI_RTT) }
  private val power = checkNotNull(context.getSystemService(PowerManager::class.java))
  private val lane = Dispatchers.IO.limitedParallelism(1)

  fun states(): Flow<SourceState<WifiSignal>> =
    flow {
      val history = History()
      emitAll(
        context
          .statusFlow(WifiManager.WIFI_STATE_CHANGED_ACTION, WifiManager.ACTION_WIFI_SCAN_AVAILABILITY_CHANGED) { status() }
          .whileOk(WARMUP_MS, hasData = { signals -> signals.any { !it.isStale } }) { session(checkNotNull(wifi), history) }
      )
    }.flowOn(lane)

  fun throttled(): Flow<Boolean> =
    ticker(PUBLISH_INTERVAL_MS)
      .map { wifi?.isScanThrottleEnabled != false }
      .distinctUntilChanged()
      .flowOn(lane)

  private fun status(): SourceStatus =
    when {
      !context.isLocationEnabled -> SourceStatus.LOCATION_OFF
      !context.hasPermissions(Source.WIFI.permissions) -> SourceStatus.NO_PERMISSION
      wifi == null -> SourceStatus.UNSUPPORTED
      !wifi.canScan(context) -> SourceStatus.RADIO_OFF
      else -> SourceStatus.OK
    }

  private class AccessPoint(
    val signal: WifiSignal,
    val smoothedRssi: Double,
    val samples: Int,
    val lastSeenScan: Int,
    val timestampMicros: Long
  )

  private inner class History {
    val ranger = rtt?.let(::WifiRttRanger)
    private val accessPoints = LinkedHashMap<String, AccessPoint>()
    private var currentScan = 0

    fun restart() {
      currentScan += STALE_AFTER_MISSED_SCANS
    }

    fun record(results: List<ScanResult>) {
      currentScan++
      val seenAtMs = SystemClock.elapsedRealtime()
      results.forEach { store(it, seenAtMs) }
    }

    fun signals(connected: Map<String, Int?>): List<WifiSignal> = accessPoints.values.map { it.toSignal(connected) }

    private fun store(
      result: ScanResult,
      seenAtMs: Long
    ) {
      val bssid = result.BSSID ?: return
      val rssi = result.level.takeIf { it < 0 && it != Int.MIN_VALUE } ?: return
      val previous = accessPoints[bssid]
      if (previous != null && result.timestamp > 0L && result.timestamp <= previous.timestampMicros) return
      accessPoints.remove(bssid)
      accessPoints[bssid] =
        AccessPoint(
          signal = result.toSignal(previous?.signal?.firstSeenMs ?: seenAtMs),
          smoothedRssi = smoothRssi(previous?.smoothedRssi, rssi),
          samples = (previous?.samples ?: 0) + 1,
          lastSeenScan = currentScan,
          timestampMicros = result.timestamp
        )
      while (accessPoints.size > MAX_TRACKED_ACCESS_POINTS) accessPoints.remove(accessPoints.keys.first())
    }

    private fun AccessPoint.toSignal(connected: Map<String, Int?>): WifiSignal {
      val liveRssi = connected[signal.bssid]
      return signal.copy(
        rssi = liveRssi ?: signal.rssi,
        isRssiLive = liveRssi != null,
        distanceMeters =
          ranger?.distanceMeters(signal.bssid)
            ?: if (samples < MIN_SAMPLES_FOR_DISTANCE) null else wifiDistanceMeters(smoothedRssi, signal.frequencyMhz, signal.band),
        isConnected = signal.bssid in connected,
        isStale = currentScan - lastSeenScan >= STALE_AFTER_MISSED_SCANS
      )
    }
  }

  @SuppressLint("MissingPermission")
  private fun session(
    wifi: WifiManager,
    history: History
  ): Flow<SourceState<WifiSignal>> =
    channelFlow {
      val executor = callbackExecutor()
      val ranger = history.ranger
      var ranging: Job? = null
      history.restart()

      fun publish() {
        trySend(SourceState(signals = history.signals(wifi.connectedLinks(withLiveRssi = power.isInteractive))))
      }

      fun ingest() {
        val results = wifi.scanResults
        history.record(results)
        if (ranger != null && ranging?.isActive != true) {
          ranging =
            launch {
              if (ranger.range(results, executor)) publish()
            }
        }
      }

      val callback =
        object : WifiManager.ScanResultsCallback() {
          override fun onScanResultsAvailable() {
            ingest()
            publish()
          }
        }
      wifi.registerScanResultsCallback(executor, callback)
      ingest()
      publish()
      launch {
        while (true) {
          @Suppress("DEPRECATION")
          wifi.startScan()
          delay(if (wifi.isScanThrottleEnabled) THROTTLED_SCAN_INTERVAL_MS else UNTHROTTLED_SCAN_INTERVAL_MS)
        }
      }
      launch {
        while (true) {
          delay(PUBLISH_INTERVAL_MS)
          publish()
        }
      }
      awaitClose { wifi.unregisterScanResultsCallback(callback) }
    }

  private fun ScanResult.toSignal(firstSeenMs: Long): WifiSignal {
    val parsed = parseCapabilities(capabilities.orEmpty())
    return WifiSignal(
      bssid = BSSID,
      ssid = @Suppress("DEPRECATION") SSID?.takeIf { it.isNotBlank() },
      vendor = oui.vendor(BSSID),
      rssi = level,
      isRssiLive = false,
      band = WifiBand.of(frequency),
      frequencyMhz = frequency,
      channel = ScanResult.convertFrequencyMhzToChannelIfSupported(frequency).takeIf { it != ScanResult.UNSPECIFIED },
      widthMhz =
        when (channelWidth) {
          ScanResult.CHANNEL_WIDTH_40MHZ -> 40
          ScanResult.CHANNEL_WIDTH_80MHZ -> 80
          ScanResult.CHANNEL_WIDTH_160MHZ, ScanResult.CHANNEL_WIDTH_80MHZ_PLUS_MHZ -> 160
          ScanResult.CHANNEL_WIDTH_320MHZ -> 320
          else -> 20
        },
      centerFrequency0Mhz = centerFreq0.takeIf { it > 0 && it != frequency },
      centerFrequency1Mhz = centerFreq1.takeIf { it > 0 },
      security = parsed.security,
      ciphers = parsed.ciphers,
      hasWps = parsed.hasWps,
      managementFrameProtection = parsed.managementFrameProtection,
      isFtmResponder = isFtmResponder,
      isPasspoint = isPasspointNetwork,
      distanceMeters = null,
      isConnected = false,
      firstSeenMs = firstSeenMs,
      isStale = false
    )
  }
}

@Suppress("DEPRECATION")
private fun WifiManager.canScan(context: Context): Boolean = isWifiEnabled || (isScanAlwaysAvailable && !context.isAirplaneModeOnForWifi)

private val Context.isAirplaneModeOnForWifi: Boolean
  get() =
    Settings.Global.getInt(contentResolver, Settings.Global.AIRPLANE_MODE_ON, 0) != 0 &&
      Settings.Global.getString(contentResolver, Settings.Global.AIRPLANE_MODE_RADIOS)?.contains(Settings.Global.RADIO_WIFI) != false

@SuppressLint("MissingPermission")
@Suppress("DEPRECATION")
private fun WifiManager.connectedLinks(withLiveRssi: Boolean): Map<String, Int?> {
  val info = connectionInfo ?: return emptyMap()

  fun Int.live(): Int? = takeIf { withLiveRssi && it in VALID_LINK_RSSI_DBM }

  val links =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
      info.associatedMloLinks.mapNotNull { link -> link.apMacAddress?.let { it.toString() to link.rssi.live() } }
    } else {
      emptyList()
    }
  val connected = links.ifEmpty { listOfNotNull(info.bssid?.takeIf { it != REDACTED_BSSID }?.let { it to info.rssi.live() }) }
  return connected.toMap(TreeMap(String.CASE_INSENSITIVE_ORDER))
}

private val WifiBand.pathLossExponent: Double
  get() =
    when (this) {
      WifiBand.GHZ_2_4 -> 4.0
      WifiBand.GHZ_5 -> 4.5
      WifiBand.GHZ_6 -> 5.0
    }

private fun wifiDistanceMeters(
  rssi: Double,
  frequencyMhz: Int,
  band: WifiBand
): Double {
  val freeSpaceLossAtOneMeterDb = 20 * log10(frequencyMhz.toDouble()) - 27.55
  return 10.0.pow((ASSUMED_EIRP_DBM - rssi - freeSpaceLossAtOneMeterDb) / (10 * band.pathLossExponent)).coerceAtLeast(MIN_DISTANCE_M)
}
