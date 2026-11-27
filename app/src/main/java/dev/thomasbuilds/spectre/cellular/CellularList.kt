package dev.thomasbuilds.spectre.cellular

import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.runtime.Composable
import dev.thomasbuilds.spectre.ui.list.Detail
import dev.thomasbuilds.spectre.ui.list.DetailTable
import dev.thomasbuilds.spectre.ui.list.ListRows
import dev.thomasbuilds.spectre.ui.list.SignalRowHeader
import dev.thomasbuilds.spectre.ui.list.Tag
import dev.thomasbuilds.spectre.ui.list.TagTone
import dev.thomasbuilds.spectre.ui.list.formatDistance
import dev.thomasbuilds.spectre.ui.list.signalList
import java.util.Locale

private const val GSM_MAX_TIMING_ADVANCE = 63

internal fun cellularRows(signals: List<CellSignal>): ListRows<CellSignal> =
  ListRows(signals.sortedWith(compareByDescending<CellSignal> { it.exposureDbm }.thenBy { it.key }), signals.size)

internal fun LazyListScope.cellularList(
  rows: ListRows<CellSignal>,
  expandedKey: String?,
  onToggle: (String) -> Unit,
  modemLabel: String?,
  onHelp: () -> Unit
) = signalList(
  title = "Cellular Towers",
  rows = rows,
  key = { it.key },
  expandedKey = expandedKey,
  onToggle = onToggle,
  header = { CellHeader(it) },
  details = { DetailTable(it.details()) },
  onHelp = onHelp,
  helpTitle = "Cellular monitoring",
  subtitle = modemLabel?.let { "Modem: $it" }
)

@Composable
private fun CellHeader(cell: CellSignal) {
  SignalRowHeader(
    tag = cell.type.generation,
    title = cell.operator ?: "Unknown carrier",
    subtitle = listOfNotNull("${cell.exposureDbm} dBm", cell.distance?.let { "·  $it" })
  ) {
    if (cell.isConnected) Tag("Connected", tone = TagTone.POSITIVE)
  }
}

private val CellSignal.distance: String?
  get() {
    val text = formatDistance(distanceMeters ?: return null)
    return if (type == CellNetworkType.GSM && timingAdvance == GSM_MAX_TIMING_ADVANCE) "≥ $text" else text
  }

private fun CellSignal.details(): List<Detail> {
  val frequency = "Frequency" to downlinkMhz?.let(::formatMhz)
  val identity =
    when (type) {
      CellNetworkType.NR -> listOf("NCI" to cellId, "PCI" to physicalId, "TAC" to areaCode, "NR-ARFCN" to arfcn, frequency)
      CellNetworkType.LTE -> listOf("CI" to cellId, "PCI" to physicalId, "TAC" to areaCode, "EARFCN" to arfcn, frequency)
      CellNetworkType.WCDMA -> listOf("CID" to cellId, "PSC" to physicalId, "LAC" to areaCode, "UARFCN" to arfcn, frequency)
      CellNetworkType.GSM -> listOf("CID" to cellId, "LAC" to areaCode, "ARFCN" to arfcn, frequency, "BSIC" to physicalId)
    }
  val carrier =
    listOf(
      "Bandwidth" to bandwidthKhz?.let { "${(it / 1000.0).toString().removeSuffix(".0")} MHz" },
      "Bands" to bands.takeIf { it.isNotEmpty() }?.joinToString { if (type == CellNetworkType.NR) "n$it" else "B$it" }
    )
  val rows = listOf("Status" to role.label, "SIM" to simSlot, "MCC" to mcc, "MNC" to mnc) + identity + carrier
  return rows.mapNotNull { (label, value) -> value?.let { Detail(label, "$it") } } +
    metrics.map { (metric, value) -> metric.detail(value) } +
    listOfNotNull(timingAdvance?.let { Detail("TA", "$it") })
}

private val CellRole.label: String
  get() =
    when (this) {
      CellRole.SERVING -> "Serving"
      CellRole.SECONDARY_SERVING -> "Secondary serving"
      CellRole.NEIGHBOR -> "Neighbor"
    }

private fun CellMetric.detail(value: Int): Detail =
  when (this) {
    CellMetric.RSRP -> Detail("RSRP", "$value dBm")
    CellMetric.RSRQ -> Detail("RSRQ", "$value dB")
    CellMetric.SNR -> Detail("SNR", "$value dB")
    CellMetric.CQI -> Detail("CQI", "$value")
    CellMetric.SS_RSRP -> Detail("SS-RSRP", "$value dBm")
    CellMetric.SS_RSRQ -> Detail("SS-RSRQ", "$value dB")
    CellMetric.SS_SINR -> Detail("SS-SINR", "$value dB")
    CellMetric.CSI_RSRP -> Detail("CSI-RSRP", "$value dBm")
    CellMetric.RSCP -> Detail("RSCP", "$value dBm")
    CellMetric.RSCP_OR_RSSI -> Detail("RSCP/RSSI", "$value dBm")
    CellMetric.EC_NO -> Detail("Ec/No", "$value dB")
    CellMetric.RX_QUAL -> Detail("RXQUAL", "$value")
  }

private fun formatMhz(mhz: Double): String = "%.1f MHz".format(Locale.ROOT, mhz)
