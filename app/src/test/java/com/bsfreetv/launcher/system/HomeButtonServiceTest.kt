package com.bsfreetv.launcher.system

import android.content.ComponentName
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.view.KeyEvent
import androidx.test.core.app.ApplicationProvider
import com.bsfreetv.launcher.domain.home.GOOGLE_TV_LAUNCHER
import com.bsfreetv.launcher.domain.home.TV_LAUNCHER
import com.bsfreetv.launcher.MainActivity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/** The real service with synthetic key events: what it consumes and what it starts. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class HomeButtonServiceTest {
    private val app = ApplicationProvider.getApplicationContext<android.app.Application>()

    private fun install(pkg: String) {
        shadowOf(app.packageManager).installPackage(
            PackageInfo().apply {
                packageName = pkg
                applicationInfo = ApplicationInfo().apply { packageName = pkg; enabled = true }
            },
        )
    }

    private fun connected(): HomeButtonService {
        val service = Robolectric.buildService(HomeButtonService::class.java).create().get()
        HomeButtonService::class.java.getDeclaredMethod("onServiceConnected").apply { isAccessible = true }.invoke(service)
        return service
    }

    private fun HomeButtonService.key(action: Int, code: Int, downTime: Long = 1_000, eventTime: Long = downTime): Boolean =
        HomeButtonService::class.java.getDeclaredMethod("onKeyEvent", KeyEvent::class.java)
            .apply { isAccessible = true }
            .invoke(this, KeyEvent(downTime, eventTime, action, code, 0)) as Boolean

    private fun nextStarted(): Intent? = shadowOf(app).nextStartedActivity

    @Test fun `google tv - Home opens BSfreeTV as a Home intent, other keys pass`() {
        install(GOOGLE_TV_LAUNCHER)
        val s = connected()
        assertFalse(s.key(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_DPAD_CENTER))
        assertFalse(s.key(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_BACK))
        assertTrue(s.key(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_HOME, 1_000))
        assertTrue(s.key(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_HOME, 1_000, 1_100))
        val started = nextStarted()!!
        assertEquals(MainActivity::class.java.name, started.component?.className)
        assertTrue(started.hasCategory(Intent.CATEGORY_HOME))
    }

    @Test fun `shield - the service is inert even if switched on`() {
        install(TV_LAUNCHER)
        val s = connected()
        assertFalse(s.key(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_HOME, 1_000))
        assertFalse(s.key(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_HOME, 1_000, 1_100))
        assertNull(nextStarted())
    }

    private fun serviceState() =
        app.packageManager.getComponentEnabledSetting(ComponentName(app, HomeButtonService::class.java))

    @Test fun `the service is only listed on google tv`() {
        install(TV_LAUNCHER)
        HomeControl(app).syncHomeButtonService()
        assertEquals(PackageManager.COMPONENT_ENABLED_STATE_DISABLED, serviceState())
    }

    @Test fun `google tv switches the service component on`() {
        install(GOOGLE_TV_LAUNCHER)
        HomeControl(app).syncHomeButtonService()
        assertEquals(PackageManager.COMPONENT_ENABLED_STATE_ENABLED, serviceState())
    }

    @Test fun `after a crash loop Home goes back to google tv`() {
        install(GOOGLE_TV_LAUNCHER)
        val now = System.currentTimeMillis()
        HomeGuard.recordCrash(app, now - 20_000)
        HomeGuard.recordCrash(app, now - 10_000)
        val s = connected()
        assertFalse(s.key(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_HOME, 1_000))
        assertFalse(s.key(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_HOME, 1_000, 1_100))
        assertNull(nextStarted())
        // A clean run hands Home back to us.
        HomeGuard.markCleanRun(app, now)
        assertTrue(s.key(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_HOME, 2_000))
    }
}
