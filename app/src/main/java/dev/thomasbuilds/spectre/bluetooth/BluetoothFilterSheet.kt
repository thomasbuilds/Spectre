package dev.thomasbuilds.spectre.bluetooth

import android.os.Build
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Fingerprint
import androidx.compose.material.icons.rounded.SwapVert
import androidx.compose.runtime.Composable
import dev.thomasbuilds.spectre.ui.kit.MultiChoice
import dev.thomasbuilds.spectre.ui.kit.OptionsSheet
import dev.thomasbuilds.spectre.ui.kit.SingleChoice
import dev.thomasbuilds.spectre.ui.kit.toggle
import dev.thomasbuilds.spectre.ui.list.FilterSection
import dev.thomasbuilds.spectre.ui.list.ShowStaleSwitch
import dev.thomasbuilds.spectre.ui.list.TextFilterSection

@Composable
internal fun BluetoothFilterSheet(
  filters: BluetoothFilters,
  onFiltersChange: (BluetoothFilters) -> Unit,
  showStale: Boolean,
  onShowStaleChange: (Boolean) -> Unit,
  onDismiss: () -> Unit
) = OptionsSheet(onDismiss = onDismiss) {
  FilterSection("Sort by", Icons.Rounded.SwapVert) {
    SingleChoice(BluetoothSort.entries, filters.sort, label = { it.label }, onSelect = { onFiltersChange(filters.copy(sort = it)) })
  }
  if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.VANILLA_ICE_CREAM) {
    FilterSection("Address type", Icons.Rounded.Fingerprint) {
      MultiChoice(
        BleAddressType.entries,
        filters.addressTypes,
        label = { it.label },
        onToggle = { onFiltersChange(filters.copy(addressTypes = filters.addressTypes.toggle(it))) }
      )
    }
  }
  TextFilterSection(
    title = "Manufacturer filter",
    filter = filters.manufacturer,
    onFilterChange = { onFiltersChange(filters.copy(manufacturer = it)) },
    placeholder = "apple, google, microsoft",
    hint = "Use commas to filter several manufacturers at once. Matches manufacturer name (e.g. Apple) or company ID (e.g. 0x004C)."
  )
  ShowStaleSwitch(
    checked = showStale,
    onCheckedChange = onShowStaleChange,
    description = "Keep devices listed after they stop being detected, grayed out."
  )
}
