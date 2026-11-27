package dev.thomasbuilds.spectre.cellular

import androidx.compose.runtime.Immutable
import dev.thomasbuilds.spectre.core.dbmToMw

enum class CellNetworkType(
  val generation: String
) {
  NR("5G"),
  LTE("4G"),
  WCDMA("3G"),
  GSM("2G")
}

enum class CellRole { SERVING, SECONDARY_SERVING, NEIGHBOR }

enum class CellMetric { RSRP, RSRQ, SNR, CQI, SS_RSRP, SS_RSRQ, SS_SINR, CSI_RSRP, RSCP, RSCP_OR_RSSI, EC_NO, RX_QUAL }

@Immutable
data class CellSignal(
  val key: String,
  val type: CellNetworkType,
  val role: CellRole,
  val isConnected: Boolean,
  val exposureDbm: Int,
  val channelKey: String?,
  val distanceMeters: Double?,
  val operator: String?,
  val simSlot: Int?,
  val mcc: String?,
  val mnc: String?,
  val cellId: Long?,
  val physicalId: Int?,
  val areaCode: Int?,
  val arfcn: Int?,
  val downlinkMhz: Double?,
  val bandwidthKhz: Int?,
  val bands: List<Int>,
  val timingAdvance: Int?,
  val metrics: Map<CellMetric, Int>
)

internal fun List<CellSignal>.receivedPowerMw(): Double =
  groupBy { it.channelKey ?: it.key }.values.sumOf { cells -> dbmToMw(cells.maxOf { it.exposureDbm }) }
