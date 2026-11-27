package dev.thomasbuilds.spectre.dashboard

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Bluetooth
import androidx.compose.material.icons.rounded.SatelliteAlt
import androidx.compose.material.icons.rounded.SignalCellularAlt
import androidx.compose.material.icons.rounded.Wifi
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.text
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import dev.thomasbuilds.spectre.core.Source
import dev.thomasbuilds.spectre.core.SourceStatus
import dev.thomasbuilds.spectre.monitor.ScanState
import kotlin.math.roundToInt

private const val NO_READING = "—"

internal data class SourceSummary(
  val status: SourceStatus,
  val ready: Boolean,
  val count: Int,
  val reading: String
)

private class SourceLabel(
  val title: String,
  val icon: ImageVector,
  val unit: String
)

internal fun ScanState.summary(source: Source): SourceSummary {
  val (count, reading) =
    when (source) {
      Source.CELLULAR -> cellular.signals.size to dbmReading(exposure.cellularDbm)
      Source.WIFI -> wifi.signals.count { !it.isStale } to dbmReading(exposure.wifiDbm)
      Source.BLUETOOTH -> bluetooth.signals.count { !it.isStale } to dbmReading(exposure.bluetoothDbm)
      Source.GNSS -> gnss.signals.size to (gnss.signals.maxOfOrNull { it.cn0DbHz }?.let { "${it.toInt()} dBHz" } ?: NO_READING)
    }
  val state = this[source]
  return SourceSummary(state.status, state.ready, count, reading)
}

@Composable
internal fun SourceCard(
  source: Source,
  summary: SourceSummary,
  isOpen: Boolean,
  onClick: () -> Unit,
  modifier: Modifier = Modifier
) {
  val label = source.label()
  val colors = MaterialTheme.colorScheme
  Card(
    onClick = onClick,
    modifier = modifier.heightIn(min = 132.dp).semantics { selected = isOpen },
    shape = RoundedCornerShape(14.dp),
    colors = CardDefaults.cardColors(containerColor = colors.surface),
    border = BorderStroke(1.dp, if (isOpen) colors.primary else colors.outlineVariant)
  ) {
    Column(Modifier.padding(14.dp).fillMaxSize()) {
      Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(label.icon, contentDescription = null, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(6.dp))
        Text(label.title, style = MaterialTheme.typography.titleMedium)
      }
      Spacer(Modifier.weight(1f))
      val message = summary.status.message
      when {
        message != null -> {
          Text(
            message,
            style = MaterialTheme.typography.bodySmall,
            color = if (summary.status == SourceStatus.NO_PERMISSION) colors.error else colors.onSurfaceVariant
          )
        }

        !summary.ready -> {
          Column(Modifier.semantics { text = AnnotatedString("Measuring") }, verticalArrangement = Arrangement.spacedBy(6.dp)) {
            SkeletonBar(90.dp, 18.dp)
            SkeletonBar(130.dp, 12.dp)
          }
        }

        else -> {
          Text("${summary.count} ${label.unit}${if (summary.count == 1) "" else "s"}")
          Text(summary.reading, style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant)
        }
      }
    }
  }
}

private fun dbmReading(dbm: Double?): String = dbm?.let { "${it.roundToInt()} dBm" } ?: NO_READING

private fun Source.label(): SourceLabel =
  when (this) {
    Source.CELLULAR -> SourceLabel("Cellular", Icons.Rounded.SignalCellularAlt, "tower")
    Source.WIFI -> SourceLabel("Wi-Fi", Icons.Rounded.Wifi, "AP")
    Source.BLUETOOTH -> SourceLabel("Bluetooth", Icons.Rounded.Bluetooth, "device")
    Source.GNSS -> SourceLabel("GNSS", Icons.Rounded.SatelliteAlt, "satellite")
  }

private val SourceStatus.message: String?
  get() =
    when (this) {
      SourceStatus.NO_PERMISSION -> "Tap to grant permission"
      SourceStatus.RADIO_OFF -> "Tap to turn on"
      SourceStatus.NO_SIM -> "No SIM card"
      SourceStatus.UNSUPPORTED -> "Not supported"
      SourceStatus.OK, SourceStatus.LOCATION_OFF -> null
    }
