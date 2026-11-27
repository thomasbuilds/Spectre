package dev.thomasbuilds.spectre.recon

import android.app.Application
import android.net.ConnectivityManager
import android.net.nsd.NsdManager
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.net.InetAddress

private const val STOP_TIMEOUT_MS = 5_000L
private const val MDNS_WINDOW_MS = 8_000L

enum class ReconPhase { IDLE, SCANNING, FINISHED }

data class ReconState(
  val phase: ReconPhase = ReconPhase.IDLE,
  val hosts: Map<InetAddress, HostInfo> = emptyMap(),
  val services: Set<MdnsService> = emptySet(),
  val pinnedToLan: Boolean = true
)

class ReconViewModel(
  application: Application
) : AndroidViewModel(application) {
  private val connectivity = checkNotNull(application.getSystemService(ConnectivityManager::class.java))
  private val nsd = checkNotNull(application.getSystemService(NsdManager::class.java))
  private var scanJob: Job? = null

  val subnet: StateFlow<LocalSubnet?> =
    application.localSubnets().stateIn(
      viewModelScope,
      SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS),
      null
    )
  val state: StateFlow<ReconState>
    field = MutableStateFlow(ReconState())

  fun scan() {
    val target = subnet.value?.takeIf { it.isScannable } ?: return
    val previous = scanJob
    scanJob =
      viewModelScope.launch {
        withContext(NonCancellable) { previous?.cancelAndJoin() }
        ensureActive()
        state.value = ReconState(ReconPhase.SCANNING, pinnedToLan = connectivity.bindProcessToNetwork(target.network))
        try {
          coroutineScope {
            val discovery = launch { discover(target) }
            val networkWatch =
              launch {
                subnet.first { it?.network != target.network }
                discovery.cancel()
              }
            discovery.join()
            networkWatch.cancel()
          }
        } finally {
          connectivity.bindProcessToNetwork(null)
          state.update { it.copy(phase = ReconPhase.FINISHED) }
        }
      }
  }

  fun stop() {
    scanJob?.cancel()
  }

  private suspend fun discover(target: LocalSubnet) =
    coroutineScope {
      val mdns =
        launch { nsd.services(MDNS_WINDOW_MS).collect { service -> state.update { it.copy(services = it.services + service) } } }
      coroutineScope {
        launch { delay(MDNS_WINDOW_MS) }
        launch { ssdpDevices().collect { device -> updateHost(device.address) { it.copy(upnp = device) } } }
        launch { scanHosts(target).collect { host -> updateHost(host.address) { host.copy(upnp = it.upnp) } } }
      }
      mdns.cancel()
    }

  private fun updateHost(
    address: InetAddress,
    transform: (HostInfo) -> HostInfo
  ) = state.update { it.copy(hosts = it.hosts + (address to transform(it.hosts[address] ?: HostInfo(address)))) }
}
