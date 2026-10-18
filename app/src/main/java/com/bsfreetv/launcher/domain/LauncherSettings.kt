package com.bsfreetv.launcher.domain

import com.bsfreetv.launcher.domain.palette.PaletteBuilder
import kotlinx.serialization.Serializable

enum class CardSize(val label: String, val widthDp: Int) { SMALL("Small", 136), MEDIUM("Medium", 160), LARGE("Large", 200) }

enum class ToolsStyle(val label: String) { TRAY("Tray"), HIDDEN("Hidden") }

enum class Motion(val label: String) { FULL("Full"), REDUCED("Reduced"), OFF("Off") }

enum class Rotation(val label: String) { OFF("Off"), DAILY("Daily"), ON_WAKE("Every wake") }

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
    val showClock: Boolean = true,
    val motion: Motion = Motion.FULL,
    /** Minutes without input until only the wallpaper shows; 0 = never. */
    val ambientAfterMin: Int = 3,
    /** Minutes in ambient until the screen goes black; 0 = never. */
    val blackAfterMin: Int = 10,
    val wallpaperDrift: Boolean = false,
    val nextUp: Boolean = false,
    /** Packages whose Watch Next rows show; null = the Media section. */
    val nextUpPackages: Set<String>? = null,
    /** Epoch day of the last daily rotation. */
    val rotatedOnDay: Long = 0,
) {
    companion object {
        const val DEFAULT_WALLPAPER = "japanese-castle-full-moon"
        val BrightnessSteps = listOf(1.0f, 0.9f, 0.8f, 0.7f, 0.6f)
        val AmbientSteps = listOf(1, 3, 5, 10, 0)
        val BlackSteps = listOf(5, 10, 20, 30, 0)
    }
}
