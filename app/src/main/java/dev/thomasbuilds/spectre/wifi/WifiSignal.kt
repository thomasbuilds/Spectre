package dev.thomasbuilds.spectre.wifi

import androidx.compose.runtime.Immutable
import dev.thomasbuilds.spectre.core.dbmToMw

enum class WifiBand(
  val label: String
) {
  GHZ_2_4("2.4G"),
  GHZ_5("5.0G"),
  GHZ_6("6.0G");

  companion object {
    fun of(frequencyMhz: Int): WifiBand =
      when {
        frequencyMhz < 3_000 -> GHZ_2_4
        frequencyMhz < 5_925 -> GHZ_5
        else -> GHZ_6
      }
  }
}

enum class WifiSecurity(
  val label: String
) {
  WPA3("WPA3"),
  WPA2("WPA2"),
  WPA("WPA"),
  PSK("PSK"),
  EAP("EAP"),
  OWE("OWE"),
  WAPI("WAPI"),
  WEP("WEP"),
  OPEN("Open")
}

enum class ManagementFrameProtection(
  val label: String
) {
  REQUIRED("Required"),
  CAPABLE("Capable"),
  NONE("Not advertised")
}

@Immutable
data class WifiSignal(
  val bssid: String,
  val ssid: String?,
  val vendor: String?,
  val rssi: Int,
  val isRssiLive: Boolean,
  val band: WifiBand,
  val frequencyMhz: Int,
  val channel: Int?,
  val widthMhz: Int,
  val centerFrequency0Mhz: Int?,
  val centerFrequency1Mhz: Int?,
  val security: Set<WifiSecurity>,
  val ciphers: List<String>,
  val hasWps: Boolean,
  val managementFrameProtection: ManagementFrameProtection,
  val isFtmResponder: Boolean,
  val isPasspoint: Boolean,
  val distanceMeters: Double?,
  val isConnected: Boolean,
  val firstSeenMs: Long,
  val isStale: Boolean
)

internal fun List<WifiSignal>.receivedPowerMw(): Double =
  filterNot { it.isStale }
    .groupBy { it.frequencyMhz }
    .values
    .sumOf { channel -> dbmToMw(channel.maxOf { it.rssi }) }
