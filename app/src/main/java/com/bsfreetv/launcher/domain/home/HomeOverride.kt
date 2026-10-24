package com.bsfreetv.launcher.domain.home

/**
 * How BSfreeTV may become Home on this device. Classic Android TV uses the ADB
 * switch (simpler, survives everything); Google TV uses the accessibility
 * service, which disables nothing; anything else gets neither.
 */
enum class HomeMethod { ADB_SWITCH, ACCESSIBILITY, NONE }

val DeviceKind.homeMethod: HomeMethod
    get() = when (this) {
        DeviceKind.CLASSIC_ANDROID_TV -> HomeMethod.ADB_SWITCH
        DeviceKind.GOOGLE_TV -> HomeMethod.ACCESSIBILITY
        DeviceKind.UNKNOWN -> HomeMethod.NONE
    }

enum class HomeKeyAction {
    /** Not ours: the key goes on to the system unchanged. */
    PASS,
    /** Swallow it, do nothing (the other half of a press already handled). */
    CONSUME,
    LAUNCH_OURS,
    /** Hold Home: show the stock Google TV home screen. */
    OPEN_STOCK,
}

/**
 * Decides what the Home button service does with each key event. Only the Home
 * key is ever touched. A short press opens BSfreeTV; a hold opens the stock
 * home when [holdForStock] is on. Stateful across one press (down, repeats, up).
 */
class HomeKeyPolicy(private val longPressMs: Long) {
    private var pressed = false
    private var longHandled = false
    /**
     * After we open the stock home, the system may replay Home to get there: a
     * new press starting within [STOCK_PASS_MS] goes through, whole. The rest
     * of the press that triggered it (repeats, release) is still ours.
     */
    private var stockAt = Long.MIN_VALUE
    private var passedPress: Long? = null

    fun onKey(keyCode: Int, down: Boolean, repeatCount: Int, downTime: Long, eventTime: Long, holdForStock: Boolean): HomeKeyAction {
        if (keyCode != KEYCODE_HOME) return HomeKeyAction.PASS
        if (downTime == passedPress) return HomeKeyAction.PASS
        if (down && repeatCount == 0 && downTime > stockAt && downTime <= stockAt + STOCK_PASS_MS) {
            passedPress = downTime
            return HomeKeyAction.PASS
        }
        if (down) {
            if (repeatCount == 0) {
                pressed = true
                longHandled = false
                return HomeKeyAction.CONSUME
            }
            // Auto-repeat while held: act once, as soon as the hold is clear.
            if (pressed && !longHandled && holdForStock) {
                longHandled = true
                return stock(eventTime)
            }
            return HomeKeyAction.CONSUME
        }
        val wasPressed = pressed
        pressed = false
        // A stray up (its down went elsewhere) is swallowed: passing it could act as Home.
        if (!wasPressed || longHandled) return HomeKeyAction.CONSUME
        return if (holdForStock && eventTime - downTime >= longPressMs) stock(eventTime) else HomeKeyAction.LAUNCH_OURS
    }

    private fun stock(now: Long): HomeKeyAction {
        stockAt = now
        return HomeKeyAction.OPEN_STOCK
    }

    companion object {
        /** android.view.KeyEvent.KEYCODE_HOME, kept here so the policy stays plain Kotlin. */
        const val KEYCODE_HOME = 3
        const val STOCK_PASS_MS = 1_000L
    }
}

/**
 * A broken BSfreeTV must never hold the Home button hostage: after
 * [MAX_CRASHES] crashes within [WINDOW_MS] of each other since the last clean
 * run, Home goes back to Google TV until BSfreeTV has run cleanly again.
 */
object CrashGuard {
    const val MAX_CRASHES = 2
    const val WINDOW_MS = 2 * 60_000L
    /** How long the launcher must stay resumed to count as a clean run. */
    const val CLEAN_RUN_MS = 10_000L

    fun allowOverride(crashTimes: List<Long>, lastCleanRun: Long): Boolean {
        val recent = crashTimes.filter { it > lastCleanRun }.sorted()
        return recent.windowed(MAX_CRASHES).none { it.last() - it.first() <= WINDOW_MS }
    }
}

/**
 * Google TV shows its own home after a restart; BSfreeTV may replace it once,
 * right after boot. Never later: a service reconnect mid-film must not pop up.
 */
object BootLaunch {
    const val MAX_UPTIME_MS = 3 * 60_000L

    /** [lastHandled] null = the service has never run: first enabling only records the boot. */
    fun shouldLaunch(bootCount: Int?, lastHandled: Int?, uptimeMs: Long, enabled: Boolean): Boolean =
        enabled && bootCount != null && lastHandled != null && bootCount != lastHandled && uptimeMs <= MAX_UPTIME_MS
}

/** Whether [component] (flattened "pkg/cls") is in Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES. */
fun accessibilityServiceEnabled(setting: String?, pkg: String, cls: String): Boolean =
    setting.orEmpty().split(':').any { entry ->
        val (p, c) = entry.trim().split('/', limit = 2).takeIf { it.size == 2 } ?: return@any false
        p == pkg && (c == cls || (c.startsWith(".") && pkg + c == cls))
    }
