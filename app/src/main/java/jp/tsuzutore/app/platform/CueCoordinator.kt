package jp.tsuzutore.app.platform

import android.util.Log
import jp.tsuzutore.app.domain.*
import kotlin.math.ceil
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * Plays cues for live timer transitions for as long as the process runs, so the screen still gets
 * sound and vibration even when the foreground service could not be started.
 * The state restored at launch, or a finished run brought in by an import, is never replayed.
 */
class CueCoordinator(private val controller: WorkoutController, private val cues: CuePlayer) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate +
        CoroutineExceptionHandler { _, failure -> Log.w("TsuzuTore", "Cue unavailable", failure) })
    private var previous: RunState? = null
    private var previousSecond: Long? = null

    init {
        scope.launch {
            controller.awaitReady()
            controller.run.collect { state ->
                runCatching { onState(state, controller.settings.value) }
                    .onFailure { Log.w("TsuzuTore", "Cue unavailable", it) }
            }
        }
    }

    private fun onState(state: RunState, settings: AppSettings) {
        val old = previous
        previous = state
        if (old == null) {
            previousSecond = countdownSecond(state)
            return
        }
        val sameRun = old.session?.id == state.session?.id
        val changed = !sameRun || old.stage != state.stage || old.exerciseIndex != state.exerciseIndex ||
            old.setNumber != state.setNumber || old.side != state.side
        if (changed && state.stage == TimerStage.PAUSED) cues.silence()
        // A different run that appears already paused or finished (restore/import) is not a live transition.
        val live = sameRun || (state.active && state.stage != TimerStage.PAUSED)
        if (changed && live && state.session != null && state.stage != TimerStage.PAUSED) {
            cueText(state)?.let { cues.play(it, settings) }
            previousSecond = null
        }
        val second = countdownSecond(state)
        if (second != null && second in 1..3 && previousSecond != second) cues.play(second.toString(), settings, countdown = true)
        previousSecond = second
    }

    private fun cueText(state: RunState): String? = when (state.stage) {
        TimerStage.WARMUP -> "準備運動を始めましょう"
        TimerStage.READY -> "${state.exercise?.name.orEmpty()}。準備ができたら開始してください"
        TimerStage.SIDE_SWITCH -> "右側へ切り替えましょう。準備ができたら開始してください"
        TimerStage.EXERCISING -> "開始"
        TimerStage.RESTING -> "セット終了。休憩しましょう"
        TimerStage.COMPLETED -> "おつかれさまでした。記録を保存しました"
        TimerStage.INTERRUPTED -> "ここまでの記録を保存しました"
        else -> null
    }

    private fun countdownSecond(state: RunState): Long? {
        val countdown = state.stage in setOf(TimerStage.WARMUP, TimerStage.PREPARING, TimerStage.RESTING) ||
            (state.stage == TimerStage.EXERCISING && state.exercise?.mode == ExerciseMode.TIME)
        return if (countdown) ceil(state.remainingMs / 1000.0).toLong() else null
    }
}
