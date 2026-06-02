package dev.thomasbuilds.spectre.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import dev.thomasbuilds.spectre.model.BluetoothSignal

enum class BluetoothSort(
  val label: String
) {
  DBM("Signal strength"),
  MAC("MAC address"),
  DETECTION("Detection order")
}

enum class BluetoothFilterMode(
  val label: String
) {
  INCLUDE("Include"),
  EXCLUDE("Exclude")
}

enum class WifiSort(
  val label: String
) {
  SIGNAL("Signal strength"),
  NAME("Name"),
  DETECTION("Detection order")
}

enum class WifiWpsFilter(
  val label: String
) {
  ANY("any"),
  ONLY_WPS("true"),
  ONLY_NON_WPS("false")
}

class DetailListState(
  btSort: BluetoothSort = BluetoothSort.DBM,
  btManufacturerFilter: String = "",
  btFilterMode: BluetoothFilterMode = BluetoothFilterMode.INCLUDE,
  wifiSort: WifiSort = WifiSort.SIGNAL,
  wifiBandNames: List<String> = emptyList(),
  wifiSecurityNames: List<String> = emptyList(),
  wifiWpsFilter: WifiWpsFilter = WifiWpsFilter.ANY
) {
  var btSort by mutableStateOf(btSort)
  var btManufacturerFilter by mutableStateOf(btManufacturerFilter)
  var btFilterMode by mutableStateOf(btFilterMode)
  var btSheetOpen by mutableStateOf(false)
  var bleAdvertiseSheetOpen by mutableStateOf(false)
  var btFrozenList by mutableStateOf<List<BluetoothSignal>?>(null)

  var wifiSort by mutableStateOf(wifiSort)
  var wifiBandNames by mutableStateOf(wifiBandNames)
  var wifiSecurityNames by mutableStateOf(wifiSecurityNames)
  var wifiWpsFilter by mutableStateOf(wifiWpsFilter)
  var wifiSheetOpen by mutableStateOf(false)

  companion object {
    val Saver: Saver<DetailListState, Any> =
      listSaver(
        save = {
          listOf(
            it.btSort.name,
            it.btManufacturerFilter,
            it.btFilterMode.name,
            it.wifiSort.name,
            it.wifiBandNames.joinToString(","),
            it.wifiSecurityNames.joinToString(","),
            it.wifiWpsFilter.name
          )
        },
        restore = {
          DetailListState(
            btSort = BluetoothSort.valueOf(it[0]),
            btManufacturerFilter = it[1],
            btFilterMode = BluetoothFilterMode.valueOf(it[2]),
            wifiSort = WifiSort.valueOf(it[3]),
            wifiBandNames = it[4].split(",").filter { s -> s.isNotEmpty() },
            wifiSecurityNames = it[5].split(",").filter { s -> s.isNotEmpty() },
            wifiWpsFilter = WifiWpsFilter.valueOf(it[6])
          )
        }
      )
  }
}

@Composable
fun rememberDetailListState(): DetailListState = rememberSaveable(saver = DetailListState.Saver) { DetailListState() }
