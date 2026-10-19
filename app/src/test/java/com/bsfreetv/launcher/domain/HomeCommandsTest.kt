package com.bsfreetv.launcher.domain

import com.bsfreetv.launcher.domain.home.AdbStep
import com.bsfreetv.launcher.domain.home.DeviceKind
import com.bsfreetv.launcher.domain.home.GOOGLE_TV_LAUNCHER
import com.bsfreetv.launcher.domain.home.HomeCommands
import com.bsfreetv.launcher.domain.home.HomeStatus
import com.bsfreetv.launcher.domain.home.StepKind
import com.bsfreetv.launcher.domain.home.StockLauncher
import com.bsfreetv.launcher.domain.home.TV_LAUNCHER
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The Home switch must never brick a device. v0.2.0 disabled Google TV's
 * launcherx and boot-looped a Google TV Streamer; these pin the fix.
 */
class HomeCommandsTest {
    private val self = "com.bsfreetv.launcher"
    private val activity = "com.bsfreetv.launcher.MainActivity"

    private fun status(vararg stock: Pair<String, Boolean>, home: String? = stock.firstOrNull()?.first, nextUp: Boolean = false) =
        HomeStatus(stock.map { StockLauncher(it.first, it.second) }, home, self, nextUp, adbEnabled = true)

    private val shield = status(TV_LAUNCHER to true)
    private val googleTv = status(GOOGLE_TV_LAUNCHER to true, TV_LAUNCHER to true)
    private val googleTvOnly = status(GOOGLE_TV_LAUNCHER to true)
    private val bricked = status(GOOGLE_TV_LAUNCHER to false, TV_LAUNCHER to false, home = null)

    @Test fun `device kinds`() {
        assertEquals(DeviceKind.CLASSIC_ANDROID_TV, shield.deviceKind)
        assertEquals(DeviceKind.GOOGLE_TV, googleTv.deviceKind)
        assertEquals(DeviceKind.GOOGLE_TV, googleTvOnly.deviceKind)
        // A device v0.2.0 already disabled is still Google TV.
        assertEquals(DeviceKind.GOOGLE_TV, bricked.deviceKind)
        assertEquals(DeviceKind.UNKNOWN, status().deviceKind)
        assertTrue(shield.canSwitchHome)
        assertFalse(googleTv.canSwitchHome)
    }

    @Test fun `google tv and unknown devices get no steps at all`() {
        listOf(googleTv, googleTvOnly, bricked, status()).forEach {
            assertTrue("$it", HomeCommands.makeDefault(it, activity).isEmpty())
        }
    }

    @Test fun `launcherx is never disabled by any plan`() {
        val all = listOf(shield, googleTv, googleTvOnly, bricked, status()).flatMap { HomeCommands.makeDefault(it, activity) }
        assertTrue(all.none { it.kind == StepKind.DISABLE && GOOGLE_TV_LAUNCHER in it.command })
        assertTrue(all.filter { it.kind == StepKind.DISABLE }.all { it.command == "pm disable-user --user 0 $TV_LAUNCHER" })
    }

    @Test fun `shield sets home first and disables tvlauncher last`() {
        val cmds = HomeCommands.makeDefault(shield, activity).map { it.command }
        assertEquals(
            listOf(
                "cmd package set-home-activity --user 0 $self/$activity",
                "pm grant $self android.permission.READ_TV_LISTINGS",
                "pm disable-user --user 0 $TV_LAUNCHER",
            ),
            cmds,
        )
        // Only the disable is required; the rest never blocks the proven path.
        assertEquals(listOf(true, true, false), HomeCommands.makeDefault(shield, activity).map { it.optional })
    }

    @Test fun `restore enables everything, launcherx included, and points home back`() {
        assertEquals(
            listOf(
                "pm enable $GOOGLE_TV_LAUNCHER",
                "pm enable $TV_LAUNCHER",
                "cmd package set-home-activity --user 0 $GOOGLE_TV_LAUNCHER",
            ),
            HomeCommands.restoreStock(bricked).map { it.command },
        )
        val shieldOff = status(TV_LAUNCHER to false, home = self)
        assertEquals(
            listOf("pm enable $TV_LAUNCHER", "cmd package set-home-activity --user 0 $TV_LAUNCHER"),
            HomeCommands.restoreStock(shieldOff).map { it.command },
        )
        // Already stock: nothing to do.
        assertTrue(HomeCommands.restoreStock(shield).isEmpty())
    }

    @Test fun `recovery commands cover both launchers`() {
        assertTrue(HomeCommands.RECOVERY.any { it.endsWith("pm enable $TV_LAUNCHER") })
        assertTrue(HomeCommands.RECOVERY.any { it.endsWith("pm enable $GOOGLE_TV_LAUNCHER") })
        assertTrue(HomeCommands.RECOVERY.any { "set-home-activity $GOOGLE_TV_LAUNCHER" in it })
    }

    @Test fun `a required failure stops the run before anything else executes`() {
        val ran = mutableListOf<String>()
        val steps = listOf(
            AdbStep(StepKind.PREFERRED_HOME, "a", optional = true),
            AdbStep(StepKind.ALLOW_NEXT_UP, "b"),
            AdbStep(StepKind.DISABLE, "c"),
        )
        val results = HomeCommands.runSteps(steps) { cmd ->
            ran += cmd
            if (cmd == "b") 1 to "Error: nope" else 0 to ""
        }
        assertEquals(listOf("a", "b"), ran)
        assertEquals(2, results.size)
        assertFalse(results.last().ok)
    }

    @Test fun `an optional failure does not stop the run`() {
        val ran = mutableListOf<String>()
        HomeCommands.runSteps(listOf(AdbStep(StepKind.PREFERRED_HOME, "a", optional = true), AdbStep(StepKind.DISABLE, "c"))) { cmd ->
            ran += cmd
            if (cmd == "a") 255 to "Unknown command" else 0 to ""
        }
        assertEquals(listOf("a", "c"), ran)
    }

    @Test fun `step success reads exit code and output`() {
        val step = HomeCommands.grantTvListings("p")
        assertTrue(step.succeeded(0, ""))
        assertFalse(step.succeeded(255, ""))
        assertFalse(step.succeeded(0, "Exception occurred while executing 'grant'"))
        assertTrue(step.succeeded(0, "Package com.google.android.tvlauncher new state: disabled-user"))
        assertEquals("adb shell pm disable-user --user 0 $TV_LAUNCHER", HomeCommands.forPc(HomeCommands.makeDefault(shield, activity)).last())
    }
}
