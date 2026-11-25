package jp.tsuzutore.app.data

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import jp.tsuzutore.app.domain.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.io.IOException
import java.util.UUID

@RunWith(AndroidJUnit4::class)
class AppRepositoryTest {
    private lateinit var db: AppDatabase
    private lateinit var storeScope: CoroutineScope
    private lateinit var storeFile: File
    private lateinit var repository: AppRepository
    private lateinit var reload: () -> AppRepository
    private lateinit var failingStore: FailOnceStore

    @Before fun setUp() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).build()
        storeScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        storeFile = File(context.cacheDir, "test-${UUID.randomUUID()}.preferences_pb")
        val store = PreferenceDataStoreFactory.create(scope = storeScope) { storeFile }
        failingStore = FailOnceStore(store)
        reload = { AppRepository(db, failingStore) }
        repository = reload()
    }

    @After fun tearDown() = runBlocking {
        storeScope.cancel()
        storeScope.coroutineContext[Job]?.join()
        db.close()
        storeFile.delete()
        Unit
    }

    @Test fun deletedSeedStaysDeletedAfterRepositoryRecreation() = runBlocking {
        val seed = repository.templates.first().single()
        repository.deleteTemplate(seed.id)
        assertTrue(repository.templates.first().isEmpty())
        assertTrue(reload().templates.first().isEmpty())
    }

    @Test fun repeatedSaveHasOneSessionAndOneResultAndTemplateEditsLeaveSnapshot() = runBlocking {
        val template = repository.templates.first().first()
        val exercise = template.exercises.first().copy(sets = 1)
        val one = template.copy(exercises = listOf(exercise))
        val engine = TimerEngine { 0L }
        engine.start(WorkoutPlan(one.id, one.name, one.exercises, warmupSeconds = 0), "stable-session", 1000, "2026-10-20", "Asia/Tokyo")
        engine.dispatch(TimerAction.StartSet)
        val completed = engine.dispatch(TimerAction.CompleteReps(10), walltime = 2000)
        repeat(3) { repository.saveRun(completed) }
        repository.saveTemplate(template.copy(name = "変更後", exercises = template.exercises.map { it.copy(name = "変更後の種目") }))
        repository.deleteTemplate(template.id)
        val history = repository.sessions.first().single()
        assertEquals(1, history.results.size)
        assertEquals(exercise.name, history.plan.exercises.single().name)
        assertEquals(10, history.results.single().actualReps)
        assertEquals(1, history.plan.exercises.single().sets)
    }

    @Test fun olderRevisionCannotOverwriteNewerCheckpoint() = runBlocking {
        val template = repository.templates.first().first()
        val engine = TimerEngine { 0L }
        val earlier = engine.start(createPlan(template, AppSettings(warmupSeconds = 0), java.time.LocalDate.of(2026, 10, 20)), "revision", 1000, "2026-10-20", "Asia/Tokyo")
        val later = engine.dispatch(TimerAction.StartSet)
        repository.saveRun(later)
        repository.saveRun(earlier)
        val restored = repository.loadRun()!!
        assertEquals(TimerStage.PAUSED, restored.stage)
        assertEquals(TimerStage.EXERCISING, restored.pausedStage)
    }

    @Test fun invalidImportLeavesSettingsTemplatesAndHistoryUnchanged() = runBlocking {
        repository.updateSettings { it.copy(cueMemo = "保存するメモ") }
        val before = repository.exportBackup()
        try {
            repository.importBackup("{\"schemaVersion\":999}")
            fail("不正な入力が受け入れられました")
        } catch (_: IllegalArgumentException) { }
        assertEquals(before, repository.exportBackup())
        assertEquals("保存するメモ", repository.settings.first().cueMemo)
    }

    @Test fun backupReplacementRestoresActiveWorkoutPausedAndNoAutomaticResults() = runBlocking {
        val template = repository.templates.first().first()
        val engine = TimerEngine { 0L }
        val run = engine.start(createPlan(template, AppSettings(), java.time.LocalDate.of(2026, 10, 20)), "active", 1000, "2026-10-20", "Asia/Tokyo")
        repository.saveRun(run)
        val backup = repository.exportBackup()
        repository.updateSettings { it.copy(weeklyGoal = 5) }
        repository.deleteTemplate(template.id)
        repository.importBackup(backup)
        assertEquals(3, repository.settings.first().weeklyGoal)
        assertEquals(1, repository.templates.first().size)
        assertEquals(TimerStage.PAUSED, repository.loadRun()!!.stage)
        assertTrue(repository.sessions.first().single().results.isEmpty())
        assertTrue(repository.loadRun()!!.active)
    }

    @Test fun settingsWriteFailureRollsBackRoomReplacementAndOriginalSettings() = runBlocking {
        repository.updateSettings { it.copy(cueMemo = "元の設定") }
        val before = repository.exportBackup()
        val replacement = BackupCodec.decode(before).copy(settings = AppSettings(weeklyGoal = 6), templates = emptyList())
        // Journal succeeds; settings write inside the Room transaction fails once.
        failingStore.writesUntilFailure = 2
        try {
            repository.importBackup(BackupCodec.encode(replacement))
            fail("設定保存の失敗が通知されませんでした")
        } catch (_: IOException) { }
        assertEquals(before, repository.exportBackup())
        assertEquals(before, reload().exportBackup())
    }

    private class FailOnceStore(private val delegate: DataStore<Preferences>) : DataStore<Preferences> {
        var writesUntilFailure: Int = -1
        override val data get() = delegate.data
        override suspend fun updateData(transform: suspend (t: Preferences) -> Preferences): Preferences {
            if (writesUntilFailure > 0 && --writesUntilFailure == 0) throw IOException("検証用の保存失敗")
            return delegate.updateData(transform)
        }
    }
}
