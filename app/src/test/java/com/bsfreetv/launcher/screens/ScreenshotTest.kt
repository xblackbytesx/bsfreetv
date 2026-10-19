package com.bsfreetv.launcher.screens

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import com.bsfreetv.launcher.data.AppEntry
import com.bsfreetv.launcher.data.NextUpItem
import com.bsfreetv.launcher.data.Wallpaper
import com.bsfreetv.launcher.domain.home.HomeStatus
import com.bsfreetv.launcher.domain.home.StockLauncher
import com.bsfreetv.launcher.domain.idle.IdlePhase
import com.bsfreetv.launcher.domain.LauncherSettings
import com.bsfreetv.launcher.domain.layout.AppInfo
import com.bsfreetv.launcher.domain.layout.Section
import com.bsfreetv.launcher.domain.palette.LauncherPalette
import com.bsfreetv.launcher.domain.palette.PaletteBuilder
import com.bsfreetv.launcher.domain.ToolsStyle
import com.bsfreetv.launcher.feature.apps.AllAppsOverlay
import com.bsfreetv.launcher.feature.apps.AppMenu
import com.bsfreetv.launcher.feature.home.AppFilter
import com.bsfreetv.launcher.feature.home.HomeActions
import com.bsfreetv.launcher.feature.home.HomeContent
import com.bsfreetv.launcher.feature.home.HomeUi
import com.bsfreetv.launcher.feature.home.SettingsPage
import com.bsfreetv.launcher.feature.home.WallpaperFrame
import com.bsfreetv.launcher.feature.settings.SettingsActions
import com.bsfreetv.launcher.feature.settings.SettingsEnv
import com.bsfreetv.launcher.feature.settings.SettingsPanel
import com.bsfreetv.launcher.feature.settings.SetupState
import com.bsfreetv.launcher.feature.settings.SetupTask
import com.bsfreetv.launcher.feature.wallpaper.WallpaperPickerOverlay
import com.bsfreetv.launcher.ui.BsfTheme
import com.bsfreetv.launcher.ui.components.LocalMotion
import com.github.takahirom.roborazzi.captureRoboImage
import java.io.File
import java.time.LocalDateTime
import org.junit.Rule
import org.junit.runner.RunWith
import org.junit.Test
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import org.robolectric.RobolectricTestRunner

/**
 * Renders the screens at the Shield's 960x540 dp (xhdpi = 1920x1080 px) with
 * real wallpapers and stand-in app banners. Output is for looking at, not a
 * golden comparison: `make screenshots` -> build-output/screenshots/.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w960dp-h540dp-land-television-xhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ScreenshotTest {
    @get:Rule val rule = createComposeRule()

    private val outDir = File(System.getProperty("bsf.screenshots") ?: "build/screenshots").apply { mkdirs() }
    private val clock = LocalDateTime.of(2026, 10, 18, 21, 42)

    private fun wallpaper(id: String): WallpaperFrame {
        val bmp = BitmapFactory.decodeFile("src/main/assets/wallpapers/$id.webp")
        val small = Bitmap.createScaledBitmap(bmp, 120, 68, true)
        val px = IntArray(120 * 68).also { small.getPixels(it, 0, 120, 0, 0, 120, 68) }
        return WallpaperFrame(id, bmp.asImageBitmap(), PaletteBuilder.fromPixels(px))
    }

    /**
     * A stand-in banner: brand colour and wordmark, like a real android:banner,
     * rasterised at the size AppCatalog uses for this display's density.
     */
    private fun banner(label: String, bg: Int, fg: Int = 0xFFFFFFFF.toInt()): ImageBitmap {
        val w = com.bsfreetv.launcher.data.AppCatalog.bannerWidthFor(org.robolectric.RuntimeEnvironment.getApplication().resources.displayMetrics.density)
        val h = w * 9 / 16
        val b = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val c = Canvas(b)
        c.drawColor(bg)
        val p = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = fg; textSize = w / 8f; typeface = Typeface.DEFAULT_BOLD; textAlign = Paint.Align.CENTER }
        c.drawText(label, w / 2f, h / 2f + w / 24f, p)
        return b.asImageBitmap()
    }

    private fun icon(bg: Int): ImageBitmap {
        val b = Bitmap.createBitmap(128, 128, Bitmap.Config.ARGB_8888)
        val c = Canvas(b)
        c.drawRoundRect(RectF(8f, 8f, 120f, 120f), 28f, 28f, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = bg })
        c.drawCircle(64f, 64f, 22f, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFFFFFFFF.toInt() })
        return b.asImageBitmap()
    }

    private fun app(pkg: String, label: String, bg: Long, withBanner: Boolean = true, fg: Long = 0xFFFFFFFF) =
        AppEntry(AppInfo(pkg, label, withBanner), if (withBanner) banner(label, bg.toInt(), fg.toInt()) else null, icon(bg.toInt()), 1)

    private val media = listOf(
        app("org.jellyfin.androidtv", "jellyfin", 0xFF101B2E),
        app("com.netflix.ninja", "NETFLIX", 0xFF000000, fg = 0xFFE50914),
        app("org.schabi.newpipe", "NewPipe", 0xFFCD201F),
        app("nl.uitzendinggemist", "NPO Start", 0xFFFF6D00),
        app("com.valvesoftware.steamlink", "STEAM LINK", 0xFF171A21),
    )
    private val tools = listOf(
        app("com.looker.droidify", "Droid-ify", 0xFF2E7D32, withBanner = false),
        app("dev.imranr.obtainium", "Obtainium", 0xFF5E35B1, withBanner = false),
    )
    private val unsorted = listOf(
        app("org.xbmc.kodi", "Kodi", 0xFF17B2E7),
        app("com.android.vending", "Play Store", 0xFF01875F, withBanner = false),
        app("com.nvidia.ota", "System upgrade", 0xFF76B900, withBanner = false),
    )

    private fun ui(settings: LauncherSettings = LauncherSettings(), fresh: Int = 0) = HomeUi(
        ready = true,
        media = media,
        tools = tools,
        unsorted = unsorted,
        sections = media.associate { it.packageName to Section.MEDIA } + tools.associate { it.packageName to Section.TOOLS },
        freshCount = fresh,
        settings = settings,
    )

    private val nextUp = listOf(
        NextUpItem(1, "org.jellyfin.androidtv", "Severance", "S2 · E5 · Trojan's Horse", null, null, 0.42f, 3),
        NextUpItem(2, "org.jellyfin.androidtv", "Dune: Part Two", null, null, null, 0.7f, 2),
        NextUpItem(3, "com.netflix.ninja", "Arcane", "S1 · E3", null, null, 0.1f, 1),
    )

    private fun shot(name: String, palette: LauncherPalette, content: @Composable () -> Unit) {
        rule.setContent {
            CompositionLocalProvider(LocalMotion provides com.bsfreetv.launcher.domain.Motion.OFF) {
                BsfTheme(palette, animate = false) { content() }
            }
        }
        rule.waitForIdle()
        rule.onRoot().captureRoboImage(File(outDir, "$name.png").path)
    }

    @Composable
    private fun Home(frame: WallpaperFrame, ui: HomeUi, next: List<NextUpItem> = emptyList(), idle: IdlePhase = IdlePhase.ACTIVE, overlay: Boolean = false, fullOverlay: Boolean = false) =
        HomeContent(ui, frame, next, idle, moving = null, homeTick = 0, focusRequest = null, overlayOpen = overlay || fullOverlay, actions = HomeActions.None, chromeHidden = fullOverlay, clockOverride = clock)

    @Test fun home_castle() {
        val f = wallpaper("japanese-castle-full-moon")
        shot("01-home-castle", f.palette) { Home(f, ui()) }
    }

    @Test fun home_bright_nextup() {
        val f = wallpaper("capri-faraglioni")
        shot("02-home-bright-nextup", f.palette) { Home(f, ui(LauncherSettings(nextUp = true)), nextUp) }
    }

    @Test fun home_mono_large_hidden_tools() {
        val f = wallpaper("marble-statue-back")
        shot("03-home-mono-large", f.palette) { Home(f, ui(LauncherSettings(cardSize = com.bsfreetv.launcher.domain.CardSize.LARGE, toolsStyle = ToolsStyle.HIDDEN), fresh = 2)) }
    }

    @Test fun home_ambient() {
        val f = wallpaper("tropical-bay-night")
        shot("04-home-ambient", f.palette) { Home(f, ui(), idle = IdlePhase.AMBIENT) }
    }

    @Test fun all_apps() {
        val f = wallpaper("japanese-castle-full-moon")
        shot("05-all-apps", f.palette) {
            Home(f, ui(), fullOverlay = true)
            AllAppsOverlay(ui(), AppFilter.ALL, HomeActions.None)
        }
    }

    @Test fun app_menu() {
        val f = wallpaper("emerald-cliffs")
        shot("06-app-menu", f.palette) {
            Home(f, ui(), overlay = true)
            AppMenu(media[0], Section.MEDIA, HomeActions.None)
        }
    }

    private fun env(state: SetupState = SetupState.Idle, language: String? = null) = SettingsEnv(
        languageTag = language,
        wallpaperTitle = "Japanese Castle Full Moon",
        nextUpPermission = false,
        homeStatus = HomeStatus(listOf(StockLauncher("com.google.android.tvlauncher", true)), "com.google.android.tvlauncher", "com.bsfreetv.launcher", false, false),
        setupState = state,
        pcCommands = listOf("adb shell pm disable-user --user 0 com.google.android.tvlauncher", "adb shell pm grant com.bsfreetv.launcher android.permission.READ_TV_LISTINGS"),
        userDir = "/storage/emulated/0/Android/data/com.bsfreetv.launcher/files/wallpapers",
    )

    @Test fun settings_main() {
        val f = wallpaper("japanese-castle-full-moon")
        shot("07-settings", f.palette) {
            Home(f, ui(), overlay = true)
            SettingsPanel(SettingsPage.MAIN, ui(), env(), HomeActions.None, SettingsActions.None)
        }
    }

    @Test fun settings_default_home() {
        val f = wallpaper("red-frame-tree")
        shot("08-default-launcher", f.palette) {
            Home(f, ui(), overlay = true)
            SettingsPanel(SettingsPage.DEFAULT_HOME, ui(), env(SetupState.Working(SetupTask.MAKE_HOME)), HomeActions.None, SettingsActions.None)
        }
    }

    @Test fun wallpaper_picker() {
        val f = wallpaper("pagoda-fuji-blossom")
        val list = listOf("pagoda-fuji-blossom", "japanese-castle-full-moon", "capri-faraglioni", "tiger-snow", "tropical-bay-night", "emerald-cliffs")
            .map { Wallpaper(it, it.split('-').joinToString(" ") { w -> w.replaceFirstChar(Char::uppercase) }, "Andreu Massanet, Impasto", false) }
        val thumbs = list.associate { w -> w.id to BitmapFactory.decodeFile("src/main/assets/wallpapers/thumbs/${w.id}.webp").asImageBitmap() }
        shot("09-wallpaper-picker", f.palette) {
            Home(f, ui(), fullOverlay = true)
            WallpaperPickerOverlay(list, "pagoda-fuji-blossom", true, env().userDir, { thumbs[it] }, { f.palette }, {}, {}, {})
        }
    }

    // Translations: the same home + settings in each shipped language and the
    // two pseudo-locales (en-XA: ~30% longer accented text, ar-XB: right-to-left).
    private fun localized(tag: String) {
        val f = wallpaper("japanese-castle-full-moon")
        shot("10-home-$tag", f.palette) { Home(f, ui(fresh = 3)) }
    }

    private fun localizedSettings(tag: String) {
        val f = wallpaper("japanese-castle-full-moon")
        shot("11-settings-$tag", f.palette) {
            Home(f, ui(), overlay = true)
            SettingsPanel(SettingsPage.MAIN, ui(), env(), HomeActions.None, SettingsActions.None)
        }
    }

    /** The Shield on a 4K TV renders at 3840x2160 (4x); artwork must stay sharp. */
    @Test @Config(qualifiers = "w960dp-h540dp-land-television-xxxhdpi")
    fun home_4k() {
        val f = wallpaper("japanese-castle-full-moon")
        shot("12-home-4k", f.palette) { Home(f, ui()) }
    }

    @Test fun language_picker() {
        val f = wallpaper("japanese-castle-full-moon")
        shot("13-language", f.palette) {
            Home(f, ui(), overlay = true)
            SettingsPanel(SettingsPage.LANGUAGE, ui(), env(), HomeActions.None, SettingsActions.None)
        }
    }

    /** Stuck in Arabic: every option is still readable in its own script. */
    @Test @Config(qualifiers = "+ar-ldrtl") fun language_picker_ar() {
        val f = wallpaper("japanese-castle-full-moon")
        shot("13-language-ar", f.palette) {
            Home(f, ui(), overlay = true)
            SettingsPanel(SettingsPage.LANGUAGE, ui(), env(language = "ar"), HomeActions.None, SettingsActions.None)
        }
    }

    @Test @Config(qualifiers = "+de") fun home_de() = localized("de")
    @Test @Config(qualifiers = "+de") fun settings_de() = localizedSettings("de")
    @Test @Config(qualifiers = "+nl") fun settings_nl() = localizedSettings("nl")
    @Test @Config(qualifiers = "+es") fun home_es() = localized("es")
    @Test @Config(qualifiers = "+es") fun settings_es() = localizedSettings("es")
    @Test @Config(qualifiers = "+ja") fun home_ja() = localized("ja")
    @Test @Config(qualifiers = "+ja") fun settings_ja() = localizedSettings("ja")
    @Test @Config(qualifiers = "+zh-rCN") fun settings_zh() = localizedSettings("zh-rCN")
    @Test @Config(qualifiers = "+fr") fun settings_fr() = localizedSettings("fr")
    @Test @Config(qualifiers = "+it") fun settings_it() = localizedSettings("it")
    @Test @Config(qualifiers = "+pt") fun settings_pt() = localizedSettings("pt")
    @Test @Config(qualifiers = "+pt") fun home_pt() = localized("pt")
    @Test @Config(qualifiers = "+pl") fun settings_pl() = localizedSettings("pl")
    @Test @Config(qualifiers = "+uk") fun settings_uk() = localizedSettings("uk")
    @Test @Config(qualifiers = "+uk") fun home_uk() = localized("uk")
    @Test @Config(qualifiers = "+ko") fun settings_ko() = localizedSettings("ko")
    @Test @Config(qualifiers = "+ar-ldrtl") fun home_ar() = localized("ar")
    @Test @Config(qualifiers = "+ar-ldrtl") fun settings_ar() = localizedSettings("ar")
    @Test @Config(qualifiers = "+en-rXA") fun settings_pseudo_long() = localizedSettings("en-rXA")
    @Test @Config(qualifiers = "+ar-rXB-ldrtl") fun home_pseudo_rtl() = localized("ar-rXB")
}
