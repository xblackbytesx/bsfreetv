package com.bsfreetv.launcher.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.Dp
import com.bsfreetv.launcher.data.AppEntry
import com.bsfreetv.launcher.domain.ToolSize
import com.bsfreetv.launcher.ui.BsfType
import com.bsfreetv.launcher.ui.LocalBsfColors
import com.bsfreetv.launcher.ui.forScript
import com.bsfreetv.launcher.R
import androidx.compose.ui.res.stringResource

private val CardShape = RoundedCornerShape(10.dp)

/**
 * Focus treatment shared by every card: scale, an accent ring and a soft accent
 * halo. All three are draw-phase only (graphicsLayer / drawBehind reading
 * animated floats), so moving focus never re-measures a row.
 */
@Composable
fun Modifier.focusHalo(focused: Boolean, corner: Dp = 10.dp, scaleTo: Float = 1.08f, dimUnfocused: Boolean = true): Modifier {
    val colors = LocalBsfColors.current
    val motion = LocalMotion.current
    val t by animateFloatAsState(if (focused) 1f else 0f, focusSpec(motion), label = "focus")
    return this
        .graphicsLayer {
            val s = 1f + (scaleTo - 1f) * t
            scaleX = s
            scaleY = s
        }
        .drawWithContent {
            drawContent()
            // Unfocused cards sink a little under a black wash; they stay
            // opaque, so the wallpaper never shows through a banner.
            if (dimUnfocused && t < 0.99f) drawRoundRect(Color.Black, alpha = 0.28f * (1f - t), cornerRadius = CornerRadius(corner.toPx()))
        }
        .drawBehind {
            if (t <= 0.01f) return@drawBehind
            val r = corner.toPx()
            // Halo: three widening translucent rounded rects instead of a blur
            // (RenderEffect needs API 31; the Shield is 30).
            for (i in 1..3) {
                val grow = 3.dp.toPx() * i
                drawRoundRect(
                    color = colors.accent.copy(alpha = 0.16f * t / i),
                    topLeft = Offset(-grow, -grow),
                    size = Size(size.width + grow * 2, size.height + grow * 2),
                    cornerRadius = CornerRadius(r + grow),
                )
            }
        }
}

/** The accent ring drawn over the content (so banners never cover it). */
@Composable
private fun Modifier.focusRing(focused: Boolean, corner: Dp = 10.dp): Modifier {
    val colors = LocalBsfColors.current
    val t by animateFloatAsState(if (focused) 1f else 0f, focusSpec(LocalMotion.current), label = "ring")
    return this.drawWithContent {
        drawContent()
        if (t > 0.01f) {
            val w = 2.5.dp.toPx()
            drawRoundRect(
                color = colors.accent.copy(alpha = t),
                topLeft = Offset(w / 2, w / 2),
                size = Size(size.width - w, size.height - w),
                cornerRadius = CornerRadius(corner.toPx() - w / 2),
                style = Stroke(w),
            )
        }
    }
}

/** A media app: its 16:9 banner, or icon + name on a tinted plate when it has none. */
@Composable
fun AppBannerCard(
    app: AppEntry,
    width: Dp,
    modifier: Modifier = Modifier,
    moving: Boolean = false,
    badge: String? = null,
    onFocus: (Boolean) -> Unit = {},
    onLongClick: () -> Unit = {},
    onClick: () -> Unit,
) {
    var focused by remember { mutableStateOf(false) }
    val colors = LocalBsfColors.current
    Box(
        modifier
            .width(width)
            .height(width * 9 / 16)
            .focusHalo(focused || moving)
            .tvClickable(onLongClick = onLongClick, onFocus = { focused = it; onFocus(it) }, onClick = onClick)
            .clip(CardShape)
            .background(colors.surfaceHigh)
            .focusRing(focused),
    ) {
        if (app.banner != null) {
            Image(app.banner, app.label, Modifier.fillMaxSize(), contentScale = ContentScale.Crop, filterQuality = FilterQuality.Medium)
        } else {
            Column(Modifier.fillMaxSize().padding(10.dp), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
                Image(app.icon, null, Modifier.size(width * 0.26f), filterQuality = FilterQuality.Medium)
                Text(app.label, style = BsfType.label, color = colors.text, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 6.dp))
            }
        }
        if (moving) MoveArrows()
        if (badge != null) {
            Text(
                badge,
                style = BsfType.overline.forScript(),
                color = colors.onAccent,
                modifier = Modifier.align(Alignment.TopEnd).padding(6.dp).clip(RoundedCornerShape(4.dp)).background(colors.accent).padding(horizontal = 5.dp, vertical = 1.dp),
            )
        }
    }
}

@Composable
private fun MoveArrows() {
    val colors = LocalBsfColors.current
    Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.55f))) {
        Text("‹  ${stringResource(R.string.card_move)}  ›", style = BsfType.bodyStrong, color = colors.text, modifier = Modifier.align(Alignment.Center))
    }
}

/** A tool: small round icon chip; the name shows only while focused. */
@Composable
fun ToolChip(
    app: AppEntry,
    modifier: Modifier = Modifier,
    size: ToolSize = ToolSize.MEDIUM,
    moving: Boolean = false,
    onLongClick: () -> Unit = {},
    onClick: () -> Unit,
) {
    var focused by remember { mutableStateOf(false) }
    val colors = LocalBsfColors.current
    Column(modifier.width(size.slotDp.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        // The icon is already round and full-size (IconShaper); the chip is just
        // the icon plus a ring: hairline at rest, accent and halo on focus.
        Box(
            Modifier
                .size(size.iconDp.dp)
                .focusHalo(focused || moving, corner = (size.iconDp / 2).dp, scaleTo = 1.12f, dimUnfocused = false)
                .tvClickable(onLongClick = onLongClick, onFocus = { focused = it }, onClick = onClick)
                .clip(CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Image(app.icon, app.label, Modifier.fillMaxSize(), filterQuality = FilterQuality.Medium)
            Box(
                Modifier
                    .fillMaxSize()
                    .border(if (focused || moving) 2.dp else 1.dp, if (focused || moving) colors.accent else Color.White.copy(alpha = 0.18f), CircleShape),
            )
        }
        Text(
            if (moving) "‹ ${app.label} ›" else app.label,
            style = BsfType.label,
            color = colors.text,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = 6.dp).graphicsLayer { alpha = if (focused || moving) 1f else 0f },
        )
    }
}

/** Thin accent progress line used by Next Up cards. */
@Composable
fun ProgressLine(progress: Float, modifier: Modifier = Modifier) {
    val colors = LocalBsfColors.current
    Canvas(modifier.fillMaxWidth().height(3.dp)) {
        drawRect(Color.White.copy(alpha = 0.18f))
        drawRect(colors.accent, size = Size(size.width * progress, size.height))
    }
}

/** A labelled pill in the top bar (Apps, Tools, Settings). */
@Composable
fun Pill(
    text: String,
    modifier: Modifier = Modifier,
    leading: (@Composable () -> Unit)? = null,
    dot: Boolean = false,
    onClick: () -> Unit,
) {
    var focused by remember { mutableStateOf(false) }
    val colors = LocalBsfColors.current
    Row(
        modifier
            .focusHalo(focused, corner = 16.dp, scaleTo = 1.05f, dimUnfocused = false)
            .tvClickable(onFocus = { focused = it }, onClick = onClick)
            .clip(RoundedCornerShape(16.dp))
            .background(if (focused) colors.accent else Color.Black.copy(alpha = 0.35f))
            .padding(horizontal = 12.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        leading?.invoke()
        Text(text, style = BsfType.label, color = if (focused) colors.onAccent else colors.text.copy(alpha = 0.85f))
        if (dot) Box(Modifier.padding(start = 6.dp).size(6.dp).clip(CircleShape).background(if (focused) colors.onAccent else colors.accent))
    }
}

/** Bitmap filled into the box; placeholder plate while null. */
@Composable
fun Thumb(image: ImageBitmap?, modifier: Modifier = Modifier) {
    val colors = LocalBsfColors.current
    if (image == null) Box(modifier.background(colors.surfaceHigh))
    else Image(image, null, modifier, contentScale = ContentScale.Crop, filterQuality = FilterQuality.Medium)
}
