package jp.tsuzutore.app.ui

import jp.tsuzutore.app.domain.*
import org.junit.Assert.*
import org.junit.Test

class UiLogicTest {
    @Test fun savingSettingsKeepsCalendarPlansAndEverythingTheScreenDoesNotEdit() {
        val plan = ScheduledWorkout("p1", "2026-12-01", "menu", "全身", 19 * 60)
        val stored = AppSettings(scheduledWorkouts = listOf(plan), firstWorkoutDate = "2026-10-20")
        val form = SettingsForm.of(AppSettings()).copy(reminderEnabled = true, weeklyGoal = "4", cuesInSilentMode = true)
        assertNull(form.error())
        val saved = form.applyTo(stored)
        assertEquals(listOf(plan), saved.scheduledWorkouts)
        assertTrue(saved.reminderEnabled)
        assertEquals(4, saved.weeklyGoal)
        assertTrue(saved.cuesInSilentMode)
        assertTrue(validateSettings(saved).isEmpty())
    }

    @Test fun settingsFormRoundTripsAndRejectsInvalidValues() {
        val settings = AppSettings(weeklyGoal = 5, warmupSeconds = 120, reminderHour = 7, reminderMinute = 30, cueMemo = "朝", firstWorkoutDate = "2026-10-20")
        assertEquals(settings, SettingsForm.of(settings).applyTo(settings))
        assertNotNull(SettingsForm.of(settings).copy(weeklyGoal = "8").error())
        assertNotNull(SettingsForm.of(settings).copy(warmupSeconds = "").error())
        assertNotNull(SettingsForm.of(settings).copy(warmupSeconds = "${Limits.WARMUP_SECONDS + 1}").error())
        assertNotNull(SettingsForm.of(settings).copy(firstWorkoutDate = "2026-02-30").error())
        assertNotNull(SettingsForm.of(settings).copy(cueMemo = "あ".repeat(Limits.CUE_MEMO + 1)).error())
    }

    @Test fun timerSamplesWithinTheSameDisplayedSecondAreNotScreenChanges() {
        val base = RunState(stage = TimerStage.RESTING, remainingMs = 8_500, elapsedMs = 1_500, revision = 3)
        assertTrue(base.displaysSameAs(base.copy(remainingMs = 8_200, elapsedMs = 1_800)))
        assertFalse(base.displaysSameAs(base.copy(remainingMs = 7_800, elapsedMs = 2_200)))
        assertFalse(base.displaysSameAs(base.copy(revision = 4)))
        assertFalse(base.displaysSameAs(base.copy(stage = TimerStage.PAUSED, pausedStage = TimerStage.RESTING)))
        assertEquals("00:09", clockText(8_200))
    }
}
