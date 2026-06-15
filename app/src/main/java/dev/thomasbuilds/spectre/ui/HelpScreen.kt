package dev.thomasbuilds.spectre.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

@Composable
fun HelpScreen(onBack: () -> Unit) {
  val insets = WindowInsets.safeDrawing.asPaddingValues()
  Surface(
    modifier = Modifier.fillMaxSize(),
    color = MaterialTheme.colorScheme.background
  ) {
    Column(modifier = Modifier.fillMaxSize().padding(insets)) {
      BackTopBar(title = "Help & Glossary", onBack = onBack)

      Column(
        modifier =
          Modifier
            .weight(1f)
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp)
      ) {
        Text(
          "Plain-language definitions for the labels, acronyms, and readings shown across the app.",
          style = MaterialTheme.typography.bodyMedium,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
          modifier = Modifier.padding(top = 4.dp, start = 4.dp, end = 4.dp)
        )
        GLOSSARY.forEach { section -> GlossarySectionView(section) }
        Spacer(Modifier.height(28.dp))
      }
    }
  }
}

@Composable
private fun GlossarySectionView(section: GlossarySection) {
  Spacer(Modifier.height(20.dp))
  Text(
    section.title,
    style = MaterialTheme.typography.titleMedium,
    color = MaterialTheme.colorScheme.primary,
    modifier = Modifier.padding(start = 4.dp, bottom = 8.dp)
  )
  Column(
    modifier =
      Modifier
        .fillMaxWidth()
        .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(14.dp))
        .padding(horizontal = 16.dp)
  ) {
    section.entries.forEachIndexed { index, entry ->
      if (index > 0) {
        HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.4f))
      }
      Column(modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp)) {
        Text(
          entry.term,
          style = MaterialTheme.typography.bodyMedium,
          fontWeight = FontWeight.SemiBold,
          color = MaterialTheme.colorScheme.onSurface
        )
        Spacer(Modifier.height(3.dp))
        Text(
          entry.definition,
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant
        )
      }
    }
  }
}

private class GlossaryEntry(
  val term: String,
  val definition: String
)

private class GlossarySection(
  val title: String,
  val entries: List<GlossaryEntry>
)

private fun e(
  term: String,
  definition: String
) = GlossaryEntry(term, definition)

private val GLOSSARY =
  listOf(
    GlossarySection(
      "RF exposure",
      listOf(
        e(
          "dBm",
          "Decibel-milliwatts, a logarithmic measure of received power. Less negative is stronger: -40 dBm is far stronger than -90 dBm."
        ),
        e(
          "RF exposure",
          "The total radio power reaching your phone right now, summed across cellular, WiFi, and Bluetooth into one figure. It is dominated by the strongest nearby emitters."
        )
      )
    ),
    GlossarySection(
      "Cellular",
      listOf(
        e("Serving / Neighbor", "The serving cell is the one your phone is camped on. Neighbor cells are others the modem can also see."),
        e(
          "5G NSA",
          "5G Non-Standalone: a 4G anchor cell with a 5G carrier added for speed. Android exposes only the anchor, so the reading is the 4G anchor's, not the 5G signal."
        ),
        e("5G SA", "5G Standalone: pure 5G with no 4G anchor. The only mode where a real 5G signal reading is available."),
        e("MCC", "Mobile Country Code: identifies the carrier's country."),
        e("MNC", "Mobile Network Code: identifies the carrier within that country. MCC and MNC together name the operator."),
        e("CI / CID / NCI", "Cell Identity, the cell's unique id on the network. CI is 4G, CID is 3G and 2G, NCI is 5G."),
        e(
          "PCI / PSC",
          "Physical Cell ID (4G/5G) or Primary Scrambling Code (3G): a small, locally reused code that tells apart cells on the same frequency."
        ),
        e(
          "TAC / LAC",
          "Tracking Area Code (4G/5G) or Location Area Code (3G/2G): the zone a cell belongs to, used by the network to page your phone."
        ),
        e(
          "ARFCN family",
          "EARFCN (4G), NR-ARFCN (5G), UARFCN (3G), and ARFCN (2G) are channel numbers that map to the exact frequency a cell transmits on."
        ),
        e("Bands", "The frequency band in use, such as n78 on 5G or B3 on 4G."),
        e("RSRP", "Reference Signal Received Power: the main 4G/5G signal strength, measured per subcarrier."),
        e("RSRQ", "Reference Signal Received Quality: signal quality relative to interference."),
        e("SINR / SS-SINR", "Signal-to-Interference-plus-Noise Ratio. Higher means a cleaner signal."),
        e("SS-RSRP / CSI-RSRP", "5G forms of RSRP, measured on the synchronization signal or the CSI reference signal."),
        e("RSCP", "Received Signal Code Power: the 3G signal strength."),
        e("Ec/No", "3G signal quality: useful energy versus noise."),
        e("SNR", "Signal-to-Noise Ratio of the channel."),
        e("CQI", "Channel Quality Indicator, the phone's own rating of the link from 0 to 15."),
        e("BER", "Bit Error Rate: the error level on a 2G channel."),
        e("BSIC", "Base Station Identity Code: tells apart 2G cells that share a frequency."),
        e("TA", "Timing Advance: round-trip timing to the tower, which gives a rough distance.")
      )
    ),
    GlossarySection(
      "WiFi",
      listOf(
        e("SSID", "The network name. Shown as Hidden when the network does not broadcast it."),
        e("BSSID", "The access point radio's MAC address. One physical router often runs several."),
        e("Band", "2.4, 5, or 6 GHz. Higher bands are faster but carry less far."),
        e("Channel / Width", "Which slice of the band is used and how wide it is, from 20 up to 160 MHz. Wider is faster."),
        e("Center 0 / Center 1", "The center frequencies of a wide or split channel."),
        e(
          "Security",
          "The protection in use. WPA3 is strongest, then WPA2 and the older WPA; OWE encrypts otherwise-open networks; Open and WEP offer no real protection."
        ),
        e("PSK / EAP", "How you authenticate: a Pre-Shared Key (a password) or EAP (enterprise login)."),
        e("WPS", "WiFi Protected Setup, push-button or PIN pairing. Convenient but a well-known weak point."),
        e(
          "MFP (802.11w)",
          "Management Frame Protection, which blocks forced-disconnect attacks. Shown as Required, Capable, or Not advertised."
        ),
        e("802.11mc FTM", "Fine Timing Measurement, which lets the phone range the access point for an accurate distance."),
        e("Passpoint", "Hotspot 2.0: automatic, secure roaming onto partner networks.")
      )
    ),
    GlossarySection(
      "Bluetooth",
      listOf(
        e("MAC", "The device's Bluetooth address. Most devices rotate a random one every few minutes for privacy."),
        e("RSSI", "Received signal strength, in dBm."),
        e("Adv TX power", "The transmit power the device claims it is broadcasting at."),
        e("iBeacon RSSI@1m", "The signal strength a beacon says it produces at 1 metre, used to estimate distance."),
        e("Address type", "Public (a permanent registered address), Random (a rotating privacy address), or Anonymous."),
        e("PHY", "The LE radio mode: 1M standard, 2M faster, or Coded for long range."),
        e("Service UUID", "Identifies a capability the device advertises (a GATT service)."),
        e("Manufacturer / service data", "Vendor-specific data in the advertisement, used to identify the maker and product."),
        e("iBeacon", "Apple's beacon format: a UUID plus major and minor numbers and a 1 metre power value."),
        e(
          "GATT",
          "Generic Attribute Profile, the tree of services and characteristics on a device. Inspecting it connects and reads those values."
        ),
        e("Characteristic properties", "What a value supports: read, write, notify, indicate, and so on.")
      )
    ),
    GlossarySection(
      "GNSS",
      listOf(
        e(
          "Constellation",
          "The satellite system: GPS (US), GLONASS (Russia), Galileo (EU), BeiDou (China), QZSS (Japan), NavIC (India), or SBAS (accuracy augmentation)."
        ),
        e("SV / SVID", "Space Vehicle ID, the satellite's number within its constellation."),
        e("C/N0 (dB-Hz)", "Carrier-to-noise density, the satellite's signal quality. Around 40 and above is strong."),
        e("Elevation", "Angle above the horizon, from 0 at the horizon to 90 directly overhead."),
        e("Azimuth", "Compass bearing to the satellite, where 0 is north."),
        e("Used in fix", "Whether this satellite is being used to compute your position."),
        e("Slant range", "Straight-line distance from you to the satellite."),
        e("Orbital altitude", "The satellite's height above the Earth's surface."),
        e(
          "Bands (L1, L5, E1...)",
          "The carrier frequencies a satellite transmits on. Tracking two at once, dual-frequency, sharpens accuracy."
        )
      )
    ),
    GlossarySection(
      "Recon",
      listOf(
        e("Local IP / Gateway", "Your phone's own address on the network, and the router's address."),
        e("CIDR", "The subnet size, such as /24, which sets how many addresses get scanned."),
        e("Interface", "The network interface in use, for example wlan0."),
        e("Host", "A device found responding on the local network."),
        e("Open port", "A TCP port accepting connections, labeled with its usual service such as SSH or HTTP."),
        e("Banner", "Text a service returns when connected to, often revealing its software and version."),
        e("mDNS / DNS-SD", "Multicast DNS service discovery (Bonjour): how devices announce services like AirPlay or printers."),
        e("SSDP / UPnP", "Discovery for routers, smart TVs, media servers, and similar devices.")
      )
    )
  )

internal object HelpEntries {
  val Cellular =
    """
    5G NSA networks anchor your phone to a 4G cell and add a 5G carrier on top for speed. Android only lets apps see the 4G anchor, never the 5G carrier itself, so the strength shown for your connection is the anchor's, not the 5G signal actually carrying your data. Only 5G SA exposes a real 5G reading.

    You'll only see your own carrier's towers. Your phone tunes only to the frequencies your SIM's network uses, plus any roaming partners, so cells from other carriers never appear.

    The signal strength shown is an estimate. A phone doesn't report a cell's total power directly, only a smaller reference measurement, which is converted into the full strength you see. The phone's own reading is used when available, and a best-fit estimate per network type otherwise, normally landing within a few dB of the true value.
    """.trimIndent()

  val WifiThrottle =
    """Android limits 1 scan every 30 seconds. To disable this throttling and scan every 5 seconds, enable Developer options in your phone's settings and turn off "WiFi scan throttling"."""

  val ReconScreen =
    """Scans the local network you're connected to and lists the devices it finds, with the open ports and services on each. It combines direct port probing with mDNS and UPnP discovery, so it also catches devices that announce themselves, such as AirPlay, Chromecast, printers, and media servers."""
}
