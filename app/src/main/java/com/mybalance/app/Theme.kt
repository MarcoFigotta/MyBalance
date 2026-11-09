package com.mybalance.app

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

data class Palette(
    val dark: Boolean,
    val bg: Color,
    val surface: Color,
    val text: Color,
    val muted: Color,
    val track: Color,
    val outline: Color,
    val accent: Color,
    val onAccent: Color,
    val positive: Color,
    val negative: Color,
    val bar: Color,
    val onBar: Color
)

val LightPalette = Palette(
    dark = false,
    bg = Color(0xFFFFFFFF),
    surface = Color(0xFFF2F5FA),
    text = Color(0xFF0F172A),
    muted = Color(0xFF64748B),
    track = Color(0xFFE6ECF5),
    outline = Color(0xFFCBD5E1),
    accent = Color(0xFF0E8F83),
    onAccent = Color(0xFFFFFFFF),
    positive = Color(0xFF15803D),
    negative = Color(0xFFDC2626),
    bar = Color(0xFF1B2538),
    onBar = Color(0xFFFFFFFF)
)

val DarkPalette = Palette(
    dark = true,
    bg = Color(0xFF0B1220),
    surface = Color(0xFF151F33),
    text = Color(0xFFE8EDF7),
    muted = Color(0xFF94A3B8),
    track = Color(0xFF223049),
    outline = Color(0xFF3B4A63),
    accent = Color(0xFF2DD4BF),
    onAccent = Color(0xFF04201C),
    positive = Color(0xFF4ADE80),
    negative = Color(0xFFF87171),
    bar = Color(0xFF1C2940),
    onBar = Color(0xFFFFFFFF)
)

/** Colore di evidenza per la voce selezionata sulla barra scura in basso. */
val BarAccent = Color(0xFF2DD4BF)

val LocalPalette = staticCompositionLocalOf { LightPalette }

@Composable
fun MyBalanceTheme(dark: Boolean, content: @Composable () -> Unit) {
    val p = if (dark) DarkPalette else LightPalette
    val base = if (dark) darkColorScheme() else lightColorScheme()
    val scheme = base.copy(
        primary = p.accent,
        onPrimary = p.onAccent,
        background = p.bg,
        onBackground = p.text,
        surface = p.bg,
        onSurface = p.text,
        surfaceVariant = p.surface,
        onSurfaceVariant = p.muted,
        surfaceContainer = p.surface,
        surfaceContainerHigh = p.surface,
        surfaceContainerHighest = p.surface,
        outline = p.outline,
        outlineVariant = p.track,
        error = p.negative
    )
    CompositionLocalProvider(LocalPalette provides p) {
        MaterialTheme(colorScheme = scheme, content = content)
    }
}
