package com.bsfreetv.launcher.domain.home

/** Classic Android TV Home (the Shield). The only launcher we ever disable. */
const val TV_LAUNCHER = "com.google.android.tvlauncher"

/** Google TV Home. Never disabled: the Home key starts it explicitly, so a disabled one boot-loops the device. */
const val GOOGLE_TV_LAUNCHER = "com.google.android.apps.tv.launcherx"

/** The stock Home apps an Android TV device may carry, for status and restore only. */
val StockLaunchers = listOf(
    TV_LAUNCHER,
    "com.google.android.leanbacklauncher",
    GOOGLE_TV_LAUNCHER,
)

/**
 * Which Home-switch strategy is safe here. Only CLASSIC_ANDROID_TV (stock Home
 * is tvlauncher, no Google TV launcher present) is verified: the Shield.
 */
enum class DeviceKind { CLASSIC_ANDROID_TV, GOOGLE_TV, UNKNOWN }

data class StockLauncher(val packageName: String, val enabled: Boolean)

/** What the Default launcher screen knows about the device right now. */
data class HomeStatus(
    /** Installed stock launchers and whether each is enabled. */
    val stock: List<StockLauncher>,
    /** Package that the Home button currently resolves to, or null when ambiguous. */
    val currentHome: String?,
    val selfPackage: String,
    val tvListingsGranted: Boolean,
    val adbEnabled: Boolean,
) {
    val weAreHome get() = currentHome == selfPackage
    val stockEnabled get() = stock.any { it.enabled }

    /** Google TV wins whenever its launcher is installed at all, even disabled (a v0.2.0 victim). */
    val deviceKind: DeviceKind
        get() = when {
            stock.any { it.packageName == GOOGLE_TV_LAUNCHER } -> DeviceKind.GOOGLE_TV
            stock.any { it.packageName == TV_LAUNCHER } -> DeviceKind.CLASSIC_ANDROID_TV
            else -> DeviceKind.UNKNOWN
        }

    val canSwitchHome get() = deviceKind == DeviceKind.CLASSIC_ANDROID_TV
}

/** What a step does, for the UI to name it (string resources live in ui/Labels.kt). */
enum class StepKind { DISABLE, ENABLE, PREFERRED_HOME, RESTORE_HOME, ALLOW_NEXT_UP, READ_VERSION }

/**
 * One adb shell command plus how to recognise that it worked. [target] is the
 * package a DISABLE/ENABLE step acts on. The command itself is never translated.
 * A failed step that is not [optional] stops the run: nothing after it executes.
 */
data class AdbStep(val kind: StepKind, val command: String, val optional: Boolean = false, val target: String? = null) {
    fun succeeded(exitCode: Int, output: String): Boolean =
        exitCode == 0 && !output.contains("Exception") && !output.contains("Error:", ignoreCase = false) && !output.contains("Unknown")
}

/** Result of one executed step. Steps after a required failure are not run and have no result. */
data class StepResult(val step: AdbStep, val ok: Boolean, val output: String)

/** Builds the adb commands; pure so the exact strings are unit tested. */
object HomeCommands {
    /** Not a public Manifest.permission constant, but a real runtime permission on Android 11. */
    const val TV_LISTINGS = "android.permission.READ_TV_LISTINGS"

    /**
     * Make us Home. Safe by construction:
     * - nothing at all unless this is a classic Android TV (tvlauncher, no Google TV);
     * - only tvlauncher is ever disabled, and only as the very last step, after
     *   our Home is set and nothing required has failed.
     */
    fun makeDefault(status: HomeStatus, homeActivity: String): List<AdbStep> {
        if (!status.canSwitchHome) return emptyList()
        return buildList {
            // Not every build has the command, and on the Shield disabling
            // tvlauncher alone already hands us Home (the proven v0.1 path).
            add(AdbStep(StepKind.PREFERRED_HOME, "cmd package set-home-activity --user 0 ${status.selfPackage}/$homeActivity", optional = true))
            if (!status.tvListingsGranted) add(grantTvListings(status.selfPackage).copy(optional = true))
            if (status.stock.any { it.packageName == TV_LAUNCHER && it.enabled }) {
                add(AdbStep(StepKind.DISABLE, "pm disable-user --user 0 $TV_LAUNCHER", target = TV_LAUNCHER))
            }
        }
    }

    /**
     * Undo, on any device: enable every installed stock launcher (launcherx too,
     * for devices v0.2.0 broke) and point Home back at the stock one.
     */
    fun restoreStock(status: HomeStatus): List<AdbStep> = buildList {
        status.stock.filter { !it.enabled }.forEach {
            add(AdbStep(StepKind.ENABLE, "pm enable ${it.packageName}", target = it.packageName))
        }
        val stockHome = when (status.deviceKind) {
            DeviceKind.GOOGLE_TV -> GOOGLE_TV_LAUNCHER
            DeviceKind.CLASSIC_ANDROID_TV -> TV_LAUNCHER
            DeviceKind.UNKNOWN -> null
        }
        if (stockHome != null && (status.currentHome != stockHome || status.stock.any { !it.enabled })) {
            add(AdbStep(StepKind.RESTORE_HOME, "cmd package set-home-activity --user 0 $stockHome", optional = true, target = stockHome))
        }
    }

    fun grantTvListings(selfPackage: String) = AdbStep(StepKind.ALLOW_NEXT_UP, "pm grant $selfPackage $TV_LISTINGS")

    /** Execute in order, stopping at the first failed required step. */
    fun runSteps(steps: List<AdbStep>, exec: (String) -> Pair<Int, String>): List<StepResult> {
        val out = ArrayList<StepResult>()
        for (step in steps) {
            val (code, output) = exec(step.command)
            val ok = step.succeeded(code, output)
            out += StepResult(step, ok, output.trim())
            if (!ok && !step.optional) break
        }
        return out
    }

    /** The same steps for a PC, prefixed so they paste into a terminal. */
    fun forPc(steps: List<AdbStep>): List<String> = steps.map { "adb shell ${it.command}" }

    /** Recovery when a device is stuck without a working Home (classic Android TV and Google TV). */
    val RECOVERY = listOf(
        "adb shell pm enable $TV_LAUNCHER",
        "adb shell pm enable $GOOGLE_TV_LAUNCHER",
        "adb shell cmd package set-home-activity $GOOGLE_TV_LAUNCHER",
    )
}
