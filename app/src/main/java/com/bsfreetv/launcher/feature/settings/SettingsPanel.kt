package com.bsfreetv.launcher.feature.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.bsfreetv.launcher.BuildConfig
import com.bsfreetv.launcher.data.AppEntry
import com.bsfreetv.launcher.domain.CardSize
import com.bsfreetv.launcher.domain.home.HomeCommands
import com.bsfreetv.launcher.domain.home.HomeStatus
import com.bsfreetv.launcher.domain.home.StockLaunchers
import com.bsfreetv.launcher.domain.LauncherSettings
import com.bsfreetv.launcher.domain.Motion
import com.bsfreetv.launcher.domain.palette.PaletteBuilder
import com.bsfreetv.launcher.domain.Rotation
import com.bsfreetv.launcher.domain.ToolsStyle
import com.bsfreetv.launcher.feature.home.HomeActions
import com.bsfreetv.launcher.feature.home.HomeUi
import com.bsfreetv.launcher.feature.home.Overlay
import com.bsfreetv.launcher.feature.home.SettingsPage
import com.bsfreetv.launcher.system.AdbOutcome
import com.bsfreetv.launcher.ui.BsfType
import com.bsfreetv.launcher.ui.components.PanelButton
import com.bsfreetv.launcher.ui.components.SectionHeader
import com.bsfreetv.launcher.ui.components.SettingRow
import com.bsfreetv.launcher.ui.components.StatusLine
import com.bsfreetv.launcher.ui.components.trapFocus
import com.bsfreetv.launcher.ui.LocalBsfColors
import com.bsfreetv.launcher.ui.toColors

/** Cycle to the next value of a list, wrapping. */
fun <T> List<T>.after(current: T): T = this[(indexOf(current) + 1).mod(size)]

private fun minutes(n: Int) = if (n == 0) "Never" else "$n min"

/** Everything the settings pages need beyond [HomeUi]. */
data class SettingsEnv(
    val wallpaperTitle: String,
    val nextUpPermission: Boolean,
    val homeStatus: HomeStatus?,
    val setupState: SetupState,
    val pcCommands: List<String>,
    val userDir: String?,
)

interface SettingsActions {
    fun requestNextUpPermission()
    fun probe()
    fun makeDefault()
    fun restoreStock()
    fun grantNextUp()
    fun refreshStatus()

    object None : SettingsActions {
        override fun requestNextUpPermission() = Unit
        override fun probe() = Unit
        override fun makeDefault() = Unit
        override fun restoreStock() = Unit
        override fun grantNextUp() = Unit
        override fun refreshStatus() = Unit
    }
}

/**
 * Right-hand panel over the dimmed home. Only settings that change something
 * visible or protect the screen; each row cycles its value on OK.
 */
@Composable
fun SettingsPanel(page: SettingsPage, ui: HomeUi, env: SettingsEnv, actions: HomeActions, setup: SettingsActions) {
    val colors = LocalBsfColors.current
    val first = remember(page) { FocusRequester() }
    LaunchedEffect(page) {
        withFrameNanos { }
        runCatching { first.requestFocus() }
    }
    Box(Modifier.fillMaxSize().background(Brush.horizontalGradient(0f to Color.Transparent, 0.45f to Color.Black.copy(alpha = 0.55f), 1f to Color.Black.copy(alpha = 0.8f)))) {
        Column(
            Modifier
                .align(Alignment.CenterEnd)
                .fillMaxHeight()
                .width(430.dp)
                .background(colors.surface)
                .trapFocus()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 18.dp, vertical = 26.dp),
        ) {
            Text(
                when (page) {
                    SettingsPage.MAIN -> "Settings"
                    SettingsPage.DEFAULT_HOME -> "Default launcher"
                    SettingsPage.NEXT_UP_APPS -> "Next Up apps"
                    SettingsPage.ABOUT -> "About"
                },
                style = BsfType.title,
                color = colors.text,
                modifier = Modifier.padding(start = 14.dp, bottom = 4.dp),
            )
            when (page) {
                SettingsPage.MAIN -> MainPage(ui, env, actions, setup, first)
                SettingsPage.DEFAULT_HOME -> DefaultHomePage(env, setup, first)
                SettingsPage.NEXT_UP_APPS -> NextUpAppsPage(ui, actions, first)
                SettingsPage.ABOUT -> AboutPage(env, first)
            }
        }
    }
}

@Composable
private fun MainPage(ui: HomeUi, env: SettingsEnv, actions: HomeActions, setup: SettingsActions, first: FocusRequester) {
    val s = ui.settings
    fun set(t: (LauncherSettings) -> LauncherSettings) = actions.updateSettings(t)
    val modes = listOf(PaletteBuilder.ADAPTIVE) + PaletteBuilder.fixed.map { it.id }
    val modeLabel = PaletteBuilder.fixed.firstOrNull { it.id == s.colorMode }?.label ?: "From wallpaper"
    val fixedSwatch = PaletteBuilder.fixed.firstOrNull { it.id == s.colorMode }?.let { PaletteBuilder.fromAccent(it.accent).toColors().accent }

    SectionHeader("Look")
    SettingRow("Wallpaper", env.wallpaperTitle, chevron = true, modifier = Modifier.focusRequester(first)) { actions.open(Overlay.WallpaperPicker) }
    SettingRow("Colours", modeLabel, swatch = fixedSwatch ?: LocalBsfColors.current.accent) { set { it.copy(colorMode = modes.after(it.colorMode)) } }
    SettingRow("Wallpaper brightness", "${(s.wallpaperBrightness * 100).toInt()}%", description = "Lower is kinder to an OLED on bright images") {
        set { it.copy(wallpaperBrightness = LauncherSettings.BrightnessSteps.after(LauncherSettings.BrightnessSteps.minBy { b -> kotlin.math.abs(b - it.wallpaperBrightness) })) }
    }
    SettingRow("Change wallpaper", s.rotation.label) { set { it.copy(rotation = Rotation.entries.after(it.rotation)) } }

    SectionHeader("Layout")
    SettingRow("Media card size", s.cardSize.label) { set { it.copy(cardSize = CardSize.entries.after(it.cardSize)) } }
    SettingRow("Tools", s.toolsStyle.label, description = if (s.toolsStyle == ToolsStyle.TRAY) "Small row under your media" else "Behind the Tools button only") {
        set { it.copy(toolsStyle = ToolsStyle.entries.after(it.toolsStyle)) }
    }
    SettingRow("Clock", if (s.showClock) "On" else "Off") { set { it.copy(showClock = !it.showClock) } }
    SettingRow("Animations", s.motion.label) { set { it.copy(motion = Motion.entries.after(it.motion)) } }
    SettingRow("Sort apps", "${ui.unsorted.size} unsorted", chevron = true) { actions.open(Overlay.AllApps(com.bsfreetv.launcher.feature.home.AppFilter.ALL)) }

    SectionHeader("Screen care")
    SettingRow("Wallpaper only after", minutes(s.ambientAfterMin), description = "Hides clock and rows when idle") {
        set { it.copy(ambientAfterMin = LauncherSettings.AmbientSteps.after(it.ambientAfterMin)) }
    }
    SettingRow("Black screen after", if (s.blackAfterMin == 0) "Never" else "+${s.blackAfterMin} min", description = "True black, pixels off") {
        set { it.copy(blackAfterMin = LauncherSettings.BlackSteps.after(it.blackAfterMin)) }
    }
    SettingRow("Wallpaper drift", if (s.wallpaperDrift) "On" else "Off", description = "Very slow pan; costs a little GPU") { set { it.copy(wallpaperDrift = !it.wallpaperDrift) } }

    SectionHeader("Next Up")
    SettingRow(
        "Next Up row",
        when {
            !s.nextUp -> "Off"
            !env.nextUpPermission -> "Needs access"
            else -> "On"
        },
        description = "Continue watching from apps that publish it (Jellyfin does)",
    ) {
        if (!s.nextUp && !env.nextUpPermission) setup.requestNextUpPermission()
        set { it.copy(nextUp = !it.nextUp) }
    }
    if (s.nextUp) {
        SettingRow("Apps in Next Up", if (s.nextUpPackages == null) "Media apps" else "${s.nextUpPackages.size} chosen", chevron = true) {
            actions.open(Overlay.Settings(SettingsPage.NEXT_UP_APPS))
        }
    }

    SectionHeader("System")
    val home = env.homeStatus
    SettingRow(
        "Default launcher",
        when {
            home == null -> ""
            home.weAreHome -> "BSfreeTV"
            home.stockEnabled -> "Stock launcher"
            else -> "Not set"
        },
        chevron = true,
    ) {
        setup.refreshStatus()
        actions.open(Overlay.Settings(SettingsPage.DEFAULT_HOME))
    }
    SettingRow("Android settings", chevron = true) { actions.openSystemSettings() }
    SettingRow("About", "v${BuildConfig.VERSION_NAME}", chevron = true) { actions.open(Overlay.Settings(SettingsPage.ABOUT)) }
}

@Composable
private fun DefaultHomePage(env: SettingsEnv, setup: SettingsActions, first: FocusRequester) {
    val colors = LocalBsfColors.current
    val st = env.homeStatus
    Column(Modifier.padding(horizontal = 14.dp)) {
        Text(
            "The Shield has no Home picker. BSfreeTV becomes Home by disabling the stock launcher through adb, " +
                "which it can do itself once Network debugging is on.",
            style = BsfType.body,
            color = colors.textMuted,
            modifier = Modifier.padding(top = 6.dp, bottom = 12.dp),
        )
        if (st != null) {
            StatusLine(
                st.weAreHome,
                when {
                    st.weAreHome -> "Home button opens BSfreeTV"
                    st.currentHome in StockLaunchers -> "Home button opens the stock launcher"
                    else -> "Home button opens ${st.currentHome ?: "a chooser"}"
                },
            )
            st.stock.forEach { StatusLine(!it.enabled, "${it.packageName.substringAfterLast('.')}: ${if (it.enabled) "enabled" else "disabled"}") }
            StatusLine(st.tvListingsGranted, if (st.tvListingsGranted) "Next Up access granted" else "Next Up access not granted")
            StatusLine(st.adbEnabled, if (st.adbEnabled) "Debugging is on" else "Debugging is off (Developer options)")
        }
        Text("1. Settings > Device Preferences > About > tap Build 7 times.\n2. Developer options > turn on Network debugging.\n3. Press a button below and choose Allow (tick Always allow) on the prompt.",
            style = BsfType.label, color = colors.textMuted, modifier = Modifier.padding(top = 12.dp))
        Row(Modifier.padding(top = 14.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            PanelButton("Make BSfreeTV Home", primary = true, modifier = Modifier.focusRequester(first)) { setup.makeDefault() }
            PanelButton("Test connection") { setup.probe() }
        }
        Row(Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            PanelButton("Allow Next Up") { setup.grantNextUp() }
            PanelButton("Restore stock") { setup.restoreStock() }
        }
        SetupResult(env.setupState)
        Text("Then turn Network debugging off again: anyone on your network could otherwise ask to connect.",
            style = BsfType.label, color = colors.textMuted, modifier = Modifier.padding(top = 12.dp))
        SectionHeader("From a PC instead", Modifier.padding(start = 0.dp))
        Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp)).background(Color.Black.copy(alpha = 0.5f)).padding(10.dp)) {
            (listOf("adb connect <shield-ip>:5555") + env.pcCommands).forEach { Text(it, style = BsfType.mono, color = colors.text) }
            Text("Undo / recovery:", style = BsfType.label, color = colors.textMuted, modifier = Modifier.padding(top = 8.dp))
            Text(HomeCommands.RECOVERY, style = BsfType.mono, color = colors.text)
        }
        Text("Restore the stock launcher before uninstalling BSfreeTV, or the Home button has nothing to open.",
            style = BsfType.label, color = colors.textMuted, modifier = Modifier.padding(top = 10.dp))
    }
}

@Composable
private fun SetupResult(state: SetupState) {
    val colors = LocalBsfColors.current
    when (state) {
        SetupState.Idle -> Unit
        is SetupState.Working -> Text("${state.what}… If the TV asks to allow debugging, choose Allow.", style = BsfType.body, color = colors.accent, modifier = Modifier.padding(top = 12.dp))
        is SetupState.Result -> Column(Modifier.padding(top = 12.dp)) {
            when (val o = state.outcome) {
                is AdbOutcome.Failed -> StatusLine(false, o.reason)
                is AdbOutcome.Done -> {
                    StatusLine(o.ok, if (o.ok) "${state.title}: done" else "${state.title}: some steps failed")
                    if (o.results.isEmpty()) Text("Nothing to change.", style = BsfType.label, color = colors.textMuted)
                    o.results.forEach { r ->
                        Text("${if (r.ok) "✓" else if (r.step.optional) "–" else "✗"} ${r.step.label}${if (r.output.isNotBlank()) ": ${r.output.take(120)}" else ""}",
                            style = BsfType.label, color = colors.textMuted)
                    }
                }
            }
        }
    }
}

@Composable
private fun NextUpAppsPage(ui: HomeUi, actions: HomeActions, first: FocusRequester) {
    val chosen = ui.settings.nextUpPackages
    SettingRow("Use my Media apps", if (chosen == null) "On" else "Off", modifier = Modifier.focusRequester(first)) {
        actions.updateSettings { it.copy(nextUpPackages = if (it.nextUpPackages == null) ui.media.map { m -> m.packageName }.toSet() else null) }
    }
    SectionHeader("Apps")
    val effective = chosen ?: ui.media.map { it.packageName }.toSet()
    ui.all.forEach { app: AppEntry ->
        SettingRow(app.label, if (app.packageName in effective) "Shown" else "Off", enabled = chosen != null) {
            actions.updateSettings { s ->
                val set = s.nextUpPackages ?: effective
                s.copy(nextUpPackages = if (app.packageName in set) set - app.packageName else set + app.packageName)
            }
        }
    }
}

@Composable
private fun AboutPage(env: SettingsEnv, first: FocusRequester) {
    val colors = LocalBsfColors.current
    SettingRow("BSfreeTV", "v${BuildConfig.VERSION_NAME}", description = "An ad-free launcher. GPL-3.0.", modifier = Modifier.focusRequester(first)) {}
    Column(Modifier.padding(horizontal = 14.dp, vertical = 8.dp)) {
        Text("Wallpapers", style = BsfType.bodyStrong, color = colors.text)
        Text("From Impasto by Andreu Massanet (github.com/andreumassanet/impasto), GPL-3.0. Colour extraction follows Impasto's accent picker.",
            style = BsfType.label, color = colors.textMuted)
        env.userDir?.let {
            Text("Your wallpapers", style = BsfType.bodyStrong, color = colors.text, modifier = Modifier.padding(top = 10.dp))
            Text(it, style = BsfType.mono, color = colors.textMuted)
        }
    }
}
