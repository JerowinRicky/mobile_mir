package com.antigravity.remotedevice.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable

private val DarkColorScheme = darkColorScheme(
    primary = BluePrimary,
    secondary = EmeraldAccent,
    background = Slate900,
    surface = Slate800,
    error = RedDanger,
    onPrimary = White,
    onBackground = Slate100,
    onSurface = Slate100
)

@Composable
fun RemoteDeviceTheme(
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = DarkColorScheme,
        typography = Typography,
        content = content
    )
}
