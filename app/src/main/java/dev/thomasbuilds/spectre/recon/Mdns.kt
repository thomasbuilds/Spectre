package dev.thomasbuilds.spectre.recon

import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import android.util.Log
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.buffer
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.withTimeoutOrNull

private const val TAG = "Mdns"
private const val MAX_CONCURRENT_BROWSES = 10
private val SERVICE_TYPES =
  listOf(
    "_http._tcp",
    "_https._tcp",
    "_ssh._tcp",
    "_printer._tcp",
    "_ipp._tcp",
    "_ipps._tcp",
    "_airplay._tcp",
    "_raop._tcp",
    "_companion-link._tcp",
    "_googlecast._tcp",
    "_spotify-connect._tcp",
    "_hap._tcp",
    "_apple-mobdev2._tcp",
    "_smb._tcp",
    "_afpovertcp._tcp",
    "_workstation._tcp",
    "_device-info._tcp",
    "_plex._tcp",
    "_googlerpc._tcp"
  )

data class MdnsService(
  val name: String,
  val type: String
)

internal fun NsdManager.services(windowMs: Long): Flow<MdnsService> =
  channelFlow {
    val batches = SERVICE_TYPES.chunked(MAX_CONCURRENT_BROWSES)
    while (true) batches.forEach { batch -> withTimeoutOrNull(windowMs / batches.size) { browse(batch).collect { send(it) } } }
  }

private fun NsdManager.browse(types: List<String>): Flow<MdnsService> =
  callbackFlow {
    val listeners =
      types.map { type ->
        object : NsdManager.DiscoveryListener {
          override fun onServiceFound(info: NsdServiceInfo) {
            trySend(MdnsService(info.serviceName, info.serviceType.removeSuffix(".")))
          }

          override fun onStartDiscoveryFailed(
            serviceType: String,
            errorCode: Int
          ) {
            Log.w(TAG, "Browsing $serviceType failed with code $errorCode")
          }

          override fun onStopDiscoveryFailed(
            serviceType: String,
            errorCode: Int
          ) = Unit

          override fun onDiscoveryStarted(serviceType: String) = Unit

          override fun onDiscoveryStopped(serviceType: String) = Unit

          override fun onServiceLost(info: NsdServiceInfo) = Unit
        }.also { discoverServices(type, NsdManager.PROTOCOL_DNS_SD, it) }
      }
    awaitClose { listeners.forEach(::stopBrowsing) }
  }.buffer(Channel.UNLIMITED)

private fun NsdManager.stopBrowsing(listener: NsdManager.DiscoveryListener) {
  try {
    stopServiceDiscovery(listener)
  } catch (_: IllegalArgumentException) {
  }
}
