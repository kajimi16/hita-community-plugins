package com.stupidtree.hita.ui.anime.ui

import androidx.compose.material.MaterialTheme
import androidx.compose.material.darkColors
import androidx.compose.material.lightColors
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

/** Animeko 的紫粉主色，但保持 Material 2 的低成本实现。 */
private val LightColors = lightColors(
    primary = Color(0xFF5B4B8A),
    primaryVariant = Color(0xFF3F3266),
    secondary = Color(0xFFFF6B9D),
    secondaryVariant = Color(0xFFC2406F),
    background = Color(0xFFF7F5FB),
    surface = Color.White,
    error = Color(0xFFB3261E),
)

private val DarkColors = darkColors(
    primary = Color(0xFFC9B8FF),
    primaryVariant = Color(0xFF9A84E8),
    secondary = Color(0xFFFF8FB4),
    secondaryVariant = Color(0xFFD65C8C),
    background = Color(0xFF121016),
    surface = Color(0xFF1C1922),
    error = Color(0xFFF2B8B5),
)

@Composable
fun AnimeTheme(dark: Boolean, content: @Composable () -> Unit) {
    MaterialTheme(
        colors = if (dark) DarkColors else LightColors,
        content = content,
    )
}
