package dev.thomasbuilds.spectre.scanner

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.GnssStatus
import android.location.LocationListener
import android.location.LocationManager
import android.location.LocationRequest
import android.util.Log
import androidx.core.content.ContextCompat
import dev.thomasbuilds.spectre.analysis.CelestialGeometry
import dev.thomasbuilds.spectre.model.Constellation
import dev.thomasbuilds.spectre.model.DetailEntry
import dev.thomasbuilds.spectre.model.GnssSignal
import dev.thomasbuilds.spectre.model.GnssSourceState
import dev.thomasbuilds.spectre.model.ScannerStatus
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.util.concurrent.Executors
import kotlin.math.abs

class GnssScanner(
  private val context: Context
) {
  private val lm: LocationManager? =
    context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager

  @Volatile
  private var lastSatellites: List<GnssSignal> = emptyList()

  @Volatile
  private var registered = false

  private val _state = MutableStateFlow(GnssSourceState())
  val state: StateFlow<GnssSourceState> = _state.asStateFlow()

  private val readiness = ReadinessTracker(GNSS_WARMUP_MS, GNSS_STALENESS_MS)
  private var heartbeatJob: Job? = null

  private val callbackExecutor =
    Executors.newSingleThreadExecutor { r ->
      Thread(r, "spectre-gnss").apply { isDaemon = true }
    }

  private data class RawSatelliteEntry(
    val constellation: Constellation,
    val svid: Int,
    val cn0DbHz: Float,
    val baseband: Float?,
    val elevationDeg: Float,
    val azimuthDeg: Float,
    val usedInFix: Boolean,
    val carrierHz: Float?
  )

  private val callback =
    object : GnssStatus.Callback() {
      override fun onSatelliteStatusChanged(status: GnssStatus) {
        val rawCount = status.satelliteCount
        val raw = ArrayList<RawSatelliteEntry>(rawCount)
        for (i in 0 until rawCount) {
          raw.add(
            RawSatelliteEntry(
              constellation = mapConstellation(status.getConstellationType(i)),
              svid = status.getSvid(i),
              cn0DbHz = status.getCn0DbHz(i),
              baseband = if (status.hasBasebandCn0DbHz(i)) status.getBasebandCn0DbHz(i) else null,
              elevationDeg = status.getElevationDegrees(i),
              azimuthDeg = status.getAzimuthDegrees(i),
              usedInFix = status.usedInFix(i),
              carrierHz = if (status.hasCarrierFrequencyHz(i)) status.getCarrierFrequencyHz(i) else null
            )
          )
        }

        // A dual-frequency satellite appears once per band (L1, L5, ...); group by constellation
        // and svid so it shows as a single row, headlined by its strongest band.
        val groups = raw.groupBy { it.constellation to it.svid }
        val merged =
          groups.values.map { group ->
            val bestBand = group.maxByOrNull { it.cn0DbHz } ?: group.first()
            val bandsByCn0 = group.sortedByDescending { it.cn0DbHz }
            val usedAnyBand = group.any { it.usedInFix }

            val phoneLoc = lastLocation
            val subPoint =
              phoneLoc?.let {
                val satAlt =
                  CelestialGeometry.orbitalAltitudeM(
                    bestBand.constellation,
                    bestBand.svid
                  )
                CelestialGeometry.compute(
                  phoneLatDeg = it.latitude,
                  phoneLonDeg = it.longitude,
                  phoneAltM = if (it.hasAltitude()) it.altitude else 0.0,
                  satElevationDeg = bestBand.elevationDeg,
                  satAzimuthDeg = bestBand.azimuthDeg,
                  satAltitudeAboveEarthM = satAlt
                )
              }

            val details =
              buildList {
                add(DetailEntry("Constellation", bestBand.constellation.label))
                add(DetailEntry("Azimuth", "${"%.1f".format(bestBand.azimuthDeg)}°"))
                add(DetailEntry("Used in fix", usedAnyBand.toString()))
                if (subPoint != null) {
                  add(
                    DetailEntry(
                      "Position",
                      "${formatLat(subPoint.latDeg)}, ${formatLon(subPoint.lonDeg)}"
                    )
                  )
                  add(
                    DetailEntry(
                      "Slant range",
                      "${"%.0f".format(subPoint.slantRangeM / 1000.0)} km"
                    )
                  )
                  add(
                    DetailEntry(
                      "Orbital altitude",
                      "${"%.0f".format(subPoint.altitudeAboveEarthM / 1000.0)} km"
                    )
                  )
                } else if (phoneLoc == null) {
                  add(DetailEntry("Position", "Pending"))
                }
                bandsByCn0.forEachIndexed { idx, b ->
                  val bandLabel =
                    b.carrierHz?.let { gnssBandName(b.constellation, it) }
                      ?: b.carrierHz?.let { "${"%.0f".format(it / 1_000_000f)} MHz" }
                      ?: "Band ${idx + 1}"
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
              azimuthDeg = bestBand.azimuthDeg,
              usedInFix = usedAnyBand,
              details = details
            )
          }
        lastSatellites = merged
        publishNow()
      }
    }

  @Volatile private var lastLocation: android.location.Location? = null
  private val locationListener = LocationListener { loc -> lastLocation = loc }

  private fun gnssBandName(
    constellation: Constellation,
    freqHz: Float
  ): String? {
    fun near(target: Double): Boolean = kotlin.math.abs(freqHz - target) < 10_000_000.0
    return when (constellation) {
      Constellation.GPS -> {
        when {
          near(1_575_420_000.0) -> "L1"
          near(1_227_600_000.0) -> "L2"
          near(1_176_450_000.0) -> "L5"
          else -> null
        }
      }

      Constellation.GALILEO -> {
        when {
          near(1_575_420_000.0) -> "E1"
          near(1_176_450_000.0) -> "E5a"
          near(1_207_140_000.0) -> "E5b"
          else -> null
        }
      }

      Constellation.GLONASS -> {
        when {
          near(1_602_000_000.0) -> "L1"
          near(1_246_000_000.0) -> "L2"
          else -> null
        }
      }

      Constellation.BEIDOU -> {
        when {
          near(1_561_098_000.0) -> "B1I"
          near(1_575_420_000.0) -> "B1C"
          near(1_176_450_000.0) -> "B2a"
          near(1_268_520_000.0) -> "B3"
          else -> null
        }
      }

      Constellation.QZSS -> {
        when {
          near(1_575_420_000.0) -> "L1"
          near(1_176_450_000.0) -> "L5"
          else -> null
        }
      }

      else -> {
        null
      }
    }
  }

  private fun mapConstellation(c: Int): Constellation =
    when (c) {
      GnssStatus.CONSTELLATION_GPS -> Constellation.GPS
      GnssStatus.CONSTELLATION_GLONASS -> Constellation.GLONASS
      GnssStatus.CONSTELLATION_GALILEO -> Constellation.GALILEO
      GnssStatus.CONSTELLATION_BEIDOU -> Constellation.BEIDOU
      GnssStatus.CONSTELLATION_QZSS -> Constellation.QZSS
      GnssStatus.CONSTELLATION_IRNSS -> Constellation.IRNSS
      GnssStatus.CONSTELLATION_SBAS -> Constellation.SBAS
      else -> Constellation.UNKNOWN
    }

  fun hasPermission(): Boolean =
    ContextCompat.checkSelfPermission(
      context,
      Manifest.permission.ACCESS_FINE_LOCATION
    ) == PackageManager.PERMISSION_GRANTED

  fun status(): ScannerStatus {
    if (!hasPermission()) return ScannerStatus.NO_PERMISSION
    if (lm?.isLocationEnabled != true) return ScannerStatus.LOCATION_OFF
    return ScannerStatus.OK
  }

  @SuppressLint("MissingPermission")
  fun start(scope: CoroutineScope) {
    if (!registered && hasPermission()) {
      val lm = lm
      if (lm != null) {
        runCatching {
          val request =
            LocationRequest
              .Builder(GPS_INTERVAL_MS)
              .setMinUpdateDistanceMeters(0f)
              .setQuality(LocationRequest.QUALITY_HIGH_ACCURACY)
              .build()
          lm.requestLocationUpdates(
            LocationManager.GPS_PROVIDER,
            request,
            callbackExecutor,
            locationListener
          )
          lm.registerGnssStatusCallback(callbackExecutor, callback)
        }.onSuccess {
          registered = true
        }.onFailure {
          Log.w(TAG, "Failed to engage GPS", it)
        }
      }
    }
    if (heartbeatJob?.isActive != true) {
      heartbeatJob =
        scope.launch {
          while (isActive) {
            delay(GNSS_HEARTBEAT_MS)
            publishNow()
          }
        }
    }
    publishNow()
  }

  fun stop() {
    if (!registered) return
    runCatching {
      lm?.unregisterGnssStatusCallback(callback)
      lm?.removeUpdates(locationListener)
    }
    registered = false
  }

  private fun publishNow() {
    val status = status()
    val signals = if (status == ScannerStatus.OK) lastSatellites else emptyList()
    val now = System.currentTimeMillis()
    val ready = readiness.compute(status == ScannerStatus.OK, signals.isNotEmpty(), now)
    _state.value =
      GnssSourceState(
        signals = signals,
        status = status,
        ready = ready
      )
  }

  private fun formatLat(deg: Double): String {
    val hemi = if (deg >= 0.0) "N" else "S"
    return "${"%.2f".format(abs(deg))}°$hemi"
  }

  private fun formatLon(deg: Double): String {
    val hemi = if (deg >= 0.0) "E" else "W"
    return "${"%.2f".format(abs(deg))}°$hemi"
  }

  private companion object {
    const val GPS_INTERVAL_MS = 2_000L
    const val TAG = "GnssScanner"
    const val GNSS_HEARTBEAT_MS = 5_000L
    const val GNSS_WARMUP_MS = 45_000L
    const val GNSS_STALENESS_MS = 20_000L
  }
}
