package com.victorious.victordrax.ui

import androidx.compose.animation.core.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.*
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import com.victorious.victordrax.data.AppTheme

data class Palette(val a: Color, val b: Color, val bg: Color, val surface: Color)

fun paletteFor(t: AppTheme) = when (t) {
    AppTheme.NEON -> Palette(Color(0xFF00F0FF), Color(0xFFFF00E5), Color(0xFF0A0A12), Color(0xFF14141F))
    AppTheme.SYNTHWAVE -> Palette(Color(0xFFFF6EC7), Color(0xFF7B2FFF), Color(0xFF120A1E), Color(0xFF1E1230))
    AppTheme.MATRIX -> Palette(Color(0xFF00FF66), Color(0xFF00B3FF), Color(0xFF050A06), Color(0xFF0E1A10))
    AppTheme.ICE -> Palette(Color(0xFF9BE7FF), Color(0xFF4D7CFF), Color(0xFF070B14), Color(0xFF111827))
}

// Kept for code that references the original fleet colours.
val NeonCyan = Color(0xFF00F0FF)
val NeonMagenta = Color(0xFFFF00E5)
val DeepBg = Color(0xFF0A0A12)

val LocalPalette = staticCompositionLocalOf { paletteFor(AppTheme.NEON) }

val NeonGradient: Brush @Composable get() = LocalPalette.current.let { Brush.horizontalGradient(listOf(it.a, it.b)) }

/** Slowly sweeping gradient used on dashboard cards. */
@Composable
fun animatedGradient(): Brush {
    val p = LocalPalette.current
    val t by rememberInfiniteTransition(label = "grad").animateFloat(
        0f, 1000f, infiniteRepeatable(tween(6000, easing = LinearEasing), RepeatMode.Reverse), label = "shift")
    return Brush.linearGradient(listOf(p.a.copy(alpha = .35f), p.b.copy(alpha = .35f), p.a.copy(alpha = .15f)),
        start = Offset(t, 0f), end = Offset(t + 600f, 600f))
}

@Composable
fun FleetTheme(theme: AppTheme = AppTheme.NEON, content: @Composable () -> Unit) {
    val p = paletteFor(theme)
    CompositionLocalProvider(LocalPalette provides p) {
        MaterialTheme(
            colorScheme = darkColorScheme(
                primary = p.a, secondary = p.b, tertiary = p.b,
                background = p.bg, surface = p.surface, surfaceVariant = p.surface,
                onPrimary = Color.Black, onSecondary = Color.Black,
                onBackground = Color(0xFFE6E6F0), onSurface = Color(0xFFE6E6F0),
            ),
            content = content,
        )
    }
}
