package dev.thomasbuilds.spectre.ui.components

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context

internal fun copyToClipboard(
  context: Context,
  label: String,
  text: String
) {
  val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager ?: return
  cm.setPrimaryClip(ClipData.newPlainText(label, text))
}
