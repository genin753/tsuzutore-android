package jp.tsuzutore.app.domain

import kotlinx.serialization.Serializable

@Serializable enum class ExerciseMode { REPS, TIME }
@Serializable enum class Side { BOTH, LEFT, RIGHT }
@Serializable enum class SetOutcome { COMPLETE, PARTIAL, SKIPPED }
@Serializable enum class SessionStatus { ACTIVE, COMPLETE, PARTIAL, INTERRUPTED }
@Serializable enum class PlanKind { WORKOUT, INTERVAL }
@Serializable enum class TimerStage { IDLE, WARMUP, READY, PREPARING, EXERCISING, SIDE_SWITCH, RESTING, PAUSED, COMPLETED, INTERRUPTED }

@Serializable
data class Exercise(
    val id: String,
    val name: String,
    val mode: ExerciseMode = ExerciseMode.REPS,
    val sets: Int = 3,
    val targetMin: Int = 6,
    val targetMax: Int = 15,
    val targetSeconds: Int = 30,
    val restSeconds: Int = 120,
    val bilateral: Boolean = false,
    val load: String = "",
    val difficulty: String = "通常",
)

@Serializable
data class MenuTemplate(val id: String, val name: String, val exercises: List<Exercise>)

@Serializable
data class WorkoutPlan(
    val templateId: String,
    val title: String,
    val exercises: List<Exercise>,
    val warmupSeconds: Int = 180,
    val shortened: Boolean = false,
    val firstWeek: Boolean = false,
    val kind: PlanKind = PlanKind.WORKOUT,
)

@Serializable
data class SetResult(
    val id: String,
    val sessionId: String,
    val exerciseId: String,
    val exerciseName: String,
    val setNumber: Int,
    val side: Side,
    val mode: ExerciseMode,
    val actualReps: Int? = null,
    val elapsedMs: Long = 0,
    val outcome: SetOutcome,
    val load: String = "",
    val difficulty: String = "通常",
    val targetMin: Int = 0,
    val targetMax: Int = 0,
    val targetSeconds: Int = 0,
    val timeAdjustmentMs: Long = 0,
)

@Serializable
data class WorkoutSession(
    val id: String,
    val startedAt: Long,
    val localDate: String,
    val zoneId: String,
    val plan: WorkoutPlan,
    val results: List<SetResult> = emptyList(),
    val status: SessionStatus = SessionStatus.ACTIVE,
    val endedAt: Long? = null,
    val formConfirmed: Boolean = false,
)

@Serializable
data class AppSettings(
    val plannedDays: Set<Int> = setOf(1, 3, 5),
    val weeklyGoal: Int = 3,
    val reminderHour: Int = 19,
    val reminderMinute: Int = 0,
    val cueMemo: String = "帰宅して着替えたら",
    val firstWeekEnabled: Boolean = true,
    val firstWorkoutDate: String? = null,
    val warmupSeconds: Int = 180,
    val soundEnabled: Boolean = true,
    val vibrationEnabled: Boolean = true,
    val voiceEnabled: Boolean = false,
    val keepScreenOn: Boolean = false,
    val reminderEnabled: Boolean = false,
    val notificationsEnabled: Boolean = false,
    val scheduledWorkouts: List<ScheduledWorkout> = emptyList(),
    /** Off keeps silent mode quiet; connected headphones still receive cues. */
    val cuesInSilentMode: Boolean = false,
)

/** A durable snapshot. Monotonic deadlines deliberately never cross process boundaries. */
@Serializable
data class RunState(
    val session: WorkoutSession? = null,
    val stage: TimerStage = TimerStage.IDLE,
    val exerciseIndex: Int = 0,
    val setNumber: Int = 1,
    val side: Side = Side.BOTH,
    val remainingMs: Long = 0,
    val elapsedMs: Long = 0,
    val pausedStage: TimerStage? = null,
    val revision: Long = 0,
    val timeAdjustmentMs: Long = 0,
) {
    val exercise: Exercise? get() = session?.plan?.exercises?.getOrNull(exerciseIndex)
    val active: Boolean get() = session?.status == SessionStatus.ACTIVE
}
