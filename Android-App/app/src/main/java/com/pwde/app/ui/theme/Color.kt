package com.pwde.app.ui.theme

import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import com.pwde.app.data.prefs.ColorSchemeOption

/** PWDe palette. One instance per [ColorSchemeOption]; read via `PwdeTheme.colors`. */
@Immutable
data class PwdeColors(
    val isDark: Boolean,
    val background: Color,
    val surface: Color,
    val surfaceMuted: Color,
    val cardStart: Color,
    val cardEnd: Color,
    val navStart: Color,
    val navEnd: Color,
    val primary: Color,
    val secondary: Color,
    val onAccent: Color,
    val text: Color,
    val textMuted: Color,
    val textAlt: Color,
    val border: Color,
    val warning: Color,
    val danger: Color,
    val success: Color,
) {
    val cardBrush: Brush get() = Brush.linearGradient(listOf(cardStart, cardEnd))
    val navBrush: Brush get() = Brush.verticalGradient(listOf(navStart, navEnd))
    val buttonBrush: Brush get() = Brush.horizontalGradient(listOf(primary, secondary))
    val borderBrush: Brush get() = Brush.linearGradient(listOf(primary, secondary))
}

private val DefaultColors = PwdeColors(
    isDark = true,
    background = Color(0xFF15172B),
    surface = Color(0xFF1E2140),
    surfaceMuted = Color(0xFF272A4D),
    cardStart = Color(0xFF38269F),
    cardEnd = Color(0xFF150F3C),
    navStart = Color(0xFF160E26),
    navEnd = Color(0xFF51348C),
    primary = Color(0xFF9BEEE2),
    secondary = Color(0xFFCF87FB),
    onAccent = Color(0xFF15172B),
    text = Color.White,
    textMuted = Color(0xFFC5C8E0),
    textAlt = Color(0xFFCF87FB),
    border = Color(0xFF9BEEE2),
    warning = Color(0xFFFFC764),
    danger = Color(0xFFFF7A7A),
    success = Color(0xFF9BEEE2),
)

private val ContrastColors = PwdeColors(
    isDark = true,
    background = Color.Black,
    surface = Color(0xFF0D0D0D),
    surfaceMuted = Color(0xFF1A1A1A),
    cardStart = Color(0xFF141414),
    cardEnd = Color.Black,
    navStart = Color.Black,
    navEnd = Color(0xFF141414),
    primary = Color(0xFF6CFFEB),
    secondary = Color(0xFFFFE14D),
    onAccent = Color.Black,
    text = Color.White,
    textMuted = Color.White,
    textAlt = Color(0xFFFFE14D),
    border = Color.White,
    warning = Color(0xFFFFE14D),
    danger = Color(0xFFFF6B6B),
    success = Color(0xFF6CFFEB),
)

private val LightColors = PwdeColors(
    isDark = false,
    background = Color(0xFFF4F2FC),
    surface = Color.White,
    surfaceMuted = Color(0xFFE8E4F7),
    cardStart = Color(0xFFE9E1FF),
    cardEnd = Color.White,
    navStart = Color(0xFFFFFFFF),
    navEnd = Color(0xFFE3DAFB),
    primary = Color(0xFF0A7468),
    secondary = Color(0xFF7B2FBF),
    onAccent = Color.White,
    text = Color(0xFF15172B),
    textMuted = Color(0xFF454962),
    textAlt = Color(0xFF7B2FBF),
    border = Color(0xFF0A7468),
    warning = Color(0xFF8A5A00),
    danger = Color(0xFFB3261E),
    success = Color(0xFF0A7468),
)

/** Blue / orange pairing that stays distinguishable for the common colour-vision deficiencies. */
private val ColorSafeColors = PwdeColors(
    isDark = true,
    background = Color(0xFF14182B),
    surface = Color(0xFF1C2238),
    surfaceMuted = Color(0xFF262D48),
    cardStart = Color(0xFF1F3A6B),
    cardEnd = Color(0xFF0F1A33),
    navStart = Color(0xFF0F1A33),
    navEnd = Color(0xFF1F3A6B),
    primary = Color(0xFF56B4E9),
    secondary = Color(0xFFE69F00),
    onAccent = Color(0xFF14182B),
    text = Color.White,
    textMuted = Color(0xFFCBD3E6),
    textAlt = Color(0xFFE69F00),
    border = Color(0xFF56B4E9),
    warning = Color(0xFFE69F00),
    danger = Color(0xFFF0E442),
    success = Color(0xFF56B4E9),
)

fun colorsFor(option: ColorSchemeOption): PwdeColors = when (option) {
    ColorSchemeOption.DEFAULT -> DefaultColors
    ColorSchemeOption.CONTRAST -> ContrastColors
    ColorSchemeOption.LIGHT -> LightColors
    ColorSchemeOption.COLOR_SAFE -> ColorSafeColors
}
