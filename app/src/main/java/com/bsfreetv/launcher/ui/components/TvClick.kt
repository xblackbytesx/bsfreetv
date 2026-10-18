package com.bsfreetv.launcher.ui.components

import android.view.KeyEvent as AndroidKeyEvent
import androidx.compose.foundation.focusable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.composed
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.Modifier

private fun isSelect(code: Int) = code == AndroidKeyEvent.KEYCODE_DPAD_CENTER ||
    code == AndroidKeyEvent.KEYCODE_ENTER || code == AndroidKeyEvent.KEYCODE_NUMPAD_ENTER ||
    code == AndroidKeyEvent.KEYCODE_BUTTON_A

/**
 * D-pad click with long-press (hold OK) and the Menu key as a secondary action.
 * Long-press fires once on the first key repeat and swallows the release, so
 * the click never follows it. A release only clicks when this element also saw
 * the press: after a long-press opens a menu, the release lands on the menu's
 * first item and must not activate it.
 *
 * Pointer input (air mice, the Shield remote's touch-free pointer apps) is a
 * tap = click and a press-and-hold = long click, and focuses the item first so
 * the D-pad continues from where the pointer was.
 */
fun Modifier.tvClickable(
    onLongClick: (() -> Unit)? = null,
    onFocus: ((Boolean) -> Unit)? = null,
    interaction: MutableInteractionSource? = null,
    onClick: () -> Unit,
): Modifier = composed {
    var longFired by remember { mutableStateOf(false) }
    var pressed by remember { mutableStateOf(false) }
    val source = interaction ?: remember { MutableInteractionSource() }
    val focus = remember { FocusRequester() }
    val click by rememberUpdatedState(onClick)
    val longClick by rememberUpdatedState(onLongClick)
    this
        .pointerInput(Unit) {
            detectTapGestures(
                onLongPress = { focus.requestFocus(); longClick?.invoke() },
                onTap = { focus.requestFocus(); click() },
            )
        }
        .focusRequester(focus)
        .onFocusChanged {
            if (!it.isFocused) pressed = false
            onFocus?.invoke(it.isFocused)
        }
        .onKeyEvent { event ->
            val native = event.nativeKeyEvent
            val code = native.keyCode
            when {
                code == AndroidKeyEvent.KEYCODE_MENU && onLongClick != null -> {
                    if (event.type == KeyEventType.KeyUp) onLongClick()
                    true
                }
                isSelect(code) && event.type == KeyEventType.KeyDown -> {
                    if (native.repeatCount == 0) {
                        longFired = false
                        pressed = true
                    }
                    if (native.repeatCount >= 1 && !longFired && onLongClick != null) {
                        longFired = true
                        onLongClick()
                    }
                    true
                }
                isSelect(code) && event.type == KeyEventType.KeyUp -> {
                    if (pressed && !longFired && !native.isCanceled) onClick()
                    longFired = false
                    pressed = false
                    true
                }
                else -> false
            }
        }
        .focusable(interactionSource = source)
}
