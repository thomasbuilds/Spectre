package dev.thomasbuilds.spectre.ui.components

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Campaign
import androidx.compose.material.icons.rounded.FilterAlt
import androidx.compose.material.icons.rounded.Fingerprint
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.Key
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.SwapVert
import androidx.compose.material.icons.rounded.Wifi
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import dev.thomasbuilds.spectre.model.BleAddressType
import dev.thomasbuilds.spectre.model.WifiBand
import dev.thomasbuilds.spectre.model.WifiSecurity
import dev.thomasbuilds.spectre.scanner.ble.BleAdvertiser
import dev.thomasbuilds.spectre.ui.SignalSource
import java.util.UUID

@Composable
fun DetailListSheets(
  source: SignalSource?,
  holder: DetailListState,
  showStaleWifi: Boolean,
  onSetShowStaleWifi: (Boolean) -> Unit,
  showStaleBluetooth: Boolean,
  onSetShowStaleBluetooth: (Boolean) -> Unit
) {
  val context = LocalContext.current
  val advertiser = remember { BleAdvertiser(context) }
  DisposableEffect(Unit) { onDispose { advertiser.stop() } }

  if (holder.bleAdvertiseSheetOpen && source == SignalSource.BLUETOOTH) {
    BleAdvertiseSheet(advertiser = advertiser, onDismiss = { holder.bleAdvertiseSheetOpen = false })
  }

  if (holder.btSheetOpen && source == SignalSource.BLUETOOTH) {
    val selectedAddressTypes =
      remember(holder.btAddressTypes) {
        holder.btAddressTypes.mapNotNullTo(linkedSetOf()) { n -> BleAddressType.entries.firstOrNull { it.name == n } }
      }
    BluetoothFilterSheet(
      currentSort = holder.btSort,
      onSortChange = { holder.btSort = it },
      manufacturerFilter = holder.btManufacturerFilter,
      onManufacturerFilterChange = { holder.btManufacturerFilter = it },
      filterMode = holder.btFilterMode,
      onFilterModeChange = { holder.btFilterMode = it },
      selectedAddressTypes = selectedAddressTypes,
      onAddressTypeToggle = { type ->
        holder.btAddressTypes =
          if (type.name in holder.btAddressTypes) holder.btAddressTypes - type.name else holder.btAddressTypes + type.name
      },
      showStale = showStaleBluetooth,
      onShowStaleChange = onSetShowStaleBluetooth,
      onDismiss = { holder.btSheetOpen = false }
    )
  }

  if (holder.wifiSheetOpen && source == SignalSource.WIFI) {
    val selectedBands =
      remember(holder.wifiBandNames) {
        holder.wifiBandNames.mapNotNullTo(linkedSetOf()) { n -> WifiBand.entries.firstOrNull { it.name == n } }
      }
    val selectedSecurities =
      remember(holder.wifiSecurityNames) {
        holder.wifiSecurityNames.mapNotNullTo(linkedSetOf()) { n -> WifiSecurity.entries.firstOrNull { it.name == n } }
      }
    WifiFilterSheet(
      currentSort = holder.wifiSort,
      onSortChange = { holder.wifiSort = it },
      selectedBands = selectedBands,
      onBandToggle = { band ->
        holder.wifiBandNames =
          if (band.name in holder.wifiBandNames) holder.wifiBandNames - band.name else holder.wifiBandNames + band.name
      },
      selectedSecurities = selectedSecurities,
      onSecurityToggle = { sec ->
        holder.wifiSecurityNames =
          if (sec.name in holder.wifiSecurityNames) holder.wifiSecurityNames - sec.name else holder.wifiSecurityNames + sec.name
      },
      wpsFilter = holder.wifiWpsFilter,
      onWpsFilterChange = { holder.wifiWpsFilter = it },
      vendorFilter = holder.wifiVendorFilter,
      onVendorFilterChange = { holder.wifiVendorFilter = it },
      vendorFilterMode = holder.wifiVendorFilterMode,
      onVendorFilterModeChange = { holder.wifiVendorFilterMode = it },
      showStale = showStaleWifi,
      onShowStaleChange = onSetShowStaleWifi,
      onDismiss = { holder.wifiSheetOpen = false }
    )
  }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun BleAdvertiseSheet(
  advertiser: BleAdvertiser,
  onDismiss: () -> Unit
) {
  val sheetState = rememberModalBottomSheetState()
  var uuid by rememberSaveable { mutableStateOf("E2C56DB5-DFFB-48D2-B060-D0F5A71096E0") }
  var major by rememberSaveable { mutableStateOf("1") }
  var minor by rememberSaveable { mutableStateOf("1") }
  var power by rememberSaveable { mutableStateOf("-59") }
  var status by remember { mutableStateOf(if (advertiser.isAdvertising) "Broadcasting" else "") }
  var advertising by remember { mutableStateOf(advertiser.isAdvertising) }

  val parsedUuid = remember(uuid) { runCatching { UUID.fromString(uuid.trim()) }.getOrNull() }
  val valid =
    parsedUuid != null &&
      major.toIntOrNull() != null &&
      minor.toIntOrNull() != null &&
      power.toIntOrNull() != null

  ModalBottomSheet(
    onDismissRequest = onDismiss,
    sheetState = sheetState,
    containerColor = MaterialTheme.colorScheme.surface,
    contentColor = MaterialTheme.colorScheme.onSurface,
    contentWindowInsets = { WindowInsets(0) }
  ) {
    Column(
      modifier =
        Modifier
          .fillMaxWidth()
          .imePadding()
          .verticalScroll(rememberScrollState())
          .padding(horizontal = 20.dp)
          .padding(top = 8.dp, bottom = 28.dp),
      verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
      SheetSection(icon = Icons.Rounded.Campaign, label = "iBeacon broadcast") {
        if (!advertiser.isSupported()) {
          Text(
            "This device can't BLE-advertise.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.error
          )
        }
        OutlinedTextField(
          value = uuid,
          onValueChange = { uuid = it },
          label = { Text("Proximity UUID") },
          singleLine = true,
          isError = uuid.isNotBlank() && parsedUuid == null,
          modifier = Modifier.fillMaxWidth()
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
          OutlinedTextField(
            value = major,
            onValueChange = { major = it },
            label = { Text("Major") },
            singleLine = true,
            modifier = Modifier.weight(1f)
          )
          OutlinedTextField(
            value = minor,
            onValueChange = { minor = it },
            label = { Text("Minor") },
            singleLine = true,
            modifier = Modifier.weight(1f)
          )
          OutlinedTextField(
            value = power,
            onValueChange = { power = it },
            label = { Text("TX@1m") },
            singleLine = true,
            modifier = Modifier.weight(1f)
          )
        }
        Row(
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
          Button(
            onClick = {
              if (advertising) {
                advertiser.stop()
                advertising = false
                status = "Stopped"
              } else {
                advertiser.startIBeacon(
                  parsedUuid!!,
                  major.toInt(),
                  minor.toInt(),
                  power.toInt()
                ) { ok, msg ->
                  advertising = ok
                  status = msg
                }
              }
            },
            enabled = advertising || valid
          ) {
            Text(if (advertising) "Stop" else "Broadcast")
          }
          if (status.isNotBlank()) {
            Text(
              status,
              style = MaterialTheme.typography.bodySmall,
              color = MaterialTheme.colorScheme.onSurfaceVariant
            )
          }
        }
      }
    }
  }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
private fun BluetoothFilterSheet(
  currentSort: BluetoothSort,
  onSortChange: (BluetoothSort) -> Unit,
  manufacturerFilter: String,
  onManufacturerFilterChange: (String) -> Unit,
  filterMode: FilterMode,
  onFilterModeChange: (FilterMode) -> Unit,
  selectedAddressTypes: Set<BleAddressType>,
  onAddressTypeToggle: (BleAddressType) -> Unit,
  showStale: Boolean,
  onShowStaleChange: (Boolean) -> Unit,
  onDismiss: () -> Unit
) {
  val sheetState = rememberModalBottomSheetState()
  ModalBottomSheet(
    onDismissRequest = onDismiss,
    sheetState = sheetState,
    containerColor = MaterialTheme.colorScheme.surface,
    contentColor = MaterialTheme.colorScheme.onSurface,
    contentWindowInsets = { WindowInsets(0) }
  ) {
    Column(
      modifier =
        Modifier
          .fillMaxWidth()
          .imePadding()
          .verticalScroll(rememberScrollState())
          .padding(horizontal = 20.dp)
          .padding(top = 8.dp, bottom = 28.dp),
      verticalArrangement = Arrangement.spacedBy(24.dp)
    ) {
      SheetSection(icon = Icons.Rounded.SwapVert, label = "Sort by") {
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
          BluetoothSort.entries.forEach { option ->
            FilterChip(
              selected = option == currentSort,
              onClick = { onSortChange(option) },
              label = { Text(option.label) },
              colors = filterChipColors(),
              border = filterChipBorder(option == currentSort)
            )
          }
        }
      }

      if (BleAddressType.supported) {
        SheetSection(icon = Icons.Rounded.Fingerprint, label = "Address type") {
          FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            BleAddressType.entries.forEach { type ->
              FilterChip(
                selected = type in selectedAddressTypes,
                onClick = { onAddressTypeToggle(type) },
                label = { Text(type.label) },
                colors = filterChipColors(),
                border = filterChipBorder(type in selectedAddressTypes)
              )
            }
          }
        }
      }

      SheetSection(icon = Icons.Rounded.FilterAlt, label = "Manufacturer filter") {
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
          FilterMode.entries.forEach { mode ->
            FilterChip(
              selected = mode == filterMode,
              onClick = { onFilterModeChange(mode) },
              label = { Text(mode.label) },
              colors = filterChipColors(),
              border = filterChipBorder(mode == filterMode)
            )
          }
        }
        Box(
          modifier =
            Modifier
              .fillMaxWidth()
              .border(
                1.dp,
                MaterialTheme.colorScheme.outline,
                RoundedCornerShape(10.dp)
              ).padding(horizontal = 14.dp, vertical = 12.dp)
        ) {
          androidx.compose.foundation.text.BasicTextField(
            value = manufacturerFilter,
            onValueChange = onManufacturerFilterChange,
            singleLine = true,
            textStyle =
              MaterialTheme.typography.bodyMedium.copy(
                color = MaterialTheme.colorScheme.onSurface
              ),
            cursorBrush =
              androidx.compose.ui.graphics.SolidColor(
                MaterialTheme.colorScheme.primary
              ),
            modifier = Modifier.fillMaxWidth(),
            decorationBox = { inner ->
              if (manufacturerFilter.isEmpty()) {
                Text(
                  "apple, google, microsoft",
                  style = MaterialTheme.typography.bodyMedium,
                  color = MaterialTheme.colorScheme.onSurfaceVariant
                )
              }
              inner()
            }
          )
        }
        Text(
          "Use commas to filter several manufacturers at once. Matches vendor name (e.g. Apple) or company ID (e.g. 0x004C).",
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
          modifier = Modifier.padding(top = 2.dp, start = 4.dp)
        )
      }

      ShowStaleToggle(
        checked = showStale,
        onCheckedChange = onShowStaleChange,
        description = "Keep devices listed after they stop being detected, grayed out."
      )
    }
  }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
private fun WifiFilterSheet(
  currentSort: WifiSort,
  onSortChange: (WifiSort) -> Unit,
  selectedBands: Set<WifiBand>,
  onBandToggle: (WifiBand) -> Unit,
  selectedSecurities: Set<WifiSecurity>,
  onSecurityToggle: (WifiSecurity) -> Unit,
  wpsFilter: WifiWpsFilter,
  onWpsFilterChange: (WifiWpsFilter) -> Unit,
  vendorFilter: String,
  onVendorFilterChange: (String) -> Unit,
  vendorFilterMode: FilterMode,
  onVendorFilterModeChange: (FilterMode) -> Unit,
  showStale: Boolean,
  onShowStaleChange: (Boolean) -> Unit,
  onDismiss: () -> Unit
) {
  val sheetState = rememberModalBottomSheetState()
  ModalBottomSheet(
    onDismissRequest = onDismiss,
    sheetState = sheetState,
    containerColor = MaterialTheme.colorScheme.surface,
    contentColor = MaterialTheme.colorScheme.onSurface,
    contentWindowInsets = { WindowInsets(0) }
  ) {
    Column(
      modifier =
        Modifier
          .fillMaxWidth()
          .imePadding()
          .verticalScroll(rememberScrollState())
          .padding(horizontal = 20.dp)
          .padding(top = 8.dp, bottom = 28.dp),
      verticalArrangement = Arrangement.spacedBy(24.dp)
    ) {
      SheetSection(icon = Icons.Rounded.SwapVert, label = "Sort by") {
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
          WifiSort.entries.forEach { option ->
            FilterChip(
              selected = option == currentSort,
              onClick = { onSortChange(option) },
              label = { Text(option.label) },
              colors = filterChipColors(),
              border = filterChipBorder(option == currentSort)
            )
          }
        }
      }

      SheetSection(icon = Icons.Rounded.Wifi, label = "Band") {
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
          WifiBand.entries.forEach { band ->
            FilterChip(
              selected = band in selectedBands,
              onClick = { onBandToggle(band) },
              label = { Text(band.label) },
              colors = filterChipColors(),
              border = filterChipBorder(band in selectedBands)
            )
          }
        }
      }

      SheetSection(icon = Icons.Rounded.Lock, label = "Security") {
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
          WifiSecurity.entries.forEach { sec ->
            FilterChip(
              selected = sec in selectedSecurities,
              onClick = { onSecurityToggle(sec) },
              label = { Text(sec.label) },
              colors = filterChipColors(),
              border = filterChipBorder(sec in selectedSecurities)
            )
          }
        }
      }

      SheetSection(icon = Icons.Rounded.Key, label = "WPS") {
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
          WifiWpsFilter.entries.forEach { f ->
            FilterChip(
              selected = f == wpsFilter,
              onClick = { onWpsFilterChange(f) },
              label = { Text(f.label) },
              colors = filterChipColors(),
              border = filterChipBorder(f == wpsFilter)
            )
          }
        }
      }

      SheetSection(icon = Icons.Rounded.FilterAlt, label = "Vendor filter") {
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
          FilterMode.entries.forEach { mode ->
            FilterChip(
              selected = mode == vendorFilterMode,
              onClick = { onVendorFilterModeChange(mode) },
              label = { Text(mode.label) },
              colors = filterChipColors(),
              border = filterChipBorder(mode == vendorFilterMode)
            )
          }
        }
        Box(
          modifier =
            Modifier
              .fillMaxWidth()
              .border(
                1.dp,
                MaterialTheme.colorScheme.outline,
                RoundedCornerShape(10.dp)
              ).padding(horizontal = 14.dp, vertical = 12.dp)
        ) {
          androidx.compose.foundation.text.BasicTextField(
            value = vendorFilter,
            onValueChange = onVendorFilterChange,
            singleLine = true,
            textStyle =
              MaterialTheme.typography.bodyMedium.copy(
                color = MaterialTheme.colorScheme.onSurface
              ),
            cursorBrush =
              androidx.compose.ui.graphics.SolidColor(
                MaterialTheme.colorScheme.primary
              ),
            modifier = Modifier.fillMaxWidth(),
            decorationBox = { inner ->
              if (vendorFilter.isEmpty()) {
                Text(
                  "cisco, netgear, tp-link",
                  style = MaterialTheme.typography.bodyMedium,
                  color = MaterialTheme.colorScheme.onSurfaceVariant
                )
              }
              inner()
            }
          )
        }
        Text(
          "Use commas to filter several vendors at once. Matches the access point maker from its BSSID (e.g. Cisco, Ubiquiti).",
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
          modifier = Modifier.padding(top = 2.dp, start = 4.dp)
        )
      }

      ShowStaleToggle(
        checked = showStale,
        onCheckedChange = onShowStaleChange,
        description = "Keep access points listed after they stop being detected, grayed out."
      )
    }
  }
}

@Composable
internal fun filterChipColors() =
  FilterChipDefaults.filterChipColors(
    containerColor = MaterialTheme.colorScheme.surfaceVariant,
    labelColor = MaterialTheme.colorScheme.onSurface,
    selectedContainerColor = MaterialTheme.colorScheme.primary,
    selectedLabelColor = MaterialTheme.colorScheme.onPrimary
  )

@Composable
internal fun filterChipBorder(selected: Boolean) =
  FilterChipDefaults.filterChipBorder(
    enabled = true,
    selected = selected,
    borderColor = MaterialTheme.colorScheme.outline,
    selectedBorderColor = MaterialTheme.colorScheme.primary,
    borderWidth = 0.dp,
    selectedBorderWidth = 0.dp
  )

@Composable
private fun ShowStaleToggle(
  checked: Boolean,
  onCheckedChange: (Boolean) -> Unit,
  description: String
) {
  SheetSection(icon = Icons.Rounded.History, label = "No longer detected") {
    Row(verticalAlignment = Alignment.CenterVertically) {
      Text(
        description,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.weight(1f)
      )
      Spacer(Modifier.width(12.dp))
      Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
  }
}

@Composable
private fun SheetSection(
  icon: ImageVector,
  label: String,
  content: @Composable () -> Unit
) {
  Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
    Row(verticalAlignment = Alignment.CenterVertically) {
      Icon(
        imageVector = icon,
        contentDescription = null,
        tint = MaterialTheme.colorScheme.primary,
        modifier = Modifier.size(18.dp)
      )
      Spacer(Modifier.width(8.dp))
      Text(
        label,
        style = MaterialTheme.typography.titleMedium,
        color = MaterialTheme.colorScheme.primary
      )
    }
    content()
  }
}
