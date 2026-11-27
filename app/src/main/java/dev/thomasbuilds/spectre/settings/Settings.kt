package dev.thomasbuilds.spectre.settings

import android.app.UiModeManager
import android.content.Context
import android.util.Log
import androidx.datastore.core.handlers.ReplaceFileCorruptionHandler
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.io.IOException

private const val TAG = "Settings"

private val THEME_MODE = stringPreferencesKey("theme_mode")
private val SHOW_STALE_WIFI = booleanPreferencesKey("show_stale_wifi")
private val SHOW_STALE_BLUETOOTH = booleanPreferencesKey("show_stale_bluetooth")

private val Context.dataStore by preferencesDataStore(
  name = "spectre_settings",
  corruptionHandler = ReplaceFileCorruptionHandler { emptyPreferences() }
)

enum class ThemeMode(
  val label: String,
  val nightMode: Int
) {
  SYSTEM("System default", UiModeManager.MODE_NIGHT_AUTO),
  LIGHT("Light", UiModeManager.MODE_NIGHT_NO),
  DARK("Dark", UiModeManager.MODE_NIGHT_YES)
}

data class AppSettings(
  val themeMode: ThemeMode = ThemeMode.SYSTEM,
  val showStaleWifi: Boolean = false,
  val showStaleBluetooth: Boolean = false
)

class SettingsRepository(
  context: Context
) {
  private val store = context.applicationContext.dataStore
  private val uiMode = checkNotNull(context.getSystemService(UiModeManager::class.java))

  val settings: Flow<AppSettings> =
    store.data.catch { if (it is IOException) emit(emptyPreferences()) else throw it }.map { preferences ->
      AppSettings(
        themeMode = ThemeMode.entries.firstOrNull { it.name == preferences[THEME_MODE] } ?: ThemeMode.SYSTEM,
        showStaleWifi = preferences[SHOW_STALE_WIFI] ?: false,
        showStaleBluetooth = preferences[SHOW_STALE_BLUETOOTH] ?: false
      )
    }

  suspend fun setThemeMode(mode: ThemeMode) {
    save { it[THEME_MODE] = mode.name }
    uiMode.setApplicationNightMode(mode.nightMode)
  }

  suspend fun setShowStaleWifi(show: Boolean) {
    save { it[SHOW_STALE_WIFI] = show }
  }

  suspend fun setShowStaleBluetooth(show: Boolean) {
    save { it[SHOW_STALE_BLUETOOTH] = show }
  }

  suspend fun reset() {
    save { it.clear() }
    uiMode.setApplicationNightMode(ThemeMode.SYSTEM.nightMode)
  }

  suspend fun applyStoredNightMode() {
    uiMode.setApplicationNightMode(settings.first().themeMode.nightMode)
  }

  private suspend fun save(transform: (MutablePreferences) -> Unit) {
    try {
      store.edit(transform)
    } catch (e: IOException) {
      Log.w(TAG, "Saving settings failed", e)
    }
  }
}
