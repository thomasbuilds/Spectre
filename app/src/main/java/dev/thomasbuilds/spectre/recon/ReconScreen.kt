package dev.thomasbuilds.spectre.recon

import android.Manifest
import android.os.Build
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.thomasbuilds.spectre.help.LOCAL_NETWORK_HELP
import dev.thomasbuilds.spectre.ui.kit.SubScreen
import dev.thomasbuilds.spectre.ui.kit.rememberPermissionRequest

private const val INTRO =
  "Tap Scan to enumerate hosts on the LAN you're connected to.\n\nHost discovery uses TCP connect probes on common ports. mDNS uses Android's NsdManager to list services advertising on the network (AirPlay, Chromecast, IPP printers, Plex, etc.). Scanning is limited to your current LAN."

@Composable
fun ReconScreen(
  viewModel: ReconViewModel,
  onBack: () -> Unit
) {
  val subnet by viewModel.subnet.collectAsStateWithLifecycle()
  val state by viewModel.state.collectAsStateWithLifecycle()
  val requestPermission = rememberPermissionRequest(onGranted = viewModel::scan)
  var expandedHost by rememberSaveable { mutableStateOf<String?>(null) }
  val hosts = remember(state.hosts) { state.hosts.values.sortedBy { it.address.sortKey } }
  val serviceGroups =
    remember(state.services) {
      state.services
        .groupBy { it.type }
        .entries
        .sortedBy { it.key }
    }
  val scanning = state.phase == ReconPhase.SCANNING
  SubScreen("Local network scan", onBack, help = LOCAL_NETWORK_HELP) {
    item(key = "subnet", contentType = "subnet") { SubnetCard(subnet) }
    if (!state.pinnedToLan) {
      item(key = "vpn", contentType = "notice") {
        ReconCard(12.dp, Modifier.padding(top = 8.dp)) {
          Text(
            "A VPN that doesn't allow bypass is active; LAN results may be wrong",
            Modifier.padding(16.dp),
            color = MaterialTheme.colorScheme.error,
            style = MaterialTheme.typography.bodyMedium
          )
        }
      }
    }
    item(key = "controls", contentType = "controls") {
      ScanControls(
        scanning = scanning,
        canScan = subnet?.isScannable == true,
        onScan = {
          if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.CINNAMON_BUN) {
            requestPermission(listOf(Manifest.permission.ACCESS_LOCAL_NETWORK))
          } else {
            viewModel.scan()
          }
        },
        onStop = viewModel::stop
      )
    }
    if (hosts.isNotEmpty()) {
      item(key = "hosts", contentType = "label") { SectionLabel("Hosts (${hosts.size})") }
      items(hosts, key = { it.address.hostAddress.orEmpty() }, contentType = { "host" }) { host ->
        val address = host.address.hostAddress.orEmpty()
        HostRow(host, address, expanded = expandedHost == address) { expandedHost = if (expandedHost == address) null else address }
      }
      item(key = "hosts-end", contentType = "spacer") { Spacer(Modifier.height(16.dp)) }
    }
    if (serviceGroups.isNotEmpty()) {
      item(key = "services", contentType = "label") { SectionLabel("mDNS services (${state.services.size})") }
      items(serviceGroups, key = { it.key }, contentType = { "services" }) { (type, services) -> MdnsGroup(type, services) }
    }
    if (!scanning && hosts.isEmpty() && serviceGroups.isEmpty()) {
      item(key = "empty", contentType = "message") {
        Text(
          if (state.phase == ReconPhase.FINISHED) "No devices found" else INTRO,
          Modifier.padding(top = 24.dp),
          color = MaterialTheme.colorScheme.onSurfaceVariant,
          style = MaterialTheme.typography.bodyMedium
        )
      }
    }
    item(key = "end", contentType = "spacer") { Spacer(Modifier.height(24.dp)) }
  }
}

@Composable
private fun SubnetCard(subnet: LocalSubnet?) {
  ReconCard(14.dp) {
    Column(Modifier.padding(16.dp)) {
      Text("This network", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
      Spacer(Modifier.height(8.dp))
      if (subnet == null) {
        Text(
          "No usable Wi-Fi or Ethernet network",
          style = MaterialTheme.typography.bodyMedium,
          color = MaterialTheme.colorScheme.onSurfaceVariant
        )
      } else {
        InfoRow("Local IP", subnet.address.hostAddress.orEmpty())
        InfoRow("Gateway", subnet.gateway?.hostAddress ?: "—")
        InfoRow("CIDR", "/${subnet.prefixLength}")
        subnet.interfaceName?.let { InfoRow("Interface", it) }
        subnet.scanNote()?.let {
          Text(
            it,
            Modifier.padding(top = 4.dp),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.bodySmall
          )
        }
      }
    }
  }
}

@Composable
private fun ScanControls(
  scanning: Boolean,
  canScan: Boolean,
  onScan: () -> Unit,
  onStop: () -> Unit
) {
  Row(Modifier.padding(vertical = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
    Button(onClick = onScan, modifier = Modifier.weight(1f), enabled = !scanning && canScan) {
      if (scanning) {
        CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
        Spacer(Modifier.width(8.dp))
        Text("Scanning…")
      } else {
        Text("Scan local network")
      }
    }
    if (scanning) {
      OutlinedButton(onClick = onStop, border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)) {
        Text("Stop")
      }
    }
  }
}

@Composable
private fun HostRow(
  host: HostInfo,
  address: String,
  expanded: Boolean,
  onClick: () -> Unit
) {
  ReconCard(10.dp, Modifier.padding(vertical = 4.dp)) {
    Column(
      Modifier
        .fillMaxWidth()
        .clickable(onClickLabel = if (expanded) "Collapse" else "Expand", onClick = onClick)
        .semantics { stateDescription = if (expanded) "Expanded" else "Collapsed" }
        .padding(horizontal = 12.dp, vertical = 10.dp)
    ) {
      Row(verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
          Text(address, fontFamily = FontFamily.Monospace)
          host.hostname?.let {
            Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
          }
        }
        Text(
          if (host.ports.isEmpty()) "UPnP only" else "${host.ports.size} open",
          style = MaterialTheme.typography.bodyMedium,
          color = MaterialTheme.colorScheme.primary
        )
      }
      host.upnp?.server?.takeIf { it.isNotBlank() }?.let {
        Text(
          "UPnP · $it",
          Modifier.padding(top = 4.dp),
          color = MaterialTheme.colorScheme.primary,
          style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace)
        )
      }
      AnimatedVisibility(visible = expanded) {
        Column(Modifier.padding(top = 8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
          host.upnp?.location?.let {
            Text(
              "Device XML: $it",
              color = MaterialTheme.colorScheme.onSurfaceVariant,
              style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace)
            )
          }
          host.ports.forEach { port ->
            Column(Modifier.fillMaxWidth()) {
              Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("${port.service.port}", style = MaterialTheme.typography.bodySmall, fontFamily = FontFamily.Monospace)
                Text(port.service.label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
              }
              port.banner?.let {
                Text(
                  it,
                  Modifier.padding(start = 12.dp, top = 1.dp),
                  color = MaterialTheme.colorScheme.primary,
                  style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace)
                )
              }
            }
          }
        }
      }
    }
  }
}

@Composable
private fun MdnsGroup(
  type: String,
  services: List<MdnsService>
) {
  ReconCard(10.dp, Modifier.padding(vertical = 4.dp)) {
    Column(Modifier.padding(horizontal = 12.dp, vertical = 10.dp)) {
      Text(type, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.primary)
      Spacer(Modifier.height(4.dp))
      services.forEach { Text(it.name, Modifier.padding(vertical = 2.dp), style = MaterialTheme.typography.bodySmall) }
    }
  }
}

@Composable
private fun SectionLabel(text: String) {
  Text(
    text,
    Modifier.padding(vertical = 6.dp).semantics { heading() },
    color = MaterialTheme.colorScheme.onSurfaceVariant,
    style = MaterialTheme.typography.titleSmall
  )
}

@Composable
private fun InfoRow(
  label: String,
  value: String
) {
  Row(
    Modifier.fillMaxWidth().semantics(mergeDescendants = true) {}.padding(vertical = 3.dp),
    horizontalArrangement = Arrangement.SpaceBetween
  ) {
    Text(label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    Text(value, style = MaterialTheme.typography.bodyMedium, fontFamily = FontFamily.Monospace)
  }
}

@Composable
private fun ReconCard(
  cornerRadius: Dp,
  modifier: Modifier = Modifier,
  content: @Composable ColumnScope.() -> Unit
) = Card(
  modifier.fillMaxWidth(),
  shape = RoundedCornerShape(cornerRadius),
  colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
  content = content
)

private fun LocalSubnet.scanNote(): String? =
  when {
    !isScannable -> "/$prefixLength subnets are not scanned"
    scannedPrefixLength > prefixLength -> "Scanning ${scannedNetwork.hostAddress}/$scannedPrefixLength of /$prefixLength"
    else -> null
  }
