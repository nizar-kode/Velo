package com.velo.remote.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable

private val DarkColorScheme = darkColorScheme(
    primary = CyanPrimary,
    onPrimary = BgDark,
    primaryContainer = SurfaceLighter,
    onPrimaryContainer = TextPrimary,
    secondary = EmeraldAccent,
    background = BgDark,
    onBackground = TextPrimary,
    surface = SurfaceDark,
    onSurface = TextPrimary,
    error = RoseError,
    onError = TextPrimary
)

@Composable
fun VeloTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = DarkColorScheme,
        content = content
    )
}

// Backward-compatibility alias
@Composable
fun AirPilotTheme(content: @Composable () -> Unit) = VeloTheme(content)
