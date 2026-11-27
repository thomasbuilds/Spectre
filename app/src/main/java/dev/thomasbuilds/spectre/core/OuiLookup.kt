package dev.thomasbuilds.spectre.core

import android.content.Context

private const val ASSET = "oui.txt"
private const val LOCALLY_ADMINISTERED = 0x02
private const val MAC_HEX_LENGTH = 12
private val PREFIX_LENGTHS_LONGEST_FIRST = listOf(9, 7, 6)

class OuiLookup(
  context: Context
) {
  private val table: Map<String, String> by lazy {
    context.applicationContext.assets
      .open(ASSET)
      .bufferedReader()
      .useLines(::parse)
  }

  fun vendor(address: String): String? {
    val hex = address.filter { it.isLetterOrDigit() }.uppercase()
    if (hex.length < MAC_HEX_LENGTH || hex.take(2).toInt(16) and LOCALLY_ADMINISTERED != 0) return null
    return PREFIX_LENGTHS_LONGEST_FIRST.firstNotNullOfOrNull { table[hex.take(it)] }
  }

  private fun parse(lines: Sequence<String>): Map<String, String> {
    val names = HashMap<String, String>()
    return buildMap {
      lines.forEach { line ->
        val name = line.substringAfter('\t')
        put(line.substringBefore('\t'), names.getOrPut(name) { name })
      }
    }
  }
}
