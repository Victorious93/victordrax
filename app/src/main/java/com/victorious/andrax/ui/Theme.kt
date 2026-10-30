package com.victorious.andrax.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color

val NeonCyan = Color(0xFF00F0FF)
val NeonMagenta = Color(0xFFFF00E5)
val DeepBg = Color(0xFF0A0A12)
val NeonGradient = Brush.horizontalGradient(listOf(NeonCyan, NeonMagenta))

@Composable
fun FleetTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = darkColorScheme(
            primary = NeonCyan,
            secondary = NeonMagenta,
            background = DeepBg,
            surface = Color(0xFF14141F),
            onPrimary = Color.Black,
            onBackground = Color(0xFFE6E6F0),
            onSurface = Color(0xFFE6E6F0),
        ),
        content = content,
    )
}
