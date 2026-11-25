package jp.tsuzutore.app.desktop

import jp.tsuzutore.app.data.BackupCodec
import jp.tsuzutore.app.domain.*
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.IOException
import java.nio.file.Files
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first

class DesktopStoreTest {
    @get:Rule val temporary = TemporaryFolder()
    private fun path() = temporary.root.toPath().resolve("pc-data/state.json")

    @Test fun missingFileSeedsDefaultsWithoutChangingDisk() {
        val path = path()
        DesktopStore(path).use { store ->
            val loaded = store.load()
            assertEquals(listOf(initialTemplate()), loaded.templates)
            assertEquals(AppSettings(), loaded.settings)
            assertTrue(loaded.sessions.isEmpty())
            assertFalse(Files.exists(path))
        }
    }

    @Test fun roundTripPreservesSettingsMenusAndSchedule() {
        val path = path()
        val document = DesktopStore.defaults().copy(settings = AppSettings(weeklyGoal = 4, firstWorkoutDate = "2026-10-22"))
        DesktopStore(path).use { it.save(document); assertEquals(document, it.load()) }
        DesktopStore(path).use { assertEquals(document, it.load()) }
    }

    @Test fun invalidExistingFileIsNeverReplacedWithDefaults() {
        val path = path()
        Files.createDirectories(path.parent)
        Files.writeString(path, "{broken")
        DesktopStore(path).use { store ->
            assertThrows(IllegalArgumentException::class.java) { store.load() }
            assertEquals("{broken", Files.readString(path))
        }
    }

    @Test fun invalidWritePreservesPreviouslyCommittedFile() {
        val path = path()
        DesktopStore(path).use { store ->
            val previous = DesktopStore.defaults()
            store.save(previous)
            val bytes = Files.readAllBytes(path)
            assertThrows(IllegalArgumentException::class.java) { store.save(previous.copy(settings = previous.settings.copy(weeklyGoal = 99))) }
            assertArrayEquals(bytes, Files.readAllBytes(path))
            assertEquals(previous, store.load())
        }
    }

    @Test fun runtimeBackupRestoresPausedWithSameProgressAndFirstWorkoutDate() {
        val path = path()
        val engine = TimerEngine(MonotonicClock { 0L })
        val template = initialTemplate()
        val run = engine.start(WorkoutPlan(template.id, template.name, template.exercises, warmupSeconds = 180),
            "pc-session", 1000L, "2026-10-22", "Asia/Tokyo")
        val document = DesktopStore.defaults().copy(settings = AppSettings(firstWorkoutDate = "2026-10-22"),
            sessions = listOf(run.session!!), runtime = run)
        DesktopStore(path).use { store ->
            store.save(document)
            val restored = BackupCodec.forRestore(store.load())
            assertEquals(TimerStage.PAUSED, restored.runtime!!.stage)
            assertEquals(TimerStage.WARMUP, restored.runtime!!.pausedStage)
            assertEquals(run.remainingMs, restored.runtime!!.remainingMs)
            assertEquals(run.session, restored.runtime!!.session)
            assertEquals("2026-10-22", restored.settings.firstWorkoutDate)
            store.save(restored)
            assertEquals(restored, store.load())
        }
    }

    @Test fun secondInstanceCannotRaceWritesAndLockCanBeReopenedAfterClose() {
        val path = path()
        DesktopStore(path).use { assertThrows(IOException::class.java) { DesktopStore(path) } }
        DesktopStore(path).use { it.save(DesktopStore.defaults()) }
    }
}
class DesktopControllerTest {
    @get:Rule val temporary = TemporaryFolder()

    @Test fun quickStartAndDuplicateSetCallbackUseRealEngineAndPersistFirstDate() = runBlocking {
        val path = temporary.root.toPath().resolve("controller/state.json")
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        val controller = DesktopController(scope, path)
        try {
            withTimeout(10_000) { controller.state.first { !it.loading } }
            val exercise = Exercise("pc-reps", "PC確認用", sets = 2, restSeconds = 0)
            val plan = WorkoutPlan("pc-menu", "PC確認", listOf(exercise), warmupSeconds = 180)
            controller.actions().startQuickWorkout(plan)
            withTimeout(10_000) { controller.state.first { it.run.stage == TimerStage.EXERCISING } }
            assertEquals(0, controller.state.value.run.session!!.plan.warmupSeconds)
            val captured = controller.actions()
            captured.completeSet()
            captured.completeSet()
            withTimeout(10_000) { controller.state.first { it.run.stage == TimerStage.READY && it.run.setNumber == 2 } }
            delay(100)
            assertEquals(1, controller.state.value.run.session!!.results.size)
            assertNotNull(controller.state.value.settings.firstWorkoutDate)
        } finally { controller.close(); scope.cancel() }
        DesktopStore(path).use { store ->
            val saved = store.load()
            assertEquals(TimerStage.PAUSED, saved.runtime!!.stage)
            assertEquals(TimerStage.READY, saved.runtime!!.pausedStage)
            assertEquals(1, saved.runtime!!.session!!.results.size)
            assertNotNull(saved.settings.firstWorkoutDate)
        }
    }

    @Test fun closeAndReopenRequireExplicitResumeWithoutLosingCountdown() = runBlocking {
        val path = temporary.root.toPath().resolve("restore/state.json")
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        val controller = DesktopController(scope, path)
        try {
            withTimeout(10_000) { controller.state.first { !it.loading } }
            val template = initialTemplate()
            controller.actions().startWorkout(WorkoutPlan(template.id, template.name, template.exercises, warmupSeconds = 180))
            withTimeout(10_000) { controller.state.first { it.run.stage == TimerStage.WARMUP } }
            controller.actions().adjustTime(10)
            withTimeout(10_000) { controller.state.first { it.run.timeAdjustmentMs == 10_000L } }
        } finally { controller.close(); scope.cancel() }
        val scope2 = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        val reopened = DesktopController(scope2, path)
        try {
            withTimeout(10_000) { reopened.state.first { !it.loading } }
            val restored = reopened.state.value.run
            assertEquals(TimerStage.PAUSED, restored.stage)
            assertEquals(TimerStage.WARMUP, restored.pausedStage)
            assertEquals(10_000L, restored.timeAdjustmentMs)
            val remaining = restored.remainingMs
            delay(450)
            assertEquals(remaining, reopened.state.value.run.remainingMs)
        } finally { reopened.close(); scope2.cancel() }
    }

    @Test fun corruptStateIsRetainedThroughFailedStartupAndClose() = runBlocking {
        val path = temporary.root.toPath().resolve("invalid/state.json")
        Files.createDirectories(path.parent)
        Files.writeString(path, "corrupt state must survive")
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        val controller = DesktopController(scope, path)
        try {
            val loaded = withTimeout(10_000) { controller.state.first { !it.loading } }
            assertNotNull(loaded.message)
        } finally { controller.close(); scope.cancel() }
        assertEquals("corrupt state must survive", Files.readString(path))
    }
}