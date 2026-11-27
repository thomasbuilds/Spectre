package dev.thomasbuilds.spectre.bluetooth

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.Context
import android.os.Build
import android.os.PowerManager
import android.os.SystemClock
import android.util.Log
import androidx.core.util.forEach
import dev.thomasbuilds.spectre.bluetooth.gatt.shortUuid
import dev.thomasbuilds.spectre.core.OuiLookup
import dev.thomasbuilds.spectre.core.Source
import dev.thomasbuilds.spectre.core.SourceState
import dev.thomasbuilds.spectre.core.SourceStatus
import dev.thomasbuilds.spectre.core.callbackExecutor
import dev.thomasbuilds.spectre.core.hasPermission
import dev.thomasbuilds.spectre.core.hasPermissions
import dev.thomasbuilds.spectre.core.isLocationEnabled
import dev.thomasbuilds.spectre.core.smoothRssi
import dev.thomasbuilds.spectre.core.statusFlow
import dev.thomasbuilds.spectre.core.whileOk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.onCompletion
import kotlinx.coroutines.launch
import kotlin.math.pow

private const val TAG = "BluetoothSource"
private const val WARMUP_MS = 6_000L
private const val PUBLISH_INTERVAL_MS = 5_000L
private const val NEW_DEVICE_PUBLISH_SPACING_MS = 250L
private const val WATCHDOG_MS = 3_000L
private const val MIN_SCAN_AGE_BEFORE_RENEWAL_MS = 6_000L
private const val STALL_MS = 6_000L
private const val QUIET_MS = 30_000L
private const val RENEW_BEFORE_OPPORTUNISTIC_DOWNGRADE_MS = 9 * 60_000L
private const val STALE_AFTER_MS = 30_000L
private const val MAX_TRACKED_DEVICES = 10_000
private const val MIN_SAMPLES_FOR_DISTANCE = 3
private const val DEFAULT_RSSI_AT_ONE_METER_DBM = -59
private const val TX_POWER_LOSS_AT_ONE_METER_DB = 41
private const val PATH_LOSS_EXPONENT = 2.5
private const val MIN_DISTANCE_M = 0.1
private const val MAX_DISTANCE_M = 200.0
private val PLAUSIBLE_RSSI_DBM = -127..20
private val SCAN_PERMISSIONS = Source.BLUETOOTH.permissions - Manifest.permission.BLUETOOTH_CONNECT

class BluetoothSource(
  private val context: Context,
  private val oui: OuiLookup
) {
  private val adapter: BluetoothAdapter? = context.getSystemService(BluetoothManager::class.java)?.adapter
  private val power = checkNotNull(context.getSystemService(PowerManager::class.java))
  private val companies = CompanyIdLookup(context)
  private val lane = Dispatchers.IO.limitedParallelism(1)
  private val devices = LinkedHashMap<String, Device>()
  private val hasConnectPermission: Boolean get() = context.hasPermission(Manifest.permission.BLUETOOTH_CONNECT)

  fun states(): Flow<SourceState<BluetoothSignal>> =
    context
      .statusFlow(BluetoothAdapter.ACTION_STATE_CHANGED) { status() }
      .whileOk(WARMUP_MS, hasData = { signals -> signals.any { !it.isStale } }) { scan(checkNotNull(adapter)) }
      .onCompletion { devices.clear() }
      .flowOn(lane)

  private fun status(): SourceStatus =
    when {
      !context.isLocationEnabled -> SourceStatus.LOCATION_OFF
      !context.hasPermissions(SCAN_PERMISSIONS) -> SourceStatus.NO_PERMISSION
      adapter == null -> SourceStatus.UNSUPPORTED
      !adapter.isEnabled -> SourceStatus.RADIO_OFF
      else -> SourceStatus.OK
    }

  private class Device(
    val result: ScanResult,
    val name: String?,
    val smoothedRssi: Double,
    val samples: Int,
    val firstSeenMs: Long,
    val lastSeenMs: Long,
    val seenConnectable: Boolean,
    val seenNonConnectable: Boolean
  ) {
    var built: BluetoothSignal? = null
  }

  @SuppressLint("MissingPermission")
  private fun scan(adapter: BluetoothAdapter): Flow<SourceState<BluetoothSignal>> =
    channelFlow {
      val scanner = checkNotNull(adapter.bluetoothLeScanner)
      val settings = scanSettings(adapter)
      val executor = callbackExecutor()
      val sessionStartedAtMs = SystemClock.elapsedRealtime()
      val newDevices = Channel<Unit>(Channel.CONFLATED)
      var startedAtMs = 0L
      var lastResultAtMs: Long? = null
      var failed = false
      val callback =
        object : ScanCallback() {
          override fun onScanResult(
            callbackType: Int,
            result: ScanResult
          ) {
            executor.execute {
              lastResultAtMs = SystemClock.elapsedRealtime()
              if (ingest(result, sessionStartedAtMs)) newDevices.trySend(Unit)
            }
          }

          override fun onScanFailed(errorCode: Int) {
            Log.w(TAG, "Scan failed with error $errorCode")
            executor.execute { failed = true }
          }
        }

      fun start() {
        scanner.stopScan(callback)
        scanner.startScan(null, settings, callback)
        startedAtMs = SystemClock.elapsedRealtime()
        lastResultAtMs = null
        failed = false
      }

      fun publish() {
        val freshSinceMs = maxOf(SystemClock.elapsedRealtime() - STALE_AFTER_MS, sessionStartedAtMs)
        val bonded = adapter.bondedAddresses()
        trySend(SourceState(signals = devices.values.map { it.signal(bonded, freshSinceMs) }))
      }

      start()
      launch {
        while (true) {
          publish()
          delay(PUBLISH_INTERVAL_MS)
        }
      }
      launch {
        while (true) {
          newDevices.receive()
          publish()
          delay(NEW_DEVICE_PUBLISH_SPACING_MS)
        }
      }
      launch {
        while (true) {
          delay(WATCHDOG_MS)
          val now = SystemClock.elapsedRealtime()
          val age = now - startedAtMs
          val silence = lastResultAtMs?.let { now - it >= STALL_MS } ?: (age >= QUIET_MS)
          val renew = failed || (power.isInteractive && (silence || age >= RENEW_BEFORE_OPPORTUNISTIC_DOWNGRADE_MS))
          if (age >= MIN_SCAN_AGE_BEFORE_RENEWAL_MS && renew) start()
        }
      }
      awaitClose {
        try {
          scanner.stopScan(callback)
        } catch (_: IllegalStateException) {
        }
      }
    }

  private fun scanSettings(adapter: BluetoothAdapter): ScanSettings =
    ScanSettings
      .Builder()
      .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
      .apply {
        if (adapter.isLeExtendedAdvertisingSupported) {
          setLegacy(false)
          setPhy(ScanSettings.PHY_LE_ALL_SUPPORTED)
        }
      }.build()

  private fun ingest(
    result: ScanResult,
    sessionStartedAtMs: Long
  ): Boolean {
    if (result.rssi !in PLAUSIBLE_RSSI_DBM) return false
    val now = SystemClock.elapsedRealtime()
    val previous = devices.remove(result.device.address)
    val advertisedName =
      result.scanRecord
        ?.deviceName
        ?.filterNot(Char::isISOControl)
        ?.takeIf { it.isNotBlank() }
    devices[result.device.address] =
      Device(
        result = result,
        name = advertisedName ?: if (previous != null) previous.name else result.device.cachedName(),
        smoothedRssi = smoothRssi(previous?.smoothedRssi, result.rssi),
        samples = (previous?.samples ?: 0) + 1,
        firstSeenMs = previous?.firstSeenMs ?: now,
        lastSeenMs = now,
        seenConnectable = previous?.seenConnectable == true || result.isConnectable,
        seenNonConnectable = previous?.seenNonConnectable == true || !result.isConnectable
      )
    while (devices.size > MAX_TRACKED_DEVICES) devices.remove(devices.keys.first())
    return previous == null || previous.lastSeenMs < sessionStartedAtMs
  }

  @SuppressLint("MissingPermission")
  private fun BluetoothDevice.cachedName(): String? = if (hasConnectPermission) name?.takeIf { it.isNotBlank() } else null

  @SuppressLint("MissingPermission")
  private fun BluetoothAdapter.bondedAddresses(): Set<String> =
    if (hasConnectPermission) bondedDevices.orEmpty().mapTo(HashSet()) { it.address } else emptySet()

  private fun Device.signal(
    bonded: Set<String>,
    freshSinceMs: Long
  ): BluetoothSignal {
    val isBonded = result.device.address in bonded
    val isStale = lastSeenMs < freshSinceMs
    val signal = built?.takeIf { it.isBonded == isBonded && it.isStale == isStale } ?: toSignal(isBonded, isStale)
    built = signal
    return signal
  }

  private fun Device.toSignal(
    isBonded: Boolean,
    isStale: Boolean
  ): BluetoothSignal {
    val record = result.scanRecord
    val appleData = record?.getManufacturerSpecificData(APPLE_COMPANY_ID)
    val ibeaconPower = appleData?.let(::ibeaconMeasuredPower)
    val txPower = record?.txPowerLevel?.takeIf { it != Int.MIN_VALUE }
    val addressType = result.device.bleAddressType()
    return BluetoothSignal(
      device = result.device,
      name = name,
      rssi = result.rssi,
      distanceMeters =
        if (samples < MIN_SAMPLES_FOR_DISTANCE) {
          null
        } else {
          bleDistanceMeters(smoothedRssi, ibeaconPower ?: txPower?.minus(TX_POWER_LOSS_AT_ONE_METER_DB) ?: DEFAULT_RSSI_AT_ONE_METER_DBM)
        },
      vendor = if (addressType == null || addressType == BleAddressType.PUBLIC) oui.vendor(result.device.address) else null,
      addressType = addressType,
      isConnectable = result.isConnectable,
      alternatesConnectable = seenConnectable && seenNonConnectable,
      isBonded = isBonded,
      txPowerDbm = txPower,
      ibeaconPowerDbm = ibeaconPower,
      primaryPhy = result.primaryPhy.toBlePhy(),
      secondaryPhy = result.secondaryPhy.toBlePhy(),
      serviceUuids = record?.serviceUuids.orEmpty().map { shortUuid(it.uuid.toString()) },
      serviceDataCount = record?.serviceData?.size ?: 0,
      manufacturers = buildList { record?.manufacturerSpecificData?.forEach { id, _ -> add(Manufacturer(id, companies.name(id))) } },
      appleTypes = appleData?.let(::appleTypes).orEmpty(),
      googleType = record?.let(::googleType),
      microsoftType = record?.let(::microsoftType),
      advertiseFlags = record?.advertiseFlags?.takeIf { it >= 0 },
      advertisementHex = record?.let { advertisementHex(it, companies) },
      firstSeenMs = firstSeenMs,
      isStale = isStale
    )
  }
}

private fun BluetoothDevice.bleAddressType(): BleAddressType? =
  if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.VANILLA_ICE_CREAM) {
    when (addressType) {
      BluetoothDevice.ADDRESS_TYPE_PUBLIC -> BleAddressType.PUBLIC
      BluetoothDevice.ADDRESS_TYPE_RANDOM -> BleAddressType.RANDOM
      BluetoothDevice.ADDRESS_TYPE_ANONYMOUS -> BleAddressType.ANONYMOUS
      else -> null
    }
  } else {
    null
  }

private fun Int.toBlePhy(): BlePhy? = if (this == ScanResult.PHY_UNUSED) null else BlePhy(this)

private fun bleDistanceMeters(
  rssi: Double,
  rssiAtOneMeterDbm: Int
): Double = 10.0.pow((rssiAtOneMeterDbm - rssi) / (10 * PATH_LOSS_EXPONENT)).coerceIn(MIN_DISTANCE_M, MAX_DISTANCE_M)
