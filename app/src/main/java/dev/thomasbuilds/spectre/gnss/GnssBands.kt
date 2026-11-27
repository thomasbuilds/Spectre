package dev.thomasbuilds.spectre.gnss

import kotlin.math.abs

private const val TOLERANCE_MHZ = 10.0
private const val LAST_BEIDOU_2_SVID = 18

private val BANDS =
  mapOf(
    Constellation.GPS to listOf(1575.42 to "L1", 1227.6 to "L2", 1176.45 to "L5"),
    Constellation.GALILEO to listOf(1575.42 to "E1", 1278.75 to "E6", 1207.14 to "E5b", 1191.795 to "E5", 1176.45 to "E5a"),
    Constellation.GLONASS to listOf(1602.0 to "L1", 1246.0 to "L2", 1202.025 to "L3"),
    Constellation.BEIDOU to listOf(1561.098 to "B1I", 1575.42 to "B1C", 1268.52 to "B3", 1207.14 to "B2b", 1176.45 to "B2a"),
    Constellation.QZSS to listOf(1575.42 to "L1", 1227.6 to "L2", 1278.75 to "L6", 1176.45 to "L5"),
    Constellation.IRNSS to listOf(1575.42 to "L1", 1176.45 to "L5", 2492.028 to "S"),
    Constellation.SBAS to listOf(1575.42 to "L1", 1176.45 to "L5")
  )

internal fun bandName(
  constellation: Constellation,
  svid: Int,
  carrierMhz: Double
): String? =
  BANDS[constellation]
    ?.minByOrNull { (mhz, _) -> abs(carrierMhz - mhz) }
    ?.takeIf { (mhz, _) -> abs(carrierMhz - mhz) < TOLERANCE_MHZ }
    ?.second
    ?.let { if (it == "B2b" && svid <= LAST_BEIDOU_2_SVID) "B2I" else it }
