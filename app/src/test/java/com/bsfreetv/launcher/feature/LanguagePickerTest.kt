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
import com.bsfreetv.launcher.domain.palette.LauncherPalette
import com.bsfreetv.launcher.feature.home.HomeActions
import com.bsfreetv.launcher.feature.home.HomeUi
import com.bsfreetv.launcher.feature.home.SettingsPage
import com.bsfreetv.launcher.feature.settings.SettingsActions
import com.bsfreetv.launcher.feature.settings.SettingsEnv
import com.bsfreetv.launcher.feature.settings.SettingsPanel
import com.bsfreetv.launcher.feature.settings.SetupState
import com.bsfreetv.launcher.ui.BsfTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Settings > Language is a list you pick from, starting on the current choice. */
@OptIn(ExperimentalTestApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w960dp-h540dp-land-television-xhdpi")
class LanguagePickerTest {
    @get:Rule val rule = createComposeRule()

    private var picked: String? = "unset"
    private val setup = object : SettingsActions by SettingsActions.None {
        override fun setLanguage(tag: String?) { picked = tag }
    }

    private fun show(current: String?) {
        val env = SettingsEnv("", false, null, SetupState.Idle, emptyList(), null, languageTag = current)
        rule.setContent {
            BsfTheme(LauncherPalette.Neutral, animate = false) {
                SettingsPanel(SettingsPage.LANGUAGE, HomeUi(ready = true), env, HomeActions.None, setup)
            }
        }
        rule.waitForIdle()
    }

    private fun press(key: Key) {
        rule.onRoot().performKeyInput { pressKey(key) }
        rule.waitForIdle()
    }

    @Test fun `focus starts on the current language and OK picks another`() {
        show("nl")
        rule.onNode(isFocused()).assert(hasAnyDescendant(hasText("Nederlands")))
        press(Key.DirectionDown)
        rule.onNode(isFocused()).assert(hasAnyDescendant(hasText("Deutsch")))
        press(Key.DirectionCenter)
        assertEquals("de", picked)
    }

    @Test fun `system default is first and picking the current one does nothing`() {
        show(null)
        rule.onNode(isFocused()).assert(hasAnyDescendant(hasText("System default")))
        press(Key.DirectionCenter)
        assertEquals("unset", picked)
    }
}
