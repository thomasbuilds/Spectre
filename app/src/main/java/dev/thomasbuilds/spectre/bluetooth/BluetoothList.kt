package dev.thomasbuilds.spectre.bluetooth

import android.bluetooth.BluetoothDevice
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import dev.thomasbuilds.spectre.bluetooth.gatt.DeviceInspection
import dev.thomasbuilds.spectre.ui.list.Detail
import dev.thomasbuilds.spectre.ui.list.DetailTable
import dev.thomasbuilds.spectre.ui.list.ListRows
import dev.thomasbuilds.spectre.ui.list.SignalRowHeader
import dev.thomasbuilds.spectre.ui.list.Tag
import dev.thomasbuilds.spectre.ui.list.TagTone
import dev.thomasbuilds.spectre.ui.list.formatDistance
import dev.thomasbuilds.spectre.ui.list.signalList

internal fun bluetoothRows(
  signals: List<BluetoothSignal>,
  filters: BluetoothFilters,
  showStale: Boolean
): ListRows<BluetoothSignal> {
  val visible = if (showStale) signals else signals.filterNot { it.isStale }
  return ListRows(filters.sorted(visible.filter(filters::accepts).sortedBy { it.address }), visible.size)
}

internal fun LazyListScope.bluetoothList(
  rows: ListRows<BluetoothSignal>,
  filters: BluetoothFilters,
  expandedKey: String?,
  onToggle: (String) -> Unit,
  onOpenFilters: () -> Unit,
  inspection: DeviceInspection?,
  onInspect: (BluetoothDevice) -> Unit
) = signalList(
  title = "Bluetooth Devices",
  rows = rows,
  key = { it.address },
  expandedKey = expandedKey,
  onToggle = onToggle,
  header = { BluetoothHeader(it) },
  details = { signal ->
    DetailTable(signal.details())
    GattSection(signal, inspection?.takeIf { it.address == signal.address }?.inspection, onInspect = { onInspect(signal.device) })
  },
  isFiltered = filters.isActive,
  filteredEmptyText = "No devices match the current filter",
  isDimmed = { it.isStale },
  actions = {
    IconButton(onClick = onOpenFilters, modifier = Modifier.size(32.dp)) {
      Icon(Icons.Rounded.Tune, contentDescription = "Bluetooth view options", modifier = Modifier.size(20.dp))
    }
  }
)

@Composable
private fun BluetoothHeader(signal: BluetoothSignal) =
  SignalRowHeader(
    tag = signal.address.takeLast(5),
    title = signal.name ?: "Unknown",
    subtitle = listOf("${signal.rssi} dBm", "·  ${signal.distanceMeters?.let(::formatDistance) ?: "Pending"}")
  ) {
    if (signal.everConnectable && !signal.isBonded) Tag("Connectable")
    if (signal.isBonded) Tag("Paired", tone = TagTone.OUTLINED)
  }

private fun BluetoothSignal.details(): List<Detail> =
  buildList {
    add(Detail("Name", name ?: "Unknown"))
    add(Detail("MAC", address))
    if (vendor != null) add(Detail("Vendor", vendor))
    if (txPowerDbm != null) add(Detail("Adv TX power", "$txPowerDbm dBm"))
    if (ibeaconPowerDbm != null) add(Detail("iBeacon RSSI@1m", "$ibeaconPowerDbm dBm"))
    add(Detail("Connectable", if (alternatesConnectable) "$isConnectable (alternates)" else "$isConnectable"))
    if (addressType != null) add(Detail("Address type", addressType.label))
    if (primaryPhy != null) add(Detail("Primary PHY", primaryPhy.label))
    if (secondaryPhy != null) add(Detail("Secondary PHY", secondaryPhy.label))
    if (serviceUuids.isNotEmpty()) add(Detail("Service UUIDs", serviceUuids.joinToString()))
    if (serviceDataCount > 0) add(Detail("Service data", if (serviceDataCount == 1) "1 entry" else "$serviceDataCount entries"))
    if (manufacturers.isNotEmpty()) add(Detail("Manufacturer", manufacturers.joinToString { it.name ?: it.idHex }))
    if (appleTypes.isNotEmpty()) add(Detail("Apple type", appleTypes.joinToString()))
    if (googleType != null) add(Detail("Google type", googleType))
    if (microsoftType != null) add(Detail("Microsoft type", microsoftType))
    if (advertiseFlags != null) add(Detail("Adv flags", advertiseFlags.toByte().toPrefixedHex()))
  }
