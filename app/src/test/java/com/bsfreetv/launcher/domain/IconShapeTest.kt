package com.bsfreetv.launcher.domain

import com.bsfreetv.launcher.domain.icon.AlphaBox
import com.bsfreetv.launcher.domain.icon.IconKind
import com.bsfreetv.launcher.domain.icon.IconShape
import com.bsfreetv.launcher.domain.palette.PaletteBuilder
import com.bsfreetv.launcher.domain.palette.Rgb
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.hypot

/** Which legacy icons may be cropped to a circle and which need a plate. */
class IconShapeTest {
    private val n = 100
    private val opaque = 0xFF3366CC.toInt()

    private fun grid(inside: (Int, Int) -> Boolean) = IntArray(n * n) { i -> if (inside(i % n, i / n)) opaque else 0 }

    @Test fun `full bleed square is solid`() {
        assertEquals(IconKind.SOLID, IconShape.classify(grid { _, _ -> true }, n, n))
    }

    @Test fun `circle with padding is solid and trims to its bounds`() {
        val px = grid { x, y -> hypot(x - 49.5, y - 49.5) <= 40 }
        assertEquals(IconKind.SOLID, IconShape.classify(px, n, n))
        val box = IconShape.alphaBox(px, n, n)!!
        assertTrue(box.width in 79..82 && box.left in 9..11)
    }

    @Test fun `rounded square with margin is solid`() {
        val px = grid { x, y ->
            val inBox = x in 10..89 && y in 10..89
            val cx = x.coerceIn(30, 69); val cy = y.coerceIn(30, 69)
            inBox && hypot((x - cx).toDouble(), (y - cy).toDouble()) <= 20
        }
        assertEquals(IconKind.SOLID, IconShape.classify(px, n, n))
    }

    @Test fun `logo on transparency gets a plate`() {
        // A ring: hollow in the middle.
        val ring = grid { x, y -> hypot(x - 49.5, y - 49.5).let { it in 30.0..40.0 } }
        assertEquals(IconKind.LOGO, IconShape.classify(ring, n, n))
        // A thin diagonal stroke.
        val stroke = grid { x, y -> kotlin.math.abs(x - y) < 6 }
        assertEquals(IconKind.LOGO, IconShape.classify(stroke, n, n))
        // A wide wordmark.
        val wide = grid { x, y -> x in 5..94 && y in 40..59 }
        assertEquals(IconKind.LOGO, IconShape.classify(wide, n, n))
    }

    @Test fun `empty icon is a logo and has no bounds`() {
        assertNull(IconShape.alphaBox(IntArray(n * n), n, n))
        assertEquals(IconKind.LOGO, IconShape.classify(IntArray(n * n), n, n))
    }

    @Test fun `square around a box is centred`() {
        val sq = AlphaBox(10, 20, 90, 60).square()
        assertEquals(80, sq.width)
        assertEquals(80, sq.height)
        assertEquals(0, sq.top)
    }

    @Test fun `plate is a dark tint of the logo colour, neutral for grey logos`() {
        val green = IntArray(400) { Rgb(40, 200, 90).argb }
        val plate = PaletteBuilder.plateFor(green)!!
        assertTrue(plate.g > plate.r && plate.g > plate.b)
        assertTrue("dark enough for OLED", plate.relativeLuminance < 0.05)
        assertNull(PaletteBuilder.plateFor(IntArray(400) { Rgb(128, 128, 128).argb }))
    }
}
