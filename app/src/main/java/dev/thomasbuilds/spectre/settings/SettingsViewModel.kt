package dev.thomasbuilds.spectre.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

private const val STOP_TIMEOUT_MS = 5_000L

class SettingsViewModel(
  private val repository: SettingsRepository
) : ViewModel() {
  val settings: StateFlow<AppSettings?> = repository.settings.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), null)

  fun setThemeMode(mode: ThemeMode) {
    viewModelScope.launch { repository.setThemeMode(mode) }
  }

  fun reset() {
    viewModelScope.launch { repository.reset() }
  }
}
