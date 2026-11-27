package dev.thomasbuilds.spectre.bluetooth

import androidx.compose.runtime.Immutable
import dev.thomasbuilds.spectre.ui.list.TextFilter
import java.io.Serializable

enum class BluetoothSort(
  val label: String
) {
  SIGNAL("Signal strength"),
  ADDRESS("MAC address"),
  DETECTION("Detection order")
}

@Immutable
data class BluetoothFilters(
  val sort: BluetoothSort = BluetoothSort.SIGNAL,
  val addressTypes: Set<BleAddressType> = emptySet(),
  val manufacturer: TextFilter = TextFilter()
) : Serializable {
  val isActive: Boolean get() = addressTypes.isNotEmpty() || manufacturer.isActive

  fun accepts(signal: BluetoothSignal): Boolean =
    (addressTypes.isEmpty() || signal.addressType in addressTypes) &&
      manufacturer.accepts { token -> signal.manufacturers.any { it.matches(token) } }

  fun sorted(signals: List<BluetoothSignal>): List<BluetoothSignal> =
    when (sort) {
      BluetoothSort.SIGNAL -> signals.sortedByDescending { it.rssi }
      BluetoothSort.ADDRESS -> signals.sortedBy { it.address }
      BluetoothSort.DETECTION -> signals.sortedWith { a, b -> a.firstSeenMs.compareTo(b.firstSeenMs) }
    }
}
