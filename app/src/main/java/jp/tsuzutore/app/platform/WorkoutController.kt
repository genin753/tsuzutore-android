package jp.tsuzutore.app.platform

import android.content.Context
import android.os.SystemClock
import android.util.Log
import jp.tsuzutore.app.data.AppRepository
import jp.tsuzutore.app.domain.*
import java.time.LocalDate
import java.time.ZoneId
import java.util.UUID
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** One engine per application process; Activity, notifications and service share this owner. */
class WorkoutController(private val context: Context, private val repository: AppRepository) {
    private val mutableError = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = mutableError.asStateFlow()

    // A storage failure in a background collector is reported instead of crashing the whole app.
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate +
        CoroutineExceptionHandler { _, failure -> report(failure, "記録を読み込めませんでした。アプリを開き直してください。") })
    private val mutex = Mutex()
    private val engine = TimerEngine(MonotonicClock { SystemClock.elapsedRealtime() })
    private val initialized = CompletableDeferred<Unit>()
    private val mutableRun = MutableStateFlow(RunState())
    val run: StateFlow<RunState> = mutableRun.asStateFlow()
    val settings = repository.settings.stateIn(scope, SharingStarted.Eagerly, AppSettings())
    private var lastCheckpoint = 0L
    private var lastSaved: RunState? = null

    init {
        scope.launch {
            try {
                repository.loadRun()?.let { saved ->
                    val restored = engine.restore(saved)
                    repository.saveRun(restored)
                    mutableRun.value = restored
                }
            } catch (e: Exception) {
                report(e, "記録を読み込めませんでした。アプリを開き直してください。")
            } finally { initialized.complete(Unit) }
        }
        scope.launch {
            initialized.await()
            while (isActive) {
                delay(200)
                mutex.withLock {
                    if (!engine.state.active || engine.state.stage == TimerStage.PAUSED) return@withLock
                    val previous = engine.state
                    try {
                        val updated = engine.tick(System.currentTimeMillis())
                        publish(updated, previous.revision != updated.revision)
                    } catch (e: Exception) { pauseAfterFailure(e) }
                }
            }
        }
        scope.launch {
            repository.settings.collect { newSettings ->
                runCatching { ReminderScheduler.schedule(context, newSettings) }
                    .onFailure { Log.w("TsuzuTore", "Reminder scheduling unavailable", it) }
            }
        }
    }

    suspend fun awaitReady() { initialized.await() }

    suspend fun start(plan: WorkoutPlan, startImmediately: Boolean = false): Boolean {
        initialized.await()
        return mutex.withLock {
            if (engine.state.active) return@withLock false
            val problems = validatePlan(plan)
            require(problems.isEmpty()) { problems.joinToString("\n") }
            val zone = ZoneId.systemDefault()
            val date = LocalDate.now(zone)
            val currentSettings = repository.settings.first()
            if (plan.kind == PlanKind.WORKOUT && currentSettings.firstWorkoutDate == null) {
                repository.updateSettings { it.copy(firstWorkoutDate = date.toString()) }
            }
            val started = engine.start(plan, UUID.randomUUID().toString(), System.currentTimeMillis(), date.toString(), zone.id)
            val ready = if (startImmediately && started.stage == TimerStage.READY) {
                engine.dispatch(TimerAction.StartSet, started.revision, System.currentTimeMillis())
            } else started
            publish(ready, true)
            true
        }
    }

    suspend fun command(action: TimerAction, expectedRevision: Long): Boolean {
        initialized.await()
        return mutex.withLock {
            val before = engine.state
            val updated = engine.dispatch(action, expectedRevision, System.currentTimeMillis())
            if (updated.revision == before.revision) return@withLock false
            try { publish(updated, true) }
            catch (e: Exception) { pauseAfterFailure(e); throw e }
            true
        }
    }

    suspend fun replaceFromBackup(text: String) {
        initialized.await()
        mutex.withLock {
            // The engine is stopped before replacement; failed imports retain an explicit paused run.
            if (engine.state.active && engine.state.stage != TimerStage.PAUSED) {
                publish(engine.dispatch(TimerAction.Pause), true)
            }
            repository.importBackup(text)
            lastSaved = null
            val imported = repository.loadRun()
            if (imported != null) {
                val paused = engine.restore(imported)
                repository.saveRun(paused)
                mutableRun.value = paused
            } else {
                mutableRun.value = engine.restore(RunState())
            }
        }
    }

    suspend fun confirmForm(sessionId: String, confirmed: Boolean) {
        mutex.withLock {
            val session = repository.sessions.first().find { it.id == sessionId } ?: return@withLock
            val updated = session.copy(formConfirmed = confirmed)
            if (engine.state.session?.id == sessionId) {
                publish(engine.updateFormConfirmation(sessionId, confirmed), true)
            } else repository.saveSession(updated)
        }
    }

    /** Deletes a finished record. The finished run shown by the engine is cleared with it. */
    suspend fun deleteSession(sessionId: String) {
        initialized.await()
        mutex.withLock {
            val current = engine.state
            require(!(current.active && current.session?.id == sessionId)) {
                "進行中のトレーニングは削除できません。終了してから削除してください。"
            }
            repository.deleteSession(sessionId)
            if (current.session?.id == sessionId) {
                lastSaved = null
                mutableRun.value = engine.restore(RunState())
            }
        }
    }

    /** Adds, changes or clears the recorded count of a completed rep set after the workout. */
    suspend fun updateSetReps(sessionId: String, resultId: String, reps: Int?) {
        initialized.await()
        mutex.withLock {
            val held = engine.state.session
            if (held != null && held.id == sessionId) {
                require(!engine.state.active) { "進行中の記録は、トレーニングの終了後に修正できます。" }
                publish(engine.replaceFinishedSession(held.withResultReps(resultId, reps)), true)
            } else {
                val session = repository.sessions.first().find { it.id == sessionId }
                    ?: throw IllegalArgumentException("記録が見つかりません。")
                repository.saveSession(session.withResultReps(resultId, reps))
            }
        }
    }

    suspend fun pauseForServiceShutdown(expectedSessionId: String? = run.value.session?.id, expectedRevision: Long? = null) {
        initialized.await()
        mutex.withLock {
            val state = engine.state
            if (state.session?.id == expectedSessionId && (expectedRevision == null || state.revision == expectedRevision) &&
                state.active && state.stage != TimerStage.PAUSED) {
                runCatching { publish(engine.dispatch(TimerAction.Pause), true) }
            }
        }
    }

    fun clearError() { mutableError.value = null }

    private suspend fun publish(value: RunState, forceSave: Boolean) {
        val now = SystemClock.elapsedRealtime()
        if (forceSave || (now - lastCheckpoint >= 5_000 && value != lastSaved)) {
            // Clock-only checkpoints keep set results untouched; transitions save everything together.
            val includeSession = forceSave || value.session != lastSaved?.session
            repository.saveRun(value, includeSession)
            lastSaved = value
            lastCheckpoint = now
        }
        mutableRun.value = value
    }

    private fun pauseAfterFailure(error: Exception) {
        val safe = engine.dispatch(TimerAction.Pause)
        mutableRun.value = safe
        report(error, "保存できなかったため一時停止しました。空き容量を確認して、再開をお試しください。")
    }

    private fun report(error: Throwable, message: String) {
        Log.e("TsuzuTore", message, error)
        mutableError.value = message
    }
}
