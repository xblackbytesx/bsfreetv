package com.bsfreetv.launcher.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.sp
import com.bsfreetv.launcher.R
import com.bsfreetv.launcher.domain.CardSize
import com.bsfreetv.launcher.domain.Motion
import com.bsfreetv.launcher.domain.Rotation
import com.bsfreetv.launcher.domain.ToolSize
import com.bsfreetv.launcher.domain.ToolsStyle
import com.bsfreetv.launcher.domain.home.AdbStep
import com.bsfreetv.launcher.domain.home.StepKind
import com.bsfreetv.launcher.domain.layout.Section
import com.bsfreetv.launcher.feature.home.AppFilter
import com.bsfreetv.launcher.feature.settings.SetupTask
import com.bsfreetv.launcher.system.AdbError
import java.util.Locale

/*
 * The one place domain values get their user-facing names. The domain stays
 * free of display text (and JVM-testable); every label is a string resource.
 */

@Composable fun CardSize.label() = stringResource(
    when (this) {
        CardSize.SMALL -> R.string.size_small
        CardSize.MEDIUM -> R.string.size_medium
        CardSize.LARGE -> R.string.size_large
    },
)

@Composable fun ToolSize.label() = stringResource(
    when (this) {
        ToolSize.SMALL -> R.string.size_small
        ToolSize.MEDIUM -> R.string.size_medium
        ToolSize.LARGE -> R.string.size_large
    },
)

@Composable fun ToolsStyle.label() = stringResource(
    when (this) {
        ToolsStyle.TRAY -> R.string.tools_tray
        ToolsStyle.HIDDEN -> R.string.tools_hidden
    },
)

@Composable fun Motion.label() = stringResource(
    when (this) {
        Motion.FULL -> R.string.motion_full
        Motion.REDUCED -> R.string.motion_reduced
        Motion.OFF -> R.string.value_off
    },
)

@Composable fun Rotation.label() = stringResource(
    when (this) {
        Rotation.OFF -> R.string.value_off
        Rotation.DAILY -> R.string.rotation_daily
        Rotation.ON_WAKE -> R.string.rotation_on_wake
    },
)

@Composable fun AppFilter.label() = stringResource(
    when (this) {
        AppFilter.ALL -> R.string.section_all
        AppFilter.UNSORTED -> R.string.section_unsorted
        AppFilter.MEDIA -> R.string.section_media
        AppFilter.TOOLS -> R.string.section_tools
        AppFilter.HIDDEN -> R.string.section_hidden
    },
)

@Composable fun Section?.label() = stringResource(
    when (this) {
        Section.MEDIA -> R.string.section_media
        Section.TOOLS -> R.string.section_tools
        Section.HIDDEN -> R.string.section_hidden
        null -> R.string.section_unsorted
    },
)

/** Small badge text on an All apps card, or null for Unsorted. */
@Composable fun Section?.badge(): String? = when (this) {
    Section.MEDIA -> stringResource(R.string.badge_media)
    Section.TOOLS -> stringResource(R.string.badge_tool)
    Section.HIDDEN -> stringResource(R.string.badge_hidden)
    null -> null
}?.upper()

/** Fixed palette ids from PaletteBuilder.fixed. */
@Composable fun paletteLabel(id: String): String = stringResource(
    when (id) {
        "ember" -> R.string.palette_ember
        "sea" -> R.string.palette_sea
        "moss" -> R.string.palette_moss
        "iris" -> R.string.palette_iris
        "rose" -> R.string.palette_rose
        "mono" -> R.string.palette_mono
        else -> R.string.colours_adaptive
    },
)

@Composable fun SetupTask.label() = stringResource(
    when (this) {
        SetupTask.MAKE_HOME -> R.string.task_make_home
        SetupTask.RESTORE -> R.string.task_restore
        SetupTask.ALLOW_NEXT_UP -> R.string.task_allow_next_up
        SetupTask.ALLOW_RESTRICTED -> R.string.task_allow_restricted
        SetupTask.TEST -> R.string.task_test
    },
)

@Composable fun AdbStep.label(): String {
    val pkg = target?.substringAfterLast('.').orEmpty()
    return when (kind) {
        StepKind.DISABLE -> stringResource(R.string.step_disable, pkg)
        StepKind.ENABLE -> stringResource(R.string.step_enable, pkg)
        StepKind.PREFERRED_HOME -> stringResource(R.string.step_preferred_home)
        StepKind.RESTORE_HOME -> stringResource(R.string.step_restore_home, pkg)
        StepKind.ALLOW_NEXT_UP -> stringResource(R.string.step_allow_next_up)
        StepKind.ALLOW_RESTRICTED -> stringResource(R.string.task_allow_restricted)
        StepKind.READ_VERSION -> stringResource(R.string.step_read_version)
    }
}

@Composable fun AdbError.message(detail: String?): String = when (this) {
    AdbError.UNREACHABLE -> stringResource(R.string.adb_err_unreachable)
    AdbError.PROMPT_TIMEOUT -> stringResource(R.string.adb_err_timeout)
    AdbError.REFUSED -> stringResource(R.string.adb_err_refused)
    AdbError.OTHER -> stringResource(R.string.adb_err_other, detail.orEmpty())
}

@Composable fun minutesLabel(n: Int): String =
    if (n == 0) stringResource(R.string.value_never) else pluralStringResource(R.plurals.value_minutes, n, n)

@Composable fun onOff(on: Boolean): String = stringResource(if (on) R.string.value_on else R.string.value_off)

/** The UI locale (follows the in-app override; see i18n/Languages.kt). */
@Composable @ReadOnlyComposable
fun uiLocale(): Locale = LocalConfiguration.current.locales[0]

/** Capitals for overlines and badges, with the UI locale's case rules. */
@Composable @ReadOnlyComposable
fun String.upper(): String = uppercase(uiLocale())

/** Scripts without letter case, where heading letter-spacing does harm. */
private val NoCaseScripts = setOf("ja", "zh", "ko", "ar")

/**
 * Wide letter-spacing makes Latin capitals read as a heading. CJK has no case
 * and looks sparse with it, and spacing breaks the joined letters of Arabic,
 * so those locales get normal spacing.
 */
@Composable @ReadOnlyComposable
fun TextStyle.forScript(): TextStyle = if (uiLocale().language in NoCaseScripts) copy(letterSpacing = 0.sp) else this
