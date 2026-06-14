package dev.thomasbuilds.spectre.ui.components

import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.OpenInNew
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import dev.thomasbuilds.spectre.R

private const val GITHUB_REPO_URL = "https://github.com/thomasbuilds/Spectre"
private const val BTC_ADDRESS = "bc1qphgd8leqsf06qlm6jjxpuw2rtq9f9hdjnhfluh"
private const val XMR_ADDRESS = "85fzziWM1pH77HWHNhE6aAN9uaHrLL7CMFA72rVecmMDR1fUjfv9YmS6GGeiV3hDEn7e9d8v4hfMSRmmEp171fpR4nipNfZ"

@Composable
fun GithubRepoRow() {
  val context = LocalContext.current
  Row(
    modifier =
      Modifier
        .fillMaxWidth()
        .clickable { openUrl(context, GITHUB_REPO_URL) }
        .heightIn(min = 56.dp)
        .padding(horizontal = 16.dp, vertical = 10.dp),
    horizontalArrangement = Arrangement.SpaceBetween,
    verticalAlignment = Alignment.CenterVertically
  ) {
    Row(verticalAlignment = Alignment.CenterVertically) {
      Icon(
        imageVector = ImageVector.vectorResource(R.drawable.ic_github),
        contentDescription = null,
        tint = MaterialTheme.colorScheme.onSurface,
        modifier = Modifier.size(20.dp)
      )
      Spacer(Modifier.width(12.dp))
      Text(
        "GitHub repo",
        style = MaterialTheme.typography.bodyLarge,
        color = MaterialTheme.colorScheme.onSurface
      )
    }
    Icon(
      imageVector = Icons.AutoMirrored.Rounded.OpenInNew,
      contentDescription = "Opens in browser",
      tint = MaterialTheme.colorScheme.onSurfaceVariant,
      modifier = Modifier.size(18.dp)
    )
  }
}

@Composable
fun SponsorSectionContent() {
  val context = LocalContext.current
  Text(
    "Spectre is free, open-source, and ad-free. If you find it useful, you can support the project and my broader open source work.",
    style = MaterialTheme.typography.bodySmall,
    color = MaterialTheme.colorScheme.onSurfaceVariant,
    modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp)
  )
  Column(
    modifier =
      Modifier.padding(
        start = 16.dp,
        end = 16.dp,
        top = 4.dp,
        bottom = 10.dp
      )
  ) {
    SponsorTile(
      label = "Bitcoin",
      caption = "tap to copy",
      value = BTC_ADDRESS,
      onTap = {
        copyToClipboard(context, "Bitcoin address", BTC_ADDRESS)
        Toast.makeText(context, "Bitcoin address copied", Toast.LENGTH_SHORT).show()
      }
    )
    Spacer(Modifier.size(8.dp))
    SponsorTile(
      label = "Monero",
      caption = "tap to copy",
      value = XMR_ADDRESS,
      onTap = {
        copyToClipboard(context, "Monero address", XMR_ADDRESS)
        Toast.makeText(context, "Monero address copied", Toast.LENGTH_SHORT).show()
      }
    )
  }
}

@Composable
private fun SponsorTile(
  label: String,
  caption: String,
  value: String,
  onTap: () -> Unit
) {
  Column(
    modifier =
      Modifier
        .fillMaxWidth()
        .background(
          MaterialTheme.colorScheme.surfaceVariant,
          RoundedCornerShape(8.dp)
        ).clickable(onClick = onTap)
        .padding(horizontal = 12.dp, vertical = 10.dp)
  ) {
    Row(verticalAlignment = Alignment.CenterVertically) {
      Text(
        label,
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurface
      )
      Text(
        "  · $caption",
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant
      )
    }
    Text(
      value,
      style = MaterialTheme.typography.labelSmall.copy(fontFamily = FontFamily.Monospace),
      color = MaterialTheme.colorScheme.onSurface,
      modifier = Modifier.padding(top = 4.dp)
    )
  }
}

private fun openUrl(
  context: Context,
  url: String
) {
  runCatching {
    context.startActivity(
      Intent(Intent.ACTION_VIEW, url.toUri())
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    )
  }
}
