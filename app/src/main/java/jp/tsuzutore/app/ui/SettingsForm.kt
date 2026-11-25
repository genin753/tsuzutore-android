package jp.tsuzutore.app.ui

import jp.tsuzutore.app.domain.AppSettings
import jp.tsuzutore.app.domain.Limits
import java.time.LocalDate

/**
 * What the settings screen edits. Saving applies only these fields onto the latest stored
 * settings, so calendar plans and everything else stored alongside are never overwritten.
 */
internal data class SettingsForm(
    val plannedDays: Set<Int>,
    val weeklyGoal: String,
    val cueMemo: String,
    val reminderEnabled: Boolean,
    val reminderHour: Int,
    val reminderMinute: Int,
    val firstWeekEnabled: Boolean,
    val firstWorkoutDate: String?,
    val warmupSeconds: String,
    val soundEnabled: Boolean,
    val vibrationEnabled: Boolean,
    val voiceEnabled: Boolean,
    val cuesInSilentMode: Boolean,
    val keepScreenOn: Boolean,
    val notificationsEnabled: Boolean,
) {
    fun error(): String? = when {
        weeklyGoal.toIntOrNull() !in 1..7 -> "週間目標を1〜7日で入力してください。"
        warmupSeconds.toIntOrNull() !in 0..Limits.WARMUP_SECONDS -> "準備運動を0〜${Limits.WARMUP_SECONDS}秒で入力してください。"
        cueMemo.length > Limits.CUE_MEMO -> "きっかけのメモは${Limits.CUE_MEMO}文字以内にしてください。"
        reminderHour !in 0..23 || reminderMinute !in 0..59 -> "通知時刻が正しくありません。"
        firstWorkoutDate != null && runCatching { LocalDate.parse(firstWorkoutDate) }.isFailure -> "初回開始日が正しくありません。"
        else -> null
    }

    /** Applies the edited fields. Call only when [error] returns null. */
    fun applyTo(current: AppSettings): AppSettings = current.copy(
        plannedDays = plannedDays,
        weeklyGoal = weeklyGoal.toInt(),
        cueMemo = cueMemo,
        reminderEnabled = reminderEnabled,
        reminderHour = reminderHour,
        reminderMinute = reminderMinute,
        firstWeekEnabled = firstWeekEnabled,
        firstWorkoutDate = firstWorkoutDate,
        warmupSeconds = warmupSeconds.toInt(),
        soundEnabled = soundEnabled,
        vibrationEnabled = vibrationEnabled,
        voiceEnabled = voiceEnabled,
        cuesInSilentMode = cuesInSilentMode,
        keepScreenOn = keepScreenOn,
        notificationsEnabled = notificationsEnabled,
    )

    companion object {
        fun of(settings: AppSettings) = SettingsForm(
            plannedDays = settings.plannedDays,
            weeklyGoal = settings.weeklyGoal.toString(),
            cueMemo = settings.cueMemo,
            reminderEnabled = settings.reminderEnabled,
            reminderHour = settings.reminderHour,
            reminderMinute = settings.reminderMinute,
            firstWeekEnabled = settings.firstWeekEnabled,
            firstWorkoutDate = settings.firstWorkoutDate,
            warmupSeconds = settings.warmupSeconds.toString(),
            soundEnabled = settings.soundEnabled,
            vibrationEnabled = settings.vibrationEnabled,
            voiceEnabled = settings.voiceEnabled,
            cuesInSilentMode = settings.cuesInSilentMode,
            keepScreenOn = settings.keepScreenOn,
            notificationsEnabled = settings.notificationsEnabled,
        )
    }
}
