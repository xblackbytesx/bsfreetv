package com.bsfreetv.launcher.feature

import android.graphics.Bitmap
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.hasAnyDescendant
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isFocused
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.click
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.pressKey
import com.bsfreetv.launcher.data.AppEntry
import com.bsfreetv.launcher.domain.layout.AppInfo
import com.bsfreetv.launcher.domain.layout.Section
import com.bsfreetv.launcher.domain.palette.LauncherPalette
import com.bsfreetv.launcher.feature.apps.AllAppsOverlay
import com.bsfreetv.launcher.feature.apps.AppMenu
import com.bsfreetv.launcher.feature.home.AppFilter
import com.bsfreetv.launcher.feature.home.HomeActions
import com.bsfreetv.launcher.feature.home.HomeUi
import com.bsfreetv.launcher.ui.BsfTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** The app menu must take focus and be navigable when opened over All apps. */
@OptIn(ExperimentalTestApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w960dp-h540dp-land-television-xhdpi")
class AppMenuFocusTest {
    @get:Rule val rule = createComposeRule()

    private fun app(pkg: String, label: String) = AppEntry(
        AppInfo(pkg, label, hasBanner = false),
        null,
        Bitmap.createBitmap(8, 8, Bitmap.Config.ARGB_8888).asImageBitmap(),
        1,
    )

    private val jellyfin = app("org.jellyfin.androidtv", "Jellyfin")
    private val kodi = app("org.xbmc.kodi", "Kodi")
    private val ui = HomeUi(ready = true, media = listOf(jellyfin), unsorted = listOf(kodi), sections = mapOf(jellyfin.packageName to Section.MEDIA))

    @Test fun `menu over All apps takes focus and moves with the d-pad`() {
        rule.setContent {
            BsfTheme(LauncherPalette.Neutral, animate = false) {
                AllAppsOverlay(ui, AppFilter.ALL, HomeActions.None, menuOpen = true)
                AppMenu(jellyfin, Section.MEDIA, HomeActions.None)
            }
        }
        rule.waitForIdle()
        rule.onNode(isFocused()).assert(hasAnyDescendant(hasText("Move to Tools")))
        rule.onRoot().performKeyInput { pressKey(Key.DirectionDown) }
        rule.waitForIdle()
        rule.onNode(isFocused()).assert(hasAnyDescendant(hasText("Reorder")))
    }

    @Test fun `closing the menu returns focus to the app it was opened on`() {
        var menu by mutableStateOf(false)
        rule.setContent {
            BsfTheme(LauncherPalette.Neutral, animate = false) {
                AllAppsOverlay(ui, AppFilter.ALL, HomeActions.None, menuOpen = menu)
                if (menu) AppMenu(kodi, null, HomeActions.None)
            }
        }
        rule.waitForIdle()
        rule.onRoot().performKeyInput { pressKey(Key.DirectionRight) }
        rule.waitForIdle()
        rule.onNode(isFocused()).assert(hasAnyDescendant(hasText("Kodi")))
        menu = true
        rule.waitForIdle()
        rule.onNode(isFocused()).assert(hasAnyDescendant(hasText("Add to Media")))
        menu = false
        rule.waitForIdle()
        rule.onNode(isFocused()).assert(hasAnyDescendant(hasText("Kodi")))
    }

    @Test fun `a pointer click activates a menu item`() {
        var reordered: String? = null
        val actions = object : HomeActions by HomeActions.None {
            override fun startMove(pkg: String) { reordered = pkg }
        }
        rule.setContent {
            BsfTheme(LauncherPalette.Neutral, animate = false) {
                AllAppsOverlay(ui, AppFilter.ALL, actions, menuOpen = true)
                AppMenu(jellyfin, Section.MEDIA, actions)
            }
        }
        rule.waitForIdle()
        rule.onNodeWithText("Reorder").performTouchInput { click() }
        rule.waitForIdle()
        org.junit.Assert.assertEquals(jellyfin.packageName, reordered)
    }
}
