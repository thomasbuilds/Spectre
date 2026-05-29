package dev.thomasbuilds.spectre.recon

import android.util.Log
import androidx.compose.runtime.Immutable
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.withContext
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.net.SocketTimeoutException

@Immutable
data class SsdpDevice(
  val ip: String,
  val location: String?,
  val server: String?,
  val usn: String
)

class SsdpDiscovery(
  private val io: CoroutineDispatcher = Dispatchers.IO
) {
  fun scan(timeoutMs: Int = 4_000): Flow<SsdpDevice> =
    channelFlow {
      withContext(io) {
        val socket =
          DatagramSocket().apply {
            soTimeout = 250
            reuseAddress = true
          }

        try {
          listOf("upnp:rootdevice", "ssdp:all").forEach { st ->
            val msg = buildMSearch(st)
            runCatching {
              socket.send(
                DatagramPacket(
                  msg,
                  msg.size,
                  InetAddress.getByName(SSDP_MULTICAST_HOST),
                  SSDP_PORT
                )
              )
            }.onFailure { Log.w(TAG, "M-SEARCH send failed for $st: ${it.message}") }
          }

          val seen = HashSet<String>()
          val deadline = System.currentTimeMillis() + timeoutMs
          val buf = ByteArray(2048)
          while (System.currentTimeMillis() < deadline) {
            val packet = DatagramPacket(buf, buf.size)
            try {
              socket.receive(packet)
            } catch (_: SocketTimeoutException) {
              continue
            } catch (_: Throwable) {
              break
            }
            val raw = String(packet.data, 0, packet.length, Charsets.UTF_8)
            val device = parseResponse(raw, packet.address.hostAddress ?: continue) ?: continue
            if (seen.add(device.usn)) {
              trySend(device)
            }
          }
        } finally {
          runCatching { socket.close() }
        }
      }
    }

  private fun buildMSearch(searchTarget: String): ByteArray =
    (
      "M-SEARCH * HTTP/1.1\r\n" +
        "HOST: $SSDP_MULTICAST_HOST:$SSDP_PORT\r\n" +
        "MAN: \"ssdp:discover\"\r\n" +
        "MX: 2\r\n" +
        "ST: $searchTarget\r\n" +
        "USER-AGENT: Spectre-Recon/1.0\r\n" +
        "\r\n"
    ).toByteArray(Charsets.US_ASCII)

  private fun parseResponse(
    raw: String,
    fromIp: String
  ): SsdpDevice? {
    val lines = raw.lineSequence().toList()
    if (lines.isEmpty()) return null
    val statusLine = lines[0].trim()
    val isResponse = statusLine.startsWith("HTTP/1.1 200", ignoreCase = true)
    val isNotify = statusLine.startsWith("NOTIFY ", ignoreCase = true)
    if (!isResponse && !isNotify) return null

    val headers =
      lines
        .drop(1)
        .mapNotNull { line ->
          val idx = line.indexOf(':')
          if (idx < 1) return@mapNotNull null
          line.substring(0, idx).trim().lowercase() to line.substring(idx + 1).trim()
        }.toMap()

    val usn = headers["usn"] ?: return null
    return SsdpDevice(
      ip = fromIp,
      location = headers["location"],
      server = headers["server"],
      usn = usn
    )
  }

  private companion object {
    const val TAG = "SsdpDiscovery"
    const val SSDP_MULTICAST_HOST = "239.255.255.250"
    const val SSDP_PORT = 1900
  }
}
