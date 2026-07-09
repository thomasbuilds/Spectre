package dev.thomasbuilds.spectre.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import dev.thomasbuilds.spectre.analysis.ExposureCalculator
import dev.thomasbuilds.spectre.ui.theme.SpectreGreen
import dev.thomasbuilds.spectre.ui.theme.SpectreOrange
import dev.thomasbuilds.spectre.ui.theme.SpectreRed
import dev.thomasbuilds.spectre.ui.theme.SpectreYellow

@Composable
fun ExposureGauge(
  totalDbm: Float,
  complete: Boolean
) {
  val animatedDbm by animateFloatAsState(
    targetValue = if (complete) totalDbm else ExposureCalculator.LOWER_DBM.toFloat(),
    animationSpec = tween(durationMillis = 800),
    label = "totalDbm"
  )
  val fraction = dbmFraction(animatedDbm)
  val inactiveColor = MaterialTheme.colorScheme.onSurfaceVariant
  val trackColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)
  val targetColor = if (complete) colorForDbm(animatedDbm) else inactiveColor
  val animatedColor by animateColorAsState(
    targetValue = targetColor,
    animationSpec = tween(durationMillis = 800),
    label = "color"
  )

  Box(
    modifier = Modifier.size(240.dp),
    contentAlignment = Alignment.Center
  ) {
    Canvas(modifier = Modifier.size(240.dp)) {
      val strokeWidthPx = 18.dp.toPx()
      val arcSize = Size(size.width - strokeWidthPx, size.height - strokeWidthPx)
      val topLeft = Offset(strokeWidthPx / 2f, strokeWidthPx / 2f)
      val stroke = Stroke(width = strokeWidthPx, cap = StrokeCap.Round)

      drawArc(
        color = trackColor,
        startAngle = 135f,
        sweepAngle = 270f,
        useCenter = false,
        topLeft = topLeft,
        size = arcSize,
        style = stroke
      )
      if (complete) {
        drawArc(
          color = animatedColor,
          startAngle = 135f,
          sweepAngle = 270f * fraction,
          useCenter = false,
          topLeft = topLeft,
          size = arcSize,
          style = stroke
        )
      }
    }
    if (complete) {
      val dbmInt = animatedDbm.toInt()
      Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
          text = "$dbmInt",
          style = MaterialTheme.typography.displayLarge,
          color = animatedColor
        )
        Text(
          text = "dBm",
          style = MaterialTheme.typography.titleMedium,
          color = animatedColor
        )
      }
      Text(
        text = "RF exposure",
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.align(Alignment.Center).offset(y = 84.dp)
      )
    } else {
      Column(horizontalAlignment = Alignment.CenterHorizontally) {
        val skeletonAlpha = rememberSkeletonAlpha()
        SkeletonBar(width = 96.dp, height = 56.dp, alpha = skeletonAlpha, cornerRadius = 8.dp)
        Spacer(Modifier.height(6.dp))
        SkeletonBar(width = 48.dp, height = 18.dp, alpha = skeletonAlpha)
      }
    }
  }
}

private fun dbmFraction(dbm: Float): Float {
  val lower = ExposureCalculator.LOWER_DBM.toFloat()
  val upper = ExposureCalculator.UPPER_DBM.toFloat()
  return ((dbm - lower) / (upper - lower)).coerceIn(0f, 1f)
}

private fun colorForDbm(dbm: Float): Color =
  when {
    dbm <= -90f -> SpectreGreen
    dbm <= -60f -> SpectreYellow
    dbm <= -30f -> SpectreOrange
    else -> SpectreRed
  }
