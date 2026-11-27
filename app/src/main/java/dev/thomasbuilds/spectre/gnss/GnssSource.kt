package dev.thomasbuilds.spectre.gnss

import android.annotation.SuppressLint
import android.content.Context
import android.location.GnssMeasurementsEvent
import android.location.GnssStatus
import android.location.LocationListener
import android.location.LocationManager
import android.location.LocationRequest
import android.os.SystemClock
import dev.thomasbuilds.spectre.core.Source
import dev.thomasbuilds.spectre.core.SourceState
import dev.thomasbuilds.spectre.core.SourceStatus
import dev.thomasbuilds.spectre.core.callbackExecutor
import dev.thomasbuilds.spectre.core.hasPermissions
import dev.thomasbuilds.spectre.core.isLocationEnabled
import dev.thomasbuilds.spectre.core.statusFlow
import dev.thomasbuilds.spectre.core.whileOk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.launch

private const val WARMUP_MS = 45_000L
private const val GPS_INTERVAL_MS = 2_000L
private const val PUBLISH_INTERVAL_MS = 5_000L
private const val STALE_AFTER_MS = 20_000L
private const val RANGE_RATE_MAX_AGE_MS = 5_000L
private const val NO_DRIFT_EPOCHS_UNTIL_UNAVAILABLE = 8
private const val SPEED_OF_LIGHT_MPS = 299_792_458.0

class GnssSource(
  private val context: Context
) {
  private val locationManager = checkNotNull(context.getSystemService(LocationManager::class.java))
  private val lane = Dispatchers.IO.limitedParallelism(1)
  private val rangeRates by lazy { RangeRates().takeIf { locationManager.gnssCapabilities.hasMeasurements() } }

  fun states(): Flow<SourceState<GnssSignal>> =
    context
      .statusFlow { status() }
      .whileOk(WARMUP_MS) { satellites() }
      .flowOn(lane)

  private fun status(): SourceStatus =
    when {
      !context.isLocationEnabled -> SourceStatus.LOCATION_OFF
      !context.hasPermissions(Source.GNSS.permissions) -> SourceStatus.NO_PERMISSION
      !locationManager.hasProvider(LocationManager.GPS_PROVIDER) -> SourceStatus.UNSUPPORTED
      else -> SourceStatus.OK
    }

  @SuppressLint("MissingPermission")
  private fun satellites(): Flow<SourceState<GnssSignal>> =
    channelFlow {
      val executor = callbackExecutor()
      var satellites = emptyList<GnssSignal>()
      var lastEpochMs = 0L

      fun publish() {
        val fresh = SystemClock.elapsedRealtime() - lastEpochMs <= STALE_AFTER_MS
        trySend(SourceState(signals = if (fresh) satellites else emptyList()))
      }

      val statusCallback =
        object : GnssStatus.Callback() {
          override fun onSatelliteStatusChanged(status: GnssStatus) {
            satellites = status.toSignals()
            lastEpochMs = SystemClock.elapsedRealtime()
            publish()
          }
        }
      val locationListener = LocationListener { }
      val request = LocationRequest.Builder(GPS_INTERVAL_MS).setQuality(LocationRequest.QUALITY_HIGH_ACCURACY).build()
      locationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER, request, executor, locationListener)
      locationManager.registerGnssStatusCallback(executor, statusCallback)
      rangeRates?.let { locationManager.registerGnssMeasurementsCallback(executor, it) }
      launch {
        while (true) {
          delay(PUBLISH_INTERVAL_MS)
          publish()
        }
      }
      awaitClose {
        locationManager.removeUpdates(locationListener)
        locationManager.unregisterGnssStatusCallback(statusCallback)
        rangeRates?.let(locationManager::unregisterGnssMeasurementsCallback)
      }
    }

  private data class SatelliteId(
    val constellation: Constellation,
    val svid: Int
  )

  private class RangeRates : GnssMeasurementsEvent.Callback() {
    private val readings = HashMap<SatelliteId, Pair<Float, Long>>()
    private var driftSeen = false
    private var epochsWithoutDrift = 0

    val unavailable: Boolean get() = !driftSeen && epochsWithoutDrift >= NO_DRIFT_EPOCHS_UNTIL_UNAVAILABLE

    override fun onGnssMeasurementsReceived(event: GnssMeasurementsEvent) {
      val clock = event.clock
      if (!clock.hasDriftNanosPerSecond()) {
        epochsWithoutDrift = if (clock.hasFullBiasNanos()) epochsWithoutDrift + 1 else 0
        return
      }
      driftSeen = true
      epochsWithoutDrift = 0
      val driftMps = clock.driftNanosPerSecond * 1e-9 * SPEED_OF_LIGHT_MPS
      val now = SystemClock.elapsedRealtime()
      event.measurements.forEach {
        readings[SatelliteId(constellationOf(it.constellationType), it.svid)] =
          (it.pseudorangeRateMetersPerSecond - driftMps).toFloat() to now
      }
    }

    fun metersPerSecond(
      id: SatelliteId,
      now: Long
    ): Float? = readings[id]?.takeIf { (_, at) -> now - at <= RANGE_RATE_MAX_AGE_MS }?.first
  }

  private fun GnssStatus.toSignals(): List<GnssSignal> {
    val now = SystemClock.elapsedRealtime()
    return (0..<satelliteCount)
      .filter { getCn0DbHz(it) > 0f || usedInFix(it) }
      .groupBy { SatelliteId(constellationOf(getConstellationType(it)), getSvid(it)) }
      .map { (id, indices) ->
        val bands = indices.sortedByDescending(::getCn0DbHz)
        val geometry = bands.firstOrNull { getElevationDegrees(it) != 0f || getAzimuthDegrees(it) != 0f }
        GnssSignal(
          constellation = id.constellation,
          svid = id.svid,
          cn0DbHz = getCn0DbHz(bands.first()),
          elevationDeg = geometry?.let(::getElevationDegrees),
          azimuthDeg = geometry?.let(::getAzimuthDegrees),
          isRangeRateSupported = rangeRates?.unavailable == false,
          rangeRateMps = rangeRates?.metersPerSecond(id, now),
          hasEphemeris = bands.any(::hasEphemerisData),
          hasAlmanac = bands.any(::hasAlmanacData),
          bands = bands.map { band(id, it) }
        )
      }
  }

  private fun GnssStatus.band(
    id: SatelliteId,
    index: Int
  ): GnssBandReading {
    val carrierMhz = if (hasCarrierFrequencyHz(index)) getCarrierFrequencyHz(index) / 1e6 else null
    return GnssBandReading(
      name = carrierMhz?.let { bandName(id.constellation, id.svid, it) },
      carrierMhz = carrierMhz?.toFloat(),
      cn0DbHz = getCn0DbHz(index),
      basebandCn0DbHz = if (hasBasebandCn0DbHz(index)) getBasebandCn0DbHz(index) else null,
      usedInFix = usedInFix(index)
    )
  }
}

private fun constellationOf(type: Int): Constellation =
  when (type) {
    GnssStatus.CONSTELLATION_GPS -> Constellation.GPS
    GnssStatus.CONSTELLATION_GLONASS -> Constellation.GLONASS
    GnssStatus.CONSTELLATION_GALILEO -> Constellation.GALILEO
    GnssStatus.CONSTELLATION_BEIDOU -> Constellation.BEIDOU
    GnssStatus.CONSTELLATION_QZSS -> Constellation.QZSS
    GnssStatus.CONSTELLATION_IRNSS -> Constellation.IRNSS
    GnssStatus.CONSTELLATION_SBAS -> Constellation.SBAS
    else -> Constellation.UNKNOWN
  }
