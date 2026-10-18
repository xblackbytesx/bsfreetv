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

/** One adb shell command plus how to recognise that it worked. */
data class AdbStep(val label: String, val command: String, val optional: Boolean = false) {
    fun succeeded(exitCode: Int, output: String): Boolean =
        exitCode == 0 && !output.contains("Exception") && !output.contains("Error:", ignoreCase = false) && !output.contains("Unknown")
}

/** Builds the adb commands; pure so the exact strings are unit tested. */
object HomeCommands {
    /** Not a public Manifest.permission constant, but a real runtime permission on Android 11. */
    const val TV_LISTINGS = "android.permission.READ_TV_LISTINGS"

    fun makeDefault(status: HomeStatus, homeActivity: String): List<AdbStep> = buildList {
        status.stock.filter { it.enabled }.forEach {
            add(AdbStep("Disable ${it.packageName}", "pm disable-user --user 0 ${it.packageName}"))
        }
        // Belt and braces: with the stock Home gone we are already the only
        // candidate, but an explicit preferred Home survives other launchers
        // being installed later. Not every build has the command.
        add(AdbStep("Set preferred Home", "cmd package set-home-activity --user 0 ${status.selfPackage}/$homeActivity", optional = true))
        if (!status.tvListingsGranted) add(grantTvListings(status.selfPackage))
    }

    fun restoreStock(status: HomeStatus): List<AdbStep> =
        status.stock.filter { !it.enabled }.map { AdbStep("Enable ${it.packageName}", "pm enable ${it.packageName}") }

    fun grantTvListings(selfPackage: String) = AdbStep("Allow Next Up", "pm grant $selfPackage $TV_LISTINGS")

    /** The same steps for a PC, prefixed so they paste into a terminal. */
    fun forPc(steps: List<AdbStep>): List<String> = steps.map { "adb shell ${it.command}" }

    /** Recovery when the Shield is stuck without a working Home. */
    const val RECOVERY = "adb shell pm enable com.google.android.tvlauncher"
}
