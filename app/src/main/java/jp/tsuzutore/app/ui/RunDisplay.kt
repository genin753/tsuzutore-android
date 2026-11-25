package jp.tsuzutore.app.ui

import jp.tsuzutore.app.domain.RunState

/** Whole seconds as the timer shows them (a remaining 0.2 s still reads as 1 s). */
internal fun displaySeconds(ms: Long): Long = (ms.coerceAtLeast(0) + 999L) / 1000L

/**
 * The engine is sampled five times a second for accurate cues, but the screen only shows whole
 * seconds. Samples that look identical need not rebuild and recompose the whole app.
 */
internal fun RunState.displaysSameAs(other: RunState): Boolean =
    displaySeconds(elapsedMs) == displaySeconds(other.elapsedMs) &&
        displaySeconds(remainingMs) == displaySeconds(other.remainingMs) &&
        copy(elapsedMs = 0, remainingMs = 0) == other.copy(elapsedMs = 0, remainingMs = 0)
