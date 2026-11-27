package dev.thomasbuilds.spectre.ui.list

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import dev.thomasbuilds.spectre.core.Source
import dev.thomasbuilds.spectre.ui.kit.InfoButton

private const val DIMMED_ALPHA = 0.5f
private val TOP_CORNERS = RoundedCornerShape(topStart = 14.dp, topEnd = 14.dp)
private val BOTTOM_CORNERS = RoundedCornerShape(bottomStart = 14.dp, bottomEnd = 14.dp)

data class ListRows<out T>(
  val rows: List<T>,
  val detected: Int,
  val departed: Set<String> = emptySet(),
  val source: Source? = null
)

internal fun <T> LazyListScope.signalList(
  title: String,
  rows: ListRows<T>,
  key: (T) -> String,
  expandedKey: String?,
  onToggle: (String) -> Unit,
  header: @Composable (T) -> Unit,
  details: @Composable ColumnScope.(T) -> Unit,
  onHelp: (() -> Unit)? = null,
  helpTitle: String = title,
  isFiltered: Boolean = false,
  filteredEmptyText: String = "Nothing matches the current filter",
  isDimmed: (T) -> Boolean = { false },
  subtitle: String? = null,
  actions: @Composable RowScope.() -> Unit = {}
) {
  if (rows.source == null) return
  item(key = "list-header", contentType = "listHeader") {
    Column(
      Modifier
        .padding(top = 24.dp)
        .fillMaxWidth()
        .background(MaterialTheme.colorScheme.surface, TOP_CORNERS)
        .padding(start = 16.dp, top = 16.dp, end = 16.dp)
    ) {
      Row(verticalAlignment = Alignment.CenterVertically) {
        Text(title, Modifier.semantics { heading() }, style = MaterialTheme.typography.titleMedium)
        if (onHelp != null) InfoButton(title = helpTitle, onClick = onHelp, buttonSize = 20.dp, iconSize = 14.dp)
        Spacer(Modifier.weight(1f))
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically, content = actions)
      }
      if (subtitle != null) {
        Text(
          subtitle,
          Modifier.padding(top = 4.dp),
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.primary
        )
      }
      HorizontalDivider(Modifier.padding(vertical = 10.dp))
      if (isFiltered) {
        Text(
          "Showing ${rows.rows.size - rows.departed.size} of ${rows.detected}",
          Modifier.padding(bottom = 8.dp),
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant
        )
      }
    }
  }
  if (rows.rows.isEmpty()) {
    item(key = "list-empty", contentType = "listEmpty") {
      Text(
        if (rows.detected == 0) "Nothing detected" else filteredEmptyText,
        Modifier
          .fillMaxWidth()
          .background(MaterialTheme.colorScheme.surface, BOTTOM_CORNERS)
          .padding(start = 16.dp, end = 16.dp, bottom = 16.dp),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant
      )
    }
    return
  }
  items(rows.rows, key = key, contentType = { "listRow" }) { row ->
    val rowKey = key(row)
    ExpandableRow(
      expanded = expandedKey == rowKey,
      onToggle = { onToggle(rowKey) },
      header = { header(row) },
      modifier = Modifier.background(MaterialTheme.colorScheme.surface).padding(horizontal = 16.dp),
      dimmed = isDimmed(row) || rowKey in rows.departed,
      details = { details(row) }
    )
  }
  item(key = "list-footer", contentType = "listFooter") {
    Box(Modifier.fillMaxWidth().height(16.dp).background(MaterialTheme.colorScheme.surface, BOTTOM_CORNERS))
  }
}

@Composable
private fun ExpandableRow(
  expanded: Boolean,
  onToggle: () -> Unit,
  header: @Composable () -> Unit,
  modifier: Modifier = Modifier,
  dimmed: Boolean = false,
  details: @Composable ColumnScope.() -> Unit
) {
  val expansion = if (expanded) "Expanded" else "Collapsed"
  Column(
    modifier
      .fillMaxWidth()
      .alpha(if (dimmed) DIMMED_ALPHA else 1f)
      .clickable(
        interactionSource = null,
        indication = null,
        onClickLabel = if (expanded) "Collapse" else "Expand",
        onClick = onToggle
      ).semantics { stateDescription = if (dimmed) "$expansion, no longer detected" else expansion }
      .padding(vertical = 6.dp)
  ) {
    header()
    AnimatedVisibility(visible = expanded) {
      Column(content = details)
    }
  }
}
