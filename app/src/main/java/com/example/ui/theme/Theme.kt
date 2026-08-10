package com.example.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable

private val PureLockColorScheme = darkColorScheme(
    primary = PrimaryCyan,
    onPrimary = PureDarkBackground,
    primaryContainer = PrimaryCyanGlow,
    onPrimaryContainer = TextPrimary,
    secondary = SecondaryEmerald,
    onSecondary = PureDarkBackground,
    background = PureDarkBackground,
    onBackground = TextPrimary,
    surface = PureDarkSurface,
    onSurface = TextPrimary,
    surfaceVariant = PureDarkSurfaceVariant,
    onSurfaceVariant = TextSecondary,
    outline = PureDarkBorder,
    error = AlertRose,
    onError = TextPrimary,
    errorContainer = AlertRoseDark
)

@Composable
fun PureLockTheme(
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = PureLockColorScheme,
        typography = Typography,
        content = content
    )
}
