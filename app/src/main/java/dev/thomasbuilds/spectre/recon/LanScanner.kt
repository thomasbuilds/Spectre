package dev.thomasbuilds.spectre.recon

import androidx.compose.runtime.Immutable
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.asExecutor
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import java.io.IOException
import java.net.InetAddress
import java.net.InetSocketAddress
import java.nio.ByteBuffer
import java.nio.channels.AsynchronousChannelGroup
import java.nio.channels.AsynchronousSocketChannel
import java.nio.channels.CompletionHandler
import java.util.concurrent.Executors
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

private const val HOST_WORKERS = 46
private const val PROBE_TIMEOUT_MS = 1_500L
private const val REVERSE_LOOKUP_TIMEOUT_MS = 500L
private const val RAW_BANNER_MAX_CHARS = 120
private const val MAX_RESPONSE_BYTES = 4096
private const val REVERSE_LOOKUP_THREADS = 64
private val REVERSE_LOOKUPS = Dispatchers.IO.limitedParallelism(REVERSE_LOOKUP_THREADS).asExecutor()
private val NO_PAYLOAD_PORTS = setOf(515) + (9100..9107)
private val SSH_BANNER = Regex("(?m)^SSH-\\d[\\d.]*-(\\S+)")
private val VNC_BANNER = Regex("^RFB (\\d{3}\\.\\d{3})")

enum class BannerProbe { NONE, GREETING, HTTP, RTSP }

enum class TcpService(
  val port: Int,
  val label: String,
  val probe: BannerProbe = BannerProbe.NONE
) {
  SSH(22, "SSH", BannerProbe.GREETING),
  DNS(53, "DNS"),
  HTTP(80, "HTTP", BannerProbe.HTTP),
  NETBIOS(139, "SMB"),
  HTTPS(443, "HTTPS"),
  SMB(445, "SMB"),
  LPD(515, "LPD"),
  AFP(548, "AFP"),
  RTSP(554, "RTSP", BannerProbe.RTSP),
  IPP(631, "IPP", BannerProbe.HTTP),
  SSDP(1900, "SSDP"),
  NFS(2049, "NFS"),
  RDP(3389, "RDP"),
  UPNP(5000, "UPnP", BannerProbe.HTTP),
  MDNS(5353, "mDNS"),
  VNC(5900, "VNC", BannerProbe.GREETING),
  HTTP_ALT(8000, "HTTP-alt", BannerProbe.HTTP),
  CHROMECAST(8008, "Chromecast", BannerProbe.HTTP),
  HTTP_PROXY(8080, "HTTP-proxy", BannerProbe.HTTP),
  HTTPS_ALT(8443, "HTTPS-alt", BannerProbe.HTTP),
  JETDIRECT(9100, "JetDirect"),
  PLEX(32400, "Plex", BannerProbe.HTTP)
}

data class OpenPort(
  val service: TcpService,
  val banner: String?
)

@Immutable
data class HostInfo(
  val address: InetAddress,
  val hostname: String? = null,
  val ports: List<OpenPort> = emptyList(),
  val upnp: SsdpDevice? = null
)

internal fun scanHosts(subnet: LocalSubnet): Flow<HostInfo> =
  channelFlow {
    val group = AsynchronousChannelGroup.withFixedThreadPool(1, Executors.defaultThreadFactory())
    try {
      val queue = Channel<InetAddress>(Channel.UNLIMITED)
      subnet.hostAddresses().forEach(queue::trySend)
      queue.close()
      coroutineScope {
        repeat(HOST_WORKERS) {
          launch {
            for (address in queue) {
              val host = group.probe(address) ?: continue
              send(host)
              reverseLookup(address)?.let { send(host.copy(hostname = it)) }
            }
          }
        }
      }
    } finally {
      group.shutdownNow()
    }
  }.flowOn(Dispatchers.IO)

private suspend fun AsynchronousChannelGroup.probe(address: InetAddress): HostInfo? {
  val open =
    coroutineScope {
      TcpService.entries
        .map { service -> async { service.takeIf { connects(address, it.port) } } }
        .awaitAll()
        .filterNotNull()
    }
  if (open.isEmpty()) return null
  val ports = coroutineScope { open.map { async { OpenPort(it, banner(address, it)) } }.awaitAll() }
  return HostInfo(address, ports = ports)
}

private suspend fun AsynchronousChannelGroup.banner(
  address: InetAddress,
  service: TcpService
): String? {
  if (service.port in NO_PAYLOAD_PORTS) return null
  val request =
    when (service.probe) {
      BannerProbe.NONE -> return null
      BannerProbe.GREETING -> null
      BannerProbe.HTTP -> "GET / HTTP/1.0\r\nHost: ${address.hostAddress}\r\nUser-Agent: Spectre-Recon\r\nConnection: close\r\n\r\n"
      BannerProbe.RTSP -> "OPTIONS * RTSP/1.0\r\nCSeq: 1\r\n\r\n"
    }
  return exchange(address, service.port, request?.toByteArray(Charsets.US_ASCII))?.let { fingerprint(it) ?: rawBanner(it) }
}

private fun fingerprint(response: String): String? =
  when {
    response.startsWith("HTTP/") -> {
      response
        .lineSequence()
        .drop(1)
        .takeWhile { it.isNotEmpty() }
        .firstOrNull { it.startsWith("Server:", ignoreCase = true) }
        ?.substringAfter(':')
        ?.trim()
        ?.takeIf { it.isNotEmpty() }
        ?.let { "http · $it" } ?: "http"
    }

    response.startsWith("RTSP/") -> {
      "rtsp"
    }

    else -> {
      SSH_BANNER.find(response)?.let { "ssh · ${it.groupValues[1]}" }
        ?: VNC_BANNER.find(response)?.let { "vnc · ${it.groupValues[1]}" }
    }
  }

private fun rawBanner(response: String): String? =
  response
    .lineSequence()
    .firstOrNull()
    ?.filterNot(Char::isISOControl)
    ?.trim()
    ?.takeIf { it.isNotEmpty() }
    ?.take(RAW_BANNER_MAX_CHARS)

private suspend fun AsynchronousChannelGroup.connects(
  address: InetAddress,
  port: Int
): Boolean = withConnection(address, port) { true } == true

private suspend fun AsynchronousChannelGroup.exchange(
  address: InetAddress,
  port: Int,
  request: ByteArray?
): String? =
  withConnection(address, port) { channel ->
    if (request != null) channel.await<Int> { channel.write(ByteBuffer.wrap(request), null, it) }
    val buffer = ByteBuffer.allocate(MAX_RESPONSE_BYTES)
    val read = channel.await<Int> { channel.read(buffer, null, it) }
    if (read > 0) String(buffer.array(), 0, read, Charsets.ISO_8859_1) else null
  }

private suspend fun <T : Any> AsynchronousChannelGroup.withConnection(
  address: InetAddress,
  port: Int,
  session: suspend (AsynchronousSocketChannel) -> T?
): T? =
  try {
    AsynchronousSocketChannel.open(this).use { channel ->
      withTimeoutOrNull(PROBE_TIMEOUT_MS) {
        channel.await<Void?> { channel.connect(InetSocketAddress(address, port), null, it) }
        session(channel)
      }
    }
  } catch (_: IOException) {
    null
  }

private suspend fun reverseLookup(address: InetAddress): String? =
  withTimeoutOrNull(REVERSE_LOOKUP_TIMEOUT_MS) {
    suspendCancellableCoroutine { continuation ->
      REVERSE_LOOKUPS.execute {
        if (continuation.isActive) continuation.resume(address.canonicalHostName.takeIf { it != address.hostAddress })
      }
    }
  }

private suspend fun <T> AsynchronousSocketChannel.await(start: (CompletionHandler<T, Any?>) -> Unit): T =
  suspendCancellableCoroutine { continuation ->
    continuation.invokeOnCancellation { close() }
    start(
      object : CompletionHandler<T, Any?> {
        override fun completed(
          result: T,
          attachment: Any?
        ) = continuation.resume(result)

        override fun failed(
          exc: Throwable,
          attachment: Any?
        ) = continuation.resumeWithException(exc)
      }
    )
  }
