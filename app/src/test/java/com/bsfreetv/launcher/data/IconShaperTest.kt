package com.bsfreetv.launcher.data

import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.drawable.AdaptiveIconDrawable
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.ColorDrawable
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** Every icon comes out round, full size, with nothing cut that matters. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class IconShaperTest {
    private val size = 200
    private fun alpha(b: Bitmap, x: Int, y: Int) = Color.alpha(b.getPixel(x, y))

    private fun assertRound(b: Bitmap) {
        assertEquals(size, b.width)
        listOf(1 to 1, size - 2 to 1, 1 to size - 2, size - 2 to size - 2).forEach { (x, y) -> assertEquals("corner $x,$y", 0, alpha(b, x, y)) }
        listOf(size / 2 to 2, size / 2 to size - 3, 2 to size / 2, size - 3 to size / 2).forEach { (x, y) -> assertTrue("edge $x,$y", alpha(b, x, y) > 200) }
    }

    @Test fun `adaptive icon fills the whole circle with its own layers`() {
        val fg = Bitmap.createBitmap(108, 108, Bitmap.Config.ARGB_8888).apply {
            // A white dot in the middle of the safe zone.
            for (y in 44..63) for (x in 44..63) setPixel(x, y, Color.WHITE)
        }
        val icon = AdaptiveIconDrawable(ColorDrawable(Color.rgb(20, 120, 60)), BitmapDrawable(RuntimeEnvironment.getApplication().resources, fg))
        val out = IconShaper.round(icon, size)
        assertRound(out)
        // The background layer bleeds to the edge; the foreground sits in the middle.
        assertEquals(Color.rgb(20, 120, 60), out.getPixel(size / 2, 6) or 0xFF000000.toInt())
        assertEquals(Color.WHITE, out.getPixel(size / 2, size / 2))
    }

    @Test fun `legacy full-bleed square is cropped to a circle`() {
        val src = Bitmap.createBitmap(96, 96, Bitmap.Config.ARGB_8888).apply { eraseColor(Color.rgb(200, 40, 40)) }
        assertRound(IconShaper.round(BitmapDrawable(RuntimeEnvironment.getApplication().resources, src), size))
    }

    @Test fun `legacy logo lands on a dark tinted plate, uncut`() {
        val src = Bitmap.createBitmap(96, 96, Bitmap.Config.ARGB_8888).apply {
            // A thin bright-green cross on transparency: a logo, not a shape.
            for (i in 10..85) for (t in 44..51) { setPixel(i, t, Color.rgb(40, 210, 90)); setPixel(t, i, Color.rgb(40, 210, 90)) }
        }
        val out = IconShaper.round(BitmapDrawable(RuntimeEnvironment.getApplication().resources, src), size)
        assertRound(out)
        val plate = out.getPixel(size / 2, 12)
        assertTrue("plate is green-tinted", Color.green(plate) > Color.red(plate) && Color.green(plate) > Color.blue(plate))
        assertTrue("plate is dark", Color.green(plate) < 90)
        assertEquals(Color.rgb(40, 210, 90), out.getPixel(size / 2, size / 2) or 0xFF000000.toInt())
    }
}
