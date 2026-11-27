package dev.thomasbuilds.spectre.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

internal val ExposureGreen = Color(0xFF22C55E)
internal val ExposureYellow = Color(0xFFEAB308)
internal val ExposureOrange = Color(0xFFF97316)
internal val ExposureRed = Color(0xFFEF4444)

private val DarkColors =
  darkColorScheme(
    primary = Color(0xFFE6E6E6),
    onPrimary = Color.Black,
    primaryContainer = Color(0xFF1F1F1F),
    onPrimaryContainer = Color(0xFFE6E6E6),
    inversePrimary = Color(0xFF111111),
    secondary = Color(0xFFE6E6E6),
    onSecondary = Color.Black,
    secondaryContainer = Color(0xFF1F1F1F),
    onSecondaryContainer = Color(0xFFE6E6E6),
    tertiary = Color(0xFFE6E6E6),
    onTertiary = Color.Black,
    tertiaryContainer = Color(0xFF1F1F1F),
    onTertiaryContainer = Color(0xFFE6E6E6),
    background = Color(0xFF0E0E0E),
    onBackground = Color(0xFFE6E6E6),
    surface = Color(0xFF131313),
    onSurface = Color(0xFFE6E6E6),
    surfaceVariant = Color(0xFF1F1F1F),
    onSurfaceVariant = Color(0xFF9E9E9E),
    surfaceTint = Color.Transparent,
    inverseSurface = Color(0xFFE6E6E6),
    inverseOnSurface = Color(0xFF131313),
    error = ExposureRed,
    onError = Color.Black,
    errorContainer = Color(0xFF1F1F1F),
    onErrorContainer = ExposureRed,
    outline = Color(0xFF6C6C6C),
    outlineVariant = Color(0xFF2A2A2A),
    scrim = Color.Black,
    surfaceBright = Color(0xFF1F1F1F),
    surfaceContainer = Color(0xFF131313),
    surfaceContainerHigh = Color(0xFF1F1F1F),
    surfaceContainerHighest = Color(0xFF1F1F1F),
    surfaceContainerLow = Color(0xFF131313),
    surfaceContainerLowest = Color(0xFF0E0E0E),
    surfaceDim = Color(0xFF0E0E0E),
    primaryFixed = Color(0xFFE6E6E6),
    primaryFixedDim = Color(0xFF9E9E9E),
    onPrimaryFixed = Color.Black,
    onPrimaryFixedVariant = Color(0xFF1F1F1F),
    secondaryFixed = Color(0xFFE6E6E6),
    secondaryFixedDim = Color(0xFF9E9E9E),
    onSecondaryFixed = Color.Black,
    onSecondaryFixedVariant = Color(0xFF1F1F1F),
    tertiaryFixed = Color(0xFFE6E6E6),
    tertiaryFixedDim = Color(0xFF9E9E9E),
    onTertiaryFixed = Color.Black,
    onTertiaryFixedVariant = Color(0xFF1F1F1F)
  )

private val LightColors =
  lightColorScheme(
    primary = Color(0xFF111111),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFECECEC),
    onPrimaryContainer = Color(0xFF111111),
    inversePrimary = Color(0xFFE6E6E6),
    secondary = Color(0xFF111111),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFECECEC),
    onSecondaryContainer = Color(0xFF111111),
    tertiary = Color(0xFF111111),
    onTertiary = Color.White,
    tertiaryContainer = Color(0xFFECECEC),
    onTertiaryContainer = Color(0xFF111111),
    background = Color(0xFFF1F1F1),
    onBackground = Color(0xFF111111),
    surface = Color(0xFFFCFCFC),
    onSurface = Color(0xFF111111),
    surfaceVariant = Color(0xFFECECEC),
    onSurfaceVariant = Color(0xFF555555),
    surfaceTint = Color.Transparent,
    inverseSurface = Color(0xFF111111),
    inverseOnSurface = Color(0xFFF1F1F1),
    error = ExposureRed,
    onError = Color.White,
    errorContainer = Color(0xFFECECEC),
    onErrorContainer = ExposureRed,
    outline = Color(0xFF888888),
    outlineVariant = Color(0xFFD4D4D4),
    scrim = Color.Black,
    surfaceBright = Color(0xFFFCFCFC),
    surfaceContainer = Color(0xFFFCFCFC),
    surfaceContainerHigh = Color(0xFFECECEC),
    surfaceContainerHighest = Color(0xFFECECEC),
    surfaceContainerLow = Color(0xFFFCFCFC),
    surfaceContainerLowest = Color.White,
    surfaceDim = Color(0xFFF1F1F1),
    primaryFixed = Color(0xFF111111),
    primaryFixedDim = Color(0xFF555555),
    onPrimaryFixed = Color.White,
    onPrimaryFixedVariant = Color(0xFFECECEC),
    secondaryFixed = Color(0xFF111111),
    secondaryFixedDim = Color(0xFF555555),
    onSecondaryFixed = Color.White,
    onSecondaryFixedVariant = Color(0xFFECECEC),
    tertiaryFixed = Color(0xFF111111),
    tertiaryFixedDim = Color(0xFF555555),
    onTertiaryFixed = Color.White,
    onTertiaryFixedVariant = Color(0xFFECECEC)
  )

@Composable
fun SpectreTheme(content: @Composable () -> Unit) =
  MaterialTheme(colorScheme = if (isSystemInDarkTheme()) DarkColors else LightColors, content = content)
