package dev.thomasbuilds.spectre.bluetooth.gatt

import java.util.UUID

private const val BASE_UUID_SUFFIX = "-0000-1000-8000-00805f9b34fb"

internal fun uuid16(value: Int): UUID = UUID.fromString("0000${value.toShort().toHexString()}$BASE_UUID_SUFFIX")

internal fun shortUuidCode(uuid: String): String? =
  uuid.lowercase().takeIf { it.startsWith("0000") && it.endsWith(BASE_UUID_SUFFIX) }?.substring(4, 8)

internal fun shortUuid(uuid: String): String = shortUuidCode(uuid)?.let { "0x$it" } ?: uuid.lowercase()

internal fun gattServiceName(uuid: String): String? = shortUuidCode(uuid)?.let(SERVICES::get) ?: FULL_SERVICES[uuid.lowercase()]

internal fun gattCharacteristicName(uuid: String): String? =
  shortUuidCode(uuid)?.let(CHARACTERISTICS::get) ?: FULL_CHARACTERISTICS[uuid.lowercase()]

private val SERVICES =
  mapOf(
    "1800" to "Generic Access",
    "1801" to "Generic Attribute",
    "1802" to "Immediate Alert",
    "1803" to "Link Loss",
    "1804" to "Tx Power",
    "1805" to "Current Time",
    "1806" to "Reference Time Update",
    "1807" to "Next DST Change",
    "1808" to "Glucose",
    "1809" to "Health Thermometer",
    "180a" to "Device Information",
    "180d" to "Heart Rate",
    "180e" to "Phone Alert Status",
    "180f" to "Battery",
    "1810" to "Blood Pressure",
    "1811" to "Alert Notification",
    "1812" to "Human Interface Device",
    "1813" to "Scan Parameters",
    "1814" to "Running Speed and Cadence",
    "1816" to "Cycling Speed and Cadence",
    "1818" to "Cycling Power",
    "1819" to "Location and Navigation",
    "181a" to "Environmental Sensing",
    "181b" to "Body Composition",
    "181c" to "User Data",
    "181d" to "Weight Scale",
    "181e" to "Bond Management",
    "181f" to "Continuous Glucose Monitoring",
    "1822" to "Pulse Oximeter",
    "1826" to "Fitness Machine",
    "feaa" to "Eddystone",
    "fd6f" to "Exposure Notification",
    "fe2c" to "Google Fast Pair / Find My Device",
    "fef5" to "Dialog Semiconductor"
  )

private val CHARACTERISTICS =
  mapOf(
    "2a00" to "Device Name",
    "2a01" to "Appearance",
    "2a02" to "Peripheral Privacy Flag",
    "2a03" to "Reconnection Address",
    "2a04" to "Preferred Connection Parameters",
    "2a05" to "Service Changed",
    "2aa6" to "Central Address Resolution",
    "2ac9" to "Resolvable Private Address Only",
    "2b29" to "Client Supported Features",
    "2b2a" to "Database Hash",
    "2b3a" to "Server Supported Features",
    "2a08" to "Date Time",
    "2a09" to "Day of Week",
    "2a0a" to "Day Date Time",
    "2a0c" to "Exact Time 256",
    "2a0d" to "DST Offset",
    "2a0e" to "Time Zone",
    "2a0f" to "Local Time Information",
    "2a11" to "Time with DST",
    "2a12" to "Time Accuracy",
    "2a13" to "Time Source",
    "2a14" to "Reference Time Information",
    "2a16" to "Time Update Control Point",
    "2a17" to "Time Update State",
    "2a2b" to "Current Time",
    "2a19" to "Battery Level",
    "2a23" to "System ID",
    "2a24" to "Model Number",
    "2a25" to "Serial Number",
    "2a26" to "Firmware Revision",
    "2a27" to "Hardware Revision",
    "2a28" to "Software Revision",
    "2a29" to "Manufacturer Name",
    "2a2a" to "Regulatory Cert Data",
    "2a50" to "PnP ID",
    "2a37" to "Heart Rate Measurement",
    "2a38" to "Body Sensor Location",
    "2a39" to "Heart Rate Control Point",
    "2a6d" to "Pressure",
    "2a6e" to "Temperature",
    "2a6f" to "Humidity",
    "2a70" to "True Wind Speed",
    "2a71" to "True Wind Direction",
    "2a76" to "UV Index",
    "2a77" to "Irradiance",
    "2a78" to "Rainfall",
    "2a7b" to "Dew Point",
    "2a06" to "Alert Level",
    "2a3f" to "Alert Status",
    "2a40" to "Ringer Control Point",
    "2a41" to "Ringer Setting",
    "2a44" to "Alert Notification Control Point",
    "2a45" to "Unread Alert Status",
    "2a46" to "New Alert",
    "2a47" to "Supported New Alert Category",
    "2a48" to "Supported Unread Alert Category",
    "2a4a" to "HID Information",
    "2a4b" to "Report Map",
    "2a4c" to "HID Control Point",
    "2a4d" to "Report",
    "2a4e" to "Protocol Mode",
    "2a4f" to "Scan Interval Window",
    "2a07" to "Tx Power Level",
    "2af9" to "Generic Level"
  )

private val FULL_SERVICES =
  mapOf(
    "7905f431-b5ce-4e99-a40f-4b1e122d00d0" to "Apple Notification Center (ANCS)",
    "89d3502b-0f36-433a-8ef4-c502ad55f8dc" to "Apple Media Service (AMS)"
  )

private val FULL_CHARACTERISTICS =
  mapOf(
    "9fbf120d-6301-42d9-8c58-25e699a21dbd" to "ANCS Notification Source",
    "69d1d8f3-45e1-49a8-9821-9bbdfdaad9d9" to "ANCS Control Point",
    "22eac6e9-24d6-4bb5-be44-b36ace7c7bfb" to "ANCS Data Source",
    "9b3c81d8-57b1-4a8a-b8df-0e56f7ca51c2" to "AMS Remote Command",
    "2f7cabce-808d-411f-9a0c-bb92ba96c102" to "AMS Entity Update",
    "c6b2f38c-23ab-46d8-a6ab-a3a870bbd5d7" to "AMS Entity Attribute"
  )
