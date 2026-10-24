package com.bsfreetv.launcher.system

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.os.SystemClock
import android.provider.Settings
import android.util.Log
import android.view.KeyEvent
import android.view.ViewConfiguration
import android.view.accessibility.AccessibilityEvent
import com.bsfreetv.launcher.data.LauncherStore
import com.bsfreetv.launcher.domain.home.BootLaunch
import com.bsfreetv.launcher.domain.home.GOOGLE_TV_LAUNCHER
import com.bsfreetv.launcher.domain.home.HomeKeyAction
import com.bsfreetv.launcher.domain.home.HomeKeyPolicy
import com.bsfreetv.launcher.domain.home.HomeMethod
import com.bsfreetv.launcher.domain.LauncherSettings
import com.bsfreetv.launcher.MainActivity
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import kotlinx.coroutines.cancel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.SupervisorJob

/**
 * Google TV's Home button for BSfreeTV. Google TV starts its own launcher
 * directly on Home and must never be disabled (that boot-loops it), so this
 * service catches the Home key instead and opens us. It touches no other key,
 * cannot read the screen (no window content, no events) and disables nothing:
 * switching it off, or uninstalling, gives Google TV its Home button back.
 *
 * Inert unless this is Google TV, and whenever BSfreeTV keeps crashing.
 */
@AndroidEntryPoint
class HomeButtonService : AccessibilityService() {
    @Inject lateinit var store: LauncherStore
    @Inject lateinit var control: HomeControl

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val policy by lazy { HomeKeyPolicy(ViewConfiguration.getLongPressTimeout().toLong()) }
    @Volatile private var active = false
    @Volatile private var settings = LauncherSettings()

    override fun onServiceConnected() {
        super.onServiceConnected()
        active = runCatching { control.status().method == HomeMethod.ACCESSIBILITY }.getOrDefault(false)
        if (!active) return
        scope.launch {
            settings = store.settings.first()
            bootLaunch()
            store.settings.collect { settings = it }
        }
    }

    /** Once per boot, only right after it, never on a later reconnect. */
    private fun bootLaunch() {
        val boot = runCatching { Settings.Global.getInt(contentResolver, Settings.Global.BOOT_COUNT) }.getOrNull() ?: return
        val launch = BootLaunch.shouldLaunch(boot, HomeGuard.lastBootHandled(this), SystemClock.elapsedRealtime(), settings.homeOpenAfterBoot)
        HomeGuard.setBootHandled(this, boot)
        if (launch && HomeGuard.allowOverride(this)) openOurs()
    }

    override fun onKeyEvent(event: KeyEvent): Boolean {
        if (event.keyCode != KeyEvent.KEYCODE_HOME || !active) return false
        if (!HomeGuard.allowOverride(this)) return false
        val action = policy.onKey(
            keyCode = event.keyCode,
            down = event.action == KeyEvent.ACTION_DOWN,
            repeatCount = event.repeatCount,
            downTime = event.downTime,
            eventTime = event.eventTime,
            holdForStock = settings.homeHoldForStock,
        )
        return when (action) {
            HomeKeyAction.PASS -> false
            HomeKeyAction.CONSUME -> true
            HomeKeyAction.LAUNCH_OURS -> { openOurs(); true }
            HomeKeyAction.OPEN_STOCK -> { openStock(); true }
        }
    }

    /** Same intent a Home press delivers, so MainActivity resets to the first media app. */
    private fun openOurs() {
        val intent = Intent(Intent.ACTION_MAIN)
            .addCategory(Intent.CATEGORY_HOME)
            .setClass(this, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        runCatching { startActivity(intent) }.onFailure { Log.w(TAG, "cannot open BSfreeTV", it) }
    }

    private fun openStock() {
        val home = Intent(Intent.ACTION_MAIN)
            .addCategory(Intent.CATEGORY_HOME)
            .setPackage(GOOGLE_TV_LAUNCHER)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        val started = home.resolveActivity(packageManager) != null && runCatching { startActivity(home) }.isSuccess
        // The policy lets a replayed Home through for a moment, so this reaches Google TV.
        if (!started) performGlobalAction(GLOBAL_ACTION_HOME)
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) = Unit

    override fun onInterrupt() = Unit

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    private companion object {
        const val TAG = "HomeButtonService"
    }
}
