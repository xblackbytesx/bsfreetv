package com.bsfreetv.launcher.feature.home

import android.text.format.DateFormat
import android.view.KeyEvent as AndroidKeyEvent
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRestorer
import androidx.compose.ui.focus.onFocusEvent
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.IntSize
import coil3.compose.AsyncImage
import com.bsfreetv.launcher.data.AppEntry
import com.bsfreetv.launcher.data.NextUpItem
import com.bsfreetv.launcher.domain.idle.IdlePhase
import com.bsfreetv.launcher.domain.idle.PixelShift
import com.bsfreetv.launcher.domain.LauncherSettings
import com.bsfreetv.launcher.domain.Motion
import com.bsfreetv.launcher.domain.ToolsStyle
import com.bsfreetv.launcher.ui.BsfColors
import com.bsfreetv.launcher.ui.BsfType
import com.bsfreetv.launcher.ui.components.AppBannerCard
import com.bsfreetv.launcher.ui.components.fadeSpec
import com.bsfreetv.launcher.ui.components.focusHalo
import com.bsfreetv.launcher.ui.components.LocalMotion
import com.bsfreetv.launcher.ui.components.Pill
import com.bsfreetv.launcher.ui.components.ProgressLine
import com.bsfreetv.launcher.ui.components.ToolChip
import com.bsfreetv.launcher.ui.components.tvClickable
import com.bsfreetv.launcher.ui.LocalBsfColors
import java.time.format.DateTimeFormatter
import java.time.LocalDateTime
import kotlinx.coroutines.delay

private val Gutter = 48.dp

/** Home: wallpaper, hero, Media row, optional Next Up row, Tools tray. Stateless. */
@Composable
fun HomeContent(
    ui: HomeUi,
    frame: WallpaperFrame?,
    nextUp: List<NextUpItem>,
    idle: IdlePhase,
    moving: String?,
    homeTick: Int,
    focusRequest: String?,
    overlayOpen: Boolean,
    actions: HomeActions,
    /** Full-screen overlays (All apps, wallpaper picker) hide the home chrome entirely. */
    chromeHidden: Boolean = false,
    clockOverride: LocalDateTime? = null,
) {
    val s = ui.settings
    val motion = LocalMotion.current
    val showChrome = idle == IdlePhase.ACTIVE && !chromeHidden
    val chromeAlpha by animateFloatAsState(if (showChrome) 1f else 0f, fadeSpec(motion, if (showChrome || chromeHidden) 250 else 1500), label = "chrome")
    val curtain by animateFloatAsState(if (idle == IdlePhase.BLACK) 1f else 0f, fadeSpec(motion, if (idle == IdlePhase.BLACK) 3000 else 200), label = "curtain")

    val requesters = remember { HashMap<String, FocusRequester>() }
    fun requester(key: String) = requesters.getOrPut(key) { FocusRequester() }
    val appsPill = remember { FocusRequester() }
    val mediaState = rememberLazyListState()
    val toolsState = rememberLazyListState()
    var lastFocused by remember { mutableStateOf<String?>(null) }
    var hero by remember { mutableStateOf<HeroText?>(null) }

    suspend fun focus(key: String?) {
        withFrameNanos { }
        val target = key?.takeIf { k -> requesters.containsKey(k) } ?: ui.media.firstOrNull()?.packageName
        val r = target?.let { requesters[it] } ?: appsPill
        runCatching { r.requestFocus() }
    }

    // First show, every Home press: back to the first media app.
    LaunchedEffect(ui.ready, homeTick) {
        if (!ui.ready) return@LaunchedEffect
        mediaState.scrollToItem(0)
        toolsState.scrollToItem(0)
        focus(ui.media.firstOrNull()?.packageName)
    }
    // Leaving an overlay: back to where the user was.
    LaunchedEffect(overlayOpen) { if (!overlayOpen && ui.ready) focus(lastFocused) }
    LaunchedEffect(focusRequest) {
        val pkg = focusRequest ?: return@LaunchedEffect
        ui.media.indexOfFirst { it.packageName == pkg }.takeIf { it >= 0 }?.let { mediaState.scrollToItem(it) }
        ui.tools.indexOfFirst { it.packageName == pkg }.takeIf { it >= 0 }?.let { toolsState.scrollToItem(it) }
        focus(pkg)
        actions.focusHandled()
    }

    val shift by produceState(PixelShift.offset(System.currentTimeMillis())) {
        while (true) {
            delay(30_000)
            value = PixelShift.offset(System.currentTimeMillis())
        }
    }
    val density = LocalDensity.current
    val shiftX by animateFloatAsState(with(density) { shift.first.dp.toPx() }, tween(2000), label = "shiftX")
    val shiftY by animateFloatAsState(with(density) { shift.second.dp.toPx() }, tween(2000), label = "shiftY")

    // While reordering, Left/Right move the app instead of the focus.
    val moveKeys = Modifier.onPreviewKeyEvent { e ->
        if (moving == null) return@onPreviewKeyEvent false
        val code = e.nativeKeyEvent.keyCode
        if (e.type != KeyEventType.KeyDown) return@onPreviewKeyEvent code in MoveKeys
        when (code) {
            AndroidKeyEvent.KEYCODE_DPAD_LEFT -> { actions.move(-1); true }
            AndroidKeyEvent.KEYCODE_DPAD_RIGHT -> { actions.move(1); true }
            AndroidKeyEvent.KEYCODE_DPAD_UP, AndroidKeyEvent.KEYCODE_DPAD_DOWN -> true
            else -> false
        }
    }

    Box(Modifier.fillMaxSize().background(BsfColors.Black)) {
        WallpaperLayer(frame?.image, s.wallpaperBrightness, s.wallpaperDrift && motion != Motion.OFF)
        Scrim(nextUp.isNotEmpty())
        Box(
            Modifier.fillMaxSize().graphicsLayer {
                alpha = chromeAlpha
                translationX = shiftX
                translationY = shiftY
            },
        ) {
            TopBar(
                settings = s,
                freshCount = ui.freshCount,
                appsPill = appsPill,
                clockOverride = clockOverride,
                actions = actions,
                modifier = Modifier.align(Alignment.TopStart),
            )
            Column(Modifier.align(Alignment.BottomStart).fillMaxWidth().padding(bottom = 30.dp).then(moveKeys)) {
                Hero(hero, Modifier.padding(start = Gutter, bottom = 18.dp))
                if (ui.media.isEmpty() && ui.ready) {
                    EmptyMedia(onClick = { actions.open(Overlay.AllApps(AppFilter.UNSORTED)) })
                } else {
                    MediaRow(ui.media, s, mediaState, moving, ::requester, actions) { app ->
                        lastFocused = app.packageName
                        hero = HeroText(app.label, if (moving == app.packageName) "Left and right to move, OK when done" else null)
                    }
                }
                if (nextUp.isNotEmpty()) {
                    NextUpRow(nextUp, ui.all, ::requester, actions) { item ->
                        lastFocused = "nextup:${item.id}"
                        hero = HeroText(item.title, item.subtitle, item.progress)
                    }
                }
                if (s.toolsStyle == ToolsStyle.TRAY && ui.tools.isNotEmpty()) {
                    ToolsTray(ui.tools, toolsState, moving, ::requester, actions) { app -> lastFocused = app.packageName; hero = HeroText(app.label, "Tool") }
                }
            }
        }
        // True black curtain for the last idle stage: OLED pixels off.
        if (curtain > 0f) Box(Modifier.fillMaxSize().graphicsLayer { alpha = curtain }.background(BsfColors.Black))
    }
}

private val MoveKeys = setOf(AndroidKeyEvent.KEYCODE_DPAD_LEFT, AndroidKeyEvent.KEYCODE_DPAD_RIGHT, AndroidKeyEvent.KEYCODE_DPAD_UP, AndroidKeyEvent.KEYCODE_DPAD_DOWN)

private data class HeroText(val title: String, val subtitle: String?, val progress: Float? = null)

@Composable
private fun Hero(text: HeroText?, modifier: Modifier = Modifier) {
    val colors = LocalBsfColors.current
    Column(modifier.height(78.dp), verticalArrangement = Arrangement.Bottom) {
        Text(text?.title.orEmpty(), style = BsfType.hero, color = colors.text, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.fillMaxWidth(0.7f))
        Row(Modifier.padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.width(28.dp).height(3.dp).clip(RoundedCornerShape(2.dp)).background(colors.accent))
            text?.subtitle?.let {
                Text(it, style = BsfType.heroSub, color = colors.text.copy(alpha = 0.75f), maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(start = 10.dp))
            }
            text?.progress?.let { p ->
                Box(Modifier.padding(start = 10.dp).width(80.dp)) { ProgressLine(p) }
            }
        }
    }
}

@Composable
private fun MediaRow(
    media: List<AppEntry>,
    s: LauncherSettings,
    state: LazyListState,
    moving: String?,
    requester: (String) -> FocusRequester,
    actions: HomeActions,
    onFocused: (AppEntry) -> Unit,
) {
    LazyRow(
        state = state,
        contentPadding = PaddingValues(horizontal = Gutter, vertical = 14.dp),
        horizontalArrangement = Arrangement.spacedBy(18.dp),
        modifier = Modifier.fillMaxWidth().focusRestorer(),
    ) {
        items(media, key = { it.packageName }, contentType = { "media" }) { app ->
            AppBannerCard(
                app = app,
                width = s.cardSize.widthDp.dp,
                moving = moving == app.packageName,
                modifier = Modifier.focusRequester(requester(app.packageName)),
                onFocus = { if (it) onFocused(app) },
                onLongClick = { actions.openMenu(app.packageName) },
                onClick = { if (moving != null) actions.endMove() else actions.launch(app.packageName) },
            )
        }
    }
}

@Composable
private fun NextUpRow(
    items: List<NextUpItem>,
    apps: List<AppEntry>,
    requester: (String) -> FocusRequester,
    actions: HomeActions,
    onFocused: (NextUpItem) -> Unit,
) {
    val colors = LocalBsfColors.current
    val icons = remember(apps) { apps.associate { it.packageName to it.icon } }
    Column {
        Text("UP NEXT", style = BsfType.overlineOnImage, color = colors.textMuted, modifier = Modifier.padding(start = Gutter, top = 4.dp))
        LazyRow(
            contentPadding = PaddingValues(horizontal = Gutter, vertical = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            modifier = Modifier.fillMaxWidth().focusRestorer(),
        ) {
            items(items, key = { it.id }, contentType = { "nextup" }) { item ->
                NextUpCard(item, icons[item.packageName], Modifier.focusRequester(requester("nextup:${item.id}")), { if (it) onFocused(item) }) { actions.launch(item) }
            }
        }
    }
}

@Composable
private fun NextUpCard(item: NextUpItem, appIcon: ImageBitmap?, modifier: Modifier, onFocus: (Boolean) -> Unit, onClick: () -> Unit) {
    var focused by remember { mutableStateOf(false) }
    val colors = LocalBsfColors.current
    Box(
        modifier
            .width(144.dp)
            .height(81.dp)
            .focusHalo(focused)
            .tvClickable(onFocus = { focused = it; onFocus(it) }, onClick = onClick)
            .clip(RoundedCornerShape(8.dp))
            .background(colors.surfaceHigh),
    ) {
        // Sized to the card: Coil decodes at 288x162 px, never the full poster.
        AsyncImage(
            model = item.imageUri,
            contentDescription = item.title,
            modifier = Modifier.fillMaxSize(),
            contentScale = androidx.compose.ui.layout.ContentScale.Crop,
            filterQuality = FilterQuality.Medium,
        )
        if (appIcon != null) {
            androidx.compose.foundation.Image(appIcon, null, Modifier.align(Alignment.TopStart).padding(6.dp).size(16.dp))
        }
        item.progress?.let { ProgressLine(it, Modifier.align(Alignment.BottomStart)) }
    }
}

@Composable
private fun ToolsTray(
    tools: List<AppEntry>,
    state: LazyListState,
    moving: String?,
    requester: (String) -> FocusRequester,
    actions: HomeActions,
    onFocused: (AppEntry) -> Unit,
) {
    val colors = LocalBsfColors.current
    Row(verticalAlignment = Alignment.Top, modifier = Modifier.padding(top = 6.dp)) {
        Text("TOOLS", style = BsfType.overlineOnImage, color = colors.textMuted.copy(alpha = 0.8f), modifier = Modifier.padding(start = Gutter, top = 22.dp).width(56.dp))
        LazyRow(
            state = state,
            contentPadding = PaddingValues(start = 4.dp, end = Gutter, top = 6.dp, bottom = 0.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            modifier = Modifier.fillMaxWidth().focusRestorer(),
        ) {
            items(tools, key = { it.packageName }, contentType = { "tool" }) { app ->
                ToolChip(
                    app = app,
                    moving = moving == app.packageName,
                    modifier = Modifier.focusRequester(requester(app.packageName)).onFocusChangedCompat { if (it) onFocused(app) },
                    onLongClick = { actions.openMenu(app.packageName) },
                    onClick = { if (moving != null) actions.endMove() else actions.launch(app.packageName) },
                )
            }
        }
    }
}

private fun Modifier.onFocusChangedCompat(onFocus: (Boolean) -> Unit) =
    this.then(Modifier.onFocusEvent { onFocus(it.hasFocus) })

@Composable
private fun EmptyMedia(onClick: () -> Unit) {
    val colors = LocalBsfColors.current
    var focused by remember { mutableStateOf(false) }
    Column(
        Modifier
            .padding(start = Gutter, top = 14.dp, bottom = 14.dp)
            .focusHalo(focused, dimUnfocused = false)
            .tvClickable(onFocus = { focused = it }, onClick = onClick)
            .clip(RoundedCornerShape(10.dp))
            .background(colors.surface.copy(alpha = 0.9f))
            .padding(horizontal = 20.dp, vertical = 14.dp),
    ) {
        Text("Pick your media apps", style = BsfType.bodyStrong, color = colors.text)
        Text("Open All apps, hold OK on an app and choose Media.", style = BsfType.body, color = colors.textMuted)
    }
}

@Composable
private fun TopBar(
    settings: LauncherSettings,
    freshCount: Int,
    appsPill: FocusRequester,
    clockOverride: LocalDateTime?,
    actions: HomeActions,
    modifier: Modifier = Modifier,
) {
    Row(modifier.fillMaxWidth().padding(horizontal = Gutter, vertical = 26.dp), verticalAlignment = Alignment.CenterVertically) {
        if (settings.showClock) Clock(clockOverride)
        Spacer(Modifier.weight(1f))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Pill(if (freshCount > 0) "Apps · $freshCount new" else "Apps", Modifier.focusRequester(appsPill)) {
                actions.open(Overlay.AllApps(if (freshCount > 0) AppFilter.UNSORTED else AppFilter.ALL))
            }
            if (settings.toolsStyle == ToolsStyle.HIDDEN) Pill("Tools") { actions.open(Overlay.AllApps(AppFilter.TOOLS)) }
            Pill("Settings") { actions.open(Overlay.Settings(SettingsPage.MAIN)) }
        }
    }
}

@Composable
private fun Clock(override: LocalDateTime?) {
    val colors = LocalBsfColors.current
    val context = LocalContext.current
    val now by produceState(override ?: LocalDateTime.now(), override) {
        if (override != null) return@produceState
        while (true) {
            value = LocalDateTime.now()
            delay(60_000L - (System.currentTimeMillis() % 60_000L))
        }
    }
    val time = remember(now) { now.format(DateTimeFormatter.ofPattern(if (DateFormat.is24HourFormat(context)) "HH:mm" else "h:mm")) }
    val date = remember(now) { now.format(DateTimeFormatter.ofPattern("EEE d MMM")) }
    Row(verticalAlignment = Alignment.Bottom) {
        Text(time, style = BsfType.clock, color = colors.text.copy(alpha = 0.82f))
        Text(date, style = BsfType.clockDate, color = colors.text.copy(alpha = 0.6f), modifier = Modifier.padding(start = 10.dp, bottom = 3.dp))
    }
}

/**
 * The wallpaper, cross-faded on change. Draws a single bitmap (two only during
 * a fade); brightness is a black wash in the same draw. Drift, when enabled, is
 * a slow layer translation: no re-decode, no relayout.
 */
@Composable
fun WallpaperLayer(image: ImageBitmap?, brightness: Float, drift: Boolean) {
    var shown by remember { mutableStateOf<ImageBitmap?>(null) }
    var previous by remember { mutableStateOf<ImageBitmap?>(null) }
    val fade = remember { Animatable(1f) }
    val motion = LocalMotion.current
    LaunchedEffect(image) {
        if (image == shown) return@LaunchedEffect
        previous = shown
        shown = image
        if (previous == null || motion == Motion.OFF) {
            fade.snapTo(1f)
        } else {
            fade.snapTo(0f)
            fade.animateTo(1f, tween(if (motion == Motion.FULL) 700 else 250))
        }
        previous = null
    }
    val driftModifier = if (drift) {
        val t = rememberInfiniteTransition(label = "drift")
        val x by t.animateFloat(-1f, 1f, infiniteRepeatable(tween(97_000, easing = LinearEasing), RepeatMode.Reverse), label = "dx")
        val y by t.animateFloat(-1f, 1f, infiniteRepeatable(tween(131_000, easing = LinearEasing), RepeatMode.Reverse), label = "dy")
        Modifier.graphicsLayer {
            scaleX = 1.04f
            scaleY = 1.04f
            translationX = x * size.width * 0.015f
            translationY = y * size.height * 0.015f
        }
    } else Modifier
    Canvas(Modifier.fillMaxSize().then(driftModifier)) {
        val dst = IntSize(size.width.toInt(), size.height.toInt())
        previous?.let { drawImage(it, dstSize = dst, filterQuality = FilterQuality.Low) }
        shown?.let { drawImage(it, dstSize = dst, alpha = fade.value, filterQuality = FilterQuality.Low) }
        if (brightness < 1f) drawRect(Color.Black, alpha = 1f - brightness)
    }
}

/** Bottom fade to true black behind the rows, plus a light top fade behind the clock. */
@Composable
private fun Scrim(tall: Boolean) {
    val bottom = remember(tall) {
        Brush.verticalGradient(
            *if (tall) arrayOf(0f to Color.Transparent, 0.25f to Color.Black.copy(alpha = 0.25f), 0.55f to Color.Black.copy(alpha = 0.72f), 1f to Color.Black.copy(alpha = 0.95f))
            else arrayOf(0f to Color.Transparent, 0.4f to Color.Transparent, 0.68f to Color.Black.copy(alpha = 0.62f), 1f to Color.Black.copy(alpha = 0.93f)),
        )
    }
    val top = remember { Brush.verticalGradient(0f to Color.Black.copy(alpha = 0.45f), 0.2f to Color.Transparent) }
    Canvas(Modifier.fillMaxSize()) {
        drawRect(top)
        drawRect(bottom)
    }
}
