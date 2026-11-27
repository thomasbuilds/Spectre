package dev.thomasbuilds.spectre.ui.list

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.FilterAlt
import androidx.compose.material.icons.rounded.History
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import dev.thomasbuilds.spectre.ui.kit.SectionHeader
import dev.thomasbuilds.spectre.ui.kit.SingleChoice

@Composable
internal fun FilterSection(
  title: String,
  icon: ImageVector,
  content: @Composable () -> Unit
) = Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
  SectionHeader(title, icon = icon)
  content()
}

@Composable
internal fun TextFilterSection(
  title: String,
  filter: TextFilter,
  onFilterChange: (TextFilter) -> Unit,
  placeholder: String,
  hint: String
) {
  val colors = MaterialTheme.colorScheme
  val typography = MaterialTheme.typography
  FilterSection(title, Icons.Rounded.FilterAlt) {
    SingleChoice(FilterMode.entries, filter.mode, label = { it.label }, onSelect = { onFilterChange(filter.copy(mode = it)) })
    Box(
      Modifier
        .fillMaxWidth()
        .border(1.dp, colors.outline, RoundedCornerShape(10.dp))
        .padding(horizontal = 14.dp, vertical = 12.dp)
    ) {
      BasicTextField(
        value = filter.text,
        onValueChange = { onFilterChange(filter.copy(text = it)) },
        modifier = Modifier.fillMaxWidth(),
        textStyle = typography.bodyMedium.copy(color = colors.onSurface),
        singleLine = true,
        cursorBrush = SolidColor(colors.primary),
        decorationBox = { field ->
          if (filter.text.isEmpty()) Text(placeholder, style = typography.bodyMedium, color = colors.onSurfaceVariant)
          field()
        }
      )
    }
    Text(hint, Modifier.padding(start = 4.dp, top = 2.dp), style = typography.bodySmall, color = colors.onSurfaceVariant)
  }
}

@Composable
internal fun ShowStaleSwitch(
  checked: Boolean,
  onCheckedChange: (Boolean) -> Unit,
  description: String
) {
  FilterSection("No longer detected", Icons.Rounded.History) {
    SwitchRow(text = description, checked = checked, onCheckedChange = onCheckedChange)
  }
}

@Composable
private fun SwitchRow(
  text: String,
  checked: Boolean,
  onCheckedChange: (Boolean) -> Unit,
  modifier: Modifier = Modifier
) {
  Row(
    modifier.toggleable(value = checked, role = Role.Switch, onValueChange = onCheckedChange),
    verticalAlignment = Alignment.CenterVertically
  ) {
    Text(
      text,
      Modifier.weight(1f),
      style = MaterialTheme.typography.bodySmall,
      color = MaterialTheme.colorScheme.onSurfaceVariant
    )
    Spacer(Modifier.width(12.dp))
    Switch(checked = checked, onCheckedChange = null, modifier = Modifier.minimumInteractiveComponentSize())
  }
}
