package dev.thomasbuilds.spectre.bluetooth.gatt

import java.math.BigDecimal
import java.nio.ByteBuffer
import java.nio.ByteOrder

private const val UNKNOWN = "Unknown"
private const val UINT16_UNKNOWN = 0xFFFF
private val PREFIXED_HEX = HexFormat { number { prefix = "0x" } }
private val SPACED_HEX = HexFormat { bytes { byteSeparator = " " } }

internal fun decodeGattValue(
  uuid: String,
  bytes: ByteArray
): String {
  if (bytes.isEmpty()) return "(empty)"
  val value = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
  return when (shortUuidCode(uuid)) {
    "2a01" if bytes.size >= 2 -> appearance(value.uint16(0))
    "2a04" if bytes.size >= 8 -> connectionParameters(value)
    "2a04", "2a05", "2a23", "2a2a", "2a50" -> bytes.toSpacedHex()
    "2a07" -> "${bytes[0]} dBm"
    "2a19" -> "${bytes[0].toInt() and 0xFF}%"
    "2a38" -> BODY_SENSOR_LOCATIONS.getOrNull(bytes[0].toInt() and 0xFF) ?: bytes[0].toHexString(PREFIXED_HEX)
    "2a6d" if bytes.size >= 4 -> "${fixed(value.getInt(0).toLong() and 0xFFFFFFFFL, 1)} Pa"
    "2a6e" if bytes.size >= 2 -> value.getShort(0).let { if (it == Short.MIN_VALUE) UNKNOWN else "${fixed(it.toLong(), 2)} °C" }
    "2a6f" if bytes.size >= 2 -> value.uint16(0).let { if (it == UINT16_UNKNOWN) UNKNOWN else "${fixed(it.toLong(), 2)} %RH" }
    "2aa6" -> if (bytes[0].toInt() == 0) "Not supported" else "Supported"
    "2af9" if bytes.size >= 2 -> value.uint16(0).toString()
    else -> bytes.textOrNull() ?: bytes.toSpacedHex()
  }
}

internal fun ByteArray.toSpacedHex(): String = toHexString(SPACED_HEX)

private fun ByteBuffer.uint16(index: Int): Int = getShort(index).toInt() and 0xFFFF

private fun fixed(
  unscaled: Long,
  scale: Int
): String = BigDecimal.valueOf(unscaled, scale).toPlainString()

private fun ByteArray.textOrNull(): String? {
  val text =
    try {
      decodeToString(throwOnInvalidSequence = true)
    } catch (_: CharacterCodingException) {
      return null
    }
  return text.trim { it.isWhitespace() || it == '\u0000' }.takeIf { it.isNotEmpty() && it.none(Char::isISOControl) }
}

private fun appearance(code: Int): String {
  val category = APPEARANCE_CATEGORIES[code shr 6] ?: return code.toShort().toHexString(PREFIXED_HEX)
  val subcategory = code and 0x3F
  return if (subcategory == 0) category else "$category (#$subcategory)"
}

private fun connectionParameters(value: ByteBuffer): String {
  val timeout = value.uint16(6)
  return "interval ${interval(value.uint16(0))}–${interval(value.uint16(2))} ms, latency ${value.uint16(4)}, " +
    "timeout ${if (timeout == UINT16_UNKNOWN) UNKNOWN else "${timeout * 10} ms"}"
}

private fun interval(raw: Int): String = if (raw == UINT16_UNKNOWN) UNKNOWN else fixed(raw * 125L, 2)

private val BODY_SENSOR_LOCATIONS = listOf("Other", "Chest", "Wrist", "Finger", "Hand", "Ear lobe", "Foot")

private val APPEARANCE_CATEGORIES =
  mapOf(
    0x00 to "Unknown",
    0x01 to "Phone",
    0x02 to "Computer",
    0x03 to "Watch",
    0x04 to "Clock",
    0x05 to "Display",
    0x06 to "Remote control",
    0x07 to "Eye glasses",
    0x08 to "Tag",
    0x09 to "Keyring",
    0x0A to "Media player",
    0x0B to "Barcode scanner",
    0x0C to "Thermometer",
    0x0D to "Heart-rate sensor",
    0x0E to "Blood-pressure",
    0x0F to "HID",
    0x10 to "Glucose meter",
    0x11 to "Running / walking sensor",
    0x12 to "Cycling",
    0x13 to "Control device",
    0x14 to "Network device",
    0x15 to "Sensor",
    0x16 to "Light fixtures",
    0x17 to "Fan",
    0x18 to "HVAC",
    0x19 to "Air conditioning",
    0x1A to "Humidifier",
    0x1B to "Heating",
    0x1C to "Access control",
    0x1D to "Motorized device",
    0x1E to "Power device",
    0x1F to "Light source",
    0x20 to "Window covering",
    0x21 to "Audio sink",
    0x22 to "Audio source",
    0x23 to "Motorized vehicle",
    0x24 to "Domestic appliance",
    0x25 to "Wearable audio device",
    0x26 to "Aircraft",
    0x27 to "AV equipment",
    0x28 to "Display equipment",
    0x29 to "Hearing aid",
    0x2A to "Gaming",
    0x2B to "Signage",
    0x31 to "Pulse oximeter",
    0x32 to "Weight scale",
    0x33 to "Personal mobility device",
    0x34 to "Continuous glucose monitor",
    0x35 to "Insulin pump",
    0x36 to "Medication delivery",
    0x37 to "Spirometer",
    0x51 to "Outdoor sports activity",
    0x52 to "Industrial measurement device",
    0x53 to "Industrial tools",
    0x54 to "Cookware device"
  )
