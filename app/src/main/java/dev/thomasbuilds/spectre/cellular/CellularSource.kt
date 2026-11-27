package dev.thomasbuilds.spectre.cellular

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.SystemClock
import android.telephony.CellInfo
import android.telephony.CellSignalStrengthLte
import android.telephony.ServiceState
import android.telephony.SignalStrength
import android.telephony.SubscriptionInfo
import android.telephony.SubscriptionManager
import android.telephony.TelephonyCallback
import android.telephony.TelephonyDisplayInfo
import android.telephony.TelephonyManager
import android.util.Log
import dev.thomasbuilds.spectre.core.Source
import dev.thomasbuilds.spectre.core.SourceState
import dev.thomasbuilds.spectre.core.SourceStatus
import dev.thomasbuilds.spectre.core.callbackExecutor
import dev.thomasbuilds.spectre.core.hasPermissions
import dev.thomasbuilds.spectre.core.isLocationEnabled
import dev.thomasbuilds.spectre.core.statusFlow
import dev.thomasbuilds.spectre.core.whileOk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.launch
import java.util.concurrent.Executor

private const val TAG = "CellularSource"
private const val WARMUP_MS = 8_000L
private const val HEARTBEAT_MS = 5_000L
private const val EXPIRE_AFTER_MS = 60_000L

class CellularSource(
  private val context: Context
) {
  private val telephony: TelephonyManager? = context.getSystemService(TelephonyManager::class.java)
  private val subscriptions: SubscriptionManager? = context.getSystemService(SubscriptionManager::class.java)
  private val lane = Dispatchers.IO.limitedParallelism(1)
  private val hasTelephony = context.packageManager.hasSystemFeature(PackageManager.FEATURE_TELEPHONY)

  fun states(): Flow<SourceState<CellSignal>> =
    context
      .statusFlow(Intent.ACTION_AIRPLANE_MODE_CHANGED) { status() }
      .whileOk(WARMUP_MS) { cells(checkNotNull(telephony), checkNotNull(subscriptions)) }
      .flowOn(lane)

  @SuppressLint("MissingPermission")
  private fun activeSubscriptions(): List<SubscriptionInfo> = subscriptions?.activeSubscriptionInfoList.orEmpty()

  private fun status(): SourceStatus =
    when {
      !context.isLocationEnabled -> SourceStatus.LOCATION_OFF
      !context.hasPermissions(Source.CELLULAR.permissions) -> SourceStatus.NO_PERMISSION
      telephony == null || subscriptions == null || !hasTelephony -> SourceStatus.UNSUPPORTED
      else -> subscriptionStatus(telephony)
    }

  @SuppressLint("MissingPermission")
  private fun subscriptionStatus(telephony: TelephonyManager): SourceStatus =
    try {
      val active = activeSubscriptions()
      when {
        active.isEmpty() -> SourceStatus.NO_SIM

        active.all {
          telephony.createForSubscriptionId(it.subscriptionId).serviceState?.state == ServiceState.STATE_POWER_OFF
        } -> SourceStatus.RADIO_OFF

        else -> SourceStatus.OK
      }
    } catch (_: UnsupportedOperationException) {
      SourceStatus.UNSUPPORTED
    }

  private class SeenCell(
    val subId: Int,
    val signal: CellSignal,
    val seenAtMs: Long
  )

  private fun cells(
    telephony: TelephonyManager,
    subscriptions: SubscriptionManager
  ): Flow<SourceState<CellSignal>> =
    channelFlow {
      val executor = callbackExecutor()
      val monitors = HashMap<Int, SubscriptionMonitor>()
      val cells = HashMap<String, SeenCell>()

      fun label(): String? {
        val dataSubId = SubscriptionManager.getDefaultDataSubscriptionId()
        val displayInfo = (monitors[SubscriptionManager.getActiveDataSubscriptionId()] ?: monitors[dataSubId])?.displayInfo ?: return null
        return modemLabel(displayInfo, dataEnabled = monitors[dataSubId]?.dataEnabled != false)
      }

      fun publish() {
        val now = SystemClock.elapsedRealtime()
        cells.values.removeIf { now - it.seenAtMs > EXPIRE_AFTER_MS }
        trySend(SourceState(signals = cells.values.map { it.signal }, label = label()))
      }

      fun ingest(
        monitor: SubscriptionMonitor,
        infos: List<CellInfo>
      ) {
        if (monitors[monitor.subId] !== monitor) return
        val now = SystemClock.elapsedRealtime()
        val context =
          CellContext(
            subId = monitor.subId,
            simSlot = monitor.simSlot.takeIf { monitors.size > 1 },
            isActiveDataSubscription = monitor.subId == SubscriptionManager.getActiveDataSubscriptionId(),
            servingLte = monitor.signalStrength?.getCellSignalStrengths(CellSignalStrengthLte::class.java)?.firstOrNull()
          )
        val signals =
          infos
            .filterNot { !it.isRegistered && it.timestampMillis > 0 && now - it.timestampMillis > EXPIRE_AFTER_MS }
            .mapNotNull { it.toCellSignal(context) }
        signals.forEach { cells[it.key] = SeenCell(monitor.subId, it, now) }
        if (signals.any { it.role == CellRole.SERVING }) {
          val listed = infos.filter { it.role() != CellRole.NEIGHBOR }.mapNotNullTo(HashSet()) { it.identityKey(monitor.subId) }
          cells.values.removeIf { it.subId == monitor.subId && it.signal.role != CellRole.NEIGHBOR && it.signal.key !in listed }
        }
        publish()
      }

      fun sync() {
        val active = activeSubscriptions().associateBy { it.subscriptionId }
        (monitors.keys - active.keys).forEach { subId ->
          monitors.remove(subId)?.unregister()
          cells.values.removeIf { it.subId == subId }
        }
        (active - monitors.keys).forEach { (subId, info) ->
          val monitor = SubscriptionMonitor(info, telephony.createForSubscriptionId(subId), executor, ::ingest, ::publish)
          monitors[subId] = monitor
          monitor.register()
        }
      }

      launch {
        subscriptions.changes().collect {
          sync()
          publish()
        }
      }
      launch {
        sync()
        while (true) {
          monitors.values.forEach { it.requestRefresh() }
          publish()
          delay(HEARTBEAT_MS)
        }
      }
      awaitClose { monitors.values.forEach { it.unregister() } }
    }

  @SuppressLint("MissingPermission")
  private class SubscriptionMonitor(
    info: SubscriptionInfo,
    private val telephony: TelephonyManager,
    private val executor: Executor,
    private val onCells: (SubscriptionMonitor, List<CellInfo>) -> Unit,
    private val onChange: () -> Unit
  ) : TelephonyCallback(),
    TelephonyCallback.CellInfoListener,
    TelephonyCallback.SignalStrengthsListener,
    TelephonyCallback.DisplayInfoListener,
    TelephonyCallback.UserMobileDataStateListener {
    val subId = info.subscriptionId
    val simSlot = info.simSlotIndex + 1
    var signalStrength: SignalStrength? = null
      private set
    var displayInfo: TelephonyDisplayInfo? = null
      private set
    var dataEnabled: Boolean? = null
      private set

    fun register() = telephony.registerTelephonyCallback(executor, this)

    fun requestRefresh() =
      telephony.requestCellInfoUpdate(
        executor,
        object : TelephonyManager.CellInfoCallback() {
          override fun onCellInfo(cellInfo: List<CellInfo>) = onCells(this@SubscriptionMonitor, cellInfo)

          override fun onError(
            errorCode: Int,
            detail: Throwable?
          ) {
            Log.w(TAG, "Cell info refresh failed for subscription $subId with code $errorCode", detail)
          }
        }
      )

    fun unregister() = telephony.unregisterTelephonyCallback(this)

    override fun onCellInfoChanged(cellInfo: List<CellInfo>) = onCells(this, cellInfo)

    override fun onSignalStrengthsChanged(signalStrength: SignalStrength) {
      this.signalStrength = signalStrength
    }

    override fun onDisplayInfoChanged(telephonyDisplayInfo: TelephonyDisplayInfo) {
      displayInfo = telephonyDisplayInfo
      onChange()
    }

    override fun onUserMobileDataStateChanged(enabled: Boolean) {
      dataEnabled = enabled
      onChange()
    }
  }
}

private fun SubscriptionManager.changes(): Flow<Unit> =
  callbackFlow {
    val listener =
      object : SubscriptionManager.OnSubscriptionsChangedListener() {
        override fun onSubscriptionsChanged() {
          trySend(Unit)
        }
      }
    addOnSubscriptionsChangedListener(Runnable::run, listener)
    awaitClose { removeOnSubscriptionsChangedListener(listener) }
  }.flowOn(Dispatchers.Main)

private fun modemLabel(
  info: TelephonyDisplayInfo,
  dataEnabled: Boolean
): String {
  val network =
    when (info.networkType) {
      TelephonyManager.NETWORK_TYPE_NR -> nrLabel(info.overrideNetworkType)

      TelephonyManager.NETWORK_TYPE_LTE -> lteLabel(info.overrideNetworkType)

      TelephonyManager.NETWORK_TYPE_UMTS,
      TelephonyManager.NETWORK_TYPE_HSDPA,
      TelephonyManager.NETWORK_TYPE_HSUPA,
      TelephonyManager.NETWORK_TYPE_HSPA,
      TelephonyManager.NETWORK_TYPE_HSPAP -> "3G UMTS"

      TelephonyManager.NETWORK_TYPE_EDGE,
      TelephonyManager.NETWORK_TYPE_GPRS,
      TelephonyManager.NETWORK_TYPE_GSM -> "2G GSM"

      TelephonyManager.NETWORK_TYPE_UNKNOWN -> return "Not connected"

      else -> "Unknown"
    }
  return if (dataEnabled) network else "$network · data off"
}

private fun nrLabel(overrideNetworkType: Int): String =
  if (overrideNetworkType == TelephonyDisplayInfo.OVERRIDE_NETWORK_TYPE_NR_ADVANCED) "5G+ SA" else "5G SA"

private fun lteLabel(overrideNetworkType: Int): String =
  when (overrideNetworkType) {
    TelephonyDisplayInfo.OVERRIDE_NETWORK_TYPE_NR_ADVANCED -> "5G+ NSA (LTE anchor)"
    TelephonyDisplayInfo.OVERRIDE_NETWORK_TYPE_NR_NSA -> "5G NSA (LTE anchor)"
    TelephonyDisplayInfo.OVERRIDE_NETWORK_TYPE_LTE_ADVANCED_PRO -> "4G LTE-A Pro"
    TelephonyDisplayInfo.OVERRIDE_NETWORK_TYPE_LTE_CA -> "4G LTE-CA"
    else -> "4G LTE"
  }
