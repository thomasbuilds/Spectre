package dev.thomasbuilds.spectre.scanner.gnss

import android.location.GnssStatus
import dev.thomasbuilds.spectre.model.Constellation
import kotlin.math.abs

internal object GnssBands {
  fun constellationFor(type: Int): Constellation =
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

  private const val TOLERANCE_MHZ = 10.0

  // List order is match precedence where tolerance windows overlap (E5b before E5).
  private val bands =
    mapOf(
      Constellation.GPS to listOf(1575.42 to "L1", 1227.6 to "L2", 1176.45 to "L5"),
      Constellation.GALILEO to listOf(1575.42 to "E1", 1278.75 to "E6", 1207.14 to "E5b", 1191.795 to "E5", 1176.45 to "E5a"),
      Constellation.GLONASS to listOf(1602.0 to "L1", 1246.0 to "L2", 1202.025 to "L3"),
      Constellation.BEIDOU to listOf(1561.098 to "B1I", 1575.42 to "B1C", 1268.52 to "B3", 1207.14 to "B2b", 1176.45 to "B2a"),
      Constellation.QZSS to listOf(1575.42 to "L1", 1227.6 to "L2", 1278.75 to "L6", 1176.45 to "L5"),
      Constellation.IRNSS to listOf(1575.42 to "L1", 1176.45 to "L5", 2492.028 to "S"),
      Constellation.SBAS to listOf(1575.42 to "L1", 1176.45 to "L5")
    )

  fun bandName(
    constellation: Constellation,
    freqHz: Float
  ): String? = bands[constellation]?.firstOrNull { (mhz, _) -> abs(freqHz / 1e6 - mhz) < TOLERANCE_MHZ }?.second
}
