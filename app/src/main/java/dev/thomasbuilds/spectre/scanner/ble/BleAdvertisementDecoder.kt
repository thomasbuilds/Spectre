package dev.thomasbuilds.spectre.scanner.ble

import android.bluetooth.le.ScanRecord
import androidx.core.util.size

internal fun advertisementHexBlock(
  scanRecord: ScanRecord,
  companies: CompanyIdLookup
): String? {
  val lines = mutableListOf<String>()
  scanRecord.manufacturerSpecificData?.let { msd ->
    for (i in 0..<msd.size) {
      val cid = msd.keyAt(i)
      val bytes = msd.valueAt(i) ?: continue
      if (bytes.isEmpty()) continue
      val name = companies.name(cid) ?: "Unknown"
      lines += "Manufacturer 0x%04X (%s): %s".format(cid, name, bytesToHex(bytes))
    }
  }
  scanRecord.serviceData
    ?.filterValues { it != null && it.isNotEmpty() }
    ?.forEach { (uuid, bytes) ->
      lines += "Service ${shortenUuid(uuid.uuid.toString())}: ${bytesToHex(bytes)}"
    }
  return if (lines.isEmpty()) null else lines.joinToString("\n")
}

internal val SpacedHexFormat = HexFormat { bytes { byteSeparator = " " } }

internal fun bytesToHex(bytes: ByteArray): String = bytes.toHexString(SpacedHexFormat)

internal fun shortUuidCode(uuid: String): String? {
  val lower = uuid.lowercase()
  return if (lower.startsWith("0000") && lower.endsWith("-0000-1000-8000-00805f9b34fb")) {
    lower.substring(4, 8)
  } else {
    null
  }
}

internal fun shortenUuid(uuid: String): String = shortUuidCode(uuid)?.let { "0x$it" } ?: uuid

internal fun parseIbeaconMeasuredPower(appleData: ByteArray): Int? {
  if (appleData.size < 23) return null
  if ((appleData[0].toInt() and 0xFF) != 0x02) return null
  if ((appleData[1].toInt() and 0xFF) != 0x15) return null
  return appleData[22].toInt()
}

internal fun appleAdvLabel(data: ByteArray): String? {
  if (data.isEmpty()) return null
  return when (data[0].toInt() and 0xFF) {
    0x02 -> "iBeacon"
    0x03 -> "AirPrint"
    0x05 -> "AirDrop"
    0x06 -> "HomeKit"
    0x07 -> "AirPods / proximity pairing"
    0x08 -> "Hey Siri / handoff"
    0x09 -> "AirPlay"
    0x0A -> "Magic-Pair"
    0x0B -> "Magic-Switch"
    0x0C -> "Handoff"
    0x0D -> "Wi-Fi setup"
    0x0E -> "Hotspot"
    0x0F -> "Wi-Fi join"
    0x10 -> "Nearby info"
    0x12 -> "Find My (offline finding)"
    0x14 -> "Activity (continuity)"
    0x16 -> "Find My (owner pairing)"
    else -> "Continuity 0x%02X".format(data[0].toInt() and 0xFF)
  }
}

internal fun msAdvLabel(data: ByteArray): String =
  when (data[0].toInt() and 0xFF) {
    0x01 -> "Swift Pair (peripheral pairing)"
    0x03 -> "Continuum / Phone Link"
    else -> "Microsoft 0x%02X".format(data[0].toInt() and 0xFF)
  }
