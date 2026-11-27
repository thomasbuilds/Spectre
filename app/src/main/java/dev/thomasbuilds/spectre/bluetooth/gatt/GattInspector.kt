package dev.thomasbuilds.spectre.bluetooth.gatt

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattService
import android.bluetooth.BluetoothProfile
import android.content.Context
import android.os.Build
import android.os.Handler
import android.os.Looper
import dev.thomasbuilds.spectre.core.hasPermission
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.SendChannel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withTimeoutOrNull
import java.util.UUID

private const val CONNECT_TIMEOUT_MS = 12_000L
private const val RESPONSE_WATCHDOG_MS = 60_000L
private const val CONNECT_RETRY_DELAY_MS = 600L
private const val CONNECT_ATTEMPTS = 3
private const val LINK_LOSS_GRACE_MS = 1_000L
private val HID_SERVICE = uuid16(0x1812)
private val HID_RESTRICTED_CHARACTERISTICS = (0x2A4A..0x2A4D).mapTo(HashSet(), ::uuid16)
private val ALWAYS_RESTRICTED_SERVICES = setOf(uuid16(0xFFFD), UUID.fromString("ab5e0001-5a21-4f05-bc7d-af01f617b664"))
private val RESTRICTED_SERVICES_SINCE_TIRAMISU =
  listOf(0x1843, 0x1844, 0x1845, 0x1846, 0x184E, 0x184F, 0x1850, 0x1854).mapTo(HashSet(), ::uuid16) +
    UUID.fromString("109b862f-50e3-45cc-8ea1-ac62de4846d1") +
    UUID.fromString("7905f431-b5ce-4e99-a40f-4b1e122d00d0")

private sealed interface GattEvent {
  class Connection(
    val status: Int,
    val connected: Boolean
  ) : GattEvent

  class Discovered(
    val status: Int
  ) : GattEvent

  class Read(
    val characteristic: BluetoothGattCharacteristic,
    val value: ByteArray,
    val status: Int
  ) : GattEvent
}

class GattInspector(
  private val context: Context
) {
  private val handler = Handler(Looper.getMainLooper())

  fun inspect(device: BluetoothDevice): Flow<GattInspection> =
    channelFlow {
      val events = Channel<GattEvent>(Channel.UNLIMITED)
      val session = Session(device, EventCallback(events), events)
      try {
        send(session.execute { send(it) })
      } finally {
        session.close()
      }
    }.flowOn(Dispatchers.Main.immediate)

  private inner class Session(
    private val device: BluetoothDevice,
    private val callback: EventCallback,
    private val events: Channel<GattEvent>
  ) {
    private var gatt: BluetoothGatt? = null

    @SuppressLint("MissingPermission")
    suspend fun execute(progress: suspend (GattInspection) -> Unit): GattInspection {
      if (!context.hasPermission(Manifest.permission.BLUETOOTH_CONNECT)) {
        return GattInspection.Failed("Nearby devices permission is not granted")
      }
      progress(GattInspection.Connecting)
      val connection =
        withTimeoutOrNull(CONNECT_TIMEOUT_MS) { connect() }
          ?: return GattInspection.Failed(if (gatt == null) "Bluetooth is off" else "Connect timed out")
      val link = gatt
      if (!connection.connected || link == null) return GattInspection.Failed("Disconnected (status ${connection.status})")
      progress(GattInspection.Discovering)
      val started = link.discoverServices()
      val discovery =
        withTimeoutOrNull(if (started) RESPONSE_WATCHDOG_MS else LINK_LOSS_GRACE_MS) {
          events.next<GattEvent> {
            it is GattEvent.Discovered || it.isLinkLoss
          }
        }
      return when {
        discovery == null -> {
          GattInspection.Failed(if (started) "No answer from the device" else "Service discovery could not start")
        }

        discovery is GattEvent.Connection -> {
          GattInspection.Failed("Disconnected (status ${discovery.status})")
        }

        discovery is GattEvent.Discovered && discovery.status != BluetoothGatt.GATT_SUCCESS -> {
          GattInspection.Failed("Service discovery failed (status ${discovery.status})")
        }

        else -> {
          link.readAll { done, total -> progress(GattInspection.Reading(done, total)) }
        }
      }
    }

    @SuppressLint("MissingPermission")
    @Suppress("DEPRECATION")
    private suspend fun connect(): GattEvent.Connection? {
      var attempt = 1
      while (true) {
        gatt?.close()
        gatt = device.connectGatt(context, false, callback, BluetoothDevice.TRANSPORT_LE, BluetoothDevice.PHY_LE_1M_MASK, handler)
        callback.current = gatt ?: return null
        val outcome = events.next<GattEvent.Connection>()
        if (outcome.connected || outcome.status == BluetoothGatt.GATT_SUCCESS || attempt == CONNECT_ATTEMPTS) return outcome
        attempt++
        callback.current = null
        delay(CONNECT_RETRY_DELAY_MS)
      }
    }

    private suspend fun BluetoothGatt.readAll(onProgress: suspend (done: Int, total: Int) -> Unit): GattInspection.Done {
      val readable = services.flatMap { service -> service.characteristics.filter { it.isReadable && !it.isRestricted(service) } }
      val readings = HashMap<BluetoothGattCharacteristic, GattReading>()
      for ((index, characteristic) in readable.withIndex()) {
        onProgress(index, readable.size)
        val started =
          try {
            readCharacteristic(characteristic)
          } catch (_: SecurityException) {
            false
          }
        val event =
          withTimeoutOrNull(if (started) RESPONSE_WATCHDOG_MS else LINK_LOSS_GRACE_MS) {
            events.next<GattEvent> {
              (it is GattEvent.Read && it.characteristic === characteristic) || it.isLinkLoss
            }
          }
        when (event) {
          is GattEvent.Read -> readings[characteristic] = event.toReading()
          is GattEvent.Connection -> return done(readings, "Connection lost (status ${event.status})")
          null if !started -> return done(readings, "Android refused to read ${shortUuid(characteristic.uuid.toString())}")
          else -> return done(readings, "No answer from the device")
        }
      }
      return done(readings, null)
    }

    private fun BluetoothGatt.done(
      readings: Map<BluetoothGattCharacteristic, GattReading>,
      note: String?
    ): GattInspection.Done = GattInspection.Done(services.map { it.toService(readings) }, note)

    @SuppressLint("MissingPermission")
    fun close() {
      callback.current = null
      gatt?.run {
        disconnect()
        close()
      }
      events.close()
    }
  }

  private class EventCallback(
    private val events: SendChannel<GattEvent>
  ) : BluetoothGattCallback() {
    var current: BluetoothGatt? = null

    @SuppressLint("MissingPermission")
    override fun onConnectionStateChange(
      gatt: BluetoothGatt,
      status: Int,
      newState: Int
    ) {
      if (gatt === current) events.trySend(GattEvent.Connection(status, newState == BluetoothProfile.STATE_CONNECTED)) else gatt.close()
    }

    override fun onServicesDiscovered(
      gatt: BluetoothGatt,
      status: Int
    ) {
      if (gatt === current) events.trySend(GattEvent.Discovered(status))
    }

    override fun onCharacteristicRead(
      gatt: BluetoothGatt,
      characteristic: BluetoothGattCharacteristic,
      value: ByteArray,
      status: Int
    ) {
      if (gatt === current) events.trySend(GattEvent.Read(characteristic, value, status))
    }

    @Suppress("DEPRECATION", "OVERRIDE_DEPRECATION")
    override fun onCharacteristicRead(
      gatt: BluetoothGatt,
      characteristic: BluetoothGattCharacteristic,
      status: Int
    ) = onCharacteristicRead(gatt, characteristic, characteristic.value ?: ByteArray(0), status)
  }
}

private suspend inline fun <reified E : GattEvent> Channel<GattEvent>.next(accept: (E) -> Boolean = { true }): E {
  while (true) {
    val event = receive()
    if (event is E && accept(event)) return event
  }
}

private val GattEvent.isLinkLoss: Boolean get() = this is GattEvent.Connection && !connected

private val BluetoothGattCharacteristic.isReadable: Boolean get() = properties and BluetoothGattCharacteristic.PROPERTY_READ != 0

private fun BluetoothGattCharacteristic.isRestricted(service: BluetoothGattService): Boolean =
  service.uuid in ALWAYS_RESTRICTED_SERVICES ||
    (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && service.uuid in RESTRICTED_SERVICES_SINCE_TIRAMISU) ||
    (service.uuid == HID_SERVICE && uuid in HID_RESTRICTED_CHARACTERISTICS)

private fun GattEvent.Read.toReading(): GattReading =
  if (status == BluetoothGatt.GATT_SUCCESS) {
    GattReading.Value(decodeGattValue(characteristic.uuid.toString(), value), value.toSpacedHex())
  } else {
    GattReading.Failed(status)
  }

private fun BluetoothGattService.toService(readings: Map<BluetoothGattCharacteristic, GattReading>): GattService =
  GattService(
    uuid = uuid.toString(),
    name = gattServiceName(uuid.toString()),
    characteristics =
      characteristics.map { characteristic ->
        GattCharacteristic(
          uuid = characteristic.uuid.toString(),
          name = gattCharacteristicName(characteristic.uuid.toString()),
          properties = GattProperty.entries.filterTo(LinkedHashSet()) { characteristic.properties and it.mask != 0 },
          reading = if (characteristic.isRestricted(this)) GattReading.Restricted else readings[characteristic]
        )
      }
  )
