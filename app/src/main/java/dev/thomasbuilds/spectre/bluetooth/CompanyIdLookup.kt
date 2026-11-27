package dev.thomasbuilds.spectre.bluetooth

import android.content.Context

private const val ASSET = "cid.txt"

class CompanyIdLookup(
  context: Context
) {
  private val table: Map<Int, String> by lazy {
    context.applicationContext.assets
      .open(ASSET)
      .bufferedReader()
      .useLines { lines -> buildMap { lines.forEach { put(it.substringBefore('\t').toInt(16), it.substringAfter('\t')) } } }
  }

  fun name(id: Int): String? = table[id]
}
