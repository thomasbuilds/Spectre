package dev.thomasbuilds.spectre.wifi

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Key
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.SwapVert
import androidx.compose.material.icons.rounded.Wifi
import androidx.compose.runtime.Composable
import dev.thomasbuilds.spectre.ui.kit.MultiChoice
import dev.thomasbuilds.spectre.ui.kit.OptionsSheet
import dev.thomasbuilds.spectre.ui.kit.SingleChoice
import dev.thomasbuilds.spectre.ui.kit.toggle
import dev.thomasbuilds.spectre.ui.list.FilterSection
import dev.thomasbuilds.spectre.ui.list.ShowStaleSwitch
import dev.thomasbuilds.spectre.ui.list.TextFilterSection

@Composable
internal fun WifiFilterSheet(
  filters: WifiFilters,
  onFiltersChange: (WifiFilters) -> Unit,
  showStale: Boolean,
  onShowStaleChange: (Boolean) -> Unit,
  onDismiss: () -> Unit
) = OptionsSheet(onDismiss = onDismiss) {
  FilterSection("Sort by", Icons.Rounded.SwapVert) {
    SingleChoice(WifiSort.entries, filters.sort, label = { it.label }, onSelect = { onFiltersChange(filters.copy(sort = it)) })
  }
  FilterSection("Band", Icons.Rounded.Wifi) {
    MultiChoice(
      WifiBand.entries,
      filters.bands,
      label = { it.label },
      onToggle = { onFiltersChange(filters.copy(bands = filters.bands.toggle(it))) }
    )
  }
  FilterSection("Security", Icons.Rounded.Lock) {
    MultiChoice(
      WifiSecurity.entries,
      filters.security,
      label = { it.label },
      onToggle = { onFiltersChange(filters.copy(security = filters.security.toggle(it))) }
    )
  }
  FilterSection("WPS", Icons.Rounded.Key) {
    SingleChoice(WpsFilter.entries, filters.wps, label = { it.label }, onSelect = { onFiltersChange(filters.copy(wps = it)) })
  }
  TextFilterSection(
    title = "Vendor filter",
    filter = filters.vendor,
    onFilterChange = { onFiltersChange(filters.copy(vendor = it)) },
    placeholder = "cisco, netgear, tp-link",
    hint = "Use commas to filter several vendors at once. Matches the access point maker from its BSSID (e.g. Cisco, Ubiquiti)."
  )
  ShowStaleSwitch(
    checked = showStale,
    onCheckedChange = onShowStaleChange,
    description = "Keep access points listed after they stop being detected, grayed out."
  )
}
