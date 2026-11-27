package dev.thomasbuilds.spectre

import android.app.Application
import dev.thomasbuilds.spectre.monitor.Monitor
import dev.thomasbuilds.spectre.settings.SettingsRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class SpectreApp : Application() {
  private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

  val monitor by lazy { Monitor(this, scope) }
  val settings by lazy { SettingsRepository(this) }

  override fun onCreate() {
    super.onCreate()
    scope.launch { settings.applyStoredNightMode() }
  }
}
