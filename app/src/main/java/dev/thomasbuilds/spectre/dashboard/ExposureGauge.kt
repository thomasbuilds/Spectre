package dev.thomasbuilds.spectre.dashboard

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.unit.dp
import dev.thomasbuilds.spectre.monitor.EXPOSURE_FLOOR_DBM
import dev.thomasbuilds.spectre.ui.theme.ExposureGreen
import dev.thomasbuilds.spectre.ui.theme.ExposureOrange
import dev.thomasbuilds.spectre.ui.theme.ExposureRed
import dev.thomasbuilds.spectre.ui.theme.ExposureYellow
import kotlin.math.roundToInt

private const val FULL_SCALE_DBM = 0.0
private const val ANIMATION_MS = 800
private const val START_ANGLE_DEGREES = 135f
private const val SWEEP_DEGREES = 270f

@Composable
internal fun ExposureGauge(
  totalDbm: Double?,
  modifier: Modifier = Modifier
) {
  val total = totalDbm?.roundToInt()
  val animatedDbm = animateFloatAsState(total?.toFloat() ?: EXPOSURE_FLOOR_DBM.toFloat(), tween(ANIMATION_MS), label = "totalDbm")
  val shownDbm by remember { derivedStateOf { animatedDbm.value.roundToInt() } }
  val band by remember { derivedStateOf { bandColor(animatedDbm.value) } }
  val animatedColor by animateColorAsState(
    if (total != null) band else MaterialTheme.colorScheme.onSurfaceVariant,
    tween(ANIMATION_MS),
    label = "color"
  )
  val track = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
  Box(
    modifier
      .size(240.dp)
      .clearAndSetSemantics { contentDescription = if (total != null) "RF exposure $total dBm" else "Measuring RF exposure" },
    contentAlignment = Alignment.Center
  ) {
    Canvas(Modifier.matchParentSize()) {
      val stroke = Stroke(18.dp.toPx(), cap = StrokeCap.Round)
      val inset = stroke.width / 2

      fun arc(
        color: Color,
        sweep: Float
      ) = drawArc(
        color,
        START_ANGLE_DEGREES,
        sweep,
        useCenter = false,
        topLeft = Offset(inset, inset),
        size = Size(size.width - stroke.width, size.height - stroke.width),
        style = stroke
      )
      arc(track, SWEEP_DEGREES)
      if (total != null) arc(animatedColor, SWEEP_DEGREES * fraction(animatedDbm.value))
    }
    if (total != null) {
      Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text("$shownDbm", style = MaterialTheme.typography.displayLarge, color = animatedColor)
        Text("dBm", style = MaterialTheme.typography.titleMedium, color = animatedColor)
      }
      Text(
        "RF exposure",
        Modifier.offset(y = 84.dp),
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant
      )
    } else {
      Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        SkeletonBar(96.dp, 56.dp, cornerRadius = 8.dp)
        SkeletonBar(48.dp, 18.dp)
      }
    }
  }
}

private fun fraction(dbm: Float): Float = ((dbm - EXPOSURE_FLOOR_DBM) / (FULL_SCALE_DBM - EXPOSURE_FLOOR_DBM)).toFloat().coerceIn(0f, 1f)

private fun bandColor(dbm: Float): Color =
  when {
    dbm <= -90f -> ExposureGreen
    dbm <= -60f -> ExposureYellow
    dbm <= -30f -> ExposureOrange
    else -> ExposureRed
  }
