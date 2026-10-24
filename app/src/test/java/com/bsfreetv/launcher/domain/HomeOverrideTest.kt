package com.bsfreetv.launcher.domain

import com.bsfreetv.launcher.domain.home.accessibilityServiceEnabled
import com.bsfreetv.launcher.domain.home.BootLaunch
import com.bsfreetv.launcher.domain.home.CrashGuard
import com.bsfreetv.launcher.domain.home.DeviceKind
import com.bsfreetv.launcher.domain.home.GOOGLE_TV_LAUNCHER
import com.bsfreetv.launcher.domain.home.HomeCommands
import com.bsfreetv.launcher.domain.home.HomeKeyAction
import com.bsfreetv.launcher.domain.home.HomeKeyPolicy
import com.bsfreetv.launcher.domain.home.HomeMethod
import com.bsfreetv.launcher.domain.home.homeMethod
import com.bsfreetv.launcher.domain.home.HomeStatus
import com.bsfreetv.launcher.domain.home.StockLauncher
import com.bsfreetv.launcher.domain.home.TV_LAUNCHER
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** The Google TV Home button service: only Home, never stuck, never a surprise launch. */
class HomeOverrideTest {
    private val home = HomeKeyPolicy.KEYCODE_HOME
    private val dpadCenter = 23
    private val longPress = 500L

    private fun HomeKeyPolicy.down(t: Long, repeat: Int = 0, hold: Boolean = true, downAt: Long = t) =
        onKey(home, down = true, repeatCount = repeat, downTime = downAt, eventTime = t, holdForStock = hold)

    private fun HomeKeyPolicy.up(downAt: Long, t: Long, hold: Boolean = true) =
        onKey(home, down = false, repeatCount = 0, downTime = downAt, eventTime = t, holdForStock = hold)

    @Test fun `each device kind gets exactly one method`() {
        assertEquals(HomeMethod.ADB_SWITCH, DeviceKind.CLASSIC_ANDROID_TV.homeMethod)
        assertEquals(HomeMethod.ACCESSIBILITY, DeviceKind.GOOGLE_TV.homeMethod)
        assertEquals(HomeMethod.NONE, DeviceKind.UNKNOWN.homeMethod)
    }

    @Test fun `google tv never gets the adb switch, the shield never gets the service`() {
        val gtv = HomeStatus(listOf(StockLauncher(GOOGLE_TV_LAUNCHER, true)), GOOGLE_TV_LAUNCHER, "p", false, false, overrideEnabled = true)
        assertEquals(HomeMethod.ACCESSIBILITY, gtv.method)
        assertTrue(HomeCommands.makeDefault(gtv, "p.Main").isEmpty())
        assertTrue(gtv.homeIsOurs)
        // A service someone switched on by hand on a Shield is not "our Home".
        val shield = HomeStatus(listOf(StockLauncher(TV_LAUNCHER, true)), TV_LAUNCHER, "p", false, false, overrideEnabled = true)
        assertEquals(HomeMethod.ADB_SWITCH, shield.method)
        assertFalse(shield.homeIsOurs)
    }

    @Test fun `other keys always pass, untouched`() {
        val p = HomeKeyPolicy(longPress)
        listOf(dpadCenter, 4, 19, 20, 82, 85, 164).forEach { code ->
            assertEquals(HomeKeyAction.PASS, p.onKey(code, true, 0, 0, 0, true))
            assertEquals(HomeKeyAction.PASS, p.onKey(code, false, 0, 0, 10, true))
        }
    }

    @Test fun `short press opens BSfreeTV on release`() {
        val p = HomeKeyPolicy(longPress)
        assertEquals(HomeKeyAction.CONSUME, p.down(1_000))
        assertEquals(HomeKeyAction.LAUNCH_OURS, p.up(1_000, 1_120))
    }

    @Test fun `hold with auto repeat opens google tv once, release is swallowed`() {
        val p = HomeKeyPolicy(longPress)
        assertEquals(HomeKeyAction.CONSUME, p.down(1_000))
        assertEquals(HomeKeyAction.OPEN_STOCK, p.down(1_500, repeat = 1, downAt = 1_000))
        assertEquals(HomeKeyAction.CONSUME, p.down(1_550, repeat = 2, downAt = 1_000))
        assertEquals(HomeKeyAction.CONSUME, p.up(1_000, 1_900))
    }

    @Test fun `hold without repeats is judged on release`() {
        val p = HomeKeyPolicy(longPress)
        p.down(1_000)
        assertEquals(HomeKeyAction.OPEN_STOCK, p.up(1_000, 1_600))
    }

    @Test fun `with hold switched off every press opens BSfreeTV`() {
        val p = HomeKeyPolicy(longPress)
        p.down(1_000, hold = false)
        assertEquals(HomeKeyAction.CONSUME, p.down(1_500, repeat = 1, hold = false, downAt = 1_000))
        assertEquals(HomeKeyAction.LAUNCH_OURS, p.up(1_000, 2_000, hold = false))
    }

    @Test fun `a stray release is swallowed, not passed on as a Home press`() {
        val p = HomeKeyPolicy(longPress)
        assertEquals(HomeKeyAction.CONSUME, p.up(900, 1_000))
    }

    @Test fun `after opening google tv a replayed Home press reaches it`() {
        val p = HomeKeyPolicy(longPress)
        p.down(1_000)
        p.up(1_000, 1_700) // OPEN_STOCK
        assertEquals(HomeKeyAction.PASS, p.down(1_750))
        assertEquals(HomeKeyAction.PASS, p.up(1_750, 1_760))
        // The window is short: the next real press is ours again.
        assertEquals(HomeKeyAction.CONSUME, p.down(5_000))
        assertEquals(HomeKeyAction.LAUNCH_OURS, p.up(5_000, 5_100))
    }

    @Test fun `crash guard steps aside after two quick crashes until a clean run`() {
        assertTrue(CrashGuard.allowOverride(emptyList(), 0))
        assertTrue(CrashGuard.allowOverride(listOf(10_000), 0))
        assertFalse(CrashGuard.allowOverride(listOf(10_000, 70_000), 0))
        // Far apart is bad luck, not a crash loop.
        assertTrue(CrashGuard.allowOverride(listOf(10_000, 10_000 + CrashGuard.WINDOW_MS + 1), 0))
        // A clean run since clears it.
        assertTrue(CrashGuard.allowOverride(listOf(10_000, 70_000), 80_000))
        assertFalse(CrashGuard.allowOverride(listOf(10_000, 70_000, 90_000, 100_000), 80_000))
    }

    @Test fun `boot launch happens once per boot, early, and never on first enabling`() {
        assertTrue(BootLaunch.shouldLaunch(bootCount = 8, lastHandled = 7, uptimeMs = 40_000, enabled = true))
        assertFalse(BootLaunch.shouldLaunch(8, 8, 40_000, true)) // reconnect, same boot
        assertFalse(BootLaunch.shouldLaunch(8, 7, BootLaunch.MAX_UPTIME_MS + 1, true)) // late: maybe mid-film
        assertFalse(BootLaunch.shouldLaunch(8, null, 40_000, true)) // just switched on
        assertFalse(BootLaunch.shouldLaunch(8, 7, 40_000, false))
        assertFalse(BootLaunch.shouldLaunch(null, 7, 40_000, true))
    }

    @Test fun `enabled service detection`() {
        val pkg = "com.bsfreetv.launcher.debug"
        val cls = "com.bsfreetv.launcher.system.HomeButtonService"
        assertTrue(accessibilityServiceEnabled("com.google.android.marvin.talkback/.TalkBackService:$pkg/$cls", pkg, cls))
        assertFalse(accessibilityServiceEnabled("com.bsfreetv.launcher/$cls", pkg, cls))
        assertTrue(accessibilityServiceEnabled("$pkg/.Other:$pkg/$cls", pkg, cls))
        assertFalse(accessibilityServiceEnabled(null, pkg, cls))
        assertFalse(accessibilityServiceEnabled("", pkg, cls))
        assertFalse(accessibilityServiceEnabled("garbage", pkg, cls))
    }

    @Test fun `restricted settings step is the single appops command`() {
        val step = HomeCommands.allowRestrictedSettings("com.bsfreetv.launcher")
        assertEquals("appops set com.bsfreetv.launcher ACCESS_RESTRICTED_SETTINGS allow", step.command)
        assertFalse(step.command.contains("disable"))
        assertEquals(listOf("adb shell appops set com.bsfreetv.launcher ACCESS_RESTRICTED_SETTINGS allow"), HomeCommands.forPc(listOf(step)))
    }
}
