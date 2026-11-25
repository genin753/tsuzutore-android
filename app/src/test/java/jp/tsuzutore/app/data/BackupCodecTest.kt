package jp.tsuzutore.app.data

import jp.tsuzutore.app.domain.*
import kotlinx.serialization.encodeToString
import org.junit.Assert.*
import org.junit.Test

class BackupCodecTest {
    private val exercise = Exercise("pushup", "腕立て伏せ", sets = 1, targetMin = 6, targetMax = 15)
    private val template = MenuTemplate("home", "自宅メニュー", listOf(exercise))
    private val plan = WorkoutPlan(template.id, template.name, template.exercises, warmupSeconds = 0)
    private val result = SetResult("session:pushup:1:BOTH", "session", exercise.id, exercise.name, 1, Side.BOTH,
        ExerciseMode.REPS, actualReps = 12, elapsedMs = 20_000, outcome = SetOutcome.COMPLETE,
        targetMin = 6, targetMax = 15, targetSeconds = 30)
    private val session = WorkoutSession("session", 1_792_454_400_000L, "2026-10-20", "Asia/Tokyo", plan,
        listOf(result), SessionStatus.COMPLETE, endedAt = 1_792_454_500_000L, formConfirmed = true)
    private fun document() = BackupDocument(1, AppSettings(), listOf(template), listOf(session))

    @Test fun backupRoundTripPreservesSettingsSnapshotsAndResults() {
        val source = document().copy(settings = AppSettings(soundEnabled = false, cueMemo = "朝食のあと"))
        assertEquals(source, BackupCodec.decode(BackupCodec.encode(source)))
        val preview = BackupCodec.preview(source)
        assertEquals(1, preview.templateCount)
        assertEquals(1, preview.sessionCount)
        assertEquals(1, preview.performedSets)
        assertFalse(preview.hasActiveRun)
    }

    @Test fun simpleCompletionRoundTripPreservesUnknownRepsAndCountsAsPerformed() {
        val completed = result.copy(actualReps = null, elapsedMs = 0)
        val source = document().copy(sessions = listOf(session.copy(results = listOf(completed), formConfirmed = false)))
        val restored = BackupCodec.decode(BackupCodec.encode(source))
        assertEquals(source, restored)
        assertNull(restored.sessions.single().results.single().actualReps)
        assertTrue(restored.sessions.single().wasPerformed())
        assertEquals(1, BackupCodec.preview(restored).performedSets)
        assertEquals(setOf(java.time.LocalDate.parse(session.localDate)),
            weeklyPerformedDays(restored.sessions, java.time.LocalDate.parse(session.localDate)))
    }

    @Test fun completedRepSetRejectsExplicitZeroWithoutReplacingUnknownWithZero() {
        rejectWithResult(result.copy(actualReps = 0))
        rejectWithResult(result.copy(actualReps = 0, elapsedMs = 0))
    }

    @Test fun templateEditingAndDeletionCannotRewriteHistorySnapshot() {
        val edited = template.copy(name = "編集後", exercises = listOf(exercise.copy(name = "膝つき腕立て", sets = 5)))
        val changed = BackupCodec.decode(BackupCodec.encode(document().copy(templates = listOf(edited))))
        assertEquals("腕立て伏せ", changed.sessions.single().plan.exercises.single().name)
        assertEquals(1, changed.sessions.single().plan.exercises.single().sets)
        assertEquals(12, changed.sessions.single().results.single().actualReps)
        val deleted = BackupCodec.decode(BackupCodec.encode(document().copy(templates = emptyList())))
        assertEquals(session, deleted.sessions.single())
    }

    @Test fun changingWallClockDuringWorkoutDoesNotInvalidateMonotonicResults() {
        val changedClock = session.copy(endedAt = session.startedAt - 3_600_000)
        val source = document().copy(sessions = listOf(changedClock))
        val restored = BackupCodec.decode(BackupCodec.encode(source)).sessions.single()
        assertEquals(changedClock.endedAt, restored.endedAt)
        assertEquals(20_000L, restored.results.single().elapsedMs)
    }

    @Test fun importedActiveWorkoutRestoresPausedWithoutAdvancingOrCreatingResults() {
        val active = session.copy(plan = plan.copy(exercises = listOf(exercise.copy(mode = ExerciseMode.TIME))), results = emptyList(), status = SessionStatus.ACTIVE, endedAt = null)
        val run = RunState(active, TimerStage.EXERCISING, remainingMs = 12_000, elapsedMs = 18_000, revision = 4)
        val source = document().copy(sessions = listOf(active), runtime = run)
        val restored = BackupCodec.forRestore(BackupCodec.decode(BackupCodec.encode(source)))
        assertEquals(TimerStage.PAUSED, restored.runtime!!.stage)
        assertEquals(TimerStage.EXERCISING, restored.runtime!!.pausedStage)
        assertEquals(12_000L, restored.runtime!!.remainingMs)
        assertTrue(restored.sessions.single().results.isEmpty())
        assertEquals(restored.sessions.single(), restored.runtime!!.session)
    }

    @Test fun rejectsUnsupportedVersionMissingRequiredFieldsUnknownFieldsAndOversize() {
        reject(document().copy(schemaVersion = 2))
        rejectText("{}")
        rejectText(BackupCodec.encode(document()).dropLast(1) + ",\"unexpected\":true}")
        rejectText(" ".repeat(BackupCodec.MAX_BYTES + 1))
    }

    @Test fun rejectsInvalidSettingsExerciseAndActualValues() {
        reject(document().copy(settings = AppSettings(weeklyGoal = 0)))
        reject(document().copy(settings = AppSettings(plannedDays = setOf(8))))
        reject(document().copy(settings = AppSettings(reminderHour = 24)))
        reject(document().copy(templates = listOf(template.copy(exercises = listOf(exercise.copy(sets = 0))))))
        reject(document().copy(templates = listOf(template.copy(exercises = listOf(exercise.copy(targetMin = 16, targetMax = 15))))))
        rejectWithResult(result.copy(actualReps = -1))
        rejectWithResult(result.copy(elapsedMs = -1))
        rejectWithResult(result.copy(outcome = SetOutcome.SKIPPED))
    }

    @Test fun rejectsDuplicateIdsAndBrokenReferences() {
        reject(document().copy(templates = listOf(template, template)))
        reject(document().copy(sessions = listOf(session, session)))
        reject(document().copy(sessions = listOf(session.copy(results = listOf(result, result)))))
        rejectWithResult(result.copy(sessionId = "missing"))
        rejectWithResult(result.copy(exerciseId = "missing"))
        rejectWithResult(result.copy(setNumber = 2))
        rejectWithResult(result.copy(side = Side.LEFT))
        rejectWithResult(result.copy(targetMax = 999))
    }

    @Test fun rejectsFalseCompletionAndEmptyPartialAchievements() {
        reject(document().copy(sessions = listOf(session.copy(results = emptyList()))))
        reject(document().copy(sessions = listOf(session.copy(results = emptyList(), status = SessionStatus.PARTIAL))))
        val timed = exercise.copy(mode = ExerciseMode.TIME, targetSeconds = 30)
        val timedResult = result.copy(mode = ExerciseMode.TIME, actualReps = null, elapsedMs = 1000)
        reject(document().copy(sessions = listOf(session.copy(plan = plan.copy(exercises = listOf(timed)), results = listOf(timedResult)))))
    }

    @Test fun rejectsInvalidRuntimeInsteadOfMutatingOriginal() {
        val original = document()
        val active = session.copy(status = SessionStatus.ACTIVE, endedAt = null)
        reject(original.copy(sessions = listOf(active)))
        reject(original.copy(runtime = RunState(session = active, stage = TimerStage.READY)))
        reject(original.copy(sessions = listOf(active), runtime = RunState(active, TimerStage.READY, exerciseIndex = 10)))
        reject(original.copy(sessions = listOf(active), runtime = RunState(active, TimerStage.PAUSED, pausedStage = TimerStage.PAUSED)))
        assertEquals(original, BackupCodec.decode(BackupCodec.encode(original)))
    }

    @Test fun rejectsAlreadyRecordedCurrentSetAndInconsistentCountdown() {
        val active = session.copy(status = SessionStatus.ACTIVE, endedAt = null)
        reject(document().copy(sessions = listOf(active), runtime = RunState(active, TimerStage.READY)))
        val timed = active.copy(plan = plan.copy(exercises = listOf(exercise.copy(mode = ExerciseMode.TIME))), results = emptyList())
        reject(document().copy(sessions = listOf(timed), runtime = RunState(timed, TimerStage.EXERCISING, remainingMs = 30_000, elapsedMs = 10_000)))
        reject(document().copy(sessions = listOf(timed), runtime = RunState(timed, TimerStage.PREPARING, remainingMs = 3000, elapsedMs = 3000)))
    }

    @Test fun bothSidesCountAsOneLogicalSetInPreview() {
        val ex = exercise.copy(bilateral = true)
        val bilateralPlan = plan.copy(exercises = listOf(ex))
        val left = result.copy(id = "left", side = Side.LEFT)
        val right = result.copy(id = "right", side = Side.RIGHT)
        val bilateralSession = session.copy(plan = bilateralPlan, results = listOf(left, right))
        val source = document().copy(sessions = listOf(bilateralSession))
        BackupCodec.validate(source)
        assertEquals(1, BackupCodec.preview(source).performedSets)
    }

    @Test fun bilateralSimpleCompletionCountsAsOneLogicalSetWithoutInventingRepCounts() {
        val bilateralPlan = plan.copy(exercises = listOf(exercise.copy(bilateral = true)))
        val completed = result.copy(actualReps = null, elapsedMs = 0)
        val source = document().copy(sessions = listOf(session.copy(plan = bilateralPlan,
            results = listOf(completed.copy(id = "left", side = Side.LEFT), completed.copy(id = "right", side = Side.RIGHT)))))
        val restored = BackupCodec.decode(BackupCodec.encode(source))
        assertEquals(1, BackupCodec.preview(restored).performedSets)
        assertTrue(restored.sessions.single().results.all { it.actualReps == null })
    }

    @Test fun realTimerTransitionsAreExportableAndReimportable() {
        var now = 0L
        val engine = TimerEngine { now }
        fun checkRun() {
            val state = engine.state
            val source = document().copy(sessions = listOf(state.session!!), runtime = state)
            assertEquals(source, BackupCodec.decode(BackupCodec.encode(source)))
        }
        engine.start(plan, "real", 1000, "2026-10-20", "Asia/Tokyo")
        checkRun()
        engine.dispatch(TimerAction.StartSet)
        now = 20_000
        engine.tick()
        checkRun()
        engine.dispatch(TimerAction.Pause)
        checkRun()
        engine.dispatch(TimerAction.Resume)
        engine.dispatch(TimerAction.CompleteReps(10), walltime = 25_000)
        checkRun()
        assertEquals(SessionStatus.COMPLETE, engine.state.session!!.status)
    }

    @Test fun arbitraryExerciseOrderAndInterleavedSetsRestoreAtTheSelectedCursor() {
        val first = exercise.copy(sets = 2)
        val second = exercise.copy(id = "row", name = "リュックロー", sets = 2)
        val engine = TimerEngine { 0L }
        engine.start(plan.copy(exercises = listOf(first, second)), "flex", 1000, "2026-10-20", "Asia/Tokyo")
        engine.dispatch(TimerAction.SelectExercise(second.id))
        engine.dispatch(TimerAction.CompleteSet)
        roundTripRun(engine.state)
        engine.dispatch(TimerAction.SelectExercise(first.id))
        engine.dispatch(TimerAction.CompleteSet)
        engine.dispatch(TimerAction.SelectExercise(second.id))
        engine.dispatch(TimerAction.CompleteSet)
        val restored = BackupCodec.forRestore(roundTripRun(engine.state))
        assertEquals(listOf(second.id, first.id, second.id), restored.sessions.single().results.map { it.exerciseId })
        assertEquals(0, restored.runtime!!.exerciseIndex)
        assertEquals(2, restored.runtime!!.setNumber)
        assertEquals(TimerStage.PAUSED, restored.runtime!!.stage)
        assertEquals(TimerStage.READY, restored.runtime!!.pausedStage)
    }

    @Test fun partiallyCompletedBilateralExerciseCanBeChosenAfterAnotherExercise() {
        val split = exercise.copy(id = "split", name = "スプリットスクワット", bilateral = true)
        val engine = TimerEngine { 0L }
        engine.start(plan.copy(exercises = listOf(split, exercise)), "sides", 1000, "2026-10-20", "Asia/Tokyo")
        engine.dispatch(TimerAction.SelectExercise(split.id))
        engine.dispatch(TimerAction.CompleteSet)
        assertEquals(TimerStage.SIDE_SWITCH, engine.state.stage)
        engine.dispatch(TimerAction.SelectExercise(exercise.id))
        engine.dispatch(TimerAction.CompleteSet)
        engine.dispatch(TimerAction.SelectExercise(split.id))
        val restored = BackupCodec.forRestore(roundTripRun(engine.state))
        assertEquals(Side.RIGHT, restored.runtime!!.side)
        assertEquals(1, restored.runtime!!.setNumber)
        assertEquals(TimerStage.EXERCISING, restored.runtime!!.pausedStage)
        assertEquals(listOf(split.id, exercise.id), restored.sessions.single().results.map { it.exerciseId })
    }

    @Test fun rejectsCursorThatJumpsOverAnUnresolvedSetInTheSameExercise() {
        val threeSets = exercise.copy(sets = 3)
        val active = session.copy(plan = plan.copy(exercises = listOf(threeSets)), status = SessionStatus.ACTIVE, endedAt = null)
        reject(runDocument(RunState(active, TimerStage.READY, setNumber = 3)))
        val valid = runDocument(RunState(active, TimerStage.READY, setNumber = 2))
        assertEquals(valid, BackupCodec.decode(BackupCodec.encode(valid)))
    }

    @Test fun rejectsSetHolesAndRightBeforeLeftWithoutRequiringGlobalExerciseOrder() {
        val split = exercise.copy(id = "split", name = "スプリットスクワット", sets = 2, bilateral = true)
        val active = session.copy(plan = plan.copy(exercises = listOf(exercise, split)), results = emptyList(), status = SessionStatus.ACTIVE, endedAt = null)
        fun invalid(vararg results: SetResult) = runDocument(RunState(active.copy(results = results.toList()), TimerStage.READY))
        reject(invalid(savedResult(split, side = Side.RIGHT)))
        reject(invalid(savedResult(split, setNumber = 2, side = Side.LEFT)))
        reject(invalid(savedResult(split, side = Side.RIGHT), savedResult(split, side = Side.LEFT)))
        val valid = invalid(savedResult(split, side = Side.LEFT))
        assertEquals(valid, BackupCodec.decode(BackupCodec.encode(valid)))
    }

    @Test fun adjustedWarmupPreparationAndExerciseRestoreWithTheirExactRemainingTime() {
        var now = 0L
        val timed = exercise.copy(mode = ExerciseMode.TIME)
        val engine = TimerEngine { now }
        engine.start(plan.copy(exercises = listOf(timed), warmupSeconds = 30), "adjusted", 1000, "2026-10-20", "Asia/Tokyo")
        engine.dispatch(TimerAction.AdjustTime(10))
        now = 5000
        engine.tick()
        val warmup = BackupCodec.forRestore(roundTripRun(engine.state)).runtime!!
        assertEquals(35_000L, warmup.remainingMs)
        assertEquals(10_000L, warmup.timeAdjustmentMs)
        engine.dispatch(TimerAction.SkipWarmup)
        engine.dispatch(TimerAction.SelectExercise(timed.id))
        engine.dispatch(TimerAction.AdjustTime(10))
        now = 8000
        engine.tick()
        val preparing = BackupCodec.forRestore(roundTripRun(engine.state)).runtime!!
        assertEquals(TimerStage.PREPARING, preparing.pausedStage)
        assertEquals(10_000L, preparing.remainingMs)
        now = 18_000
        engine.tick()
        engine.dispatch(TimerAction.AdjustTime(-10))
        now = 23_000
        engine.tick()
        val exercising = BackupCodec.forRestore(roundTripRun(engine.state)).runtime!!
        assertEquals(TimerStage.EXERCISING, exercising.pausedStage)
        assertEquals(15_000L, exercising.remainingMs)
        assertEquals(5000L, exercising.elapsedMs)
        assertEquals(-10_000L, exercising.timeAdjustmentMs)
    }

    @Test fun shortenedTimedCompletionKeepsOriginalTargetAndActualDuration() {
        val completed = completedAdjustedTimedRun(-10)
        val restored = roundTripRun(completed)
        val actual = restored.sessions.single().results.single()
        assertEquals(SessionStatus.COMPLETE, restored.sessions.single().status)
        assertEquals(30, actual.targetSeconds)
        assertEquals(20_000L, actual.elapsedMs)
        assertEquals(-10_000L, actual.timeAdjustmentMs)
    }

    @Test fun extendedTimedCompletionKeepsOriginalTargetAndActualDuration() {
        val restored = roundTripRun(completedAdjustedTimedRun(10))
        val actual = restored.sessions.single().results.single()
        assertEquals(SessionStatus.COMPLETE, restored.sessions.single().status)
        assertEquals(30, actual.targetSeconds)
        assertEquals(40_000L, actual.elapsedMs)
        assertEquals(10_000L, actual.timeAdjustmentMs)
    }

    @Test fun rejectsInconsistentAdjustedCountdownAndOutOfRangeAdjustment() {
        val active = session.copy(plan = plan.copy(exercises = listOf(exercise.copy(mode = ExerciseMode.TIME))), results = emptyList(), status = SessionStatus.ACTIVE, endedAt = null)
        val valid = RunState(active, TimerStage.EXERCISING, remainingMs = 40_000, timeAdjustmentMs = 10_000)
        roundTripRun(valid)
        reject(runDocument(valid.copy(timeAdjustmentMs = 9999)))
        reject(runDocument(valid.copy(timeAdjustmentMs = 86_400_001)))
        reject(runDocument(valid.copy(timeAdjustmentMs = -86_400_001)))
        reject(runDocument(valid.copy(remainingMs = 1000, timeAdjustmentMs = -30_000)))
        reject(runDocument(valid.copy(remainingMs = 86_400_000, elapsedMs = 1, timeAdjustmentMs = 86_370_001)))
    }

    @Test fun rejectsTimeAdjustmentOnReadySwitchAndRepStopwatch() {
        val active = session.copy(results = emptyList(), status = SessionStatus.ACTIVE, endedAt = null)
        reject(runDocument(RunState(active, TimerStage.READY, timeAdjustmentMs = 10_000)))
        reject(runDocument(RunState(active, TimerStage.EXERCISING, timeAdjustmentMs = 10_000)))
        val split = exercise.copy(bilateral = true)
        val switching = active.copy(plan = plan.copy(exercises = listOf(split)), results = listOf(savedResult(split, side = Side.LEFT)))
        reject(runDocument(RunState(switching, TimerStage.SIDE_SWITCH, side = Side.RIGHT, timeAdjustmentMs = 1000)))
    }

    @Test fun legacyBackupWithoutScheduleOrAdjustmentFieldsUsesCompatibleDefaults() {
        val legacy = BackupCodec.encode(document())
            .replace(Regex(",\\s*\"timeAdjustmentMs\":\\s*0"), "")
            .replace(Regex(",\\s*\"scheduledWorkouts\":\\s*\\[\\]"), "")
        assertFalse(legacy.contains("timeAdjustmentMs"))
        assertFalse(legacy.contains("scheduledWorkouts"))
        assertEquals(document(), BackupCodec.decode(legacy))
    }

    @Test fun legacyExtendedRestWithoutAdjustmentStillRestoresPaused() {
        val active = session.copy(plan = plan.copy(exercises = listOf(exercise.copy(sets = 2))), status = SessionStatus.ACTIVE, endedAt = null)
        val run = RunState(active, TimerStage.RESTING, setNumber = 2, remainingMs = 150_000)
        val restored = BackupCodec.forRestore(roundTripRun(run)).runtime!!
        assertEquals(TimerStage.RESTING, restored.pausedStage)
        assertEquals(150_000L, restored.remainingMs)
        assertEquals(0L, restored.timeAdjustmentMs)
    }

    @Test fun scheduleRoundTripPreservesTimedAllDayAndDeletedMenuPlans() {
        val events = listOf(
            ScheduledWorkout("morning", "2026-10-22", template.id, "全身トレーニング", timeMinutes = 0),
            ScheduledWorkout("night", "2026-10-22", template.id, "夜トレ", timeMinutes = 1439),
            ScheduledWorkout("all-day", "2026-10-24", "deleted-menu", "以前のメニュー"),
        )
        val source = document().copy(settings = AppSettings(scheduledWorkouts = events))
        val restored = BackupCodec.decode(BackupCodec.encode(source))
        assertEquals(source, restored)
        assertEquals(events, restored.settings.scheduledWorkouts)
        assertNull(restored.settings.scheduledWorkouts.last().timeMinutes)
    }

    @Test fun rejectsInvalidDatesTimesNamesAndDuplicateScheduledWorkouts() {
        val event = ScheduledWorkout("planned", "2026-10-22", template.id, template.name)
        fun invalid(vararg events: ScheduledWorkout) = document().copy(settings = AppSettings(scheduledWorkouts = events.toList()))
        reject(invalid(event.copy(date = "2026-02-30")))
        reject(invalid(event.copy(date = "2026-2-03")))
        reject(invalid(event.copy(timeMinutes = -1)))
        reject(invalid(event.copy(timeMinutes = 1440)))
        reject(invalid(event.copy(title = "")))
        reject(invalid(event.copy(templateId = "")))
        reject(invalid(event.copy(id = "")))
        reject(invalid(event, event))
        reject(document().copy(settings = AppSettings(scheduledWorkouts = List(5001) { event.copy(id = "planned-$it") })))
    }

    private fun completedAdjustedTimedRun(adjustmentSeconds: Int): RunState {
        var now = 0L
        val engine = TimerEngine { now }
        val timed = exercise.copy(mode = ExerciseMode.TIME)
        engine.start(plan.copy(exercises = listOf(timed)), "timed", 1000, "2026-10-20", "Asia/Tokyo")
        engine.dispatch(TimerAction.SelectExercise(timed.id))
        now = 3000
        engine.tick()
        engine.dispatch(TimerAction.AdjustTime(adjustmentSeconds))
        now += (30 + adjustmentSeconds) * 1000L
        engine.tick(walltime = 100_000)
        return engine.state
    }

    private fun savedResult(exercise: Exercise, setNumber: Int = 1, side: Side = Side.BOTH): SetResult = SetResult(
        "session:${exercise.id}:$setNumber:$side", "session", exercise.id, exercise.name, setNumber, side,
        exercise.mode, actualReps = null, elapsedMs = 0, outcome = SetOutcome.COMPLETE,
        targetMin = exercise.targetMin, targetMax = exercise.targetMax, targetSeconds = exercise.targetSeconds,
    )

    private fun runDocument(run: RunState) = document().copy(sessions = listOf(requireNotNull(run.session)), runtime = run)

    private fun roundTripRun(run: RunState): BackupDocument {
        val source = runDocument(run)
        val restored = BackupCodec.decode(BackupCodec.encode(source))
        assertEquals(source, restored)
        return restored
    }
    private fun rejectWithResult(invalid: SetResult) = reject(document().copy(sessions = listOf(session.copy(results = listOf(invalid)))))
    private fun reject(invalid: BackupDocument) = rejectText(BackupCodec.json.encodeToString(invalid))
    private fun rejectText(text: String) {
        try {
            BackupCodec.decode(text)
            fail("不正なバックアップが受け入れられました")
        } catch (expected: IllegalArgumentException) {
            assertTrue(expected.message?.isNotBlank() == true)
        }
    }
}
