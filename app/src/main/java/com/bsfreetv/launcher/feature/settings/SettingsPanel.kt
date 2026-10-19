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
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import com.bsfreetv.launcher.BuildConfig
import com.bsfreetv.launcher.R
import com.bsfreetv.launcher.feature.home.AppFilter
import com.bsfreetv.launcher.i18n.SupportedLanguages
import com.bsfreetv.launcher.ui.label
import com.bsfreetv.launcher.ui.message
import com.bsfreetv.launcher.ui.minutesLabel
import com.bsfreetv.launcher.ui.onOff
import com.bsfreetv.launcher.ui.paletteLabel
import kotlin.math.roundToInt
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

/** Everything the settings pages need beyond [HomeUi]. */
data class SettingsEnv(
    val wallpaperTitle: String,
    val nextUpPermission: Boolean,
    val homeStatus: HomeStatus?,
    val setupState: SetupState,
    val pcCommands: List<String>,
    val userDir: String?,
    /** In-app language override tag, or null for the system language. */
    val languageTag: String? = null,
)

interface SettingsActions {
    fun requestNextUpPermission()
    fun probe()
    fun makeDefault()
    fun restoreStock()
    fun grantNextUp()
    fun refreshStatus()
    /** Step to the next language and restart the activity in it. */
    fun cycleLanguage()

    object None : SettingsActions {
        override fun requestNextUpPermission() = Unit
        override fun probe() = Unit
        override fun makeDefault() = Unit
        override fun restoreStock() = Unit
        override fun grantNextUp() = Unit
        override fun refreshStatus() = Unit
        override fun cycleLanguage() = Unit
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
                stringResource(
                    when (page) {
                        SettingsPage.MAIN -> R.string.settings_title
                        SettingsPage.DEFAULT_HOME -> R.string.settings_default_home
                        SettingsPage.NEXT_UP_APPS -> R.string.settings_next_up_apps
                        SettingsPage.ABOUT -> R.string.settings_about
                    },
                ),
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
    val fixed = PaletteBuilder.fixed.firstOrNull { it.id == s.colorMode }
    val fixedSwatch = fixed?.let { PaletteBuilder.fromAccent(it.accent).toColors().accent }

    SectionHeader(stringResource(R.string.header_look))
    SettingRow(stringResource(R.string.set_wallpaper), env.wallpaperTitle, chevron = true, modifier = Modifier.focusRequester(first)) { actions.open(Overlay.WallpaperPicker) }
    SettingRow(stringResource(R.string.set_colours), paletteLabel(s.colorMode), swatch = fixedSwatch ?: LocalBsfColors.current.accent) { set { it.copy(colorMode = modes.after(it.colorMode)) } }
    SettingRow(
        stringResource(R.string.set_brightness),
        stringResource(R.string.value_percent, (s.wallpaperBrightness * 100).roundToInt()),
        description = stringResource(R.string.set_brightness_desc),
    ) {
        set { it.copy(wallpaperBrightness = LauncherSettings.BrightnessSteps.after(LauncherSettings.BrightnessSteps.minBy { b -> kotlin.math.abs(b - it.wallpaperBrightness) })) }
    }
    SettingRow(stringResource(R.string.set_rotation), s.rotation.label()) { set { it.copy(rotation = Rotation.entries.after(it.rotation)) } }
    SettingRow(stringResource(R.string.set_language), SupportedLanguages.nameOf(env.languageTag) ?: stringResource(R.string.language_system)) { setup.cycleLanguage() }

    SectionHeader(stringResource(R.string.header_layout))
    SettingRow(stringResource(R.string.set_card_size), s.cardSize.label()) { set { it.copy(cardSize = CardSize.entries.after(it.cardSize)) } }
    SettingRow(
        stringResource(R.string.set_tools),
        s.toolsStyle.label(),
        description = stringResource(if (s.toolsStyle == ToolsStyle.TRAY) R.string.set_tools_desc_tray else R.string.set_tools_desc_hidden),
    ) {
        set { it.copy(toolsStyle = ToolsStyle.entries.after(it.toolsStyle)) }
    }
    SettingRow(stringResource(R.string.set_clock), onOff(s.showClock)) { set { it.copy(showClock = !it.showClock) } }
    SettingRow(stringResource(R.string.set_animations), s.motion.label()) { set { it.copy(motion = Motion.entries.after(it.motion)) } }
    SettingRow(stringResource(R.string.set_sort_apps), pluralStringResource(R.plurals.value_unsorted, ui.unsorted.size, ui.unsorted.size), chevron = true) {
        actions.open(Overlay.AllApps(AppFilter.ALL))
    }

    SectionHeader(stringResource(R.string.header_screen_care))
    SettingRow(stringResource(R.string.set_ambient), minutesLabel(s.ambientAfterMin), description = stringResource(R.string.set_ambient_desc)) {
        set { it.copy(ambientAfterMin = LauncherSettings.AmbientSteps.after(it.ambientAfterMin)) }
    }
    SettingRow(
        stringResource(R.string.set_black),
        if (s.blackAfterMin == 0) stringResource(R.string.value_never) else pluralStringResource(R.plurals.value_plus_minutes, s.blackAfterMin, s.blackAfterMin),
        description = stringResource(R.string.set_black_desc),
    ) {
        set { it.copy(blackAfterMin = LauncherSettings.BlackSteps.after(it.blackAfterMin)) }
    }
    SettingRow(stringResource(R.string.set_drift), onOff(s.wallpaperDrift), description = stringResource(R.string.set_drift_desc)) { set { it.copy(wallpaperDrift = !it.wallpaperDrift) } }

    SectionHeader(stringResource(R.string.header_next_up))
    SettingRow(
        stringResource(R.string.set_next_up),
        when {
            !s.nextUp -> stringResource(R.string.value_off)
            !env.nextUpPermission -> stringResource(R.string.value_needs_access)
            else -> stringResource(R.string.value_on)
        },
        description = stringResource(R.string.set_next_up_desc),
    ) {
        if (!s.nextUp && !env.nextUpPermission) setup.requestNextUpPermission()
        set { it.copy(nextUp = !it.nextUp) }
    }
    if (s.nextUp) {
        SettingRow(
            stringResource(R.string.set_next_up_apps),
            s.nextUpPackages?.let { pluralStringResource(R.plurals.value_chosen, it.size, it.size) } ?: stringResource(R.string.value_media_apps),
            chevron = true,
        ) {
            actions.open(Overlay.Settings(SettingsPage.NEXT_UP_APPS))
        }
    }

    SectionHeader(stringResource(R.string.header_system))
    val home = env.homeStatus
    SettingRow(
        stringResource(R.string.settings_default_home),
        when {
            home == null -> ""
            home.weAreHome -> stringResource(R.string.app_name)
            home.stockEnabled -> stringResource(R.string.home_value_stock)
            else -> stringResource(R.string.home_value_not_set)
        },
        chevron = true,
    ) {
        setup.refreshStatus()
        actions.open(Overlay.Settings(SettingsPage.DEFAULT_HOME))
    }
    SettingRow(stringResource(R.string.set_android_settings), chevron = true) { actions.openSystemSettings() }
    SettingRow(stringResource(R.string.settings_about), stringResource(R.string.value_version, BuildConfig.VERSION_NAME), chevron = true) {
        actions.open(Overlay.Settings(SettingsPage.ABOUT))
    }
}

@Composable
private fun DefaultHomePage(env: SettingsEnv, setup: SettingsActions, first: FocusRequester) {
    val colors = LocalBsfColors.current
    val st = env.homeStatus
    Column(Modifier.padding(horizontal = 14.dp)) {
        Text(
            stringResource(R.string.dh_intro),
            style = BsfType.body,
            color = colors.textMuted,
            modifier = Modifier.padding(top = 6.dp, bottom = 12.dp),
        )
        if (st != null) {
            StatusLine(
                st.weAreHome,
                when {
                    st.weAreHome -> stringResource(R.string.status_home_ours)
                    st.currentHome in StockLaunchers -> stringResource(R.string.status_home_stock)
                    st.currentHome == null -> stringResource(R.string.status_home_chooser)
                    else -> stringResource(R.string.status_home_other, st.currentHome)
                },
            )
            st.stock.forEach {
                val name = it.packageName.substringAfterLast('.')
                StatusLine(!it.enabled, stringResource(if (it.enabled) R.string.status_pkg_enabled else R.string.status_pkg_disabled, name))
            }
            StatusLine(st.tvListingsGranted, stringResource(if (st.tvListingsGranted) R.string.status_next_up_granted else R.string.status_next_up_missing))
            StatusLine(st.adbEnabled, stringResource(if (st.adbEnabled) R.string.status_debug_on else R.string.status_debug_off))
        }
        Text(stringResource(R.string.dh_steps), style = BsfType.label, color = colors.textMuted, modifier = Modifier.padding(top = 12.dp))
        Row(Modifier.padding(top = 14.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            PanelButton(stringResource(R.string.btn_make_home), primary = true, modifier = Modifier.focusRequester(first)) { setup.makeDefault() }
            PanelButton(stringResource(R.string.btn_test)) { setup.probe() }
        }
        Row(Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            PanelButton(stringResource(R.string.btn_allow_next_up)) { setup.grantNextUp() }
            PanelButton(stringResource(R.string.btn_restore)) { setup.restoreStock() }
        }
        SetupResult(env.setupState)
        Text(stringResource(R.string.dh_turn_off), style = BsfType.label, color = colors.textMuted, modifier = Modifier.padding(top = 12.dp))
        SectionHeader(stringResource(R.string.header_from_pc), Modifier.padding(start = 0.dp))
        Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp)).background(Color.Black.copy(alpha = 0.5f)).padding(10.dp)) {
            (listOf(PC_CONNECT) + env.pcCommands).forEach { Text(it, style = BsfType.mono, color = colors.text) }
            Text(stringResource(R.string.dh_recovery), style = BsfType.label, color = colors.textMuted, modifier = Modifier.padding(top = 8.dp))
            Text(HomeCommands.RECOVERY, style = BsfType.mono, color = colors.text)
        }
        Text(stringResource(R.string.dh_uninstall_warning), style = BsfType.label, color = colors.textMuted, modifier = Modifier.padding(top = 10.dp))
    }
}

/** A shell command, never translated. */
private const val PC_CONNECT = "adb connect <shield-ip>:5555"

@Composable
private fun SetupResult(state: SetupState) {
    val colors = LocalBsfColors.current
    when (state) {
        SetupState.Idle -> Unit
        is SetupState.Working -> Text(
            stringResource(R.string.setup_working, state.task.label()),
            style = BsfType.body,
            color = colors.accent,
            modifier = Modifier.padding(top = 12.dp),
        )
        is SetupState.Result -> Column(Modifier.padding(top = 12.dp)) {
            when (val o = state.outcome) {
                is AdbOutcome.Failed -> StatusLine(false, o.error.message(o.detail))
                is AdbOutcome.Done -> {
                    val title = state.task.label()
                    StatusLine(o.ok, stringResource(if (o.ok) R.string.setup_done else R.string.setup_failed, title))
                    if (o.results.isEmpty()) Text(stringResource(R.string.setup_nothing), style = BsfType.label, color = colors.textMuted)
                    o.results.forEach { r ->
                        val mark = if (r.ok) "✓" else if (r.step.optional) "–" else "✗"
                        val output = if (r.output.isNotBlank()) ": ${r.output.take(120)}" else ""
                        Text("$mark ${r.step.label()}$output", style = BsfType.label, color = colors.textMuted)
                    }
                }
            }
        }
    }
}

@Composable
private fun NextUpAppsPage(ui: HomeUi, actions: HomeActions, first: FocusRequester) {
    val chosen = ui.settings.nextUpPackages
    SettingRow(stringResource(R.string.next_up_use_media), onOff(chosen == null), modifier = Modifier.focusRequester(first)) {
        actions.updateSettings { it.copy(nextUpPackages = if (it.nextUpPackages == null) ui.media.map { m -> m.packageName }.toSet() else null) }
    }
    SectionHeader(stringResource(R.string.header_apps))
    val effective = chosen ?: ui.media.map { it.packageName }.toSet()
    ui.all.forEach { app: AppEntry ->
        SettingRow(app.label, stringResource(if (app.packageName in effective) R.string.value_shown else R.string.value_off), enabled = chosen != null) {
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
    SettingRow(
        stringResource(R.string.app_name),
        stringResource(R.string.value_version, BuildConfig.VERSION_NAME),
        description = stringResource(R.string.about_desc),
        modifier = Modifier.focusRequester(first),
    ) {}
    Column(Modifier.padding(horizontal = 14.dp, vertical = 8.dp)) {
        Text(stringResource(R.string.about_wallpapers), style = BsfType.bodyStrong, color = colors.text)
        Text(stringResource(R.string.about_wallpapers_credit), style = BsfType.label, color = colors.textMuted)
        env.userDir?.let {
            Text(stringResource(R.string.about_your_wallpapers), style = BsfType.bodyStrong, color = colors.text, modifier = Modifier.padding(top = 10.dp))
            Text(it, style = BsfType.mono, color = colors.textMuted)
        }
    }
}
