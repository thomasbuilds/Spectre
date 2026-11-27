package dev.thomasbuilds.spectre.wifi

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import dev.thomasbuilds.spectre.ui.list.Detail
import dev.thomasbuilds.spectre.ui.list.DetailTable
import dev.thomasbuilds.spectre.ui.list.ListRows
import dev.thomasbuilds.spectre.ui.list.SignalRowHeader
import dev.thomasbuilds.spectre.ui.list.Tag
import dev.thomasbuilds.spectre.ui.list.TagTone
import dev.thomasbuilds.spectre.ui.list.formatDistance
import dev.thomasbuilds.spectre.ui.list.signalList

internal fun wifiRows(
  signals: List<WifiSignal>,
  filters: WifiFilters,
  showStale: Boolean
): ListRows<WifiSignal> {
  val visible = if (showStale) signals else signals.filterNot { it.isStale }
  return ListRows(filters.sorted(visible.filter(filters::accepts).sortedBy { it.bssid }), visible.size)
}

internal fun LazyListScope.wifiList(
  rows: ListRows<WifiSignal>,
  filters: WifiFilters,
  throttled: Boolean,
  expandedKey: String?,
  onToggle: (String) -> Unit,
  onOpenFilters: () -> Unit,
  onOpenRecon: () -> Unit,
  onOpenHelp: () -> Unit
) = signalList(
  title = "Wi-Fi Access Points",
  rows = rows,
  key = { it.bssid },
  expandedKey = expandedKey,
  onToggle = onToggle,
  header = { WifiHeader(it) },
  details = {
    DetailTable(it.details())
    if (it.isConnected) {
      OutlinedButton(
        onClick = onOpenRecon,
        modifier = Modifier.padding(top = 4.dp, start = 4.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
      ) {
        Text("Scan local network")
      }
    }
  },
  isFiltered = filters.isActive,
  filteredEmptyText = "No APs match the current filter",
  isDimmed = { it.isStale },
  actions = {
    if (throttled) Tag("THROTTLED", tone = TagTone.EMPHASIZED, onClick = onOpenHelp)
    IconButton(onClick = onOpenFilters, modifier = Modifier.size(32.dp)) {
      Icon(Icons.Rounded.Tune, contentDescription = "Wi-Fi view options", modifier = Modifier.size(20.dp))
    }
  }
)

@Composable
private fun WifiHeader(signal: WifiSignal) =
  SignalRowHeader(
    tag = signal.band.label,
    title = signal.ssid ?: "Hidden",
    subtitle = listOf("${signal.rssi} dBm", "·  ${signal.distanceMeters?.let(::formatDistance) ?: "Pending"}")
  ) {
    if (signal.isConnected) Tag("Connected", tone = TagTone.POSITIVE)
  }

private fun WifiSignal.details(): List<Detail> =
  buildList {
    add(Detail("BSSID", bssid))
    if (vendor != null) add(Detail("Vendor", vendor))
    add(Detail("Signal", if (isRssiLive) "$rssi dBm (live)" else "$rssi dBm"))
    add(Detail("Frequency", "$frequencyMhz MHz"))
    if (channel != null) add(Detail("Channel", "$channel"))
    add(Detail("Width", "$widthMhz MHz"))
    if (centerFrequency0Mhz != null) add(Detail("Center 0", "$centerFrequency0Mhz MHz"))
    if (centerFrequency1Mhz != null) add(Detail("Center 1", "$centerFrequency1Mhz MHz"))
    add(Detail("Security", security.joinToString(" / ") { it.label }.ifEmpty { "—" }))
    add(Detail("Cipher", ciphers.joinToString(" / ").ifEmpty { "None" }))
    add(Detail("WPS", "$hasWps"))
    add(Detail("MFP (802.11w)", managementFrameProtection.label))
    add(Detail("FTM responder", "$isFtmResponder"))
    if (isPasspoint) add(Detail("Passpoint", "true"))
  }
