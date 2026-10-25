package com.bsfreetv.launcher.feature

import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.click
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performTouchInput
import com.bsfreetv.launcher.domain.LauncherSettings
import com.bsfreetv.launcher.domain.palette.LauncherPalette
import com.bsfreetv.launcher.domain.status.IpInfo
import com.bsfreetv.launcher.domain.status.IpLookup
import com.bsfreetv.launcher.domain.status.StatusUi
import com.bsfreetv.launcher.feature.home.HomeActions
import com.bsfreetv.launcher.feature.home.HomeContent
import com.bsfreetv.launcher.feature.home.HomeUi
import com.bsfreetv.launcher.domain.idle.IdlePhase
import com.bsfreetv.launcher.ui.BsfTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** The status area: absent unless its state is true; the VPN pill looks up the IP only when pressed. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w960dp-h540dp-land-television-xhdpi")
class StatusAreaTest {
    @get:Rule val rule = createComposeRule()

    private var lookups = 0
    private val actions = object : HomeActions by HomeActions.None {
        override fun lookUpIp() { lookups++ }
    }

    private fun show(status: StatusUi) {
        rule.setContent {
            BsfTheme(LauncherPalette.Neutral, animate = false) {
                HomeContent(
                    HomeUi(ready = true, settings = LauncherSettings(statusVpn = true)), null, emptyList(), IdlePhase.ACTIVE,
                    moving = null, homeTick = 0, focusRequest = null, overlayOpen = false, actions = actions, status = status,
                )
            }
        }
        rule.waitForIdle()
    }

    @Test fun `no vpn, no pill`() {
        show(StatusUi(vpnShown = false))
        rule.onAllNodesWithText("VPN", substring = true).assertCountEquals(0)
    }

    @Test fun `pill shows, and a press looks up and shows ip and country`() {
        show(StatusUi(vpnShown = true, ip = IpLookup.Done(IpInfo("100.34.42.1", "NO"))))
        assertEquals(0, lookups)
        rule.onNodeWithText("VPN").performTouchInput { click() }
        rule.waitForIdle()
        assertEquals(1, lookups)
        rule.onNodeWithText("VPN · 100.34.42.1 · Norway").assertExists()
    }
}
