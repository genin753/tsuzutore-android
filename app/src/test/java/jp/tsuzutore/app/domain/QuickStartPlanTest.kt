package jp.tsuzutore.app.domain

import java.time.LocalDate
import org.junit.Assert.*
import org.junit.Test

class QuickStartPlanTest {
    private val today = LocalDate.parse("2026-10-21")

    @Test fun chosenExerciseStartsFirstWithoutDroppingOtherExercisesOrChangingTemplate() {
        val template = initialTemplate()
        val originalOrder = template.exercises.map { it.id }
        val plan = createQuickStartPlan(template, AppSettings(firstWeekEnabled = false), today, "plank")
        assertEquals("plank", plan.exercises.first().id)
        assertEquals(originalOrder.toSet(), plan.exercises.map { it.id }.toSet())
        assertEquals(originalOrder, template.exercises.map { it.id })
        assertEquals(0, plan.warmupSeconds)
        assertEquals(template.exercises.sumOf { it.sets }, plan.exercises.sumOf { it.sets })
    }

    @Test fun quickStartPreservesFirstWeekRules() {
        val plan = createQuickStartPlan(initialTemplate(), AppSettings(), today, "backpack-row")
        assertTrue(plan.firstWeek)
        assertTrue(plan.exercises.all { it.sets == 2 })
        assertEquals("backpack-row", plan.exercises.first().id)
    }

    @Test(expected = IllegalArgumentException::class)
    fun missingExerciseCannotStartAnUnrelatedExercise() {
        createQuickStartPlan(initialTemplate(), AppSettings(), today, "missing")
    }

    @Test fun nextDateUsesExplicitScheduleAndExcludesPastDays() {
        val settings = AppSettings(plannedDays = emptySet(), scheduledWorkouts = listOf(
            ScheduledWorkout("old", "2026-10-20", "full", "全身"),
            ScheduledWorkout("today", "2026-10-21", "full", "全身"),
            ScheduledWorkout("future", "2026-11-03", "full", "全身"),
        ))
        assertEquals(today, nextPlannedDate(settings, today, includeToday = true))
        assertEquals(LocalDate.parse("2026-11-03"), nextPlannedDate(settings, today))
        assertEquals(LocalDate.parse("2026-10-22"), nextPlannedDate(settings.copy(plannedDays = setOf(4)), today))
    }
}