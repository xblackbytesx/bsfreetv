package com.bsfreetv.launcher.domain.palette

import kotlin.math.abs

/**
 * The launcher's colour roles. The base is always true black (OLED); the
 * wallpaper only tints the accent and the near-black surfaces.
 */
data class LauncherPalette(
    val accent: Rgb,
    val accentHover: Rgb,
    val onAccent: Rgb,
    /** Panels over the wallpaper (settings, menus); drawn at high alpha. */
    val surface: Rgb,
    /** Raised/focused rows inside a panel. */
    val surfaceHigh: Rgb,
    val border: Rgb,
    val text: Rgb,
    val textMuted: Rgb,
) {
    companion object {
        /** Impasto's NEUTRAL_ACCENT. */
        val NeutralAccent = Rgb.hex("#89B4FA")
        val Neutral: LauncherPalette = PaletteBuilder.fromAccent(NeutralAccent)
    }
}

/** A fixed palette offered next to Adaptive. */
data class FixedPalette(val id: String, val label: String, val accent: Rgb)

object PaletteBuilder {
    const val ADAPTIVE = "adaptive"

    val fixed = listOf(
        FixedPalette("ember", "Ember", Rgb.hex("#F5A97F")),
        FixedPalette("sea", "Sea", Rgb.hex("#7DC4E4")),
        FixedPalette("moss", "Moss", Rgb.hex("#A6DA95")),
        FixedPalette("iris", "Iris", Rgb.hex("#C6A0F6")),
        FixedPalette("rose", "Rose", Rgb.hex("#F38BA8")),
        FixedPalette("mono", "Mono", Rgb.hex("#D8DEE9")),
    )

    /** Impasto `pick_accent`: vibrant mid-luminance colours win, else the most saturated, else neutral. */
    fun pickAccent(swatches: List<Swatch>): Rgb {
        val candidates = swatches.map { it.color }
        val vibrant = candidates.filter { it.sat > 0.2 && it.lum > 0.2 && it.lum < 0.85 }
        if (vibrant.isNotEmpty()) return vibrant.maxBy { it.sat * 2.0 + (1.0 - abs(it.lum - 0.55)) }
        val colorful = candidates.filter { it.sat > 0.1 }
        return colorful.maxByOrNull { it.sat } ?: LauncherPalette.NeutralAccent
    }

    /** Wallpaper sample (already downscaled, ~120x120) to palette. */
    fun fromPixels(pixels: IntArray): LauncherPalette = fromAccent(pickAccent(Quantizer.quantize(pixels, 24)))

    /**
     * Impasto `build_palette`, with the OLED twist: the background is #000 and
     * the accent is lifted until a focus ring on black is clearly visible.
     */
    fun fromAccent(raw: Rgb): LauncherPalette {
        // A ring or progress bar must read on black: WCAG non-text 3:1, and we
        // aim higher (4.5) because it is the only focus cue on a 3 m screen.
        val accent = lift(raw, Rgb.Black, 4.5)
        val (r, g, b) = Triple(accent.r.toDouble(), accent.g.toDouble(), accent.b.toDouble())
        // Impasto's background formula is our panel surface; the screen base stays black.
        val surface = Rgb.of(17 + r * 0.04, 19 + g * 0.04, 24 + b * 0.05)
        val surfaceHigh = Rgb.of(surface.r + 14.0, surface.g + 14.0, surface.b + 14.0)
        val border = Rgb.of(surfaceHigh.r + 32.0, surfaceHigh.g + 32.0, surfaceHigh.b + 38.0)
        val hover = Rgb.of(r * 1.15 + 15, g * 1.15 + 15, b * 1.15 + 15)
        val onAccent = if (accent.lum > 0.45) Rgb.hex("#11111B") else Rgb.hex("#FFFFFF")
        // Off-white text (never pure white: brightest static pixels age an OLED fastest).
        val text = lift(Rgb.hex("#EEF2F7"), surfaceHigh, 7.0)
        val muted = lift(Rgb.hex("#94A1B2"), surfaceHigh, 4.5)
        return LauncherPalette(accent, hover, onAccent, surface, surfaceHigh, border, text, muted)
    }

    fun forMode(mode: String, wallpaperPalette: LauncherPalette?): LauncherPalette =
        if (mode == ADAPTIVE) wallpaperPalette ?: LauncherPalette.Neutral
        else fixed.firstOrNull { it.id == mode }?.let { fromAccent(it.accent) } ?: (wallpaperPalette ?: LauncherPalette.Neutral)
}
