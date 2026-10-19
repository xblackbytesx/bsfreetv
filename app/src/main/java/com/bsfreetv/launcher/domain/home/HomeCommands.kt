package com.bsfreetv.launcher.domain.home

/** The stock Home apps an Android TV device may carry. Only tvlauncher is on the Shield. */
val StockLaunchers = listOf(
    "com.google.android.tvlauncher",
    "com.google.android.leanbacklauncher",
    "com.google.android.apps.tv.launcherx",
)

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
}

/** What a step does, for the UI to name it (string resources live in ui/Labels.kt). */
enum class StepKind { DISABLE, ENABLE, PREFERRED_HOME, ALLOW_NEXT_UP, READ_VERSION }

/**
 * One adb shell command plus how to recognise that it worked. [target] is the
 * package a DISABLE/ENABLE step acts on. The command itself is never translated.
 */
data class AdbStep(val kind: StepKind, val command: String, val optional: Boolean = false, val target: String? = null) {
    fun succeeded(exitCode: Int, output: String): Boolean =
        exitCode == 0 && !output.contains("Exception") && !output.contains("Error:", ignoreCase = false) && !output.contains("Unknown")
}

/** Builds the adb commands; pure so the exact strings are unit tested. */
object HomeCommands {
    /** Not a public Manifest.permission constant, but a real runtime permission on Android 11. */
    const val TV_LISTINGS = "android.permission.READ_TV_LISTINGS"

    fun makeDefault(status: HomeStatus, homeActivity: String): List<AdbStep> = buildList {
        status.stock.filter { it.enabled }.forEach {
            add(AdbStep(StepKind.DISABLE, "pm disable-user --user 0 ${it.packageName}", target = it.packageName))
        }
        // Belt and braces: with the stock Home gone we are already the only
        // candidate, but an explicit preferred Home survives other launchers
        // being installed later. Not every build has the command.
        add(AdbStep(StepKind.PREFERRED_HOME, "cmd package set-home-activity --user 0 ${status.selfPackage}/$homeActivity", optional = true))
        if (!status.tvListingsGranted) add(grantTvListings(status.selfPackage))
    }

    fun restoreStock(status: HomeStatus): List<AdbStep> =
        status.stock.filter { !it.enabled }.map { AdbStep(StepKind.ENABLE, "pm enable ${it.packageName}", target = it.packageName) }

    fun grantTvListings(selfPackage: String) = AdbStep(StepKind.ALLOW_NEXT_UP, "pm grant $selfPackage $TV_LISTINGS")

    /** The same steps for a PC, prefixed so they paste into a terminal. */
    fun forPc(steps: List<AdbStep>): List<String> = steps.map { "adb shell ${it.command}" }

    /** Recovery when the Shield is stuck without a working Home. */
    const val RECOVERY = "adb shell pm enable com.google.android.tvlauncher"
}
