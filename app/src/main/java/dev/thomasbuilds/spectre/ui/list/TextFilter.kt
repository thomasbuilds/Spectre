package dev.thomasbuilds.spectre.ui.list

import androidx.compose.runtime.Immutable
import java.io.Serializable

enum class FilterMode(
  val label: String
) {
  INCLUDE("Include"),
  EXCLUDE("Exclude")
}

@Immutable
data class TextFilter(
  val text: String = "",
  val mode: FilterMode = FilterMode.INCLUDE
) : Serializable {
  private val tokens = text.split(',').map { it.trim().lowercase() }.filter { it.isNotEmpty() }

  val isActive: Boolean get() = tokens.isNotEmpty()

  fun accepts(matches: (String) -> Boolean): Boolean = tokens.isEmpty() || tokens.any(matches) == (mode == FilterMode.INCLUDE)
}
