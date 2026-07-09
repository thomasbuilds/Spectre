package dev.thomasbuilds.spectre.scanner.wifi

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.net.wifi.ScanResult
import android.net.wifi.rtt.RangingRequest
import android.net.wifi.rtt.RangingResult
import android.net.wifi.rtt.RangingResultCallback
import android.net.wifi.rtt.WifiRttManager
import android.os.SystemClock
import android.util.Log
import dev.thomasbuilds.spectre.hasPermission
import dev.thomasbuilds.spectre.scanner.daemonExecutor
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean

class WifiRttRanger(
  private val context: Context
) {
  data class FtmReading(
    val distanceM: Double,
    val timestampMs: Long
  )

  private val rttMgr: WifiRttManager? =
    context.getSystemService(Context.WIFI_RTT_RANGING_SERVICE) as? WifiRttManager

  private val cache = ConcurrentHashMap<String, FtmReading>()
  private val executor = daemonExecutor("wifi-rtt")
  private val inFlight = AtomicBoolean(false)

  fun isSupported(): Boolean =
    rttMgr != null &&
      context.packageManager.hasSystemFeature(PackageManager.FEATURE_WIFI_RTT)

  private fun hasPermission(): Boolean = context.hasPermission(Manifest.permission.ACCESS_FINE_LOCATION)

  fun fresh(
    bssid: String?,
    maxAgeMs: Long = MAX_AGE_MS
  ): FtmReading? {
    if (bssid.isNullOrEmpty()) return null
    val r = cache[bssid.uppercase()] ?: return null
    return if (SystemClock.elapsedRealtime() - r.timestampMs < maxAgeMs) r else null
  }

  @SuppressLint("MissingPermission")
  fun requestRanging(scanResults: List<ScanResult>) {
    if (!isSupported()) return
    if (!hasPermission()) return
    if (inFlight.get()) return
    val mgr = rttMgr ?: return

    val ftmCapable = scanResults.filter { it.is80211mcResponder }
    if (ftmCapable.isEmpty()) return
    val targets =
      ftmCapable
        .filter { sr ->
          val mac = sr.BSSID
          !mac.isNullOrEmpty() && fresh(mac, REREQUEST_THRESHOLD_MS) == null
        }.take(RangingRequest.getMaxPeers())

    if (targets.isEmpty()) return

    val request =
      try {
        RangingRequest
          .Builder()
          .apply {
            targets.forEach { addAccessPoint(it) }
          }.build()
      } catch (e: IllegalArgumentException) {
        Log.w(TAG, "ranging request build failed: ${e.message}")
        return
      }

    if (!inFlight.compareAndSet(false, true)) return
    try {
      mgr.startRanging(
        request,
        executor,
        object : RangingResultCallback() {
          override fun onRangingResults(results: List<RangingResult>) {
            inFlight.set(false)
            val now = SystemClock.elapsedRealtime()
            results.forEach { r ->
              val mac = r.macAddress?.toString()?.uppercase() ?: return@forEach
              if (r.status == RangingResult.STATUS_SUCCESS) {
                cache[mac] =
                  FtmReading(
                    // Measurement noise can put a very close peer a few cm "behind" the phone.
                    distanceM = (r.distanceMm / 1000.0).coerceAtLeast(0.0),
                    timestampMs = now
                  )
              }
            }
          }

          override fun onRangingFailure(code: Int) {
            inFlight.set(false)
            Log.w(TAG, "FTM batch failed code=$code")
          }
        }
      )
    } catch (e: SecurityException) {
      Log.w(TAG, "FTM start denied: ${e.message}")
      inFlight.set(false)
    } catch (e: Throwable) {
      Log.w(TAG, "FTM start threw: ${e.message}")
      inFlight.set(false)
    }
  }

  private companion object {
    const val TAG = "WifiRttRanger"
    const val MAX_AGE_MS = 60_000L
    const val REREQUEST_THRESHOLD_MS = 30_000L
  }
}
