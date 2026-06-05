package dev.thomasbuilds.spectre

import android.Manifest
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.os.Build
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
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.repeatOnLifecycle
import dev.thomasbuilds.spectre.service.RFMonitorService
import dev.thomasbuilds.spectre.settings.Settings
import dev.thomasbuilds.spectre.settings.SettingsRepository
import dev.thomasbuilds.spectre.ui.DashboardScreen
import dev.thomasbuilds.spectre.ui.HelpScreen
import dev.thomasbuilds.spectre.ui.PermissionsScreen
import dev.thomasbuilds.spectre.ui.ReconScreen
import dev.thomasbuilds.spectre.ui.SettingsScreen
import dev.thomasbuilds.spectre.ui.theme.SpectreTheme
import dev.thomasbuilds.spectre.viewmodel.SpectreViewModel

enum class Screen { DASHBOARD, SETTINGS, RECON, HELP }

class MainActivity : ComponentActivity() {
  private val viewModel: SpectreViewModel by viewModels()
  private lateinit var settingsRepository: SettingsRepository

  private val shutdownReceiver =
    object : BroadcastReceiver() {
      override fun onReceive(
        context: Context?,
        intent: Intent?
      ) {
        finishAndRemoveTask()
      }
    }

  private val requiredPermissions: Array<String> =
    buildList {
      add(Manifest.permission.READ_PHONE_STATE)
      add(Manifest.permission.ACCESS_FINE_LOCATION)
      add(Manifest.permission.ACCESS_COARSE_LOCATION)
      add(Manifest.permission.BLUETOOTH_SCAN)
      add(Manifest.permission.BLUETOOTH_CONNECT)
      add(Manifest.permission.BLUETOOTH_ADVERTISE)
      // POST_NOTIFICATIONS is a runtime permission only on Android 13+
      if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        add(Manifest.permission.POST_NOTIFICATIONS)
      }
      // ACCESS_LOCAL_NETWORK is the runtime permission introduced in Android 16 that gates LAN
      // access (including WifiInfo.bssid for the Connected label, mDNS, SSDP, and unicast TCP).
      // Mandatory on Android 17 for apps targeting SDK 37+.
      if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.BAKLAVA) {
        add("android.permission.ACCESS_LOCAL_NETWORK")
      }
    }.toTypedArray()

  override fun onCreate(savedInstanceState: Bundle?) {
    enableEdgeToEdge()
    super.onCreate(savedInstanceState)
    window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
    ContextCompat.registerReceiver(
      this,
      shutdownReceiver,
      IntentFilter(RFMonitorService.ACTION_APP_SHUTDOWN),
      ContextCompat.RECEIVER_NOT_EXPORTED
    )
    settingsRepository = SettingsRepository(applicationContext)
    val versionName =
      runCatching {
        packageManager.getPackageInfo(packageName, 0).versionName ?: "1.0"
      }.getOrDefault("1.0")

    setContent {
      val settings by settingsRepository.settings
        .collectAsStateWithLifecycle(initialValue = Settings.DEFAULTS)

      SpectreTheme(mode = settings.themeMode) {
        var permissionsRequested by remember { mutableStateOf(hasEssentialPermissions()) }
        var screen by rememberSaveable { mutableStateOf(Screen.DASHBOARD) }

        val launcher =
          rememberLauncherForActivityResult(
            ActivityResultContracts.RequestMultiplePermissions()
          ) {
            permissionsRequested = true
          }

        val lifecycle = LocalLifecycleOwner.current.lifecycle
        LaunchedEffect(permissionsRequested) {
          if (!permissionsRequested) return@LaunchedEffect
          lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            viewModel.locationEnabled.collect { locOn ->
              if (locOn) viewModel.ensureServiceRunning()
            }
          }
        }

        BackHandler(enabled = permissionsRequested && screen != Screen.DASHBOARD) {
          screen = Screen.DASHBOARD
        }

        when {
          !permissionsRequested -> {
            PermissionsScreen(
              onContinue = { launcher.launch(requiredPermissions) }
            )
          }

          screen == Screen.SETTINGS -> {
            SettingsScreen(
              repository = settingsRepository,
              appVersion = versionName,
              onBack = { screen = Screen.DASHBOARD },
              onOpenHelp = { screen = Screen.HELP }
            )
          }

          screen == Screen.RECON -> {
            ReconScreen(
              onBack = { screen = Screen.DASHBOARD }
            )
          }

          screen == Screen.HELP -> {
            HelpScreen(
              onBack = { screen = Screen.SETTINGS }
            )
          }

          else -> {
            DashboardScreen(
              viewModel = viewModel,
              settingsRepository = settingsRepository,
              onOpenSettings = { screen = Screen.SETTINGS },
              onOpenRecon = { screen = Screen.RECON }
            )
          }
        }
      }
    }
  }

  override fun onDestroy() {
    runCatching { unregisterReceiver(shutdownReceiver) }
    super.onDestroy()
  }

  private fun hasEssentialPermissions(): Boolean =
    ContextCompat.checkSelfPermission(
      this,
      Manifest.permission.ACCESS_FINE_LOCATION
    ) == PackageManager.PERMISSION_GRANTED
}
