package com.bsfreetv.launcher.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
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
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.bsfreetv.launcher.ui.BsfType
import com.bsfreetv.launcher.ui.forScript
import com.bsfreetv.launcher.ui.LocalBsfColors
import com.bsfreetv.launcher.ui.upper

/**
 * Keeps D-pad focus inside an overlay: nothing behind it can be reached.
 * The trap also blocks requestFocus() from outside, so an overlay that opens a
 * menu on top of itself must pass enabled = false while the menu shows, or the
 * menu can never take focus.
 */
fun Modifier.trapFocus(enabled: Boolean = true): Modifier =
    this.focusProperties { onExit = { if (enabled) cancelFocusChange() } }.focusGroup()

/** Overline section header inside a panel. */
@Composable
fun SectionHeader(text: String, modifier: Modifier = Modifier) {
    val colors = LocalBsfColors.current
    Text(text.upper(), style = BsfType.overline.forScript(), color = colors.accent, modifier = modifier.padding(start = 14.dp, top = 18.dp, bottom = 6.dp))
}

/**
 * One settings row: label (+ optional description) and a value on the right.
 * Focus fills the row with the raised surface and an accent edge, so the
 * focused row is obvious from the sofa without any motion at all.
 */
@Composable
fun SettingRow(
    label: String,
    value: String? = null,
    description: String? = null,
    chevron: Boolean = false,
    swatch: Color? = null,
    enabled: Boolean = true,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    var focused by remember { mutableStateOf(false) }
    val colors = LocalBsfColors.current
    Row(
        modifier
            .fillMaxWidth()
            .tvClickable(onFocus = { focused = it }, onClick = { if (enabled) onClick() })
            .clip(RoundedCornerShape(8.dp))
            .background(if (focused) colors.surfaceHigh else Color.Transparent)
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.width(3.dp).size(3.dp, 22.dp).clip(RoundedCornerShape(2.dp)).background(if (focused) colors.accent else Color.Transparent))
        Column(Modifier.weight(1f).padding(start = 10.dp)) {
            Text(label, style = BsfType.bodyStrong, color = if (enabled) colors.text else colors.textMuted)
            if (description != null) Text(description, style = BsfType.label, color = colors.textMuted, modifier = Modifier.padding(top = 2.dp))
        }
        if (swatch != null) Box(Modifier.padding(end = 8.dp).size(12.dp).clip(CircleShape).background(swatch))
        // Capped so a long translated value never squeezes the label into wrapping.
        if (value != null) Text(value, style = BsfType.body, color = if (focused) colors.text else colors.textMuted, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(start = 12.dp).widthIn(max = 200.dp))
        if (chevron) Text("›", style = BsfType.title, color = colors.textMuted, modifier = Modifier.padding(start = 8.dp))
    }
}

/** A primary or secondary button inside panels. */
@Composable
fun PanelButton(text: String, modifier: Modifier = Modifier, primary: Boolean = false, enabled: Boolean = true, onClick: () -> Unit) {
    var focused by remember { mutableStateOf(false) }
    val colors = LocalBsfColors.current
    val bg = when {
        focused -> colors.accent
        primary -> colors.accent.copy(alpha = 0.22f)
        else -> colors.surfaceHigh
    }
    Box(
        modifier
            .tvClickable(onFocus = { focused = it }, onClick = { if (enabled) onClick() })
            .clip(RoundedCornerShape(8.dp))
            .background(bg)
            .padding(horizontal = 16.dp, vertical = 10.dp),
    ) {
        Text(text, style = BsfType.bodyStrong, color = if (focused) colors.onAccent else if (enabled) colors.text else colors.textMuted)
    }
}

@Composable
fun Gap(h: Int) = Spacer(Modifier.padding(top = h.dp))

/** A status line: a coloured dot and text. */
@Composable
fun StatusLine(ok: Boolean, text: String, modifier: Modifier = Modifier, small: Boolean = false) {
    val colors = LocalBsfColors.current
    Row(modifier.fillMaxWidth().padding(vertical = 3.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(if (small) 6.dp else 8.dp).clip(CircleShape).background(if (ok) Color(0xFFA6E3A1) else Color(0xFFF9E2AF)))
        Text(
            text,
            style = if (small) BsfType.label else BsfType.body,
            color = if (small) colors.textMuted else colors.text,
            modifier = Modifier.padding(start = if (small) 11.dp else 10.dp),
        )
    }
}

/**
 * A block of text the D-pad can land on. TV panels only scroll by moving
 * focus, so text with nothing focusable around it can end up unreachable;
 * every info block on a settings page is one of these. Focus shows the same
 * accent edge as a settings row.
 */
@Composable
fun InfoBlock(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    var focused by remember { mutableStateOf(false) }
    val colors = LocalBsfColors.current
    Row(
        modifier
            .fillMaxWidth()
            .height(IntrinsicSize.Min)
            .onFocusChanged { focused = it.isFocused }
            .focusable()
            .clip(RoundedCornerShape(8.dp))
            .background(if (focused) colors.surfaceHigh.copy(alpha = 0.6f) else Color.Transparent)
            .padding(horizontal = 14.dp, vertical = 10.dp),
    ) {
        Box(Modifier.width(3.dp).fillMaxHeight().clip(RoundedCornerShape(2.dp)).background(if (focused) colors.accent else Color.Transparent))
        Column(Modifier.weight(1f).padding(start = 10.dp), content = content)
    }
}

/** Shell commands in a dark, monospaced box (never translated). */
@Composable
fun CodeBlock(lines: List<String>, modifier: Modifier = Modifier) {
    val colors = LocalBsfColors.current
    Column(modifier.fillMaxWidth().padding(top = 6.dp).clip(RoundedCornerShape(6.dp)).background(Color.Black.copy(alpha = 0.5f)).padding(10.dp)) {
        lines.forEach { Text(it, style = BsfType.mono, color = colors.text) }
    }
}

