package dev.thomasbuilds.spectre.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.HelpOutline
import androidx.compose.material.icons.automirrored.rounded.OpenInNew
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.rounded.Brightness6
import androidx.compose.material.icons.rounded.FavoriteBorder
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.thomasbuilds.spectre.R
import dev.thomasbuilds.spectre.ui.kit.SectionHeader
import dev.thomasbuilds.spectre.ui.kit.SubScreen
import dev.thomasbuilds.spectre.ui.kit.copyToClipboard

private const val GITHUB_REPO_URL = "https://github.com/thomasbuilds/Spectre"
private const val BTC_ADDRESS = "bc1qphgd8leqsf06qlm6jjxpuw2rtq9f9hdjnhfluh"
private const val XMR_ADDRESS = "85fzziWM1pH77HWHNhE6aAN9uaHrLL7CMFA72rVecmMDR1fUjfv9YmS6GGeiV3hDEn7e9d8v4hfMSRmmEp171fpR4nipNfZ"

@Composable
fun SettingsScreen(
  viewModel: SettingsViewModel,
  onBack: () -> Unit,
  onOpenHelp: () -> Unit
) {
  val settings by viewModel.settings.collectAsStateWithLifecycle()
  val context = LocalContext.current
  val version =
    remember(context) {
      context.packageManager
        .getPackageInfo(context.packageName, 0)
        .versionName
        .orEmpty()
    }
  SubScreen(title = "Settings", onBack = onBack) {
    item(key = "appearance", contentType = "section") {
      SettingsSection("Appearance", Icons.Rounded.Brightness6) {
        Column(Modifier.selectableGroup()) {
          ThemeMode.entries.forEach { mode ->
            ThemeRow(mode.label, selected = settings?.themeMode == mode, onSelect = { viewModel.setThemeMode(mode) })
          }
        }
      }
    }
    item(key = "help", contentType = "section") {
      SettingsSection("Help", Icons.AutoMirrored.Rounded.HelpOutline) {
        SettingsRow(Modifier.clickable(onClick = onOpenHelp), verticalPadding = 12.dp) {
          Column {
            Text("Help & glossary", style = MaterialTheme.typography.bodyLarge)
            Text(
              "What every screen / metric / heuristic means",
              style = MaterialTheme.typography.bodySmall,
              color = MaterialTheme.colorScheme.onSurfaceVariant
            )
          }
          Text(
            "›",
            Modifier.clearAndSetSemantics {},
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant
          )
        }
      }
    }
    item(key = "sponsor", contentType = "section") {
      SettingsSection("Sponsor", Icons.Rounded.FavoriteBorder) { SponsorTiles() }
    }
    item(key = "about", contentType = "section") {
      SettingsSection("About", Icons.Outlined.Info) {
        GitHubRow()
        InfoRow("Version", version)
        InfoRow("Package", context.packageName)
        Text(
          "Spectre is a radio frequency scanner with recon and offensive capabilities. It listens for cellular, Wi-Fi, Bluetooth, and GNSS signals nearby.",
          Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp), horizontalArrangement = Arrangement.End) {
          TextButton(onClick = viewModel::reset) { Text("Reset to defaults") }
        }
      }
    }
    item(key = "end", contentType = "spacer") { Spacer(Modifier.height(24.dp)) }
  }
}

@Composable
private fun SettingsRow(
  modifier: Modifier = Modifier,
  verticalPadding: Dp = 10.dp,
  content: @Composable RowScope.() -> Unit
) {
  Row(
    modifier.fillMaxWidth().heightIn(min = 56.dp).padding(horizontal = 16.dp, vertical = verticalPadding),
    horizontalArrangement = Arrangement.SpaceBetween,
    verticalAlignment = Alignment.CenterVertically,
    content = content
  )
}

@Composable
private fun SettingsSection(
  title: String,
  icon: ImageVector,
  content: @Composable ColumnScope.() -> Unit
) {
  Column(Modifier.padding(vertical = 8.dp)) {
    SectionHeader(title, Modifier.padding(horizontal = 16.dp, vertical = 8.dp), icon)
    Surface(Modifier.fillMaxWidth(), shape = RoundedCornerShape(14.dp), color = MaterialTheme.colorScheme.surface) {
      Column(content = content)
    }
  }
}

@Composable
private fun ThemeRow(
  label: String,
  selected: Boolean,
  onSelect: () -> Unit
) {
  Row(
    Modifier
      .fillMaxWidth()
      .selectable(selected = selected, role = Role.RadioButton, onClick = onSelect)
      .padding(horizontal = 16.dp, vertical = 10.dp),
    verticalAlignment = Alignment.CenterVertically
  ) {
    RadioButton(selected = selected, onClick = null, modifier = Modifier.minimumInteractiveComponentSize())
    Spacer(Modifier.width(8.dp))
    Text(label, style = MaterialTheme.typography.bodyLarge)
  }
}

@Composable
private fun InfoRow(
  label: String,
  value: String
) {
  SettingsRow(Modifier.semantics(mergeDescendants = true) {}) {
    Text(label, style = MaterialTheme.typography.bodyLarge)
    Text(value, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
  }
}

@Composable
private fun GitHubRow() {
  val uriHandler = LocalUriHandler.current
  SettingsRow(
    Modifier.clickable {
      try {
        uriHandler.openUri(GITHUB_REPO_URL)
      } catch (_: IllegalArgumentException) {
      }
    }
  ) {
    Row(verticalAlignment = Alignment.CenterVertically) {
      Icon(ImageVector.vectorResource(R.drawable.ic_github), contentDescription = null, modifier = Modifier.size(20.dp))
      Spacer(Modifier.width(12.dp))
      Text("GitHub repo", style = MaterialTheme.typography.bodyLarge)
    }
    Icon(
      Icons.AutoMirrored.Rounded.OpenInNew,
      contentDescription = "Opens in browser",
      modifier = Modifier.size(18.dp),
      tint = MaterialTheme.colorScheme.onSurfaceVariant
    )
  }
}

@Composable
private fun SponsorTiles() {
  Text(
    "Spectre is free, open-source, and ad-free. If you find it useful, you can support the project and my broader open source work.",
    Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
    style = MaterialTheme.typography.bodySmall,
    color = MaterialTheme.colorScheme.onSurfaceVariant
  )
  Column(
    Modifier.padding(start = 16.dp, top = 4.dp, end = 16.dp, bottom = 10.dp),
    verticalArrangement = Arrangement.spacedBy(8.dp)
  ) {
    SponsorTile("Bitcoin", BTC_ADDRESS)
    SponsorTile("Monero", XMR_ADDRESS)
  }
}

@Composable
private fun SponsorTile(
  coin: String,
  address: String
) {
  val context = LocalContext.current
  Column(
    Modifier
      .fillMaxWidth()
      .clip(RoundedCornerShape(8.dp))
      .background(MaterialTheme.colorScheme.surfaceVariant)
      .clickable { context.copyToClipboard("$coin address", address) }
      .padding(horizontal = 12.dp, vertical = 10.dp)
  ) {
    Row(verticalAlignment = Alignment.CenterVertically) {
      Text(coin, style = MaterialTheme.typography.labelMedium)
      Text("  · tap to copy", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
    Text(
      address,
      Modifier.padding(top = 4.dp),
      style = MaterialTheme.typography.labelSmall,
      fontFamily = FontFamily.Monospace
    )
  }
}
