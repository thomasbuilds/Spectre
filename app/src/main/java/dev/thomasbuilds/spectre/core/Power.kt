package dev.thomasbuilds.spectre.core

import kotlin.math.pow

private const val RSSI_SMOOTHING = 0.3

internal fun smoothRssi(
  previous: Double?,
  sample: Int
): Double = previous?.let { it + RSSI_SMOOTHING * (sample - it) } ?: sample.toDouble()

internal fun dbmToMw(dbm: Number): Double = 10.0.pow(dbm.toDouble() / 10)
