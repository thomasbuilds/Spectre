package dev.thomasbuilds.spectre.recon

import android.os.SystemClock
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import java.io.IOException
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.net.SocketTimeoutException

private const val TAG = "Ssdp"
private const val MULTICAST_HOST = "239.255.255.250"
private const val SSDP_PORT = 1900
private const val RECEIVE_SLICE_MS = 250
private const val LISTEN_WINDOW_MS = 4_000L
private const val MAX_DATAGRAM_BYTES = 2048
private val M_SEARCH =
  "M-SEARCH * HTTP/1.1\r\nHOST: $MULTICAST_HOST:$SSDP_PORT\r\nMAN: \"ssdp:discover\"\r\nMX: 2\r\nST: ssdp:all\r\n\r\n"
    .toByteArray(Charsets.US_ASCII)

data class SsdpDevice(
  val address: InetAddress,
  val location: String?,
  val server: String?
)

internal fun ssdpDevices(): Flow<SsdpDevice> =
  flow {
    DatagramSocket().use { socket ->
      socket.soTimeout = RECEIVE_SLICE_MS
      val group = InetAddress.getByName(MULTICAST_HOST)
      repeat(2) { socket.send(DatagramPacket(M_SEARCH, M_SEARCH.size, group, SSDP_PORT)) }
      val seen = HashMap<InetAddress, SsdpDevice>()
      val deadline = SystemClock.elapsedRealtime() + LISTEN_WINDOW_MS
      val packet = DatagramPacket(ByteArray(MAX_DATAGRAM_BYTES), MAX_DATAGRAM_BYTES)
      while (SystemClock.elapsedRealtime() < deadline) {
        currentCoroutineContext().ensureActive()
        try {
          socket.receive(packet)
        } catch (_: SocketTimeoutException) {
          continue
        }
        val response = parseSearchResponse(String(packet.data, 0, packet.length, Charsets.UTF_8), packet.address) ?: continue
        val previous = seen[response.address]
        val device = response.copy(location = response.location ?: previous?.location, server = response.server ?: previous?.server)
        if (device != previous) {
          seen[device.address] = device
          emit(device)
        }
      }
    }
  }.catch { cause ->
    if (cause !is IOException) throw cause
    Log.w(TAG, "SSDP discovery failed", cause)
  }.flowOn(Dispatchers.IO)

private fun parseSearchResponse(
  raw: String,
  address: InetAddress
): SsdpDevice? {
  val lines = raw.lines()
  if (!lines.first().trimStart().startsWith("HTTP/1.1 200", ignoreCase = true)) return null
  val headers =
    lines
      .drop(1)
      .filter { ':' in it }
      .associate { it.substringBefore(':').trim().lowercase() to it.substringAfter(':').trim() }
  return SsdpDevice(address, location = headers["location"], server = headers["server"])
}
