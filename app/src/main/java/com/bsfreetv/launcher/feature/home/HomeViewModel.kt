package com.bsfreetv.launcher.feature.home

import android.net.Uri
import android.os.SystemClock
import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.ImageBitmap
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.bsfreetv.launcher.data.AppCatalog
import com.bsfreetv.launcher.data.AppEntry
import com.bsfreetv.launcher.data.LauncherStore
import com.bsfreetv.launcher.data.NextUpItem
import com.bsfreetv.launcher.data.Wallpaper
import com.bsfreetv.launcher.data.Wallpapers
import com.bsfreetv.launcher.data.WatchNext
import com.bsfreetv.launcher.domain.idle.IdlePhase
import com.bsfreetv.launcher.domain.idle.IdlePolicy
import com.bsfreetv.launcher.domain.LauncherSettings
import com.bsfreetv.launcher.domain.layout.resolve
import com.bsfreetv.launcher.domain.layout.Section
import com.bsfreetv.launcher.domain.palette.LauncherPalette
import com.bsfreetv.launcher.domain.palette.PaletteBuilder
import com.bsfreetv.launcher.domain.Rotation
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.LocalDate
import javax.inject.Inject
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/** What the All apps screen lists. */
enum class AppFilter { ALL, UNSORTED, MEDIA, TOOLS, HIDDEN }

enum class SettingsPage(val parent: SettingsPage? = null) {
    MAIN,
    LANGUAGE(MAIN),
    DEFAULT_HOME(MAIN),
    HOME_DEBUGGING(DEFAULT_HOME),
    HOME_PC(DEFAULT_HOME),
    HOME_RECOVERY(DEFAULT_HOME),
    NEXT_UP_APPS(MAIN),
    ABOUT(MAIN),
}

sealed interface Overlay {
    data object None : Overlay
    data class AllApps(val filter: AppFilter) : Overlay
    data class Settings(val page: SettingsPage) : Overlay
    data object WallpaperPicker : Overlay
}

@Immutable
data class HomeUi(
    val ready: Boolean = false,
    val media: List<AppEntry> = emptyList(),
    val tools: List<AppEntry> = emptyList(),
    val unsorted: List<AppEntry> = emptyList(),
    val hidden: List<AppEntry> = emptyList(),
    val sections: Map<String, Section> = emptyMap(),
    val freshCount: Int = 0,
    val settings: LauncherSettings = LauncherSettings(),
) {
    val all: List<AppEntry> get() = (media + tools + unsorted + hidden).sortedBy { it.label.lowercase() }
}

/** The wallpaper on screen and the palette derived from it. */
@Immutable
data class WallpaperFrame(val id: String, val image: ImageBitmap?, val palette: LauncherPalette)

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val catalog: AppCatalog,
    private val store: LauncherStore,
    private val wallpapers: Wallpapers,
    private val watchNext: WatchNext,
) : ViewModel(), HomeActions {

    val ui: StateFlow<HomeUi> = combine(catalog.apps, store.layout, store.settings) { apps, layout, settings ->
        if (apps == null) return@combine HomeUi(settings = settings)
        val byPkg = apps.associateBy { it.packageName }
        val r = layout.resolve(apps.map { it.info })
        fun List<com.bsfreetv.launcher.domain.layout.AppInfo>.entries() = mapNotNull { byPkg[it.packageName] }
        HomeUi(
            ready = true,
            media = r.media.entries(),
            tools = r.tools.entries(),
            unsorted = r.unsorted.entries(),
            hidden = r.hidden.entries(),
            sections = apps.mapNotNull { a -> layout.sectionOf(a.packageName)?.let { a.packageName to it } }.toMap(),
            freshCount = r.freshCount,
            settings = settings,
        )
    }.stateIn(viewModelScope, SharingStarted.Eagerly, HomeUi())

    private val _overlay = MutableStateFlow<Overlay>(Overlay.None)
    val overlay: StateFlow<Overlay> = _overlay.asStateFlow()

    /** App whose context menu is open (over whatever overlay is showing). */
    private val _menuFor = MutableStateFlow<String?>(null)
    val menuFor: StateFlow<String?> = _menuFor.asStateFlow()

    /** App being reordered on the home rows; Left/Right move it, OK/Back finish. */
    private val _moving = MutableStateFlow<String?>(null)
    val moving: StateFlow<String?> = _moving.asStateFlow()

    /** Incremented on every Home press: the screen scrolls back and focuses the first media app. */
    private val _homeTick = MutableStateFlow(0)
    val homeTick: StateFlow<Int> = _homeTick.asStateFlow()

    /** Package the home screen should focus next (after a reorder starts from All apps). */
    private val _focusRequest = MutableStateFlow<String?>(null)
    val focusRequest: StateFlow<String?> = _focusRequest.asStateFlow()

    private val _preview = MutableStateFlow<String?>(null)
    private val _wallpaper = MutableStateFlow<WallpaperFrame?>(null)
    val wallpaper: StateFlow<WallpaperFrame?> = _wallpaper.asStateFlow()

    val palette: StateFlow<LauncherPalette> = combine(_wallpaper, store.settings.map { it.colorMode }) { frame, mode ->
        PaletteBuilder.forMode(mode, frame?.palette)
    }.stateIn(viewModelScope, SharingStarted.Eagerly, LauncherPalette.Neutral)

    private val _wallpaperList = MutableStateFlow<List<Wallpaper>>(emptyList())
    val wallpaperList: StateFlow<List<Wallpaper>> = _wallpaperList.asStateFlow()

    private val _nextUp = MutableStateFlow<List<NextUpItem>>(emptyList())
    val nextUp: StateFlow<List<NextUpItem>> = _nextUp.asStateFlow()

    private val _idle = MutableStateFlow(IdlePhase.ACTIVE)
    val idle: StateFlow<IdlePhase> = _idle.asStateFlow()
    private var lastInput = SystemClock.elapsedRealtime()
    private var idleJob: Job? = null

    /** Software bitmaps in tests (Robolectric has no hardware bitmaps). */
    var hardwareBitmaps = true

    init {
        // Fold newly installed apps into the layout (seeds, fresh badge).
        viewModelScope.launch {
            catalog.apps.filterNotNull().collect { apps -> store.updateLayout { it.reconcile(apps.map { a -> a.info }) } }
        }
        // Wallpaper on screen = preview while browsing the picker, else the saved one.
        viewModelScope.launch {
            combine(_preview, store.settings.map { it.wallpaperId }) { p, saved -> p ?: saved }
                .distinctUntilChanged()
                .collectLatest { id -> showWallpaper(id) }
        }
        viewModelScope.launch {
            combine(store.settings.map { it.nextUp to it.nextUpPackages }.distinctUntilChanged(), ui.map { u -> u.media.map { it.packageName } }.distinctUntilChanged()) { a, b -> a to b }
                .collectLatest { refreshNextUp() }
        }
        viewModelScope.launch { store.settings.map { it.ambientAfterMin to it.blackAfterMin }.distinctUntilChanged().collect { scheduleIdle() } }
    }

    private suspend fun showWallpaper(id: String) {
        // A delayed preview (fast scrolling through thumbnails) is cancelled by collectLatest.
        if (_preview.value != null) delay(250)
        var shownId = id
        var bitmap = wallpapers.loadFull(id, hardwareBitmaps)
        if (bitmap == null && id != LauncherSettings.DEFAULT_WALLPAPER) {
            // A removed user image: fall back to the default rather than black.
            shownId = LauncherSettings.DEFAULT_WALLPAPER
            bitmap = wallpapers.loadFull(shownId, hardwareBitmaps)
        }
        val palette = if (bitmap != null) wallpapers.palette(shownId) else LauncherPalette.Neutral
        _wallpaper.value = WallpaperFrame(shownId, bitmap?.asImageBitmap(), palette)
    }

    // ---- lifecycle and input -------------------------------------------------

    fun onResume() {
        catalog.refresh()
        viewModelScope.launch {
            refreshNextUp()
            val s = store.settings.first()
            val today = LocalDate.now().toEpochDay()
            if (s.rotation == Rotation.DAILY && s.rotatedOnDay != today) rotate(today)
        }
        onUserInput()
    }

    /** Any key press. Wakes from ambient/black and restarts the idle clock. */
    fun onUserInput(): Boolean {
        val wasPhase = _idle.value
        lastInput = SystemClock.elapsedRealtime()
        if (wasPhase != IdlePhase.ACTIVE) {
            _idle.value = IdlePhase.ACTIVE
            if (wasPhase == IdlePhase.BLACK) viewModelScope.launch {
                if (store.settings.first().rotation == Rotation.ON_WAKE) rotate(LocalDate.now().toEpochDay())
            }
        }
        scheduleIdle()
        // The first key after ambient only wakes; it must not also act.
        return wasPhase != IdlePhase.ACTIVE
    }

    fun onScreenOff() {
        viewModelScope.launch { if (store.settings.first().rotation == Rotation.ON_WAKE) rotate(LocalDate.now().toEpochDay()) }
    }

    private fun scheduleIdle() {
        idleJob?.cancel()
        idleJob = viewModelScope.launch {
            val s = store.settings.first()
            val policy = IdlePolicy(s.ambientAfterMin * 60_000L, s.blackAfterMin * 60_000L)
            while (true) {
                val idleMs = SystemClock.elapsedRealtime() - lastInput
                // Never fade while a menu or settings page is open.
                _idle.value = if (_overlay.value == Overlay.None && _menuFor.value == null) policy.phase(idleMs) else IdlePhase.ACTIVE
                val wait = policy.nextChangeIn(idleMs) ?: break
                delay(wait + 50)
            }
        }
    }

    private suspend fun rotate(today: Long) {
        val all = wallpapers.all()
        val current = store.settings.first().wallpaperId
        val next = all.filter { it.id != current }.randomOrNull() ?: return
        store.updateSettings { it.copy(wallpaperId = next.id, rotatedOnDay = today) }
    }

    fun onHomePressed() {
        _overlay.value = Overlay.None
        _menuFor.value = null
        _moving.value = null
        _preview.value = null
        _homeTick.value++
        onUserInput()
    }

    // ---- navigation ------------------------------------------------------------

    override fun open(overlay: Overlay) {
        if (overlay is Overlay.AllApps) viewModelScope.launch { store.updateLayout { it.clearFresh() } }
        if (overlay == Overlay.WallpaperPicker) viewModelScope.launch { _wallpaperList.value = wallpapers.all() }
        _overlay.value = overlay
    }

    /** Back: menu, then sub-page, then overlay. Returns false at the home root (a launcher has nowhere to go back to). */
    override fun back(): Boolean {
        when {
            _menuFor.value != null -> _menuFor.value = null
            _moving.value != null -> _moving.value = null
            // Back walks up one page: a Default launcher sub-page returns to it, not to the top.
            _overlay.value is Overlay.Settings && (_overlay.value as Overlay.Settings).page.parent != null ->
                _overlay.value = Overlay.Settings((_overlay.value as Overlay.Settings).page.parent!!)
            _overlay.value == Overlay.WallpaperPicker -> {
                _preview.value = null
                _overlay.value = Overlay.Settings(SettingsPage.MAIN)
            }
            _overlay.value != Overlay.None -> _overlay.value = Overlay.None
            else -> return false
        }
        return true
    }

    // ---- apps ------------------------------------------------------------------

    override fun launch(pkg: String) {
        if (_moving.value != null) {
            _moving.value = null
            return
        }
        catalog.launch(pkg)
    }

    override fun launch(item: NextUpItem) {
        if (!watchNext.launch(item)) catalog.launch(item.packageName)
    }

    override fun openMenu(pkg: String) { _menuFor.value = pkg }

    override fun assign(pkg: String, section: Section?) {
        _menuFor.value = null
        viewModelScope.launch { store.updateLayout { it.assign(pkg, section) } }
    }

    override fun startMove(pkg: String) {
        _menuFor.value = null
        _overlay.value = Overlay.None
        _moving.value = pkg
        _focusRequest.value = pkg
    }

    override fun focusHandled() { _focusRequest.value = null }

    override fun move(delta: Int) {
        val pkg = _moving.value ?: return
        viewModelScope.launch { store.updateLayout { it.move(pkg, delta) } }
    }

    override fun endMove() { _moving.value = null }

    override fun appInfo(pkg: String) { _menuFor.value = null; catalog.openAppInfo(pkg) }

    override fun uninstall(pkg: String) { _menuFor.value = null; catalog.uninstall(pkg) }

    override fun openSystemSettings() = catalog.openSystemSettings()

    // ---- settings --------------------------------------------------------------

    override fun updateSettings(transform: (LauncherSettings) -> LauncherSettings) {
        viewModelScope.launch { store.updateSettings(transform) }
    }

    override fun previewWallpaper(id: String?) { _preview.value = id }

    override fun chooseWallpaper(id: String) {
        _preview.value = null
        updateSettings { it.copy(wallpaperId = id) }
        _overlay.value = Overlay.Settings(SettingsPage.MAIN)
    }

    fun importWallpaper(uri: Uri) {
        viewModelScope.launch {
            val id = wallpapers.import(uri) ?: return@launch
            _wallpaperList.value = wallpapers.all()
            updateSettings { it.copy(wallpaperId = id) }
        }
    }

    val userWallpaperDir: String? get() = wallpapers.userDir?.absolutePath

    suspend fun thumb(id: String): ImageBitmap? = wallpapers.loadThumb(id)?.asImageBitmap()

    suspend fun paletteOf(id: String): LauncherPalette = wallpapers.palette(id)

    val nextUpPermission: Boolean get() = watchNext.hasPermission

    fun refreshNextUpSoon() { viewModelScope.launch { refreshNextUp() } }

    private suspend fun refreshNextUp() {
        val s = store.settings.first()
        if (!s.nextUp) {
            _nextUp.value = emptyList()
            return
        }
        val pkgs = s.nextUpPackages ?: ui.value.media.map { it.packageName }.toSet()
        _nextUp.value = watchNext.load(pkgs)
    }
}

/** Convenience for the layout editor: the section an app is in, or null for Unsorted. */
fun HomeUi.sectionOf(pkg: String): Section? = sections[pkg]
