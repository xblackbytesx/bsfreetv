package com.bsfreetv.launcher.ui.components

import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.tween
import androidx.compose.runtime.staticCompositionLocalOf
import com.bsfreetv.launcher.domain.Motion

val LocalMotion = staticCompositionLocalOf { Motion.FULL }

/** Focus scale/alpha: a soft spring when Full, a short tween when Reduced, instant when Off. */
fun focusSpec(motion: Motion): AnimationSpec<Float> = when (motion) {
    Motion.FULL -> spring(dampingRatio = 0.82f, stiffness = Spring.StiffnessMediumLow)
    Motion.REDUCED -> tween(90)
    Motion.OFF -> snap()
}

fun fadeSpec(motion: Motion, fullMs: Int): AnimationSpec<Float> = when (motion) {
    Motion.FULL -> tween(fullMs)
    Motion.REDUCED -> tween(fullMs / 3)
    Motion.OFF -> snap()
}
