package com.bsfreetv.launcher.feature.apps

import androidx.compose.foundation.background
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.bsfreetv.launcher.data.AppEntry
import com.bsfreetv.launcher.domain.layout.Section
import com.bsfreetv.launcher.feature.home.AppFilter
import com.bsfreetv.launcher.feature.home.HomeActions
import com.bsfreetv.launcher.feature.home.HomeUi
import com.bsfreetv.launcher.feature.home.Overlay
import com.bsfreetv.launcher.ui.BsfColors
import com.bsfreetv.launcher.ui.BsfType
import com.bsfreetv.launcher.ui.components.AppBannerCard
import com.bsfreetv.launcher.ui.components.focusHalo
import com.bsfreetv.launcher.ui.components.trapFocus
import com.bsfreetv.launcher.ui.components.tvClickable
import com.bsfreetv.launcher.ui.LocalBsfColors

fun HomeUi.filtered(filter: AppFilter): List<AppEntry> = when (filter) {
    AppFilter.ALL -> all
    AppFilter.UNSORTED -> unsorted
    AppFilter.MEDIA -> media
    AppFilter.TOOLS -> tools
    AppFilter.HIDDEN -> hidden
}

private fun Section?.tag(): String? = when (this) {
    Section.MEDIA -> "MEDIA"
    Section.TOOLS -> "TOOL"
    Section.HIDDEN -> "HIDDEN"
    null -> null
}

/** Every app, filterable by section. Hold OK (or Menu) on one to sort it. */
@Composable
fun AllAppsOverlay(ui: HomeUi, filter: AppFilter, actions: HomeActions) {
    val colors = LocalBsfColors.current
    val apps = ui.filtered(filter)
    val first = remember { FocusRequester() }
    val tabFocus = remember { FocusRequester() }
    LaunchedEffect(Unit) {
        withFrameNanos { }
        runCatching { if (apps.isNotEmpty()) first.requestFocus() else tabFocus.requestFocus() }
    }
    Box(Modifier.fillMaxSize().background(BsfColors.Black.copy(alpha = 0.93f)).trapFocus()) {
        Column(Modifier.fillMaxSize().padding(horizontal = 48.dp, vertical = 28.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(if (filter == AppFilter.TOOLS) "Tools" else "All apps", style = BsfType.title, color = colors.text)
                Text(
                    "Hold OK on an app to sort it",
                    style = BsfType.label,
                    color = colors.textMuted,
                    modifier = Modifier.padding(start = 16.dp, top = 4.dp),
                )
            }
            Row(Modifier.padding(top = 14.dp, bottom = 6.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                AppFilter.entries.forEach { f ->
                    val count = ui.filtered(f).size
                    FilterTab(
                        "${f.label} $count",
                        selected = f == filter,
                        modifier = if (f == filter) Modifier.focusRequester(tabFocus) else Modifier,
                    ) { actions.open(Overlay.AllApps(f)) }
                }
            }
            if (apps.isEmpty()) {
                Text(
                    when (filter) {
                        AppFilter.UNSORTED -> "Everything is sorted."
                        AppFilter.HIDDEN -> "Nothing hidden."
                        else -> "No apps here yet."
                    },
                    style = BsfType.body,
                    color = colors.textMuted,
                    modifier = Modifier.padding(top = 24.dp),
                )
            }
            LazyVerticalGrid(
                columns = GridCells.Fixed(5),
                contentPadding = PaddingValues(vertical = 16.dp, horizontal = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(18.dp),
                verticalArrangement = Arrangement.spacedBy(18.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                items(apps, key = { it.packageName }, contentType = { "app" }) { app ->
                    val m = if (app == apps.first()) Modifier.focusRequester(first) else Modifier
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        AppBannerCard(
                            app = app,
                            width = 152.dp,
                            badge = if (filter == AppFilter.ALL) ui.sections[app.packageName].tag() else null,
                            modifier = m,
                            onLongClick = { actions.openMenu(app.packageName) },
                            onClick = { actions.launch(app.packageName) },
                        )
                        Text(app.label, style = BsfType.label, color = colors.textMuted, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 8.dp).width(152.dp))
                    }
                }
            }
        }
    }
}

@Composable
private fun FilterTab(text: String, selected: Boolean, modifier: Modifier = Modifier, onClick: () -> Unit) {
    var focused by remember { mutableStateOf(false) }
    val colors = LocalBsfColors.current
    Box(
        modifier
            .tvClickable(onFocus = { focused = it }, onClick = onClick)
            .clip(RoundedCornerShape(14.dp))
            .background(
                when {
                    focused -> colors.accent
                    selected -> colors.surfaceHigh
                    else -> Color.Transparent
                },
            )
            .padding(horizontal = 12.dp, vertical = 5.dp),
    ) {
        Text(text, style = BsfType.label, color = if (focused) colors.onAccent else if (selected) colors.text else colors.textMuted)
    }
}

/** Context menu for one app: where it lives, reorder, and the system actions. */
@Composable
fun AppMenu(app: AppEntry, section: Section?, actions: HomeActions) {
    val colors = LocalBsfColors.current
    val first = remember { FocusRequester() }
    LaunchedEffect(app.packageName) {
        withFrameNanos { }
        runCatching { first.requestFocus() }
    }
    Box(Modifier.fillMaxSize().background(BsfColors.Black.copy(alpha = 0.6f)), contentAlignment = Alignment.Center) {
        Column(
            Modifier
                .width(340.dp)
                .clip(RoundedCornerShape(14.dp))
                .background(colors.surface)
                .padding(18.dp)
                .trapFocus(),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Image(app.icon, null, Modifier.size(36.dp), filterQuality = FilterQuality.Medium)
                Column(Modifier.padding(start = 12.dp)) {
                    Text(app.label, style = BsfType.bodyStrong, color = colors.text, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(section?.let { it.name.lowercase().replaceFirstChar(Char::uppercase) } ?: "Unsorted", style = BsfType.label, color = colors.textMuted)
                }
            }
            Column(Modifier.padding(top = 14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                val items = buildList<Pair<String, () -> Unit>> {
                    if (section != Section.MEDIA) add("Add to Media" to { actions.assign(app.packageName, Section.MEDIA) })
                    if (section != Section.TOOLS) add("Move to Tools" to { actions.assign(app.packageName, Section.TOOLS) })
                    if (section == Section.MEDIA || section == Section.TOOLS) add("Reorder" to { actions.startMove(app.packageName) })
                    if (section != null) add("Move to Unsorted" to { actions.assign(app.packageName, null) })
                    if (section != Section.HIDDEN) add("Hide" to { actions.assign(app.packageName, Section.HIDDEN) })
                    add("App info" to { actions.appInfo(app.packageName) })
                    add("Uninstall" to { actions.uninstall(app.packageName) })
                }
                items.forEachIndexed { i, (label, action) ->
                    MenuItem(label, if (i == 0) Modifier.focusRequester(first) else Modifier, action)
                }
            }
        }
    }
}

@Composable
private fun MenuItem(text: String, modifier: Modifier, onClick: () -> Unit) {
    var focused by remember { mutableStateOf(false) }
    val colors = LocalBsfColors.current
    Box(
        modifier
            .fillMaxWidth()
            .focusHalo(focused, scaleTo = 1.02f, dimUnfocused = false)
            .tvClickable(onFocus = { focused = it }, onClick = onClick)
            .clip(RoundedCornerShape(8.dp))
            .background(if (focused) colors.accent else colors.surfaceHigh.copy(alpha = 0.6f))
            .padding(horizontal = 14.dp, vertical = 9.dp),
    ) {
        Text(text, style = BsfType.bodyStrong, color = if (focused) colors.onAccent else colors.text)
    }
}
