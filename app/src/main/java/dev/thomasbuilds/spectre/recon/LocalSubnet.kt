package dev.thomasbuilds.spectre.recon

import android.content.Context
import android.net.ConnectivityManager
import android.net.LinkProperties
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.os.Handler
import android.os.Looper
import androidx.compose.runtime.Immutable
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import java.net.Inet4Address
import java.net.InetAddress
import java.nio.ByteBuffer

private const val WIDEST_SCANNED_PREFIX = 22
private const val NARROWEST_SCANNABLE_PREFIX = 30

@Immutable
data class LocalSubnet(
  val network: Network,
  val address: Inet4Address,
  val prefixLength: Int,
  val gateway: InetAddress?,
  val interfaceName: String?
) {
  val scannedPrefixLength: Int get() = prefixLength.coerceAtLeast(WIDEST_SCANNED_PREFIX)

  val scannedNetwork: InetAddress
    get() {
      val mask = -1 shl (32 - scannedPrefixLength)
      return InetAddress.getByAddress(ByteBuffer.allocate(4).putInt(ByteBuffer.wrap(address.address).int and mask).array())
    }

  val isScannable: Boolean get() = prefixLength <= NARROWEST_SCANNABLE_PREFIX

  fun hostAddresses(): List<InetAddress> {
    if (!isScannable) return emptyList()
    val ip = ByteBuffer.wrap(address.address).int
    val mask = -1 shl (32 - scannedPrefixLength)
    return ((ip and mask) + 1 until (ip or mask.inv())).map { InetAddress.getByAddress(ByteBuffer.allocate(4).putInt(it).array()) }
  }
}

internal val InetAddress.sortKey: UInt get() = ByteBuffer.wrap(address).int.toUInt()

internal fun Context.localSubnets(): Flow<LocalSubnet?> =
  callbackFlow {
    val connectivity = checkNotNull(getSystemService(ConnectivityManager::class.java))
    var current: Network? = null
    val callback =
      object : ConnectivityManager.NetworkCallback() {
        override fun onLinkPropertiesChanged(
          network: Network,
          linkProperties: LinkProperties
        ) {
          current = network
          trySend(linkProperties.toLocalSubnet(network))
        }

        override fun onLost(network: Network) {
          if (network != current) return
          current = null
          trySend(null)
        }
      }
    val request =
      NetworkRequest
        .Builder()
        .addTransportType(NetworkCapabilities.TRANSPORT_WIFI)
        .addTransportType(NetworkCapabilities.TRANSPORT_ETHERNET)
        .build()
    trySend(null)
    connectivity.registerBestMatchingNetworkCallback(request, callback, Handler(Looper.getMainLooper()))
    awaitClose { connectivity.unregisterNetworkCallback(callback) }
  }

private fun LinkProperties.toLocalSubnet(network: Network): LocalSubnet? {
  val ipv4 = linkAddresses.firstOrNull { it.address is Inet4Address } ?: return null
  return LocalSubnet(
    network = network,
    address = ipv4.address as Inet4Address,
    prefixLength = ipv4.prefixLength,
    gateway = routes.firstOrNull { it.isDefaultRoute && it.hasGateway() && it.gateway is Inet4Address }?.gateway,
    interfaceName = interfaceName
  )
}
