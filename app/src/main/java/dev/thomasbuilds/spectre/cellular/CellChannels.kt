package dev.thomasbuilds.spectre.cellular

private class Band(
  val channels: IntRange,
  val firstMhz: Double
)

internal fun nrArfcnToMhz(nrArfcn: Int): Double? =
  when (nrArfcn) {
    in 0..<600_000 -> nrArfcn * 0.005
    in 600_000..<2_016_667 -> 3000.0 + (nrArfcn - 600_000) * 0.015
    in 2_016_667..3_279_165 -> 24_250.08 + (nrArfcn - 2_016_667) * 0.060
    else -> null
  }

internal fun earfcnToMhz(earfcn: Int): Double? = LTE_BANDS.toMhz(earfcn, stepMhz = 0.1)

internal fun uarfcnToMhz(uarfcn: Int): Double? = UMTS_BANDS.toMhz(uarfcn, stepMhz = 0.2)

internal fun arfcnToMhz(arfcn: Int): Double? = GSM_BANDS.toMhz(arfcn, stepMhz = 0.2)

private fun List<Band>.toMhz(
  channel: Int,
  stepMhz: Double
): Double? = firstOrNull { channel in it.channels }?.let { it.firstMhz + stepMhz * (channel - it.channels.first) }

private val LTE_BANDS =
  listOf(
    Band(0..599, 2110.0),
    Band(600..1199, 1930.0),
    Band(1200..1949, 1805.0),
    Band(1950..2399, 2110.0),
    Band(2400..2649, 869.0),
    Band(2650..2749, 875.0),
    Band(2750..3449, 2620.0),
    Band(3450..3799, 925.0),
    Band(3800..4149, 1844.9),
    Band(4150..4749, 2110.0),
    Band(4750..4949, 1475.9),
    Band(5010..5179, 729.0),
    Band(5180..5279, 746.0),
    Band(5280..5379, 758.0),
    Band(5730..5849, 734.0),
    Band(5850..5999, 860.0),
    Band(6000..6149, 875.0),
    Band(6150..6449, 791.0),
    Band(6450..6599, 1495.9),
    Band(6600..7399, 3510.0),
    Band(7500..7699, 2180.0),
    Band(7700..8039, 1525.0),
    Band(8040..8689, 1930.0),
    Band(8690..9039, 859.0),
    Band(9040..9209, 852.0),
    Band(9210..9659, 758.0),
    Band(9660..9769, 717.0),
    Band(9770..9869, 2350.0),
    Band(9870..9919, 462.5),
    Band(9920..10359, 1452.0),
    Band(36000..36199, 1900.0),
    Band(36200..36349, 2010.0),
    Band(36350..36949, 1850.0),
    Band(36950..37549, 1930.0),
    Band(37550..37749, 1910.0),
    Band(37750..38249, 2570.0),
    Band(38250..38649, 1880.0),
    Band(38650..39649, 2300.0),
    Band(39650..41589, 2496.0),
    Band(41590..43589, 3400.0),
    Band(43590..45589, 3600.0),
    Band(45590..46589, 703.0),
    Band(46590..46789, 1447.0),
    Band(46790..54539, 5150.0),
    Band(54540..55239, 5855.0),
    Band(55240..56739, 3550.0),
    Band(56740..58239, 3550.0),
    Band(58240..59089, 1432.0),
    Band(59090..59139, 1427.0),
    Band(59140..60139, 3300.0),
    Band(60140..60254, 2483.5),
    Band(60255..60304, 1670.0),
    Band(65536..66435, 2110.0),
    Band(66436..67335, 2110.0),
    Band(67336..67535, 738.0),
    Band(67536..67835, 753.0),
    Band(67836..68335, 2570.0),
    Band(68336..68585, 1995.0),
    Band(68586..68935, 617.0),
    Band(68936..68985, 461.0),
    Band(68986..69035, 460.0),
    Band(69036..69465, 1475.0),
    Band(69466..70315, 1432.0),
    Band(70316..70365, 1427.0),
    Band(70366..70545, 728.0),
    Band(70546..70595, 420.0),
    Band(70596..70645, 422.0),
    Band(70646..70655, 757.0),
    Band(70656..70705, 935.0),
    Band(73386..73485, 1820.0)
  )

private val UMTS_BANDS =
  listOf(
    Band(10562..10838, 2112.4),
    Band(9662..9938, 1932.4),
    Band(1162..1513, 1807.4),
    Band(1537..1738, 2112.4),
    Band(4357..4458, 871.4),
    Band(2237..2563, 2622.4),
    Band(2937..3088, 927.4),
    Band(9237..9387, 1847.4),
    Band(3112..3388, 2112.4),
    Band(3712..3787, 1478.4),
    Band(3842..3903, 731.4),
    Band(4017..4043, 748.4),
    Band(4117..4143, 760.4),
    Band(712..763, 877.4),
    Band(4512..4638, 793.4),
    Band(862..912, 1498.4),
    Band(4662..5038, 3512.4),
    Band(5112..5413, 1932.4),
    Band(5762..5913, 861.4),
    Band(6617..6813, 1454.4)
  )

private val GSM_BANDS =
  listOf(
    Band(0..124, 935.0),
    Band(128..251, 869.2),
    Band(259..293, 460.6),
    Band(306..340, 489.0),
    Band(512..885, 1805.2),
    Band(940..1023, 918.2)
  )
