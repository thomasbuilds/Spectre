package dev.thomasbuilds.spectre.wifi

import androidx.compose.runtime.Immutable
import dev.thomasbuilds.spectre.ui.list.TextFilter
import java.io.Serializable

enum class WpsFilter(
  val label: String
) {
  ANY("any"),
  ENABLED("true"),
  DISABLED("false")
}

enum class WifiSort(
  val label: String
) {
  SIGNAL("Signal strength"),
  NAME("Name"),
  DETECTION("Detection order")
}

@Immutable
data class WifiFilters(
  val sort: WifiSort = WifiSort.SIGNAL,
  val bands: Set<WifiBand> = emptySet(),
  val security: Set<WifiSecurity> = emptySet(),
  val wps: WpsFilter = WpsFilter.ANY,
  val vendor: TextFilter = TextFilter()
) : Serializable {
  val isActive: Boolean get() = bands.isNotEmpty() || security.isNotEmpty() || wps != WpsFilter.ANY || vendor.isActive

  fun accepts(signal: WifiSignal): Boolean =
    (bands.isEmpty() || signal.band in bands) &&
      (security.isEmpty() || signal.security.any { it in security }) &&
      (wps == WpsFilter.ANY || signal.hasWps == (wps == WpsFilter.ENABLED)) &&
      vendor.accepts { signal.vendor?.lowercase()?.contains(it) == true }

  fun sorted(signals: List<WifiSignal>): List<WifiSignal> =
    when (sort) {
      WifiSort.SIGNAL -> signals.sortedByDescending { it.rssi }
      WifiSort.NAME -> signals.sortedBy { (it.ssid ?: "Hidden").lowercase() }
      WifiSort.DETECTION -> signals.sortedWith { a, b -> a.firstSeenMs.compareTo(b.firstSeenMs) }
    }
}
