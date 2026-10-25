package com.bsfreetv.launcher.domain

import com.bsfreetv.launcher.domain.palette.PaletteBuilder
import kotlinx.serialization.Serializable

// User-facing names live in ui/Labels.kt (string resources), not here.
enum class CardSize(val widthDp: Int) { SMALL(136), MEDIUM(160), LARGE(200) }

enum class ToolsStyle { TRAY, HIDDEN }

/** Tool chip icon diameter and the slot it sits in (the slot also bounds the focused name). */
enum class ToolSize(val iconDp: Int, val slotDp: Int) { SMALL(28, 52), MEDIUM(34, 56), LARGE(44, 64) }

enum class Motion { FULL, REDUCED, OFF }

enum class Rotation { OFF, DAILY, ON_WAKE }

/** Every user-facing setting, persisted as one JSON document. Defaults are the out-of-box experience. */
@Serializable
data class LauncherSettings(
    val wallpaperId: String = DEFAULT_WALLPAPER,
    val rotation: Rotation = Rotation.OFF,
    /** [PaletteBuilder.ADAPTIVE] or a [PaletteBuilder.fixed] id. */
    val colorMode: String = PaletteBuilder.ADAPTIVE,
    /** 0.55..1.0 multiplier on the wallpaper; lower spares the OLED on bright images. */
    val wallpaperBrightness: Float = 0.9f,
    val cardSize: CardSize = CardSize.MEDIUM,
    val toolsStyle: ToolsStyle = ToolsStyle.TRAY,
    val toolSize: ToolSize = ToolSize.MEDIUM,
    val showClock: Boolean = true,
    val motion: Motion = Motion.FULL,
    /** Minutes without input until only the wallpaper shows; 0 = never. */
    val ambientAfterMin: Int = 3,
    /** Minutes in ambient until the screen goes black; 0 = never. */
    val blackAfterMin: Int = 10,
    val wallpaperDrift: Boolean = false,
    val nextUp: Boolean = false,
    /** Status indicator: a VPN pill next to the clock while a VPN is connected. */
    val statusVpn: Boolean = false,
    /** Packages whose Watch Next rows show; null = the Media section. */
    val nextUpPackages: Set<String>? = null,
    /** Google TV Home button service: holding Home opens the stock home (the way back). */
    val homeHoldForStock: Boolean = true,
    /** Google TV Home button service: open BSfreeTV once right after a restart. */
    val homeOpenAfterBoot: Boolean = true,
    /** Epoch day of the last daily rotation. */
    val rotatedOnDay: Long = 0,
) {
    /** Status indicators switched on, for the settings row. */
    val statusIndicatorCount: Int get() = listOf(statusVpn).count { it }

    companion object {
        const val DEFAULT_WALLPAPER = "japanese-castle-full-moon"
        val BrightnessSteps = listOf(1.0f, 0.9f, 0.8f, 0.7f, 0.6f)
        val AmbientSteps = listOf(1, 3, 5, 10, 0)
        val BlackSteps = listOf(5, 10, 20, 30, 0)
    }
}
