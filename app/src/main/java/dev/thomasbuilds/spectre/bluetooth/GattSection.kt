package dev.thomasbuilds.spectre.bluetooth

import android.Manifest
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import dev.thomasbuilds.spectre.bluetooth.gatt.GattCharacteristic
import dev.thomasbuilds.spectre.bluetooth.gatt.GattInspection
import dev.thomasbuilds.spectre.bluetooth.gatt.GattReading
import dev.thomasbuilds.spectre.bluetooth.gatt.GattService
import dev.thomasbuilds.spectre.bluetooth.gatt.shortUuid
import dev.thomasbuilds.spectre.core.hasPermission
import dev.thomasbuilds.spectre.ui.kit.copyToClipboard

@Composable
internal fun GattSection(
  signal: BluetoothSignal,
  inspection: GattInspection?,
  onInspect: () -> Unit
) {
  val context = LocalContext.current
  if (!signal.everConnectable || !context.hasPermission(Manifest.permission.BLUETOOTH_CONNECT)) return
  val enabled = inspection !is GattInspection.Busy
  val border = if (enabled) BorderStroke(1.dp, MaterialTheme.colorScheme.outline) else ButtonDefaults.outlinedButtonBorder(enabled = false)
  Column(Modifier.padding(top = 8.dp)) {
    Row(verticalAlignment = Alignment.CenterVertically) {
      OutlinedButton(onClick = onInspect, enabled = enabled, border = border) {
        Text(inspection.buttonLabel)
      }
      Spacer(Modifier.width(8.dp))
      if (inspection is GattInspection.Failed) SmallText(inspection.reason, color = MaterialTheme.colorScheme.error)
      if (inspection is GattInspection.Done) {
        SmallText(inspection.summary)
        IconButton(onClick = { context.copyToClipboard("GATT output", inspection.export(signal)) }, modifier = Modifier.size(32.dp)) {
          Icon(Icons.Rounded.ContentCopy, "Copy GATT output", Modifier.size(16.dp), MaterialTheme.colorScheme.onSurfaceVariant)
        }
      }
    }
    if (inspection is GattInspection.Done) {
      if (inspection.note != null) SmallText(inspection.note, Modifier.padding(top = 4.dp), MaterialTheme.colorScheme.error)
      GattServices(inspection.services)
    }
  }
}

@Composable
private fun GattServices(services: List<GattService>) {
  Column(Modifier.padding(top = 6.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
    services.forEach { service ->
      Column(
        Modifier
          .fillMaxWidth()
          .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(8.dp))
          .padding(horizontal = 10.dp, vertical = 6.dp)
      ) {
        val label = service.name ?: shortUuid(service.uuid)
        Text(label, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.primary)
        if (label != service.uuid) SmallText(service.uuid, fontFamily = FontFamily.Monospace)
        service.characteristics.forEach { GattCharacteristicRow(it) }
      }
    }
  }
}

@Composable
private fun GattCharacteristicRow(characteristic: GattCharacteristic) {
  Column(Modifier.padding(top = 6.dp, start = 6.dp)) {
    Text(characteristic.name ?: shortUuid(characteristic.uuid), style = MaterialTheme.typography.bodySmall)
    val reading = characteristic.reading
    if (reading is GattReading.Value) {
      Text(
        "= ${reading.text}",
        Modifier.padding(start = 8.dp),
        color = MaterialTheme.colorScheme.primary,
        fontFamily = FontFamily.Monospace,
        style = MaterialTheme.typography.bodyMedium
      )
      if (reading.showsHex) SmallText(reading.hex, Modifier.padding(start = 8.dp), fontFamily = FontFamily.Monospace)
    } else if (reading != null) {
      SmallText(reading.description, Modifier.padding(start = 8.dp))
    }
    if (characteristic.propertiesLine.isNotEmpty()) SmallText(characteristic.propertiesLine, fontFamily = FontFamily.Monospace)
  }
}

@Composable
private fun SmallText(
  text: String,
  modifier: Modifier = Modifier,
  color: Color = MaterialTheme.colorScheme.onSurfaceVariant,
  fontFamily: FontFamily? = null
) = Text(text, modifier, color = color, fontFamily = fontFamily, style = MaterialTheme.typography.bodySmall)

private val GattInspection?.buttonLabel: String
  get() =
    when (this) {
      GattInspection.Connecting -> "Connecting…"
      GattInspection.Discovering -> "Discovering…"
      is GattInspection.Reading -> "Reading $done/$total"
      is GattInspection.Done -> "Re-inspect"
      is GattInspection.Failed, null -> "Inspect GATT"
    }

private val GattReading.Value.showsHex: Boolean get() = hex.isNotEmpty() && hex != text

private val GattReading.description: String
  get() =
    when (this) {
      GattReading.Restricted -> "Restricted by Android"
      is GattReading.Failed -> "Read failed (status $status)"
      is GattReading.Value -> if (showsHex) "$text [$hex]" else text
    }

private val GattCharacteristic.propertyLabels: String get() = properties.joinToString(",") { it.label }

private val GattCharacteristic.propertiesLine: String get() = if (name == null) propertyLabels else "${shortUuid(uuid)} · $propertyLabels"

private val GattInspection.Done.summary: String
  get() {
    val values = services.sumOf { service -> service.characteristics.count { it.reading is GattReading.Value } }
    return "${services.size.counted("service")} · ${values.counted("value")}"
  }

private fun Int.counted(noun: String): String = if (this == 1) "1 $noun" else "$this ${noun}s"

private fun GattInspection.Done.export(signal: BluetoothSignal): String =
  buildString {
    appendLine("GATT inspection")
    appendLine("Device: ${signal.name ?: "Unknown"} (${signal.address})")
    if (note != null) appendLine("Note: $note")
    appendLine()
    if (!signal.advertisementHex.isNullOrBlank()) {
      appendLine("Advertisement:")
      signal.advertisementHex.lines().forEach { appendLine("  $it") }
      appendLine()
    }
    services.forEach { service ->
      appendLine(service.name?.let { "$it / ${service.uuid}" } ?: service.uuid)
      service.characteristics.forEach { characteristic ->
        appendLine("  ${characteristic.name?.let { "$it / ${characteristic.uuid}" } ?: characteristic.uuid}")
        if (characteristic.properties.isNotEmpty()) appendLine("    properties: ${characteristic.propertyLabels}")
        characteristic.reading?.let { appendLine("    value: ${it.description}") }
      }
      appendLine()
    }
  }.trimEnd()
