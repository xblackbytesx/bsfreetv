package com.bsfreetv.baselineprofile

import android.view.KeyEvent
import androidx.benchmark.macro.junit4.BaselineProfileRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Rule
import org.junit.runner.RunWith
import org.junit.Test

/**
 * Cold start plus the paths that must never stutter: D-pad along the media
 * row, down into the tools, and opening All apps and Settings.
 */
@RunWith(AndroidJUnit4::class)
class HomeProfileGenerator {
    @get:Rule val rule = BaselineProfileRule()

    @Test
    fun generate() {
        val pkg = InstrumentationRegistry.getArguments().getString("targetAppId") ?: "com.bsfreetv.launcher"
        rule.collect(packageName = pkg, includeInStartupProfile = true) {
            pressHome()
            startActivityAndWait()
            device.waitForIdle()
            repeat(4) { device.pressDPadRight(); device.waitForIdle(200) }
            repeat(4) { device.pressDPadLeft(); device.waitForIdle(200) }
            device.pressDPadDown()
            device.pressDPadRight()
            device.waitForIdle(300)
            // Up to the top bar, open All apps, scroll, back.
            repeat(3) { device.pressDPadUp() }
            device.pressDPadCenter()
            device.waitForIdle(500)
            repeat(6) { device.pressDPadDown(); device.waitForIdle(150) }
            device.pressBack()
            device.waitForIdle(300)
            // Settings panel.
            repeat(3) { device.pressDPadUp() }
            device.pressDPadRight()
            device.pressDPadCenter()
            device.waitForIdle(500)
            repeat(8) { device.pressDPadDown(); device.waitForIdle(120) }
            device.pressKeyCode(KeyEvent.KEYCODE_BACK)
            device.waitForIdle(300)
        }
    }
}
