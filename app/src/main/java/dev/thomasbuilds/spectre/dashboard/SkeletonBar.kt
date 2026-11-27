package dev.thomasbuilds.spectre.dashboard

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

@Composable
internal fun SkeletonBar(
  width: Dp,
  height: Dp,
  modifier: Modifier = Modifier,
  cornerRadius: Dp = 4.dp
) {
  val alpha by rememberInfiniteTransition(label = "skeleton").animateFloat(
    initialValue = 0.35f,
    targetValue = 0.75f,
    animationSpec = infiniteRepeatable(tween(durationMillis = 1_000), RepeatMode.Reverse),
    label = "skeleton-alpha"
  )
  Box(
    modifier
      .size(width, height)
      .graphicsLayer { this.alpha = alpha }
      .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.18f), RoundedCornerShape(cornerRadius))
  )
}
