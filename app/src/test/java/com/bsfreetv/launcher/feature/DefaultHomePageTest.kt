package com.bsfreetv.launcher.feature

import androidx.compose.ui.input.key.Key
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.hasAnyDescendant
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isFocused
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.pressKey
import com.bsfreetv.launcher.domain.home.GOOGLE_TV_LAUNCHER
import com.bsfreetv.launcher.domain.home.HomeStatus
import com.bsfreetv.launcher.domain.home.StockLauncher
import com.bsfreetv.launcher.domain.home.TV_LAUNCHER
import com.bsfreetv.launcher.domain.palette.LauncherPalette
import com.bsfreetv.launcher.feature.home.HomeActions
import com.bsfreetv.launcher.feature.home.HomeUi
import com.bsfreetv.launcher.feature.home.SettingsPage
import com.bsfreetv.launcher.feature.settings.SettingsActions
import com.bsfreetv.launcher.feature.settings.SettingsEnv
import com.bsfreetv.launcher.feature.settings.SettingsPanel
import com.bsfreetv.launcher.feature.settings.SetupState
import com.bsfreetv.launcher.ui.BsfTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * No information may be unreachable: TV panels only scroll by moving focus,
 * so walking the D-pad to both ends must land on the first and last blocks.
 */
@OptIn(ExperimentalTestApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w960dp-h540dp-land-television-xhdpi")
class DefaultHomePageTest {
    @get:Rule val rule = createComposeRule()

    private val shield = HomeStatus(listOf(StockLauncher(TV_LAUNCHER, true)), TV_LAUNCHER, "com.bsfreetv.launcher", false, false)
    private val googleTv = HomeStatus(listOf(StockLauncher(GOOGLE_TV_LAUNCHER, true), StockLauncher(TV_LAUNCHER, true)), GOOGLE_TV_LAUNCHER, "com.bsfreetv.launcher", false, true)

    private fun show(page: SettingsPage, home: HomeStatus) {
        val env = SettingsEnv("", false, home, SetupState.Idle, listOf("adb shell pm disable-user --user 0 $TV_LAUNCHER"), null)
        rule.setContent {
            BsfTheme(LauncherPalette.Neutral, animate = false) {
                SettingsPanel(page, HomeUi(ready = true), env, HomeActions.None, SettingsActions.None)
            }
        }
        rule.waitForIdle()
    }

    private fun walk(key: Key) = repeat(12) {
        rule.onRoot().performKeyInput { pressKey(key) }
        rule.waitForIdle()
    }

    private fun focusedHas(text: String) = rule.onNode(isFocused()).assert(hasAnyDescendant(hasText(text, substring = true)))

    @Test fun `overview reaches the status card and the last row`() {
        show(SettingsPage.DEFAULT_HOME, shield)
        walk(Key.DirectionUp)
        focusedHas("Home button opens the stock launcher")
        walk(Key.DirectionDown)
        focusedHas("Undo and recovery")
    }

    @Test fun `google tv overview reaches the explanation and recovery`() {
        show(SettingsPage.DEFAULT_HOME, googleTv)
        focusedHas("accessibility service")
        walk(Key.DirectionDown)
        focusedHas("Undo and recovery")
    }

    @Test fun `google tv overview with the service on still reaches recovery`() {
        show(SettingsPage.DEFAULT_HOME, googleTv.copy(overrideEnabled = true))
        walk(Key.DirectionUp)
        focusedHas("accessibility service")
        walk(Key.DirectionDown)
        focusedHas("Undo and recovery")
    }

    @Test fun `disclosure page is read top to bottom and ends on the buttons`() {
        show(SettingsPage.HOME_ACCESSIBILITY, googleTv)
        focusedHas("Why an accessibility permission")
        walk(Key.DirectionDown)
        rule.onNode(isFocused()).assert(hasAnyDescendant(hasText("Open Accessibility") or hasText("Not now")))
        walk(Key.DirectionUp)
        focusedHas("Why an accessibility permission")
    }

    @Test fun `restricted page reaches the pc command`() {
        show(SettingsPage.HOME_RESTRICTED, googleTv)
        focusedHas("outside an app store")
        walk(Key.DirectionDown)
        focusedHas("Network debugging off")
    }

    @Test fun `recovery page reaches the commands and the uninstall warning`() {
        show(SettingsPage.HOME_RECOVERY, googleTv)
        rule.onRoot().performKeyInput { pressKey(Key.DirectionDown) }
        rule.waitForIdle()
        focusedHas("adb shell pm enable $GOOGLE_TV_LAUNCHER")
        walk(Key.DirectionDown)
        focusedHas("before uninstalling")
    }

    @Test fun `pc page shows the commands in a reachable block`() {
        show(SettingsPage.HOME_PC, shield)
        focusedHas("adb shell pm disable-user --user 0 $TV_LAUNCHER")
        walk(Key.DirectionDown)
        focusedHas("Network debugging off")
    }
}
