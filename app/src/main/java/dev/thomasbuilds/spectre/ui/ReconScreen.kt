package dev.thomasbuilds.spectre.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import dev.thomasbuilds.spectre.recon.HostInfo
import dev.thomasbuilds.spectre.recon.LanScanner
import dev.thomasbuilds.spectre.recon.MdnsBrowser
import dev.thomasbuilds.spectre.recon.MdnsService
import dev.thomasbuilds.spectre.recon.ReconMerge
import dev.thomasbuilds.spectre.recon.SsdpDiscovery
import dev.thomasbuilds.spectre.recon.SubnetInfo
import dev.thomasbuilds.spectre.ui.components.InfoButton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private const val DISCOVERY_WINDOW_MS = 8_000L

@Composable
fun ReconScreen(onBack: () -> Unit) {
  val context = LocalContext.current
  val scanner = remember { LanScanner(context) }
  val mdnsScanner = remember { MdnsBrowser(context) }
  val ssdpScanner = remember { SsdpDiscovery(context) }
  val scope = rememberCoroutineScope()

  var subnet by remember { mutableStateOf<SubnetInfo?>(null) }
  var diagnostic by remember { mutableStateOf("") }
  val hostsFlow = remember { MutableStateFlow<List<HostInfo>>(emptyList()) }
  val mdnsFlow = remember { MutableStateFlow<List<MdnsService>>(emptyList()) }
  val hosts by hostsFlow.collectAsState()
  val mdns by mdnsFlow.collectAsState()
  var scanning by remember { mutableStateOf(false) }
  var scanJob by remember { mutableStateOf<Job?>(null) }
  var expandedHost by remember { mutableStateOf<String?>(null) }
  var showDiagnostic by remember { mutableStateOf(false) }
  var localBlocked by remember { mutableStateOf(false) }

  LaunchedEffect(Unit) {
    while (subnet == null) {
      val info = withContext(Dispatchers.IO) { scanner.currentSubnet() }
      subnet = info
      diagnostic = scanner.lastDiagnostic
      if (info != null) break
      kotlinx.coroutines.delay(2_000)
    }
  }

  val insets = WindowInsets.safeDrawing.asPaddingValues()
  Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
    Column(modifier = Modifier.fillMaxSize().padding(insets)) {
      BackTopBar(title = "Recon", onBack = onBack) {
        InfoButton(title = "Recon screen", body = HelpEntries.ReconScreen)
      }
      Column(
        modifier =
          Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 8.dp)
      ) {
        SubnetCard(subnet)
        if (localBlocked) {
          Spacer(Modifier.height(8.dp))
          LocalBlockedCard()
        }
        if (subnet == null && diagnostic.isNotBlank()) {
          Spacer(Modifier.height(8.dp))
          DiagnosticCard(
            text = diagnostic,
            expanded = showDiagnostic,
            onToggle = { showDiagnostic = !showDiagnostic }
          )
        }
        Spacer(Modifier.height(16.dp))

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
          Button(
            enabled = !scanning && subnet != null,
            onClick = {
              hostsFlow.value = emptyList()
              mdnsFlow.value = emptyList()
              localBlocked = false
              scanning = true
              scanJob =
                scope.launch {
                  val net = subnet ?: return@launch
                  val startMs = System.currentTimeMillis()
                  val hostJob =
                    launch {
                      scanner.scanHosts(net).collect { host ->
                        hostsFlow.update { list ->
                          (list.filterNot { it.ip == host.ip } + ReconMerge.mergeHost(list, host))
                            .sortedBy { ReconMerge.ipToLong(it.ip) }
                        }
                      }
                    }
                  val mdnsJob =
                    launch {
                      mdnsScanner.discoverAll().collect { svc ->
                        mdnsFlow.update { it + svc }
                      }
                    }
                  val ssdpJob =
                    launch {
                      ssdpScanner.scan().collect { device ->
                        hostsFlow.update { list ->
                          (list.filterNot { it.ip == device.ip } + ReconMerge.mergeSsdp(list, device))
                            .sortedBy { ReconMerge.ipToLong(it.ip) }
                        }
                      }
                    }
                  hostJob.join()
                  localBlocked = scanner.localAccessBlocked
                  ssdpJob.join()
                  val remaining = DISCOVERY_WINDOW_MS - (System.currentTimeMillis() - startMs)
                  if (remaining > 0) kotlinx.coroutines.delay(remaining)
                  mdnsJob.cancel()
                  scanning = false
                }
            },
            modifier = Modifier.weight(1f)
          ) {
            if (scanning) {
              CircularProgressIndicator(
                modifier = Modifier.size(16.dp),
                strokeWidth = 2.dp,
                color = MaterialTheme.colorScheme.onPrimary
              )
              Spacer(Modifier.width(8.dp))
              Text("Scanning…")
            } else {
              Text("Scan local network")
            }
          }
          if (scanning) {
            OutlinedButton(onClick = {
              scanJob?.cancel()
              scanning = false
            }) {
              Text("Stop")
            }
          }
        }

        Spacer(Modifier.height(16.dp))

        if (hosts.isNotEmpty()) {
          SectionLabel("Hosts (${hosts.size})")
          hosts.forEach { host ->
            HostRow(
              host = host,
              expanded = expandedHost == host.ip,
              onClick = {
                expandedHost = if (expandedHost == host.ip) null else host.ip
              }
            )
          }
          Spacer(Modifier.height(16.dp))
        }

        if (mdns.isNotEmpty()) {
          SectionLabel("mDNS services (${mdns.size})")
          val grouped = mdns.groupBy { it.type }.entries.sortedBy { it.key }
          grouped.forEach { (type, services) ->
            MdnsGroup(type, services)
          }
        }

        if (!scanning && hosts.isEmpty() && mdns.isEmpty()) {
          Spacer(Modifier.height(24.dp))
          Text(
            "Tap Scan to enumerate hosts on the LAN you're connected to.\n\n" +
              "Host discovery uses TCP connect probes on common ports. mDNS " +
              "uses Android's NsdManager to list services advertising on the " +
              "network (AirPlay, Chromecast, IPP printers, Plex, etc.). " +
              "Scanning is limited to your current LAN.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
          )
        }
        Spacer(Modifier.height(24.dp))
      }
    }
  }
}

@Composable
private fun LocalBlockedCard() {
  Card(
    modifier = Modifier.fillMaxWidth(),
    shape = RoundedCornerShape(12.dp),
    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
  ) {
    Column(modifier = Modifier.padding(16.dp)) {
      Text(
        "Local network blocked",
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.error
      )
      Spacer(Modifier.height(6.dp))
      Text(
        "Connections to the LAN were rejected by the system (EPERM). This usually means " +
          "a VPN with \"Block connections without VPN\" (lockdown) is on, or a firewall " +
          "app is blocking Spectre. Allow local network traffic in your VPN, or turn it " +
          "off, then scan again. mDNS still works because it goes through the system " +
          "service, which is why services can appear with no open ports.",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurface
      )
    }
  }
}

@Composable
private fun DiagnosticCard(
  text: String,
  expanded: Boolean,
  onToggle: () -> Unit
) {
  Card(
    modifier = Modifier.fillMaxWidth(),
    shape = RoundedCornerShape(10.dp),
    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
  ) {
    Column(
      modifier =
        Modifier
          .fillMaxWidth()
          .clickable(onClick = onToggle)
          .padding(horizontal = 14.dp, vertical = 10.dp)
    ) {
      Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
          "Subnet diagnostic",
          style = MaterialTheme.typography.titleSmall,
          color = MaterialTheme.colorScheme.error,
          modifier = Modifier.weight(1f)
        )
        Text(
          if (expanded) "hide" else "show",
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.primary
        )
      }
      AnimatedVisibility(visible = expanded) {
        Text(
          text,
          style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
          color = MaterialTheme.colorScheme.onSurface,
          modifier = Modifier.padding(top = 8.dp)
        )
      }
    }
  }
}

@Composable
private fun SubnetCard(subnet: SubnetInfo?) {
  Card(
    modifier = Modifier.fillMaxWidth(),
    shape = RoundedCornerShape(14.dp),
    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
  ) {
    Column(modifier = Modifier.padding(16.dp)) {
      Text(
        "This network",
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary
      )
      Spacer(Modifier.height(8.dp))
      if (subnet == null) {
        Text(
          "No active network. Connect to WiFi to enable recon.",
          style = MaterialTheme.typography.bodyMedium,
          color = MaterialTheme.colorScheme.onSurfaceVariant
        )
      } else {
        InfoRow("Local IP", subnet.localIp)
        InfoRow("Gateway", subnet.gatewayIp ?: "—")
        InfoRow("CIDR", "/${subnet.prefixLength}")
        subnet.interfaceName?.let { InfoRow("Interface", it) }
      }
    }
  }
}

@Composable
private fun HostRow(
  host: HostInfo,
  expanded: Boolean,
  onClick: () -> Unit
) {
  Card(
    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
    shape = RoundedCornerShape(10.dp),
    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
  ) {
    Column(
      modifier =
        Modifier
          .fillMaxWidth()
          .clickable(onClick = onClick)
          .padding(horizontal = 12.dp, vertical = 10.dp)
    ) {
      Row(verticalAlignment = Alignment.CenterVertically) {
        Column(modifier = Modifier.weight(1f)) {
          Text(
            host.ip,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurface,
            fontFamily = FontFamily.Monospace
          )
          if (!host.hostname.isNullOrBlank()) {
            Text(
              host.hostname,
              style = MaterialTheme.typography.bodySmall,
              color = MaterialTheme.colorScheme.onSurfaceVariant
            )
          }
        }
        Column(horizontalAlignment = Alignment.End) {
          val portsLabel = if (host.openPorts.isEmpty()) "UPnP only" else "${host.openPorts.size} open"
          Text(
            portsLabel,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.primary
          )
        }
      }
      if (!host.ssdpServer.isNullOrBlank()) {
        Text(
          "UPnP · ${host.ssdpServer}",
          style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
          color = MaterialTheme.colorScheme.primary,
          modifier = Modifier.padding(top = 4.dp)
        )
      }
      AnimatedVisibility(visible = expanded) {
        Column(modifier = Modifier.padding(top = 8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
          host.ssdpLocation?.let { loc ->
            Text(
              "Device XML: $loc",
              style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
              color = MaterialTheme.colorScheme.onSurfaceVariant
            )
          }
          host.openPorts.forEach { port ->
            Column(modifier = Modifier.fillMaxWidth()) {
              Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
              ) {
                Text(
                  "$port",
                  style = MaterialTheme.typography.bodySmall,
                  color = MaterialTheme.colorScheme.onSurface,
                  fontFamily = FontFamily.Monospace
                )
                Text(
                  LanScanner.labelForPort(port).ifBlank { "—" },
                  style = MaterialTheme.typography.bodySmall,
                  color = MaterialTheme.colorScheme.onSurfaceVariant
                )
              }
              host.banners[port]?.let { banner ->
                Text(
                  banner,
                  style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                  color = MaterialTheme.colorScheme.primary,
                  modifier = Modifier.padding(start = 12.dp, top = 1.dp)
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
  Card(
    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
    shape = RoundedCornerShape(10.dp),
    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
  ) {
    Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp)) {
      Text(
        ReconMerge.shortType(type),
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.primary
      )
      Spacer(Modifier.height(4.dp))
      services.distinctBy { it.name }.forEach { svc ->
        Row(
          modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
          horizontalArrangement = Arrangement.SpaceBetween
        ) {
          Text(
            svc.name,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurface
          )
          Text(
            if (svc.port > 0) ":${svc.port}" else "",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontFamily = FontFamily.Monospace
          )
        }
      }
    }
  }
}

@Composable
private fun SectionLabel(text: String) {
  Text(
    text,
    style = MaterialTheme.typography.titleSmall,
    color = MaterialTheme.colorScheme.onSurfaceVariant,
    modifier = Modifier.padding(vertical = 6.dp)
  )
}

@Composable
private fun InfoRow(
  label: String,
  value: String
) {
  Row(
    modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp),
    horizontalArrangement = Arrangement.SpaceBetween
  ) {
    Text(label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    Text(
      value,
      style = MaterialTheme.typography.bodyMedium,
      color = MaterialTheme.colorScheme.onSurface,
      fontFamily = FontFamily.Monospace
    )
  }
}
