package dev.thomasbuilds.spectre.ui.list

import java.util.Locale

internal fun formatDistance(meters: Double): String =
  when {
    meters < 1 -> "<1 m"
    meters < 100 -> "${meters.toInt()} m"
    meters < 1000 -> "${(meters / 10).toInt() * 10} m"
    else -> "%.1f km".format(Locale.ROOT, meters / 1000)
  }
