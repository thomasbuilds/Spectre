package dev.thomasbuilds.spectre.ui

import android.Manifest
import android.app.Activity
import android.bluetooth.BluetoothAdapter
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Bluetooth
import androidx.compose.material.icons.rounded.LocationOn
import androidx.compose.material.icons.rounded.SatelliteAlt
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.SignalCellularAlt
import androidx.compose.material.icons.rounded.Wifi
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.thomasbuilds.spectre.R
import dev.thomasbuilds.spectre.analysis.ExposureCalculator
import dev.thomasbuilds.spectre.model.ScannerStatus
import dev.thomasbuilds.spectre.settings.SettingsRepository
import dev.thomasbuilds.spectre.ui.components.DetailListSheets
import dev.thomasbuilds.spectre.ui.components.ExposureGauge
import dev.thomasbuilds.spectre.ui.components.SignalCard
import dev.thomasbuilds.spectre.ui.components.detailListSection
import dev.thomasbuilds.spectre.ui.components.rememberDetailListState
import dev.thomasbuilds.spectre.viewmodel.SpectreViewModel
import kotlinx.coroutines.launch
import kotlin.math.roundToInt
import dev.thomasbuilds.spectre.settings.Settings as AppSettings

enum class SignalSource { CELLULAR, WIFI, BLUETOOTH, GNSS }

@Composable
fun DashboardScreen(
  viewModel: SpectreViewModel,
  settingsRepository: SettingsRepository,
  onOpenSettings: () -> Unit,
  onOpenRecon: () -> Unit
) {
  val state by viewModel.state.collectAsStateWithLifecycle()
  val context = LocalContext.current
  val settings by settingsRepository.settings.collectAsStateWithLifecycle(initialValue = AppSettings.DEFAULTS)
  val coroutineScope = rememberCoroutineScope()
  var expanded by rememberSaveable { mutableStateOf<SignalSource?>(null) }

  var expandedDetailKey by rememberSaveable(expanded) { mutableStateOf<String?>(null) }
  var detailAnchorIndex by rememberSaveable(expanded) { mutableIntStateOf(-1) }
  val detailState = rememberDetailListState()
  val onToggleDetail: (String, Int) -> Unit = { key, index ->
    if (expandedDetailKey == key) {
      expandedDetailKey = null
      detailAnchorIndex = -1
    } else {
      expandedDetailKey = key
      detailAnchorIndex = index
    }
  }

  var showWifiDialog by rememberSaveable { mutableStateOf(false) }
  var showLocationDialog by rememberSaveable { mutableStateOf(false) }

  val permissionLauncher =
    rememberLauncherForActivityResult(
      ActivityResultContracts.RequestMultiplePermissions()
    ) { results ->
      val firstDenied = results.entries.firstOrNull { !it.value }?.key ?: return@rememberLauncherForActivityResult
      val activity = context as? Activity
      val canPromptAgain = activity?.shouldShowRequestPermissionRationale(firstDenied) == true
      if (!canPromptAgain) {
        openAppPermissionSettings(context)
      }
    }

  val bluetoothEnableLauncher =
    rememberLauncherForActivityResult(
      ActivityResultContracts.StartActivityForResult()
    ) {}

  val expandedStatus =
    when (expanded) {
      SignalSource.CELLULAR -> state.cellularStatus
      SignalSource.WIFI -> state.wifiStatus
      SignalSource.BLUETOOTH -> state.bluetoothStatus
      SignalSource.GNSS -> state.gnssStatus
      null -> ScannerStatus.OK
    }
  LaunchedEffect(expandedStatus) {
    if (expanded != null && expandedStatus != ScannerStatus.OK) {
      expanded = null
    }
  }

  fun handleCardTap(
    source: SignalSource,
    status: ScannerStatus
  ) {
    when (status) {
      ScannerStatus.OK -> {
        expanded = if (expanded == source) null else source
      }

      ScannerStatus.NO_PERMISSION -> {
        permissionLauncher.launch(permissionsForSource(source))
      }

      ScannerStatus.RADIO_OFF -> {
        when (source) {
          SignalSource.BLUETOOTH -> {
            runCatching {
              bluetoothEnableLauncher.launch(Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE))
            }
          }

          SignalSource.WIFI -> {
            showWifiDialog = true
          }

          else -> {
            openWirelessSettings(context)
          }
        }
      }

      ScannerStatus.LOCATION_OFF -> {
        showLocationDialog = true
      }

      ScannerStatus.NO_SIM -> {
        Unit
      }
    }
  }

  val insets = WindowInsets.safeDrawing.asPaddingValues()
  Surface(
    modifier = Modifier.fillMaxSize(),
    color = MaterialTheme.colorScheme.background
  ) {
    val locationOff = state.cellularStatus == ScannerStatus.LOCATION_OFF
    val expandedSource = expanded
    LazyColumn(
      modifier =
        Modifier
          .fillMaxSize()
          .padding(insets),
      contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
      horizontalAlignment = Alignment.CenterHorizontally
    ) {
      item(key = "header") {
        Box(modifier = Modifier.fillMaxWidth()) {
          Row(
            modifier =
              Modifier
                .align(Alignment.CenterStart)
                .offset(x = (-7).dp),
            verticalAlignment = Alignment.CenterVertically
          ) {
            Icon(
              imageVector = ImageVector.vectorResource(R.drawable.ic_launcher_foreground),
              contentDescription = null,
              tint = MaterialTheme.colorScheme.onSurface,
              modifier = Modifier.size(56.dp)
            )
            Text(
              "Spectre",
              style = MaterialTheme.typography.headlineSmall,
              fontWeight = FontWeight.SemiBold,
              color = MaterialTheme.colorScheme.onSurface,
              modifier = Modifier.offset(x = (-8).dp)
            )
          }
          IconButton(
            onClick = onOpenSettings,
            modifier = Modifier.align(Alignment.CenterEnd)
          ) {
            Icon(
              imageVector = Icons.Rounded.Settings,
              contentDescription = "Settings",
              tint = MaterialTheme.colorScheme.onSurface
            )
          }
        }
      }

      item(key = "gauge") {
        Spacer(Modifier.height(24.dp))
        Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
          ExposureGauge(
            totalDbm = state.totalDbm.toFloat(),
            complete = state.totalScoreUsable
          )
        }
        Spacer(Modifier.height(24.dp))
      }

      item(key = "cards") {
        val liveWifi = state.wifi.filterNot { it.isStale }
        val liveBluetooth = state.bluetooth.filterNot { it.isStale }
        Column(
          modifier = Modifier.fillMaxWidth(),
          verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
          Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            SignalCard(
              title = "Cellular",
              icon = Icons.Rounded.SignalCellularAlt,
              count = state.cellular.size,
              countLabel = if (state.cellular.size == 1) "tower" else "towers",
              strongest =
                ExposureCalculator
                  .cellularDbm(state.cellular)
                  ?.let { "${it.roundToInt()} dBm" } ?: "—",
              status = state.cellularStatus,
              selected = expanded == SignalSource.CELLULAR,
              ready = state.cellularReady,
              modifier = Modifier.weight(1f),
              onClick = { handleCardTap(SignalSource.CELLULAR, state.cellularStatus) }
            )
            SignalCard(
              title = "Wi-Fi",
              icon = Icons.Rounded.Wifi,
              count = liveWifi.size,
              countLabel = if (liveWifi.size == 1) "AP" else "APs",
              strongest =
                ExposureCalculator
                  .wifiDbm(liveWifi)
                  ?.let { "${it.roundToInt()} dBm" } ?: "—",
              status = state.wifiStatus,
              selected = expanded == SignalSource.WIFI,
              ready = state.wifiReady,
              modifier = Modifier.weight(1f),
              onClick = { handleCardTap(SignalSource.WIFI, state.wifiStatus) }
            )
          }
          Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            SignalCard(
              title = "Bluetooth",
              icon = Icons.Rounded.Bluetooth,
              count = liveBluetooth.size,
              countLabel = if (liveBluetooth.size == 1) "device" else "devices",
              strongest =
                ExposureCalculator
                  .bluetoothDbm(liveBluetooth)
                  ?.let { "${it.roundToInt()} dBm" } ?: "—",
              status = state.bluetoothStatus,
              selected = expanded == SignalSource.BLUETOOTH,
              ready = state.bluetoothReady,
              modifier = Modifier.weight(1f),
              onClick = { handleCardTap(SignalSource.BLUETOOTH, state.bluetoothStatus) }
            )
            SignalCard(
              title = "GNSS",
              icon = Icons.Rounded.SatelliteAlt,
              count = state.gnss.size,
              countLabel = if (state.gnss.size == 1) "satellite" else "satellites",
              strongest =
                state.gnss
                  .maxByOrNull { it.cn0DbHz }
                  ?.let { "${it.cn0DbHz.toInt()} dBHz" } ?: "—",
              status = state.gnssStatus,
              selected = expanded == SignalSource.GNSS,
              ready = state.gnssReady,
              modifier = Modifier.weight(1f),
              onClick = { handleCardTap(SignalSource.GNSS, state.gnssStatus) }
            )
          }
        }
      }

      if (locationOff) {
        item(key = "location") {
          Spacer(Modifier.height(16.dp))
          Button(
            onClick = { showLocationDialog = true },
            modifier = Modifier.fillMaxWidth()
          ) {
            Icon(
              imageVector = Icons.Rounded.LocationOn,
              contentDescription = null,
              modifier = Modifier.size(18.dp)
            )
            Spacer(Modifier.width(8.dp))
            Text("Enable Location")
          }
        }
      } else if (expandedSource != null) {
        item(key = "detail-spacer") { Spacer(Modifier.height(24.dp)) }
        detailListSection(
          state = state,
          source = expandedSource,
          holder = detailState,
          expandedKey = expandedDetailKey,
          anchorIndex = detailAnchorIndex,
          onToggle = onToggleDetail,
          onOpenRecon = onOpenRecon,
          showStaleWifi = settings.showStaleWifi,
          showStaleBluetooth = settings.showStaleBluetooth,
          resolveBleDevice = viewModel::bleRemoteDevice
        )
      }
    }
  }

  DetailListSheets(
    source = expanded,
    holder = detailState,
    showStaleWifi = settings.showStaleWifi,
    onSetShowStaleWifi = { show ->
      coroutineScope.launch { settingsRepository.setShowStaleWifi(show) }
    },
    showStaleBluetooth = settings.showStaleBluetooth,
    onSetShowStaleBluetooth = { show ->
      coroutineScope.launch { settingsRepository.setShowStaleBluetooth(show) }
    }
  )

  if (showWifiDialog) {
    EnableDialog(
      title = "Enable Wi-Fi?",
      body =
        "Spectre needs Wi-Fi turned on to scan for access points.\n\nTap Enable to open the Wi-Fi panel.",
      confirmLabel = "Enable",
      onConfirm = {
        showWifiDialog = false
        runCatching {
          context.startActivity(Intent(Settings.Panel.ACTION_INTERNET_CONNECTIVITY))
        }.recoverCatching { context.startActivity(Intent(Settings.Panel.ACTION_WIFI)) }
          .recoverCatching { openWirelessSettings(context) }
      },
      onDismiss = { showWifiDialog = false }
    )
  }

  if (showLocationDialog) {
    EnableDialog(
      title = "Enable Location",
      body = "Android requires precise location enabled for apps to scan cellular, Wi-Fi, Bluetooth, and GNSS.",
      confirmLabel = "Open Settings",
      onConfirm = {
        showLocationDialog = false
        runCatching {
          context.startActivity(
            Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS)
              .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
          )
        }
      },
      onDismiss = { showLocationDialog = false }
    )
  }
}

@Composable
private fun EnableDialog(
  title: String,
  body: String,
  confirmLabel: String,
  onConfirm: () -> Unit,
  onDismiss: () -> Unit
) {
  AlertDialog(
    onDismissRequest = onDismiss,
    title = { Text(title) },
    text = { Text(body, style = MaterialTheme.typography.bodyMedium) },
    confirmButton = { TextButton(onClick = onConfirm) { Text(confirmLabel) } },
    dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
  )
}

private fun permissionsForSource(source: SignalSource): Array<String> =
  when (source) {
    SignalSource.CELLULAR -> {
      arrayOf(
        Manifest.permission.READ_PHONE_STATE,
        Manifest.permission.ACCESS_FINE_LOCATION
      )
    }

    SignalSource.WIFI -> {
      arrayOf(
        Manifest.permission.ACCESS_FINE_LOCATION,
        Manifest.permission.ACCESS_WIFI_STATE,
        Manifest.permission.CHANGE_WIFI_STATE
      )
    }

    SignalSource.BLUETOOTH -> {
      arrayOf(
        Manifest.permission.BLUETOOTH_SCAN,
        Manifest.permission.BLUETOOTH_CONNECT,
        Manifest.permission.ACCESS_FINE_LOCATION
      )
    }

    SignalSource.GNSS -> {
      arrayOf(
        Manifest.permission.ACCESS_FINE_LOCATION
      )
    }
  }

private fun openAppPermissionSettings(context: Context) {
  runCatching {
    context.startActivity(
      Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
        data = Uri.fromParts("package", context.packageName, null)
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
      }
    )
  }
}

private fun openWirelessSettings(context: Context) {
  runCatching {
    context.startActivity(
      Intent(Settings.ACTION_WIRELESS_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    )
  }
}
