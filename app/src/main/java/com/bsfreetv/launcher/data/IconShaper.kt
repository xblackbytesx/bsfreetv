package com.bsfreetv.launcher.data

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.Rect
import android.graphics.drawable.AdaptiveIconDrawable
import android.graphics.drawable.Drawable
import androidx.core.graphics.drawable.toBitmap
import com.bsfreetv.launcher.domain.icon.IconKind
import com.bsfreetv.launcher.domain.icon.IconShape
import com.bsfreetv.launcher.domain.palette.LauncherPalette
import com.bsfreetv.launcher.domain.palette.PaletteBuilder
import kotlin.math.roundToInt

/**
 * Turns any app icon into a full, round bitmap, once per app version (called
 * from AppCatalog's background scan; drawing it later costs nothing extra).
 *
 * - Adaptive icons: the background and foreground layers are drawn ourselves
 *   and clipped to a circle. The layers are designed to bleed past the visible
 *   area (108 dp drawn, 72 dp shown), so nothing meaningful is lost; this
 *   replaces the device's own mask (a square on the Shield).
 * - Legacy icons that fill their own bounds (square, squircle, circle): trimmed
 *   to those bounds and cropped to a circle.
 * - Legacy logos on transparency: centred on a round plate tinted with the
 *   logo's own colour at OLED-dark lightness, so nothing gets cut off.
 */
object IconShaper {
    /** Adaptive layers extend 25% beyond the visible icon on every side (18 of 72 dp). */
    private const val ADAPTIVE_BLEED = 0.25f
    /** A logo's longest side on its plate: the square's diagonal stays inside the circle. */
    private const val LOGO_ON_PLATE = 0.62f

    fun round(drawable: Drawable, size: Int): Bitmap =
        if (drawable is AdaptiveIconDrawable) adaptive(drawable, size) else legacy(drawable, size)

    private fun adaptive(icon: AdaptiveIconDrawable, size: Int): Bitmap {
        val layers = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(layers)
        val bleed = (size * ADAPTIVE_BLEED).roundToInt()
        listOfNotNull(icon.background, icon.foreground).forEach { layer ->
            layer.setBounds(-bleed, -bleed, size + bleed, size + bleed)
            layer.draw(canvas)
        }
        return circleCrop(layers, Rect(0, 0, size, size), size).also { layers.recycle() }
    }

    private fun legacy(drawable: Drawable, size: Int): Bitmap {
        val raster = drawable.toBitmap(size, size, Bitmap.Config.ARGB_8888)
        val px = IntArray(size * size).also { raster.getPixels(it, 0, size, 0, 0, size, size) }
        val box = IconShape.alphaBox(px, size, size)
        val out = when {
            box == null -> plate(raster, null, LauncherPalette.Neutral.surfaceHigh.argb, size)
            IconShape.classify(px, size, size) == IconKind.SOLID -> {
                val sq = box.square()
                circleCrop(raster, Rect(sq.left, sq.top, sq.right, sq.bottom), size)
            }
            else -> {
                val opaque = px.filter { (it ushr 24) > 200 }.toIntArray()
                val tint = PaletteBuilder.plateFor(opaque)?.argb ?: LauncherPalette.Neutral.surfaceHigh.argb
                plate(raster, Rect(box.left, box.top, box.right, box.bottom), tint, size)
            }
        }
        if (out !== raster) raster.recycle()
        return out
    }

    /** [src] region scaled to [size] and clipped to an anti-aliased circle. */
    private fun circleCrop(src: Bitmap, region: Rect, size: Int): Bitmap {
        val out = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(out)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
        canvas.drawCircle(size / 2f, size / 2f, size / 2f, paint)
        paint.xfermode = PorterDuffXfermode(PorterDuff.Mode.SRC_IN)
        canvas.drawBitmap(src, region, Rect(0, 0, size, size), paint)
        return out
    }

    /** A round plate in [tint] with [src] (its [region], or all of it) centred inside. */
    private fun plate(src: Bitmap, region: Rect?, tint: Int, size: Int): Bitmap {
        val out = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(out)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
        paint.color = tint
        canvas.drawCircle(size / 2f, size / 2f, size / 2f, paint)
        val r = region ?: Rect(0, 0, src.width, src.height)
        val scale = size * LOGO_ON_PLATE / maxOf(r.width(), r.height())
        val w = (r.width() * scale).roundToInt()
        val h = (r.height() * scale).roundToInt()
        val left = (size - w) / 2
        val top = (size - h) / 2
        canvas.drawBitmap(src, r, Rect(left, top, left + w, top + h), Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG))
        return out
    }
}
