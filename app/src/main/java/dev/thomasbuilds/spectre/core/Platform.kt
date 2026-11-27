package dev.thomasbuilds.spectre.core

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.location.LocationManager
import androidx.core.content.ContextCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.launch
import java.util.concurrent.Executor

internal fun Context.hasPermission(permission: String): Boolean = checkSelfPermission(permission) == PackageManager.PERMISSION_GRANTED

internal fun Context.hasPermissions(permissions: List<String>): Boolean = permissions.all(::hasPermission)

internal fun ticker(periodMs: Long): Flow<Unit> =
  flow {
    while (true) {
      emit(Unit)
      delay(periodMs)
    }
  }

internal fun Context.broadcasts(vararg actions: String): Flow<Unit> =
  callbackFlow {
    val receiver =
      object : BroadcastReceiver() {
        override fun onReceive(
          context: Context,
          intent: Intent
        ) {
          trySend(Unit)
        }
      }
    val filter = IntentFilter().apply { actions.forEach(::addAction) }
    ContextCompat.registerReceiver(this@broadcasts, receiver, filter, ContextCompat.RECEIVER_NOT_EXPORTED)
    trySend(Unit)
    awaitClose { unregisterReceiver(receiver) }
  }

internal fun CoroutineScope.callbackExecutor(): Executor = Executor { launch { it.run() } }

internal val Context.isLocationEnabled: Boolean get() = checkNotNull(getSystemService(LocationManager::class.java)).isLocationEnabled
