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
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val mutex = Mutex()
    private val engine = TimerEngine(MonotonicClock { SystemClock.elapsedRealtime() })
    private val initialized = CompletableDeferred<Unit>()
    private val mutableRun = MutableStateFlow(RunState())
    val run: StateFlow<RunState> = mutableRun.asStateFlow()
    val settings = repository.settings.stateIn(scope, SharingStarted.Eagerly, AppSettings())
    private val mutableError = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = mutableError.asStateFlow()
    private var lastCheckpoint = 0L

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
            val ready = if (startImmediately && started.stage == TimerStage.READY) engine.dispatch(TimerAction.StartSet, started.revision, System.currentTimeMillis()) else started
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

    suspend fun pauseForServiceShutdown(expectedSessionId: String? = run.value.session?.id, expectedRevision: Long? = null) {
        initialized.await()
        mutex.withLock {
            if (engine.state.session?.id == expectedSessionId && (expectedRevision == null || engine.state.revision == expectedRevision) && engine.state.active && engine.state.stage != TimerStage.PAUSED) {
                runCatching { publish(engine.dispatch(TimerAction.Pause), true) }
            }
        }
    }

    fun clearError() { mutableError.value = null }

    private suspend fun publish(value: RunState, forceSave: Boolean) {
        val now = SystemClock.elapsedRealtime()
        if (forceSave || now - lastCheckpoint >= 5_000) {
            repository.saveRun(value)
            lastCheckpoint = now
        }
        mutableRun.value = value
    }

    private fun pauseAfterFailure(error: Exception) {
        val safe = engine.dispatch(TimerAction.Pause)
        mutableRun.value = safe
        report(error, "保存できなかったため一時停止しました。空き容量を確認して、再開をお試しください。")
    }

    private fun report(error: Exception, message: String) {
        Log.e("TsuzuTore", message, error)
        mutableError.value = message
    }
}
