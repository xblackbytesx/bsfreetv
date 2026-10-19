package com.bsfreetv.launcher.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Banners and icons are rasterised for the pixels they are drawn at, not a fixed 1080p size. */
class ArtworkSizeTest {
    @Test fun `banner covers the largest focused card at the display density`() {
        // 1080p Shield (2x): 200 dp * 1.08 * 2 = 432 px.
        assertEquals(432, AppCatalog.bannerWidthFor(2f))
        // 4K Shield (4x): 864 px, so the 640 px-wide large card is never upscaled.
        assertEquals(864, AppCatalog.bannerWidthFor(4f))
        assertTrue(AppCatalog.bannerWidthFor(4f) >= (200 * 1.08f * 4).toInt())
    }

    @Test fun `icons scale with density and keep a floor`() {
        assertEquals(256, AppCatalog.iconSizeFor(4f))
        assertEquals(128, AppCatalog.iconSizeFor(2f))
        assertEquals(AppCatalog.MIN_ICON_PX, AppCatalog.iconSizeFor(1f))
        assertEquals(AppCatalog.MIN_BANNER_W, AppCatalog.bannerWidthFor(1f))
    }
}
