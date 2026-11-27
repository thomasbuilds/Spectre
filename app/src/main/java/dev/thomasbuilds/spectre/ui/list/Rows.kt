package dev.thomasbuilds.spectre.ui.list

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.thomasbuilds.spectre.ui.theme.ExposureGreen

data class Detail(
  val label: String,
  val value: String
)

enum class TagTone { BADGE, NEUTRAL, POSITIVE, OUTLINED, EMPHASIZED }

@Composable
internal fun Tag(
  text: String,
  modifier: Modifier = Modifier,
  tone: TagTone = TagTone.NEUTRAL,
  onClick: (() -> Unit)? = null
) {
  val colors = MaterialTheme.colorScheme
  val labelMedium = MaterialTheme.typography.labelMedium
  val shape = if (tone == TagTone.BADGE) RoundedCornerShape(6.dp) else CircleShape
  val decoration =
    when (tone) {
      TagTone.POSITIVE -> Modifier.background(ExposureGreen.copy(alpha = 0.15f), shape)
      TagTone.OUTLINED -> Modifier.border(1.dp, colors.primary, shape)
      TagTone.NEUTRAL, TagTone.EMPHASIZED, TagTone.BADGE -> Modifier.background(colors.surfaceVariant, shape)
    }
  Text(
    text,
    modifier
      .then(if (onClick == null) Modifier else Modifier.clip(shape).clickable(onClick = onClick))
      .then(decoration)
      .padding(horizontal = if (tone == TagTone.BADGE) 6.dp else 10.dp, vertical = 2.dp),
    color =
      when (tone) {
        TagTone.NEUTRAL, TagTone.EMPHASIZED -> colors.onSurfaceVariant
        TagTone.POSITIVE -> ExposureGreen
        TagTone.OUTLINED -> colors.primary
        TagTone.BADGE -> colors.onSurface
      },
    style =
      if (tone == TagTone.EMPHASIZED) {
        labelMedium.copy(fontWeight = FontWeight.Bold, fontSize = 10.sp, letterSpacing = 0.5.sp)
      } else {
        labelMedium
      }
  )
}

@Composable
private fun LabeledValue(
  label: String,
  value: String,
  modifier: Modifier = Modifier
) {
  Row(modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
    Text(label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    Text(value, style = MaterialTheme.typography.bodySmall, fontFamily = FontFamily.Monospace)
  }
}

@Composable
internal fun DetailTable(
  details: List<Detail>,
  modifier: Modifier = Modifier
) {
  if (details.isEmpty()) return
  SelectionContainer {
    Column(
      modifier
        .fillMaxWidth()
        .padding(top = 8.dp, bottom = 4.dp)
        .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(10.dp))
        .padding(horizontal = 12.dp, vertical = 8.dp),
      verticalArrangement = Arrangement.spacedBy(2.dp)
    ) {
      details.forEach { LabeledValue(it.label, it.value) }
    }
  }
}

@Composable
internal fun SignalRowHeader(
  tag: String,
  title: String,
  subtitle: List<String>,
  modifier: Modifier = Modifier,
  content: @Composable RowScope.() -> Unit = {}
) {
  Row(modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
    Tag(tag, tone = TagTone.BADGE)
    Spacer(Modifier.width(12.dp))
    Column(Modifier.weight(1f)) {
      Text(title, style = MaterialTheme.typography.bodyMedium)
      Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        subtitle.forEach { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
      }
    }
    Row(
      horizontalArrangement = Arrangement.spacedBy(4.dp),
      verticalAlignment = Alignment.CenterVertically,
      content = content
    )
  }
}
