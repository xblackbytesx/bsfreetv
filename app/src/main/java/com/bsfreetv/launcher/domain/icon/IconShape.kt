package com.bsfreetv.launcher.domain.icon

import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/** How a legacy (non-adaptive) icon can be made round without losing anything. */
enum class IconKind {
    /** A solid shape filling its own bounds (full-bleed square, rounded square, circle): crop to a circle. */
    SOLID,

    /** A logo on transparency, or too wide to crop: put it on a round plate instead. */
    LOGO,
}

/** Opaque-pixel bounds, [left, right) x [top, bottom). */
data class AlphaBox(val left: Int, val top: Int, val right: Int, val bottom: Int) {
    val width get() = right - left
    val height get() = bottom - top

    /** The smallest square around this box, centred on it: what a circle crop is cut from. */
    fun square(): AlphaBox {
        val side = max(width, height)
        val cx = (left + right) / 2f
        val cy = (top + bottom) / 2f
        val l = (cx - side / 2f).roundToInt()
        val t = (cy - side / 2f).roundToInt()
        return AlphaBox(l, t, l + side, t + side)
    }
}

/**
 * Pure pixel analysis behind round icons, on ARGB pixels (row-major). Decides
 * whether a legacy icon can be cropped to a circle (its shape fills its own
 * bounds, so the crop only trims corners) or must sit on a plate (cropping
 * would cut the logo).
 */
object IconShape {
    /** Alpha above this counts as part of the icon. */
    private const val VISIBLE = 24
    /** Alpha above this counts as solid. */
    private const val SOLID = 200

    private fun alpha(px: IntArray, w: Int, x: Int, y: Int) = px[y * w + x] ushr 24

    fun alphaBox(px: IntArray, w: Int, h: Int): AlphaBox? {
        var l = w; var t = h; var r = -1; var b = -1
        for (y in 0 until h) for (x in 0 until w) {
            if (alpha(px, w, x, y) > VISIBLE) {
                l = min(l, x); t = min(t, y); r = max(r, x); b = max(b, y)
            }
        }
        return if (r < 0) null else AlphaBox(l, t, r + 1, b + 1)
    }

    fun classify(px: IntArray, w: Int, h: Int): IconKind {
        val box = alphaBox(px, w, h) ?: return IconKind.LOGO
        val aspect = box.width.toFloat() / box.height
        if (aspect < 0.85f || aspect > 1.18f) return IconKind.LOGO
        val cx = (box.left + box.right) / 2f
        val cy = (box.top + box.bottom) / 2f
        fun solidAt(fx: Float, fy: Float): Boolean {
            val x = fx.roundToInt().coerceIn(0, w - 1)
            val y = fy.roundToInt().coerceIn(0, h - 1)
            return alpha(px, w, x, y) > SOLID
        }
        val hw = box.width / 2f
        val hh = box.height / 2f
        // Solid in the middle of each edge (a circle, squircle or square all
        // touch their bounds there) and across the body: a logo does not.
        val probes = listOf(
            0f to 0f,
            -0.94f to 0f, 0.94f to 0f, 0f to -0.94f, 0f to 0.94f,
            -0.6f to -0.6f, 0.6f to -0.6f, -0.6f to 0.6f, 0.6f to 0.6f,
            -0.5f to 0f, 0.5f to 0f, 0f to -0.5f, 0f to 0.5f,
        )
        return if (probes.all { (dx, dy) -> solidAt(cx + dx * hw, cy + dy * hh) }) IconKind.SOLID else IconKind.LOGO
    }
}
