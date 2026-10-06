package com.ownervortex.nyxrecorder.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

/** One app theme: a full set of UI colors. */
data class NixPalette(
    val primary: Color,
    val primaryDim: Color,
    val accent: Color,
    val background: Color,
    val surface: Color,
    val surfaceHigh: Color,
    val stroke: Color,
    val red: Color,
    val amber: Color,
    val text: Color,
    val textDim: Color
)

val THEME_NAMES = listOf("Nyx Dark", "AMOLED", "Cyber Cyan", "Neon Green", "Crimson")

private val palettes = listOf(
    // 0 — Nyx Dark (default brand purple)
    NixPalette(
        primary = Color(0xFF6C63FF),
        primaryDim = Color(0xFF4B45B8),
        accent = Color(0xFF00D9A6),
        background = Color(0xFF0D0F12),
        surface = Color(0xFF151726),
        surfaceHigh = Color(0xFF1C1F33),
        stroke = Color(0xFF2A2D45),
        red = Color(0xFFFF3B5C),
        amber = Color(0xFFFFB020),
        text = Color(0xFFF2F2F7),
        textDim = Color(0xFF9A9CB0)
    ),
    // 1 — AMOLED (true black)
    NixPalette(
        primary = Color(0xFF7C6CFF),
        primaryDim = Color(0xFF5A4FD6),
        accent = Color(0xFF00E5B0),
        background = Color(0xFF000000),
        surface = Color(0xFF0B0B10),
        surfaceHigh = Color(0xFF16161D),
        stroke = Color(0xFF26262F),
        red = Color(0xFFFF3B5C),
        amber = Color(0xFFFFB020),
        text = Color(0xFFF2F2F7),
        textDim = Color(0xFF8E8EA0)
    ),
    // 2 — Cyber Cyan
    NixPalette(
        primary = Color(0xFF00C8E0),
        primaryDim = Color(0xFF0090A8),
        accent = Color(0xFF7C4DFF),
        background = Color(0xFF060B12),
        surface = Color(0xFF0C1420),
        surfaceHigh = Color(0xFF121E2E),
        stroke = Color(0xFF1E2C40),
        red = Color(0xFFFF4D67),
        amber = Color(0xFFFFC24D),
        text = Color(0xFFEAF6FA),
        textDim = Color(0xFF8FA6B5)
    ),
    // 3 — Neon Green (dark emerald base, neon accents)
    NixPalette(
        primary = Color(0xFF00A86B),
        primaryDim = Color(0xFF00794B),
        accent = Color(0xFF39FF14),
        background = Color(0xFF050907),
        surface = Color(0xFF0B1410),
        surfaceHigh = Color(0xFF12201A),
        stroke = Color(0xFF1E332A),
        red = Color(0xFFFF4D5E),
        amber = Color(0xFFFFC24D),
        text = Color(0xFFECFFF5),
        textDim = Color(0xFF8FAFA2)
    ),
    // 4 — Crimson
    NixPalette(
        primary = Color(0xFFE53956),
        primaryDim = Color(0xFFB02740),
        accent = Color(0xFFFFC24D),
        background = Color(0xFF0F0A0C),
        surface = Color(0xFF1A1014),
        surfaceHigh = Color(0xFF26151B),
        stroke = Color(0xFF3B2129),
        red = Color(0xFFFF3B5C),
        amber = Color(0xFFFFB020),
        text = Color(0xFFF9F1F3),
        textDim = Color(0xFFB09AA1)
    )
)

private val currentPalette = mutableStateOf(palettes[0])

/** Applies a palette by index (see [THEME_NAMES]). Safe to call from anywhere. */
fun setNixPalette(index: Int) {
    currentPalette.value = palettes[index.coerceIn(0, palettes.lastIndex)]
}

fun nixPaletteIndex(): Int = palettes.indexOf(currentPalette.value)

/**
 * Theme colors are read through these accessors everywhere in the UI, so
 * switching [currentPalette] restyles the whole app live — no call sites need
 * to change and reads outside composition (services, bubble) work too.
 */
val NixPrimary: Color get() = currentPalette.value.primary
val NixPrimaryDim: Color get() = currentPalette.value.primaryDim
val NixAccent: Color get() = currentPalette.value.accent
val NixBackground: Color get() = currentPalette.value.background
val NixSurface: Color get() = currentPalette.value.surface
val NixSurfaceHigh: Color get() = currentPalette.value.surfaceHigh
val NixStroke: Color get() = currentPalette.value.stroke
val NixRed: Color get() = currentPalette.value.red
val NixAmber: Color get() = currentPalette.value.amber
val NixText: Color get() = currentPalette.value.text
val NixTextDim: Color get() = currentPalette.value.textDim

private val NixTypography = Typography(
    displaySmall = Typography().displaySmall.copy(fontWeight = FontWeight.Bold),
    headlineMedium = Typography().headlineMedium.copy(fontWeight = FontWeight.Bold),
    titleLarge = Typography().titleLarge.copy(fontWeight = FontWeight.SemiBold),
    titleMedium = Typography().titleMedium.copy(fontWeight = FontWeight.SemiBold),
    bodyLarge = Typography().bodyLarge.copy(fontSize = 16.sp),
    labelLarge = Typography().labelLarge.copy(fontWeight = FontWeight.SemiBold)
)

@Composable
fun NixTheme(content: @Composable () -> Unit) {
    // Always dark: a screen recorder should not flash light UI over recordings.
    @Suppress("UNUSED_EXPRESSION")
    isSystemInDarkTheme()
    val palette = currentPalette.value
    MaterialTheme(
        colorScheme = darkColorScheme(
            primary = palette.primary,
            onPrimary = Color.White,
            secondary = palette.accent,
            onSecondary = Color.Black,
            background = palette.background,
            onBackground = palette.text,
            surface = palette.surface,
            onSurface = palette.text,
            surfaceVariant = palette.surfaceHigh,
            onSurfaceVariant = palette.textDim,
            outline = palette.stroke,
            error = palette.red,
            onError = Color.White
        ),
        typography = NixTypography,
        content = content
    )
}
