package dev.thomasbuilds.spectre.wifi

import android.annotation.SuppressLint
import android.net.wifi.ScanResult
import android.net.wifi.rtt.RangingRequest
import android.net.wifi.rtt.RangingResult
import android.net.wifi.rtt.RangingResultCallback
import android.net.wifi.rtt.WifiRttManager
import android.os.Build
import android.os.SystemClock
import android.util.Log
import kotlinx.coroutines.suspendCancellableCoroutine
import java.util.TreeMap
import java.util.concurrent.Executor
import kotlin.coroutines.resume

private const val TAG = "WifiRttRanger"
private const val MAX_AGE_MS = 60_000L
private const val REREQUEST_AFTER_MS = 30_000L

internal class WifiRttRanger(
  private val rtt: WifiRttManager
) {
  private class Reading(
    val meters: Double,
    val atMs: Long
  )

  private val readings = TreeMap<String, Reading>(String.CASE_INSENSITIVE_ORDER)
  private val attemptedAtMs = TreeMap<String, Long>(String.CASE_INSENSITIVE_ORDER)

  fun distanceMeters(bssid: String): Double? = readings[bssid]?.takeIf { SystemClock.elapsedRealtime() - it.atMs < MAX_AGE_MS }?.meters

  suspend fun range(
    results: List<ScanResult>,
    executor: Executor
  ): Boolean {
    if (!rtt.isAvailable) return false
    val now = SystemClock.elapsedRealtime()
    val targets =
      results
        .filter { it.isFtmResponder && readings[it.BSSID]?.let { reading -> now - reading.atMs < REREQUEST_AFTER_MS } != true }
        .sortedBy { attemptedAtMs[it.BSSID] ?: 0L }
        .take(RangingRequest.getMaxPeers())
    if (targets.isEmpty()) return false
    readings.values.removeAll { now - it.atMs >= MAX_AGE_MS }
    targets.forEach { attemptedAtMs[it.BSSID] = now }
    val ranged = measure(RangingRequest.Builder().addAccessPoints(targets).build(), executor)
    val measuredAt = SystemClock.elapsedRealtime()
    val measured = ranged.filter { it.status == RangingResult.STATUS_SUCCESS }
    measured.forEach { readings[it.macAddress.toString()] = Reading((it.distanceMm / 1000.0).coerceAtLeast(0.0), measuredAt) }
    return measured.isNotEmpty()
  }

  @SuppressLint("MissingPermission")
  private suspend fun measure(
    request: RangingRequest,
    executor: Executor
  ): List<RangingResult> =
    suspendCancellableCoroutine { continuation ->
      val callback =
        object : RangingResultCallback() {
          override fun onRangingResults(results: List<RangingResult>) = continuation.resume(results)

          override fun onRangingFailure(code: Int) {
            Log.w(TAG, "Ranging failed with code $code")
            continuation.resume(emptyList())
          }
        }
      try {
        rtt.startRanging(request, executor, callback)
      } catch (e: RuntimeException) {
        Log.w(TAG, "Ranging could not start", e)
        continuation.resume(emptyList())
      }
    }
}

internal val ScanResult.isFtmResponder: Boolean
  get() = is80211mcResponder || (Build.VERSION.SDK_INT >= Build.VERSION_CODES.VANILLA_ICE_CREAM && is80211azNtbResponder)
