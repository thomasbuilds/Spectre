package dev.thomasbuilds.spectre.scanner.ble

object GattValueDecoder {
  fun decode(
    uuid: String,
    bytes: ByteArray
  ): String {
    if (bytes.isEmpty()) return "(empty)"
    val short = shortUuidCode(uuid)
    return when (short) {
      "2a00" -> bytes.asUtf8()
      "2a01" -> appearance(bytes.uint16le())
      "2a04" -> connectionParams(bytes)
      "2a05" -> bytes.toHex()
      "2aa6" -> if (bytes[0].toInt() == 0) "Not supported" else "Supported"
      "2a23" -> bytes.toHex()
      "2a24", "2a25", "2a26", "2a27", "2a28", "2a29", "2a2a" -> bytes.asUtf8()
      "2a19" -> "${bytes[0].toInt() and 0xff}%"
      "2a37" -> heartRate(bytes)
      "2a38" -> bodySensorLocation(bytes[0].toInt() and 0xff)
      "2a6d" -> "${bytes.uint32le() / 10.0} Pa"
      "2a6e" -> "${bytes.int16le() / 100.0} °C"
      "2a6f" -> "${bytes.uint16le() / 100.0} %RH"
      "2af9" -> "${bytes[0].toInt() and 0xff}%"
      else -> bytes.preview()
    }
  }

  private fun ByteArray.asUtf8(): String {
    val s = toString(Charsets.UTF_8).trimEnd(' ').trim()
    return s.ifEmpty { preview() }
  }

  private fun ByteArray.preview(): String {
    if (looksLikeUtf8()) {
      val s = toString(Charsets.UTF_8).trimEnd(' ').trim()
      if (s.isNotEmpty()) return s
    }
    return toHex()
  }

  private fun ByteArray.looksLikeUtf8(): Boolean {
    if (isEmpty()) return false
    var printable = 0
    for (b in this) {
      val c = b.toInt() and 0xff
      if (c == 0) continue
      if (c in 0x20..0x7e) {
        printable++
      } else {
        return false
      }
    }
    return printable >= 1
  }

  private fun ByteArray.toHex(): String = toHexString(SpacedHexFormat)

  private fun ByteArray.uint16le(): Int {
    if (size < 2) return 0
    return (this[0].toInt() and 0xff) or ((this[1].toInt() and 0xff) shl 8)
  }

  private fun ByteArray.int16le(): Int {
    val u = uint16le()
    return if (u >= 0x8000) u - 0x10000 else u
  }

  private fun ByteArray.uint32le(): Long {
    if (size < 4) return 0L
    return (this[0].toLong() and 0xff) or
      ((this[1].toLong() and 0xff) shl 8) or
      ((this[2].toLong() and 0xff) shl 16) or
      ((this[3].toLong() and 0xff) shl 24)
  }

  private fun appearance(code: Int): String {
    val category = code shr 6
    val subType = code and 0x3f
    val name =
      APPEARANCE_CATEGORIES[category]
        ?: return "0x%04x".format(code)
    return if (subType == 0) name else "$name (#$subType)"
  }

  private val APPEARANCE_CATEGORIES =
    mapOf(
      0 to "Unknown",
      1 to "Phone",
      2 to "Computer",
      3 to "Watch",
      4 to "Clock",
      5 to "Display",
      6 to "Remote control",
      7 to "Eye glasses",
      8 to "Tag",
      9 to "Keyring",
      10 to "Media player",
      11 to "Barcode scanner",
      12 to "Thermometer",
      13 to "Heart-rate sensor",
      14 to "Blood-pressure",
      15 to "HID",
      16 to "Glucose meter",
      17 to "Running / walking sensor",
      18 to "Cycling",
      19 to "Control device",
      20 to "Network device",
      21 to "Sensor",
      22 to "Light fixtures",
      23 to "Fan",
      24 to "HVAC",
      25 to "Air conditioning",
      26 to "Humidifier",
      27 to "Heating",
      28 to "Access control",
      29 to "Motorized device",
      30 to "Power device",
      31 to "Light source",
      32 to "Window covering",
      33 to "Audio sink",
      34 to "Audio source",
      35 to "Motorized vehicle",
      36 to "Domestic appliance",
      37 to "Wearable audio device",
      38 to "Aircraft",
      39 to "AV equipment",
      40 to "Display equipment",
      41 to "Hearing aid",
      42 to "Gaming",
      43 to "Signage",
      49 to "Pulse oximeter",
      50 to "Weight scale",
      51 to "Personal mobility device",
      52 to "Continuous glucose monitor",
      53 to "Insulin pump",
      54 to "Medication delivery",
      55 to "Spirometer",
      81 to "Outdoor sports activity"
    )

  private fun connectionParams(bytes: ByteArray): String {
    if (bytes.size < 8) return bytes.toHex()
    val minI = bytes.uint16leAt(0) * 1.25
    val maxI = bytes.uint16leAt(2) * 1.25
    val latency = bytes.uint16leAt(4)
    val timeout = bytes.uint16leAt(6) * 10
    return "interval ${"%.1f".format(minI)}–${"%.1f".format(maxI)} ms, latency $latency, timeout $timeout ms"
  }

  private fun ByteArray.uint16leAt(offset: Int): Int = (this[offset].toInt() and 0xff) or ((this[offset + 1].toInt() and 0xff) shl 8)

  private fun heartRate(bytes: ByteArray): String {
    val flags = bytes[0].toInt() and 0xff
    val wide = (flags and 0x01) != 0
    return if (wide && bytes.size >= 3) {
      "${bytes.uint16leAt(1)} bpm"
    } else if (bytes.size >= 2) {
      "${bytes[1].toInt() and 0xff} bpm"
    } else {
      "(short)"
    }
  }

  private fun bodySensorLocation(code: Int): String =
    when (code) {
      0 -> "Other"
      1 -> "Chest"
      2 -> "Wrist"
      3 -> "Finger"
      4 -> "Hand"
      5 -> "Ear lobe"
      6 -> "Foot"
      else -> "0x%02x".format(code)
    }
}
