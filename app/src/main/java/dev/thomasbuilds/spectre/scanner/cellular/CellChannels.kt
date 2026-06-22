package dev.thomasbuilds.spectre.scanner.cellular

// *ARFCN to downlink frequency (MHz) per 3GPP TS 38.104 (NR), 36.101 (LTE), 25.101 (WCDMA), 45.005 (GSM).
internal object CellChannels {
  fun label(mhz: Double): String = "%.1f MHz".format(mhz)

  fun nrArfcnToMhz(nrArfcn: Int): Double? =
    when {
      nrArfcn < 0 -> null
      nrArfcn < 600_000 -> nrArfcn * 0.005
      nrArfcn < 2_016_667 -> 3000.0 + (nrArfcn - 600_000) * 0.015
      nrArfcn <= 3_279_165 -> 24_250.08 + (nrArfcn - 2_016_667) * 0.060
      else -> null
    }

  fun earfcnToMhz(earfcn: Int): Double? {
    val band = LTE_BANDS.firstOrNull { earfcn in it.first..it.last } ?: return null
    return band.fdlLowMhz + 0.1 * (earfcn - band.first)
  }

  fun uarfcnToMhz(uarfcn: Int): Double? {
    val mhz = uarfcn * 0.2
    return if (mhz in 400.0..3000.0) mhz else null
  }

  // 512..810 overlaps DCS-1800 and PCS-1900. Resolved as DCS-1800, the near-universal modern allocation.
  fun arfcnToMhz(arfcn: Int): Double? =
    when (arfcn) {
      in 0..124 -> 935.0 + 0.2 * arfcn
      in 128..251 -> 869.2 + 0.2 * (arfcn - 128)
      in 512..885 -> 1805.2 + 0.2 * (arfcn - 512)
      in 975..1023 -> 935.0 + 0.2 * (arfcn - 1024)
      else -> null
    }

  // first = DL EARFCN offset (NOffs-DL, also identifies the band), last = range end, fdlLow in MHz.
  private class LteBand(
    val first: Int,
    val last: Int,
    val fdlLowMhz: Double
  )

  private val LTE_BANDS =
    listOf(
      LteBand(0, 599, 2110.0),
      LteBand(600, 1199, 1930.0),
      LteBand(1200, 1949, 1805.0),
      LteBand(1950, 2399, 2110.0),
      LteBand(2400, 2649, 869.0),
      LteBand(2750, 3449, 2620.0),
      LteBand(3450, 3799, 925.0),
      LteBand(5010, 5179, 729.0),
      LteBand(5180, 5279, 746.0),
      LteBand(5280, 5379, 758.0),
      LteBand(5730, 5849, 734.0),
      LteBand(5850, 5999, 860.0),
      LteBand(6000, 6149, 875.0),
      LteBand(6150, 6449, 791.0),
      LteBand(6450, 6599, 1495.9),
      LteBand(8040, 8689, 1930.0),
      LteBand(8690, 9039, 859.0),
      LteBand(9210, 9659, 758.0),
      LteBand(9660, 9769, 717.0),
      LteBand(9770, 9869, 2350.0),
      LteBand(9920, 10359, 1452.0),
      LteBand(37750, 38249, 2570.0),
      LteBand(38250, 38649, 1880.0),
      LteBand(38650, 39649, 2300.0),
      LteBand(39650, 41589, 2496.0),
      LteBand(41590, 43589, 3400.0),
      LteBand(43590, 45589, 3600.0),
      LteBand(46790, 54539, 5150.0),
      LteBand(55240, 56739, 3550.0),
      LteBand(66436, 67335, 2110.0),
      LteBand(68586, 68935, 617.0)
    )
}
