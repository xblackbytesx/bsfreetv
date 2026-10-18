package com.bsfreetv.launcher.domain

import com.bsfreetv.launcher.domain.palette.contrast
import com.bsfreetv.launcher.domain.palette.LauncherPalette
import com.bsfreetv.launcher.domain.palette.lift
import com.bsfreetv.launcher.domain.palette.PaletteBuilder
import com.bsfreetv.launcher.domain.palette.Quantizer
import com.bsfreetv.launcher.domain.palette.Rgb
import com.bsfreetv.launcher.domain.palette.Swatch
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PaletteTest {
    private fun pixels(vararg runs: Pair<Rgb, Int>) = runs.flatMap { (c, n) -> List(n) { c.argb } }.toIntArray()

    @Test fun `contrast matches WCAG reference values`() {
        assertEquals(21.0, contrast(Rgb.hex("#FFFFFF"), Rgb.Black), 0.01)
        assertEquals(1.0, contrast(Rgb.hex("#777777"), Rgb.hex("#777777")), 0.0001)
        // #767676 on white is the classic 4.54:1 boundary.
        assertEquals(4.54, contrast(Rgb.hex("#767676"), Rgb.hex("#FFFFFF")), 0.01)
    }

    @Test fun `quantizer keeps dominant colours and their populations`() {
        val red = Rgb(200, 30, 30)
        val blue = Rgb(20, 40, 180)
        val out = Quantizer.quantize(pixels(red to 600, blue to 400), 24)
        assertEquals(2, out.size)
        assertEquals(600, out[0].population)
        // 5-bit packing loses at most 8 per channel.
        assertTrue(kotlin.math.abs(out[0].color.r - red.r) <= 8)
        assertTrue(kotlin.math.abs(out[1].color.b - blue.b) <= 8)
    }

    @Test fun `quantizer caps the colour count and ignores transparency`() {
        val many = IntArray(4096) { i -> (0xFF shl 24) or ((i * 37) and 0xFFFFFF) }
        assertTrue(Quantizer.quantize(many, 24).size <= 24)
        assertTrue(Quantizer.quantize(IntArray(10) { 0x00FF0000 }).isEmpty())
    }

    @Test fun `pick accent prefers vibrant mid luminance over dominant grey`() {
        val grey = Swatch(Rgb(90, 90, 95), 9000)
        val orange = Swatch(Rgb(230, 140, 50), 300)
        val nearWhite = Swatch(Rgb(250, 240, 235), 500)
        assertEquals(orange.color, PaletteBuilder.pickAccent(listOf(grey, orange, nearWhite)))
    }

    @Test fun `pick accent falls back to most saturated, then neutral`() {
        val darkBlue = Swatch(Rgb(10, 20, 45), 100) // lum < 0.2: not "vibrant"
        val grey = Swatch(Rgb(40, 40, 40), 100)
        assertEquals(darkBlue.color, PaletteBuilder.pickAccent(listOf(grey, darkBlue)))
        assertEquals(LauncherPalette.NeutralAccent, PaletteBuilder.pickAccent(listOf(grey)))
    }

    @Test fun `every palette role is readable on black and on its surface`() {
        val accents = listOf("#0A1A60", "#8B0000", "#F5A97F", "#FFFFFF", "#202020", "#00FF00").map(Rgb::hex)
        for (a in accents) {
            val p = PaletteBuilder.fromAccent(a)
            assertTrue("accent $a on black", contrast(p.accent, Rgb.Black) >= 4.5)
            assertTrue("text on surfaceHigh", contrast(p.text, p.surfaceHigh) >= 7.0)
            assertTrue("muted on surfaceHigh", contrast(p.textMuted, p.surfaceHigh) >= 4.5)
            assertTrue("onAccent on accent", contrast(p.onAccent, p.accent) >= 3.0)
            // Surfaces stay near-black: OLED-friendly panels.
            assertTrue(p.surface.relativeLuminance < 0.03)
        }
    }

    @Test fun `lift keeps hue and only moves lightness`() {
        val dark = Rgb.hex("#301010")
        val lifted = lift(dark, Rgb.Black, 4.5)
        assertTrue(contrast(lifted, Rgb.Black) >= 4.5)
        assertTrue(lifted.r > lifted.g && lifted.r > lifted.b)
    }

    @Test fun `fromPixels on a moonlit image picks the warm highlight`() {
        val night = Rgb(12, 18, 34)
        val moon = Rgb(236, 200, 120)
        val p = PaletteBuilder.fromPixels(pixels(night to 13000, moon to 400))
        assertTrue("warm accent expected, got ${p.accent.hex}", p.accent.r > p.accent.b)
    }

    @Test fun `forMode uses fixed palettes and falls back to the wallpaper`() {
        val wall = PaletteBuilder.fromAccent(Rgb.hex("#7DC4E4"))
        assertEquals(wall, PaletteBuilder.forMode(PaletteBuilder.ADAPTIVE, wall))
        assertEquals(LauncherPalette.Neutral, PaletteBuilder.forMode(PaletteBuilder.ADAPTIVE, null))
        assertEquals(PaletteBuilder.fromAccent(Rgb.hex("#F5A97F")), PaletteBuilder.forMode("ember", wall))
        assertEquals(wall, PaletteBuilder.forMode("gone", wall))
    }
}
