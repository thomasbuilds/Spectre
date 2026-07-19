package dev.thomasbuilds.spectre.scanner.gnss

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.location.GnssMeasurementsEvent
import android.location.GnssStatus
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.location.LocationRequest
import android.os.SystemClock
import android.util.Log
import dev.thomasbuilds.spectre.analysis.CelestialGeometry
import dev.thomasbuilds.spectre.hasPermission
import dev.thomasbuilds.spectre.model.Constellation
import dev.thomasbuilds.spectre.model.DetailEntry
import dev.thomasbuilds.spectre.model.GnssSignal
import dev.thomasbuilds.spectre.model.GnssSourceState
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
import kotlin.math.abs

class GnssScanner(
  private val context: Context
) {
  private val lm: LocationManager? =
    context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager

  @Volatile private var lastSatellites: List<GnssSignal> = emptyList()

  @Volatile private var lastLocation: Location? = null

  @Volatile private var lastStatusAtMs = 0L

  @Volatile private var driftSeen = false

  @Volatile private var syncedNoDriftEpochs = 0

  @Volatile private var registered = false

  @Volatile private var stopped = false

  private val rangeRates = ConcurrentHashMap<Pair<Constellation, Int>, RangeReading>()

  // Chipset constant, avoids a binder call per status epoch.
  private val measurementsSupported by lazy {
    runCatching { lm?.gnssCapabilities?.hasMeasurements() != false }.getOrDefault(true)
  }

  private val _state = MutableStateFlow(GnssSourceState())
  val state: StateFlow<GnssSourceState> = _state.asStateFlow()

  private val readiness = ReadinessTracker(GNSS_WARMUP_MS)
  private var heartbeatJob: Job? = null

  private val callbackExecutor = daemonExecutor("spectre-gnss")

  private data class RangeReading(
    val metersPerSecond: Float,
    val atMs: Long
  )

  private data class RawSatelliteEntry(
    val constellation: Constellation,
    val svid: Int,
    val cn0DbHz: Float,
    val baseband: Float?,
    val elevationDeg: Float,
    val azimuthDeg: Float,
    val usedInFix: Boolean,
    val hasEphemeris: Boolean,
    val hasAlmanac: Boolean,
    val carrierHz: Float?
  )

  private val callback =
    object : GnssStatus.Callback() {
      override fun onSatelliteStatusChanged(status: GnssStatus) {
        val now = SystemClock.elapsedRealtime()
        // The HAL also lists satellites it is merely searching for, reporting C/N0 as 0 with no
        // flag to tell them apart. Only tracked satellites are received signals; keep those.
        val raw =
          List(status.satelliteCount) { i ->
            RawSatelliteEntry(
              constellation = GnssBands.constellationFor(status.getConstellationType(i)),
              svid = status.getSvid(i),
              cn0DbHz = status.getCn0DbHz(i),
              baseband = if (status.hasBasebandCn0DbHz(i)) status.getBasebandCn0DbHz(i) else null,
              elevationDeg = status.getElevationDegrees(i),
              azimuthDeg = status.getAzimuthDegrees(i),
              usedInFix = status.usedInFix(i),
              hasEphemeris = status.hasEphemerisData(i),
              hasAlmanac = status.hasAlmanacData(i),
              carrierHz = if (status.hasCarrierFrequencyHz(i)) status.getCarrierFrequencyHz(i) else null
            )
          }.filter { it.cn0DbHz > 0f || it.usedInFix }

        val rateUnavailable =
          !measurementsSupported || (!driftSeen && syncedNoDriftEpochs >= SYNCED_NO_DRIFT_EPOCHS)
        val merged =
          raw.groupBy { it.constellation to it.svid }.values.map { group ->
            val bandsByCn0 = group.sortedByDescending { it.cn0DbHz }
            val bestBand = bandsByCn0.first()

            // Elevation/azimuth stay at 0 until the receiver knows the orbit; exactly (0°, 0°)
            // from a live satellite means "not computed yet", not a bearing.
            val geometryKnown = bestBand.elevationDeg != 0f || bestBand.azimuthDeg != 0f
            val phoneLoc = lastLocation
            val subPoint =
              phoneLoc?.takeIf { geometryKnown }?.let {
                CelestialGeometry.compute(
                  phoneLatDeg = it.latitude,
                  phoneLonDeg = it.longitude,
                  phoneAltM = if (it.hasAltitude()) it.altitude else 0.0,
                  satElevationDeg = bestBand.elevationDeg,
                  satAzimuthDeg = bestBand.azimuthDeg,
                  satAltitudeAboveEarthM = CelestialGeometry.orbitalAltitudeM(bestBand.constellation, bestBand.svid)
                )
              }

            val details =
              buildList {
                add(DetailEntry("Constellation", bestBand.constellation.label))
                add(DetailEntry("Elevation", if (geometryKnown) "${"%.1f".format(bestBand.elevationDeg)}°" else "Unknown"))
                add(DetailEntry("Azimuth", if (geometryKnown) "${"%.1f".format(bestBand.azimuthDeg)}°" else "Unknown"))
                val rateText =
                  if (rateUnavailable) {
                    "Unavailable"
                  } else {
                    rangeRates[bestBand.constellation to bestBand.svid]
                      ?.takeIf { now - it.atMs <= RANGE_RATE_STALE_MS }
                      ?.let { reading ->
                        val direction = if (reading.metersPerSecond < 0f) "approaching" else "receding"
                        "${"%.0f".format(abs(reading.metersPerSecond))} m/s ($direction)"
                      } ?: "Pending"
                  }
                add(DetailEntry("Range rate", rateText))
                if (subPoint != null) {
                  add(DetailEntry("Position", "${formatDeg(subPoint.latDeg, 'N', 'S')}, ${formatDeg(subPoint.lonDeg, 'E', 'W')}"))
                  add(DetailEntry("Slant range", "${"%.0f".format(subPoint.slantRangeM / 1000.0)} km"))
                  add(DetailEntry("Orbital altitude", "${"%.0f".format(subPoint.altitudeAboveEarthM / 1000.0)} km"))
                } else {
                  val positionText =
                    when {
                      !geometryKnown -> "Unknown"
                      phoneLoc == null -> "Pending"
                      else -> "Below horizon"
                    }
                  add(DetailEntry("Position", positionText))
                }
                add(DetailEntry("Ephemeris data", if (bestBand.hasEphemeris) "Yes" else "No"))
                add(DetailEntry("Almanac data", if (bestBand.hasAlmanac) "Yes" else "No"))
                bandsByCn0.forEachIndexed { idx, b ->
                  val bandLabel =
                    b.carrierHz?.let { hz ->
                      GnssBands.bandName(b.constellation, hz) ?: "${"%.0f".format(hz / 1_000_000f)} MHz"
                    } ?: "Band ${idx + 1}"
                  val basebandStr = b.baseband?.let { " (baseband ${"%.1f".format(it)})" } ?: ""
                  val usedStr = if (b.usedInFix) " · in fix" else ""
                  add(DetailEntry(bandLabel, "${"%.1f".format(b.cn0DbHz)} dB-Hz$basebandStr$usedStr"))
                }
              }

            GnssSignal(
              constellation = bestBand.constellation,
              svid = bestBand.svid,
              cn0DbHz = bestBand.cn0DbHz,
              elevationDeg = bestBand.elevationDeg,
              details = details
            )
          }
        lastSatellites = merged
        lastStatusAtMs = now
        publishNow()
      }
    }

  private val measurementsCallback =
    object : GnssMeasurementsEvent.Callback() {
      override fun onGnssMeasurementsReceived(event: GnssMeasurementsEvent) {
        val clock = event.clock
        if (!clock.hasDriftNanosPerSecond()) {
          syncedNoDriftEpochs = if (clock.hasFullBiasNanos()) syncedNoDriftEpochs + 1 else 0
          return
        }
        driftSeen = true
        syncedNoDriftEpochs = 0
        val driftMps = clock.driftNanosPerSecond * 1e-9 * SPEED_OF_LIGHT_MPS
        val now = SystemClock.elapsedRealtime()
        for (m in event.measurements) {
          rangeRates[GnssBands.constellationFor(m.constellationType) to m.svid] =
            RangeReading((m.pseudorangeRateMetersPerSecond - driftMps).toFloat(), now)
        }
      }
    }

  private val locationListener = LocationListener { loc -> lastLocation = loc }

  fun hasPermission(): Boolean = context.hasPermission(Manifest.permission.ACCESS_FINE_LOCATION)

  fun status(): ScannerStatus {
    if (!hasPermission()) return ScannerStatus.NO_PERMISSION
    if (lm?.isLocationEnabled != true) return ScannerStatus.LOCATION_OFF
    return ScannerStatus.OK
  }

  fun start(scope: CoroutineScope) {
    maybeRegister()
    if (heartbeatJob?.isActive != true) {
      // Also retries registration, a permission granted after service start never re-invokes start().
      heartbeatJob =
        scope.repeatEvery(GNSS_HEARTBEAT_MS) {
          maybeRegister()
          publishNow()
        }
    }
    publishNow()
  }

  @Synchronized
  @SuppressLint("MissingPermission")
  private fun maybeRegister() {
    if (stopped || registered || !hasPermission()) return
    lm
      ?.runCatching {
        val request =
          LocationRequest
            .Builder(GPS_INTERVAL_MS)
            .setMinUpdateDistanceMeters(0f)
            .setQuality(LocationRequest.QUALITY_HIGH_ACCURACY)
            .build()
        if (lastLocation == null) lastLocation = getLastKnownLocation(LocationManager.FUSED_PROVIDER)
        requestLocationUpdates(LocationManager.GPS_PROVIDER, request, callbackExecutor, locationListener)
        registerGnssStatusCallback(callbackExecutor, callback)
        registerGnssMeasurementsCallback(callbackExecutor, measurementsCallback)
      }?.onSuccess { registered = true }
      ?.onFailure { Log.w(TAG, "Failed to engage GPS", it) }
  }

  // Synchronized with maybeRegister so an in-flight registration can't complete after teardown.
  @Synchronized
  fun stop() {
    stopped = true
    lm?.runCatching {
      unregisterGnssStatusCallback(callback)
      unregisterGnssMeasurementsCallback(measurementsCallback)
      removeUpdates(locationListener)
    }
    rangeRates.clear()
    driftSeen = false
    syncedNoDriftEpochs = 0
    registered = false
  }

  private fun publishNow() {
    val status = status()
    val now = SystemClock.elapsedRealtime()
    // Gate on callback recency. A silent chip's last list would otherwise republish as live forever.
    val fresh = now - lastStatusAtMs <= GNSS_STALENESS_MS
    val signals = if (status == ScannerStatus.OK && fresh) lastSatellites else emptyList()
    _state.value = GnssSourceState(signals, status, readiness.compute(status == ScannerStatus.OK, signals.isNotEmpty(), now))
  }

  private fun formatDeg(
    deg: Double,
    pos: Char,
    neg: Char
  ): String = "${"%.2f".format(abs(deg))}°${if (deg >= 0.0) pos else neg}"

  private companion object {
    const val GPS_INTERVAL_MS = 2_000L
    const val TAG = "GnssScanner"
    const val GNSS_HEARTBEAT_MS = 5_000L
    const val GNSS_WARMUP_MS = 45_000L
    const val GNSS_STALENESS_MS = 20_000L
    const val RANGE_RATE_STALE_MS = 5_000L
    const val SYNCED_NO_DRIFT_EPOCHS = 8
    const val SPEED_OF_LIGHT_MPS = 299_792_458.0
  }
}
