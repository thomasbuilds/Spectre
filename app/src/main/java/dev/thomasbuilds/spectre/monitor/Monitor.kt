package dev.thomasbuilds.spectre.monitor

import android.content.Context
import android.location.LocationManager
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import dev.thomasbuilds.spectre.bluetooth.BluetoothSource
import dev.thomasbuilds.spectre.cellular.CellSignal
import dev.thomasbuilds.spectre.cellular.CellularSource
import dev.thomasbuilds.spectre.core.OuiLookup
import dev.thomasbuilds.spectre.core.SourceState
import dev.thomasbuilds.spectre.core.broadcasts
import dev.thomasbuilds.spectre.core.isLocationEnabled
import dev.thomasbuilds.spectre.gnss.GnssSource
import dev.thomasbuilds.spectre.wifi.WifiSignal
import dev.thomasbuilds.spectre.wifi.WifiSource
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

private const val STOP_TIMEOUT_MS = 5_000L

private enum class DataTransport { WIFI, CELLULAR }

class Monitor(
  context: Context,
  scope: CoroutineScope
) {
  private val app = context.applicationContext
  private val oui = OuiLookup(app)
  private val wifi = WifiSource(app, oui)

  val locationEnabled: StateFlow<Boolean> =
    app.locationEnabled().stateIn(scope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), app.isLocationEnabled)

  val state: StateFlow<ScanState> =
    combine(
      CellularSource(app).states(),
      wifi.states(),
      BluetoothSource(app, oui).states(),
      GnssSource(app).states(),
      app.dataTransport()
    ) { cellular, wifi, bluetooth, gnss, transport ->
      ScanState(
        cellular = cellular.connectedOnlyOn(transport == DataTransport.CELLULAR),
        wifi = wifi.connectedOnlyOn(transport == DataTransport.WIFI),
        bluetooth = bluetooth,
        gnss = gnss,
        exposure = exposureOf(cellular, wifi, bluetooth)
      )
    }.combine(wifi.throttled()) { state, throttled -> state.copy(wifiThrottled = throttled) }
      .stateIn(scope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS, 0), ScanState())
}

@JvmName("cellularConnectedOnlyOn")
private fun SourceState<CellSignal>.connectedOnlyOn(active: Boolean): SourceState<CellSignal> =
  if (active) this else copy(signals = signals.map { if (it.isConnected) it.copy(isConnected = false) else it })

@JvmName("wifiConnectedOnlyOn")
private fun SourceState<WifiSignal>.connectedOnlyOn(active: Boolean): SourceState<WifiSignal> =
  if (active) this else copy(signals = signals.map { if (it.isConnected) it.copy(isConnected = false) else it })

private fun Context.dataTransport(): Flow<DataTransport?> =
  callbackFlow {
    val connectivity = checkNotNull(getSystemService(ConnectivityManager::class.java))
    val callback =
      object : ConnectivityManager.NetworkCallback() {
        override fun onCapabilitiesChanged(
          network: Network,
          capabilities: NetworkCapabilities
        ) {
          trySend(
            when {
              capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> DataTransport.WIFI
              capabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> DataTransport.CELLULAR
              else -> null
            }
          )
        }

        override fun onLost(network: Network) {
          trySend(null)
        }
      }
    trySend(null)
    connectivity.registerDefaultNetworkCallback(callback)
    awaitClose { connectivity.unregisterNetworkCallback(callback) }
  }.distinctUntilChanged()

private fun Context.locationEnabled(): Flow<Boolean> = broadcasts(LocationManager.MODE_CHANGED_ACTION).map { isLocationEnabled }
