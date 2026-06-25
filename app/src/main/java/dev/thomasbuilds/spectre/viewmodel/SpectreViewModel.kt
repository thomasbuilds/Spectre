package dev.thomasbuilds.spectre.viewmodel

import android.app.Application
import android.bluetooth.BluetoothDevice
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.ServiceConnection
import android.location.LocationManager
import android.os.IBinder
import androidx.core.content.ContextCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import dev.thomasbuilds.spectre.model.ScanState
import dev.thomasbuilds.spectre.model.ScannerStatus
import dev.thomasbuilds.spectre.service.RFMonitorService
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn

@OptIn(ExperimentalCoroutinesApi::class)
class SpectreViewModel(
  application: Application
) : AndroidViewModel(application) {
  private val lm = application.getSystemService(Context.LOCATION_SERVICE) as LocationManager
  private val boundService = MutableStateFlow<RFMonitorService?>(null)
  private var isBound = false

  val locationEnabled: StateFlow<Boolean> =
    callbackFlow {
      trySend(lm.isLocationEnabled)
      val receiver =
        object : BroadcastReceiver() {
          override fun onReceive(
            context: Context?,
            intent: Intent?
          ) {
            trySend(lm.isLocationEnabled)
          }
        }
      ContextCompat.registerReceiver(
        application,
        receiver,
        IntentFilter(LocationManager.MODE_CHANGED_ACTION),
        ContextCompat.RECEIVER_NOT_EXPORTED
      )
      awaitClose {
        runCatching { application.unregisterReceiver(receiver) }
      }
    }.distinctUntilChanged()
      .stateIn(viewModelScope, SharingStarted.Eagerly, lm.isLocationEnabled)

  private val locationOffState =
    ScanState(
      cellularStatus = ScannerStatus.LOCATION_OFF,
      wifiStatus = ScannerStatus.LOCATION_OFF,
      bluetoothStatus = ScannerStatus.LOCATION_OFF,
      gnssStatus = ScannerStatus.LOCATION_OFF,
      cellularReady = false,
      wifiReady = false,
      bluetoothReady = false,
      gnssReady = false
    )

  private val serviceState: Flow<ScanState> =
    boundService.flatMapLatest { it?.state ?: flowOf(ScanState.Loading) }

  val state: StateFlow<ScanState> =
    combine(locationEnabled, serviceState) { locOn, srvState ->
      if (locOn) srvState else locationOffState
    }.stateIn(
      scope = viewModelScope,
      started = SharingStarted.WhileSubscribed(5_000),
      initialValue = if (lm.isLocationEnabled) ScanState.Loading else locationOffState
    )

  private val connection =
    object : ServiceConnection {
      override fun onServiceConnected(
        name: ComponentName?,
        binder: IBinder?
      ) {
        boundService.value = (binder as? RFMonitorService.LocalBinder)?.service()
      }

      override fun onServiceDisconnected(name: ComponentName?) {
        boundService.value = null
      }
    }

  fun bleRemoteDevice(mac: String): BluetoothDevice? = boundService.value?.bleRemoteDevice(mac)

  fun ensureServiceRunning() {
    if (!lm.isLocationEnabled) return
    val ctx = getApplication<Application>()
    val intent = Intent(ctx, RFMonitorService::class.java)
    val started = runCatching { ctx.startForegroundService(intent) }.isSuccess
    if (!started) return
    if (!isBound) {
      isBound = ctx.bindService(intent, connection, Context.BIND_AUTO_CREATE)
    }
  }

  override fun onCleared() {
    if (isBound) {
      runCatching { getApplication<Application>().unbindService(connection) }
      isBound = false
    }
    boundService.value = null
  }
}
