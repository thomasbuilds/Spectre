package dev.thomasbuilds.spectre.scanner.ble

import android.content.Context

class CompanyIdLookup(
  context: Context
) {
  private val appContext = context.applicationContext
  private val table: Map<Int, String> by lazy { load() }

  fun name(id: Int): String? = table[id]

  private fun load(): Map<Int, String> =
    runCatching {
      appContext.assets
        .open(ASSET)
        .bufferedReader()
        .useLines { parse(it) }
    }.getOrDefault(emptyMap())

  companion object {
    private const val ASSET = "cid.txt"

    fun parse(lines: Sequence<String>): Map<Int, String> =
      buildMap {
        for (line in lines) {
          val tab = line.indexOf('\t')
          if (tab < 4) continue
          val id = line.substring(0, tab).toIntOrNull(16) ?: continue
          put(id, line.substring(tab + 1))
        }
      }
  }
}
