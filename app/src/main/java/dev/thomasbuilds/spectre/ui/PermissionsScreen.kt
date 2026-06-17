package dev.thomasbuilds.spectre.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
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
import androidx.compose.ui.unit.dp
import dev.thomasbuilds.spectre.R

@Composable
fun PermissionsScreen(onContinue: () -> Unit) {
  val insets = WindowInsets.safeDrawing.asPaddingValues()
  Surface(
    modifier = Modifier.fillMaxSize(),
    color = MaterialTheme.colorScheme.background
  ) {
    Column(
      modifier =
        Modifier
          .fillMaxSize()
          .verticalScroll(rememberScrollState())
          .padding(insets)
          .padding(24.dp),
      horizontalAlignment = Alignment.Start
    ) {
      Row(
        modifier = Modifier.offset(x = (-32).dp),
        verticalAlignment = Alignment.CenterVertically
      ) {
        Icon(
          imageVector = ImageVector.vectorResource(R.drawable.ic_launcher_foreground),
          contentDescription = null,
          tint = MaterialTheme.colorScheme.onSurface,
          modifier = Modifier.size(96.dp)
        )
        Text(
          "Spectre",
          style = MaterialTheme.typography.displaySmall,
          color = MaterialTheme.colorScheme.onSurface,
          modifier = Modifier.offset(x = (-18).dp)
        )
      }
      Text(
        "Radio frequency scanner with recon and offensive capabilities",
        style = MaterialTheme.typography.bodyLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant
      )
      Spacer(Modifier.height(36.dp))
      Text(
        "Permissions",
        style = MaterialTheme.typography.titleLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant
      )
      Spacer(Modifier.height(12.dp))
      Row(
        title = "Phone state",
        desc = "Read visible cellular tower info: 5G, 4G, 3G, 2G."
      )
      Row(
        title = "Precise location",
        desc =
          "Required by Android to enumerate Wi-Fi access points, GNSS satellites, Bluetooth devices, and cellular cell IDs. Location data stays on your device."
      )
      Row(
        title = "Bluetooth scan",
        desc = "Discover BLE advertising devices nearby."
      )
      Row(
        title = "Notifications",
        desc = "Show the ongoing monitor notification with your live RF exposure reading."
      )
      Row(
        title = "Local network (internet)",
        desc =
          "Required by the Recon screen for host discovery + port scans + mDNS on the LAN you're connected to."
      )
      Spacer(Modifier.height(36.dp))
      Button(
        onClick = onContinue,
        modifier = Modifier.fillMaxWidth()
      ) {
        Text("Grant permissions")
      }
      Spacer(Modifier.height(24.dp))
    }
  }
}

@Composable
private fun Row(
  title: String,
  desc: String
) {
  Column(
    modifier = Modifier.padding(vertical = 10.dp),
    verticalArrangement = Arrangement.spacedBy(2.dp)
  ) {
    Text(
      title,
      style = MaterialTheme.typography.titleMedium,
      color = MaterialTheme.colorScheme.onSurface
    )
    Text(
      desc,
      style = MaterialTheme.typography.bodySmall,
      color = MaterialTheme.colorScheme.onSurfaceVariant
    )
  }
}
