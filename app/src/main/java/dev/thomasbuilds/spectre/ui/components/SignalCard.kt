package dev.thomasbuilds.spectre.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import dev.thomasbuilds.spectre.model.ScannerStatus

@Composable
fun SignalCard(
  title: String,
  icon: ImageVector,
  count: Int,
  countLabel: String,
  strongest: String,
  status: ScannerStatus,
  selected: Boolean,
  modifier: Modifier = Modifier,
  ready: Boolean = true,
  onClick: () -> Unit
) {
  val border =
    if (selected) {
      BorderStroke(1.dp, MaterialTheme.colorScheme.primary)
    } else {
      BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
    }
  Card(
    onClick = onClick,
    modifier = modifier.height(132.dp),
    shape = RoundedCornerShape(14.dp),
    border = border,
    colors =
      CardDefaults.cardColors(
        containerColor = MaterialTheme.colorScheme.surface
      )
  ) {
    Column(
      modifier =
        Modifier
          .padding(14.dp)
          .fillMaxSize()
    ) {
      Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(
          imageVector = icon,
          contentDescription = null,
          tint = MaterialTheme.colorScheme.onSurface,
          modifier = Modifier.size(20.dp)
        )
        Spacer(Modifier.width(6.dp))
        Text(
          title,
          style = MaterialTheme.typography.titleMedium,
          color = MaterialTheme.colorScheme.onSurface
        )
      }
      Spacer(Modifier.weight(1f))
      val statusMessage = status.message
      when {
        statusMessage != null -> {
          Text(
            statusMessage,
            style = MaterialTheme.typography.bodySmall,
            color =
              if (status == ScannerStatus.NO_PERMISSION) {
                MaterialTheme.colorScheme.error
              } else {
                MaterialTheme.colorScheme.onSurfaceVariant
              }
          )
        }

        !ready -> {
          WarmingUpRow()
        }

        else -> {
          Text(
            "$count $countLabel",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurface
          )
          Text(
            strongest,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
          )
        }
      }
    }
  }
}

@Composable
private fun WarmingUpRow() {
  val alpha = rememberSkeletonAlpha()
  Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
    SkeletonBar(width = 90.dp, height = 18.dp, alpha = alpha)
    SkeletonBar(width = 130.dp, height = 12.dp, alpha = alpha)
  }
}
