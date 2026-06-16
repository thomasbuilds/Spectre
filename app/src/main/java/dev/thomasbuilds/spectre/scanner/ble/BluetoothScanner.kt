package dev.thomasbuilds.spectre.scanner.ble

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.Context
import android.location.LocationManager
import android.os.Build
import android.util.Log
import androidx.core.util.isNotEmpty
import androidx.core.util.size
import dev.thomasbuilds.spectre.analysis.Distance
import dev.thomasbuilds.spectre.hasPermission
import dev.thomasbuilds.spectre.model.BluetoothSignal
import dev.thomasbuilds.spectre.model.BluetoothSourceState
import dev.thomasbuilds.spectre.model.DetailEntry
import dev.thomasbuilds.spectre.model.DistanceConfidence
import dev.thomasbuilds.spectre.model.ScannerStatus
import dev.thomasbuilds.spectre.scanner.OuiLookup
import dev.thomasbuilds.spectre.scanner.ReadinessTracker
import dev.thomasbuilds.spectre.scanner.daemonExecutor
import dev.thomasbuilds.spectre.scanner.repeatEvery
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.launch
import java.util.concurrent.ConcurrentHashMap

class BluetoothScanner(
  private val context: Context,
  private val oui: OuiLookup
) {
  private val manager: BluetoothManager? =
    context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
  private val locationManager: LocationManager? =
    context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager

  private val adapter: BluetoothAdapter? get() = manager?.adapter

  private val _state = MutableStateFlow(BluetoothSourceState())
  val state: StateFlow<BluetoothSourceState> = _state.asStateFlow()

  fun remoteDevice(mac: String): BluetoothDevice? = deviceCache[mac]?.latestResult?.device

  private data class DeviceState(
    val mac: String,
    val name: String,
    val latestResult: ScanResult,
    val smoothedRssi: Double,
    val sampleCount: Int,
    val firstSeenMs: Long,
    val lastSeenMs: Long,
    val seenConnectable: Boolean,
    val seenNonConnectable: Boolean,
    val nameIpcAttempted: Boolean
  )

  private data class SignalCacheKey(
    val name: String,
    val smoothedRssiInt: Int,
    val seenConnectable: Boolean,
    val seenNonConnectable: Boolean,
    val isConnectable: Boolean,
    val isBonded: Boolean,
    val sampleCount: Int,
    val isStale: Boolean
  )

  private data class CachedSignal(
    val key: SignalCacheKey,
    val signal: BluetoothSignal
  )

  private val deviceCache = ConcurrentHashMap<String, DeviceState>()
  private val signalCache = ConcurrentHashMap<String, CachedSignal>()

  @Volatile private var scanning = false

  @Volatile private var lastResultMs: Long = 0L

  @Volatile private var scanStartedMs: Long = 0L

  private val publishTrigger =
    MutableSharedFlow<Unit>(
      extraBufferCapacity = 1,
      onBufferOverflow = BufferOverflow.DROP_OLDEST
    )
  private var publishJob: Job? = null
  private var heartbeatJob: Job? = null
  private var watchdogJob: Job? = null

  private val readiness = ReadinessTracker(BT_WARMUP_MS, BT_STALENESS_MS)

  private val ingestExecutor = daemonExecutor("spectre-bt")

  private val callback =
    object : ScanCallback() {
      override fun onScanResult(
        callbackType: Int,
        result: ScanResult
      ) {
        lastResultMs = System.currentTimeMillis()
        ingestExecutor.execute { handle(result) }
      }

      override fun onBatchScanResults(results: MutableList<ScanResult>) {
        lastResultMs = System.currentTimeMillis()
        ingestExecutor.execute { results.forEach(::handle) }
      }

      override fun onScanFailed(errorCode: Int) {
        Log.w(TAG, "scan failed errorCode=$errorCode; will retry on next tick")
        markScanFailed()
      }
    }

  private fun handle(result: ScanResult) {
    val mac = result.device?.address ?: return
    val rssi = result.rssi
    if (rssi > 20 || rssi < -127) return
    val previous = deviceCache[mac]
    val sampleCount = (previous?.sampleCount ?: 0) + 1
    val smoothedRssi =
      if (previous == null) {
        rssi.toDouble()
      } else {
        EMA_ALPHA * rssi + (1 - EMA_ALPHA) * previous.smoothedRssi
      }
    val advName = result.scanRecord?.deviceName?.takeIf { it.isNotBlank() }
    val name: String
    val nameIpcAttempted: Boolean
    when {
      advName != null -> {
        name = advName
        nameIpcAttempted = previous?.nameIpcAttempted ?: false
      }

      previous != null && previous.name != UNKNOWN_NAME -> {
        name = previous.name
        nameIpcAttempted = previous.nameIpcAttempted
      }

      previous?.nameIpcAttempted == true -> {
        name = previous.name
        nameIpcAttempted = true
      }

      else -> {
        name = resolveNameViaIpc(result.device)
        nameIpcAttempted = true
      }
    }
    val now = System.currentTimeMillis()
    val isConnectable = result.isConnectable
    deviceCache[mac] =
      DeviceState(
        mac = mac,
        name = name,
        latestResult = result,
        smoothedRssi = smoothedRssi,
        sampleCount = sampleCount,
        firstSeenMs = previous?.firstSeenMs ?: now,
        lastSeenMs = now,
        seenConnectable = (previous?.seenConnectable ?: false) || isConnectable,
        seenNonConnectable = (previous?.seenNonConnectable ?: false) || !isConnectable,
        nameIpcAttempted = nameIpcAttempted
      )
    if (previous == null) publishTrigger.tryEmit(Unit)
  }

  private fun resolveNameViaIpc(device: BluetoothDevice): String {
    val deviceName =
      runCatching {
        if (context.hasPermission(Manifest.permission.BLUETOOTH_CONNECT)) {
          @SuppressLint("MissingPermission")
          device.name
        } else {
          null
        }
      }.getOrNull()
    return deviceName?.takeIf { it.isNotBlank() } ?: UNKNOWN_NAME
  }

  private fun buildSignal(
    state: DeviceState,
    isBonded: Boolean,
    isStale: Boolean
  ): BluetoothSignal {
    val result = state.latestResult
    val mac = state.mac
    val name = state.name
    val rssi = result.rssi
    val smoothedRssi = state.smoothedRssi
    val sampleCount = state.sampleCount

    val advertisedTxPower = result.scanRecord?.txPowerLevel?.takeIf { it != Int.MIN_VALUE }
    val ibeaconMeasuredPower =
      result.scanRecord
        ?.manufacturerSpecificData
        ?.get(0x004C)
        ?.let(::parseIbeaconMeasuredPower)

    val (distance, confidence) =
      when {
        sampleCount < MIN_SAMPLES_FOR_DISTANCE -> {
          null to DistanceConfidence.PENDING
        }

        ibeaconMeasuredPower != null -> {
          Distance.fromBleRssi(
            smoothedRssi.toInt(),
            rssiAtOneMeterDbm = ibeaconMeasuredPower
          ) to DistanceConfidence.CALIBRATED
        }

        else -> {
          Distance.fromBleRssi(smoothedRssi.toInt()) to DistanceConfidence.APPROXIMATE
        }
      }

    val details =
      buildList {
        add(DetailEntry("Name", name))
        add(DetailEntry("MAC", mac))
        if (!isRandomAddress(result.device)) {
          oui.vendor(mac)?.let { add(DetailEntry("Vendor", it)) }
        }
        if (advertisedTxPower != null) {
          add(DetailEntry("Adv TX power", "$advertisedTxPower dBm"))
        }
        if (ibeaconMeasuredPower != null) {
          add(DetailEntry("iBeacon RSSI@1m", "$ibeaconMeasuredPower dBm"))
        }
        val alternates = state.seenConnectable && state.seenNonConnectable
        val connectableLabel =
          if (alternates) "${result.isConnectable} (alternates)" else result.isConnectable.toString()
        add(DetailEntry("Connectable", connectableLabel))
        add(DetailEntry("Address type", addressTypeLabel(result.device)))
        result.primaryPhy.takeIf { it != 0 }?.let { add(DetailEntry("Primary PHY", phyLabel(it))) }
        result.secondaryPhy.takeIf { it != 0 }?.let { add(DetailEntry("Secondary PHY", phyLabel(it))) }
        result.scanRecord?.serviceUuids?.takeIf { it.isNotEmpty() }?.let { uuids ->
          add(DetailEntry("Service UUIDs", uuids.joinToString { shortenUuid(it.uuid.toString()) }))
        }
        result.scanRecord?.serviceData?.takeIf { it.isNotEmpty() }?.let { sd ->
          add(DetailEntry("Service data", "${sd.size} entries"))
        }
        result.scanRecord?.manufacturerSpecificData?.let { msd ->
          if (msd.isNotEmpty()) {
            val companies =
              (0..<msd.size).joinToString { idx ->
                val cid = msd.keyAt(idx)
                companyName(cid) ?: "0x%04X".format(cid)
              }
            add(DetailEntry("Manufacturer", companies))

            val appleData = msd.get(0x004C)
            if (appleData != null && appleData.isNotEmpty()) {
              appleAdvLabel(appleData)?.let {
                add(DetailEntry("Apple type", it))
              }
            }
            val googleData = msd.get(0x00E0)
            if (googleData != null && googleData.isNotEmpty()) {
              add(DetailEntry("Google type", "Find My Device / Fast Pair"))
            }
            val microsoftData = msd.get(0x0006)
            if (microsoftData != null && microsoftData.size > 1) {
              add(DetailEntry("Microsoft type", msAdvLabel(microsoftData)))
            }
          }
        }
        result.scanRecord
          ?.advertiseFlags
          ?.takeIf { it >= 0 }
          ?.let { add(DetailEntry("Adv flags", "0x%02X".format(it))) }
      }

    return BluetoothSignal(
      name = name,
      mac = mac,
      rssi = rssi,
      distanceMeters = distance,
      distanceConfidence = confidence,
      isBonded = isBonded,
      details = details,
      advertisementHex = result.scanRecord?.let { advertisementHexBlock(it) },
      firstSeenMs = state.firstSeenMs,
      isConnectable = result.isConnectable,
      isStale = isStale
    )
  }

  fun hasPermission(): Boolean = context.hasPermission(Manifest.permission.BLUETOOTH_SCAN)

  fun status(): ScannerStatus {
    if (!hasPermission()) return ScannerStatus.NO_PERMISSION
    if (locationManager?.isLocationEnabled != true) return ScannerStatus.LOCATION_OFF
    if (adapter?.isEnabled != true) return ScannerStatus.RADIO_OFF
    return ScannerStatus.OK
  }

  @OptIn(FlowPreview::class, ExperimentalCoroutinesApi::class)
  fun start(scope: CoroutineScope) {
    startScan()
    if (publishJob?.isActive != true) {
      publishJob =
        scope.launch(Dispatchers.Default.limitedParallelism(1)) {
          publishTrigger.debounce(BT_PUBLISH_DEBOUNCE_MS).collect { publishNow() }
        }
    }
    if (heartbeatJob?.isActive != true) {
      heartbeatJob =
        scope.repeatEvery(BT_HEARTBEAT_MS, Dispatchers.Default.limitedParallelism(1)) { publishNow() }
    }
    if (watchdogJob?.isActive != true) {
      watchdogJob =
        scope.repeatEvery(BT_WATCHDOG_MS, Dispatchers.Default.limitedParallelism(1)) {
          if (status() == ScannerStatus.OK) startScan()
        }
    }
    publishNow()
  }

  // The publish, heartbeat, and watchdog jobs each run on their own single-thread dispatcher,
  // so without synchronization the check-then-set on `scanning` could race and start the
  // scanner twice with the same callback, which fails with ALREADY_STARTED.
  @Synchronized
  @SuppressLint("MissingPermission")
  private fun startScan() {
    if (scanning) {
      maybeRestartIfStalled()
      return
    }
    if (!hasPermission()) return
    val a = adapter ?: return
    if (!a.isEnabled) return
    val scanner = a.bluetoothLeScanner ?: return
    val settings =
      ScanSettings
        .Builder()
        .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
        .setCallbackType(ScanSettings.CALLBACK_TYPE_ALL_MATCHES)
        .build()
    try {
      scanner.startScan(null, settings, callback)
      scanning = true
      scanStartedMs = System.currentTimeMillis()
      lastResultMs = scanStartedMs
    } catch (e: SecurityException) {
      Log.w(TAG, "startScan denied: ${e.message}")
    } catch (e: Throwable) {
      Log.w(TAG, "startScan threw: ${e.message}")
    }
  }

  @Synchronized
  @SuppressLint("MissingPermission")
  fun stop() {
    if (!scanning) return
    try {
      adapter?.bluetoothLeScanner?.stopScan(callback)
    } catch (e: Throwable) {
      Log.w(TAG, "stopScan threw: ${e.message}")
    }
    scanning = false
  }

  @Synchronized
  private fun markScanFailed() {
    scanning = false
  }

  @Synchronized
  @SuppressLint("MissingPermission")
  private fun maybeRestartIfStalled() {
    if (!scanning) return
    val now = System.currentTimeMillis()
    val sinceStart = now - scanStartedMs
    if (sinceStart < STALL_GRACE_MS) return
    if (lastResultMs <= scanStartedMs) return
    val sinceResult = now - lastResultMs
    if (sinceResult < STALL_THRESHOLD_MS) return
    Log.w(TAG, "scan stalled: ${sinceResult / 1000}s since last result, restarting")
    try {
      adapter?.bluetoothLeScanner?.stopScan(callback)
    } catch (_: Throwable) {
    }
    scanning = false
    startScan()
  }

  private fun enforceSizeCap() {
    val overflow = deviceCache.size - MAX_TRACKED_DEVICES
    if (overflow <= 0) return
    deviceCache.entries
      .sortedBy { it.value.lastSeenMs }
      .take(overflow)
      .forEach {
        deviceCache.remove(it.key)
        signalCache.remove(it.key)
      }
  }

  private fun publishNow() {
    val status = status()
    if (status == ScannerStatus.OK) {
      startScan()
    }
    enforceSizeCap()
    val now = System.currentTimeMillis()
    val bonded = bondedMacs()
    val signals =
      if (status != ScannerStatus.OK) {
        emptyList()
      } else {
        deviceCache.values.sortedBy { it.mac }.map { dev ->
          val isStale = now - dev.lastSeenMs > STALE_TTL_MS
          val key =
            SignalCacheKey(
              name = dev.name,
              smoothedRssiInt = dev.smoothedRssi.toInt(),
              seenConnectable = dev.seenConnectable,
              seenNonConnectable = dev.seenNonConnectable,
              isConnectable = dev.latestResult.isConnectable,
              isBonded = dev.mac in bonded,
              sampleCount = dev.sampleCount.coerceAtMost(MIN_SAMPLES_FOR_DISTANCE),
              isStale = isStale
            )
          val cached = signalCache[dev.mac]
          if (cached != null && cached.key == key) {
            cached.signal
          } else {
            val s = buildSignal(dev, isBonded = dev.mac in bonded, isStale = isStale)
            signalCache[dev.mac] = CachedSignal(key, s)
            s
          }
        }
      }

    val hasData = signals.any { !it.isStale }
    val ready = readiness.compute(status == ScannerStatus.OK, hasData, now)
    _state.value =
      BluetoothSourceState(
        signals = signals,
        status = status,
        ready = ready
      )
  }

  @SuppressLint("MissingPermission")
  private fun bondedMacs(): Set<String> {
    if (!hasPermission()) return emptySet()
    val bonded = runCatching { adapter?.bondedDevices }.getOrNull().orEmpty()
    return bonded.mapNotNull { it.address }.toSet()
  }

  @SuppressLint("MissingPermission")
  private fun addressTypeLabel(device: BluetoothDevice?): String {
    if (device == null) return "—"
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.VANILLA_ICE_CREAM) {
      return "Unknown (API 34)"
    }
    return when (device.addressType) {
      BluetoothDevice.ADDRESS_TYPE_PUBLIC -> "Public"
      BluetoothDevice.ADDRESS_TYPE_RANDOM -> "Random"
      BluetoothDevice.ADDRESS_TYPE_ANONYMOUS -> "Anonymous"
      else -> "Unknown"
    }
  }

  // A random BLE address has no real OUI, so skip those we can detect (API 35+) to avoid a coincidental mislabel.
  @SuppressLint("MissingPermission")
  private fun isRandomAddress(device: BluetoothDevice?): Boolean {
    if (device == null || Build.VERSION.SDK_INT < Build.VERSION_CODES.VANILLA_ICE_CREAM) return false
    return runCatching { device.addressType == BluetoothDevice.ADDRESS_TYPE_RANDOM }.getOrDefault(false)
  }

  private fun phyLabel(phy: Int): String =
    when (phy) {
      BluetoothDevice.PHY_LE_1M -> "LE 1M"
      BluetoothDevice.PHY_LE_2M -> "LE 2M"
      BluetoothDevice.PHY_LE_CODED -> "LE Coded"
      else -> "Unknown ($phy)"
    }

  private companion object {
    const val TAG = "BluetoothScanner"
    const val UNKNOWN_NAME = "Unknown"
    const val EMA_ALPHA = 0.3
    const val MIN_SAMPLES_FOR_DISTANCE = 3

    const val STALE_TTL_MS = 30_000L
    const val STALL_GRACE_MS = 6_000L
    const val STALL_THRESHOLD_MS = 6_000L
    const val BT_WATCHDOG_MS = 3_000L
    const val BT_PUBLISH_DEBOUNCE_MS = 250L
    const val BT_HEARTBEAT_MS = 5_000L
    const val BT_WARMUP_MS = 6_000L
    const val BT_STALENESS_MS = 20_000L

    const val MAX_TRACKED_DEVICES = 10_000
  }
}
