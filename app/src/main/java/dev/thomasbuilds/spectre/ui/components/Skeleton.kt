package dev.thomasbuilds.spectre.ui.components

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

@Composable
fun SkeletonBar(
  width: Dp,
  height: Dp,
  alpha: State<Float>,
  cornerRadius: Dp = 4.dp
) {
  Box(
    modifier =
      Modifier
        .width(width)
        .height(height)
        // Read the animated alpha in the draw phase so the shimmer never recomposes anything.
        .graphicsLayer { this.alpha = alpha.value }
        .background(
          MaterialTheme.colorScheme.onSurface.copy(alpha = 0.18f),
          RoundedCornerShape(cornerRadius)
        )
  )
}

@Composable
fun rememberSkeletonAlpha(): State<Float> {
  val transition = rememberInfiniteTransition(label = "skeleton")
  return transition.animateFloat(
    initialValue = 0.35f,
    targetValue = 0.75f,
    animationSpec =
      infiniteRepeatable(
        animation = tween(durationMillis = 1_000),
        repeatMode = RepeatMode.Reverse
      ),
    label = "skeleton-alpha"
  )
}
