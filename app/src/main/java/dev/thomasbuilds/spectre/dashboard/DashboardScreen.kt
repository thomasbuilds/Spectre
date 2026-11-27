package dev.thomasbuilds.spectre.dashboard

import android.bluetooth.BluetoothAdapter
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.LocationOn
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.thomasbuilds.spectre.R
import dev.thomasbuilds.spectre.bluetooth.BluetoothFilterSheet
import dev.thomasbuilds.spectre.bluetooth.bluetoothList
import dev.thomasbuilds.spectre.cellular.cellularList
import dev.thomasbuilds.spectre.core.Source
import dev.thomasbuilds.spectre.core.SourceStatus
import dev.thomasbuilds.spectre.gnss.gnssList
import dev.thomasbuilds.spectre.help.CELLULAR_HELP
import dev.thomasbuilds.spectre.help.WIFI_THROTTLE_HELP
import dev.thomasbuilds.spectre.ui.kit.MessageDialog
import dev.thomasbuilds.spectre.ui.kit.rememberPermissionRequest
import dev.thomasbuilds.spectre.ui.kit.startFirstAvailable
import dev.thomasbuilds.spectre.wifi.WifiFilterSheet
import dev.thomasbuilds.spectre.wifi.wifiList

@Composable
fun DashboardScreen(
  viewModel: DashboardViewModel,
  onOpenSettings: () -> Unit,
  onOpenRecon: () -> Unit
) {
  val state by viewModel.state.collectAsStateWithLifecycle()
  val settings by viewModel.settings.collectAsStateWithLifecycle()
  val openSource by viewModel.openSource.collectAsStateWithLifecycle()
  val expandedKey by viewModel.expandedKey.collectAsStateWithLifecycle()
  val wifiFilters by viewModel.wifiFilters.collectAsStateWithLifecycle()
  val bluetoothFilters by viewModel.bluetoothFilters.collectAsStateWithLifecycle()
  val cellularRows by viewModel.cellularRows.collectAsStateWithLifecycle()
  val wifiRows by viewModel.wifiRows.collectAsStateWithLifecycle()
  val bluetoothRows by viewModel.bluetoothRows.collectAsStateWithLifecycle()
  val gnssRows by viewModel.gnssRows.collectAsStateWithLifecycle()
  val inspection by viewModel.inspection.collectAsStateWithLifecycle()
  val context = LocalContext.current
  val requestPermissions = rememberPermissionRequest()
  val bluetoothRequest = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {}
  var sheetOpen by rememberSaveable(openSource) { mutableStateOf(false) }
  var helpOpen by rememberSaveable(openSource) { mutableStateOf(false) }
  var showWifiDialog by rememberSaveable { mutableStateOf(false) }
  var showLocationDialog by rememberSaveable { mutableStateOf(false) }

  val openStatus = openSource?.let { state[it].status }
  LaunchedEffect(openStatus) {
    if (openStatus != null && openStatus != SourceStatus.OK) viewModel.closeSource()
  }

  fun enableBluetooth() {
    try {
      bluetoothRequest.launch(Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE))
    } catch (_: ActivityNotFoundException) {
      context.startFirstAvailable(Intent(Settings.ACTION_BLUETOOTH_SETTINGS))
    } catch (_: SecurityException) {
      context.startFirstAvailable(Intent(Settings.ACTION_BLUETOOTH_SETTINGS))
    }
  }

  fun onCardClick(source: Source) {
    when (state[source].status) {
      SourceStatus.OK -> viewModel.toggleSource(source)
      SourceStatus.NO_PERMISSION -> requestPermissions(source.permissions)
      SourceStatus.RADIO_OFF if source == Source.WIFI -> showWifiDialog = true
      SourceStatus.RADIO_OFF if source == Source.BLUETOOTH -> enableBluetooth()
      SourceStatus.RADIO_OFF -> context.openWirelessSettings()
      SourceStatus.LOCATION_OFF -> showLocationDialog = true
      SourceStatus.NO_SIM, SourceStatus.UNSUPPORTED -> Unit
    }
  }

  Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
    LazyColumn(
      Modifier.fillMaxSize().safeDrawingPadding(),
      contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
      horizontalAlignment = Alignment.CenterHorizontally
    ) {
      item(key = "top-bar", contentType = "topBar") {
        TopBar(onOpenSettings)
      }
      item(key = "gauge", contentType = "gauge") {
        ExposureGauge(state.exposure.totalDbm, Modifier.padding(vertical = 24.dp))
      }
      item(key = "cards", contentType = "cards") {
        Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
          Source.entries.chunked(2).forEach { pair ->
            Row(Modifier.height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
              pair.forEach { source ->
                SourceCard(
                  source = source,
                  summary = state.summary(source),
                  isOpen = openSource == source,
                  onClick = { onCardClick(source) },
                  modifier = Modifier.weight(1f).fillMaxHeight()
                )
              }
            }
          }
        }
      }
      val locationOff = state.cellular.status == SourceStatus.LOCATION_OFF
      if (locationOff) {
        item(key = "location", contentType = "location") {
          LocationButton(onClick = { showLocationDialog = true })
        }
      }
      when (openSource.takeUnless { locationOff }) {
        Source.CELLULAR -> {
          cellularList(
            cellularRows,
            expandedKey,
            onToggle = viewModel::toggleRow,
            modemLabel = state.cellular.label,
            onHelp = { helpOpen = true }
          )
        }

        Source.WIFI -> {
          wifiList(
            wifiRows,
            wifiFilters,
            state.wifiThrottled,
            expandedKey,
            onToggle = viewModel::toggleRow,
            onOpenFilters = { sheetOpen = true },
            onOpenRecon = onOpenRecon,
            onOpenHelp = { helpOpen = true }
          )
        }

        Source.BLUETOOTH -> {
          bluetoothList(
            bluetoothRows,
            bluetoothFilters,
            expandedKey,
            onToggle = viewModel::toggleRow,
            onOpenFilters = { sheetOpen = true },
            inspection = inspection,
            onInspect = viewModel::inspect
          )
        }

        Source.GNSS -> {
          gnssList(gnssRows, expandedKey, onToggle = viewModel::toggleRow)
        }

        null -> {}
      }
    }
  }

  if (sheetOpen) {
    when (openSource) {
      Source.WIFI -> {
        WifiFilterSheet(
          filters = wifiFilters,
          onFiltersChange = viewModel::setWifiFilters,
          showStale = settings.showStaleWifi,
          onShowStaleChange = viewModel::setShowStaleWifi,
          onDismiss = { sheetOpen = false }
        )
      }

      Source.BLUETOOTH -> {
        BluetoothFilterSheet(
          filters = bluetoothFilters,
          onFiltersChange = viewModel::setBluetoothFilters,
          showStale = settings.showStaleBluetooth,
          onShowStaleChange = viewModel::setShowStaleBluetooth,
          onDismiss = { sheetOpen = false }
        )
      }

      Source.CELLULAR, Source.GNSS, null -> {}
    }
  }

  if (helpOpen) {
    when (openSource) {
      Source.CELLULAR -> {
        MessageDialog("Cellular monitoring", CELLULAR_HELP, onDismiss = { helpOpen = false })
      }

      Source.WIFI -> {
        MessageDialog("Wi-Fi scan throttling", WIFI_THROTTLE_HELP, onDismiss = { helpOpen = false })
      }

      Source.BLUETOOTH, Source.GNSS, null -> {}
    }
  }

  if (showWifiDialog) {
    MessageDialog(
      title = "Enable Wi-Fi?",
      text = "Spectre needs Wi-Fi turned on to scan for access points.\n\nTap Enable to open the Wi-Fi panel.",
      onDismiss = { showWifiDialog = false },
      confirmLabel = "Enable",
      onConfirm = {
        showWifiDialog = false
        if (!context.startFirstAvailable(Intent(Settings.Panel.ACTION_INTERNET_CONNECTIVITY), Intent(Settings.Panel.ACTION_WIFI))) {
          context.openWirelessSettings()
        }
      },
      dismissLabel = "Cancel"
    )
  }

  if (showLocationDialog) {
    MessageDialog(
      title = "Enable Location",
      text = "Android requires precise location enabled for apps to scan cellular, Wi-Fi, Bluetooth, and GNSS.",
      onDismiss = { showLocationDialog = false },
      confirmLabel = "Open Settings",
      onConfirm = {
        showLocationDialog = false
        context.startFirstAvailable(Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
      },
      dismissLabel = "Cancel"
    )
  }
}

@Composable
private fun TopBar(onOpenSettings: () -> Unit) {
  Row(Modifier.fillMaxWidth().height(56.dp), verticalAlignment = Alignment.CenterVertically) {
    Icon(
      ImageVector.vectorResource(R.drawable.ic_logo),
      contentDescription = null,
      modifier = Modifier.padding(start = 11.7.dp, end = 10.7.dp).size(18.6.dp, 23.1.dp)
    )
    Text(
      "Spectre",
      Modifier.weight(1f).semantics { heading() },
      style = MaterialTheme.typography.headlineSmall,
      fontWeight = FontWeight.SemiBold
    )
    IconButton(onClick = onOpenSettings) {
      Icon(Icons.Rounded.Settings, contentDescription = "Settings")
    }
  }
}

@Composable
private fun LocationButton(onClick: () -> Unit) {
  Button(onClick = onClick, modifier = Modifier.padding(top = 16.dp).fillMaxWidth()) {
    Icon(Icons.Rounded.LocationOn, contentDescription = null, modifier = Modifier.size(18.dp))
    Spacer(Modifier.width(8.dp))
    Text("Enable Location")
  }
}

private fun Context.openWirelessSettings() {
  startFirstAvailable(Intent(Settings.ACTION_WIRELESS_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
}
