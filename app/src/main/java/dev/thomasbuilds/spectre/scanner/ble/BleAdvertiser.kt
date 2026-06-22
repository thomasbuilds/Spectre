package dev.thomasbuilds.spectre.scanner.ble

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothManager
import android.bluetooth.le.AdvertiseCallback
import android.bluetooth.le.AdvertiseData
import android.bluetooth.le.AdvertiseSettings
import android.content.Context
import android.util.Log
import dev.thomasbuilds.spectre.hasPermission
import java.nio.ByteBuffer
import java.util.UUID

class BleAdvertiser(
  private val context: Context
) {
  private val manager: BluetoothManager? =
    context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
  private val adapter get() = manager?.adapter

  private var advertiser: android.bluetooth.le.BluetoothLeAdvertiser? = null
  private var callback: AdvertiseCallback? = null

  fun hasPermission(): Boolean = context.hasPermission(Manifest.permission.BLUETOOTH_ADVERTISE)

  fun isSupported(): Boolean = adapter?.isMultipleAdvertisementSupported == true

  fun startIBeacon(
    uuid: UUID,
    major: Int,
    minor: Int,
    measuredPower: Int,
    onResult: (Boolean, String) -> Unit
  ) {
    startManufacturer(APPLE_COMPANY_ID, buildIBeacon(uuid, major, minor, measuredPower), onResult)
  }

  @SuppressLint("MissingPermission")
  private fun startManufacturer(
    companyId: Int,
    data: ByteArray,
    onResult: (Boolean, String) -> Unit
  ) {
    stop()
    if (!hasPermission()) {
      onResult(false, "BLUETOOTH_ADVERTISE not granted")
      return
    }
    val a = adapter
    if (a == null || !a.isEnabled) {
      onResult(false, "Bluetooth is off")
      return
    }
    val adv = a.bluetoothLeAdvertiser
    if (adv == null || !isSupported()) {
      onResult(false, "This device can't BLE-advertise")
      return
    }
    val settings =
      AdvertiseSettings
        .Builder()
        .setAdvertiseMode(AdvertiseSettings.ADVERTISE_MODE_LOW_LATENCY)
        .setTxPowerLevel(AdvertiseSettings.ADVERTISE_TX_POWER_HIGH)
        .setConnectable(false)
        .setTimeout(0)
        .build()
    val advData =
      AdvertiseData
        .Builder()
        .setIncludeDeviceName(false)
        .setIncludeTxPowerLevel(false)
        .addManufacturerData(companyId, data)
        .build()
    val cb =
      object : AdvertiseCallback() {
        override fun onStartSuccess(settingsInEffect: AdvertiseSettings) {
          onResult(true, "Broadcasting")
        }

        override fun onStartFailure(errorCode: Int) {
          Log.w(TAG, "advertising failed: $errorCode")
          advertiser = null
          callback = null
          onResult(false, "Failed: ${failureLabel(errorCode)}")
        }
      }
    runCatching { adv.startAdvertising(settings, advData, cb) }
      .onSuccess {
        advertiser = adv
        callback = cb
      }.onFailure { onResult(false, "startAdvertising threw: ${it.message}") }
  }

  @SuppressLint("MissingPermission")
  fun stop() {
    val adv = advertiser
    val cb = callback
    if (adv != null && cb != null && hasPermission()) {
      runCatching { adv.stopAdvertising(cb) }
    }
    advertiser = null
    callback = null
  }

  val isAdvertising: Boolean get() = callback != null

  private fun buildIBeacon(
    uuid: UUID,
    major: Int,
    minor: Int,
    measuredPower: Int
  ): ByteArray {
    // Apple iBeacon payload: type 0x02, length 0x15, 16-byte UUID, major, minor, then the
    // 1-byte measured power (RSSI at 1 m). The caller wraps this in Apple's company id.
    val buf = ByteBuffer.allocate(23)
    buf.put(0x02)
    buf.put(0x15)
    buf.putLong(uuid.mostSignificantBits)
    buf.putLong(uuid.leastSignificantBits)
    buf.putShort((major and 0xFFFF).toShort())
    buf.putShort((minor and 0xFFFF).toShort())
    buf.put(measuredPower.toByte())
    return buf.array()
  }

  private fun failureLabel(code: Int): String =
    when (code) {
      AdvertiseCallback.ADVERTISE_FAILED_DATA_TOO_LARGE -> "payload too large"
      AdvertiseCallback.ADVERTISE_FAILED_TOO_MANY_ADVERTISERS -> "too many advertisers"
      AdvertiseCallback.ADVERTISE_FAILED_ALREADY_STARTED -> "already started"
      AdvertiseCallback.ADVERTISE_FAILED_INTERNAL_ERROR -> "internal error"
      AdvertiseCallback.ADVERTISE_FAILED_FEATURE_UNSUPPORTED -> "unsupported"
      else -> "code $code"
    }

  private companion object {
    const val TAG = "BleAdvertiser"
    const val APPLE_COMPANY_ID = 0x004C
  }
}
