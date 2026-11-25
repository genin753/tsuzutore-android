package jp.tsuzutore.app.data

import jp.tsuzutore.app.domain.*
import kotlinx.serialization.SerializationException
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.time.LocalDate
import java.time.ZoneId

/** Stable, documented interchange format; elapsedRealtime deadlines are never exported. */
@Serializable
data class BackupDocument(
    val schemaVersion: Int,
    val settings: AppSettings,
    val templates: List<MenuTemplate>,
    val sessions: List<WorkoutSession>,
    val runtime: RunState? = null,
)

data class BackupPreview(
    val templateCount: Int,
    val sessionCount: Int,
    val performedSets: Int,
    val hasActiveRun: Boolean,
    val firstDate: String?,
    val lastDate: String?,
) {
    val summary: String get() = "メニュー ${templateCount}件・記録 ${sessionCount}件・実施 ${performedSets}セット" +
        if (hasActiveRun) "。進行中の記録は一時停止で復元します。" else ""
}

object BackupCodec {
    const val SCHEMA_VERSION = 1
    const val MAX_BYTES = 8 * 1024 * 1024
    val json = Json {
        encodeDefaults = true
        ignoreUnknownKeys = false
        isLenient = false
        allowSpecialFloatingPointValues = false
        prettyPrint = true
    }

    fun encode(document: BackupDocument): String {
        validate(document)
        return json.encodeToString(document).also { requireSize(it) }
    }

    fun decode(text: String): BackupDocument {
        requireSize(text)
        val document = try {
            json.decodeFromString<BackupDocument>(text)
        } catch (_: SerializationException) {
            throw IllegalArgumentException("バックアップの形式が正しくありません。つづトレから書き出したJSONを選んでください。")
        } catch (_: IllegalArgumentException) {
            throw IllegalArgumentException("バックアップの値を読み取れません。")
        }
        validate(document)
        return document
    }

    fun preview(document: BackupDocument): BackupPreview {
        val dates = document.sessions.map { it.localDate }.sorted()
        return BackupPreview(document.templates.size, document.sessions.size,
            document.sessions.sumOf { session -> session.results.groupBy { it.exerciseId to it.setNumber }.values.count { results ->
                results.any { it.wasPerformed() } } },
            document.runtime?.active == true, dates.firstOrNull(), dates.lastOrNull())
    }

    fun forRestore(document: BackupDocument): BackupDocument = document.copy(runtime = document.runtime?.let(::restorePaused))

    fun validate(document: BackupDocument) {
        require(document.schemaVersion == SCHEMA_VERSION) { "このバックアップのバージョンには対応していません。" }
        require(document.templates.size <= 500 && document.sessions.size <= 10_000) { "バックアップの記録件数が上限を超えています。" }
        validateSettings(document.settings).firstOrNull()?.let { throw IllegalArgumentException(it) }
        document.settings.firstWorkoutDate?.let { validDate(it) }
        unique(document.templates.map { it.id }, "メニュー")
        unique(document.sessions.map { it.id }, "セッション")
        document.templates.forEach { template ->
            validId(template.id)
            validateTemplate(template).firstOrNull()?.let { throw IllegalArgumentException(it) }
            unique(template.exercises.map { it.id }, "種目")
            template.exercises.forEach { validId(it.id) }
        }
        val resultIds = HashSet<String>()
        document.sessions.forEach { session ->
            validId(session.id)
            validateSession(session).firstOrNull()?.let { throw IllegalArgumentException(it) }
            validatePlan(session.plan).firstOrNull()?.let { throw IllegalArgumentException(it) }
            require(session.startedAt >= 0 && (session.endedAt == null || session.endedAt >= 0)) { "記録の開始・終了日時が正しくありません。" }
            validDate(session.localDate)
            try { ZoneId.of(session.zoneId) } catch (_: Exception) { throw IllegalArgumentException("記録のタイムゾーンが正しくありません。") }
            unique(session.plan.exercises.map { it.id }, "記録内の種目")
            val exercises = session.plan.exercises.associateBy { it.id }
            val positions = HashSet<Triple<String, Int, Side>>()
            session.results.forEach { result ->
                require(result.id.isNotBlank() && result.id.length <= 600 && '\u0000' !in result.id) { "セットのIDが正しくありません。" }
                require(resultIds.add(result.id)) { "セットのIDが重複しています。" }
                require(result.sessionId == session.id) { "セットとセッションの対応が正しくありません。" }
                val exercise = exercises[result.exerciseId]
                    ?: throw IllegalArgumentException("セットが存在しない種目を参照しています。")
                require(result.setNumber in 1..exercise.sets) { "セット番号が範囲外です。" }
                require(result.mode == exercise.mode && result.exerciseName == exercise.name) { "セットと種目の内容が一致していません。" }
                require(if (exercise.bilateral) result.side != Side.BOTH else result.side == Side.BOTH) { "左右の記録が種目と一致していません。" }
                require(positions.add(Triple(result.exerciseId, result.setNumber, result.side))) { "同じセットの記録が重複しています。" }
                require(result.elapsedMs in 0..604_800_000L && (result.actualReps == null || result.actualReps in 0..999)) { "実績の回数・時間が範囲外です。" }
                require(result.load.length <= 120 && result.difficulty.length <= 120) { "負荷・難度の文字数が上限を超えています。" }
                require(result.targetMin == exercise.targetMin && result.targetMax == exercise.targetMax && result.targetSeconds == exercise.targetSeconds) { "セットの目標が開始時のメニューと一致していません。" }
                if (result.outcome == SetOutcome.COMPLETE && result.mode == ExerciseMode.REPS) {
                    require(result.actualReps == null || result.actualReps > 0) { "回数制の完了記録に0回は保存できません。" }
                }
                if (result.mode == ExerciseMode.TIME) require(result.actualReps == null) { "時間制の記録に回数は指定できません。" }
                if (result.outcome == SetOutcome.SKIPPED) require((result.actualReps ?: 0) == 0 && result.elapsedMs == 0L) { "スキップしたセットに実施回数・時間は保存できません。" }
            }
            // Exercises may be interleaved freely, but each exercise resolves sets and sides in order.
            session.plan.exercises.forEach { exercise ->
                val expected = (1..exercise.sets).flatMap { setNumber ->
                    (if (exercise.bilateral) listOf(Side.LEFT, Side.RIGHT) else listOf(Side.BOTH)).map { setNumber to it }
                }
                val resolved = session.results.filter { it.exerciseId == exercise.id }.map { it.setNumber to it.side }
                require(resolved == expected.take(resolved.size)) { "種目内のセット・左右の記録順が正しくありません。" }
            }
        }
        document.runtime?.let { runtime ->
            require(runtime.revision in 0..1_000_000_000_000L && runtime.remainingMs in 0..86_400_000L && runtime.elapsedMs in 0..604_800_000L && runtime.timeAdjustmentMs in -86_400_000L..86_400_000L) { "進行中タイマーの値が範囲外です。" }
            val session = runtime.session
            if (session == null) {
                require(runtime.stage == TimerStage.IDLE) { "進行中タイマーに記録がありません。" }
            } else {
                require(document.sessions.any { it.id == session.id && it == session }) { "進行中タイマーと保存記録が一致していません。" }
                if (runtime.active) {
                    require(runtime.exerciseIndex in session.plan.exercises.indices) { "進行中の種目番号が範囲外です。" }
                    val exercise = session.plan.exercises[runtime.exerciseIndex]
                    require(runtime.setNumber in 1..exercise.sets) { "進行中のセット番号が範囲外です。" }
                    require(runtime.stage !in setOf(TimerStage.IDLE, TimerStage.COMPLETED, TimerStage.INTERRUPTED)) { "進行中タイマーの状態が正しくありません。" }
                    require(if (exercise.bilateral) runtime.side != Side.BOTH else runtime.side == Side.BOTH) { "進行中タイマーの左右が一致していません。" }
                    if (runtime.stage == TimerStage.PAUSED) require(runtime.pausedStage != null && runtime.pausedStage !in setOf(TimerStage.IDLE, TimerStage.PAUSED, TimerStage.COMPLETED, TimerStage.INTERRUPTED)) { "一時停止前の状態が正しくありません。" }
                    val pending = nextPendingSet(exercise, session.results)
                    require(pending != null && pending.setNumber == runtime.setNumber && pending.side == runtime.side) { "タイマーの現在位置と確定済みセットが一致していません。" }
                    val stage = if (runtime.stage == TimerStage.PAUSED) runtime.pausedStage else runtime.stage
                    fun validCountdown(originalTargetMs: Long): Boolean {
                        val adjustedTarget = originalTargetMs + runtime.timeAdjustmentMs
                        return adjustedTarget in 1000L..86_400_000L && runtime.remainingMs + runtime.elapsedMs == adjustedTarget
                    }
                    when (stage) {
                        TimerStage.WARMUP -> require(runtime.exerciseIndex == 0 && runtime.setNumber == 1 && session.results.isEmpty() && session.plan.warmupSeconds > 0 && validCountdown(session.plan.warmupSeconds * 1000L)) { "準備運動の計時が正しくありません。" }
                        TimerStage.PREPARING -> require(exercise.mode == ExerciseMode.TIME && validCountdown(3000L)) { "開始前カウントの計時が正しくありません。" }
                        TimerStage.EXERCISING -> if (exercise.mode == ExerciseMode.TIME) {
                            require(validCountdown(exercise.targetSeconds * 1000L)) { "運動タイマーの計時が正しくありません。" }
                        } else require(runtime.remainingMs == 0L && runtime.timeAdjustmentMs == 0L) { "回数制の運動に残り時間・時間調整は指定できません。" }
                        TimerStage.SIDE_SWITCH -> require(exercise.bilateral && runtime.side == Side.RIGHT && runtime.timeAdjustmentMs == 0L) { "左右切り替えの状態が正しくありません。" }
                        TimerStage.READY -> require(runtime.timeAdjustmentMs == 0L) { "開始待ちに時間調整は指定できません。" }
                        // Older backups extended rest without an adjustment field, so keep rest compatible.
                        TimerStage.RESTING -> Unit
                        else -> Unit
                    }
                } else {
                    require(runtime.stage == if (session.status == SessionStatus.INTERRUPTED) TimerStage.INTERRUPTED else TimerStage.COMPLETED) { "終了した記録とタイマーの状態が一致していません。" }
                }
            }
            if (runtime.stage != TimerStage.PAUSED) require(runtime.pausedStage == null) { "一時停止以外の状態に再開先は指定できません。" }
        }
        require(document.sessions.count { it.status == SessionStatus.ACTIVE } <= 1) { "進行中の記録は1件までです。" }
        val activeSession = document.sessions.singleOrNull { it.status == SessionStatus.ACTIVE }
        if (activeSession != null) require(document.runtime?.session?.id == activeSession.id) { "進行中の記録にはタイマー状態が必要です。" }
    }

    private fun requireSize(text: String) {
        require(text.isNotBlank()) { "ファイルが空です。" }
        require(text.length <= MAX_BYTES && text.toByteArray(Charsets.UTF_8).size <= MAX_BYTES) { "バックアップは8MB以内のファイルを選んでください。" }
    }
    private fun validId(id: String) { require(id.isNotBlank() && id.length <= 200 && '\u0000' !in id) { "記録のIDが正しくありません。" } }
    private fun unique(ids: List<String>, label: String) { require(ids.toSet().size == ids.size) { "${label}のIDが重複しています。" } }
    private fun validDate(value: String) {
        try { require(LocalDate.parse(value).toString() == value) } catch (_: Exception) { throw IllegalArgumentException("日付は年-月-日の形式で指定してください。") }
    }
}
