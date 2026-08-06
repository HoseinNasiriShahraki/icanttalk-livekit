package com.icanttalk.android.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val DarkColors = darkColorScheme(
    primary = Color(0xFF5865F2),
    onPrimary = Color.White,
    primaryContainer = Color(0xFF404EED),
    secondary = Color(0xFF23A559),
    error = Color(0xFFF23F42),
    background = Color(0xFF1E1F22),
    surface = Color(0xFF2B2D31),
    surfaceVariant = Color(0xFF313338),
    onBackground = Color(0xFFF2F3F5),
    onSurface = Color(0xFFF2F3F5),
    onSurfaceVariant = Color(0xFFB5BAC1),
    outline = Color(0xFF4E5058),
)

@Composable
fun ICANTtalkTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = DarkColors,
        content = content,
    )
}
