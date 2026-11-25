package jp.tsuzutore.app.domain

import kotlinx.serialization.Serializable
import java.time.LocalDate
import java.time.YearMonth

/** A dated plan, kept independently of the editable menu so its title survives deletion. */
@Serializable
data class ScheduledWorkout(
    val id: String,
    val date: String,
    val templateId: String,
    val title: String,
    val timeMinutes: Int? = null,
)

fun validateSchedule(schedule: List<ScheduledWorkout>): List<String> = buildList {
    if (schedule.size > 5000) add("予定は5,000件以内にしてください")
    if (schedule.map { it.id }.toSet().size != schedule.size) add("予定IDが重複しています")
    schedule.take(5001).forEach { event ->
        if (event.id.isBlank() || event.id.length > 160) add("予定IDが不正です")
        if (event.templateId.isBlank() || event.templateId.length > 160) add("予定のメニューIDが不正です")
        if (event.title.isBlank() || event.title.length > 100) add("予定名は1〜100文字で入力してください")
        val date = runCatching { LocalDate.parse(event.date) }.getOrNull()
        if (!event.date.matches(Regex("\\d{4}-\\d{2}-\\d{2}")) || date?.toString() != event.date) add("予定日は YYYY-MM-DD の正しい日付で入力してください")
        if (event.timeMinutes != null && event.timeMinutes !in 0..1439) add("予定時刻は00:00〜23:59で入力してください")
    }
}

/** Monday-first full weeks, including adjacent-month days for a continuous calendar. */
fun monthCalendarDates(month: YearMonth): List<LocalDate> {
    val first = month.atDay(1)
    val last = month.atEndOfMonth()
    val start = first.minusDays((first.dayOfWeek.value - 1).toLong())
    val end = last.plusDays((7 - last.dayOfWeek.value).toLong())
    val count = (end.toEpochDay() - start.toEpochDay() + 1).toInt()
    return List(count) { start.plusDays(it.toLong()) }
}

/** Timed events first; all-day events remain at the bottom of their date. */
fun sortedSchedule(schedule: List<ScheduledWorkout>): List<ScheduledWorkout> = schedule.sortedWith(
    compareBy<ScheduledWorkout> { it.date }.thenBy { it.timeMinutes ?: Int.MAX_VALUE }.thenBy { it.title }.thenBy { it.id },
)
