package dev.thomasbuilds.spectre.ui.kit

import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.widget.Toast
import androidx.activity.compose.LocalActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import dev.thomasbuilds.spectre.core.hasPermission

internal fun Context.copyToClipboard(
  label: String,
  text: String
) {
  checkNotNull(getSystemService(ClipboardManager::class.java)).setPrimaryClip(ClipData.newPlainText(label, text))
  if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
    Toast.makeText(this, "$label copied", Toast.LENGTH_SHORT).show()
  }
}

internal fun Context.startFirstAvailable(vararg intents: Intent): Boolean =
  intents.any { intent ->
    try {
      startActivity(intent)
      true
    } catch (_: ActivityNotFoundException) {
      false
    }
  }

private fun Context.openAppSettings() {
  startFirstAvailable(
    Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", packageName, null))
      .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
  )
}

@Composable
internal fun rememberPermissionRequest(onGranted: () -> Unit = {}): (List<String>) -> Unit {
  val activity = checkNotNull(LocalActivity.current)
  val currentOnGranted by rememberUpdatedState(onGranted)
  var rationaleBefore by rememberSaveable { mutableStateOf(emptySet<String>()) }
  val launcher =
    rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { results ->
      val denied = results.filterValues { !it }.keys
      when {
        results.isNotEmpty() && denied.isEmpty() -> currentOnGranted()
        denied.any { it !in rationaleBefore && !activity.shouldShowRequestPermissionRationale(it) } -> activity.openAppSettings()
      }
    }
  return remember(activity, launcher) {
    { permissions ->
      val missing = permissions.filterNot(activity::hasPermission)
      if (missing.isEmpty()) {
        currentOnGranted()
      } else {
        rationaleBefore = missing.filter(activity::shouldShowRequestPermissionRationale).toSet()
        launcher.launch(missing.toTypedArray())
      }
    }
  }
}
