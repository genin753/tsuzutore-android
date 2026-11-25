package jp.tsuzutore.app.domain

import java.time.LocalDate
import java.time.YearMonth
import org.junit.Assert.*
import org.junit.Test

class ScheduleTest {
    private fun event(id: String = "one", date: String = "2028-02-29", time: Int? = null) =
        ScheduledWorkout(id, date, "full-body", "全身トレーニング", time)

    @Test fun leapMonthHasEveryDateAndAlignedFullWeeks() {
        val month = YearMonth.of(2028, 2)
        val days = monthCalendarDates(month)
        assertEquals(1, days.first().dayOfWeek.value)
        assertEquals(7, days.last().dayOfWeek.value)
        assertEquals(0, days.size % 7)
        assertEquals(29, days.count { YearMonth.from(it) == month })
        assertTrue(LocalDate.parse("2028-02-29") in days)
        assertEquals(days.toSet().size, days.size)
        days.zipWithNext().forEach { (a, b) -> assertEquals(a.plusDays(1), b) }
    }

    @Test fun calendarCrossesYearBoundaryWithoutDroppingDays() {
        val days = monthCalendarDates(YearMonth.of(2027, 1))
        assertEquals(LocalDate.parse("2026-12-28"), days.first())
        assertEquals(LocalDate.parse("2027-01-31"), days.last())
        assertEquals(35, days.size)
    }

    @Test fun exactFourWeekMonthAndSixWeekMonthAreSupported() {
        assertEquals(28, monthCalendarDates(YearMonth.of(2021, 2)).size)
        assertEquals(42, monthCalendarDates(YearMonth.of(2026, 3)).size)
    }

    @Test fun datesMustBeRealAndCanonical() {
        assertTrue(validateSchedule(listOf(event())).isEmpty())
        listOf("2027-02-29", "2028-2-29", "2028-02-30", "2028-02-29 ", "2028-01-01T00:00", "bad").forEach { date ->
            assertFalse("Accepted invalid date $date", validateSchedule(listOf(event(date = date))).isEmpty())
        }
    }

    @Test fun timeIsOptionalAndIncludesMidnightAndLastMinute() {
        listOf(null, 0, 1439).forEach { assertTrue(validateSchedule(listOf(event(time = it))).isEmpty()) }
        listOf(-1, 1440, Int.MAX_VALUE).forEach { assertFalse(validateSchedule(listOf(event(time = it))).isEmpty()) }
    }

    @Test fun identifiersAndTitleHaveBoundedNonblankValues() {
        val valid = event().copy(id = "a".repeat(160), templateId = "b".repeat(160), title = "筋".repeat(100))
        assertTrue(validateSchedule(listOf(valid)).isEmpty())
        listOf(
            event().copy(id = " "), event().copy(id = "a".repeat(161)),
            event().copy(templateId = ""), event().copy(templateId = "a".repeat(161)),
            event().copy(title = "\n"), event().copy(title = "筋".repeat(101)),
        ).forEach { assertFalse(validateSchedule(listOf(it)).isEmpty()) }
    }

    @Test fun duplicateIdsAreRejectedButMultipleEventsOnSameDayAreAllowed() {
        assertFalse(validateSchedule(listOf(event(), event())).isEmpty())
        assertTrue(validateSchedule(listOf(event("morning", time = 480), event("evening", time = 1140))).isEmpty())
    }

    @Test fun scheduleHasBoundedCapacity() {
        val schedule = List(5000) { event("event-$it") }
        assertTrue(validateSchedule(schedule).isEmpty())
        assertFalse(validateSchedule(schedule + event("overflow")).isEmpty())
    }

    @Test fun scheduleSortsByDateThenTimeWithNoTimeLast() {
        val untimed = event("untimed", "2028-02-29")
        val evening = event("evening", "2028-02-29", 1200)
        val morning = event("morning", "2028-02-29", 480)
        val previous = event("previous", "2028-02-28")
        assertEquals(listOf(previous, morning, evening, untimed), sortedSchedule(listOf(untimed, evening, previous, morning)))
    }
}
