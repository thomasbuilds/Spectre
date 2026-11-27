package dev.thomasbuilds.spectre.gnss

import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.runtime.Composable
import dev.thomasbuilds.spectre.ui.list.Detail
import dev.thomasbuilds.spectre.ui.list.DetailTable
import dev.thomasbuilds.spectre.ui.list.ListRows
import dev.thomasbuilds.spectre.ui.list.SignalRowHeader
import dev.thomasbuilds.spectre.ui.list.signalList
import java.util.Locale
import kotlin.math.abs

private const val GLONASS_FCN_SVID_OFFSET = 100
private val GLONASS_FCN_SVIDS = 93..106

internal fun gnssRows(signals: List<GnssSignal>): ListRows<GnssSignal> = ListRows(signals.sortedByDescending { it.cn0DbHz }, signals.size)

internal fun LazyListScope.gnssList(
  rows: ListRows<GnssSignal>,
  expandedKey: String?,
  onToggle: (String) -> Unit
) = signalList(
  title = "GNSS Satellites",
  rows = rows,
  key = { it.key },
  expandedKey = expandedKey,
  onToggle = onToggle,
  header = { GnssHeader(it) },
  details = { DetailTable(it.details()) }
)

@Composable
private fun GnssHeader(signal: GnssSignal) {
  val title =
    if (signal.constellation == Constellation.GLONASS && signal.svid in GLONASS_FCN_SVIDS) {
      "FCN ${signal.svid - GLONASS_FCN_SVID_OFFSET}"
    } else {
      "SV ${signal.svid}"
    }
  SignalRowHeader(signal.constellation.shortLabel, title, listOf("${signal.cn0DbHz.toInt()} dBHz"))
}

private fun GnssSignal.details(): List<Detail> =
  listOf(
    Detail("Constellation", constellation.label),
    Detail("Elevation", elevationDeg.formatAngle()),
    Detail("Azimuth", azimuthDeg.formatAngle()),
    Detail(
      "Range rate",
      when {
        !isRangeRateSupported -> "Unavailable"
        rangeRateMps == null -> "Pending"
        else -> "${abs(rangeRateMps).format(0)} m/s (${if (rangeRateMps < 0f) "approaching" else "receding"})"
      }
    ),
    Detail("Ephemeris data", if (hasEphemeris) "Yes" else "No"),
    Detail("Almanac data", if (hasAlmanac) "Yes" else "No")
  ) + bands.mapIndexed { index, band -> band.toDetail(index) }

private fun GnssBandReading.toDetail(index: Int): Detail {
  val label = name ?: carrierMhz?.let { "${it.format(0)} MHz" } ?: "Band ${index + 1}"
  val baseband = basebandCn0DbHz?.let { " (baseband ${it.format(1)})" }.orEmpty()
  val fix = if (usedInFix) " · in fix" else ""
  return Detail(label, "${cn0DbHz.format(1)} dB-Hz$baseband$fix")
}

private fun Float?.formatAngle(): String = this?.let { "${it.format(1)}°" } ?: "Unknown"

private fun Float.format(decimals: Int): String = "%.${decimals}f".format(Locale.ROOT, this)
