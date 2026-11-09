package com.zoomi.app.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

val ZoomiBrand = Color(0xFF5B4BFF)
val ZoomiAccent = Color(0xFFFFB020)

private val Light = lightColorScheme(
    primary = ZoomiBrand, onPrimary = Color.White,
    secondary = ZoomiAccent, onSecondary = Color(0xFF2E2B26),
    background = Color(0xFFF4F1EA), onBackground = Color(0xFF2E2B26),
    surface = Color.White, onSurface = Color(0xFF2E2B26),
    surfaceVariant = Color(0xFFEDE9F7), onSurfaceVariant = Color(0xFF4A4658),
)

private val Dark = darkColorScheme(
    primary = Color(0xFF8C80FF), onPrimary = Color(0xFF110A4D),
    secondary = ZoomiAccent, onSecondary = Color(0xFF2E2B26),
    background = Color(0xFF12161F), onBackground = Color(0xFFDCE2EC),
    surface = Color(0xFF1B212C), onSurface = Color(0xFFDCE2EC),
    surfaceVariant = Color(0xFF262D3A), onSurfaceVariant = Color(0xFFAEB6C4),
)

@Composable
fun ZoomiTheme(dark: Boolean, content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = if (dark) Dark else Light, content = content)
}
