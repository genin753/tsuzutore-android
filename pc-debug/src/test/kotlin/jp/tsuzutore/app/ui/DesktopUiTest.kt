package jp.tsuzutore.app.ui

import androidx.compose.ui.test.*
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.test.junit4.createComposeRule

import jp.tsuzutore.app.domain.*
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

import java.time.YearMonth
import java.awt.image.BufferedImage
import java.io.File
import javax.imageio.ImageIO

/** Shared UI checks: isolated Compose content and callbacks, without disk state or Android services. */

class DesktopUiTest {
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

        compose.onNodeWithText("トレーニングを開始").assertIsDisplayed()
        compose.onNodeWithText("ホームに戻る").assertDoesNotExist()
        saveOwnRootScreenshot("home.png")
    }

    @Test fun homeStartAndChosenExerciseRequestQuickStartAtThatExerciseWithoutWarmup() {
        var started: WorkoutPlan? = null
        compose.setContent { TsuzuToreApp(state(), actions(onQuickStart = { started = it })) }

        compose.onNodeWithText("トレーニングを開始").performClick()
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
        compose.onNodeWithText("トレーニングの予定").assertIsDisplayed()
        compose.onNodeWithContentDescription("次の月").performClick()
        val nextMonth = YearMonth.now().plusMonths(1)
        compose.onNodeWithText("${nextMonth.year}年 ${nextMonth.monthValue}月").assertIsDisplayed()
        saveOwnRootScreenshot("calendar.png")
        compose.onNodeWithText("予定を追加").performScrollTo().performClick()

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

    @Test fun repSetCanOptionallyRecordTheCountBeforeCompleting() {
        var plain = 0
        val recorded = mutableListOf<Int>()
        val session = WorkoutSession("reps-ui", 1000, "2026-10-20", "Asia/Tokyo", plan)
        val run = RunState(session, TimerStage.EXERCISING, exerciseIndex = 0)
        compose.setContent {
            TsuzuToreTheme {
                WorkoutScreen(state(run), actions(onComplete = { plain++ }, onCompleteWithReps = { recorded += it })) { }
            }
        }

        compose.onNodeWithText("＋ 回数も記録する（任意）").performScrollTo().performClick()
        compose.onNodeWithContentDescription("回数を増やす").performScrollTo().performClick()
        compose.onNodeWithText("セット完了").performScrollTo().performClick()
        compose.runOnIdle {
            assertEquals(listOf(pushup.targetMin + 1), recorded)
            assertEquals(0, plain)
        }
    }

    @Test fun historyRecordIsDeletedOnlyAfterConfirmation() {
        val deleted = mutableListOf<String>()
        val result = SetResult("done-ui:pushup", "done-ui", pushup.id, pushup.name, 1, Side.BOTH, ExerciseMode.REPS,
            outcome = SetOutcome.COMPLETE, targetMin = pushup.targetMin, targetMax = pushup.targetMax, targetSeconds = pushup.targetSeconds)
        val done = WorkoutSession("done-ui", 1000, "2026-10-20", "Asia/Tokyo", plan, listOf(result), SessionStatus.PARTIAL, endedAt = 2000)
        compose.setContent { TsuzuToreApp(state().copy(sessions = listOf(done)), actions(onDelete = { deleted += it })) }

        compose.onNodeWithText("記録").performClick()
        compose.onNodeWithText("実績を見る").performScrollTo().performClick()
        compose.onNodeWithText("回数を入力").assertExists()
        compose.onNodeWithText("この記録を削除").performScrollTo().performClick()
        compose.runOnIdle { assertTrue(deleted.isEmpty()) }
        compose.onNodeWithText("削除する").performClick()
        compose.runOnIdle { assertEquals(listOf("done-ui"), deleted) }
    }

    @Test fun enablingReminderWithoutPermissionAsksForItAndSavesOnlyScreenFields() {
        var requests = 0
        var change: ((AppSettings) -> AppSettings)? = null
        val event = ScheduledWorkout("event-ui", "2026-12-01", menu.id, menu.name, 19 * 60)
        compose.setContent {
            TsuzuToreApp(state().copy(notificationsAllowed = false), actions(onRequestPermission = { requests++ }, onSaveSettings = { change = it }))
        }

        compose.onNodeWithText("設定").performClick()
        compose.onNodeWithContentDescription("習慣リマインド").performScrollTo().performClick()
        compose.runOnIdle { assertEquals(1, requests) }
        compose.onNodeWithText("設定を保存").performScrollTo().performClick()
        compose.runOnIdle {
            val saved = requireNotNull(change)(AppSettings(firstWeekEnabled = false, scheduledWorkouts = listOf(event)))
            assertTrue(saved.reminderEnabled)
            assertEquals(listOf(event), saved.scheduledWorkouts)
        }
    }

    @Test fun scheduleDateAndTimeAreChosenWithPickersInsteadOfTyping() {
        var saved: List<ScheduledWorkout>? = null
        compose.setContent { TsuzuToreApp(state(), actions(onSaveSchedule = { saved = it })) }

        compose.onNodeWithText("カレンダー").performClick()
        compose.onNodeWithText("予定を追加").performScrollTo().performClick()
        compose.onNodeWithText("時刻なし").assertExists()
        compose.onNodeWithContentDescription("時刻を選ぶ").performClick()
        compose.onNodeWithText("決定").performClick()
        compose.onNodeWithText("19:00").assertExists()
        compose.onNodeWithContentDescription("日付を変更").performClick()
        compose.onNodeWithText("決定").performClick()
        compose.onNodeWithText("保存").performClick()
        compose.runOnIdle {
            val event = requireNotNull(saved).single()
            assertEquals(19 * 60, event.timeMinutes)
            assertEquals(java.time.LocalDate.now().toString(), event.date)
        }
    }

    private fun saveOwnRootScreenshot(name: String) {
        val pixels = compose.onRoot().captureToImage().toPixelMap()
        val image = BufferedImage(pixels.width, pixels.height, BufferedImage.TYPE_INT_ARGB)
        for (y in 0 until pixels.height) {
            for (x in 0 until pixels.width) {
                image.setRGB(x, y, pixels[x, y].toArgb())
            }
        }
        val output = File("build/reports/pc-ui", name)
        output.parentFile.mkdirs()
        check(ImageIO.write(image, "png", output)) { "PNG writer is unavailable" }
    }

    private fun actions(
        onQuickStart: (WorkoutPlan) -> Unit = {},
        onAdjust: (Int) -> Unit = {},
        onBegin: () -> Unit = {},
        onPause: () -> Unit = {},
        onComplete: () -> Unit = {},
        onCompleteWithReps: (Int) -> Unit = {},
        onDelete: (String) -> Unit = {},
        onRequestPermission: () -> Unit = {},
        onSaveSettings: ((AppSettings) -> AppSettings) -> Unit = {},
        onSaveSchedule: (List<ScheduledWorkout>) -> Unit = {},
    ) = AppUiActions(
        startWorkout = {},
        startQuickWorkout = onQuickStart,
        saveSchedule = onSaveSchedule,
        adjustTime = onAdjust,
        selectExercise = {},
        beginSet = onBegin,
        completeSet = onComplete,
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
        saveSettings = onSaveSettings,
        previewCues = {},
        requestNotificationPermission = onRequestPermission,
        exportBackup = {},
        pickImport = {},
        confirmImport = {},
        cancelImport = {},
        dismissMessage = {},
        confirmForm = { _, _ -> },
        completeSetWithReps = onCompleteWithReps,
        deleteSession = onDelete,
    )
}