package dev.thomasbuilds.spectre.analysis

import dev.thomasbuilds.spectre.model.DistanceConfidence
import kotlin.math.log10
import kotlin.math.pow

object Distance {
  private fun fsplAtOneMeter(freqMhz: Int): Double = 20.0 * log10(freqMhz.toDouble()) - 27.55

  fun fromWifiRssi(
    rssi: Int,
    freqMhz: Int,
    txPowerDbm: Double = 20.0,
    exponent: Double = defaultExponentForFreq(freqMhz)
  ): Double {
    if (rssi >= 0) return 0.5
    val pathLossDb = (txPowerDbm - rssi).coerceAtLeast(0.0)
    val pl0 = fsplAtOneMeter(freqMhz)
    val excess = (pathLossDb - pl0).coerceAtLeast(0.0)
    return 10.0.pow(excess / (10.0 * exponent)).coerceAtLeast(0.1)
  }

  private fun defaultExponentForFreq(freqMhz: Int): Double =
    when {
      freqMhz < 3000 -> 4.0
      freqMhz < 5950 -> 4.5
      else -> 5.0
    }

  fun fromBleRssi(
    rssi: Int,
    rssiAtOneMeterDbm: Int? = null,
    exponent: Double = 2.5
  ): Double {
    if (rssi >= 0) return 0.5
    val rssiAtOneMeter = rssiAtOneMeterDbm ?: -59
    val delta = (rssiAtOneMeter - rssi).toDouble().coerceAtLeast(0.0)
    return 10.0.pow(delta / (10.0 * exponent)).coerceIn(0.1, BLE_PRACTICAL_MAX_M)
  }

  private const val BLE_PRACTICAL_MAX_M = 200.0

  // Timing Advance maps to distance by the network's step size: about 78.07 m per LTE step
  // and 553.46 m per GSM step.
  fun fromLteTimingAdvance(ta: Int): Double = ta * 78.07

  fun fromGsmTimingAdvance(ta: Int): Double = ta * 553.46

  fun format(meters: Double?): String {
    if (meters == null) return "—"
    return when {
      meters < 1.0 -> "<1 m"
      meters < 100.0 -> "${meters.toInt()} m"
      meters < 1000.0 -> "${(meters / 10).toInt() * 10} m"
      else -> "${"%.1f".format(meters / 1000.0)} km"
    }
  }

  fun formatWithConfidence(
    meters: Double?,
    confidence: DistanceConfidence
  ): String =
    when (confidence) {
      DistanceConfidence.NONE -> "—"
      DistanceConfidence.PENDING -> "Pending"
      DistanceConfidence.APPROXIMATE, DistanceConfidence.CALIBRATED -> format(meters)
    }
}
