package dev.thomasbuilds.spectre

import android.app.ActivityManager
import android.app.ForegroundServiceStartNotAllowedException
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ProcessLifecycleOwner
import kotlinx.coroutines.Job
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.transform
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

private const val TAG = "MonitorService"
private const val NOTIFICATION_ID = 1
private const val QUIET_CHANNEL = "spectre_quiet"
private const val VISIBLE_CHANNEL = "spectre_visible"
private const val ACTION_STOP = "dev.thomasbuilds.spectre.action.SHUTDOWN"
private const val REFRESH_INTERVAL_MS = 5_000L

private data class NotificationContent(
  val title: String,
  val appVisible: Boolean
)

class MonitorService : Service() {
  private val scope = MainScope()
  private var updates: Job? = null
  private var current = NotificationContent("RF exposure: measuring…", appVisible = true)
  private val notifications by lazy { checkNotNull(getSystemService(NotificationManager::class.java)) }
  private val openApp by lazy {
    PendingIntent.getActivity(
      this,
      0,
      Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
      PendingIntent.FLAG_IMMUTABLE
    )
  }
  private val stop by lazy {
    PendingIntent.getService(this, 1, Intent(this, MonitorService::class.java).setAction(ACTION_STOP), PendingIntent.FLAG_IMMUTABLE)
  }

  override fun onBind(intent: Intent?): IBinder? = null

  override fun onCreate() {
    super.onCreate()
    notifications.createNotificationChannels(
      listOf(
        channel(QUIET_CHANNEL, "Monitoring while Spectre is open", NotificationManager.IMPORTANCE_LOW),
        channel(VISIBLE_CHANNEL, "Monitoring in the background", NotificationManager.IMPORTANCE_DEFAULT)
      )
    )
  }

  override fun onStartCommand(
    intent: Intent?,
    flags: Int,
    startId: Int
  ): Int {
    if (intent?.action == ACTION_STOP) {
      updates?.cancel()
      checkNotNull(getSystemService(ActivityManager::class.java)).appTasks.forEach { it.finishAndRemoveTask() }
      stopSelf()
      return START_NOT_STICKY
    }
    try {
      startForeground(NOTIFICATION_ID, notification(current), foregroundServiceType())
    } catch (e: ForegroundServiceStartNotAllowedException) {
      Log.w(TAG, "Foreground start refused", e)
      stopSelf()
      return START_NOT_STICKY
    }
    if (updates == null) {
      updates =
        scope.launch {
          contents().collect {
            current = it
            notifications.notify(NOTIFICATION_ID, notification(it))
          }
        }
    }
    return START_NOT_STICKY
  }

  override fun onDestroy() {
    scope.cancel()
    super.onDestroy()
  }

  private fun contents() =
    with((application as SpectreApp).monitor) {
      combine(
        state.map { it.exposure.totalDbm?.roundToInt() }.distinctUntilChanged().throttleLatest(REFRESH_INTERVAL_MS),
        state.map { it.exposure.totalDbm != null }.distinctUntilChanged(),
        locationEnabled,
        ProcessLifecycleOwner
          .get()
          .lifecycle.currentStateFlow
          .map { it.isAtLeast(Lifecycle.State.STARTED) }
      ) { totalDbm, measured, locationOn, appVisible ->
        val title =
          when {
            !locationOn -> "RF exposure: unavailable"
            totalDbm == null || !measured -> "RF exposure: measuring…"
            else -> "RF exposure: $totalDbm dBm"
          }
        NotificationContent(title, appVisible)
      }.distinctUntilChanged()
    }

  private fun notification(content: NotificationContent): Notification =
    NotificationCompat
      .Builder(this, if (content.appVisible) QUIET_CHANNEL else VISIBLE_CHANNEL)
      .setSmallIcon(R.drawable.ic_notification)
      .setContentTitle(content.title)
      .setContentIntent(openApp)
      .setDeleteIntent(stop)
      .apply {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
          setContentText("Swipe to close app")
        } else {
          addAction(0, "Stop measuring", stop)
        }
      }.setShowWhen(false)
      .setSilent(true)
      .setCategory(NotificationCompat.CATEGORY_SERVICE)
      .build()
}

private fun channel(
  id: String,
  name: String,
  importance: Int
) = NotificationChannel(id, name, importance).apply {
  description = "Continuous monitoring of ambient RF signals"
  setShowBadge(false)
  setSound(null, null)
}

private fun foregroundServiceType(): Int =
  if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
    ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE or ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION
  } else {
    ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION
  }

private fun <T> Flow<T>.throttleLatest(periodMs: Long): Flow<T> =
  conflate().transform {
    emit(it)
    delay(periodMs)
  }
