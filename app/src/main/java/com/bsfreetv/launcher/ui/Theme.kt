package com.bsfreetv.launcher.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.sp
import com.bsfreetv.launcher.domain.palette.LauncherPalette
import com.bsfreetv.launcher.domain.palette.Rgb

/** Compose view of [LauncherPalette]. The screen base is always [Black]. */
@Immutable
data class BsfColors(
    val accent: Color,
    val accentHover: Color,
    val onAccent: Color,
    val surface: Color,
    val surfaceHigh: Color,
    val border: Color,
    val text: Color,
    val textMuted: Color,
) {
    companion object {
        val Black = Color(0xFF000000)
    }
}

private fun Rgb.color() = Color(argb)

fun LauncherPalette.toColors() = BsfColors(
    accent = accent.color(),
    accentHover = accentHover.color(),
    onAccent = onAccent.color(),
    surface = surface.color(),
    surfaceHigh = surfaceHigh.color(),
    border = border.color(),
    text = text.color(),
    textMuted = textMuted.color(),
)

val LocalBsfColors = staticCompositionLocalOf { LauncherPalette.Neutral.toColors() }

/** Sizes read from 3 m: the Shield draws 960x540 dp, so 1 sp is 2 px. */
object BsfType {
    private val family = FontFamily.SansSerif
    /** Text drawn straight on the wallpaper gets a soft shadow so busy images stay readable. */
    private val onImage = Shadow(Color.Black.copy(alpha = 0.6f), Offset(0f, 2f), 8f)
    val hero = TextStyle(fontFamily = family, fontSize = 34.sp, fontWeight = FontWeight.SemiBold, letterSpacing = (-0.5).sp, shadow = onImage)
    val heroSub = TextStyle(fontFamily = family, fontSize = 15.sp, fontWeight = FontWeight.Normal, shadow = onImage)
    val title = TextStyle(fontFamily = family, fontSize = 22.sp, fontWeight = FontWeight.SemiBold)
    val body = TextStyle(fontFamily = family, fontSize = 15.sp, fontWeight = FontWeight.Normal)
    val bodyStrong = TextStyle(fontFamily = family, fontSize = 15.sp, fontWeight = FontWeight.Medium)
    val label = TextStyle(fontFamily = family, fontSize = 12.sp, fontWeight = FontWeight.Medium, letterSpacing = 0.4.sp)
    val overline = TextStyle(fontFamily = family, fontSize = 11.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 1.6.sp)
    val clock = TextStyle(fontFamily = family, fontSize = 22.sp, fontWeight = FontWeight.Light, letterSpacing = 0.5.sp, shadow = onImage)
    val clockDate = TextStyle(fontFamily = family, fontSize = 12.sp, fontWeight = FontWeight.Medium, letterSpacing = 0.4.sp, shadow = onImage)
    val overlineOnImage = TextStyle(fontFamily = family, fontSize = 11.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 1.6.sp, shadow = onImage)
    val mono = TextStyle(fontFamily = FontFamily.Monospace, fontSize = 12.sp)
}

/**
 * Animates between palettes when the wallpaper changes, and sets the default
 * content colour: there is no Surface at the root, so un-coloured Text would
 * otherwise fall back to black-on-black.
 */
@Composable
fun BsfTheme(palette: LauncherPalette, animate: Boolean = true, content: @Composable () -> Unit) {
    val target = palette.toColors()
    val spec = tween<Color>(if (animate) 700 else 0)
    val colors = BsfColors(
        accent = animateColorAsState(target.accent, spec, label = "accent").value,
        accentHover = animateColorAsState(target.accentHover, spec, label = "accentHover").value,
        onAccent = animateColorAsState(target.onAccent, spec, label = "onAccent").value,
        surface = animateColorAsState(target.surface, spec, label = "surface").value,
        surfaceHigh = animateColorAsState(target.surfaceHigh, spec, label = "surfaceHigh").value,
        border = animateColorAsState(target.border, spec, label = "border").value,
        text = target.text,
        textMuted = target.textMuted,
    )
    val scheme = darkColorScheme(
        primary = colors.accent,
        onPrimary = colors.onAccent,
        background = BsfColors.Black,
        surface = colors.surface,
        onSurface = colors.text,
        onBackground = colors.text,
    )
    MaterialTheme(colorScheme = scheme, typography = Typography()) {
        CompositionLocalProvider(LocalBsfColors provides colors, LocalContentColor provides colors.text, content = content)
    }
}
