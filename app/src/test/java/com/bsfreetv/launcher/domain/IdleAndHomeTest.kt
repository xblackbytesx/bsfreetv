package com.bsfreetv.launcher.domain

import com.bsfreetv.launcher.data.WatchNext
import com.bsfreetv.launcher.domain.home.HomeCommands
import com.bsfreetv.launcher.domain.home.HomeStatus
import com.bsfreetv.launcher.domain.home.StockLauncher
import com.bsfreetv.launcher.domain.idle.IdlePhase
import com.bsfreetv.launcher.domain.idle.IdlePolicy
import com.bsfreetv.launcher.domain.idle.PixelShift
import com.bsfreetv.launcher.feature.settings.after
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class IdleAndHomeTest {
    private val min = 60_000L

    @Test fun `idle phases follow the policy`() {
        val p = IdlePolicy(3 * min, 10 * min)
        assertEquals(IdlePhase.ACTIVE, p.phase(0))
        assertEquals(IdlePhase.AMBIENT, p.phase(3 * min))
        assertEquals(IdlePhase.AMBIENT, p.phase(12 * min))
        assertEquals(IdlePhase.BLACK, p.phase(13 * min))
        assertEquals(3 * min, p.nextChangeIn(0))
        assertEquals(10 * min, p.nextChangeIn(3 * min))
        assertNull(p.nextChangeIn(13 * min))
    }

    @Test fun `disabled stages are skipped`() {
        assertEquals(IdlePhase.ACTIVE, IdlePolicy(0, 0).phase(99 * min))
        assertNull(IdlePolicy(0, 0).nextChangeIn(0))
        assertEquals(IdlePhase.AMBIENT, IdlePolicy(3 * min, 0).phase(99 * min))
        assertEquals(IdlePhase.BLACK, IdlePolicy(0, 5 * min).phase(5 * min))
    }

    @Test fun `pixel shift stays within radius and actually moves`() {
        val offsets = (0 until 9).map { PixelShift.offset(it * 3 * min) }
        assertTrue(offsets.all { (x, y) -> x in -4..4 && y in -4..4 })
        assertEquals(9, offsets.toSet().size)
    }

    private val status = HomeStatus(
        stock = listOf(StockLauncher("com.google.android.tvlauncher", true)),
        currentHome = "com.google.android.tvlauncher",
        selfPackage = "com.bsfreetv.launcher",
        tvListingsGranted = false,
        adbEnabled = true,
    )

    @Test fun `make default disables the stock launcher, prefers us and grants Next Up`() {
        val cmds = HomeCommands.makeDefault(status, "com.bsfreetv.launcher.MainActivity").map { it.command }
        assertEquals(
            listOf(
                "pm disable-user --user 0 com.google.android.tvlauncher",
                "cmd package set-home-activity --user 0 com.bsfreetv.launcher/com.bsfreetv.launcher.MainActivity",
                "pm grant com.bsfreetv.launcher android.permission.READ_TV_LISTINGS",
            ),
            cmds,
        )
        assertEquals("adb shell pm disable-user --user 0 com.google.android.tvlauncher", HomeCommands.forPc(HomeCommands.makeDefault(status, "x")).first())
    }

    @Test fun `restore only re-enables what is disabled`() {
        assertTrue(HomeCommands.restoreStock(status).isEmpty())
        val disabled = status.copy(stock = listOf(StockLauncher("com.google.android.tvlauncher", false)))
        assertEquals(listOf("pm enable com.google.android.tvlauncher"), HomeCommands.restoreStock(disabled).map { it.command })
    }

    @Test fun `step success reads exit code and output`() {
        val step = HomeCommands.grantTvListings("p")
        assertTrue(step.succeeded(0, ""))
        assertFalse(step.succeeded(255, ""))
        assertFalse(step.succeeded(0, "Exception occurred while executing 'grant'"))
        assertTrue(step.succeeded(0, "Package com.google.android.tvlauncher new state: disabled-user"))
    }

    @Test fun `next up text helpers`() {
        assertEquals("S2 · E5 · The Bridge", WatchNext.episodeLine("2", "5", "The Bridge"))
        assertNull(WatchNext.episodeLine(null, null, null))
        assertEquals(0.25f, WatchNext.progress(250, 1000)!!, 0.0001f)
        assertNull(WatchNext.progress(0, 1000))
        assertNull(WatchNext.progress(10, 0))
    }

    @Test fun `cycling wraps`() {
        assertEquals(Motion.FULL, Motion.entries.after(Motion.OFF))
        assertEquals(3, LauncherSettings.AmbientSteps.after(1))
    }
}
