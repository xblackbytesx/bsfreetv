package com.bsfreetv.launcher.system

import android.annotation.SuppressLint
import android.content.Context
import com.bsfreetv.launcher.domain.home.CrashGuard

/**
 * The few facts the Home button service needs to stay safe, in plain
 * SharedPreferences: written synchronously (a crash handler may be the
 * writer, right before the process dies) and read on each Home press.
 */
object HomeGuard {
    private const val PREFS = "home_guard"
    private const val CRASHES = "crashes"
    private const val CLEAN_RUN = "clean_run"
    private const val BOOT_HANDLED = "boot_handled"
    private const val KEEP_CRASHES = 5

    private fun prefs(context: Context) = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    /** Chains onto the default handler: records the crash time, then lets the crash proceed. */
    fun installCrashRecorder(context: Context) {
        val app = context.applicationContext
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, error ->
            runCatching { recordCrash(app, System.currentTimeMillis()) }
            previous?.uncaughtException(thread, error)
        }
    }

    // commit, not apply: the process is about to die.
    @SuppressLint("ApplySharedPref")
    fun recordCrash(context: Context, now: Long) {
        val list = (crashes(context) + now).takeLast(KEEP_CRASHES)
        prefs(context).edit().putString(CRASHES, list.joinToString(",")).commit()
    }

    fun markCleanRun(context: Context, now: Long) {
        prefs(context).edit().putLong(CLEAN_RUN, now).apply()
    }

    fun allowOverride(context: Context): Boolean =
        CrashGuard.allowOverride(crashes(context), prefs(context).getLong(CLEAN_RUN, 0))

    fun lastBootHandled(context: Context): Int? =
        prefs(context).getInt(BOOT_HANDLED, -1).takeIf { it >= 0 }

    fun setBootHandled(context: Context, bootCount: Int) {
        prefs(context).edit().putInt(BOOT_HANDLED, bootCount).apply()
    }

    private fun crashes(context: Context): List<Long> =
        prefs(context).getString(CRASHES, null).orEmpty().split(',').mapNotNull { it.toLongOrNull() }
}
