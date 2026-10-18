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
@Config(sdk = [35])
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
        assertEquals(Wallpapers.SCREEN_W, full.width)
        assertEquals(Wallpapers.SCREEN_H, full.height)
        assertNotEquals(LauncherPalette.Neutral, wallpapers.palette(id))
    }

    @Test fun `every built-in thumbnail decodes`() = runTest {
        wallpapers.all().filter { !it.isUser }.forEach { assertNotNull(it.id, wallpapers.loadThumb(it.id)) }
    }
}
