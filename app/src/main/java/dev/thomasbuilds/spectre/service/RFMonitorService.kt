package dev.thomasbuilds.spectre.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.bluetooth.BluetoothDevice
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ServiceInfo
import android.location.LocationManager
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.os.Binder
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import dev.thomasbuilds.spectre.MainActivity
import dev.thomasbuilds.spectre.R
import dev.thomasbuilds.spectre.analysis.ExposureCalculator
import dev.thomasbuilds.spectre.model.ScanState
import dev.thomasbuilds.spectre.scanner.ble.BluetoothScanner
import dev.thomasbuilds.spectre.scanner.cellular.CellularScanner
import dev.thomasbuilds.spectre.scanner.gnss.GnssScanner
import dev.thomasbuilds.spectre.scanner.wifi.WifiScanner
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.sample
import kotlinx.coroutines.launch

class RFMonitorService : Service() {
  inner class LocalBinder : Binder() {
    fun service(): RFMonitorService = this@RFMonitorService
  }

  private val binder = LocalBinder()
  private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

  private lateinit var cellularScanner: CellularScanner
  private lateinit var wifiScanner: WifiScanner
  private lateinit var bluetoothScanner: BluetoothScanner
  private lateinit var gnssScanner: GnssScanner

  fun bleRemoteDevice(mac: String): BluetoothDevice? = if (::bluetoothScanner.isInitialized) bluetoothScanner.remoteDevice(mac) else null

  private val _state = MutableStateFlow(ScanState.Loading)
  val state: StateFlow<ScanState> get() = _state

  @Volatile private var isAppForeground: Boolean = false

  @Volatile private var isLocationEnabled: Boolean = true

  private val activeTransport = MutableStateFlow<DataTransport?>(null)
  private var defaultNetworkCallback: ConnectivityManager.NetworkCallback? = null

  private val lifecycleObserver =
    object : DefaultLifecycleObserver {
      override fun onStart(owner: LifecycleOwner) {
        isAppForeground = true
        refreshNotification()
      }

      override fun onStop(owner: LifecycleOwner) {
        isAppForeground = false
        refreshNotification()
      }
    }

  private var combineJob: Job? = null
  private var notificationJob: Job? = null
  private var locationModeReceiver: BroadcastReceiver? = null

  @Volatile private var startedForeground = false

  override fun onCreate() {
    super.onCreate()
    cellularScanner = CellularScanner(this)
    wifiScanner = WifiScanner(this)
    bluetoothScanner = BluetoothScanner(this)
    gnssScanner = GnssScanner(this)

    createNotificationChannel()
    isAppForeground =
      ProcessLifecycleOwner
        .get()
        .lifecycle.currentState
        .isAtLeast(androidx.lifecycle.Lifecycle.State.STARTED)
    ProcessLifecycleOwner.get().lifecycle.addObserver(lifecycleObserver)
    isLocationEnabled =
      (getSystemService(Context.LOCATION_SERVICE) as? LocationManager)
        ?.isLocationEnabled == true
    registerLocationModeReceiver()
    registerDefaultNetworkCallback()
  }

  private fun registerDefaultNetworkCallback() {
    val cm = getSystemService(CONNECTIVITY_SERVICE) as? ConnectivityManager ?: return
    activeTransport.value = computeActiveTransport(cm)
    val callback =
      object : ConnectivityManager.NetworkCallback() {
        override fun onCapabilitiesChanged(
          network: Network,
          caps: NetworkCapabilities
        ) {
          activeTransport.value = resolveTransport(cm, caps)
        }

        override fun onLost(network: Network) {
          activeTransport.value = null
        }
      }
    runCatching { cm.registerDefaultNetworkCallback(callback) }
      .onSuccess { defaultNetworkCallback = callback }
  }

  private fun computeActiveTransport(cm: ConnectivityManager): DataTransport? {
    val active = cm.activeNetwork ?: return null
    val caps = cm.getNetworkCapabilities(active) ?: return null
    return resolveTransport(cm, caps)
  }

  private fun resolveTransport(
    cm: ConnectivityManager,
    caps: NetworkCapabilities
  ): DataTransport {
    if (caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)) return DataTransport.WIFI
    if (caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR)) return DataTransport.CELLULAR
    if (caps.hasTransport(NetworkCapabilities.TRANSPORT_VPN)) {
      @Suppress("DEPRECATION")
      for (n in cm.allNetworks) {
        val nc = cm.getNetworkCapabilities(n) ?: continue
        if (nc.hasTransport(NetworkCapabilities.TRANSPORT_VPN)) continue
        if (!nc.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)) continue
        if (nc.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)) return DataTransport.WIFI
        if (nc.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR)) return DataTransport.CELLULAR
      }
    }
    return DataTransport.OTHER
  }

  private fun registerLocationModeReceiver() {
    val receiver =
      object : BroadcastReceiver() {
        override fun onReceive(
          context: Context?,
          intent: Intent?
        ) {
          val lm = context?.getSystemService(Context.LOCATION_SERVICE) as? LocationManager
          isLocationEnabled = lm?.isLocationEnabled == true
          refreshNotification()
        }
      }
    ContextCompat.registerReceiver(
      this,
      receiver,
      IntentFilter(LocationManager.MODE_CHANGED_ACTION),
      ContextCompat.RECEIVER_NOT_EXPORTED
    )
    locationModeReceiver = receiver
  }

  override fun onStartCommand(
    intent: Intent?,
    flags: Int,
    startId: Int
  ): Int {
    if (intent?.action == ACTION_SHUTDOWN) {
      sendBroadcast(Intent(ACTION_APP_SHUTDOWN).setPackage(packageName))
      stopSelf()
      return START_NOT_STICKY
    }
    if (!startedForeground) {
      startedForeground = startInForeground()
      if (!startedForeground) {
        stopSelf(startId)
        return START_NOT_STICKY
      }
    }
    ensureScanning()
    return START_NOT_STICKY
  }

  override fun onBind(intent: Intent?): IBinder {
    ensureScanning()
    return binder
  }

  override fun onUnbind(intent: Intent?): Boolean = true

  override fun onDestroy() {
    combineJob?.cancel()
    notificationJob?.cancel()
    locationModeReceiver?.let { receiver ->
      runCatching { unregisterReceiver(receiver) }
    }
    locationModeReceiver = null
    defaultNetworkCallback?.let { callback ->
      val cm = getSystemService(CONNECTIVITY_SERVICE) as? ConnectivityManager
      runCatching { cm?.unregisterNetworkCallback(callback) }
    }
    defaultNetworkCallback = null
    runCatching {
      ProcessLifecycleOwner.get().lifecycle.removeObserver(lifecycleObserver)
    }
    bluetoothScanner.stop()
    gnssScanner.stop()
    wifiScanner.stop()
    cellularScanner.stop()
    scope.cancel()
    super.onDestroy()
  }

  @OptIn(FlowPreview::class)
  private fun ensureScanning() {
    if (combineJob?.isActive == true) return

    cellularScanner.start(scope)
    bluetoothScanner.start(scope)
    gnssScanner.start(scope)
    wifiScanner.start(scope)

    val composed =
      combine(
        cellularScanner.state,
        wifiScanner.state,
        bluetoothScanner.state,
        gnssScanner.state,
        activeTransport
      ) { cell, wifi, bt, gnss, active ->
        // A registered cell or associated AP is only the live data connection when it matches
        // the active transport, so clear isConnected on the standby radio (e.g. cellular while
        // connected over WiFi).
        val cellSignals =
          if (active == DataTransport.CELLULAR) {
            cell.signals
          } else {
            cell.signals.map { if (it.isConnected) it.copy(isConnected = false) else it }
          }
        val wifiSignals =
          if (active == DataTransport.WIFI) {
            wifi.signals
          } else {
            wifi.signals.map { if (it.isConnected) it.copy(isConnected = false) else it }
          }
        val totalDbm =
          ExposureCalculator.totalExposureDbm(
            cellSignals,
            wifiSignals.filterNot { it.isStale },
            bt.signals.filterNot { it.isStale }
          )
        ScanState(
          cellular = cellSignals,
          wifi = wifiSignals,
          bluetooth = bt.signals,
          gnss = gnss.signals,
          totalDbm = totalDbm,
          cellularStatus = cell.status,
          wifiStatus = wifi.status,
          bluetoothStatus = bt.status,
          gnssStatus = gnss.status,
          cellularConnection = cell.connectionLabel,
          wifiScanThrottlingOn = wifi.scanThrottlingOn,
          cellularReady = cell.ready,
          wifiReady = wifi.ready,
          bluetoothReady = bt.ready,
          gnssReady = gnss.ready
        )
      }.distinctUntilChanged()

    combineJob =
      scope.launch {
        composed.collect { _state.value = it }
      }

    notificationJob =
      scope.launch {
        _state
          .map { if (it.totalScoreUsable) it.totalDbm.toInt() else null }
          .distinctUntilChanged()
          .sample(NOTIFICATION_THROTTLE_MS)
          .collect { refreshNotification() }
      }
  }

  private fun createNotificationChannel() {
    val nm = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
    val quietChannel =
      NotificationChannel(
        CHANNEL_ID_QUIET,
        getString(R.string.notification_channel_name),
        NotificationManager.IMPORTANCE_MIN
      ).apply {
        description = getString(R.string.notification_channel_description)
        setShowBadge(false)
        enableLights(false)
        enableVibration(false)
        setSound(null, null)
      }
    val visibleChannel =
      NotificationChannel(
        CHANNEL_ID_VISIBLE,
        getString(R.string.notification_channel_name),
        NotificationManager.IMPORTANCE_DEFAULT
      ).apply {
        description = getString(R.string.notification_channel_description)
        setShowBadge(false)
        enableLights(false)
        enableVibration(false)
        setSound(null, null)
      }
    nm.createNotificationChannels(listOf(quietChannel, visibleChannel))
  }

  private fun buildNotification(): Notification {
    val state = _state.value
    val intent =
      Intent(this, MainActivity::class.java).apply {
        flags = Intent.FLAG_ACTIVITY_SINGLE_TOP
      }
    val pi =
      PendingIntent.getActivity(
        this,
        0,
        intent,
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
      )
    val dbmInt = state.totalDbm.toInt()
    val dbmText = if (dbmInt > 0) "+$dbmInt dBm" else "$dbmInt dBm"
    val title =
      when {
        !isLocationEnabled -> "RF exposure: unavailable"
        !state.totalScoreUsable -> "RF exposure: measuring…"
        else -> "RF exposure: $dbmText"
      }
    val shutdownPi =
      PendingIntent.getService(
        this,
        1,
        Intent(this, RFMonitorService::class.java).setAction(ACTION_SHUTDOWN),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
      )
    val channelId = if (isAppForeground) CHANNEL_ID_QUIET else CHANNEL_ID_VISIBLE
    return NotificationCompat
      .Builder(this, channelId)
      .setSmallIcon(R.drawable.ic_notification)
      .setContentTitle(title)
      .setContentText("Swipe to close app")
      .setContentIntent(pi)
      .setDeleteIntent(shutdownPi)
      .setOngoing(false)
      .setShowWhen(false)
      .setPriority(NotificationCompat.PRIORITY_DEFAULT)
      .setSilent(true)
      .setCategory(NotificationCompat.CATEGORY_SERVICE)
      .build()
  }

  private fun refreshNotification() {
    val nm = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
    nm.notify(NOTIFICATION_ID, buildNotification())
  }

  private fun startInForeground(): Boolean =
    try {
      startForeground(
        NOTIFICATION_ID,
        buildNotification(),
        foregroundServiceType()
      )
      true
    } catch (e: Exception) {
      Log.w(TAG, "startForeground denied: ${e.message}")
      false
    }

  private fun foregroundServiceType(): Int =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
      ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE or ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION
    } else {
      ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION
    }

  private enum class DataTransport { WIFI, CELLULAR, OTHER }

  companion object {
    private const val TAG = "RFMonitor"

    const val CHANNEL_ID_QUIET = "spectre_quiet"
    const val CHANNEL_ID_VISIBLE = "spectre_visible"
    const val NOTIFICATION_ID = 1

    const val ACTION_SHUTDOWN = "dev.thomasbuilds.spectre.action.SHUTDOWN"
    const val ACTION_APP_SHUTDOWN = "dev.thomasbuilds.spectre.action.APP_SHUTDOWN"

    private const val NOTIFICATION_THROTTLE_MS = 5_000L
  }
}
