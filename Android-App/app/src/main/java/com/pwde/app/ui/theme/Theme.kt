package com.pwde.app.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.pwde.app.data.prefs.ColorSchemeOption
import com.pwde.app.data.prefs.LayoutMode
import com.pwde.app.data.prefs.TextSizeOption

/** Spacing tokens. Compact tightens them; Easy reach keeps standard spacing but bottom-aligns content. */
@Immutable
data class PwdeSpacing(
    val screenMargin: Dp,
    val section: Dp,
    val internal: Dp,
    val itemGap: Dp,
    val layoutMode: LayoutMode,
) {
    val easyReach: Boolean get() = layoutMode == LayoutMode.EASY_REACH
}

private fun spacingFor(mode: LayoutMode) = when (mode) {
    LayoutMode.COMPACT -> PwdeSpacing(screenMargin = 12.dp, section = 16.dp, internal = 10.dp, itemGap = 8.dp, layoutMode = mode)
    else -> PwdeSpacing(screenMargin = 20.dp, section = 24.dp, internal = 16.dp, itemGap = 12.dp, layoutMode = mode)
}

object PwdeShapes {
    val card = RoundedCornerShape(20.dp)
    val screen = RoundedCornerShape(35.dp)
    val button = RoundedCornerShape(12.dp)
    val field = RoundedCornerShape(7.dp)
    val pill = RoundedCornerShape(50)
}

/** Accessibility-critical minimum touch target. */
val MinTouchTarget = 48.dp

private val LocalPwdeColors = staticCompositionLocalOf { colorsFor(ColorSchemeOption.DEFAULT) }
private val LocalPwdeSpacing = staticCompositionLocalOf { spacingFor(LayoutMode.STANDARD) }
private val LocalBaseDensity = staticCompositionLocalOf<Density?> { null }

object PwdeTheme {
    val colors: PwdeColors
        @Composable @ReadOnlyComposable get() = LocalPwdeColors.current
    val spacing: PwdeSpacing
        @Composable @ReadOnlyComposable get() = LocalPwdeSpacing.current
}

/**
 * App-wide theme driven by the user's settings. Text size is applied by scaling the font scale in
 * [LocalDensity], so every `sp` value in the app follows it (on top of the system font scale).
 */
@Composable
fun PwdeTheme(
    colorScheme: ColorSchemeOption = ColorSchemeOption.DEFAULT,
    textSize: TextSizeOption = TextSizeOption.MEDIUM,
    layoutMode: LayoutMode = LayoutMode.STANDARD,
    content: @Composable () -> Unit,
) {
    val colors = colorsFor(colorScheme)
    val material = if (colors.isDark) {
        darkColorScheme(
            primary = colors.primary, onPrimary = colors.onAccent,
            secondary = colors.secondary, onSecondary = colors.onAccent,
            background = colors.background, onBackground = colors.text,
            surface = colors.surface, onSurface = colors.text,
            surfaceVariant = colors.surfaceMuted, onSurfaceVariant = colors.textMuted,
            outline = colors.border, error = colors.danger,
        )
    } else {
        lightColorScheme(
            primary = colors.primary, onPrimary = colors.onAccent,
            secondary = colors.secondary, onSecondary = colors.onAccent,
            background = colors.background, onBackground = colors.text,
            surface = colors.surface, onSurface = colors.text,
            surfaceVariant = colors.surfaceMuted, onSurfaceVariant = colors.textMuted,
            outline = colors.border, error = colors.danger,
        )
    }
    // Scale from the system density, not an outer PwdeTheme's, so nested themes don't compound.
    val baseDensity = LocalBaseDensity.current ?: LocalDensity.current
    val scaledDensity = Density(baseDensity.density, baseDensity.fontScale * textSize.scale)

    CompositionLocalProvider(
        LocalPwdeColors provides colors,
        LocalPwdeSpacing provides spacingFor(layoutMode),
        LocalBaseDensity provides baseDensity,
        LocalDensity provides scaledDensity,
    ) {
        MaterialTheme(colorScheme = material, typography = PwdeTypography, content = content)
    }
}
