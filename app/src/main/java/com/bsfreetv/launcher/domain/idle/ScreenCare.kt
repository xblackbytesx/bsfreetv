package com.bsfreetv.launcher.domain.idle

/** What the home screen shows after a stretch without input. */
enum class IdlePhase { ACTIVE, AMBIENT, BLACK }

/**
 * Burn-in policy. After [ambientAfterMs] without input the chrome (clock, rows,
 * pills) fades away and only the wallpaper shows; [blackAfterMs] later the screen
 * goes true black. 0 disables a stage.
 */
data class IdlePolicy(val ambientAfterMs: Long, val blackAfterMs: Long) {
    fun phase(idleMs: Long): IdlePhase = when {
        ambientAfterMs <= 0 -> if (blackAfterMs > 0 && idleMs >= blackAfterMs) IdlePhase.BLACK else IdlePhase.ACTIVE
        idleMs < ambientAfterMs -> IdlePhase.ACTIVE
        blackAfterMs > 0 && idleMs >= ambientAfterMs + blackAfterMs -> IdlePhase.BLACK
        else -> IdlePhase.AMBIENT
    }

    /** Milliseconds until the phase can next change, or null when it never will. */
    fun nextChangeIn(idleMs: Long): Long? {
        val edges = buildList {
            if (ambientAfterMs > 0) add(ambientAfterMs)
            if (blackAfterMs > 0) add(if (ambientAfterMs > 0) ambientAfterMs + blackAfterMs else blackAfterMs)
        }
        return edges.filter { it > idleMs }.minOrNull()?.minus(idleMs)
    }
}

/**
 * Pixel shift for static chrome: a slow orbit through a 3x3 grid of offsets,
 * one step every [stepMs], so no pixel of text or ring sits still for hours.
 * Offsets are in dp, within +-[radiusDp].
 */
object PixelShift {
    private val orbit = listOf(0 to 0, 1 to 0, 1 to 1, 0 to 1, -1 to 1, -1 to 0, -1 to -1, 0 to -1, 1 to -1)

    fun offset(nowMs: Long, stepMs: Long = 3 * 60_000L, radiusDp: Int = 4): Pair<Int, Int> {
        val (x, y) = orbit[((nowMs / stepMs) % orbit.size).toInt()]
        return x * radiusDp to y * radiusDp
    }
}
