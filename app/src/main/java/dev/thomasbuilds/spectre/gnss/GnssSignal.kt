package dev.thomasbuilds.spectre.gnss

import androidx.compose.runtime.Immutable

enum class Constellation(
  val label: String,
  val shortLabel: String
) {
  GPS("GPS", "GPS"),
  GLONASS("GLONASS", "GLO"),
  GALILEO("Galileo", "GAL"),
  BEIDOU("BeiDou", "BDS"),
  QZSS("QZSS", "QZS"),
  IRNSS("NavIC", "IRN"),
  SBAS("SBAS", "SBS"),
  UNKNOWN("?", "?")
}

data class GnssBandReading(
  val name: String?,
  val carrierMhz: Float?,
  val cn0DbHz: Float,
  val basebandCn0DbHz: Float?,
  val usedInFix: Boolean
)

@Immutable
data class GnssSignal(
  val constellation: Constellation,
  val svid: Int,
  val cn0DbHz: Float,
  val elevationDeg: Float?,
  val azimuthDeg: Float?,
  val isRangeRateSupported: Boolean,
  val rangeRateMps: Float?,
  val hasEphemeris: Boolean,
  val hasAlmanac: Boolean,
  val bands: List<GnssBandReading>
) {
  val key: String get() = "${constellation.name}-$svid"
}
