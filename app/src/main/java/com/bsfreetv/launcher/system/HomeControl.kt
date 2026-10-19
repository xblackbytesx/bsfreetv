package com.bsfreetv.launcher.system

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.provider.Settings
import android.util.Log
import com.bsfreetv.launcher.data.isPackageEnabled
import com.bsfreetv.launcher.domain.home.AdbStep
import com.bsfreetv.launcher.domain.home.HomeCommands
import com.bsfreetv.launcher.domain.home.HomeStatus
import com.bsfreetv.launcher.domain.home.StockLauncher
import com.bsfreetv.launcher.domain.home.StockLaunchers
import com.bsfreetv.launcher.domain.home.StepKind
import com.bsfreetv.launcher.domain.home.StepResult
import com.bsfreetv.launcher.MainActivity
import dadb.AdbKeyPair
import dadb.Dadb
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.net.ConnectException
import java.net.SocketTimeoutException
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Why self-ADB failed; the UI words it (ui/Labels.kt). */
enum class AdbError { UNREACHABLE, PROMPT_TIMEOUT, REFUSED, OTHER }

sealed interface AdbOutcome {
    /** [stopped]: a required step failed and the steps after it were not run. */
    data class Done(val results: List<StepResult>, val stopped: Boolean = false) : AdbOutcome {
        val ok get() = results.all { it.ok || it.step.optional }
    }
    data class Failed(val error: AdbError, val detail: String? = null) : AdbOutcome
}

/**
 * Status of the Home button and "self-ADB": the launcher connects as an adb
 * client to the device's own adbd (Developer options > Network debugging,
 * 127.0.0.1:5555). The first connection shows Android's "Allow debugging?"
 * prompt on the TV; once accepted (tick "Always allow") it works silently.
 */
@Singleton
class HomeControl @Inject constructor(@ApplicationContext private val context: Context) {
    private val pm = context.packageManager

    val homeActivity: String = MainActivity::class.java.name

    fun status(): HomeStatus {
        val home = runCatching {
            pm.resolveActivity(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME), PackageManager.MATCH_DEFAULT_ONLY)
                ?.activityInfo?.packageName
        }.getOrNull()
        return HomeStatus(
            stock = StockLaunchers.mapNotNull { pkg -> pm.isPackageEnabled(pkg)?.let { StockLauncher(pkg, it) } },
            // "android" is the chooser: no single Home.
            currentHome = home?.takeIf { it != "android" },
            selfPackage = context.packageName,
            tvListingsGranted = context.checkSelfPermission(HomeCommands.TV_LISTINGS) == PackageManager.PERMISSION_GRANTED,
            adbEnabled = Settings.Global.getInt(context.contentResolver, Settings.Global.ADB_ENABLED, 0) == 1,
        )
    }

    /** Run [steps] over self-ADB. Blocks for up to [promptTimeoutMs] while the TV shows the prompt. */
    suspend fun run(steps: List<AdbStep>, promptTimeoutMs: Int = 60_000): AdbOutcome = withContext(Dispatchers.IO) {
        if (steps.isEmpty()) return@withContext AdbOutcome.Done(emptyList())
        try {
            Dadb.create(HOST, PORT, keyPair(), CONNECT_TIMEOUT_MS, promptTimeoutMs).use { adb ->
                // Stops at the first required failure: never disable after something went wrong.
                val results = HomeCommands.runSteps(steps) { cmd -> adb.shell(cmd).let { it.exitCode to it.allOutput } }
                AdbOutcome.Done(results, stopped = results.size < steps.size)
            }
        } catch (e: Exception) {
            Log.w(TAG, "self-adb failed", e)
            AdbOutcome.Failed(classify(e), e.message ?: e.javaClass.simpleName)
        }
    }

    /** Proves the connection works without changing anything: reads the Android version. */
    suspend fun probe(): AdbOutcome = run(listOf(AdbStep(StepKind.READ_VERSION, "getprop ro.build.version.release")))

    private fun classify(e: Throwable): AdbError {
        val chain = generateSequence(e) { it.cause }.toList()
        return when {
            chain.any { it is ConnectException } -> AdbError.UNREACHABLE
            chain.any { it is SocketTimeoutException } -> AdbError.PROMPT_TIMEOUT
            chain.any { it is dadb.AdbAuthException } -> AdbError.REFUSED
            else -> AdbError.OTHER
        }
    }

    private fun keyPair(): AdbKeyPair {
        val dir = File(context.filesDir, "adb").apply { mkdirs() }
        val priv = File(dir, "adbkey")
        val pub = File(dir, "adbkey.pub")
        if (!priv.exists() || !pub.exists()) AdbKeyPair.generate(priv, pub)
        return AdbKeyPair.read(priv, pub)
    }

    private companion object {
        const val TAG = "HomeControl"
        const val HOST = "127.0.0.1"
        const val PORT = 5555
        const val CONNECT_TIMEOUT_MS = 3_000
    }
}
