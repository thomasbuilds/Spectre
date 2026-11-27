package dev.thomasbuilds.spectre.help

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import dev.thomasbuilds.spectre.ui.kit.SubScreen

@Composable
fun HelpScreen(onBack: () -> Unit) =
  SubScreen(title = "Help & Glossary", onBack = onBack, contentPadding = PaddingValues(horizontal = 16.dp)) {
    item(key = "intro", contentType = "intro") {
      Text(
        "Plain-language definitions for the labels, acronyms, and readings shown across the app.",
        Modifier.padding(start = 4.dp, top = 4.dp, end = 4.dp),
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant
      )
    }
    glossary()
    item(key = "end", contentType = "spacer") { Spacer(Modifier.height(28.dp)) }
  }
