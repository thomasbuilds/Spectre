package dev.thomasbuilds.spectre.bluetooth

import android.bluetooth.BluetoothDevice
import androidx.compose.runtime.Immutable
import dev.thomasbuilds.spectre.core.dbmToMw

enum class BleAddressType(
  val label: String
) {
  PUBLIC("Public"),
  RANDOM("Random"),
  ANONYMOUS("Anonymous")
}

@JvmInline
value class BlePhy(
  private val value: Int
) {
  val label: String
    get() =
      when (value) {
        BluetoothDevice.PHY_LE_1M -> "LE 1M"
        BluetoothDevice.PHY_LE_2M -> "LE 2M"
        BluetoothDevice.PHY_LE_CODED -> "LE Coded"
        else -> "Unknown ($value)"
      }
}

data class Manufacturer(
  private val id: Int,
  val name: String?
) {
  val idHex: String get() = id.toShort().toPrefixedHex()

  fun matches(token: String): Boolean = (name ?: idHex).contains(token, ignoreCase = true) || idHex.equals(token, ignoreCase = true)
}

@Immutable
data class BluetoothSignal(
  val device: BluetoothDevice,
  val name: String?,
  val rssi: Int,
  val distanceMeters: Double?,
  val vendor: String?,
  val addressType: BleAddressType?,
  val isConnectable: Boolean,
  val alternatesConnectable: Boolean,
  val isBonded: Boolean,
  val txPowerDbm: Int?,
  val ibeaconPowerDbm: Int?,
  val primaryPhy: BlePhy?,
  val secondaryPhy: BlePhy?,
  val serviceUuids: List<String>,
  val serviceDataCount: Int,
  val manufacturers: List<Manufacturer>,
  val appleTypes: List<String>,
  val googleType: String?,
  val microsoftType: String?,
  val advertiseFlags: Int?,
  val advertisementHex: String?,
  val firstSeenMs: Long,
  val isStale: Boolean
) {
  val address: String get() = device.address

  val everConnectable: Boolean get() = isConnectable || alternatesConnectable
}

internal fun List<BluetoothSignal>.receivedPowerMw(): Double = filterNot { it.isStale }.sumOf { dbmToMw(it.rssi) }
