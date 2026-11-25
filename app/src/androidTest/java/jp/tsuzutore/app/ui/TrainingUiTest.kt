package jp.tsuzutore.app.ui

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import jp.tsuzutore.app.domain.*
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.time.YearMonth

/** Render-only checks: callbacks capture intent and never touch the real repository or service. */
@RunWith(AndroidJUnit4::class)
class TrainingUiTest {
    @get:Rule val compose = createComposeRule()

    private val pushup = Exercise("pushup-ui", "腕立て伏せ", sets = 2)
    private val plank = Exercise("plank-ui", "プランク", ExerciseMode.TIME, sets = 2)
    private val menu = MenuTemplate("home-ui", "全身トレーニング", listOf(pushup, plank))
    private val plan = WorkoutPlan(menu.id, menu.name, menu.exercises, warmupSeconds = 180)
    private fun state(run: RunState = RunState()) = AppUiState(
        templates = listOf(menu),
        settings = AppSettings(firstWeekEnabled = false),
        run = run,
        loading = false,
    )

    @Test fun previousCompletedRunStillOpensHomeWithImmediateStartButton() {
        val completed = WorkoutSession("completed-ui", 1000, "2026-10-20", "Asia/Tokyo", plan,
            status = SessionStatus.COMPLETE, endedAt = 2000)
        val priorRun = RunState(completed, TimerStage.COMPLETED)
        compose.setContent { TsuzuToreApp(state(priorRun), actions()) }

        compose.onNodeWithText("トレーニングを開始  →").assertIsDisplayed()
        compose.onNodeWithText("FINISH").assertDoesNotExist()
    }

    @Test fun homeStartAndChosenExerciseRequestQuickStartAtThatExerciseWithoutWarmup() {
        var started: WorkoutPlan? = null
        compose.setContent { TsuzuToreApp(state(), actions(onQuickStart = { started = it })) }

        compose.onNodeWithText("トレーニングを開始  →").performClick()
        compose.onNodeWithText("始める種目をタップ").assertIsDisplayed()
        compose.onNodeWithText(plank.name).performScrollTo().performClick()

        compose.runOnIdle {
            assertNotNull(started)
            val selected = requireNotNull(started)
            assertEquals(plank.id, selected.exercises.first().id)
            assertEquals(0, selected.warmupSeconds)
            assertEquals(menu.exercises.map { it.id }.toSet(), selected.exercises.map { it.id }.toSet())
        }
    }

    @Test fun calendarTabAllowsMonthNavigationAndOpeningScheduleDialog() {
        compose.setContent { TsuzuToreApp(state(), actions()) }

        compose.onNodeWithText("カレンダー").performClick()
        compose.onNodeWithText("CALENDAR").assertIsDisplayed()
        compose.onNodeWithContentDescription("次の月").performClick()
        val nextMonth = YearMonth.now().plusMonths(1)
        compose.onNodeWithText("${nextMonth.year}年 ${nextMonth.monthValue}月").assertIsDisplayed()
        compose.onNodeWithText("＋ 予定を追加").performScrollTo().performClick()

        compose.onNodeWithText("トレーニングの予定を追加").assertIsDisplayed()
        compose.onNodeWithText("保存").assertIsEnabled()
        compose.onNodeWithText("キャンセル").assertIsDisplayed()
    }

    @Test fun pausedTimedWorkoutAdjustsBothDirectionsWithoutStartingOrResuming() {
        val changes = mutableListOf<Int>()
        var begins = 0
        var resumes = 0
        val session = WorkoutSession("paused-ui", 1000, "2026-10-20", "Asia/Tokyo", plan)
        val run = RunState(session, TimerStage.PAUSED, exerciseIndex = 1, remainingMs = 20_000,
            elapsedMs = 10_000, pausedStage = TimerStage.EXERCISING)
        compose.setContent {
            TsuzuToreTheme {
                WorkoutScreen(state(run), actions(onAdjust = { changes += it }, onBegin = { begins++ }, onPause = { resumes++ })) { }
            }
        }

        compose.onNodeWithContentDescription("残り時間を10秒短くする").performScrollTo().performClick()
        compose.onNodeWithContentDescription("残り時間を10秒長くする").performScrollTo().performClick()
        compose.runOnIdle {
            assertEquals(listOf(-10, 10), changes)
            assertEquals(0, begins)
            assertEquals(0, resumes)
            assertEquals(20_000L, run.remainingMs)
        }
        compose.onNodeWithText("再開する").assertExists()
    }

    private fun actions(
        onQuickStart: (WorkoutPlan) -> Unit = {},
        onAdjust: (Int) -> Unit = {},
        onBegin: () -> Unit = {},
        onPause: () -> Unit = {},
    ) = AppUiActions(
        startWorkout = {},
        startQuickWorkout = onQuickStart,
        saveSchedule = {},
        adjustTime = onAdjust,
        selectExercise = {},
        beginSet = onBegin,
        completeSet = {},
        finishTimedSet = {},
        pauseResume = onPause,
        extendRest = {},
        skipSet = {},
        skipExercise = {},
        skipWarmup = {},
        finishWorkout = {},
        saveTemplate = {},
        deleteTemplate = {},
        reorderTemplates = {},
        saveSettings = {},
        previewCues = {},
        requestNotificationPermission = {},
        exportBackup = {},
        pickImport = {},
        confirmImport = {},
        cancelImport = {},
        dismissMessage = {},
        confirmForm = { _, _ -> },
    )
}