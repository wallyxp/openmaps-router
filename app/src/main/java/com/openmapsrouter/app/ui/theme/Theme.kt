package com.openmapsrouter.app.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable

private val LightColorScheme = lightColorScheme(
    primary = OrganicGreen,
    onPrimary = SurfaceWhite,
    primaryContainer = OrganicGreenLight,
    onPrimaryContainer = OrganicGreenDark,
    background = Background,
    surface = SurfaceWhite,
    onBackground = TextPrimary,
    onSurface = TextPrimary
)

@Composable
fun OpenMapsRouterTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = LightColorScheme,
        content = content
    )
}
