package dev.thomasbuilds.spectre.scanner

import android.content.Context

class OuiLookup(
  context: Context
) {
  private val appContext = context.applicationContext
  private val table: Map<String, String> by lazy { load() }

  fun vendor(mac: String?): String? {
    val hex = normalizeMac(mac) ?: return null
    val firstOctet = hex.substring(0, 2).toIntOrNull(16) ?: return null
    if (firstOctet and LOCALLY_ADMINISTERED != 0) return "Randomized"
    // Longest assignment wins: MA-S /36 (9 hex), then MA-M /28 (7), then MA-L /24 (6).
    return table[hex.substring(0, 9)] ?: table[hex.substring(0, 7)] ?: table[hex.substring(0, 6)]
  }

  private fun load(): Map<String, String> =
    runCatching {
      appContext.assets
        .open(ASSET)
        .bufferedReader()
        .useLines { parse(it) }
    }.getOrDefault(emptyMap())

  companion object {
    private const val ASSET = "oui.txt"
    private const val LOCALLY_ADMINISTERED = 0x02

    fun parse(lines: Sequence<String>): Map<String, String> =
      buildMap {
        // Vendors repeat across many OUIs, so keep one shared String per distinct name.
        val names = HashMap<String, String>()
        for (line in lines) {
          val tab = line.indexOf('\t')
          if (tab < 6) continue
          val name = line.substring(tab + 1)
          put(line.substring(0, tab), names.getOrPut(name) { name })
        }
      }

    fun normalizeMac(mac: String?): String? =
      mac
        .orEmpty()
        .filter { it != ':' && it != '-' }
        .uppercase()
        .takeIf { it.length >= 12 }
  }
}
