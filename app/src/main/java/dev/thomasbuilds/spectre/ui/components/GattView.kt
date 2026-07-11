package dev.thomasbuilds.spectre.ui.components

import android.bluetooth.BluetoothDevice
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import dev.thomasbuilds.spectre.analysis.Distance
import dev.thomasbuilds.spectre.model.BluetoothSignal
import dev.thomasbuilds.spectre.scanner.ble.GattCharacteristicInfo
import dev.thomasbuilds.spectre.scanner.ble.GattInspection
import dev.thomasbuilds.spectre.scanner.ble.GattInspector
import dev.thomasbuilds.spectre.scanner.ble.GattServiceInfo
import dev.thomasbuilds.spectre.scanner.ble.shortenUuid

@Composable
internal fun BluetoothExpandable(
  device: BluetoothSignal,
  expanded: Boolean,
  onToggle: () -> Unit,
  resolveBleDevice: (String) -> BluetoothDevice?
) {
  val context = LocalContext.current
  val inspector = remember { GattInspector(context) }
  var inspection by remember(device.mac) { mutableStateOf<GattInspection>(GattInspection.Idle) }
  var writeTarget by remember(device.mac) { mutableStateOf<Pair<String, GattCharacteristicInfo>?>(null) }

  DisposableEffect(device.mac, expanded) {
    onDispose { inspector.cancel() }
  }

  writeTarget?.let { (serviceUuid, target) ->
    GattWriteDialog(
      characteristic = target,
      onWrite = { bytes, withResponse ->
        writeTarget = null
        inspector.write(serviceUuid, target.uuid, bytes, withResponse) { _, msg ->
          Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
        }
      },
      onDismiss = { writeTarget = null }
    )
  }

  ExpandableRow(
    expanded = expanded,
    onClick = onToggle,
    header = { BtHeader(device) },
    details = device.details,
    dimmed = device.isStale,
    footer = {
      if (device.isConnectable || inspection !is GattInspection.Idle) {
        Spacer(Modifier.height(8.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
          OutlinedButton(
            onClick = {
              inspection = GattInspection.Connecting
              inspector.inspect(device.mac, resolveBleDevice(device.mac)) { inspection = it }
            },
            enabled =
              inspection !is GattInspection.Connecting &&
                inspection !is GattInspection.DiscoveringServices &&
                inspection !is GattInspection.ReadingValues
          ) {
            Text(
              when (val s = inspection) {
                is GattInspection.Connecting -> {
                  "Connecting…"
                }

                is GattInspection.DiscoveringServices -> {
                  "Discovering…"
                }

                is GattInspection.ReadingValues -> {
                  if (s.total == 0) "Reading…" else "Reading ${s.done}/${s.total}"
                }

                is GattInspection.Done -> {
                  "Re-inspect"
                }

                else -> {
                  "Inspect GATT"
                }
              }
            )
          }
          Spacer(Modifier.width(8.dp))
          when (val s = inspection) {
            is GattInspection.Failed -> {
              Text(
                s.reason,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error
              )
            }

            is GattInspection.Done -> {
              val readCount =
                s.services.sumOf { svc ->
                  svc.characteristics.count { it.readValue != null }
                }
              Text(
                "${s.services.size} services · $readCount values",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
              )
              IconButton(
                onClick = {
                  val text =
                    formatGattInspection(
                      deviceName = device.name,
                      deviceMac = device.mac,
                      services = s.services,
                      advertisementHex = device.advertisementHex
                    )
                  copyToClipboard(context, "GATT inspection", text)
                  Toast
                    .makeText(
                      context,
                      "GATT output copied",
                      Toast.LENGTH_SHORT
                    ).show()
                },
                modifier = Modifier.size(32.dp)
              ) {
                Icon(
                  imageVector = Icons.Rounded.ContentCopy,
                  contentDescription = "Copy GATT output",
                  tint = MaterialTheme.colorScheme.onSurfaceVariant,
                  modifier = Modifier.size(16.dp)
                )
              }
            }

            else -> {}
          }
        }
        val done = inspection as? GattInspection.Done
        if (done != null) {
          Spacer(Modifier.height(6.dp))
          GattServicesView(done.services, onWrite = { svc, ch -> writeTarget = svc to ch })
        }
      }
    }
  )
}

@Composable
private fun BtHeader(b: BluetoothSignal) {
  Row(
    modifier = Modifier.fillMaxWidth(),
    verticalAlignment = Alignment.CenterVertically
  ) {
    Badge(b.mac.takeLast(5))
    Spacer(Modifier.width(12.dp))
    Column(modifier = Modifier.weight(1f)) {
      Text(
        b.name,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurface
      )
      Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
          "${b.rssi} dBm",
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
          "·  ${Distance.formatWithConfidence(b.distanceMeters, b.distanceConfidence)}",
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant
        )
      }
    }
    Row(
      horizontalArrangement = Arrangement.spacedBy(4.dp),
      verticalAlignment = Alignment.CenterVertically
    ) {
      if (b.isConnectable && !b.isBonded) ConnectableTag()
      if (b.isBonded) PairedChip()
    }
  }
}

private fun formatGattInspection(
  deviceName: String,
  deviceMac: String,
  services: List<GattServiceInfo>,
  advertisementHex: String?
): String =
  buildString {
    appendLine("GATT inspection")
    appendLine("Device: $deviceName ($deviceMac)")
    appendLine()
    if (!advertisementHex.isNullOrBlank()) {
      appendLine("Advertisement:")
      advertisementHex.lines().forEach { appendLine("  $it") }
      appendLine()
    }
    services.forEach { service ->
      val serviceHeader = service.label?.let { "$it / ${service.uuid}" } ?: service.uuid
      appendLine(serviceHeader)
      service.characteristics.forEach { ch ->
        val charHeader = ch.label?.let { "$it / ${ch.uuid}" } ?: ch.uuid
        appendLine("  $charHeader")
        if (ch.properties.isNotEmpty()) {
          appendLine("    properties: ${ch.properties.joinToString(",")}")
        }
        if (ch.readValue != null) {
          appendLine("    value: ${ch.readValue}")
        }
      }
      appendLine()
    }
  }.trimEnd()

@Composable
private fun GattServicesView(
  services: List<GattServiceInfo>,
  onWrite: (String, GattCharacteristicInfo) -> Unit
) {
  Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
    services.forEach { service ->
      Column(
        modifier =
          Modifier
            .fillMaxWidth()
            .background(
              MaterialTheme.colorScheme.surfaceVariant,
              RoundedCornerShape(8.dp)
            ).padding(horizontal = 10.dp, vertical = 6.dp)
      ) {
        Text(
          service.label ?: shortenUuid(service.uuid),
          style = MaterialTheme.typography.bodyMedium,
          color = MaterialTheme.colorScheme.primary
        )
        Text(
          service.uuid,
          style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
          color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        service.characteristics.forEach { ch ->
          Column(modifier = Modifier.padding(top = 6.dp, start = 6.dp)) {
            Text(
              ch.label ?: shortenUuid(ch.uuid),
              style = MaterialTheme.typography.bodySmall,
              color = MaterialTheme.colorScheme.onSurface
            )
            if (ch.readValue != null) {
              Text(
                "= ${ch.readValue}",
                style =
                  MaterialTheme.typography.bodyMedium.copy(
                    fontFamily = FontFamily.Monospace
                  ),
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(start = 8.dp)
              )
            }
            Text(
              "${shortenUuid(ch.uuid)} · ${ch.properties.joinToString(",")}",
              style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
              color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            if (ch.properties.any { it == "write" || it == "write-no-resp" }) {
              TextButton(
                onClick = { onWrite(service.uuid, ch) },
                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp)
              ) {
                Text("Write")
              }
            }
          }
        }
      }
    }
  }
}

@Composable
private fun GattWriteDialog(
  characteristic: GattCharacteristicInfo,
  onWrite: (ByteArray, Boolean) -> Unit,
  onDismiss: () -> Unit
) {
  var hex by remember { mutableStateOf("") }
  var withResponse by remember { mutableStateOf("write" in characteristic.properties) }
  val bytes = remember(hex) { parseHexOrNull(hex) }
  AlertDialog(
    onDismissRequest = onDismiss,
    title = { Text("Write ${characteristic.label ?: shortenUuid(characteristic.uuid)}") },
    text = {
      Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        OutlinedTextField(
          value = hex,
          onValueChange = { hex = it },
          label = { Text("Hex bytes") },
          placeholder = { Text("01 ff a0") },
          singleLine = true,
          isError = hex.isNotBlank() && bytes == null
        )
        FilterChip(
          selected = withResponse,
          onClick = { withResponse = !withResponse },
          label = { Text(if (withResponse) "With response" else "No response") },
          colors = filterChipColors(),
          border = filterChipBorder(withResponse)
        )
      }
    },
    confirmButton = {
      TextButton(
        onClick = { bytes?.let { onWrite(it, withResponse) } },
        enabled = bytes != null && bytes.isNotEmpty()
      ) {
        Text("Write")
      }
    },
    dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
  )
}

private fun parseHexOrNull(input: String): ByteArray? {
  val clean = input.filterNot { it == ' ' || it == ':' || it == '\n' || it == '\t' }
  if (clean.isEmpty() || clean.length % 2 != 0) return null
  return runCatching {
    ByteArray(clean.length / 2) { i -> clean.substring(i * 2, i * 2 + 2).toInt(16).toByte() }
  }.getOrNull()
}
