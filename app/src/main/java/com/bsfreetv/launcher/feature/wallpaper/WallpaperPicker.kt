package com.bsfreetv.launcher.feature.wallpaper

import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.bsfreetv.launcher.data.Wallpaper
import com.bsfreetv.launcher.domain.palette.LauncherPalette
import com.bsfreetv.launcher.ui.BsfType
import com.bsfreetv.launcher.ui.components.focusHalo
import com.bsfreetv.launcher.ui.components.Thumb
import com.bsfreetv.launcher.ui.components.trapFocus
import com.bsfreetv.launcher.ui.components.tvClickable
import com.bsfreetv.launcher.ui.LocalBsfColors
import com.bsfreetv.launcher.ui.toColors
import com.bsfreetv.launcher.ui.forScript
import com.bsfreetv.launcher.R
import androidx.compose.ui.res.stringResource

/**
 * Bottom strip of thumbnails over the live wallpaper. Focusing one previews it
 * full screen (and re-tints the UI) after a short pause; OK keeps it, Back
 * returns to the saved one.
 */
@Composable
fun WallpaperPickerOverlay(
    wallpapers: List<Wallpaper>,
    currentId: String,
    canImport: Boolean,
    userDir: String?,
    loadThumb: suspend (String) -> ImageBitmap?,
    loadPalette: suspend (String) -> LauncherPalette,
    onPreview: (String) -> Unit,
    onChoose: (String) -> Unit,
    onImport: () -> Unit,
) {
    val colors = LocalBsfColors.current
    var focusedId by remember { mutableStateOf(currentId) }
    val focused = wallpapers.firstOrNull { it.id == focusedId }
    val start = remember { FocusRequester() }
    val listState = rememberLazyListState()
    LaunchedEffect(wallpapers.isNotEmpty()) {
        if (wallpapers.isEmpty()) return@LaunchedEffect
        val i = wallpapers.indexOfFirst { it.id == currentId }.coerceAtLeast(0)
        listState.scrollToItem(i)
        withFrameNanos { }
        runCatching { start.requestFocus() }
    }
    Box(Modifier.fillMaxSize()) {
        Column(
            Modifier
                .align(Alignment.BottomStart)
                .fillMaxWidth()
                .background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.85f), Color.Black)))
                .padding(top = 40.dp, bottom = 26.dp)
                .trapFocus(),
        ) {
            Row(Modifier.padding(horizontal = 48.dp), verticalAlignment = Alignment.Bottom) {
                Column(Modifier.weight(1f)) {
                    Text(stringResource(R.string.wp_title), style = BsfType.overline.forScript(), color = colors.accent)
                    Text(focused?.title ?: "", style = BsfType.title, color = colors.text, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(
                        focused?.credit ?: if (focused?.isUser == true) stringResource(R.string.wp_your_image) else "",
                        style = BsfType.label,
                        color = colors.textMuted,
                    )
                }
                Text(stringResource(R.string.wp_keep_hint), style = BsfType.label, color = colors.textMuted)
            }
            LazyRow(
                state = listState,
                contentPadding = PaddingValues(horizontal = 48.dp, vertical = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(14.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                if (canImport) item(key = "import") { ImportTile(onImport) }
                items(wallpapers, key = { it.id }) { w ->
                    WallpaperTile(
                        wallpaper = w,
                        selected = w.id == currentId,
                        loadThumb = loadThumb,
                        loadPalette = loadPalette,
                        modifier = if (w.id == currentId || (currentId !in wallpapers.map { it.id } && w == wallpapers.first())) Modifier.focusRequester(start) else Modifier,
                        onFocus = { focusedId = w.id; onPreview(w.id) },
                        onClick = { onChoose(w.id) },
                    )
                }
            }
            userDir?.let {
                Text(
                    stringResource(R.string.wp_add_own, it),
                    style = BsfType.label,
                    color = colors.textMuted.copy(alpha = 0.8f),
                    modifier = Modifier.padding(horizontal = 48.dp),
                )
            }
        }
    }
}

@Composable
private fun WallpaperTile(
    wallpaper: Wallpaper,
    selected: Boolean,
    loadThumb: suspend (String) -> ImageBitmap?,
    loadPalette: suspend (String) -> LauncherPalette,
    modifier: Modifier,
    onFocus: () -> Unit,
    onClick: () -> Unit,
) {
    var focused by remember { mutableStateOf(false) }
    val image by produceState<ImageBitmap?>(null, wallpaper.id) { value = loadThumb(wallpaper.id) }
    val accent by produceState<Color?>(null, wallpaper.id) { value = loadPalette(wallpaper.id).toColors().accent }
    val colors = LocalBsfColors.current
    Box(
        modifier
            .width(192.dp)
            .height(108.dp)
            .focusHalo(focused)
            .tvClickable(onFocus = { focused = it; if (it) onFocus() }, onClick = onClick)
            .clip(RoundedCornerShape(10.dp)),
    ) {
        Thumb(image, Modifier.fillMaxSize())
        Row(Modifier.align(Alignment.BottomStart).padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
            accent?.let { Box(Modifier.size(10.dp).clip(CircleShape).background(it)) }
            if (selected) Text(stringResource(R.string.wp_current), style = BsfType.overline.forScript(), color = colors.text, modifier = Modifier.padding(start = 6.dp).clip(RoundedCornerShape(4.dp)).background(Color.Black.copy(alpha = 0.6f)).padding(horizontal = 4.dp))
        }
    }
}

@Composable
private fun ImportTile(onClick: () -> Unit) {
    var focused by remember { mutableStateOf(false) }
    val colors = LocalBsfColors.current
    Box(
        Modifier
            .width(108.dp)
            .height(108.dp)
            .focusHalo(focused)
            .tvClickable(onFocus = { focused = it }, onClick = onClick)
            .clip(RoundedCornerShape(10.dp))
            .background(colors.surfaceHigh),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text("+", style = BsfType.hero, color = colors.accent)
            Text(stringResource(R.string.wp_add_image), style = BsfType.label, color = colors.text)
        }
    }
}
