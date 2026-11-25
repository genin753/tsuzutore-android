package jp.tsuzutore.app.domain

import org.junit.Assert.*
import org.junit.Test

class TimerEngineTest {
    private class Clock(var time: Long = 0) : MonotonicClock {
        override fun nowMs() = time
        fun advance(ms: Long) { time += ms }
    }
    private val clock = Clock()
    private val engine = TimerEngine(clock)
    private fun plan(exercise: Exercise = Exercise("push", "腕立て", sets = 2), kind: PlanKind = PlanKind.WORKOUT) =
        WorkoutPlan("template", "テスト", listOf(exercise), warmupSeconds = 0, kind = kind)
    private fun start(plan: WorkoutPlan = plan()) = engine.start(plan, "session", 1000, "2026-10-20", "Asia/Tokyo")
    private fun doReps(reps: Int = 10) {
        engine.dispatch(TimerAction.StartSet)
        clock.advance(1000)
        engine.dispatch(TimerAction.CompleteReps(reps))
    }

    @Test fun repsDoNotCompleteWhenTimePassesAndLegacyRepsArePreserved() {
        start()
        engine.dispatch(TimerAction.StartSet)
        clock.advance(60_000)
        engine.tick()
        assertEquals(TimerStage.EXERCISING, engine.state.stage)
        assertTrue(engine.state.session!!.results.isEmpty())
        engine.dispatch(TimerAction.CompleteReps(7, "水1L"))
        val result = engine.state.session!!.results.single()
        assertEquals(7, result.actualReps)
        assertEquals("水1L", result.load)
        assertEquals(60_000L, result.elapsedMs)
    }

    @Test fun oneTapCompletesRepsWithoutInventingActualReps() {
        start(plan(Exercise("push", "腕立て", sets = 1)))
        engine.dispatch(TimerAction.CompleteSet, engine.state.revision)
        assertEquals(TimerStage.READY, engine.state.stage)
        assertTrue(engine.state.session!!.results.isEmpty())
        engine.dispatch(TimerAction.StartSet)
        engine.dispatch(TimerAction.CompleteSet, engine.state.revision, walltime = 2000)
        val session = engine.state.session!!
        val result = session.results.single()
        assertEquals(TimerStage.COMPLETED, engine.state.stage)
        assertEquals(SessionStatus.COMPLETE, session.status)
        assertEquals(SetOutcome.COMPLETE, result.outcome)
        assertNull(result.actualReps)
        assertEquals(0L, result.elapsedMs)
        assertTrue(session.wasPerformed())
        assertTrue(validateSession(session).isEmpty())
    }

    @Test fun oneTapCompletionPreservesBilateralAcknowledgementAndRest() {
        start(plan(Exercise("split", "左右", sets = 2, bilateral = true)))
        engine.dispatch(TimerAction.StartSet)
        val leftRevision = engine.state.revision
        engine.dispatch(TimerAction.CompleteSet, leftRevision)
        assertEquals(TimerStage.SIDE_SWITCH, engine.state.stage)
        assertEquals(Side.RIGHT, engine.state.side)
        assertEquals(1, engine.state.session!!.results.size)
        engine.dispatch(TimerAction.CompleteSet, leftRevision)
        engine.dispatch(TimerAction.StartSet)
        engine.dispatch(TimerAction.CompleteSet, leftRevision)
        assertEquals(TimerStage.EXERCISING, engine.state.stage)
        assertEquals(1, engine.state.session!!.results.size)
        engine.dispatch(TimerAction.CompleteSet, engine.state.revision)
        assertEquals(TimerStage.RESTING, engine.state.stage)
        assertEquals(2, engine.state.setNumber)
        assertEquals(Side.LEFT, engine.state.side)
        assertEquals(listOf(Side.LEFT, Side.RIGHT), engine.state.session!!.results.map { it.side })
        assertTrue(engine.state.session!!.results.all { it.actualReps == null && it.outcome == SetOutcome.COMPLETE })
    }

    @Test fun repeatedOrStaleOneTapCannotCompleteFollowingSet() {
        start()
        engine.dispatch(TimerAction.StartSet)
        val revision = engine.state.revision
        engine.dispatch(TimerAction.CompleteSet, revision)
        engine.dispatch(TimerAction.CompleteSet, revision)
        assertEquals(1, engine.state.session!!.results.size)
        clock.advance(120_000)
        engine.tick()
        engine.dispatch(TimerAction.StartSet)
        engine.dispatch(TimerAction.CompleteSet, revision)
        assertEquals(TimerStage.EXERCISING, engine.state.stage)
        assertEquals(1, engine.state.session!!.results.size)
        engine.dispatch(TimerAction.CompleteSet, engine.state.revision)
        assertEquals(TimerStage.COMPLETED, engine.state.stage)
        assertEquals(2, engine.state.session!!.results.map { it.id }.distinct().size)
    }

    @Test fun oneTapDoesNotCompleteTimedExerciseEarly() {
        start(plan(Exercise("hold", "保持", ExerciseMode.TIME, sets = 1, targetSeconds = 30)))
        engine.dispatch(TimerAction.StartSet)
        clock.advance(8000)
        engine.tick()
        engine.dispatch(TimerAction.CompleteSet, engine.state.revision)
        assertEquals(TimerStage.EXERCISING, engine.state.stage)
        assertEquals(25_000L, engine.state.remainingMs)
        assertTrue(engine.state.session!!.results.isEmpty())
    }

    @Test fun monotonicDeadlineSurvivesDelayedDisplayUpdates() {
        start(plan(Exercise("hold", "プランク", ExerciseMode.TIME, sets = 1, targetSeconds = 30)))
        engine.dispatch(TimerAction.StartSet)
        clock.advance(7_750)
        engine.tick()
        assertEquals(TimerStage.EXERCISING, engine.state.stage)
        assertEquals(25_250L, engine.state.remainingMs)
        clock.advance(25_250)
        engine.tick(walltime = 12_345)
        assertEquals(TimerStage.COMPLETED, engine.state.stage)
        assertEquals(30_000L, engine.state.session!!.results.single().elapsedMs)
        assertEquals(12_345L, engine.state.session!!.endedAt)
    }

    @Test fun pauseResumePreservesRemainingAndDoesNotCountPausedTime() {
        start(plan(Exercise("hold", "プランク", ExerciseMode.TIME, sets = 1, targetSeconds = 30)))
        engine.dispatch(TimerAction.StartSet)
        clock.advance(13_000)
        engine.dispatch(TimerAction.Pause)
        assertEquals(20_000L, engine.state.remainingMs)
        assertEquals(10_000L, engine.state.elapsedMs)
        clock.advance(500_000)
        engine.tick()
        assertEquals(20_000L, engine.state.remainingMs)
        engine.dispatch(TimerAction.Resume)
        clock.advance(20_000)
        engine.tick()
        assertEquals(TimerStage.COMPLETED, engine.state.stage)
        assertEquals(30_000L, engine.state.session!!.results.single().elapsedMs)
    }

    @Test fun extendingRestChangesDeadlineAndNormalRestWaitsForStart() {
        start()
        doReps()
        clock.advance(20_000)
        engine.dispatch(TimerAction.ExtendRest())
        assertEquals(130_000L, engine.state.remainingMs)
        clock.advance(130_000)
        engine.tick()
        assertEquals(TimerStage.READY, engine.state.stage)
        clock.advance(90_000)
        engine.tick()
        assertEquals(1, engine.state.session!!.results.size)
        assertEquals(TimerStage.READY, engine.state.stage)
    }

    @Test fun bilateralSidesWaitForPreparationAndRestOnlyAfterBoth() {
        start(plan(Exercise("split", "スプリットスクワット", sets = 2, bilateral = true)))
        assertEquals(Side.LEFT, engine.state.side)
        doReps(8)
        assertEquals(TimerStage.SIDE_SWITCH, engine.state.stage)
        assertEquals(Side.RIGHT, engine.state.side)
        clock.advance(60_000)
        engine.tick()
        assertEquals(TimerStage.SIDE_SWITCH, engine.state.stage)
        doReps(9)
        assertEquals(TimerStage.RESTING, engine.state.stage)
        assertEquals(2, engine.state.setNumber)
        assertEquals(Side.LEFT, engine.state.side)
        assertEquals(listOf(Side.LEFT, Side.RIGHT), engine.state.session!!.results.map { it.side })
    }

    @Test fun finalSetHasNoRestAndMovesDirectlyToNextExercise() {
        val exercises = listOf(Exercise("a", "A", sets = 1), Exercise("b", "B", sets = 1))
        start(WorkoutPlan("template", "テスト", exercises, warmupSeconds = 0))
        doReps()
        assertEquals(TimerStage.READY, engine.state.stage)
        assertEquals("b", engine.state.exercise!!.id)
        doReps()
        assertEquals(TimerStage.COMPLETED, engine.state.stage)
        assertEquals(SessionStatus.COMPLETE, engine.state.session!!.status)
        assertEquals(0L, engine.state.remainingMs)
    }

    @Test fun delayedDuplicateTapCannotCompleteFollowingSet() {
        start()
        engine.dispatch(TimerAction.StartSet)
        val revision = engine.state.revision
        engine.dispatch(TimerAction.CompleteReps(12), revision)
        engine.dispatch(TimerAction.CompleteReps(12), revision)
        assertEquals(1, engine.state.session!!.results.size)
        clock.advance(120_000)
        engine.tick()
        engine.dispatch(TimerAction.StartSet)
        engine.dispatch(TimerAction.CompleteReps(12), revision)
        assertEquals(TimerStage.EXERCISING, engine.state.stage)
        assertEquals(1, engine.state.session!!.results.size)
        engine.dispatch(TimerAction.CompleteReps(11))
        val ids = engine.state.session!!.results.map { it.id }
        assertEquals(2, ids.distinct().size)
    }

    @Test fun staleTapAtAutomaticTransitionCannotSkipNewPhase() {
        start(plan(Exercise("hold", "保持", ExerciseMode.TIME, sets = 2, targetSeconds = 5)))
        engine.dispatch(TimerAction.StartSet)
        val preparingRevision = engine.state.revision
        clock.advance(8000)
        engine.dispatch(TimerAction.SkipSet, preparingRevision)
        assertEquals(TimerStage.RESTING, engine.state.stage)
        assertEquals(1, engine.state.session!!.results.size)
        assertEquals(SetOutcome.COMPLETE, engine.state.session!!.results.single().outcome)
    }

    @Test fun skipBilateralSetRecordsBothSidesWithoutPerformedWork() {
        start(plan(Exercise("split", "左右", sets = 1, bilateral = true)))
        engine.dispatch(TimerAction.SkipSet)
        val session = engine.state.session!!
        assertEquals(2, session.results.size)
        assertTrue(session.results.all { it.outcome == SetOutcome.SKIPPED && it.elapsedMs == 0L && it.actualReps == null })
        assertFalse(session.wasPerformed())
        assertEquals(SessionStatus.INTERRUPTED, session.status)
    }

    @Test fun skipRemainingExercisePreservesAlreadyRecordedSide() {
        start(plan(Exercise("split", "左右", sets = 2, bilateral = true)))
        doReps(8)
        engine.dispatch(TimerAction.SkipExercise)
        val results = engine.state.session!!.results
        assertEquals(4, results.size)
        assertEquals(1, results.count { it.wasPerformed() })
        assertEquals(SessionStatus.PARTIAL, engine.state.session!!.status)
    }

    @Test fun intervalAutomaticallyRunsAllRoundsWithoutFinalRest() {
        start(plan(Exercise("interval", "自由タイマー", ExerciseMode.TIME, sets = 3, targetSeconds = 2, restSeconds = 1), PlanKind.INTERVAL))
        assertEquals(TimerStage.PREPARING, engine.state.stage)
        clock.advance(11_000)
        engine.tick()
        assertEquals(TimerStage.COMPLETED, engine.state.stage)
        assertEquals(3, engine.state.session!!.results.size)
        assertTrue(engine.state.session!!.results.all { it.elapsedMs == 2000L && it.outcome == SetOutcome.COMPLETE })
    }

    @Test fun interruptedTimedSetSavesPartialWhileRepsAreNeverInferred() {
        start(plan(Exercise("hold", "保持", ExerciseMode.TIME, sets = 1, targetSeconds = 30)))
        engine.dispatch(TimerAction.StartSet)
        clock.advance(8500)
        engine.dispatch(TimerAction.Stop(20_000))
        assertEquals(SessionStatus.INTERRUPTED, engine.state.session!!.status)
        assertEquals(5500L, engine.state.session!!.results.single().elapsedMs)
        assertEquals(SetOutcome.PARTIAL, engine.state.session!!.results.single().outcome)
        start(plan())
        engine.dispatch(TimerAction.StartSet)
        clock.advance(20_000)
        engine.dispatch(TimerAction.Stop(30_000))
        assertTrue(engine.state.session!!.results.isEmpty())
        assertFalse(engine.state.session!!.wasPerformed())
    }

    @Test fun processRestoreFreezesSavedRemainingUntilExplicitResume() {
        start(plan(Exercise("hold", "保持", ExerciseMode.TIME, sets = 1, targetSeconds = 30)))
        engine.dispatch(TimerAction.StartSet)
        clock.advance(8000)
        engine.tick()
        val saved = engine.state
        val otherClock = Clock(900_000_000)
        val restored = TimerEngine(otherClock)
        restored.restore(saved)
        assertEquals(TimerStage.PAUSED, restored.state.stage)
        assertEquals(TimerStage.EXERCISING, restored.state.pausedStage)
        otherClock.advance(500_000)
        restored.tick()
        assertEquals(25_000L, restored.state.remainingMs)
        assertTrue(restored.state.session!!.results.isEmpty())
        restored.dispatch(TimerAction.Resume)
        otherClock.advance(25_000)
        restored.tick()
        assertEquals(30_000L, restored.state.session!!.results.single().elapsedMs)
    }

    @Test fun warmupCanBeSkippedAndRepsZeroDoesNotEarnAWorkoutDay() {
        start(plan(Exercise("a", "A", sets = 1)).copy(warmupSeconds = 180))
        assertEquals(TimerStage.WARMUP, engine.state.stage)
        engine.dispatch(TimerAction.SkipWarmup)
        assertEquals(TimerStage.READY, engine.state.stage)
        doReps(0)
        assertFalse(engine.state.session!!.wasPerformed())
        assertEquals(SessionStatus.INTERRUPTED, engine.state.session!!.status)
    }

    @Test fun updatingFormConfirmationPreservesRunningDeadlineAndIsIdempotent() {
        start(plan(Exercise("hold", "保持", ExerciseMode.TIME, sets = 1, targetSeconds = 30)))
        engine.dispatch(TimerAction.StartSet)
        clock.advance(8000)
        engine.tick()
        val before = engine.state
        engine.updateFormConfirmation("other-session", true)
        assertEquals(before, engine.state)
        engine.updateFormConfirmation("session", true)
        assertEquals(TimerStage.EXERCISING, engine.state.stage)
        assertEquals(before.remainingMs, engine.state.remainingMs)
        assertTrue(engine.state.session!!.formConfirmed)
        val updated = engine.state
        engine.updateFormConfirmation("session", true)
        assertEquals(updated, engine.state)
        clock.advance(25_000)
        engine.tick()
        assertEquals(TimerStage.COMPLETED, engine.state.stage)
        assertEquals(30_000L, engine.state.session!!.results.single().elapsedMs)
    }

    @Test fun currentGlobalPauseAndStopAreNotLostWhenDispatchCrossesDeadline() {
        start(plan(Exercise("hold", "保持", ExerciseMode.TIME, sets = 2, targetSeconds = 30)))
        engine.dispatch(TimerAction.StartSet)
        val preparingRevision = engine.state.revision
        clock.advance(8000)
        engine.dispatch(TimerAction.Pause, preparingRevision)
        assertEquals(TimerStage.PAUSED, engine.state.stage)
        assertEquals(TimerStage.EXERCISING, engine.state.pausedStage)
        engine.dispatch(TimerAction.Resume)
        clock.advance(25_000)
        val exercisingRevision = engine.state.revision
        engine.dispatch(TimerAction.Stop(40_000), exercisingRevision)
        assertEquals(SessionStatus.INTERRUPTED, engine.state.session!!.status)
        assertEquals(1, engine.state.session!!.results.size)
        assertEquals(SetOutcome.COMPLETE, engine.state.session!!.results.single().outcome)
    }

    @Test fun liveTimeAdjustmentKeepsElapsedMonotonicAndChangesTheRealDeadline() {
        start(plan(Exercise("hold", "保持", ExerciseMode.TIME, sets = 1, targetSeconds = 30)))
        engine.dispatch(TimerAction.StartSet)
        clock.advance(8000)
        engine.tick() // Capture the exercising revision after the preparation boundary.
        engine.dispatch(TimerAction.AdjustTime(10), engine.state.revision)
        assertEquals(35_000L, engine.state.remainingMs)
        assertEquals(5000L, engine.state.elapsedMs)
        assertEquals(10_000L, engine.state.timeAdjustmentMs)
        clock.advance(5000)
        engine.dispatch(TimerAction.AdjustTime(-10), engine.state.revision)
        assertEquals(20_000L, engine.state.remainingMs)
        assertEquals(10_000L, engine.state.elapsedMs)
        assertEquals(0L, engine.state.timeAdjustmentMs)
        clock.advance(20_000)
        engine.tick()
        assertEquals(TimerStage.COMPLETED, engine.state.stage)
        assertEquals(30_000L, engine.state.session!!.results.single().elapsedMs)
    }

    @Test fun shorteningTimedSetWaitsAtLeastOneSecondAndSavesTheAdjustedGoal() {
        start(plan(Exercise("hold", "保持", ExerciseMode.TIME, sets = 1, targetSeconds = 30)))
        engine.dispatch(TimerAction.StartSet)
        clock.advance(13_000)
        engine.dispatch(TimerAction.AdjustTime(Int.MIN_VALUE))
        assertEquals(1000L, engine.state.remainingMs)
        assertEquals(-19_000L, engine.state.timeAdjustmentMs)
        assertTrue(engine.state.session!!.results.isEmpty())
        clock.advance(999)
        engine.tick()
        assertEquals(TimerStage.EXERCISING, engine.state.stage)
        clock.advance(1)
        engine.tick()
        val result = engine.state.session!!.results.single()
        assertEquals(SetOutcome.COMPLETE, result.outcome)
        assertEquals(11_000L, result.elapsedMs)
        assertEquals(30, result.targetSeconds)
        assertEquals(-19_000L, result.timeAdjustmentMs)
        assertTrue(validateSession(engine.state.session!!).isEmpty())
    }

    @Test fun timeAdjustmentCapsHugeInputsAndIgnoresRepeatedStaleCommands() {
        start(plan(Exercise("hold", "保持", ExerciseMode.TIME, sets = 1, targetSeconds = 30)))
        engine.dispatch(TimerAction.StartSet)
        clock.advance(3000)
        engine.tick()
        val revision = engine.state.revision
        engine.dispatch(TimerAction.AdjustTime(Int.MAX_VALUE), revision)
        assertEquals(7_200_000L, engine.state.remainingMs)
        assertEquals(7_170_000L, engine.state.timeAdjustmentMs)
        val adjusted = engine.state
        engine.dispatch(TimerAction.AdjustTime(-10), revision)
        assertEquals(adjusted, engine.state)
        engine.dispatch(TimerAction.AdjustTime(10), engine.state.revision)
        assertEquals(adjusted, engine.state)
    }

    @Test fun countdownAdjustmentSupportsWarmupPreparationAndRestIncludingPausedForms() {
        for (phase in listOf(TimerStage.WARMUP, TimerStage.PREPARING, TimerStage.RESTING)) {
            for (paused in listOf(false, true)) {
                val phaseClock = Clock()
                val phaseEngine = TimerEngine(phaseClock)
                val exercise = if (phase == TimerStage.PREPARING) Exercise("hold", "保持", ExerciseMode.TIME) else Exercise("push", "腕立て", sets = 2)
                phaseEngine.start(plan(exercise).copy(warmupSeconds = if (phase == TimerStage.WARMUP) 20 else 0), "phase", 1000, "2026-10-20", "Asia/Tokyo")
                if (phase != TimerStage.WARMUP) phaseEngine.dispatch(TimerAction.StartSet)
                if (phase == TimerStage.RESTING) phaseEngine.dispatch(TimerAction.CompleteSet)
                phaseClock.advance(1000)
                phaseEngine.tick()
                if (paused) phaseEngine.dispatch(TimerAction.Pause)
                val before = phaseEngine.state
                phaseEngine.dispatch(TimerAction.AdjustTime(10))
                assertEquals(before.remainingMs + 10_000L, phaseEngine.state.remainingMs)
                assertEquals(before.elapsedMs, phaseEngine.state.elapsedMs)
                assertEquals(10_000L, phaseEngine.state.timeAdjustmentMs)
                assertEquals(if (paused) TimerStage.PAUSED else phase, phaseEngine.state.stage)
                phaseEngine.dispatch(TimerAction.AdjustTime(Int.MAX_VALUE))
                assertEquals(3_600_000L, phaseEngine.state.remainingMs)
            }
        }
    }

    @Test fun pausedAdjustedTimedSetSurvivesRestoreAndResumesWithoutPausedTime() {
        start(plan(Exercise("hold", "保持", ExerciseMode.TIME, sets = 1, targetSeconds = 30)))
        engine.dispatch(TimerAction.StartSet)
        clock.advance(8000)
        engine.dispatch(TimerAction.Pause)
        engine.dispatch(TimerAction.AdjustTime(-10))
        val resumedClock = Clock(900_000)
        val resumedEngine = TimerEngine(resumedClock)
        resumedEngine.restore(engine.state)
        resumedClock.advance(100_000)
        resumedEngine.tick()
        assertEquals(15_000L, resumedEngine.state.remainingMs)
        assertEquals(5000L, resumedEngine.state.elapsedMs)
        assertEquals(-10_000L, resumedEngine.state.timeAdjustmentMs)
        resumedEngine.dispatch(TimerAction.Resume)
        resumedClock.advance(15_000)
        resumedEngine.tick()
        val result = resumedEngine.state.session!!.results.single()
        assertEquals(20_000L, result.elapsedMs)
        assertEquals(-10_000L, result.timeAdjustmentMs)
        assertTrue(validateSession(resumedEngine.state.session!!).isEmpty())
    }

    @Test fun timeAdjustmentIsIgnoredForReadyRepsAndPausedReps() {
        start()
        val ready = engine.state
        engine.dispatch(TimerAction.AdjustTime(10))
        assertEquals(ready, engine.state)
        engine.dispatch(TimerAction.StartSet)
        clock.advance(5000)
        engine.tick()
        val reps = engine.state
        engine.dispatch(TimerAction.AdjustTime(-10))
        assertEquals(reps, engine.state)
        engine.dispatch(TimerAction.Pause)
        val paused = engine.state
        engine.dispatch(TimerAction.AdjustTime(10))
        assertEquals(paused, engine.state)
    }

    @Test fun staleAdjustmentAtAutomaticBoundaryCannotChangeTheFollowingRest() {
        start(plan(Exercise("hold", "保持", ExerciseMode.TIME, sets = 2, targetSeconds = 2, restSeconds = 20)))
        engine.dispatch(TimerAction.StartSet)
        clock.advance(3000)
        engine.tick()
        val exerciseRevision = engine.state.revision
        clock.advance(2000)
        engine.dispatch(TimerAction.AdjustTime(10), exerciseRevision)
        assertEquals(TimerStage.RESTING, engine.state.stage)
        assertEquals(20_000L, engine.state.remainingMs)
        assertEquals(0L, engine.state.timeAdjustmentMs)
        assertEquals(0L, engine.state.session!!.results.single().timeAdjustmentMs)
    }

    @Test fun adjustmentMetadataResetsOnNewPhasesAndSkippedSetsHaveNoAdjustment() {
        start(plan(Exercise("hold", "保持", ExerciseMode.TIME, sets = 2, targetSeconds = 10, restSeconds = 2)))
        engine.dispatch(TimerAction.StartSet)
        engine.dispatch(TimerAction.AdjustTime(10)) // Preparation is independent of the set goal.
        clock.advance(13_000)
        engine.tick()
        assertEquals(0L, engine.state.timeAdjustmentMs)
        engine.dispatch(TimerAction.AdjustTime(10))
        engine.dispatch(TimerAction.SkipSet)
        assertEquals(TimerStage.RESTING, engine.state.stage)
        assertEquals(0L, engine.state.timeAdjustmentMs)
        assertEquals(0L, engine.state.session!!.results.single().timeAdjustmentMs)
        engine.dispatch(TimerAction.AdjustTime(10))
        clock.advance(12_000)
        engine.tick()
        engine.dispatch(TimerAction.StartSet)
        clock.advance(3000)
        engine.tick()
        assertEquals(0L, engine.state.timeAdjustmentMs)
        assertEquals(10_000L, engine.state.remainingMs)
    }

    @Test fun selectingLastExerciseFirstKeepsEarlierExercisesPendingUntilAllAreDone() {
        val exercises = listOf(Exercise("a", "A", sets = 1), Exercise("b", "B", sets = 1), Exercise("c", "C", sets = 1))
        start(WorkoutPlan("template", "自由順", exercises, warmupSeconds = 0))
        engine.dispatch(TimerAction.SelectExercise("c"))
        assertEquals(TimerStage.EXERCISING, engine.state.stage)
        assertEquals("c", engine.state.exercise!!.id)
        engine.dispatch(TimerAction.CompleteSet)
        assertEquals(TimerStage.READY, engine.state.stage)
        assertEquals("a", engine.state.exercise!!.id)
        engine.dispatch(TimerAction.SelectExercise("b"))
        engine.dispatch(TimerAction.CompleteSet)
        assertEquals("a", engine.state.exercise!!.id)
        assertEquals(TimerStage.READY, engine.state.stage)
        engine.dispatch(TimerAction.StartSet)
        engine.dispatch(TimerAction.CompleteSet)
        assertEquals(TimerStage.COMPLETED, engine.state.stage)
        assertEquals(listOf("c", "b", "a"), engine.state.session!!.results.map { it.exerciseId })
        assertTrue(validateSession(engine.state.session!!).isEmpty())
    }

    @Test fun switchingDuringRestResumesTheNextUnfinishedSetWithoutLosingProgress() {
        val exercises = listOf(Exercise("a", "A", sets = 2), Exercise("b", "B", sets = 1))
        start(WorkoutPlan("template", "自由順", exercises, warmupSeconds = 0))
        engine.dispatch(TimerAction.StartSet)
        engine.dispatch(TimerAction.CompleteSet)
        assertEquals(TimerStage.RESTING, engine.state.stage)
        engine.dispatch(TimerAction.SelectExercise("b"))
        assertEquals(1, engine.state.setNumber)
        engine.dispatch(TimerAction.CompleteSet)
        assertEquals("a", engine.state.exercise!!.id)
        assertEquals(2, engine.state.setNumber)
        engine.dispatch(TimerAction.StartSet)
        engine.dispatch(TimerAction.CompleteSet)
        val results = engine.state.session!!.results
        assertEquals(3, results.size)
        assertEquals(listOf(1, 2), results.filter { it.exerciseId == "a" }.map { it.setNumber })
        assertEquals(SessionStatus.COMPLETE, engine.state.session!!.status)
    }

    @Test fun bilateralSelectionPreservesPendingRightSideAndResumesItExactlyOnce() {
        val exercises = listOf(Exercise("split", "左右", sets = 2, bilateral = true), Exercise("b", "B", sets = 1))
        start(WorkoutPlan("template", "自由順", exercises, warmupSeconds = 0))
        engine.dispatch(TimerAction.StartSet)
        engine.dispatch(TimerAction.CompleteSet)
        assertEquals(TimerStage.SIDE_SWITCH, engine.state.stage)
        engine.dispatch(TimerAction.SelectExercise("b"))
        engine.dispatch(TimerAction.CompleteSet)
        assertEquals("split", engine.state.exercise!!.id)
        assertEquals(Side.RIGHT, engine.state.side)
        assertEquals(1, engine.state.setNumber)
        engine.dispatch(TimerAction.SelectExercise("split"))
        engine.dispatch(TimerAction.CompleteSet)
        assertEquals(TimerStage.RESTING, engine.state.stage)
        assertEquals(2, engine.state.setNumber)
        engine.dispatch(TimerAction.SelectExercise("split"))
        engine.dispatch(TimerAction.CompleteSet)
        engine.dispatch(TimerAction.StartSet)
        engine.dispatch(TimerAction.CompleteSet)
        val results = engine.state.session!!.results
        assertEquals(5, results.size)
        assertEquals(5, results.map { Triple(it.exerciseId, it.setNumber, it.side) }.distinct().size)
        assertEquals(SessionStatus.COMPLETE, engine.state.session!!.status)
    }

    @Test fun pausedReadyRestAndSideSwitchAllowExplicitSelectionAndStartIt() {
        for (phase in listOf(TimerStage.READY, TimerStage.RESTING, TimerStage.SIDE_SWITCH)) {
            val phaseEngine = TimerEngine(Clock())
            val first = Exercise("a", "A", sets = 2, bilateral = phase == TimerStage.SIDE_SWITCH)
            phaseEngine.start(WorkoutPlan("template", "自由順", listOf(first, Exercise("b", "B", sets = 1)), warmupSeconds = 0), "phase", 1000, "2026-10-20", "Asia/Tokyo")
            if (phase != TimerStage.READY) {
                phaseEngine.dispatch(TimerAction.StartSet)
                phaseEngine.dispatch(TimerAction.CompleteSet)
            }
            assertEquals(phase, phaseEngine.state.stage)
            phaseEngine.dispatch(TimerAction.Pause)
            phaseEngine.dispatch(TimerAction.SelectExercise("b"))
            assertEquals(TimerStage.EXERCISING, phaseEngine.state.stage)
            assertEquals("b", phaseEngine.state.exercise!!.id)
            assertNull(phaseEngine.state.pausedStage)
            assertEquals(if (phase == TimerStage.READY) 0 else 1, phaseEngine.state.session!!.results.size)
        }
    }

    @Test fun timedExerciseSelectionAlwaysWaitsForThreeSecondPreparation() {
        val exercises = listOf(Exercise("a", "A", sets = 1), Exercise("hold", "保持", ExerciseMode.TIME, sets = 1, targetSeconds = 5))
        start(WorkoutPlan("template", "自由順", exercises, warmupSeconds = 0))
        engine.dispatch(TimerAction.SelectExercise("hold"))
        assertEquals(TimerStage.PREPARING, engine.state.stage)
        assertEquals(3000L, engine.state.remainingMs)
        assertTrue(engine.state.session!!.results.isEmpty())
        clock.advance(8000)
        engine.tick()
        assertEquals("a", engine.state.exercise!!.id)
        assertEquals(5000L, engine.state.session!!.results.single().elapsedMs)
        assertEquals(TimerStage.READY, engine.state.stage)
    }

    @Test fun selectionCannotAbandonActiveExerciseWarmupOrPreparationEvenWhenPaused() {
        for (phase in listOf(TimerStage.WARMUP, TimerStage.PREPARING, TimerStage.EXERCISING)) {
            val phaseEngine = TimerEngine(Clock())
            val first = if (phase == TimerStage.PREPARING) Exercise("a", "A", ExerciseMode.TIME) else Exercise("a", "A")
            phaseEngine.start(WorkoutPlan("template", "自由順", listOf(first, Exercise("b", "B")), warmupSeconds = if (phase == TimerStage.WARMUP) 20 else 0), "phase", 1000, "2026-10-20", "Asia/Tokyo")
            if (phase != TimerStage.WARMUP) phaseEngine.dispatch(TimerAction.StartSet)
            val before = phaseEngine.state
            phaseEngine.dispatch(TimerAction.SelectExercise("b"))
            assertEquals(before, phaseEngine.state)
            phaseEngine.dispatch(TimerAction.Pause)
            val paused = phaseEngine.state
            phaseEngine.dispatch(TimerAction.SelectExercise("b"))
            assertEquals(paused, phaseEngine.state)
        }
    }

    @Test fun selectingUnknownOrResolvedExerciseDoesNotRestartItOrFabricateResults() {
        val exercises = listOf(Exercise("a", "A", sets = 1), Exercise("b", "B", sets = 1))
        start(WorkoutPlan("template", "自由順", exercises, warmupSeconds = 0))
        engine.dispatch(TimerAction.SelectExercise("missing"))
        assertTrue(engine.state.session!!.results.isEmpty())
        engine.dispatch(TimerAction.StartSet)
        engine.dispatch(TimerAction.CompleteSet)
        val before = engine.state
        engine.dispatch(TimerAction.SelectExercise("a"))
        assertEquals(before, engine.state)
        assertEquals(1, engine.state.session!!.results.size)
    }

    @Test fun skippedExercisesRemainResolvedAfterSelectingASeparateExercise() {
        val exercises = listOf(Exercise("a", "A", sets = 1), Exercise("b", "B", sets = 2, bilateral = true), Exercise("c", "C", sets = 1))
        start(WorkoutPlan("template", "自由順", exercises, warmupSeconds = 0))
        engine.dispatch(TimerAction.SelectExercise("b"))
        engine.dispatch(TimerAction.SkipExercise)
        assertEquals("c", engine.state.exercise!!.id)
        engine.dispatch(TimerAction.SelectExercise("a"))
        engine.dispatch(TimerAction.CompleteSet)
        assertEquals("c", engine.state.exercise!!.id)
        engine.dispatch(TimerAction.StartSet)
        engine.dispatch(TimerAction.CompleteSet)
        assertEquals(TimerStage.COMPLETED, engine.state.stage)
        assertEquals(SessionStatus.PARTIAL, engine.state.session!!.status)
        assertEquals(4, engine.state.session!!.results.count { it.outcome == SetOutcome.SKIPPED })
        assertTrue(validateSession(engine.state.session!!).isEmpty())
    }

    @Test fun restoredSelectionUsesSavedBilateralProgressInsteadOfTheOldListPosition() {
        val exercises = listOf(Exercise("a", "A", sets = 2, bilateral = true), Exercise("b", "B", sets = 1))
        start(WorkoutPlan("template", "自由順", exercises, warmupSeconds = 0))
        engine.dispatch(TimerAction.StartSet)
        engine.dispatch(TimerAction.CompleteSet)
        engine.dispatch(TimerAction.SelectExercise("b"))
        engine.dispatch(TimerAction.CompleteSet)
        val restored = TimerEngine(Clock(900_000))
        restored.restore(engine.state)
        restored.dispatch(TimerAction.SelectExercise("a"))
        assertEquals(TimerStage.EXERCISING, restored.state.stage)
        assertEquals(Side.RIGHT, restored.state.side)
        assertEquals(1, restored.state.setNumber)
        assertEquals(2, restored.state.session!!.results.size)
        assertEquals(0, completedExerciseSets(exercises[0], restored.state.session!!.results))
        assertEquals(1, completedExerciseSets(exercises[1], restored.state.session!!.results))
    }

    @Test fun nearlyExpiredMaximumGoalCannotBeAdjustedBeyondTheDurableLimit() {
        start(plan(Exercise("hold", "保持", ExerciseMode.TIME, sets = 1, targetSeconds = 30)))
        engine.dispatch(TimerAction.StartSet)
        clock.advance(3000)
        engine.tick()
        // This is a valid checkpoint after repeated extensions of the same running set.
        engine.restore(engine.state.copy(
            elapsedMs = 86_399_500L,
            remainingMs = 500L,
            timeAdjustmentMs = 86_370_000L,
        ))
        engine.dispatch(TimerAction.Resume)
        val almostFinished = engine.state
        engine.dispatch(TimerAction.AdjustTime(10))
        assertEquals(almostFinished, engine.state)
        engine.dispatch(TimerAction.AdjustTime(-10))
        assertEquals(almostFinished, engine.state)
        clock.advance(500)
        engine.tick()
        assertEquals(TimerStage.COMPLETED, engine.state.stage)
        assertEquals(86_400_000L, engine.state.session!!.results.single().elapsedMs)
        assertTrue(validateSession(engine.state.session!!).isEmpty())
    }
}
