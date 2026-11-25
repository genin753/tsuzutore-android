package jp.tsuzutore.app.domain

import jp.tsuzutore.app.data.BackupCodec
import jp.tsuzutore.app.data.BackupDocument
import org.junit.Assert.*
import org.junit.Test

class HistoryEditTest {
    private class Clock(var time: Long = 0) : MonotonicClock {
        override fun nowMs() = time
        fun advance(ms: Long) { time += ms }
    }

    private val reps = Exercise("push", "腕立て", sets = 1, targetMin = 8)
    private val timed = Exercise("plank", "プランク", ExerciseMode.TIME, sets = 1, targetSeconds = 30)

    private fun result(exercise: Exercise, outcome: SetOutcome = SetOutcome.COMPLETE, actualReps: Int? = null, elapsed: Long = 0) = SetResult(
        id = "s:${exercise.id}:1", sessionId = "s", exerciseId = exercise.id, exerciseName = exercise.name, setNumber = 1,
        side = Side.BOTH, mode = exercise.mode, actualReps = actualReps, elapsedMs = elapsed, outcome = outcome,
        targetMin = exercise.targetMin, targetMax = exercise.targetMax, targetSeconds = exercise.targetSeconds,
    )

    private fun session(vararg results: SetResult, id: String = "s", startedAt: Long = 1000) = WorkoutSession(
        id, startedAt, "2026-10-20", "Asia/Tokyo", WorkoutPlan("t", "テスト", listOf(reps, timed), warmupSeconds = 0),
        results.toList(), SessionStatus.PARTIAL, endedAt = startedAt + 1,
    )

    @Test fun completedRepSetCanGainChangeAndLoseItsCount() {
        val original = session(result(reps))
        val counted = original.withResultReps("s:push:1", 12)
        assertEquals(12, counted.results.single().actualReps)
        assertEquals(15, counted.withResultReps("s:push:1", 15).results.single().actualReps)
        val cleared = counted.withResultReps("s:push:1", null)
        assertNull(cleared.results.single().actualReps)
        assertTrue(cleared.results.single().wasPerformed())
    }

    @Test fun onlyCompletedRepSetsAcceptCorrectionsWithinTheLimit() {
        assertThrows(IllegalArgumentException::class.java) { session(result(timed, elapsed = 30_000)).withResultReps("s:plank:1", 10) }
        assertThrows(IllegalArgumentException::class.java) { session(result(reps, SetOutcome.SKIPPED)).withResultReps("s:push:1", 10) }
        assertThrows(IllegalArgumentException::class.java) { session(result(reps)).withResultReps("s:push:1", 0) }
        assertThrows(IllegalArgumentException::class.java) { session(result(reps)).withResultReps("s:push:1", Limits.TARGET + 1) }
        assertThrows(IllegalArgumentException::class.java) { session(result(reps)).withResultReps("missing", 10) }
    }

    @Test fun suggestionPrefersThisWorkoutThenLatestHistoryThenTargetMinimum() {
        assertEquals(8, suggestedReps(reps, null, emptyList()))
        val older = session(result(reps, actualReps = 10), id = "old", startedAt = 1000)
        val newer = session(result(reps, actualReps = 11), id = "new", startedAt = 2000)
        assertEquals(11, suggestedReps(reps, null, listOf(older, newer)))
        val current = session(result(reps, actualReps = 14), id = "current", startedAt = 3000)
        assertEquals(14, suggestedReps(reps, current, listOf(older, newer, current)))
        val currentWithoutCounts = session(result(reps), id = "plain", startedAt = 4000)
        assertEquals(11, suggestedReps(reps, currentWithoutCounts, listOf(older, newer, currentWithoutCounts)))
    }

    @Test fun finishedRunAcceptsCorrectedCountsButActiveRunCannotBeRewritten() {
        val engine = TimerEngine(Clock())
        engine.start(WorkoutPlan("t", "テスト", listOf(reps.copy(sets = 2)), warmupSeconds = 0), "run", 1000, "2026-10-20", "Asia/Tokyo")
        engine.dispatch(TimerAction.StartSet)
        engine.dispatch(TimerAction.CompleteSet)
        assertThrows(IllegalStateException::class.java) { engine.replaceFinishedSession(engine.state.session!!) }
        val finished = engine.dispatch(TimerAction.Stop(3000))
        val session = finished.session!!
        val updated = engine.replaceFinishedSession(session.withResultReps(session.results.single().id, 12))
        assertEquals(12, updated.session!!.results.single().actualReps)
        assertTrue(updated.revision > finished.revision)
        assertEquals(TimerStage.INTERRUPTED, updated.stage)
        assertTrue(validateSession(updated.session!!).isEmpty())
    }

    @Test fun forgottenRepSetStopsCountingAtOneDayAndStaysExportable() {
        val clock = Clock()
        val engine = TimerEngine(clock)
        val exercise = reps
        engine.start(WorkoutPlan("t", "テスト", listOf(exercise), warmupSeconds = 0), "forgotten", 1000, "2026-10-20", "Asia/Tokyo")
        engine.dispatch(TimerAction.StartSet)
        clock.advance(30L * 60 * 60 * 1000)
        assertEquals(Limits.MAX_SET_ELAPSED_MS, engine.tick().elapsedMs)
        val finished = engine.dispatch(TimerAction.CompleteSet, walltime = 2000)
        val session = finished.session!!
        assertEquals(Limits.MAX_SET_ELAPSED_MS, session.results.single().elapsedMs)
        assertTrue(validateSession(session).isEmpty())
        val document = BackupDocument(BackupCodec.SCHEMA_VERSION, AppSettings(), listOf(MenuTemplate("t", "テスト", listOf(exercise))),
            listOf(session), finished)
        assertEquals(document, BackupCodec.decode(BackupCodec.encode(document)))
    }
}
