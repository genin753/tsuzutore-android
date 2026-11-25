package jp.tsuzutore.app.domain

fun interface MonotonicClock { fun nowMs(): Long }

sealed interface TimerAction {
    data object StartSet : TimerAction
    data object CompleteSet : TimerAction
    data class CompleteReps(val reps: Int, val load: String? = null) : TimerAction
    data class FinishTimed(val load: String? = null) : TimerAction
    data object Pause : TimerAction
    data object Resume : TimerAction
    data class ExtendRest(val seconds: Int = 30) : TimerAction
    data class AdjustTime(val seconds: Int) : TimerAction
    data class SelectExercise(val exerciseId: String) : TimerAction
    data object SkipSet : TimerAction
    data object SkipExercise : TimerAction
    data object SkipWarmup : TimerAction
    data class Stop(val endedAt: Long, val actualReps: Int? = null, val load: String? = null) : TimerAction
}

/**
 * The service owns one instance. A tick only samples monotonic time; it never adds a second.
 * Revisions change on transitions, not display samples. Commands captured before a transition
 * cannot complete the following set, even when a tap or notification is delivered twice.
 */
class TimerEngine(private val clock: MonotonicClock) {
    var state: RunState = RunState()
        private set
    private var deadlineMs: Long? = null
    private var anchorMs: Long? = null
    private var elapsedAtAnchor: Long = 0

    @Synchronized
    fun start(plan: WorkoutPlan, id: String, walltime: Long, localDate: String, zoneId: String): RunState {
        check(!state.active) { "進行中のワークアウトを先に終了してください" }
        require(validatePlan(plan).isEmpty()) { validatePlan(plan).joinToString("\n") }
        val snapshot = plan.copy(exercises = plan.exercises.map { it.copy() })
        state = RunState(
            session = WorkoutSession(id, walltime, localDate, zoneId, snapshot),
            side = if (snapshot.exercises.first().bilateral) Side.LEFT else Side.BOTH,
            revision = state.revision + 1,
        )
        val now = clock.nowMs()
        when {
            snapshot.warmupSeconds > 0 -> enter(TimerStage.WARMUP, snapshot.warmupSeconds * 1000L, now)
            snapshot.kind == PlanKind.INTERVAL -> enter(TimerStage.PREPARING, 3000L, now)
            else -> enter(TimerStage.READY, now = now)
        }
        return state
    }

    @Synchronized
    fun restore(saved: RunState): RunState {
        state = restorePaused(saved)
        deadlineMs = null
        anchorMs = null
        elapsedAtAnchor = state.elapsedMs
        return state
    }

    /** Editing the user's form note must never rebuild or pause the active timer. */
    @Synchronized
    fun updateFormConfirmation(sessionId: String, confirmed: Boolean): RunState {
        val session = state.session ?: return state
        if (session.id != sessionId || session.formConfirmed == confirmed) return state
        state = state.copy(session = session.copy(formConfirmed = confirmed), revision = state.revision + 1)
        return state
    }

    @Synchronized
    fun tick(walltime: Long? = null): RunState {
        val now = clock.nowMs()
        // A delayed interval tick can cross multiple real deadlines without accumulating drift.
        // Normal workouts stop at READY/SIDE_SWITCH and require explicit acknowledgement.
        var transitions = 0
        while (deadlineMs != null && now >= deadlineMs!! && transitions++ < 1000) {
            val boundary = deadlineMs!!
            sample(boundary)
            when (state.stage) {
                TimerStage.WARMUP -> afterWarmup(boundary)
                TimerStage.PREPARING -> beginExercise(boundary)
                TimerStage.EXERCISING -> {
                    record(SetOutcome.COMPLETE)
                    afterSet(boundary, walltime)
                }
                TimerStage.RESTING -> if (state.session?.plan?.kind == PlanKind.INTERVAL) beginExercise(boundary) else enter(TimerStage.READY, now = boundary)
                else -> deadlineMs = null
            }
        }
        sample(now)
        return state
    }

    @Synchronized
    fun dispatch(action: TimerAction, expectedRevision: Long? = null, walltime: Long? = null): RunState {
        val revisionBeforeTick = state.revision
        tick(walltime)
        // Stop/Pause apply to the whole ongoing session. A deadline crossed by this very
        // dispatch must not discard a current stop confirmation. Older commands stay stale.
        val currentGlobalAction = expectedRevision == revisionBeforeTick && (action == TimerAction.Pause || action is TimerAction.Stop)
        if ((expectedRevision != null && expectedRevision != state.revision && !currentGlobalAction) || !state.active) return state
        val now = clock.nowMs()
        when (action) {
            TimerAction.StartSet -> if (state.stage == TimerStage.READY || state.stage == TimerStage.SIDE_SWITCH) {
                if (state.exercise?.mode == ExerciseMode.TIME) enter(TimerStage.PREPARING, 3000L, now) else beginExercise(now)
            }
            TimerAction.CompleteSet -> if (state.stage == TimerStage.EXERCISING && state.exercise?.mode == ExerciseMode.REPS) {
                record(SetOutcome.COMPLETE)
                afterSet(now, walltime)
            }
            is TimerAction.CompleteReps -> if (state.stage == TimerStage.EXERCISING && state.exercise?.mode == ExerciseMode.REPS) {
                require(action.reps in 0..999) { "実施回数は0〜999で入力してください" }
                require(action.load == null || action.load.length <= 120) { "負荷は120文字以内で入力してください" }
                record(if (action.reps > 0) SetOutcome.COMPLETE else SetOutcome.PARTIAL, action.reps, action.load)
                afterSet(now, walltime)
            }
            is TimerAction.FinishTimed -> if (state.stage == TimerStage.EXERCISING && state.exercise?.mode == ExerciseMode.TIME) {
                require(action.load == null || action.load.length <= 120) { "負荷は120文字以内で入力してください" }
                record(if (state.remainingMs == 0L) SetOutcome.COMPLETE else SetOutcome.PARTIAL, load = action.load)
                afterSet(now, walltime)
            }
            TimerAction.Pause -> if (state.stage !in setOf(TimerStage.PAUSED, TimerStage.IDLE, TimerStage.COMPLETED, TimerStage.INTERRUPTED)) {
                state = state.copy(stage = TimerStage.PAUSED, pausedStage = state.stage, revision = state.revision + 1)
                deadlineMs = null
                anchorMs = null
            }
            TimerAction.Resume -> if (state.stage == TimerStage.PAUSED) {
                val stage = state.pausedStage ?: TimerStage.READY
                enter(stage, state.remainingMs, now, state.elapsedMs, state.timeAdjustmentMs)
            }
            is TimerAction.ExtendRest -> if (state.stage == TimerStage.RESTING && action.seconds in 1..3600) adjustTime(action.seconds, now)
            is TimerAction.AdjustTime -> adjustTime(action.seconds, now)
            is TimerAction.SelectExercise -> selectExercise(action.exerciseId, now)
            TimerAction.SkipSet -> if (canSkip()) skipSet(now, walltime)
            TimerAction.SkipExercise -> if (canSkip()) skipExercise(now, walltime)
            TimerAction.SkipWarmup -> if (state.stage == TimerStage.WARMUP) afterWarmup(now)
            is TimerAction.Stop -> {
                require(action.actualReps == null || action.actualReps in 0..999) { "実施回数は0〜999で入力してください" }
                require(action.load == null || action.load.length <= 120) { "負荷は120文字以内で入力してください" }
                val effectiveStage = if (state.stage == TimerStage.PAUSED) state.pausedStage else state.stage
                if (effectiveStage == TimerStage.EXERCISING && state.elapsedMs > 0) {
                    if (state.exercise?.mode == ExerciseMode.TIME || action.actualReps != null) record(SetOutcome.PARTIAL, action.actualReps, action.load)
                }
                finish(SessionStatus.INTERRUPTED, action.endedAt)
            }
        }
        return state
    }

    private fun canSkip() = state.stage in setOf(TimerStage.READY, TimerStage.PREPARING, TimerStage.EXERCISING, TimerStage.SIDE_SWITCH, TimerStage.RESTING)

    private fun adjustTime(seconds: Int, now: Long) {
        val effectiveStage = if (state.stage == TimerStage.PAUSED) state.pausedStage else state.stage
        val countdown = effectiveStage in setOf(TimerStage.WARMUP, TimerStage.PREPARING, TimerStage.RESTING) ||
            (effectiveStage == TimerStage.EXERCISING && state.exercise?.mode == ExerciseMode.TIME)
        if (!countdown || seconds == 0) return
        // Reducing time never fabricates an exercise completion. The remaining second must run.
        val timedExercise = effectiveStage == TimerStage.EXERCISING && state.exercise?.mode == ExerciseMode.TIME
        val originalTarget = when (effectiveStage) {
            TimerStage.WARMUP -> (state.session?.plan?.warmupSeconds ?: 0) * 1000L
            TimerStage.PREPARING -> 3000L
            TimerStage.RESTING -> (state.exercise?.restSeconds ?: 0) * 1000L
            else -> state.exercise!!.targetSeconds * 1000L
        }
        val maximumRemaining = minOf(
            if (timedExercise) 7_200_000L else 3_600_000L,
            state.remainingMs + 86_400_000L - originalTarget - state.timeAdjustmentMs,
        )
        // A nearly expired 24-hour phase cannot gain even the minimum remaining second.
        if (maximumRemaining < 1000L) return
        val adjusted = (state.remainingMs + seconds * 1000L).coerceIn(1000L, maximumRemaining)
        if (adjusted == state.remainingMs) return
        state = state.copy(
            remainingMs = adjusted,
            timeAdjustmentMs = state.timeAdjustmentMs + adjusted - state.remainingMs,
            revision = state.revision + 1,
        )
        if (state.stage != TimerStage.PAUSED) deadlineMs = now + adjusted
        // Keep the original elapsed anchor; changing the deadline must not change work performed.
    }

    private fun selectExercise(exerciseId: String, now: Long) {
        val session = state.session ?: return
        val effectiveStage = if (state.stage == TimerStage.PAUSED) state.pausedStage else state.stage
        if (session.plan.kind != PlanKind.WORKOUT || effectiveStage !in setOf(TimerStage.READY, TimerStage.RESTING, TimerStage.SIDE_SWITCH)) return
        val index = session.plan.exercises.indexOfFirst { it.id == exerciseId }
        val exercise = session.plan.exercises.getOrNull(index) ?: return
        val pending = nextPendingSet(exercise, session.results) ?: return
        state = state.copy(exerciseIndex = index, setNumber = pending.setNumber, side = pending.side)
        if (exercise.mode == ExerciseMode.TIME) enter(TimerStage.PREPARING, 3000L, now) else beginExercise(now)
    }

    private fun afterWarmup(now: Long) {
        if (state.session?.plan?.kind == PlanKind.INTERVAL) enter(TimerStage.PREPARING, 3000L, now) else enter(TimerStage.READY, now = now)
    }

    private fun beginExercise(now: Long) {
        val duration = if (state.exercise?.mode == ExerciseMode.TIME) (state.exercise?.targetSeconds ?: 0) * 1000L else 0L
        enter(TimerStage.EXERCISING, duration, now)
    }

    private fun afterSet(now: Long, walltime: Long?) {
        val exercise = state.exercise ?: return
        if (exercise.bilateral && state.side == Side.LEFT) {
            state = state.copy(side = Side.RIGHT)
            enter(TimerStage.SIDE_SWITCH, now = now)
        } else advanceLogicalSet(now, walltime)
    }

    private fun advanceLogicalSet(now: Long, walltime: Long?) {
        val exercise = state.exercise ?: return
        val pending = nextPendingSet(exercise, state.session!!.results)
        if (pending != null) {
            state = state.copy(setNumber = pending.setNumber, side = pending.side)
            if (exercise.restSeconds > 0) enter(TimerStage.RESTING, exercise.restSeconds * 1000L, now)
            else if (state.session?.plan?.kind == PlanKind.INTERVAL) beginExercise(now)
            else enter(TimerStage.READY, now = now)
        } else nextExercise(now, walltime)
    }

    private fun nextExercise(now: Long, walltime: Long?) {
        val session = state.session ?: return
        val exercises = session.plan.exercises
        // A user can start at the last exercise, or leave an earlier exercise between sets.
        // Search circularly for unresolved work rather than treating list order as progress.
        val nextIndex = (1..exercises.size).map { (state.exerciseIndex + it) % exercises.size }
            .firstOrNull { nextPendingSet(exercises[it], session.results) != null }
        if (nextIndex == null) {
            finish(completedPlanStatus(session.results), walltime)
        } else {
            val pending = nextPendingSet(exercises[nextIndex], session.results)!!
            state = state.copy(exerciseIndex = nextIndex, setNumber = pending.setNumber, side = pending.side)
            enter(TimerStage.READY, now = now)
        }
    }

    /** Skip the logical set, including an unstarted right side. No skipped side counts as work. */
    private fun skipSet(now: Long, walltime: Long?) {
        val exercise = state.exercise ?: return
        record(SetOutcome.SKIPPED)
        if (exercise.bilateral && state.side == Side.LEFT) {
            state = state.copy(side = Side.RIGHT)
            record(SetOutcome.SKIPPED)
        }
        advanceLogicalSet(now, walltime)
    }

    private fun skipExercise(now: Long, walltime: Long?) {
        val exercise = state.exercise ?: return
        for (set in state.setNumber..exercise.sets) {
            val sides = if (exercise.bilateral) listOf(Side.LEFT, Side.RIGHT) else listOf(Side.BOTH)
            for (side in sides) {
                state = state.copy(setNumber = set, side = side)
                record(SetOutcome.SKIPPED)
            }
        }
        nextExercise(now, walltime)
    }

    private fun record(outcome: SetOutcome, reps: Int? = null, load: String? = null) {
        val session = state.session ?: return
        val exercise = state.exercise ?: return
        // Length prefixes keep user/imported IDs containing ':' unambiguous across sessions.
        val id = "${session.id.length}:${session.id}:${exercise.id.length}:${exercise.id}:${state.setNumber}:${state.side.name}"
        if (session.results.any { it.id == id || (it.exerciseId == exercise.id && it.setNumber == state.setNumber && it.side == state.side) }) return
        val result = SetResult(
            id = id, sessionId = session.id, exerciseId = exercise.id, exerciseName = exercise.name,
            setNumber = state.setNumber, side = state.side, mode = exercise.mode,
            actualReps = if (outcome == SetOutcome.SKIPPED || exercise.mode == ExerciseMode.TIME) null else reps,
            elapsedMs = if (outcome == SetOutcome.SKIPPED) 0 else state.elapsedMs,
            outcome = outcome, load = load ?: exercise.load, difficulty = exercise.difficulty,
            targetMin = exercise.targetMin, targetMax = exercise.targetMax, targetSeconds = exercise.targetSeconds,
            timeAdjustmentMs = if (exercise.mode == ExerciseMode.TIME && outcome != SetOutcome.SKIPPED) state.timeAdjustmentMs else 0,
        )
        state = state.copy(session = session.copy(results = session.results + result))
    }

    private fun finish(status: SessionStatus, walltime: Long?) {
        state = state.copy(
            session = state.session?.copy(status = status, endedAt = walltime),
            stage = if (status == SessionStatus.INTERRUPTED) TimerStage.INTERRUPTED else TimerStage.COMPLETED,
            remainingMs = 0, pausedStage = null, revision = state.revision + 1,
        )
        deadlineMs = null
        anchorMs = null
    }

    private fun enter(stage: TimerStage, durationMs: Long = 0, now: Long, elapsedMs: Long = 0, timeAdjustmentMs: Long = 0) {
        state = state.copy(stage = stage, remainingMs = durationMs, elapsedMs = elapsedMs, pausedStage = null, timeAdjustmentMs = timeAdjustmentMs, revision = state.revision + 1)
        val counting = stage in setOf(TimerStage.WARMUP, TimerStage.PREPARING, TimerStage.RESTING, TimerStage.EXERCISING)
        anchorMs = if (counting) now else null
        elapsedAtAnchor = elapsedMs
        deadlineMs = if (stage in setOf(TimerStage.WARMUP, TimerStage.PREPARING, TimerStage.RESTING) || (stage == TimerStage.EXERCISING && state.exercise?.mode == ExerciseMode.TIME)) now + durationMs else null
    }

    private fun sample(now: Long) {
        val anchor = anchorMs ?: return
        val elapsed = elapsedAtAnchor + (now - anchor).coerceAtLeast(0)
        state = state.copy(elapsedMs = elapsed, remainingMs = deadlineMs?.let { (it - now).coerceAtLeast(0) } ?: state.remainingMs)
    }
}
