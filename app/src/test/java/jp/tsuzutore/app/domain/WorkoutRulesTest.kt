package jp.tsuzutore.app.domain

import java.time.LocalDate
import org.junit.Assert.*
import org.junit.Test

class WorkoutRulesTest {
    private val monday = LocalDate.parse("2026-10-19")
    private fun session(id: String, date: String, sets: Int = 2, reps: Int = 15, load: String = "自重", confirmed: Boolean = true): WorkoutSession {
        val exercise = Exercise("push", "腕立て", sets = sets, targetMax = 15, load = load)
        val plan = WorkoutPlan("template", "全身", listOf(exercise), warmupSeconds = 0)
        val results = (1..sets).map { set ->
            SetResult("$id:$set", id, "push", "腕立て", set, Side.BOTH, ExerciseMode.REPS,
                actualReps = reps, elapsedMs = 1000, outcome = SetOutcome.COMPLETE, load = load,
                difficulty = exercise.difficulty, targetMin = exercise.targetMin, targetMax = exercise.targetMax, targetSeconds = exercise.targetSeconds)
        }
        return WorkoutSession(id, LocalDate.parse(date).toEpochDay(), date, "Asia/Tokyo", plan, results, SessionStatus.COMPLETE, formConfirmed = confirmed)
    }

    @Test fun initialMenuHasStraightSetsAndExpectedTargets() {
        val menu = initialTemplate()
        assertEquals(listOf(3, 3, 3, 3, 2, 2), menu.exercises.map { it.sets })
        assertEquals(1, menu.exercises.count { it.bilateral })
        assertEquals(30, menu.exercises.last().targetSeconds)
        assertTrue(validateTemplate(menu).isEmpty())
    }

    @Test fun firstWeekUsesSevenCalendarDaysRatherThanWorkoutCount() {
        val settings = AppSettings(firstWorkoutDate = "2026-10-19")
        val menu = initialTemplate()
        assertTrue(isFirstWeek(settings, monday.plusDays(6)))
        assertFalse(isFirstWeek(settings, monday.plusDays(7)))
        assertFalse(isFirstWeek(settings, monday.minusDays(1)))
        assertEquals(LocalDate.parse("2026-10-25"), firstWeekEndDate(settings))
        assertTrue(createPlan(menu, settings, monday.plusDays(6)).exercises.all { it.sets == 2 })
        assertEquals(3, createPlan(menu, settings, monday.plusDays(7)).exercises.first().sets)
        assertFalse(isFirstWeek(settings.copy(firstWeekEnabled = false), monday))
    }

    @Test fun planSnapshotSurvivesTemplateChangesAndShortenedFlag() {
        val mutableExercises = initialTemplate().exercises.toMutableList()
        val template = MenuTemplate("one", "全身", mutableExercises)
        val plan = createPlan(template, AppSettings(firstWeekEnabled = false), monday, shortened = true)
        mutableExercises[0] = mutableExercises[0].copy(name = "変更後", sets = 8)
        assertEquals("腕立て伏せ", plan.exercises.first().name)
        assertEquals(3, plan.exercises.first().sets)
        assertTrue(plan.shortened)
        assertEquals("全身", plan.title)
    }

    @Test fun weeklyDaysDeduplicateLocalDatesAndUseMondayBoundary() {
        val sessions = listOf(
            session("sunday-before", "2026-10-18"),
            session("monday", "2026-10-19"),
            session("monday-second", "2026-10-19"),
            session("sunday", "2026-10-25"),
            session("monday-after", "2026-10-26"),
        )
        assertEquals(setOf(LocalDate.parse("2026-10-19"), LocalDate.parse("2026-10-25")), weeklyPerformedDays(sessions, monday.plusDays(3)))
        assertEquals(setOf(LocalDate.parse("2026-10-26")), weeklyPerformedDays(sessions, monday.plusDays(7)))
    }

    @Test fun partialAndInterruptedActualWorkCountButSkipsAndFreeTimerDoNot() {
        val valid = session("one", "2026-10-19")
        val shortened = valid.copy(id = "short", localDate = "2026-10-21", plan = valid.plan.copy(shortened = true), status = SessionStatus.PARTIAL)
        val interrupted = valid.copy(id = "stop", localDate = "2026-10-23", status = SessionStatus.INTERRUPTED)
        val skipped = valid.copy(id = "skip", localDate = "2026-10-22", results = valid.results.map { it.copy(actualReps = null, elapsedMs = 0, outcome = SetOutcome.SKIPPED) })
        val free = valid.copy(id = "free", localDate = "2026-10-24", plan = valid.plan.copy(kind = PlanKind.INTERVAL))
        assertEquals(setOf(monday, monday.plusDays(2), monday.plusDays(4)), weeklyPerformedDays(listOf(shortened, interrupted, skipped, free, valid), monday))
    }

    @Test fun completedRepsWithoutCountsEarnWorkoutDaysAndValidateAtZeroElapsed() {
        val complete = session("simple", "2026-10-19").let { source ->
            source.copy(results = source.results.map { it.copy(actualReps = null, elapsedMs = 0) })
        }
        assertTrue(complete.wasPerformed())
        assertEquals(SessionStatus.COMPLETE, completedPlanStatus(complete.results))
        assertTrue(validateSession(complete).isEmpty())
        assertEquals(setOf(monday), weeklyPerformedDays(listOf(complete), monday))
        val partiallySkipped = complete.results.mapIndexed { index, result ->
            if (index == 0) result else result.copy(outcome = SetOutcome.SKIPPED)
        }
        assertEquals(SessionStatus.PARTIAL, completedPlanStatus(partiallySkipped))
        val skipped = complete.copy(results = complete.results.map { it.copy(outcome = SetOutcome.SKIPPED) })
        val partialWithoutReps = complete.copy(results = complete.results.map { it.copy(outcome = SetOutcome.PARTIAL) })
        assertFalse(skipped.wasPerformed())
        assertFalse(partialWithoutReps.wasPerformed())
        assertTrue(weeklyPerformedDays(listOf(skipped, partialWithoutReps), monday).isEmpty())
        assertEquals(SessionStatus.INTERRUPTED, completedPlanStatus(partialWithoutReps.results))
    }

    @Test fun explicitNonpositiveCompletedRepsAreRejectedWhileMissingCountsAreAllowed() {
        val complete = session("one", "2026-10-19")
        listOf(0, -1).forEach { reps ->
            assertTrue(validateSession(complete.copy(results = complete.results.map { it.copy(actualReps = reps) })).isNotEmpty())
        }
        val uncounted = complete.copy(results = complete.results.map { it.copy(actualReps = null) })
        assertTrue(validateSession(uncounted).isEmpty())
    }

    @Test fun unknownRepsNeverSuggestProgressionOrFallBackToOlderCountedSessions() {
        val first = session("one", "2026-10-19")
        val second = session("two", "2026-10-21")
        val newest = session("simple", "2026-10-23").let { source ->
            source.copy(results = source.results.map { it.copy(actualReps = null, elapsedMs = 0) })
        }
        assertTrue(progressionSuggested(listOf(first, second), "push"))
        assertFalse(progressionSuggested(listOf(first, second, newest), "push"))
        val alsoUncounted = second.copy(results = second.results.map { it.copy(actualReps = null) })
        assertFalse(progressionSuggested(listOf(alsoUncounted, newest), "push"))
    }

    @Test fun progressionNeedsTwoConsecutiveMatchingFullSessionsAndFormConfirmation() {
        val first = session("one", "2026-10-19")
        val second = session("two", "2026-10-21")
        assertTrue(progressionSuggested(listOf(first, second), "push"))
        assertFalse(progressionSuggested(listOf(first), "push"))
        assertFalse(progressionSuggested(listOf(first, second.copy(formConfirmed = false)), "push"))
        assertFalse(progressionSuggested(listOf(first, second, session("low", "2026-10-23", reps = 14)), "push"))
        assertFalse(progressionSuggested(listOf(first, session("new-load", "2026-10-21", load = "リュック2kg")), "push"))
        assertFalse(progressionSuggested(listOf(first, session("new-sets", "2026-10-21", sets = 3)), "push"))
        assertFalse(progressionSuggested(listOf(first, second.copy(results = second.results.dropLast(1))), "push"))
    }

    @Test fun progressionRejectsPerSetLoadMismatchAndSkippedSides() {
        val first = session("one", "2026-10-19")
        val second = session("two", "2026-10-21")
        assertFalse(progressionSuggested(listOf(first, second.copy(results = second.results.map { it.copy(load = "重い") })), "push"))
        assertFalse(progressionSuggested(listOf(first, second.copy(results = second.results.map { it.copy(outcome = SetOutcome.SKIPPED) })), "push"))
    }

    @Test fun nextScheduleHonorsRestDaysAndEmptySchedule() {
        val settings = AppSettings()
        assertEquals(monday.plusDays(2), nextPlannedDate(settings, monday))
        assertEquals(monday, nextPlannedDate(settings, monday, includeToday = true))
        assertNull(nextPlannedDate(settings.copy(plannedDays = emptySet()), monday))
    }

    @Test fun invalidValuesProduceJapaneseErrorsWithoutThrowing() {
        assertTrue(validateExercise(Exercise("x", "", sets = 0, targetMin = 20, targetMax = 10, restSeconds = -1)).size >= 4)
        assertTrue(validateSettings(AppSettings(plannedDays = setOf(8), reminderHour = 24, firstWorkoutDate = "invalid")).size >= 3)
        assertTrue(validatePlan(WorkoutPlan("p", "x", emptyList(), warmupSeconds = -1)).isNotEmpty())
        val good = session("one", "2026-10-19")
        assertTrue(validateSession(good).isEmpty())
        assertTrue(validateSession(good.copy(results = good.results + good.results.first())).isNotEmpty())
    }

    @Test fun claimedCompletionRequiresEveryExpectedSetAndWallClockCanMoveBackwards() {
        val good = session("one", "2026-10-19")
        assertTrue(validateSession(good.copy(results = good.results.dropLast(1))).isNotEmpty())
        assertTrue(validateSession(good.copy(results = emptyList(), status = SessionStatus.PARTIAL)).isNotEmpty())
        assertTrue(validateSession(good.copy(endedAt = good.startedAt - 1)).isEmpty())
        val timed = Exercise("hold", "保持", ExerciseMode.TIME, sets = 1, targetSeconds = 30)
        val result = SetResult("timed", good.id, timed.id, timed.name, 1, Side.BOTH, ExerciseMode.TIME,
            elapsedMs = 29_999, outcome = SetOutcome.COMPLETE, targetMin = timed.targetMin,
            targetMax = timed.targetMax, targetSeconds = timed.targetSeconds)
        assertTrue(validateSession(good.copy(plan = good.plan.copy(exercises = listOf(timed)), results = listOf(result))).isNotEmpty())
    }
}
