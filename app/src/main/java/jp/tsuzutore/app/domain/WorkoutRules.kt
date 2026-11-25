package jp.tsuzutore.app.domain

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.TemporalAdjusters

fun initialTemplate(): MenuTemplate = MenuTemplate(
    id = "default-full-body", name = "全身トレーニング",
    exercises = listOf(
        Exercise("pushup", "腕立て伏せ", targetMin = 6, targetMax = 15),
        Exercise("backpack-row", "リュックロー", targetMin = 10, targetMax = 20),
        Exercise("split-squat", "スプリットスクワット", targetMin = 8, targetMax = 15, bilateral = true),
        Exercise("hip-lift", "ヒップリフト", targetMin = 12, targetMax = 20),
        Exercise("side-raise", "サイドレイズ", sets = 2, targetMin = 12, targetMax = 20),
        Exercise("plank", "プランク", mode = ExerciseMode.TIME, sets = 2, targetMin = 20, targetMax = 40, targetSeconds = 30),
    ),
)

fun validateExercise(exercise: Exercise): List<String> = buildList {
    if (exercise.id.isBlank() || exercise.id.length > 200) add("種目IDが不正です")
    if (exercise.name.isBlank() || exercise.name.length > 120) add("種目名は1〜120文字で入力してください")
    if (exercise.sets !in 1..20) add("セット数は1〜20で入力してください")
    if (exercise.targetMin !in 1..999 || exercise.targetMax !in 1..999 || exercise.targetMin > exercise.targetMax) add("目標範囲は1〜999で、下限を上限以下にしてください")
    if (exercise.targetSeconds !in 1..7200) add("運動時間は1〜7200秒で入力してください")
    if (exercise.restSeconds !in 0..3600) add("休憩は0〜3600秒で入力してください")
    if (exercise.load.length > 120 || exercise.difficulty.length > 120) add("負荷・難度は120文字以内で入力してください")
}

fun validateTemplate(template: MenuTemplate): List<String> = buildList {
    if (template.id.isBlank() || template.id.length > 200) add("メニューIDが不正です")
    if (template.name.isBlank() || template.name.length > 120) add("メニュー名は1〜120文字で入力してください")
    if (template.exercises.isEmpty() || template.exercises.size > 50) add("種目数は1〜50にしてください")
    if (template.exercises.map { it.id }.distinct().size != template.exercises.size) add("同じ種目IDが重複しています")
    template.exercises.forEach { addAll(validateExercise(it)) }
}

fun validatePlan(plan: WorkoutPlan): List<String> = buildList {
    addAll(validateTemplate(MenuTemplate(plan.templateId, plan.title, plan.exercises)))
    if (plan.warmupSeconds !in 0..1800) add("準備運動は0〜1800秒で入力してください")
    if (plan.kind == PlanKind.INTERVAL && (plan.exercises.size != 1 || plan.exercises.any { it.mode != ExerciseMode.TIME || it.bilateral })) add("インターバルは左右なしの時間制1種目にしてください")
}

fun validateSettings(settings: AppSettings): List<String> = buildList {
    addAll(validateSchedule(settings.scheduledWorkouts))
    if (settings.plannedDays.any { it !in 1..7 }) add("予定曜日が不正です")
    if (settings.weeklyGoal !in 1..7) add("週間目標は1〜7日で入力してください")
    if (settings.reminderHour !in 0..23 || settings.reminderMinute !in 0..59) add("通知時刻が不正です")
    if (settings.cueMemo.length > 500) add("きっかけのメモは500文字以内で入力してください")
    if (settings.warmupSeconds !in 0..1800) add("準備運動は0〜1800秒で入力してください")
    if (settings.firstWorkoutDate != null && runCatching { LocalDate.parse(settings.firstWorkoutDate) }.isFailure) add("初回ワークアウト日が不正です")
}

fun validateSession(session: WorkoutSession): List<String> = buildList {
    if (session.id.isBlank() || session.id.length > 200) add("セッションIDが不正です")
    if (runCatching { LocalDate.parse(session.localDate) }.isFailure) add("実施日が不正です")
    if (runCatching { ZoneId.of(session.zoneId) }.isFailure) add("タイムゾーンが不正です")
    if (session.startedAt < 0 || (session.endedAt != null && session.endedAt < 0)) add("記録時刻が不正です")
    addAll(validatePlan(session.plan))
    if (session.results.map { it.id }.distinct().size != session.results.size) add("セットIDが重複しています")
    if (session.results.map { Triple(it.exerciseId, it.setNumber, it.side) }.distinct().size != session.results.size) add("セット記録が重複しています")
    session.results.forEach { result ->
        val exercise = session.plan.exercises.find { it.id == result.exerciseId }
        if (result.id.isBlank() || result.id.length > 600 || result.sessionId != session.id) add("セットの所属が不正です")
        if (exercise == null || result.setNumber !in 1..(exercise?.sets ?: 0)) add("セット番号・種目が不正です")
        if (exercise != null && (exercise.mode != result.mode || (exercise.bilateral && result.side == Side.BOTH) || (!exercise.bilateral && result.side != Side.BOTH))) add("セットの種目形式が一致しません")
        if (result.actualReps != null && result.actualReps !in 0..999) add("実施回数は0〜999で入力してください")
        if (result.mode == ExerciseMode.TIME && result.actualReps != null) add("時間制に実施回数を保存できません")
        if (result.mode == ExerciseMode.TIME && result.outcome == SetOutcome.COMPLETE && exercise != null && result.elapsedMs < (exercise.targetSeconds * 1000L + result.timeAdjustmentMs).coerceAtLeast(1000L)) add("時間制の完了には目標時間の実施が必要です")
        if (result.mode == ExerciseMode.REPS && result.outcome == SetOutcome.COMPLETE && result.actualReps != null && result.actualReps <= 0) add("完了セットの実施回数は1以上にしてください")
        if (result.timeAdjustmentMs !in -86_400_000L..86_400_000L || (result.mode != ExerciseMode.TIME && result.timeAdjustmentMs != 0L) || (result.outcome == SetOutcome.SKIPPED && result.timeAdjustmentMs != 0L)) add("時間調整の記録が不正です")
        if (exercise != null && result.mode == ExerciseMode.TIME && result.outcome != SetOutcome.SKIPPED && exercise.targetSeconds * 1000L + result.timeAdjustmentMs !in 1000L..86_400_000L) add("調整後の目標時間が不正です")
        if (result.elapsedMs < 0 || result.elapsedMs > 86_400_000L) add("経過時間が不正です")
        if (result.load.length > 120 || result.difficulty.length > 120 || result.exerciseName.length > 120) add("セット記録の文字数が上限を超えています")
        if (result.outcome == SetOutcome.SKIPPED && (result.actualReps != null || result.elapsedMs != 0L)) add("スキップには実績を保存できません")
    }
    if (session.status == SessionStatus.COMPLETE) {
        val expected = session.plan.exercises.flatMap { exercise ->
            (1..exercise.sets).flatMap { set ->
                (if (exercise.bilateral) listOf(Side.LEFT, Side.RIGHT) else listOf(Side.BOTH)).map { side -> Triple(exercise.id, set, side) }
            }
        }.toSet()
        if (session.results.map { Triple(it.exerciseId, it.setNumber, it.side) }.toSet() != expected || session.results.any { it.outcome != SetOutcome.COMPLETE }) add("完了記録には予定した全セットの実績が必要です")
    }
    if (session.status == SessionStatus.PARTIAL && !session.wasPerformed()) add("一部実施の記録には実施したセットが必要です")
}

/** The initial date itself is day 1; day 8 uses the ordinary plan. */
fun isFirstWeek(settings: AppSettings, today: LocalDate): Boolean {
    if (!settings.firstWeekEnabled) return false
    val first = settings.firstWorkoutDate?.let { runCatching { LocalDate.parse(it) }.getOrNull() } ?: return true
    return !today.isBefore(first) && today.isBefore(first.plusDays(7))
}

fun firstWeekEndDate(settings: AppSettings): LocalDate? = settings.firstWorkoutDate?.let {
    runCatching { LocalDate.parse(it).plusDays(6) }.getOrNull()
}

fun createPlan(template: MenuTemplate, settings: AppSettings, today: LocalDate, shortened: Boolean = false): WorkoutPlan {
    val firstWeek = isFirstWeek(settings, today)
    val exercises = template.exercises.map { it.copy(sets = if (firstWeek) 2 else it.sets) }
    return WorkoutPlan(template.id, template.name, exercises, settings.warmupSeconds, shortened, firstWeek)
}

/** A quick start begins with the tapped exercise and retains every other planned exercise. */
fun createQuickStartPlan(template: MenuTemplate, settings: AppSettings, today: LocalDate, exerciseId: String): WorkoutPlan {
    val plan = createPlan(template, settings, today)
    val first = plan.exercises.find { it.id == exerciseId }
        ?: throw IllegalArgumentException("開始する種目が見つかりません。")
    return plan.copy(exercises = listOf(first) + plan.exercises.filterNot { it.id == exerciseId }, warmupSeconds = 0)
}
fun SetResult.wasPerformed(): Boolean = outcome != SetOutcome.SKIPPED && when (mode) {
    ExerciseMode.REPS -> actualReps?.let { it > 0 } ?: (outcome == SetOutcome.COMPLETE)
    ExerciseMode.TIME -> elapsedMs > 0
}

fun WorkoutSession.wasPerformed(): Boolean = results.any { it.wasPerformed() }

fun completedPlanStatus(results: List<SetResult>): SessionStatus = when {
    results.none { it.wasPerformed() } -> SessionStatus.INTERRUPTED
    results.any { it.outcome != SetOutcome.COMPLETE } -> SessionStatus.PARTIAL
    else -> SessionStatus.COMPLETE
}

fun weeklyPerformedDays(sessions: List<WorkoutSession>, today: LocalDate): Set<LocalDate> {
    val monday = today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
    return sessions.asSequence()
        .filter { it.plan.kind == PlanKind.WORKOUT && it.wasPerformed() }
        .mapNotNull { runCatching { LocalDate.parse(it.localDate) }.getOrNull() }
        .filter { !it.isBefore(monday) && it.isBefore(monday.plusDays(7)) }
        .toSet()
}

fun nextPlannedDate(settings: AppSettings, from: LocalDate, includeToday: Boolean = false): LocalDate? {
    val first = if (includeToday) from else from.plusDays(1)
    val weekly = (0L..6L).asSequence().map { first.plusDays(it) }
        .firstOrNull { it.dayOfWeek.value in settings.plannedDays }
    val dated = settings.scheduledWorkouts.mapNotNull { runCatching { LocalDate.parse(it.date) }.getOrNull() }
        .filter { !it.isBefore(first) }.minOrNull()
    return listOfNotNull(weekly, dated).minOrNull()
}

/** A persisted running phase always needs an explicit user resume after process death. */
fun restorePaused(saved: RunState): RunState = if (saved.active && saved.stage !in setOf(TimerStage.IDLE, TimerStage.COMPLETED, TimerStage.INTERRUPTED)) {
    saved.copy(stage = TimerStage.PAUSED, pausedStage = saved.pausedStage ?: saved.stage, revision = saved.revision + 1)
} else saved

/** Two most recent performed occurrences must use the same structure and user-confirmed form. */
fun progressionSuggested(sessions: List<WorkoutSession>, exerciseId: String): Boolean {
    val recent = sessions.filter { it.plan.kind == PlanKind.WORKOUT && it.plan.exercises.any { ex -> ex.id == exerciseId } && it.results.any { result -> result.exerciseId == exerciseId && result.wasPerformed() } }
        .sortedByDescending { it.startedAt }.take(2)
    if (recent.size != 2 || recent.any { !it.formConfirmed }) return false
    val exercises = recent.map { session -> session.plan.exercises.first { it.id == exerciseId } }
    val first = exercises.first()
    if (first.mode != ExerciseMode.REPS || exercises.any { it.mode != first.mode || it.sets != first.sets || it.bilateral != first.bilateral || it.targetMin != first.targetMin || it.targetMax != first.targetMax || it.load != first.load || it.difficulty != first.difficulty }) return false
    return recent.all { session ->
        val results = session.results.filter { it.exerciseId == exerciseId }
        val expected = (1..first.sets).flatMap { set -> (if (first.bilateral) listOf(Side.LEFT, Side.RIGHT) else listOf(Side.BOTH)).map { side -> set to side } }.toSet()
        results.size == expected.size && results.map { it.setNumber to it.side }.toSet() == expected && results.all {
            it.outcome == SetOutcome.COMPLETE && (it.actualReps ?: 0) >= first.targetMax && it.load == first.load && it.difficulty == first.difficulty
        }
    }
}
