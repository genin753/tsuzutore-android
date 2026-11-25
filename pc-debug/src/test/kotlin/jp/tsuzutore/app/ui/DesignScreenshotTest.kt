package jp.tsuzutore.app.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.toAwtImage
import androidx.compose.ui.test.*
import androidx.compose.ui.unit.Density
import jp.tsuzutore.app.domain.*
import org.junit.Test
import java.io.File
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.TemporalAdjusters
import javax.imageio.ImageIO

/**
 * Renders every main screen at phone size (412 x 892 dp, 2x) for design review of the
 * always-dark "Dark Athletic" theme. Output: pc-debug/build/reports/pc-ui/design/NN-name.png
 */
@OptIn(ExperimentalTestApi::class)
class DesignScreenshotTest {
    private val zone = ZoneId.of("Asia/Tokyo")
    private val today = LocalDate.now(zone)
    private val template = initialTemplate()
    private val plan = WorkoutPlan(template.id, template.name, template.exercises, warmupSeconds = 0)
    private val pushup = template.exercises[0]
    private val done = (1..3).map { result(pushup, it, 12) }

    @Test fun home() {
        shoot("01-home") { TsuzuToreApp(appState(), noActions) }
        shoot("02-home-in-progress") {
            Page {
                HomeScreen(
                    appState().copy(run = RunState(runningSession(done), TimerStage.RESTING, exerciseIndex = 0, setNumber = 3, remainingMs = 40_000, elapsedMs = 20_000)),
                    {}, {}, {}, {}, {},
                )
            }
        }
    }

    @Test fun workout() {
        shoot("10-timer-work") {
            Workout(RunState(runningSession(done), TimerStage.EXERCISING, exerciseIndex = 5, remainingMs = 20_000, elapsedMs = 10_000))
        }
        // The frozen clock catches the deep red pulse of the last seconds part-way through.
        shoot("11-timer-final-seconds", clockMs = 150) {
            Workout(RunState(runningSession(done), TimerStage.EXERCISING, exerciseIndex = 5, remainingMs = 2_400, elapsedMs = 27_600))
        }
        shoot("12-timer-rest") {
            Workout(RunState(runningSession(done.take(1)), TimerStage.RESTING, exerciseIndex = 0, setNumber = 2, remainingMs = 75_000, elapsedMs = 45_000))
        }
        shoot("13-timer-reps", setup = { onNodeWithText("＋ 回数も記録する（任意）").performScrollTo().performClick() }) {
            Workout(RunState(runningSession(done), TimerStage.EXERCISING, exerciseIndex = 1, elapsedMs = 23_000))
        }
        shoot("14-timer-ready-choose") {
            Workout(RunState(runningSession(done), TimerStage.READY, exerciseIndex = 2, side = Side.LEFT))
        }
        shoot("15-timer-paused") {
            Workout(RunState(runningSession(done), TimerStage.PAUSED, exerciseIndex = 5, remainingMs = 12_000, elapsedMs = 18_000, pausedStage = TimerStage.EXERCISING))
        }
        val finished = runningSession(done + result(template.exercises[1], 1, null)).copy(status = SessionStatus.PARTIAL, endedAt = 2_000)
        shoot("16-finished") { Workout(RunState(finished, TimerStage.COMPLETED)) }
    }

    @Test fun pages() {
        shoot("20-menu", setup = { onNodeWithText("メニュー").performClick() }) { TsuzuToreApp(appState(), noActions) }
        shoot("21-calendar", setup = { onNodeWithText("カレンダー").performClick() }) { TsuzuToreApp(appState(), noActions) }
        shoot("22-history", setup = {
            onNodeWithText("記録").performClick()
            onAllNodesWithText("実績を見る")[0].performScrollTo().performClick()
        }) { TsuzuToreApp(appState(), noActions) }
        shoot("23-settings", setup = { onNodeWithText("設定").performClick() }) {
            TsuzuToreApp(appState().copy(notificationsAllowed = false), noActions)
        }
        shoot("24-quick-start", setup = { onNodeWithText("トレーニングを開始").performClick() }) { TsuzuToreApp(appState(), noActions) }
    }

    @Composable
    private fun Workout(run: RunState) {
        TsuzuToreTheme {
            Surface(Modifier.fillMaxSize(), color = Athletic.Black) {
                WorkoutScreen(appState().copy(run = run), noActions) { }
            }
        }
    }

    /** A tab page as the app lays it out, for screens that cannot be reached from the app's start state. */
    @Composable
    private fun Page(content: @Composable ColumnScope.() -> Unit) {
        TsuzuToreTheme {
            Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                Column(
                    Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(TtSpace.Large),
                    verticalArrangement = Arrangement.spacedBy(TtSpace.Large),
                    content = content,
                )
            }
        }
    }

    /** [clockMs] stops the test clock and advances it by that much, to catch an animation mid-way. */
    private fun shoot(
        name: String,
        clockMs: Long? = null,
        setup: ComposeUiTest.() -> Unit = {},
        content: @Composable () -> Unit,
    ) = runSkikoComposeUiTest(Size(412f * 2, 892f * 2), Density(2f)) {
        if (clockMs != null) mainClock.autoAdvance = false
        setContent { content() }
        if (clockMs != null) {
            mainClock.advanceTimeBy(clockMs)
        } else {
            waitForIdle()
            setup()
            waitForIdle()
        }
        val output = File("build/reports/pc-ui/design", "$name.png")
        output.parentFile.mkdirs()
        check(ImageIO.write(onRoot().captureToImage().toAwtImage(), "png", output)) { "PNG writer is unavailable" }
    }

    private fun appState(): AppUiState {
        val monday = today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
        val past = listOf(monday.minusDays(2), monday, monday.plusDays(2)).filter { !it.isAfter(today) }.mapIndexed { index, date ->
            val id = "past-$index"
            val startedAt = date.atTime(19, 5 + index).atZone(zone).toInstant().toEpochMilli()
            WorkoutSession(
                id, startedAt, date.toString(), zone.id, plan,
                (1..3).map { set -> result(pushup, set, 10 + index - (if (set == 3) 1 else 0)).copy(id = "$id:${pushup.id}:$set", sessionId = id) },
                SessionStatus.PARTIAL, endedAt = startedAt + 1_800_000,
            )
        }
        return AppUiState(
            templates = listOf(template, template.copy(id = "upper", name = "上半身の日", exercises = template.exercises.take(3))),
            sessions = past,
            settings = AppSettings(
                firstWeekEnabled = false,
                firstWorkoutDate = monday.minusDays(14).toString(),
                scheduledWorkouts = listOf(ScheduledWorkout("plan-1", today.plusDays(1).toString(), template.id, template.name, 7 * 60 + 30)),
            ),
            loading = false,
        )
    }

    private fun runningSession(results: List<SetResult>) = WorkoutSession(
        "shot-run", 1_000, today.toString(), zone.id, plan,
        results.map { it.copy(sessionId = "shot-run", id = "shot-run:${it.exerciseId}:${it.setNumber}:${it.side}") },
    )

    private fun result(exercise: Exercise, set: Int, reps: Int?) = SetResult(
        "r:${exercise.id}:$set", "shot-run", exercise.id, exercise.name, set, if (exercise.bilateral) Side.LEFT else Side.BOTH,
        exercise.mode, actualReps = reps, elapsedMs = 35_000, outcome = SetOutcome.COMPLETE,
        targetMin = exercise.targetMin, targetMax = exercise.targetMax, targetSeconds = exercise.targetSeconds,
    )

    private val noActions = AppUiActions(
        startWorkout = {}, startQuickWorkout = {}, saveSchedule = {}, adjustTime = {}, selectExercise = {}, beginSet = {},
        completeSet = {}, finishTimedSet = {}, pauseResume = {}, extendRest = {}, skipSet = {}, skipExercise = {}, skipWarmup = {},
        finishWorkout = {}, saveTemplate = {}, deleteTemplate = {}, reorderTemplates = {}, saveSettings = {}, previewCues = {},
        requestNotificationPermission = {}, exportBackup = {}, pickImport = {}, confirmImport = {}, cancelImport = {},
        dismissMessage = {}, confirmForm = { _, _ -> },
    )
}
