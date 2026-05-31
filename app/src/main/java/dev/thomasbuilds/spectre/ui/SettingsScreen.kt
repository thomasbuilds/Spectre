package dev.thomasbuilds.spectre.ui

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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.HelpOutline
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.rounded.Brightness6
import androidx.compose.material.icons.rounded.FavoriteBorder
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.thomasbuilds.spectre.settings.Settings
import dev.thomasbuilds.spectre.settings.SettingsRepository
import dev.thomasbuilds.spectre.settings.ThemeMode
import dev.thomasbuilds.spectre.ui.components.GithubRepoRow
import dev.thomasbuilds.spectre.ui.components.SponsorSectionContent
import kotlinx.coroutines.launch

@Composable
fun SettingsScreen(
  repository: SettingsRepository,
  appVersion: String,
  onBack: () -> Unit,
  onOpenHelp: () -> Unit
) {
  val scope = rememberCoroutineScope()
  val settings by repository.settings.collectAsStateWithLifecycle(initialValue = Settings.DEFAULTS)

  val insets = WindowInsets.safeDrawing.asPaddingValues()
  Surface(
    modifier = Modifier.fillMaxSize(),
    color = MaterialTheme.colorScheme.background
  ) {
    Column(
      modifier =
        Modifier
          .fillMaxSize()
          .padding(insets)
    ) {
      TopBar(onBack = onBack)
      Column(
        modifier =
          Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 8.dp)
      ) {
        Section(
          title = "Appearance",
          icon = Icons.Rounded.Brightness6
        ) {
          ThemeMode.entries.forEach { mode ->
            ChoiceRow(
              label = themeLabel(mode),
              selected = settings.themeMode == mode,
              onClick = { scope.launch { repository.setThemeMode(mode) } }
            )
          }
        }
        Section(title = "Help", icon = Icons.AutoMirrored.Rounded.HelpOutline) {
          Row(
            modifier =
              Modifier
                .fillMaxWidth()
                .clickable(onClick = onOpenHelp)
                .heightIn(min = SETTINGS_ROW_MIN_HEIGHT)
                .padding(horizontal = 16.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
          ) {
            Column {
              Text(
                "Help & glossary",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface
              )
              Text(
                "What every screen / metric / heuristic means",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
              )
            }
            Text(
              "›",
              style = MaterialTheme.typography.titleLarge,
              color = MaterialTheme.colorScheme.onSurfaceVariant
            )
          }
        }
        Section(
          title = "Sponsor",
          icon = Icons.Rounded.FavoriteBorder
        ) {
          SponsorSectionContent()
        }
        Section(
          title = "About",
          icon = Icons.Outlined.Info
        ) {
          GithubRepoRow()
          InfoRow("Version", appVersion)
          InfoRow("Package", "dev.thomasbuilds.spectre")
          Text(
            "Spectre is a radio frequency scanner with recon and offensive capabilities. It listens for cellular, WiFi, Bluetooth, and GNSS signals nearby.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
          )
          Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.End
          ) {
            TextButton(onClick = { scope.launch { repository.resetToDefaults() } }) {
              Text("Reset to defaults")
            }
          }
        }
        Spacer(Modifier.height(24.dp))
      }
    }
  }
}

@Composable
private fun TopBar(onBack: () -> Unit) {
  Row(
    modifier =
      Modifier
        .fillMaxWidth()
        .padding(horizontal = 4.dp, vertical = 8.dp),
    verticalAlignment = Alignment.CenterVertically
  ) {
    IconButton(onClick = onBack) {
      Icon(
        imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
        contentDescription = "Back",
        tint = MaterialTheme.colorScheme.onSurface
      )
    }
    Text(
      "Settings",
      style = MaterialTheme.typography.titleLarge,
      fontWeight = FontWeight.Medium,
      color = MaterialTheme.colorScheme.onSurface
    )
  }
}

@Composable
private fun Section(
  title: String,
  icon: ImageVector,
  content: @Composable () -> Unit
) {
  Column(modifier = Modifier.padding(vertical = 8.dp)) {
    Row(
      verticalAlignment = Alignment.CenterVertically,
      modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
    ) {
      Icon(
        imageVector = icon,
        contentDescription = null,
        tint = MaterialTheme.colorScheme.primary,
        modifier = Modifier.size(18.dp)
      )
      Spacer(Modifier.size(8.dp))
      Text(
        title,
        style = MaterialTheme.typography.titleMedium,
        color = MaterialTheme.colorScheme.primary
      )
    }
    Surface(
      modifier = Modifier.fillMaxWidth(),
      shape = RoundedCornerShape(14.dp),
      color = MaterialTheme.colorScheme.surface
    ) {
      Column(modifier = Modifier.padding(vertical = 8.dp)) { content() }
    }
  }
}

@Composable
private fun ChoiceRow(
  label: String,
  selected: Boolean,
  onClick: () -> Unit
) {
  Row(
    modifier =
      Modifier
        .fillMaxWidth()
        .clickable(onClick = onClick)
        .padding(horizontal = 16.dp, vertical = 10.dp),
    verticalAlignment = Alignment.CenterVertically
  ) {
    RadioButton(selected = selected, onClick = onClick)
    Spacer(Modifier.size(8.dp))
    Text(
      label,
      style = MaterialTheme.typography.bodyLarge,
      color = MaterialTheme.colorScheme.onSurface
    )
  }
}

@Composable
private fun InfoRow(
  label: String,
  value: String
) {
  Row(
    modifier =
      Modifier
        .fillMaxWidth()
        .heightIn(min = SETTINGS_ROW_MIN_HEIGHT)
        .padding(horizontal = 16.dp, vertical = 10.dp),
    horizontalArrangement = Arrangement.SpaceBetween,
    verticalAlignment = Alignment.CenterVertically
  ) {
    Text(label, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurface)
    Text(value, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
  }
}

private val SETTINGS_ROW_MIN_HEIGHT = 56.dp

private fun themeLabel(mode: ThemeMode): String =
  when (mode) {
    ThemeMode.SYSTEM -> "System default"
    ThemeMode.LIGHT -> "Light"
    ThemeMode.DARK -> "Dark"
  }
