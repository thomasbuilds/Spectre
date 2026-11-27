package dev.thomasbuilds.spectre.onboarding

import android.Manifest
import android.os.Build
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import dev.thomasbuilds.spectre.R
import dev.thomasbuilds.spectre.core.Source

private class PermissionGroup(
  val title: String,
  val description: String,
  val permissions: List<String> = emptyList()
)

private val PERMISSION_GROUPS: List<PermissionGroup> =
  buildList {
    add(PermissionGroup("Phone state", "Read visible cellular tower info: 5G, 4G, 3G, 2G."))
    add(
      PermissionGroup(
        "Precise location",
        "Required by Android to enumerate Wi-Fi access points, GNSS satellites, Bluetooth devices, and cellular cell IDs. Location data stays on your device."
      )
    )
    add(PermissionGroup("Nearby devices", "Discover BLE advertising devices nearby and connect to the ones you inspect."))
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
      add(
        PermissionGroup(
          "Notifications",
          "Show the ongoing monitor notification with your live RF exposure reading.",
          listOf(Manifest.permission.POST_NOTIFICATIONS)
        )
      )
    }
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.CINNAMON_BUN) {
      add(
        PermissionGroup(
          "Local network",
          "Discover hosts, probe ports, and browse mDNS services on the LAN you're connected to.",
          listOf(Manifest.permission.ACCESS_LOCAL_NETWORK)
        )
      )
    }
  }

internal val ONBOARDING_PERMISSIONS: List<String> =
  Source.entries.flatMap { it.permissions }.distinct() + PERMISSION_GROUPS.flatMap { it.permissions }

@Composable
fun PermissionsScreen(onContinue: () -> Unit) {
  Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
    Column(
      Modifier
        .fillMaxSize()
        .verticalScroll(rememberScrollState())
        .safeDrawingPadding()
        .padding(24.dp)
    ) {
      Row(
        Modifier.height(96.dp),
        horizontalArrangement = Arrangement.spacedBy(14.dp),
        verticalAlignment = Alignment.CenterVertically
      ) {
        Icon(ImageVector.vectorResource(R.drawable.ic_logo), contentDescription = null)
        Text("Spectre", Modifier.semantics { heading() }, style = MaterialTheme.typography.displaySmall)
      }
      Text(
        "Radio frequency scanner with recon and offensive capabilities",
        style = MaterialTheme.typography.bodyLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant
      )
      Spacer(Modifier.height(36.dp))
      Text(
        "Permissions",
        Modifier.semantics { heading() },
        style = MaterialTheme.typography.titleLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant
      )
      Spacer(Modifier.height(12.dp))
      PERMISSION_GROUPS.forEach { PermissionRow(it.title, it.description) }
      Spacer(Modifier.height(36.dp))
      Button(onClick = onContinue, modifier = Modifier.fillMaxWidth()) {
        Text("Grant permissions")
      }
      Spacer(Modifier.height(24.dp))
    }
  }
}

@Composable
private fun PermissionRow(
  title: String,
  description: String
) {
  Column(Modifier.semantics(mergeDescendants = true) {}.padding(vertical = 10.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
    Text(title, style = MaterialTheme.typography.titleMedium)
    Text(description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
  }
}
