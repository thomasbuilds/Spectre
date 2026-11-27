package dev.thomasbuilds.spectre.ui.kit

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

@Composable
internal fun SubScreen(
  title: String,
  onBack: () -> Unit,
  modifier: Modifier = Modifier,
  help: String? = null,
  contentPadding: PaddingValues = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
  content: LazyListScope.() -> Unit
) {
  var showHelp by rememberSaveable { mutableStateOf(false) }
  Surface(modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
    Column(Modifier.fillMaxSize().safeDrawingPadding()) {
      Row(
        Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
      ) {
        IconButton(onClick = onBack) {
          Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back")
        }
        Text(
          title,
          Modifier.weight(1f).semantics { heading() },
          style = MaterialTheme.typography.titleLarge,
          fontWeight = FontWeight.Medium
        )
        if (help != null) InfoButton(title = title, onClick = { showHelp = true })
      }
      LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = contentPadding,
        content = content
      )
    }
  }
  if (showHelp && help != null) MessageDialog(title = title, text = help, onDismiss = { showHelp = false })
}

@Composable
internal fun SectionHeader(
  title: String,
  modifier: Modifier = Modifier,
  icon: ImageVector? = null
) {
  Row(modifier, verticalAlignment = Alignment.CenterVertically) {
    if (icon != null) {
      Icon(icon, contentDescription = null, modifier = Modifier.size(18.dp), tint = MaterialTheme.colorScheme.primary)
      Spacer(Modifier.width(8.dp))
    }
    Text(
      title,
      Modifier.semantics { heading() },
      style = MaterialTheme.typography.titleMedium,
      color = MaterialTheme.colorScheme.primary
    )
  }
}

@Composable
internal fun MessageDialog(
  title: String,
  text: String,
  onDismiss: () -> Unit,
  confirmLabel: String = "Got it",
  onConfirm: () -> Unit = onDismiss,
  dismissLabel: String? = null
) {
  AlertDialog(
    onDismissRequest = onDismiss,
    confirmButton = { TextButton(onClick = onConfirm) { Text(confirmLabel) } },
    dismissButton = dismissLabel?.let { label -> { TextButton(onClick = onDismiss) { Text(label) } } },
    title = { Text(title) },
    text = { Text(text, Modifier.verticalScroll(rememberScrollState())) }
  )
}

@Composable
internal fun InfoButton(
  title: String,
  onClick: () -> Unit,
  modifier: Modifier = Modifier,
  buttonSize: Dp = 28.dp,
  iconSize: Dp = 18.dp
) {
  IconButton(onClick = onClick, modifier = modifier.size(buttonSize)) {
    Icon(
      Icons.Outlined.Info,
      contentDescription = "Help: $title",
      modifier = Modifier.size(iconSize),
      tint = MaterialTheme.colorScheme.onSurfaceVariant
    )
  }
}
