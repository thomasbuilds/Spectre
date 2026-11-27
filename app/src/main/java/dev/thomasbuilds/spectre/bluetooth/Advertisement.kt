package dev.thomasbuilds.spectre.bluetooth

import android.bluetooth.le.ScanRecord
import android.os.ParcelUuid
import androidx.core.util.forEach
import dev.thomasbuilds.spectre.bluetooth.gatt.shortUuid
import dev.thomasbuilds.spectre.bluetooth.gatt.toSpacedHex
import dev.thomasbuilds.spectre.bluetooth.gatt.uuid16

internal const val APPLE_COMPANY_ID = 0x004C
private const val MICROSOFT_COMPANY_ID = 0x0006
private const val IBEACON_TYPE = 0x02
private const val IBEACON_LENGTH = 0x15
private const val IBEACON_SIZE = 23
private const val IBEACON_MEASURED_POWER_INDEX = 22
private val FAST_PAIR_UUID = ParcelUuid(uuid16(0xFE2C))
private val EDDYSTONE_UUID = ParcelUuid(uuid16(0xFEAA))
private val FIND_HUB_FRAME_TYPES = 0x40..0x41
private val PREFIXED_HEX =
  HexFormat {
    upperCase = true
    number { prefix = "0x" }
  }

internal fun Byte.toPrefixedHex(): String = toHexString(PREFIXED_HEX)

internal fun Short.toPrefixedHex(): String = toHexString(PREFIXED_HEX)

internal fun ibeaconMeasuredPower(appleData: ByteArray): Int? =
  if (appleData.size >= IBEACON_SIZE && appleData[0].toInt() == IBEACON_TYPE && appleData[1].toInt() == IBEACON_LENGTH) {
    appleData[IBEACON_MEASURED_POWER_INDEX].toInt()
  } else {
    null
  }

internal fun appleTypes(appleData: ByteArray): List<String> =
  buildList {
    var index = 0
    while (index + 1 < appleData.size) {
      val type = appleData[index]
      add(APPLE_TYPES[type.toInt() and 0xFF] ?: "Continuity ${type.toPrefixedHex()}")
      index += 2 + (appleData[index + 1].toInt() and 0xFF)
    }
  }

internal fun microsoftType(record: ScanRecord): String? =
  record.getManufacturerSpecificData(MICROSOFT_COMPANY_ID)?.firstOrNull()?.let { scenario ->
    when (scenario.toInt() and 0xFF) {
      0x01 -> "Connected Devices Platform"
      0x03 -> "Swift Pair"
      else -> "Microsoft ${scenario.toPrefixedHex()}"
    }
  }

internal fun googleType(record: ScanRecord): String? {
  val eddystoneFrame = record.getServiceData(EDDYSTONE_UUID)?.firstOrNull()?.let { it.toInt() and 0xFF }
  return when {
    eddystoneFrame in FIND_HUB_FRAME_TYPES -> "Find Hub network"
    eddystoneFrame != null -> "Eddystone"
    record.getServiceData(FAST_PAIR_UUID) != null -> "Fast Pair"
    else -> null
  }
}

internal fun advertisementHex(
  record: ScanRecord,
  companies: CompanyIdLookup
): String? =
  buildList {
    record.manufacturerSpecificData?.forEach { id, bytes ->
      if (bytes.isNotEmpty()) {
        add("Manufacturer ${id.toShort().toPrefixedHex()} (${companies.name(id) ?: "Unknown"}): ${bytes.toSpacedHex()}")
      }
    }
    record.serviceData?.forEach { (uuid, bytes) ->
      if (bytes.isNotEmpty()) add("Service ${shortUuid(uuid.toString())}: ${bytes.toSpacedHex()}")
    }
  }.joinToString("\n").ifEmpty { null }

private val APPLE_TYPES =
  mapOf(
    0x02 to "iBeacon",
    0x03 to "AirPrint",
    0x05 to "AirDrop",
    0x06 to "HomeKit",
    0x07 to "Proximity pairing",
    0x08 to "Hey Siri",
    0x09 to "AirPlay target",
    0x0A to "AirPlay source",
    0x0B to "Magic Switch",
    0x0C to "Handoff",
    0x0D to "Instant Hotspot request",
    0x0E to "Instant Hotspot",
    0x0F to "Nearby action",
    0x10 to "Nearby info",
    0x12 to "Find My"
  )
