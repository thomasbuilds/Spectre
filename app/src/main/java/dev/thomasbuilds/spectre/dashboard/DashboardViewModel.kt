package dev.thomasbuilds.spectre.dashboard

import android.bluetooth.BluetoothDevice
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.thomasbuilds.spectre.bluetooth.BluetoothFilters
import dev.thomasbuilds.spectre.bluetooth.BluetoothSignal
import dev.thomasbuilds.spectre.bluetooth.bluetoothRows
import dev.thomasbuilds.spectre.bluetooth.gatt.DeviceInspection
import dev.thomasbuilds.spectre.bluetooth.gatt.GattInspection
import dev.thomasbuilds.spectre.bluetooth.gatt.GattInspector
import dev.thomasbuilds.spectre.cellular.CellSignal
import dev.thomasbuilds.spectre.cellular.cellularRows
import dev.thomasbuilds.spectre.core.Source
import dev.thomasbuilds.spectre.core.SourceState
import dev.thomasbuilds.spectre.gnss.GnssSignal
import dev.thomasbuilds.spectre.gnss.gnssRows
import dev.thomasbuilds.spectre.monitor.Monitor
import dev.thomasbuilds.spectre.monitor.ScanState
import dev.thomasbuilds.spectre.settings.AppSettings
import dev.thomasbuilds.spectre.settings.SettingsRepository
import dev.thomasbuilds.spectre.ui.list.ListRows
import dev.thomasbuilds.spectre.wifi.WifiFilters
import dev.thomasbuilds.spectre.wifi.WifiSignal
import dev.thomasbuilds.spectre.wifi.wifiRows
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

private const val STOP_TIMEOUT_MS = 5_000L
private const val OPEN_SOURCE = "open_source"
private const val EXPANDED_KEY = "expanded_key"
private const val WIFI_FILTERS = "wifi_filters"
private const val BLUETOOTH_FILTERS = "bluetooth_filters"
private val NO_ROWS = ListRows<Nothing>(emptyList(), 0)

class DashboardViewModel(
  monitor: Monitor,
  private val repository: SettingsRepository,
  private val inspector: GattInspector,
  private val saved: SavedStateHandle
) : ViewModel() {
  private var inspectionJob: Job? = null

  val state: StateFlow<ScanState> = monitor.state
  val settings: StateFlow<AppSettings> =
    repository.settings.stateIn(
      viewModelScope,
      SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS),
      AppSettings()
    )
  val openSource: StateFlow<Source?> = saved.getStateFlow(OPEN_SOURCE, null)
  val expandedKey: StateFlow<String?> = saved.getStateFlow(EXPANDED_KEY, null)
  val wifiFilters: StateFlow<WifiFilters> = saved.getStateFlow(WIFI_FILTERS, WifiFilters())
  val bluetoothFilters: StateFlow<BluetoothFilters> = saved.getStateFlow(BLUETOOTH_FILTERS, BluetoothFilters())
  val inspection: StateFlow<DeviceInspection?>
    field = MutableStateFlow(null)

  val cellularRows: StateFlow<ListRows<CellSignal>> =
    present(Source.CELLULAR, { it.key }, state.signalsOf { it.cellular }.map { signals -> Unit to { cellularRows(signals) } })
  val wifiRows: StateFlow<ListRows<WifiSignal>> =
    present(
      Source.WIFI,
      { it.bssid },
      combine(state.signalsOf { it.wifi }, wifiFilters, settings.map { it.showStaleWifi }) { signals, filters, showStale ->
        (filters to showStale) to { wifiRows(signals, filters, showStale) }
      }
    )
  val bluetoothRows: StateFlow<ListRows<BluetoothSignal>> =
    present(
      Source.BLUETOOTH,
      { it.address },
      combine(state.signalsOf { it.bluetooth }, bluetoothFilters, settings.map { it.showStaleBluetooth }) { signals, filters, showStale ->
        (filters to showStale) to { bluetoothRows(signals, filters, showStale) }
      }
    )
  val gnssRows: StateFlow<ListRows<GnssSignal>> =
    present(Source.GNSS, { it.key }, state.signalsOf { it.gnss }.map { signals -> Unit to { gnssRows(signals) } })

  fun toggleSource(source: Source) {
    saved[OPEN_SOURCE] = source.takeIf { it != openSource.value }
    saved[EXPANDED_KEY] = null
    cancelBusyInspection(except = null)
  }

  fun closeSource() {
    saved[OPEN_SOURCE] = null
    saved[EXPANDED_KEY] = null
    cancelBusyInspection(except = null)
  }

  fun toggleRow(key: String) {
    val expanded = key.takeIf { it != expandedKey.value }
    saved[EXPANDED_KEY] = expanded
    cancelBusyInspection(except = expanded)
  }

  fun setWifiFilters(filters: WifiFilters) {
    saved[WIFI_FILTERS] = filters
  }

  fun setBluetoothFilters(filters: BluetoothFilters) {
    saved[BLUETOOTH_FILTERS] = filters
  }

  fun setShowStaleWifi(show: Boolean) {
    viewModelScope.launch { repository.setShowStaleWifi(show) }
  }

  fun setShowStaleBluetooth(show: Boolean) {
    viewModelScope.launch { repository.setShowStaleBluetooth(show) }
  }

  fun inspect(device: BluetoothDevice) {
    inspectionJob?.cancel()
    inspectionJob =
      viewModelScope.launch { inspector.inspect(device).collect { inspection.value = DeviceInspection(device.address, it) } }
  }

  private fun cancelBusyInspection(except: String?) {
    val current = inspection.value ?: return
    if (current.address == except || current.inspection !is GattInspection.Busy) return
    inspectionJob?.cancel()
    inspection.value = null
  }

  private fun <T> present(
    source: Source,
    key: (T) -> String,
    input: Flow<Pair<Any, () -> ListRows<T>>>
  ): StateFlow<ListRows<T>> {
    val last = MutableStateFlow(Presented<T>(Unit, NO_ROWS))
    return combine(openSource, expandedKey, input) { open, expanded, (basis, rows) ->
      val isOpen = open == source
      val presented = last.value.next(isOpen, expanded, basis, key, rows)
      last.value = presented
      if (isOpen) presented.rows.copy(source = source) else presented.rows
    }.flowOn(Dispatchers.Default)
      .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), NO_ROWS)
  }
}

private fun <T> Flow<ScanState>.signalsOf(select: (ScanState) -> SourceState<T>): Flow<List<T>> =
  map { select(it).signals }.distinctUntilChanged()

private class Presented<out T>(
  val basis: Any,
  val rows: ListRows<T>
)

private fun <T> Presented<T>.next(
  isOpen: Boolean,
  expandedKey: String?,
  basis: Any,
  key: (T) -> String,
  rows: () -> ListRows<T>
): Presented<T> {
  if (!isOpen) return Presented(basis, NO_ROWS)
  val current = rows()
  val isFrozen =
    expandedKey != null &&
      basis == this.basis &&
      (this.rows.rows.any { key(it) == expandedKey } || current.rows.any { key(it) == expandedKey })
  return Presented(basis, if (isFrozen) current.frozenInto(this.rows, key) else current)
}

private fun <T> ListRows<T>.frozenInto(
  previous: ListRows<T>,
  key: (T) -> String
): ListRows<T> {
  val live = rows.associateBy(key)
  val frozenKeys = previous.rows.mapTo(LinkedHashSet(), key)
  return ListRows(
    rows = previous.rows.map { live[key(it)] ?: it } + rows.filterNot { key(it) in frozenKeys },
    detected = detected,
    departed = frozenKeys - live.keys
  )
}
