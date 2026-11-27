package dev.thomasbuilds.spectre.help

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import dev.thomasbuilds.spectre.ui.kit.SectionHeader

private data class GlossaryTerm(
  val term: String,
  val definition: String
)

internal fun LazyListScope.glossary() {
  GLOSSARY.forEach { (title, terms) ->
    item(key = title, contentType = "glossarySection") {
      Spacer(Modifier.height(20.dp))
      SectionHeader(title, Modifier.padding(start = 4.dp, bottom = 8.dp))
      Column(
        Modifier
          .fillMaxWidth()
          .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(14.dp))
          .padding(horizontal = 16.dp)
      ) {
        terms.forEachIndexed { index, (term, definition) ->
          if (index > 0) HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
          Column(Modifier.fillMaxWidth().semantics(mergeDescendants = true) {}.padding(vertical = 12.dp)) {
            Text(term, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(3.dp))
            Text(definition, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
          }
        }
      }
    }
  }
}

private val GLOSSARY =
  mapOf(
    "RF exposure" to
      listOf(
        GlossaryTerm(
          "dBm",
          "Decibel-milliwatts, a logarithmic measure of received power. Less negative is stronger: -40 dBm is far stronger than -90 dBm."
        ),
        GlossaryTerm(
          "RF exposure",
          "An estimate of the total radio power reaching your phone right now, summed across cellular, Wi-Fi, and Bluetooth into one figure once all three are measuring. It is dominated by the strongest nearby emitters. Wi-Fi and Bluetooth values are the strength of received packets, not a time average."
        )
      ),
    "Cellular" to
      listOf(
        GlossaryTerm(
          "Serving / Secondary serving / Neighbor",
          "The serving cell is the one your phone is camped on. A secondary serving cell carries extra data alongside it, as with carrier aggregation or 5G NSA. Neighbor cells are others the modem can also see."
        ),
        GlossaryTerm(
          "5G NSA",
          "5G Non-Standalone: a 4G anchor cell with a 5G carrier added for speed. Unless the phone lists the 5G carrier as a separate cell, the reading is the 4G anchor's, not the 5G signal."
        ),
        GlossaryTerm("5G SA", "5G Standalone: pure 5G with no 4G anchor, so the serving cell and its reading are 5G."),
        GlossaryTerm("MCC", "Mobile Country Code: identifies the carrier's country."),
        GlossaryTerm("MNC", "Mobile Network Code: identifies the carrier within that country. MCC and MNC together name the operator."),
        GlossaryTerm(
          "CI / CID / NCI",
          "Cell Identity, the cell's unique id on the network (on 2G, unique within its LAC). CI is 4G, CID is 3G and 2G, NCI is 5G."
        ),
        GlossaryTerm(
          "PCI / PSC",
          "Physical Cell ID (4G/5G) or Primary Scrambling Code (3G): a small, locally reused code that tells apart cells on the same frequency."
        ),
        GlossaryTerm(
          "TAC / LAC",
          "Tracking Area Code (4G/5G) or Location Area Code (3G/2G): the zone a cell belongs to, used by the network to page your phone."
        ),
        GlossaryTerm(
          "ARFCN family",
          "EARFCN (4G), NR-ARFCN (5G), UARFCN (3G), and ARFCN (2G) are channel numbers that map to the exact frequency a cell transmits on."
        ),
        GlossaryTerm("Bands", "The frequency band in use, such as n78 on 5G or B3 on 4G."),
        GlossaryTerm("RSRP", "Reference Signal Received Power: the main 4G/5G signal strength, measured per subcarrier."),
        GlossaryTerm("RSRQ / SS-RSRQ", "Reference Signal Received Quality (SS-RSRQ on 5G): signal quality relative to interference."),
        GlossaryTerm("SINR / SS-SINR", "Signal-to-Interference-plus-Noise Ratio. Higher means a cleaner signal."),
        GlossaryTerm("SS-RSRP / CSI-RSRP", "5G forms of RSRP, measured on the synchronization signal or the CSI reference signal."),
        GlossaryTerm(
          "RSCP",
          "Received Signal Code Power: the 3G signal strength. Shown as RSCP/RSSI when the phone reports RSSI instead of RSCP, or when there is no Ec/No."
        ),
        GlossaryTerm(
          "Ec/No",
          "Energy per chip over noise: the 3G pilot's share of the total channel power, in dB. Spectre uses it to turn RSCP into the channel's full power."
        ),
        GlossaryTerm("SNR", "Signal-to-Noise Ratio of the channel."),
        GlossaryTerm("CQI", "Channel Quality Indicator, the phone's own rating of the link from 0 to 15."),
        GlossaryTerm("RXQUAL", "Received quality of a 2G channel, from 0 (best) to 7 (worst), each a range of bit error rates."),
        GlossaryTerm("BSIC", "Base Station Identity Code: tells apart 2G cells that share a frequency."),
        GlossaryTerm("TA", "Timing Advance: round-trip timing to the tower, which gives a rough distance.")
      ),
    "Wi-Fi" to
      listOf(
        GlossaryTerm("SSID", "The network name. Shown as Hidden when the network does not broadcast it."),
        GlossaryTerm("BSSID", "The access point radio's MAC address. One physical router often runs several."),
        GlossaryTerm(
          "Vendor",
          "The organization that registered the BSSID's address block, usually the access point's maker, from the OUI at its start. Not shown for locally administered BSSIDs, which some routers give their extra networks."
        ),
        GlossaryTerm("Band", "2.4, 5, or 6 GHz. Higher bands are faster but carry less far."),
        GlossaryTerm("Channel / Width", "Which slice of the band is used and how wide it is, from 20 up to 320 MHz. Wider is faster."),
        GlossaryTerm("Center 0 / Center 1", "The center frequencies of a wide or split channel."),
        GlossaryTerm(
          "Security",
          "The protection in use. WPA3 is strongest, then WPA2 and the older WPA; OWE encrypts otherwise-open networks; Open and WEP offer no real protection."
        ),
        GlossaryTerm(
          "Cipher",
          "The encryption in use: CCMP (AES) on WPA2 and WPA3, GCMP-256 on WPA3 high-security, GCMP-128 (a newer AES mode), TKIP (deprecated), SMS4 on WAPI, WEP (broken), Unknown for a cipher Android does not name, or None on an open network."
        ),
        GlossaryTerm("PSK / EAP", "How you authenticate: a Pre-Shared Key (a password) or EAP (enterprise login)."),
        GlossaryTerm("WPS", "Wi-Fi Protected Setup, push-button or PIN pairing. Convenient but a well-known weak point."),
        GlossaryTerm(
          "MFP (802.11w)",
          "Management Frame Protection, which blocks forced-disconnect attacks. Shown as Required, Capable, or Not advertised."
        ),
        GlossaryTerm(
          "FTM responder",
          "Whether the access point supports Fine Timing Measurement (802.11mc, or 802.11az on Android 15+), which lets a phone with Wi-Fi RTT range it for an accurate distance."
        ),
        GlossaryTerm("Passpoint", "Hotspot 2.0: automatic, secure roaming onto partner networks.")
      ),
    "Bluetooth" to
      listOf(
        GlossaryTerm(
          "MAC",
          "The device's Bluetooth address. Phones and wearables usually use a private one that changes every 15 minutes or less for privacy."
        ),
        GlossaryTerm(
          "Vendor",
          "The organization that registered the address block, usually the device or module maker, from the OUI of a public address. Not shown for random addresses on Android 15+; before that the address type is unknown, so a random address occasionally shows a coincidental maker."
        ),
        GlossaryTerm("RSSI", "Received signal strength, in dBm."),
        GlossaryTerm(
          "Adv TX power",
          "The transmit power the device claims it is broadcasting at. Without an iBeacon value, Spectre uses it to estimate distance."
        ),
        GlossaryTerm("iBeacon RSSI@1m", "The signal strength a beacon says it produces at 1 metre, used to estimate distance."),
        GlossaryTerm(
          "Address type",
          "Public (a permanent registered address), Random (static, or a rotating private address), or Anonymous (sent without an address). Shown on Android 15+."
        ),
        GlossaryTerm("PHY", "The LE radio mode: 1M standard, 2M faster, or Coded for long range."),
        GlossaryTerm("Service UUID", "Identifies a capability the device advertises (a GATT service)."),
        GlossaryTerm(
          "Manufacturer / service data",
          "Maker- or service-specific data in the advertisement. Manufacturer is the brand from the Bluetooth SIG company ID, distinct from Vendor (from the OUI of the address)."
        ),
        GlossaryTerm(
          "Adv flags",
          "The advertising flags byte: the device's discoverable mode and whether it supports classic Bluetooth, shown in hex."
        ),
        GlossaryTerm("iBeacon", "Apple's beacon format: a UUID plus major and minor numbers and a 1 metre power value."),
        GlossaryTerm(
          "GATT",
          "Generic Attribute Profile, the tree of services and characteristics on a device. Inspecting it connects and reads the readable values without writing anything; values Android withholds from apps show Restricted by Android."
        ),
        GlossaryTerm("Characteristic properties", "What a value supports: read, write, notify, indicate, and so on.")
      ),
    "GNSS" to
      listOf(
        GlossaryTerm(
          "Constellation",
          "The satellite system: GPS (US), GLONASS (Russia), Galileo (EU), BeiDou (China), QZSS (Japan), NavIC (India), or SBAS (accuracy augmentation)."
        ),
        GlossaryTerm(
          "SV / SVID",
          "Space Vehicle ID, the satellite's number within its constellation. A GLONASS satellite whose slot number is unknown shows FCN instead, its frequency channel from -7 to +6."
        ),
        GlossaryTerm("C/N0 (dB-Hz)", "Carrier-to-noise density, the satellite's signal quality. Around 40 and above is strong."),
        GlossaryTerm(
          "Baseband C/N0",
          "A second C/N0 measured after the phone's radio front-end, so slightly lower than the antenna value. The gap reflects the receiver's own losses."
        ),
        GlossaryTerm("Elevation", "Angle above the horizon, from 0 at the horizon to 90 directly overhead."),
        GlossaryTerm("Azimuth", "Compass bearing to the satellite, where 0 is north."),
        GlossaryTerm(
          "Range rate",
          "How fast the distance to the satellite is changing, in metres per second, approaching as it rises and receding as it sets. It includes your own movement toward or away from the satellite."
        ),
        GlossaryTerm(
          "Ephemeris data",
          "Whether the receiver holds the satellite's precise orbit data, which it needs to use the satellite in a fix. A strong satellite without it is still downloading and not yet usable."
        ),
        GlossaryTerm(
          "Almanac data",
          "Whether the receiver holds coarse, long-lived orbit data for the satellite, used to know roughly where to look for it."
        ),
        GlossaryTerm("In fix", "Marks a band the receiver is actively using to compute your position."),
        GlossaryTerm(
          "Bands (L1, L5, E1...)",
          "The carrier frequencies a satellite transmits on. Tracking two at once, dual-frequency, sharpens accuracy."
        )
      ),
    "Local network scan" to
      listOf(
        GlossaryTerm("Local IP / Gateway", "Your phone's own address on the network, and the router's address."),
        GlossaryTerm(
          "CIDR",
          "The subnet size, such as /24. Subnets from /22 (1,022 addresses) to /30 are scanned in full, wider ones as the /22 around your phone; /31 and /32 are not scanned."
        ),
        GlossaryTerm("Interface", "The network interface in use, for example wlan0."),
        GlossaryTerm("Host", "A device found responding on the local network."),
        GlossaryTerm("Open port", "A TCP port accepting connections, labeled with its usual service such as SSH or HTTP."),
        GlossaryTerm("Banner", "Text a service returns when connected to, often revealing its software and version."),
        GlossaryTerm("mDNS / DNS-SD", "Multicast DNS service discovery (Bonjour): how devices announce services like AirPlay or printers."),
        GlossaryTerm("SSDP / UPnP", "Discovery for routers, smart TVs, media servers, and similar devices.")
      )
  )
