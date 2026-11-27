package dev.thomasbuilds.spectre.ui.kit

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

@Composable
internal fun <T> SingleChoice(
  options: List<T>,
  selected: T,
  label: (T) -> String,
  onSelect: (T) -> Unit,
  modifier: Modifier = Modifier
) = MultiChoice(options, setOf(selected), label = label, onToggle = onSelect, modifier = modifier)

@Composable
internal fun <T> MultiChoice(
  options: List<T>,
  selected: Set<T>,
  label: (T) -> String,
  onToggle: (T) -> Unit,
  modifier: Modifier = Modifier
) {
  val scheme = MaterialTheme.colorScheme
  val chipColors =
    FilterChipDefaults.filterChipColors(
      containerColor = scheme.surfaceVariant,
      labelColor = scheme.onSurface,
      selectedContainerColor = scheme.primary,
      selectedLabelColor = scheme.onPrimary
    )
  FlowRow(modifier, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
    options.forEach { option ->
      val isSelected = option in selected
      FilterChip(
        selected = isSelected,
        onClick = { onToggle(option) },
        label = { Text(label(option)) },
        colors = chipColors,
        border = BorderStroke(Dp.Hairline, if (isSelected) scheme.primary else scheme.outlineVariant)
      )
    }
  }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun OptionsSheet(
  onDismiss: () -> Unit,
  content: @Composable ColumnScope.() -> Unit
) {
  ModalBottomSheet(onDismissRequest = onDismiss) {
    Column(
      Modifier
        .fillMaxWidth()
        .verticalScroll(rememberScrollState())
        .padding(start = 20.dp, top = 8.dp, end = 20.dp, bottom = 16.dp),
      verticalArrangement = Arrangement.spacedBy(24.dp),
      content = content
    )
  }
}

internal fun <T> Set<T>.toggle(item: T): Set<T> = if (item in this) this - item else this + item
