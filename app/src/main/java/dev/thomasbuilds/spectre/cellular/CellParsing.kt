package dev.thomasbuilds.spectre.cellular

import android.os.Build
import android.telephony.CellIdentity
import android.telephony.CellIdentityNr
import android.telephony.CellInfo
import android.telephony.CellInfoGsm
import android.telephony.CellInfoLte
import android.telephony.CellInfoNr
import android.telephony.CellInfoWcdma
import android.telephony.CellSignalStrengthLte
import android.telephony.CellSignalStrengthNr
import kotlin.math.log10
import kotlin.math.roundToInt

private const val NR_SS_RSRP_TO_CARRIER_DB = 30
private const val WCDMA_RSCP_TO_CARRIER_DB = 10
private const val WCDMA_EC_NO_FLOOR_DB = -24
private const val WCDMA_NEIGHBOR_FLOOR_DBM = -115
private const val WCDMA_RSCP_ASU_OFFSET_DB = 120
private const val UNKNOWN_BANDWIDTH_RESOURCE_BLOCKS = 75
private const val CSQ_CEILING_DBM = -51
private const val LTE_TA_STEP_M = 78.07
private const val GSM_TA_STEP_M = 553.46
private const val SPEED_OF_LIGHT_M_PER_US = 299.792458
private val LTE_TA_RANGE = 1..1282
private val GSM_TA_RANGE = 1..219
private val CSQ_MEASURED_DBM = -111..-53
private val PLAUSIBLE_RSSI_OVER_RSRP_DB = 0..40

internal class CellContext(
  val subId: Int,
  val simSlot: Int?,
  val isActiveDataSubscription: Boolean,
  val servingLte: CellSignalStrengthLte?
)

private fun Int.known(): Int? = takeIf { it != CellInfo.UNAVAILABLE }

private fun Long.known(): Long? = takeIf { it != CellInfo.UNAVAILABLE_LONG }

private fun Int.dbm(): Int? = takeIf { it < 0 }

internal fun CellInfo.identityKey(subId: Int): String? =
  when (this) {
    is CellInfoNr -> (cellIdentity as CellIdentityNr).run { "NR-${pci.known()}@${nrarfcn.known()}" }
    is CellInfoLte -> cellIdentity.run { "LTE-${pci.known()}@${earfcn.known()}" }
    is CellInfoWcdma -> cellIdentity.run { "WCDMA-${psc.known()}@${uarfcn.known()}" }
    is CellInfoGsm -> cellIdentity.run { "GSM-${bsic.known() ?: "?"}@${arfcn.known()}" }
    else -> null
  }?.let { "$subId:$it" }

internal fun CellInfo.toCellSignal(context: CellContext): CellSignal? =
  when (this) {
    is CellInfoNr -> nr(context)
    is CellInfoLte -> lte(context)
    is CellInfoWcdma -> wcdma(context)
    is CellInfoGsm -> gsm(context)
    else -> null
  }

internal fun CellInfo.role(): CellRole =
  when {
    isRegistered || cellConnectionStatus == CellInfo.CONNECTION_PRIMARY_SERVING -> CellRole.SERVING
    cellConnectionStatus == CellInfo.CONNECTION_SECONDARY_SERVING -> CellRole.SECONDARY_SERVING
    else -> CellRole.NEIGHBOR
  }

private fun CellInfo.signal(
  context: CellContext,
  type: CellNetworkType,
  identity: CellIdentity,
  mcc: String?,
  mnc: String?,
  exposureDbm: Int,
  channel: Int?,
  downlinkMhz: Double?,
  metrics: Map<CellMetric, Int>,
  cellId: Long?,
  physicalId: Int?,
  areaCode: Int?,
  bandwidthKhz: Int? = null,
  bands: List<Int> = emptyList(),
  timingAdvance: Int? = null,
  distanceMeters: Double? = null
): CellSignal {
  val role = role()
  return CellSignal(
    key = checkNotNull(identityKey(context.subId)),
    type = type,
    role = role,
    isConnected = isRegistered && context.isActiveDataSubscription,
    exposureDbm = exposureDbm,
    channelKey = channel?.takeIf { it > 0 }?.let { "${type.name}-$it" },
    distanceMeters = distanceMeters,
    operator = identity.operatorAlphaLong?.toString()?.takeIf { it.isNotBlank() },
    simSlot = context.simSlot,
    mcc = mcc,
    mnc = mnc,
    cellId = cellId,
    physicalId = physicalId,
    areaCode = areaCode,
    arfcn = channel,
    downlinkMhz = downlinkMhz,
    bandwidthKhz = bandwidthKhz,
    bands = bands,
    timingAdvance = timingAdvance,
    metrics = metrics.filterValues { it != CellInfo.UNAVAILABLE }
  )
}

private fun CellInfoNr.nr(context: CellContext): CellSignal? {
  val identity = cellIdentity as CellIdentityNr
  val strength = cellSignalStrength as CellSignalStrengthNr
  if (identity.nrarfcn == 0 && identity.nci == 0L && identity.pci == 0 && identity.tac == 0) return null
  val ssRsrp = strength.dbm.dbm() ?: return null
  val timingAdvanceMicros =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
      strength.timingAdvanceMicros.known()?.takeIf { it > 0 }
    } else {
      null
    }
  return signal(
    context = context,
    type = CellNetworkType.NR,
    identity = identity,
    mcc = identity.mccString,
    mnc = identity.mncString,
    exposureDbm = ssRsrp + NR_SS_RSRP_TO_CARRIER_DB,
    channel = identity.nrarfcn.known(),
    downlinkMhz = identity.nrarfcn.known()?.let(::nrArfcnToMhz),
    metrics =
      mapOf(
        CellMetric.SS_RSRP to ssRsrp,
        CellMetric.SS_RSRQ to strength.ssRsrq,
        CellMetric.SS_SINR to strength.ssSinr,
        CellMetric.CSI_RSRP to strength.csiRsrp
      ),
    cellId = identity.nci.known(),
    physicalId = identity.pci.known(),
    areaCode = identity.tac.known(),
    bands = identity.bands.toList(),
    distanceMeters = timingAdvanceMicros?.takeIf { isRegistered }?.let { it * SPEED_OF_LIGHT_M_PER_US }
  )
}

private fun CellInfoLte.lte(context: CellContext): CellSignal? {
  val identity = cellIdentity
  val reported = cellSignalStrength
  val strength = context.servingLte?.takeIf { isRegistered && reported.dbm.dbm() == null && it.rsrp.dbm() != null } ?: reported
  val rsrp = strength.rsrp.dbm() ?: return null
  val bandwidthKhz = identity.bandwidth.known()?.takeIf { it > 0 }
  val timingAdvance = reported.timingAdvance.takeIf { isRegistered && it in LTE_TA_RANGE }
  return signal(
    context = context,
    type = CellNetworkType.LTE,
    identity = identity,
    mcc = identity.mccString,
    mnc = identity.mncString,
    exposureDbm = lteCarrierRssiDbm(rsrp, strength.rsrq.known(), strength.rssi.known(), bandwidthKhz),
    channel = identity.earfcn.known(),
    downlinkMhz = identity.earfcn.known()?.let(::earfcnToMhz),
    metrics =
      mapOf(
        CellMetric.RSRP to rsrp,
        CellMetric.RSRQ to strength.rsrq,
        CellMetric.SNR to strength.rssnr,
        CellMetric.CQI to strength.cqi
      ),
    cellId = identity.ci.known()?.toLong(),
    physicalId = identity.pci.known(),
    areaCode = identity.tac.known(),
    bandwidthKhz = bandwidthKhz,
    bands = identity.bands.toList(),
    timingAdvance = timingAdvance,
    distanceMeters = timingAdvance?.let { it * LTE_TA_STEP_M }
  )
}

private fun CellInfoWcdma.wcdma(context: CellContext): CellSignal? {
  val identity = cellIdentity
  val strength = cellSignalStrength
  if (identity.uarfcn == 0 && identity.cid == 0 && identity.psc == 0 && identity.lac == 0) return null
  val dbm = strength.dbm.dbm() ?: return null
  val ecNo = strength.ecNo.known()
  val isRscp = strength.asuLevel == dbm + WCDMA_RSCP_ASU_OFFSET_DB
  if (!isRegistered && ((ecNo != null && ecNo <= WCDMA_EC_NO_FLOOR_DB) || dbm <= WCDMA_NEIGHBOR_FLOOR_DBM)) return null
  return signal(
    context = context,
    type = CellNetworkType.WCDMA,
    identity = identity,
    mcc = identity.mccString,
    mnc = identity.mncString,
    exposureDbm =
      when {
        !isRscp -> dbm
        ecNo != null -> dbm - ecNo
        else -> dbm + WCDMA_RSCP_TO_CARRIER_DB
      },
    channel = identity.uarfcn.known(),
    downlinkMhz = identity.uarfcn.known()?.let(::uarfcnToMhz),
    metrics =
      mapOf((if (isRscp && ecNo != null) CellMetric.RSCP else CellMetric.RSCP_OR_RSSI) to dbm) +
        listOfNotNull(ecNo?.let { CellMetric.EC_NO to it }),
    cellId = identity.cid.known()?.toLong(),
    physicalId = identity.psc.known(),
    areaCode = identity.lac.known()
  )
}

private fun CellInfoGsm.gsm(context: CellContext): CellSignal? {
  val identity = cellIdentity
  val strength = cellSignalStrength
  val rssi = strength.dbm.dbm() ?: return null
  val timingAdvance = strength.timingAdvance.takeIf { isRegistered && it in GSM_TA_RANGE }
  return signal(
    context = context,
    type = CellNetworkType.GSM,
    identity = identity,
    mcc = identity.mccString,
    mnc = identity.mncString,
    exposureDbm = rssi,
    channel = identity.arfcn.known(),
    downlinkMhz = identity.arfcn.known()?.let(::arfcnToMhz),
    metrics = listOfNotNull(strength.bitErrorRate.takeIf { it in 0..7 }?.let { CellMetric.RX_QUAL to it }).toMap(),
    cellId = identity.cid.known()?.toLong(),
    physicalId = identity.bsic.known(),
    areaCode = identity.lac.known(),
    timingAdvance = timingAdvance,
    distanceMeters = timingAdvance?.let { it * GSM_TA_STEP_M }
  )
}

private fun lteCarrierRssiDbm(
  rsrp: Int,
  rsrq: Int?,
  rssi: Int?,
  bandwidthKhz: Int?
): Int {
  val resourceBlocks = bandwidthKhz?.let(::lteResourceBlocks) ?: UNKNOWN_BANDWIDTH_RESOURCE_BLOCKS
  val fullLoadDbm = rsrp + (10 * log10(12.0 * resourceBlocks)).roundToInt()
  return when {
    rsrq != null -> (rsrp + 10 * log10(resourceBlocks.toDouble()) - rsrq).roundToInt()
    rssi == null || rssi - rsrp !in PLAUSIBLE_RSSI_OVER_RSRP_DB -> fullLoadDbm
    rssi in CSQ_MEASURED_DBM -> rssi
    rssi == CSQ_CEILING_DBM -> maxOf(CSQ_CEILING_DBM, fullLoadDbm)
    else -> fullLoadDbm
  }
}

private fun lteResourceBlocks(bandwidthKhz: Int): Int? =
  when (bandwidthKhz) {
    in 1300..1500 -> 6
    in 2900..3100 -> 15
    in 4900..5100 -> 25
    in 9900..10100 -> 50
    in 14900..15100 -> 75
    in 19900..20100 -> 100
    else -> null
  }
