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

/** Outcome of one step sent over self-ADB. */
data class StepResult(val step: AdbStep, val ok: Boolean, val output: String)

sealed interface AdbOutcome {
    data class Done(val results: List<StepResult>) : AdbOutcome {
        val ok get() = results.all { it.ok || it.step.optional }
    }
    data class Failed(val reason: String) : AdbOutcome
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
                AdbOutcome.Done(
                    steps.map { step ->
                        val r = adb.shell(step.command)
                        StepResult(step, step.succeeded(r.exitCode, r.allOutput), r.allOutput.trim())
                    },
                )
            }
        } catch (e: Exception) {
            Log.w(TAG, "self-adb failed", e)
            AdbOutcome.Failed(explain(e))
        }
    }

    /** Proves the connection works without changing anything: reads the Android version. */
    suspend fun probe(): AdbOutcome = run(listOf(AdbStep("Read Android version", "getprop ro.build.version.release")))

    private fun explain(e: Throwable): String {
        val chain = generateSequence(e) { it.cause }.toList()
        return when {
            chain.any { it is ConnectException } ->
                "Could not reach adb on this device. Turn on Settings > Device Preferences > Developer options > Network debugging, then try again."
            chain.any { it is SocketTimeoutException } ->
                "No answer from the TV prompt. Choose Allow (tick Always allow) when it appears, then try again."
            chain.any { it is dadb.AdbAuthException } ->
                "The TV refused the connection. Try again and choose Allow on the prompt."
            else -> "Self-ADB failed: ${e.message ?: e.javaClass.simpleName}"
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
