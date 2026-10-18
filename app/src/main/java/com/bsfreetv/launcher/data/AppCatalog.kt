package com.bsfreetv.launcher.data

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.content.pm.ResolveInfo
import android.graphics.drawable.Drawable
import android.net.Uri
import android.provider.Settings
import android.util.Log
import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.ImageBitmap
import androidx.core.graphics.drawable.toBitmap
import com.bsfreetv.launcher.domain.layout.AppInfo
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.SupervisorJob

/**
 * An installed, launchable app with its artwork already rasterised. Banners and
 * icons are drawn once here, off the main thread, so scrolling a row never
 * touches PackageManager or a Drawable.
 */
@Immutable
data class AppEntry(
    val info: AppInfo,
    val banner: ImageBitmap?,
    val icon: ImageBitmap,
    val versionCode: Long,
) {
    val packageName get() = info.packageName
    val label get() = info.label
}

@Singleton
class AppCatalog @Inject constructor(@ApplicationContext private val context: Context) {
    private val pm = context.packageManager
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val reloads = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    private val _apps = MutableStateFlow<List<AppEntry>?>(null)

    /** Null until the first scan finishes. */
    val apps: StateFlow<List<AppEntry>?> = _apps.asStateFlow()
    private val artCache = HashMap<String, AppEntry>()

    init {
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_PACKAGE_ADDED)
            addAction(Intent.ACTION_PACKAGE_REMOVED)
            addAction(Intent.ACTION_PACKAGE_CHANGED)
            addAction(Intent.ACTION_PACKAGE_REPLACED)
            addDataScheme("package")
        }
        context.registerReceiver(
            object : BroadcastReceiver() {
                override fun onReceive(c: Context, i: Intent) { reloads.tryEmit(Unit) }
            },
            filter,
        )
        @OptIn(FlowPreview::class)
        reloads.onStart { emit(Unit) }
            .debounce(300)
            .onEach { _apps.value = runCatching { scan() }.onFailure { Log.e(TAG, "scan failed", it) }.getOrElse { _apps.value.orEmpty() } }
            .launchIn(scope)
    }

    fun refresh() { reloads.tryEmit(Unit) }

    private fun query(category: String): List<ResolveInfo> =
        pm.queryIntentActivities(Intent(Intent.ACTION_MAIN).addCategory(category), 0)

    private fun scan(): List<AppEntry> {
        val self = context.packageName
        // Leanback activities first so a TV app's own entry wins over its phone launcher one.
        val activities = (query(Intent.CATEGORY_LEANBACK_LAUNCHER) + query(Intent.CATEGORY_LAUNCHER))
            .distinctBy { it.activityInfo.packageName }
            .filter { it.activityInfo.packageName != self }
        val seen = HashSet<String>()
        val out = activities.map { ri ->
            val ai = ri.activityInfo
            val pkg = ai.packageName
            seen += pkg
            val version = runCatching { pm.getPackageInfo(pkg, 0).longVersionCode }.getOrDefault(0L)
            val label = ri.loadLabel(pm).toString().ifBlank { pkg }
            val cached = artCache[pkg]
            if (cached != null && cached.versionCode == version && cached.label == label) return@map cached
            val bannerDrawable: Drawable? = runCatching { ai.loadBanner(pm) ?: ai.applicationInfo.loadBanner(pm) }.getOrNull()
            val entry = AppEntry(
                info = AppInfo(pkg, label, hasBanner = bannerDrawable != null),
                banner = bannerDrawable?.let { runCatching { it.toBitmap(BANNER_W, BANNER_H).asImageBitmap() }.getOrNull() },
                icon = ri.loadIcon(pm).toBitmap(ICON_PX, ICON_PX).asImageBitmap(),
                versionCode = version,
            )
            artCache[pkg] = entry
            entry
        }
        artCache.keys.retainAll(seen)
        return out.sortedBy { it.label.lowercase() }
    }

    /** Start the app's TV entry point (or its phone launcher one). False when it cannot be started. */
    fun launch(pkg: String): Boolean {
        val intent = pm.getLeanbackLaunchIntentForPackage(pkg) ?: pm.getLaunchIntentForPackage(pkg) ?: return false
        return start(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED))
    }

    fun openAppInfo(pkg: String) = start(
        Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", pkg, null)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
    )

    fun uninstall(pkg: String) = start(
        Intent(Intent.ACTION_DELETE, Uri.fromParts("package", pkg, null)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
    )

    fun openSystemSettings() = start(Intent(Settings.ACTION_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))

    fun start(intent: Intent): Boolean = try {
        context.startActivity(intent)
        true
    } catch (e: Exception) {
        Log.w(TAG, "cannot start $intent", e)
        false
    }

    private companion object {
        const val TAG = "AppCatalog"
        // android:banner is 320x180 px at xhdpi, the Shield's density.
        const val BANNER_W = 320
        const val BANNER_H = 180
        const val ICON_PX = 128
    }
}

/** Enabled state of an installed package; null when it is not installed (or not visible). */
fun PackageManager.isPackageEnabled(pkg: String): Boolean? = runCatching {
    when (getApplicationEnabledSetting(pkg)) {
        PackageManager.COMPONENT_ENABLED_STATE_ENABLED -> true
        PackageManager.COMPONENT_ENABLED_STATE_DEFAULT -> getApplicationInfo(pkg, PackageManager.MATCH_DISABLED_COMPONENTS).enabled
        else -> false
    }
}.getOrNull()
