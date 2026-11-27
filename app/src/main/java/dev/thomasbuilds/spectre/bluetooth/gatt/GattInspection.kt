package dev.thomasbuilds.spectre.bluetooth.gatt

import android.bluetooth.BluetoothGattCharacteristic
import androidx.compose.runtime.Immutable

enum class GattProperty(
  val mask: Int,
  val label: String
) {
  BROADCAST(BluetoothGattCharacteristic.PROPERTY_BROADCAST, "broadcast"),
  READ(BluetoothGattCharacteristic.PROPERTY_READ, "read"),
  WRITE_NO_RESPONSE(BluetoothGattCharacteristic.PROPERTY_WRITE_NO_RESPONSE, "write-no-resp"),
  WRITE(BluetoothGattCharacteristic.PROPERTY_WRITE, "write"),
  NOTIFY(BluetoothGattCharacteristic.PROPERTY_NOTIFY, "notify"),
  INDICATE(BluetoothGattCharacteristic.PROPERTY_INDICATE, "indicate"),
  SIGNED_WRITE(BluetoothGattCharacteristic.PROPERTY_SIGNED_WRITE, "signed-write"),
  EXTENDED_PROPERTIES(BluetoothGattCharacteristic.PROPERTY_EXTENDED_PROPS, "extended")
}

sealed interface GattReading {
  data object Restricted : GattReading

  data class Failed(
    val status: Int
  ) : GattReading

  data class Value(
    val text: String,
    val hex: String
  ) : GattReading
}

@Immutable
data class GattCharacteristic(
  val uuid: String,
  val name: String?,
  val properties: Set<GattProperty>,
  val reading: GattReading?
)

data class GattService(
  val uuid: String,
  val name: String?,
  val characteristics: List<GattCharacteristic>
)

@Immutable
sealed interface GattInspection {
  sealed interface Busy : GattInspection

  data object Connecting : Busy

  data object Discovering : Busy

  data class Reading(
    val done: Int,
    val total: Int
  ) : Busy

  data class Done(
    val services: List<GattService>,
    val note: String?
  ) : GattInspection

  data class Failed(
    val reason: String
  ) : GattInspection
}

data class DeviceInspection(
  val address: String,
  val inspection: GattInspection
)
