package com.bsfreetv.launcher.feature.home

import android.content.Intent
import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.Lifecycle
import com.bsfreetv.launcher.domain.home.HomeCommands
import com.bsfreetv.launcher.domain.Motion
import com.bsfreetv.launcher.feature.apps.AllAppsOverlay
import com.bsfreetv.launcher.feature.apps.AppMenu
import com.bsfreetv.launcher.feature.settings.HomeSetupViewModel
import com.bsfreetv.launcher.feature.settings.SettingsActions
import com.bsfreetv.launcher.feature.settings.SettingsEnv
import com.bsfreetv.launcher.feature.settings.SettingsPanel
import com.bsfreetv.launcher.feature.wallpaper.WallpaperPickerOverlay
import com.bsfreetv.launcher.ui.BsfTheme
import com.bsfreetv.launcher.ui.components.LocalMotion
import com.bsfreetv.launcher.i18n.LanguageStore
import android.app.Activity

/** Wires the ViewModels to the stateless screens. */
@Composable
fun LauncherRoot(vm: HomeViewModel) {
    val ui by vm.ui.collectAsStateWithLifecycle()
    val frame by vm.wallpaper.collectAsStateWithLifecycle()
    val palette by vm.palette.collectAsStateWithLifecycle()
    val overlay by vm.overlay.collectAsStateWithLifecycle()
    val menuFor by vm.menuFor.collectAsStateWithLifecycle()
    val moving by vm.moving.collectAsStateWithLifecycle()
    val idle by vm.idle.collectAsStateWithLifecycle()
    val nextUp by vm.nextUp.collectAsStateWithLifecycle()
    val homeTick by vm.homeTick.collectAsStateWithLifecycle()
    val focusRequest by vm.focusRequest.collectAsStateWithLifecycle()
    val wallpaperList by vm.wallpaperList.collectAsStateWithLifecycle()
    val status by vm.status.collectAsStateWithLifecycle()

    val setupVm: HomeSetupViewModel = hiltViewModel()
    val homeStatus by setupVm.status.collectAsStateWithLifecycle()
    val setupState by setupVm.state.collectAsStateWithLifecycle()

    val context = LocalContext.current
    val languageTag = remember { LanguageStore.get(context) }
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        setupVm.refresh()
        vm.refreshNextUpSoon()
    }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri -> uri?.let(vm::importWallpaper) }
    // Android TV often ships without a document picker; only offer import when one exists.
    val canImport = remember {
        Intent(Intent.ACTION_OPEN_DOCUMENT).addCategory(Intent.CATEGORY_OPENABLE).setType("image/*").resolveActivity(context.packageManager) != null
    }

    BackHandler { vm.back() }
    // Back from system settings (Accessibility, App info): show what changed.
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { setupVm.refresh() }

    CompositionLocalProvider(LocalMotion provides ui.settings.motion) {
        BsfTheme(palette, animate = ui.settings.motion != Motion.OFF) {
            // Every key resets the idle clock; the first key after the screen faded only wakes it.
            Box(Modifier.fillMaxSize().onPreviewKeyEvent { it.type == KeyEventType.KeyDown && vm.onUserInput() }) {
                HomeContent(
                    ui = ui,
                    frame = frame,
                    nextUp = nextUp,
                    idle = idle,
                    moving = moving,
                    homeTick = homeTick,
                    focusRequest = focusRequest,
                    overlayOpen = overlay != Overlay.None || menuFor != null,
                    chromeHidden = overlay is Overlay.AllApps || overlay == Overlay.WallpaperPicker,
                    status = status,
                    actions = vm,
                )
                when (val o = overlay) {
                    Overlay.None -> Unit
                    is Overlay.AllApps -> AllAppsOverlay(ui, o.filter, vm, menuOpen = menuFor != null)
                    is Overlay.Settings -> SettingsPanel(
                        page = o.page,
                        ui = ui,
                        env = SettingsEnv(
                            wallpaperTitle = wallpaperList.firstOrNull { it.id == ui.settings.wallpaperId }?.title ?: titleOf(ui.settings.wallpaperId),
                            nextUpPermission = vm.nextUpPermission,
                            homeStatus = homeStatus,
                            setupState = setupState,
                            pcCommands = setupVm.pcCommands(),
                            userDir = vm.userWallpaperDir,
                            languageTag = languageTag,
                            restrictedSettings = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU,
                        ),
                        actions = vm,
                        setup = object : SettingsActions {
                            override fun requestNextUpPermission() = permission.launch(HomeCommands.TV_LISTINGS)
                            override fun probe() = setupVm.probe()
                            override fun makeDefault() = setupVm.makeDefault()
                            override fun restoreStock() = setupVm.restoreStock()
                            override fun grantNextUp() = setupVm.grantNextUp()
                            override fun refreshStatus() = setupVm.refresh()
                            override fun allowRestricted() = setupVm.allowRestricted()
                            override fun openAccessibilitySettings() { setupVm.openAccessibilitySettings() }
                            override fun openAppInfo() { setupVm.openAppInfo() }
                            override fun setLanguage(tag: String?) {
                                LanguageStore.set(context, tag)
                                // The panel stays open: overlay state lives in the ViewModel.
                                (context as? Activity)?.recreate()
                            }
                        },
                    )
                    Overlay.WallpaperPicker -> WallpaperPickerOverlay(
                        wallpapers = wallpaperList,
                        currentId = ui.settings.wallpaperId,
                        canImport = canImport,
                        userDir = vm.userWallpaperDir,
                        loadThumb = vm::thumb,
                        loadPalette = vm::paletteOf,
                        onPreview = vm::previewWallpaper,
                        onChoose = vm::chooseWallpaper,
                        onImport = { picker.launch(arrayOf("image/*")) },
                    )
                }
                menuFor?.let { pkg ->
                    ui.all.firstOrNull { it.packageName == pkg }?.let { AppMenu(it, ui.sectionOf(pkg), vm) }
                }
            }
        }
    }
}

private fun titleOf(id: String) = id.substringAfter(':').substringBeforeLast('.').split('-', '_').joinToString(" ") { it.replaceFirstChar(Char::uppercase) }
