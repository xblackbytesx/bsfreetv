package com.bsfreetv.launcher.domain.palette

import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.roundToInt

/** An opaque sRGB colour, channels 0..255. Pure Kotlin so the palette engine runs in JVM tests. */
data class Rgb(val r: Int, val g: Int, val b: Int) {
    init {
        require(r in 0..255 && g in 0..255 && b in 0..255) { "channel out of range: $this" }
    }

    val argb: Int get() = (0xFF shl 24) or (r shl 16) or (g shl 8) or b

    /** Impasto's ranking luminance: Rec.709 weights on 0..1 values, no gamma. */
    val lum: Double get() = (0.2126 * r + 0.7152 * g + 0.0722 * b) / 255.0

    /** HSV saturation, (max-min)/max, as Impasto's `sample_colors` computes it. */
    val sat: Double
        get() {
            val hi = max(r, max(g, b))
            val lo = min(r, min(g, b))
            return if (hi == 0) 0.0 else (hi - lo).toDouble() / hi
        }

    /** WCAG 2 relative luminance (linearised sRGB). */
    val relativeLuminance: Double
        get() {
            fun lin(c: Int): Double {
                val s = c / 255.0
                return if (s <= 0.04045) s / 12.92 else ((s + 0.055) / 1.055).pow(2.4)
            }
            return 0.2126 * lin(r) + 0.7152 * lin(g) + 0.0722 * lin(b)
        }

    val hex: String get() = "#%02X%02X%02X".format(r, g, b)

    companion object {
        val Black = Rgb(0, 0, 0)

        fun of(r: Double, g: Double, b: Double) = Rgb(clamp(r), clamp(g), clamp(b))

        fun fromArgb(argb: Int) = Rgb((argb shr 16) and 0xFF, (argb shr 8) and 0xFF, argb and 0xFF)

        fun hex(s: String): Rgb {
            val v = s.removePrefix("#").toInt(16)
            return fromArgb(v)
        }

        private fun clamp(v: Double) = v.roundToInt().coerceIn(0, 255)
    }
}

/** WCAG contrast ratio, 1..21. */
fun contrast(a: Rgb, b: Rgb): Double {
    val la = a.relativeLuminance
    val lb = b.relativeLuminance
    return (max(la, lb) + 0.05) / (min(la, lb) + 0.05)
}

/** HLS (as Python's colorsys) so [lift] moves lightness exactly like Impasto. */
internal data class Hls(val h: Double, val l: Double, val s: Double)

internal fun Rgb.toHls(): Hls {
    val rf = r / 255.0
    val gf = g / 255.0
    val bf = b / 255.0
    val hi = max(rf, max(gf, bf))
    val lo = min(rf, min(gf, bf))
    val l = (hi + lo) / 2
    if (hi == lo) return Hls(0.0, l, 0.0)
    val d = hi - lo
    val s = if (l <= 0.5) d / (hi + lo) else d / (2.0 - hi - lo)
    val rc = (hi - rf) / d
    val gc = (hi - gf) / d
    val bc = (hi - bf) / d
    val h = when (hi) {
        rf -> bc - gc
        gf -> 2.0 + rc - bc
        else -> 4.0 + gc - rc
    }
    return Hls(((h / 6.0) % 1.0 + 1.0) % 1.0, l, s)
}

internal fun Hls.toRgb(): Rgb {
    if (s == 0.0) return Rgb.of(l * 255, l * 255, l * 255)
    val m2 = if (l <= 0.5) l * (1.0 + s) else l + s - l * s
    val m1 = 2.0 * l - m2
    fun v(hue: Double): Double {
        val hh = ((hue % 1.0) + 1.0) % 1.0
        return when {
            hh < 1.0 / 6 -> m1 + (m2 - m1) * hh * 6.0
            hh < 0.5 -> m2
            hh < 2.0 / 3 -> m1 + (m2 - m1) * (2.0 / 3 - hh) * 6.0
            else -> m1
        }
    }
    return Rgb.of(v(h + 1.0 / 3) * 255, v(h) * 255, v(h - 1.0 / 3) * 255)
}

/** Impasto's pivot: backgrounds brighter than this get darker foregrounds. */
const val CONTRAST_PIVOT = 0.179

/**
 * Move [color]'s HLS lightness in 0.01 steps until it reaches [min] contrast
 * against [against] (Impasto's `lift`). Hue and saturation are kept.
 */
fun lift(color: Rgb, against: Rgb, min: Double): Rgb {
    if (contrast(color, against) >= min) return color
    val darker = against.relativeLuminance > CONTRAST_PIVOT
    var hls = color.toHls()
    var out = color
    repeat(100) {
        val l = (hls.l + if (darker) -0.01 else 0.01).coerceIn(0.0, 1.0)
        hls = hls.copy(l = l)
        out = hls.toRgb()
        if (contrast(out, against) >= min || l == 0.0 || l == 1.0) return out
    }
    return out
}
