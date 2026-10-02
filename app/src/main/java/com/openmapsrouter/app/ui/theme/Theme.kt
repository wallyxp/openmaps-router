package com.openmapsrouter.app.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable

private val DarkColorScheme = darkColorScheme(
    primary = PureWhite,
    onPrimary = PureBlack,
    primaryContainer = DarkSurfaceElevated,
    onPrimaryContainer = PureWhite,
    secondary = EmeraldGreen,
    onSecondary = PureBlack,
    secondaryContainer = EmeraldGreenContainer,
    onSecondaryContainer = EmeraldGreen,
    background = DarkBackground,
    surface = DarkSurface,
    surfaceVariant = DarkSurfaceElevated,
    onBackground = TextPrimaryDark,
    onSurface = TextPrimaryDark,
    onSurfaceVariant = TextSecondaryDark,
    outline = DarkBorder,
    outlineVariant = DarkBorderSubtle
)

@Composable
fun OpenMapsRouterTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = DarkColorScheme,
        content = content
    )
}
