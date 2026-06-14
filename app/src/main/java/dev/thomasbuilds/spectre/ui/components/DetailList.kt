package dev.thomasbuilds.spectre.ui.components

import android.bluetooth.BluetoothDevice
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Campaign
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.thomasbuilds.spectre.analysis.Distance
import dev.thomasbuilds.spectre.model.BluetoothSignal
import dev.thomasbuilds.spectre.model.CellSignal
import dev.thomasbuilds.spectre.model.DetailEntry
import dev.thomasbuilds.spectre.model.DistanceConfidence
import dev.thomasbuilds.spectre.model.GnssSignal
import dev.thomasbuilds.spectre.model.ScanState
import dev.thomasbuilds.spectre.model.WifiSignal
import dev.thomasbuilds.spectre.ui.SignalSource

private fun titleFor(source: SignalSource): String =
  when (source) {
    SignalSource.CELLULAR -> "Cellular Towers"
    SignalSource.WIFI -> "WiFi Access Points"
    SignalSource.BLUETOOTH -> "Bluetooth Devices"
    SignalSource.GNSS -> "GNSS Satellites"
  }

fun LazyListScope.detailListSection(
  state: ScanState,
  source: SignalSource,
  holder: DetailListState,
  expandedKey: String?,
  anchorIndex: Int,
  onToggle: (String, Int) -> Unit,
  onOpenRecon: () -> Unit,
  showStaleWifi: Boolean,
  showStaleBluetooth: Boolean,
  resolveBleDevice: (String) -> BluetoothDevice?
) {
  when (source) {
    SignalSource.CELLULAR -> {
      val ordered = pinAtIndex(state.cellular.sortedByDescending { it.dbm }, expandedKey, anchorIndex) { it.identifier }
      item(key = "detail-header", contentType = "detailHeader") {
        DetailCardHeader(source, state, holder, summary = null)
      }
      if (state.cellular.isEmpty()) {
        item(key = "detail-bottom", contentType = "detailBottom") { DetailCardBottomText("Nothing detected") }
      } else {
        itemsIndexed(ordered, key = { _, c -> c.identifier }, contentType = { _, _ -> "detailRow" }) { idx, c ->
          DetailRowContainer {
            ExpandableRow(
              expanded = expandedKey == c.identifier,
              onClick = { onToggle(c.identifier, idx) },
              header = { CellHeader(c) },
              details = c.details
            )
          }
        }
        item(key = "detail-footer", contentType = "detailFooter") { DetailCardFooter() }
      }
    }

    SignalSource.WIFI -> {
      val visible = if (showStaleWifi) state.wifi else state.wifi.filterNot { it.isStale }
      val filtered = visible.filter { wifiMatches(it, holder) }
      val sorted =
        when (holder.wifiSort) {
          WifiSort.SIGNAL -> filtered.sortedByDescending { it.rssi }
          WifiSort.NAME -> filtered.sortedBy { it.ssid.lowercase() }
          WifiSort.DETECTION -> filtered.sortedBy { it.firstSeenMs }
        }
      val ordered = pinAtIndex(sorted, expandedKey, anchorIndex) { it.bssid.ifEmpty { it.ssid } }
      val summary = if (wifiAnyFilterActive(holder)) "Showing ${filtered.size} of ${visible.size}" else null
      item(key = "detail-header", contentType = "detailHeader") {
        DetailCardHeader(source, state, holder, summary)
      }
      when {
        visible.isEmpty() -> {
          item(key = "detail-bottom", contentType = "detailBottom") { DetailCardBottomText("Nothing detected") }
        }

        ordered.isEmpty() -> {
          item(key = "detail-bottom", contentType = "detailBottom") { DetailCardBottomText("No APs match the current filter") }
        }

        else -> {
          itemsIndexed(ordered, key = { _, w -> w.bssid.ifEmpty { w.ssid } }, contentType = { _, _ -> "detailRow" }) { idx, w ->
            val rowKey = w.bssid.ifEmpty { w.ssid }
            DetailRowContainer {
              ExpandableRow(
                expanded = expandedKey == rowKey,
                onClick = { onToggle(rowKey, idx) },
                header = { WifiHeader(w) },
                details = w.details,
                dimmed = w.isStale,
                footer =
                  if (w.isConnected) {
                    { WifiScanButton(onClick = onOpenRecon) }
                  } else {
                    null
                  }
              )
            }
          }
          item(key = "detail-footer", contentType = "detailFooter") { DetailCardFooter() }
        }
      }
    }

    SignalSource.BLUETOOTH -> {
      val visible = if (showStaleBluetooth) state.bluetooth else state.bluetooth.filterNot { it.isStale }
      val filterTokens = parseFilterTokens(holder.btManufacturerFilter)
      val filtered =
        if (filterTokens.isEmpty()) {
          visible
        } else {
          when (holder.btFilterMode) {
            BluetoothFilterMode.INCLUDE -> visible.filter { matchesManufacturerFilter(it.details, filterTokens) }
            BluetoothFilterMode.EXCLUDE -> visible.filterNot { matchesManufacturerFilter(it.details, filterTokens) }
          }
        }
      val sorted =
        when (holder.btSort) {
          BluetoothSort.DBM -> filtered.sortedByDescending { it.rssi }
          BluetoothSort.MAC -> filtered.sortedBy { it.mac }
          BluetoothSort.DETECTION -> filtered.sortedBy { it.firstSeenMs }
        }
      val frozen = holder.btFrozenList
      val ordered =
        if (expandedKey != null && frozen != null) {
          val live = sorted.associateBy { it.mac }
          frozen.map { live[it.mac] ?: it }
        } else {
          sorted
        }
      val summary = if (filterTokens.isNotEmpty()) "Showing ${filtered.size} of ${visible.size}" else null
      item(key = "detail-header", contentType = "detailHeader") {
        DetailCardHeader(source, state, holder, summary)
      }
      when {
        visible.isEmpty() -> {
          item(key = "detail-bottom", contentType = "detailBottom") { DetailCardBottomText("Nothing detected") }
        }

        ordered.isEmpty() -> {
          item(key = "detail-bottom", contentType = "detailBottom") {
            DetailCardBottomText("No devices match the current filter")
          }
        }

        else -> {
          itemsIndexed(ordered, key = { _, b -> b.mac }, contentType = { _, _ -> "detailRow" }) { idx, b ->
            DetailRowContainer {
              BluetoothExpandable(
                device = b,
                expanded = expandedKey == b.mac,
                onToggle = {
                  holder.btFrozenList = if (expandedKey == b.mac) null else ordered
                  onToggle(b.mac, idx)
                },
                resolveBleDevice = resolveBleDevice
              )
            }
          }
          item(key = "detail-footer", contentType = "detailFooter") { DetailCardFooter() }
        }
      }
    }

    SignalSource.GNSS -> {
      val ordered =
        pinAtIndex(state.gnss.sortedByDescending { it.cn0DbHz }, expandedKey, anchorIndex) {
          "${it.constellation.label}-${it.svid}"
        }
      item(key = "detail-header", contentType = "detailHeader") {
        DetailCardHeader(source, state, holder, summary = null)
      }
      if (state.gnss.isEmpty()) {
        item(key = "detail-bottom", contentType = "detailBottom") { DetailCardBottomText("Nothing detected") }
      } else {
        itemsIndexed(
          ordered,
          key = { _, g -> "${g.constellation.label}-${g.svid}" },
          contentType = { _, _ -> "detailRow" }
        ) { idx, g ->
          val rowKey = "${g.constellation.label}-${g.svid}"
          DetailRowContainer {
            ExpandableRow(
              expanded = expandedKey == rowKey,
              onClick = { onToggle(rowKey, idx) },
              header = { GnssHeader(g) },
              details = g.details
            )
          }
        }
        item(key = "detail-footer", contentType = "detailFooter") { DetailCardFooter() }
      }
    }
  }
}

private fun wifiMatches(
  ap: WifiSignal,
  holder: DetailListState
): Boolean {
  val bandOk = holder.wifiBandNames.isEmpty() || ap.band.name in holder.wifiBandNames
  val secOk = holder.wifiSecurityNames.isEmpty() || ap.securityTypes.any { it.name in holder.wifiSecurityNames }
  val wpsOk =
    when (holder.wifiWpsFilter) {
      WifiWpsFilter.ANY -> true
      WifiWpsFilter.ONLY_WPS -> ap.hasWps
      WifiWpsFilter.ONLY_NON_WPS -> !ap.hasWps
    }
  return bandOk && secOk && wpsOk
}

private fun wifiAnyFilterActive(holder: DetailListState): Boolean =
  holder.wifiBandNames.isNotEmpty() ||
    holder.wifiSecurityNames.isNotEmpty() ||
    holder.wifiWpsFilter != WifiWpsFilter.ANY

@Composable
private fun DetailCardHeader(
  source: SignalSource,
  state: ScanState,
  holder: DetailListState,
  summary: String?
) {
  Column(
    modifier =
      Modifier
        .fillMaxWidth()
        .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(topStart = 14.dp, topEnd = 14.dp))
        .padding(start = 16.dp, end = 16.dp, top = 16.dp)
  ) {
    Row(verticalAlignment = Alignment.CenterVertically) {
      Text(
        titleFor(source),
        style = MaterialTheme.typography.titleMedium,
        color = MaterialTheme.colorScheme.onSurface
      )
      if (source == SignalSource.CELLULAR) {
        InfoButton(
          title = "Cellular monitoring",
          body = dev.thomasbuilds.spectre.ui.HelpEntries.Cellular,
          buttonSize = 20.dp,
          iconSize = 14.dp
        )
      }
      Spacer(Modifier.weight(1f))
      when (source) {
        SignalSource.BLUETOOTH -> {
          Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
          ) {
            IconButton(
              onClick = { holder.bleAdvertiseSheetOpen = true },
              modifier = Modifier.size(32.dp)
            ) {
              Icon(
                imageVector = Icons.Rounded.Campaign,
                contentDescription = "Broadcast a BLE beacon",
                tint = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.size(20.dp)
              )
            }
            IconButton(
              onClick = { holder.btSheetOpen = true },
              modifier = Modifier.size(32.dp)
            ) {
              Icon(
                imageVector = Icons.Rounded.Tune,
                contentDescription = "Bluetooth view options",
                tint = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.size(20.dp)
              )
            }
          }
        }

        SignalSource.WIFI -> {
          Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
          ) {
            if (state.wifiScanThrottlingOn) WifiThrottledTag()
            IconButton(
              onClick = { holder.wifiSheetOpen = true },
              modifier = Modifier.size(32.dp)
            ) {
              Icon(
                imageVector = Icons.Rounded.Tune,
                contentDescription = "WiFi view options",
                tint = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.size(20.dp)
              )
            }
          }
        }

        else -> {
          Unit
        }
      }
    }
    if (source == SignalSource.CELLULAR && state.cellularConnection != null) {
      Text(
        "Modem: ${state.cellularConnection}",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(top = 4.dp)
      )
    }
    HorizontalDivider(
      color = MaterialTheme.colorScheme.outline,
      modifier = Modifier.padding(vertical = 10.dp)
    )
    if (summary != null) {
      Text(
        summary,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(bottom = 8.dp)
      )
    }
  }
}

@Composable
private fun DetailRowContainer(content: @Composable () -> Unit) {
  Box(
    modifier =
      Modifier
        .fillMaxWidth()
        .background(MaterialTheme.colorScheme.surface)
        .padding(horizontal = 16.dp)
  ) {
    content()
  }
}

@Composable
private fun DetailCardFooter() {
  Box(
    modifier =
      Modifier
        .fillMaxWidth()
        .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(bottomStart = 14.dp, bottomEnd = 14.dp))
        .height(16.dp)
  )
}

@Composable
private fun DetailCardBottomText(text: String) {
  Column(
    modifier =
      Modifier
        .fillMaxWidth()
        .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(bottomStart = 14.dp, bottomEnd = 14.dp))
        .padding(start = 16.dp, end = 16.dp, bottom = 16.dp)
  ) {
    Text(
      text,
      style = MaterialTheme.typography.bodySmall,
      color = MaterialTheme.colorScheme.onSurfaceVariant
    )
  }
}

private fun <T> pinAtIndex(
  items: List<T>,
  pinnedId: String?,
  anchor: Int,
  idOf: (T) -> String
): List<T> {
  if (pinnedId == null || anchor < 0) return items
  val currentIdx = items.indexOfFirst { idOf(it) == pinnedId }
  if (currentIdx < 0 || currentIdx == anchor) return items
  val item = items[currentIdx]
  val without = items.toMutableList().apply { removeAt(currentIdx) }
  val target = anchor.coerceIn(0, without.size)
  without.add(target, item)
  return without
}

private fun parseFilterTokens(input: String): List<String> =
  input
    .split(',')
    .map { it.trim().lowercase() }
    .filter { it.isNotBlank() }

private fun matchesManufacturerFilter(
  details: List<DetailEntry>,
  tokens: List<String>
): Boolean {
  if (tokens.isEmpty()) return true
  val manuEntry =
    details
      .firstOrNull { it.label == "Manufacturer" }
      ?.value
      ?.lowercase()
      ?: return false
  return tokens.any { it in manuEntry }
}

@Composable
private fun ExpandableRow(
  expanded: Boolean,
  onClick: () -> Unit,
  header: @Composable () -> Unit,
  details: List<DetailEntry>,
  footer: (@Composable () -> Unit)? = null,
  dimmed: Boolean = false
) {
  Column(
    modifier =
      Modifier
        .fillMaxWidth()
        .alpha(if (dimmed) STALE_ROW_ALPHA else 1f)
        .clickable(onClick = onClick)
        .padding(vertical = 6.dp)
  ) {
    header()
    AnimatedVisibility(visible = expanded) {
      Column {
        DetailsTable(details)
        footer?.invoke()
      }
    }
  }
}

@Composable
private fun WifiScanButton(onClick: () -> Unit) {
  OutlinedButton(
    onClick = onClick,
    modifier = Modifier.padding(top = 4.dp, start = 4.dp)
  ) {
    Text("Scan local network")
  }
}

@Composable
internal fun DetailsTable(details: List<DetailEntry>) {
  if (details.isEmpty()) return
  SelectionContainer {
    Column(
      modifier =
        Modifier
          .fillMaxWidth()
          .padding(top = 8.dp, bottom = 4.dp)
          .background(
            MaterialTheme.colorScheme.surfaceVariant,
            RoundedCornerShape(10.dp)
          ).padding(horizontal = 12.dp, vertical = 8.dp),
      verticalArrangement = Arrangement.spacedBy(2.dp)
    ) {
      details.forEach { entry ->
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.SpaceBetween
        ) {
          Text(
            entry.label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
          )
          Text(
            entry.value,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurface,
            fontFamily = FontFamily.Monospace
          )
        }
      }
    }
  }
}

@Composable
internal fun Badge(text: String) {
  Box(
    modifier =
      Modifier
        .background(
          MaterialTheme.colorScheme.surfaceVariant,
          RoundedCornerShape(6.dp)
        ).padding(horizontal = 6.dp, vertical = 2.dp)
  ) {
    Text(
      text,
      style = MaterialTheme.typography.labelMedium,
      color = MaterialTheme.colorScheme.onSurface
    )
  }
}

@Composable
private fun Pill(
  text: String,
  color: Color,
  modifier: Modifier
) {
  Box(modifier = modifier.padding(horizontal = 10.dp, vertical = 2.dp)) {
    Text(
      text,
      style = MaterialTheme.typography.labelMedium,
      color = color
    )
  }
}

@Composable
private fun ConnectedChip() =
  Pill(
    text = "Connected",
    color = LIVE_GREEN,
    modifier = Modifier.background(LIVE_GREEN.copy(alpha = 0.15f), RoundedCornerShape(percent = 50))
  )

@Composable
internal fun PairedChip() =
  Pill(
    text = "Paired",
    color = MaterialTheme.colorScheme.primary,
    modifier =
      Modifier.border(
        width = 1.dp,
        color = MaterialTheme.colorScheme.primary,
        shape = RoundedCornerShape(percent = 50)
      )
  )

@Composable
private fun CellHeader(c: CellSignal) {
  Row(
    modifier = Modifier.fillMaxWidth(),
    verticalAlignment = Alignment.CenterVertically
  ) {
    Badge(c.type.displayName)
    Spacer(Modifier.width(12.dp))
    Column(modifier = Modifier.weight(1f)) {
      Text(
        c.operator ?: "Unknown carrier",
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurface
      )
      Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
          "${c.dbm} dBm",
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        val hasDistance =
          c.distanceConfidence == DistanceConfidence.APPROXIMATE ||
            c.distanceConfidence == DistanceConfidence.CALIBRATED
        if (hasDistance) {
          Text(
            "·  ${Distance.formatWithConfidence(c.distanceMeters, c.distanceConfidence)}",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
          )
        }
      }
    }
    if (c.isConnected) {
      ConnectedChip()
    }
  }
}

@Composable
private fun WifiHeader(w: WifiSignal) {
  Row(
    modifier = Modifier.fillMaxWidth(),
    verticalAlignment = Alignment.CenterVertically
  ) {
    Badge(w.band.label)
    Spacer(Modifier.width(12.dp))
    Column(modifier = Modifier.weight(1f)) {
      Text(
        w.ssid,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurface
      )
      Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
          "${w.rssi} dBm",
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
          "·  ${Distance.formatWithConfidence(w.distanceMeters, w.distanceConfidence)}",
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant
        )
      }
    }
    if (w.isConnected) {
      ConnectedChip()
    }
  }
}

@Composable
internal fun ConnectableTag() =
  Pill(
    text = "Connectable",
    color = MaterialTheme.colorScheme.onSurfaceVariant,
    modifier = Modifier.background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(percent = 50))
  )

@Composable
private fun GnssHeader(g: GnssSignal) {
  Row(
    modifier = Modifier.fillMaxWidth(),
    verticalAlignment = Alignment.CenterVertically
  ) {
    Badge(g.constellation.shortLabel)
    Spacer(Modifier.width(12.dp))
    Column(modifier = Modifier.weight(1f)) {
      Text(
        "SV ${g.svid}",
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurface
      )
      Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
          "${g.cn0DbHz.toInt()} dBHz",
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
          "·  el ${g.elevationDeg.toInt()}°",
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant
        )
      }
    }
  }
}

@Composable
private fun WifiThrottledTag() {
  var showDialog by remember { mutableStateOf(false) }
  Box(
    modifier =
      Modifier
        .clip(RoundedCornerShape(percent = 50))
        .clickable { showDialog = true }
        .background(MaterialTheme.colorScheme.surfaceVariant)
        .padding(horizontal = 10.dp, vertical = 2.dp)
  ) {
    Text(
      "THROTTLED",
      style =
        MaterialTheme.typography.labelMedium.copy(
          fontWeight = FontWeight.Bold,
          letterSpacing = 0.5.sp,
          fontSize = 10.sp
        ),
      color = MaterialTheme.colorScheme.onSurfaceVariant
    )
  }
  if (showDialog) {
    HelpDialog(
      title = "WiFi scan throttling",
      body = dev.thomasbuilds.spectre.ui.HelpEntries.WifiThrottle
    ) { showDialog = false }
  }
}

private val LIVE_GREEN = Color(0xFF22C55E)

internal const val STALE_ROW_ALPHA = 0.5f
