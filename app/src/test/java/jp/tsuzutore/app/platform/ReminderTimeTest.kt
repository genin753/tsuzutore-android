package jp.tsuzutore.app.platform

import jp.tsuzutore.app.domain.AppSettings
import jp.tsuzutore.app.domain.ScheduledWorkout
import java.time.ZonedDateTime
import org.junit.Assert.*
import org.junit.Test

class ReminderTimeTest {
    @Test fun mondayBeforeTimeSchedulesToday() {
        val next = nextReminderTime(AppSettings(), ZonedDateTime.parse("2026-10-19T18:59:00+09:00[Asia/Tokyo]"))
        assertEquals("2026-10-19T19:00+09:00[Asia/Tokyo]", next.toString())
    }
    @Test fun exactTimeDoesNotRescheduleSameAlarm() {
        val next = nextReminderTime(AppSettings(), ZonedDateTime.parse("2026-10-19T19:00:00+09:00[Asia/Tokyo]"))
        assertEquals("2026-10-21T19:00+09:00[Asia/Tokyo]", next.toString())
    }
    @Test fun sundayCrossesWeekBoundary() {
        val next = nextReminderTime(AppSettings(), ZonedDateTime.parse("2026-10-25T20:00:00+09:00[Asia/Tokyo]"))
        assertEquals(26, next!!.dayOfMonth)
    }
    @Test fun daylightSavingGapUsesLocalZoneRules() {
        val settings = AppSettings(plannedDays = setOf(7), reminderHour = 2, reminderMinute = 30)
        val next = nextReminderTime(settings, ZonedDateTime.parse("2026-03-08T00:00:00-05:00[America/New_York]"))
        assertEquals(3, next!!.hour)
        assertEquals("America/New_York", next.zone.id)
    }
    @Test fun noScheduledDaysMeansNoReminder() {
        assertNull(nextReminderTime(AppSettings(plannedDays = emptySet()), ZonedDateTime.parse("2026-10-19T18:00:00+09:00[Asia/Tokyo]")))
    }

    private fun dated(id: String, date: String, time: Int? = null) = ScheduledWorkout(id, date, "menu", "全身", time)

    @Test fun datedPlanOutsideWeeklyDaysSchedulesWithoutRecurrence() {
        val settings = AppSettings(plannedDays = emptySet(), scheduledWorkouts = listOf(dated("tuesday", "2026-10-20", 8 * 60)))
        val next = nextReminderTime(settings, ZonedDateTime.parse("2026-10-19T20:00:00+09:00[Asia/Tokyo]"))
        assertEquals("2026-10-20T08:00+09:00[Asia/Tokyo]", next.toString())
    }

    @Test fun earliestTimeWinsAcrossDatedAndWeeklyCandidates() {
        val now = ZonedDateTime.parse("2026-10-19T12:00:00+09:00[Asia/Tokyo]")
        val settings = AppSettings(scheduledWorkouts = listOf(dated("late", "2026-10-19", 21 * 60), dated("early", "2026-10-19", 18 * 60)))
        assertEquals(18, nextReminderTime(settings, now)!!.hour)
        assertEquals(19, nextReminderTime(settings.copy(scheduledWorkouts = listOf(dated("late", "2026-10-19", 21 * 60))), now)!!.hour)
    }

    @Test fun pastDatedPlansAreIgnoredAndMissingTimeUsesReminderSetting() {
        val settings = AppSettings(
            plannedDays = emptySet(), reminderHour = 7, reminderMinute = 45,
            scheduledWorkouts = listOf(dated("old", "2026-10-18", 23 * 60), dated("today-past", "2026-10-19", 8 * 60), dated("next", "2026-10-20")),
        )
        val next = nextReminderTime(settings, ZonedDateTime.parse("2026-10-19T12:00:00+09:00[Asia/Tokyo]"))
        assertEquals("2026-10-20T07:45+09:00[Asia/Tokyo]", next.toString())
        assertNull(nextReminderTime(settings.copy(scheduledWorkouts = settings.scheduledWorkouts.take(2)), ZonedDateTime.parse("2026-10-19T12:00:00+09:00[Asia/Tokyo]")))
    }

    @Test fun datedPlansUseDeviceLocalDateAndTimezone() {
        val settings = AppSettings(plannedDays = emptySet(), scheduledWorkouts = listOf(dated("local", "2026-10-20", 30)))
        val now = ZonedDateTime.parse("2026-10-19T23:55:00-07:00[America/Los_Angeles]")
        val next = nextReminderTime(settings, now)!!
        assertEquals("2026-10-20T00:30-07:00[America/Los_Angeles]", next.toString())
        assertTrue(next.isAfter(now))
    }

    @Test fun exactDatedTimeDoesNotScheduleSameAlarmAgain() {
        val settings = AppSettings(plannedDays = emptySet(), scheduledWorkouts = listOf(dated("now", "2026-10-19", 19 * 60)))
        assertNull(nextReminderTime(settings, ZonedDateTime.parse("2026-10-19T19:00:00+09:00[Asia/Tokyo]")))
    }
}
