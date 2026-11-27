package dev.thomasbuilds.spectre.monitor

import dev.thomasbuilds.spectre.bluetooth.BluetoothSignal
import dev.thomasbuilds.spectre.bluetooth.receivedPowerMw
import dev.thomasbuilds.spectre.cellular.CellSignal
import dev.thomasbuilds.spectre.cellular.receivedPowerMw
import dev.thomasbuilds.spectre.core.Source
import dev.thomasbuilds.spectre.core.SourceState
import dev.thomasbuilds.spectre.core.SourceStatus
import dev.thomasbuilds.spectre.gnss.GnssSignal
import dev.thomasbuilds.spectre.wifi.WifiSignal
import dev.thomasbuilds.spectre.wifi.receivedPowerMw
import kotlin.math.log10

internal const val EXPOSURE_FLOOR_DBM = -120.0

data class Exposure(
  val totalDbm: Double? = null,
  val cellularDbm: Double? = null,
  val wifiDbm: Double? = null,
  val bluetoothDbm: Double? = null
)

data class ScanState(
  val cellular: SourceState<CellSignal> = SourceState(),
  val wifi: SourceState<WifiSignal> = SourceState(),
  val bluetooth: SourceState<BluetoothSignal> = SourceState(),
  val gnss: SourceState<GnssSignal> = SourceState(),
  val wifiThrottled: Boolean = false,
  val exposure: Exposure = Exposure()
) {
  operator fun get(source: Source): SourceState<*> =
    when (source) {
      Source.CELLULAR -> cellular
      Source.WIFI -> wifi
      Source.BLUETOOTH -> bluetooth
      Source.GNSS -> gnss
    }
}

internal fun exposureOf(
  cellular: SourceState<CellSignal>,
  wifi: SourceState<WifiSignal>,
  bluetooth: SourceState<BluetoothSignal>
): Exposure {
  val cellularMw = cellular.signals.receivedPowerMw()
  val wifiMw = wifi.signals.receivedPowerMw()
  val bluetoothMw = bluetooth.signals.receivedPowerMw()
  val measured = listOf(cellular, wifi, bluetooth).all { it.status == SourceStatus.OK && it.ready }
  return Exposure(
    totalDbm = if (measured) (mwToDbm(cellularMw + wifiMw + bluetoothMw) ?: EXPOSURE_FLOOR_DBM).coerceAtLeast(EXPOSURE_FLOOR_DBM) else null,
    cellularDbm = mwToDbm(cellularMw),
    wifiDbm = mwToDbm(wifiMw),
    bluetoothDbm = mwToDbm(bluetoothMw)
  )
}

private fun mwToDbm(mw: Double): Double? = if (mw > 0.0) 10 * log10(mw) else null
