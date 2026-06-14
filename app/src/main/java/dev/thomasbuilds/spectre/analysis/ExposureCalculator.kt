package dev.thomasbuilds.spectre.analysis

import dev.thomasbuilds.spectre.model.BluetoothSignal
import dev.thomasbuilds.spectre.model.CellSignal
import dev.thomasbuilds.spectre.model.WifiSignal
import kotlin.math.log10
import kotlin.math.pow

object ExposureCalculator {
  private fun dbmToMw(dbm: Int): Double = 10.0.pow(dbm / 10.0)

  private fun mwToDbm(mw: Double): Double = 10.0 * log10(mw.coerceAtLeast(MIN_DETECTABLE_MW))

  private const val MIN_DETECTABLE_MW = 1e-15

  private fun cellularPowerMw(cells: List<CellSignal>): Double {
    // Co-channel cells (a serving cell plus its neighbors, or one channel seen by both SIMs)
    // are the same physical transmission, so count only the strongest per channel; cells with
    // no known channel can't be deduped and are summed individually.
    val (keyed, unkeyed) = cells.partition { it.channelKey != null }
    val keyedMw =
      keyed
        .groupBy { it.channelKey }
        .values
        .sumOf { group ->
          val strongest = group.maxBy { it.exposureDbm }
          dbmToMw(strongest.exposureDbm)
        }
    val unkeyedMw = unkeyed.sumOf { dbmToMw(it.exposureDbm) }
    return keyedMw + unkeyedMw
  }

  private fun wifiPowerMw(aps: List<WifiSignal>): Double = aps.sumOf { dbmToMw(it.rssi) }

  private fun bluetoothPowerMw(devices: List<BluetoothSignal>): Double = devices.sumOf { dbmToMw(it.rssi) }

  fun totalExposureDbm(
    cellular: List<CellSignal>,
    wifi: List<WifiSignal>,
    bluetooth: List<BluetoothSignal>
  ): Double {
    val totalMw = cellularPowerMw(cellular) + wifiPowerMw(wifi) + bluetoothPowerMw(bluetooth)
    if (totalMw <= MIN_DETECTABLE_MW) return LOWER_DBM
    return mwToDbm(totalMw)
  }

  private fun dbmOrNull(mw: Double): Double? = if (mw <= MIN_DETECTABLE_MW) null else mwToDbm(mw)

  fun cellularDbm(cells: List<CellSignal>): Double? = dbmOrNull(cellularPowerMw(cells))

  fun wifiDbm(aps: List<WifiSignal>): Double? = dbmOrNull(wifiPowerMw(aps))

  fun bluetoothDbm(devices: List<BluetoothSignal>): Double? = dbmOrNull(bluetoothPowerMw(devices))

  const val LOWER_DBM = -120.0
  const val UPPER_DBM = 0.0
}
