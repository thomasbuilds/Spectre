package dev.thomasbuilds.spectre

import android.Manifest
import android.content.Intent
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.runtime.setValue
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.repeatOnLifecycle
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import dev.thomasbuilds.spectre.bluetooth.gatt.GattInspector
import dev.thomasbuilds.spectre.core.hasPermission
import dev.thomasbuilds.spectre.core.isLocationEnabled
import dev.thomasbuilds.spectre.dashboard.DashboardScreen
import dev.thomasbuilds.spectre.dashboard.DashboardViewModel
import dev.thomasbuilds.spectre.help.HelpScreen
import dev.thomasbuilds.spectre.onboarding.ONBOARDING_PERMISSIONS
import dev.thomasbuilds.spectre.onboarding.PermissionsScreen
import dev.thomasbuilds.spectre.recon.ReconScreen
import dev.thomasbuilds.spectre.recon.ReconViewModel
import dev.thomasbuilds.spectre.settings.SettingsScreen
import dev.thomasbuilds.spectre.settings.SettingsViewModel
import dev.thomasbuilds.spectre.ui.theme.SpectreTheme

private enum class Screen(
  val parent: Screen?
) {
  DASHBOARD(null),
  SETTINGS(DASHBOARD),
  HELP(SETTINGS),
  RECON(DASHBOARD)
}

class MainActivity : ComponentActivity() {
  private val app: SpectreApp get() = application as SpectreApp
  private val dashboardViewModel: DashboardViewModel by viewModels {
    viewModelFactory { initializer { DashboardViewModel(app.monitor, app.settings, GattInspector(app), createSavedStateHandle()) } }
  }
  private val settingsViewModel: SettingsViewModel by viewModels { viewModelFactory { initializer { SettingsViewModel(app.settings) } } }
  private val reconViewModel: ReconViewModel by viewModels()

  override fun onCreate(savedInstanceState: Bundle?) {
    enableEdgeToEdge()
    super.onCreate(savedInstanceState)
    window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

    setContent {
      SpectreTheme {
        var onboarded by rememberSaveable { mutableStateOf(hasPermission(Manifest.permission.ACCESS_FINE_LOCATION)) }
        var screen by rememberSaveable { mutableStateOf(Screen.DASHBOARD) }
        val screens = rememberSaveableStateHolder()
        val onboarding =
          rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
            onboarded = true
          }

        val lifecycle = LocalLifecycleOwner.current.lifecycle
        LaunchedEffect(onboarded) {
          if (!onboarded) return@LaunchedEffect
          lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            app.monitor.locationEnabled.collect {
              if (isLocationEnabled && hasPermission(Manifest.permission.ACCESS_FINE_LOCATION)) {
                startForegroundService(Intent(this@MainActivity, MonitorService::class.java))
              }
            }
          }
        }

        if (!onboarded) {
          PermissionsScreen(onContinue = { onboarding.launch(ONBOARDING_PERMISSIONS.toTypedArray()) })
        } else {
          val navigateUp = {
            if (screen == Screen.RECON) reconViewModel.stop()
            screen = screen.parent ?: screen
          }
          BackHandler(enabled = screen.parent != null, onBack = navigateUp)
          screens.SaveableStateProvider(screen) {
            when (screen) {
              Screen.DASHBOARD -> {
                DashboardScreen(
                  viewModel = dashboardViewModel,
                  onOpenSettings = { screen = Screen.SETTINGS },
                  onOpenRecon = { screen = Screen.RECON }
                )
              }

              Screen.SETTINGS -> {
                SettingsScreen(settingsViewModel, onBack = navigateUp, onOpenHelp = { screen = Screen.HELP })
              }

              Screen.HELP -> {
                HelpScreen(onBack = navigateUp)
              }

              Screen.RECON -> {
                ReconScreen(reconViewModel, onBack = navigateUp)
              }
            }
          }
        }
      }
    }
  }
}
