package com.bsfreetv.launcher.data

import com.bsfreetv.launcher.domain.LauncherSettings
import com.bsfreetv.launcher.domain.palette.LauncherPalette
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** The real asset path the device uses: index, thumbnails, full images, palette. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w960dp-h540dp-land-television-xhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class WallpapersTest {
    private val wallpapers = Wallpapers(RuntimeEnvironment.getApplication())

    @Test fun `built-ins load through the app's own decode path`() = runTest {
        val all = wallpapers.all().filter { !it.isUser }
        assertEquals(40, all.size)
        val id = LauncherSettings.DEFAULT_WALLPAPER
        val thumb = wallpapers.loadThumb(id)!!
        assertEquals(Wallpapers.THUMB_W, thumb.width)
        val full = wallpapers.loadFull(id, hardware = false)!!
        // 1080p TV: decoded at the screen size, 16:9.
        assertEquals(1920 to 1080, wallpapers.screenSize)
        assertEquals(minOf(1920, assetWidth(id)), full.width)
        assertEquals(full.width * 9 / 16, full.height)
        assertNotEquals(LauncherPalette.Neutral, wallpapers.palette(id))
    }

    @Test fun `every built-in thumbnail decodes`() = runTest {
        wallpapers.all().filter { !it.isUser }.forEach { assertNotNull(it.id, wallpapers.loadThumb(it.id)) }
    }

    /** Width of the shipped full-size asset, to know where "never upscale" kicks in. */
    private fun assetWidth(id: String): Int {
        val o = android.graphics.BitmapFactory.Options().apply { inJustDecodeBounds = true }
        RuntimeEnvironment.getApplication().assets.open("wallpapers/$id.webp").use { android.graphics.BitmapFactory.decodeStream(it, null, o) }
        return o.outWidth
    }

    @Test @Config(qualifiers = "w960dp-h540dp-land-television-xxxhdpi")
    fun `a 4K screen decodes at 4K but never beyond the image`() = runTest {
        // The Shield on a 4K TV: 960x540 dp at 4x = 3840x2160 px.
        assertEquals(3840 to 2160, wallpapers.screenSize)
        val id = LauncherSettings.DEFAULT_WALLPAPER
        val full = wallpapers.loadFull(id, hardware = false)!!
        assertEquals(minOf(3840, assetWidth(id)), full.width)
        assertEquals(full.width * 9 / 16, full.height)
    }
}
