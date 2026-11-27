package dev.thomasbuilds.spectre.core

import android.Manifest
import android.content.Context
import android.location.LocationManager
import android.util.Log
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.flow.retryWhen
import kotlinx.coroutines.flow.scan

private const val STATUS_POLL_MS = 5_000L
private const val SESSION_RETRY_MS = 5_000L
private const val TAG = "Source"
private val LOCATION_PERMISSIONS = listOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION)

enum class SourceStatus { OK, LOCATION_OFF, NO_PERMISSION, UNSUPPORTED, RADIO_OFF, NO_SIM }

data class SourceState<out T>(
  val status: SourceStatus = SourceStatus.OK,
  val ready: Boolean = false,
  val signals: List<T> = emptyList(),
  val label: String? = null
)

enum class Source(
  val permissions: List<String>
) {
  CELLULAR(listOf(Manifest.permission.READ_PHONE_STATE) + LOCATION_PERMISSIONS),
  WIFI(LOCATION_PERMISSIONS),
  BLUETOOTH(LOCATION_PERMISSIONS + Manifest.permission.BLUETOOTH_SCAN + Manifest.permission.BLUETOOTH_CONNECT),
  GNSS(LOCATION_PERMISSIONS)
}

internal fun Context.statusFlow(
  vararg actions: String,
  status: () -> SourceStatus
): Flow<SourceStatus> =
  merge(ticker(STATUS_POLL_MS), broadcasts(LocationManager.MODE_CHANGED_ACTION, *actions))
    .map { status() }
    .distinctUntilChanged()

internal fun <T> Flow<SourceStatus>.whileOk(
  warmupMs: Long,
  hasData: (List<T>) -> Boolean = { it.isNotEmpty() },
  session: () -> Flow<SourceState<T>>
): Flow<SourceState<T>> =
  channelFlow {
    collectLatest { status ->
      if (status == SourceStatus.OK) {
        session()
          .conflate()
          .retryWhen { cause, _ -> cause is Exception && retryAfter(cause) }
          .warmUp(warmupMs, hasData)
          .collect { send(it) }
      } else {
        send(SourceState(status = status, ready = status != SourceStatus.LOCATION_OFF))
      }
    }
  }

private suspend fun retryAfter(cause: Exception): Boolean {
  Log.w(TAG, "Source session failed, restarting", cause)
  delay(SESSION_RETRY_MS)
  return true
}

private fun <T> Flow<SourceState<T>>.warmUp(
  warmupMs: Long,
  hasData: (List<T>) -> Boolean
): Flow<SourceState<T>> =
  merge<SourceState<T>?>(
    this,
    flow {
      delay(warmupMs)
      emit(null)
    }
  ).scan(SourceState()) { state, frame -> frame?.copy(ready = state.ready || hasData(frame.signals)) ?: state.copy(ready = true) }
