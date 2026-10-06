package com.ownervortex.nyxrecorder.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

val NixBackground = Color(0xFF0D0F12)
val NixSurface = Color(0xFF151726)
val NixSurfaceHigh = Color(0xFF1C1F33)
val NixStroke = Color(0xFF2A2D45)
val NixPrimary = Color(0xFF6C63FF)
val NixPrimaryDim = Color(0xFF4B45B8)
val NixAccent = Color(0xFF00D9A6)
val NixRed = Color(0xFFFF3B5C)
val NixAmber = Color(0xFFFFB020)
val NixText = Color(0xFFF2F2F7)
val NixTextDim = Color(0xFF9A9CB0)

private val NixColors = darkColorScheme(
    primary = NixPrimary,
    onPrimary = Color.White,
    secondary = NixAccent,
    onSecondary = Color.Black,
    background = NixBackground,
    onBackground = NixText,
    surface = NixSurface,
    onSurface = NixText,
    surfaceVariant = NixSurfaceHigh,
    onSurfaceVariant = NixTextDim,
    outline = NixStroke,
    error = NixRed,
    onError = Color.White
)

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
    MaterialTheme(
        colorScheme = NixColors,
        typography = NixTypography,
        content = content
    )
}
