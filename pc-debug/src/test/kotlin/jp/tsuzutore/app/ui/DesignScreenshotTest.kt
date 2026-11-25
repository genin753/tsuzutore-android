package jp.tsuzutore.app.ui

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
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
 * Renders every main screen at phone size (412 x 892 dp, 2x) for design review:
 * light and dark brand colors, plus two sample "wallpaper" schemes standing in for Material You.
 * Output: pc-debug/build/reports/pc-ui/design/NN-name.png
 */
@OptIn(ExperimentalTestApi::class)
class DesignScreenshotTest {
    private val zone = ZoneId.of("Asia/Tokyo")
    private val today = LocalDate.now(zone)
    private val template = initialTemplate()
    private val plan = WorkoutPlan(template.id, template.name, template.exercises, warmupSeconds = 0)

    @Test fun home() {
        shoot("01-home-light") { TsuzuToreApp(appState(), noActions) }
        shoot("02-home-dark", dark = true) { TsuzuToreApp(appState(), noActions) }
        shoot("03-home-wallpaper-blue", scheme = WallpaperBlueLight) { TsuzuToreApp(appState(), noActions) }
        shoot("04-home-wallpaper-orange-dark", dark = true, scheme = WallpaperOrangeDark) { TsuzuToreApp(appState(), noActions) }
    }

    @Test fun workout() {
        val pushup = template.exercises[0]
        val done = (1..3).map { result(pushup, it, 12) }
        shoot("10-timer-countdown") {
            Workout(RunState(runningSession(done), TimerStage.EXERCISING, exerciseIndex = 5, remainingMs = 20_000, elapsedMs = 10_000))
        }
        shoot("11-timer-rest-dark", dark = true) {
            Workout(RunState(runningSession(done.take(1)), TimerStage.RESTING, exerciseIndex = 0, setNumber = 2, remainingMs = 75_000, elapsedMs = 45_000))
        }
        shoot("12-timer-reps", setup = { onNodeWithText("＋ 回数も記録する（任意）").performScrollTo().performClick() }) {
            Workout(RunState(runningSession(done), TimerStage.EXERCISING, exerciseIndex = 1, elapsedMs = 23_000))
        }
        shoot("13-timer-ready-choose") {
            Workout(RunState(runningSession(done), TimerStage.READY, exerciseIndex = 2, side = Side.LEFT))
        }
        shoot("14-timer-paused-wallpaper-blue", scheme = WallpaperBlueLight) {
            Workout(RunState(runningSession(done), TimerStage.PAUSED, exerciseIndex = 5, remainingMs = 12_000, elapsedMs = 18_000, pausedStage = TimerStage.EXERCISING))
        }
        val finished = runningSession(done + result(template.exercises[1], 1, null)).copy(status = SessionStatus.PARTIAL, endedAt = 2_000)
        shoot("15-finished") { Workout(RunState(finished, TimerStage.COMPLETED)) }
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
        shoot("25-calendar-dark", dark = true, setup = { onNodeWithText("カレンダー").performClick() }) { TsuzuToreApp(appState(), noActions) }
    }

    @Composable
    private fun Workout(run: RunState) {
        TsuzuToreTheme {
            Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.surface) {
                WorkoutScreen(appState().copy(run = run), noActions) { }
            }
        }
    }

    private fun shoot(
        name: String,
        dark: Boolean = false,
        scheme: ColorScheme? = null,
        setup: ComposeUiTest.() -> Unit = {},
        content: @Composable () -> Unit,
    ) = runSkikoComposeUiTest(Size(412f * 2, 892f * 2), Density(2f)) {
        setContent {
            CompositionLocalProvider(
                LocalDarkThemeOverride provides dark,
                LocalColorSchemeOverride provides (scheme ?: if (dark) BrandDark else BrandLight),
            ) { content() }
        }
        waitForIdle()
        setup()
        waitForIdle()
        val output = File("build/reports/pc-ui/design", "$name.png")
        output.parentFile.mkdirs()
        check(ImageIO.write(onRoot().captureToImage().toAwtImage(), "png", output)) { "PNG writer is unavailable" }
    }

    private fun appState(): AppUiState {
        val monday = today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
        val pushup = template.exercises[0]
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

/* Sample wallpaper schemes generated with material-color-utilities (TonalSpot): blue #3D6BFF and orange #FF6B1A. */
private val WallpaperBlueLight = lightColorScheme(
    primary = Color(0xFF4E5B92),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFDCE1FF),
    onPrimaryContainer = Color(0xFF364479),
    inversePrimary = Color(0xFFB7C4FF),
    secondary = Color(0xFF595D72),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFDEE1F9),
    onSecondaryContainer = Color(0xFF424659),
    tertiary = Color(0xFF75546F),
    onTertiary = Color(0xFFFFFFFF),
    tertiaryContainer = Color(0xFFFFD7F5),
    onTertiaryContainer = Color(0xFF5C3D56),
    background = Color(0xFFFAF8FF),
    onBackground = Color(0xFF1A1B21),
    surface = Color(0xFFFAF8FF),
    onSurface = Color(0xFF1A1B21),
    surfaceVariant = Color(0xFFE2E1EC),
    onSurfaceVariant = Color(0xFF45464F),
    surfaceTint = Color(0xFF4E5B92),
    inverseSurface = Color(0xFF2F3036),
    inverseOnSurface = Color(0xFFF1F0F7),
    error = Color(0xFFBA1A1A),
    onError = Color(0xFFFFFFFF),
    errorContainer = Color(0xFFFFDAD6),
    onErrorContainer = Color(0xFF93000A),
    outline = Color(0xFF767680),
    outlineVariant = Color(0xFFC6C5D0),
    scrim = Color(0xFF000000),
    surfaceBright = Color(0xFFFAF8FF),
    surfaceContainer = Color(0xFFEFEDF4),
    surfaceContainerHigh = Color(0xFFE9E7EF),
    surfaceContainerHighest = Color(0xFFE3E1E9),
    surfaceContainerLow = Color(0xFFF4F2FA),
    surfaceContainerLowest = Color(0xFFFFFFFF),
    surfaceDim = Color(0xFFDBD9E0),
)

private val WallpaperOrangeDark = darkColorScheme(
    primary = Color(0xFFFFB596),
    onPrimary = Color(0xFF552106),
    primaryContainer = Color(0xFF71361A),
    onPrimaryContainer = Color(0xFFFFDBCD),
    inversePrimary = Color(0xFF8E4D2F),
    secondary = Color(0xFFE6BEAE),
    onSecondary = Color(0xFF442A1F),
    secondaryContainer = Color(0xFF5D4034),
    onSecondaryContainer = Color(0xFFFFDBCD),
    tertiary = Color(0xFFD2C78F),
    onTertiary = Color(0xFF373106),
    tertiaryContainer = Color(0xFF4E471B),
    onTertiaryContainer = Color(0xFFEFE3A9),
    background = Color(0xFF1A110E),
    onBackground = Color(0xFFF1DFD9),
    surface = Color(0xFF1A110E),
    onSurface = Color(0xFFF1DFD9),
    surfaceVariant = Color(0xFF53443E),
    onSurfaceVariant = Color(0xFFD8C2BA),
    surfaceTint = Color(0xFFFFB596),
    inverseSurface = Color(0xFFF1DFD9),
    inverseOnSurface = Color(0xFF382E2A),
    error = Color(0xFFFFB4AB),
    onError = Color(0xFF690005),
    errorContainer = Color(0xFF93000A),
    onErrorContainer = Color(0xFFFFDAD6),
    outline = Color(0xFFA08D85),
    outlineVariant = Color(0xFF53443E),
    scrim = Color(0xFF000000),
    surfaceBright = Color(0xFF423733),
    surfaceContainer = Color(0xFF271E1A),
    surfaceContainerHigh = Color(0xFF322824),
    surfaceContainerHighest = Color(0xFF3D322E),
    surfaceContainerLow = Color(0xFF231A16),
    surfaceContainerLowest = Color(0xFF140C09),
    surfaceDim = Color(0xFF1A110E),
)
