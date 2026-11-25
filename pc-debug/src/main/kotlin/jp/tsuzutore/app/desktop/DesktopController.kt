package jp.tsuzutore.app.desktop

import jp.tsuzutore.app.data.BackupCodec
import jp.tsuzutore.app.data.BackupDocument
import jp.tsuzutore.app.domain.*
import jp.tsuzutore.app.ui.AppUiActions
import jp.tsuzutore.app.ui.AppUiState
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.awt.EventQueue
import java.awt.GraphicsEnvironment
import java.awt.Toolkit
import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path
import java.time.LocalDate
import java.time.ZoneId
import java.util.UUID
import javax.swing.JFileChooser
import javax.swing.JOptionPane
import javax.swing.filechooser.FileNameExtensionFilter
import kotlin.math.ceil

/** Runs the Android app's exact timer engine and screens without an Android device. */
class DesktopController(scope: CoroutineScope, private val dataPath: Path) {
    private val job = SupervisorJob(scope.coroutineContext[Job])
    private val worker = CoroutineScope(scope.coroutineContext + job + Dispatchers.IO)
    private val gate = Any()
    private val ready = CompletableDeferred<Unit>()
    private val engine = TimerEngine(MonotonicClock { monotonicMs() })
    private val mutableState = MutableStateFlow(AppUiState())
    val state: StateFlow<AppUiState> = mutableState.asStateFlow()
    private var store: DesktopStore? = null
    private var document = DesktopStore.defaults()
    private var pendingImport: BackupDocument? = null
    private var initialized = false
    private var closed = false
    private var lastCheckpoint = 0L
    private var previousCueSecond: Long? = null

    init {
        worker.launch {
            synchronized(gate) {
                try {
                    if (!closed) {
                        store = DesktopStore(dataPath)
                        val loaded = store!!.load()
                        val restored = loaded.runtime?.let(engine::restore)
                        document = loaded.copy(runtime = restored)
                        store!!.save(document)
                        initialized = true
                        refresh()
                    }
                } catch (error: Exception) {
                    mutableState.value = AppUiState(loading = false,
                        message = "PC用データを開けませんでした。保存ファイルは変更していません。${error.message.orEmpty()}")
                } finally { ready.complete(Unit) }
            }
        }
        worker.launch {
            ready.await()
            while (isActive) {
                delay(200)
                synchronized(gate) {
                    if (closed || !initialized || !engine.state.active || engine.state.stage == TimerStage.PAUSED) return@synchronized
                    try {
                        val before = engine.state
                        val updated = engine.tick(System.currentTimeMillis())
                        publish(updated, before.revision != updated.revision)
                        emitCue(before, updated)
                    } catch (error: Exception) { fail(error) }
                }
            }
        }
    }

    /** Recreate on each composed state: a callback keeps the revision of the screen that produced it. */
    fun actions(capturedRun: RunState = state.value.run): AppUiActions {
        val captured = capturedRun
        val revision = captured.revision
        return AppUiActions(
            startWorkout = { start(it, false) },
            startQuickWorkout = { start(it, true) },
            saveSchedule = { schedule -> perform { commit(document.copy(settings = document.settings.copy(scheduledWorkouts = sortedSchedule(schedule)))); message("トレーニングの予定を保存しました。") } },
            adjustTime = { command(TimerAction.AdjustTime(it), revision) },
            selectExercise = { command(TimerAction.SelectExercise(it), revision) },
            beginSet = { command(TimerAction.StartSet, revision) },
            completeSet = { command(TimerAction.CompleteSet, revision) },
            finishTimedSet = { command(TimerAction.FinishTimed(), revision) },
            pauseResume = { command(if (captured.stage == TimerStage.PAUSED) TimerAction.Resume else TimerAction.Pause, revision) },
            extendRest = { command(TimerAction.ExtendRest(30), revision) },
            skipSet = { command(TimerAction.SkipSet, revision) },
            skipExercise = { command(TimerAction.SkipExercise, revision) },
            skipWarmup = { command(TimerAction.SkipWarmup, revision) },
            finishWorkout = { command(TimerAction.Stop(System.currentTimeMillis()), revision) },
            saveTemplate = { template -> perform {
                val templates = document.templates.toMutableList()
                val index = templates.indexOfFirst { it.id == template.id }
                if (index < 0) templates.add(template) else templates[index] = template
                commit(document.copy(templates = templates)); message("メニューを保存しました。")
            } },
            deleteTemplate = { id -> perform { commit(document.copy(templates = document.templates.filterNot { it.id == id })); message("メニューを削除しました。") } },
            reorderTemplates = { ids -> perform {
                require(ids.size == ids.toSet().size && ids.toSet() == document.templates.map { it.id }.toSet()) { "並べ替えるメニューが一致していません。" }
                val byId = document.templates.associateBy { it.id }
                commit(document.copy(templates = ids.map(byId::getValue)))
            } },
            saveSettings = { settings -> perform {
                commit(document.copy(settings = settings.copy(firstWorkoutDate = settings.firstWorkoutDate ?: document.settings.firstWorkoutDate)))
                message("設定を保存しました。PC版ではAndroidの通知・振動・音声案内を再現しません。")
            } },
            previewCues = { perform {
                beep()
                message("PC版では効果音がONの場合に確認音を鳴らします。振動・日本語音声は実機で確認してください。")
            } },
            requestNotificationPermission = { perform { message("PC版にAndroidの通知権限はありません。通知・リマインダーは実機で確認してください。") } },
            exportBackup = ::exportBackup,
            pickImport = ::pickImport,
            confirmImport = ::confirmImport,
            cancelImport = { synchronized(gate) { pendingImport = null; mutableState.value = mutableState.value.copy(importPreview = null) } },
            dismissMessage = { synchronized(gate) { mutableState.value = mutableState.value.copy(message = null) } },
            confirmForm = { id, confirmed -> perform {
                if (engine.state.session?.id == id) publish(engine.updateFormConfirmation(id, confirmed), true)
                else commit(document.copy(sessions = document.sessions.map { if (it.id == id) it.copy(formConfirmed = confirmed) else it }))
            } },
        )
    }

    private fun start(plan: WorkoutPlan, immediate: Boolean) = perform {
        if (engine.state.active) {
            message("進行中のワークアウトに戻ってください。")
            return@perform
        }
        val actualPlan = if (immediate) plan.copy(warmupSeconds = 0) else plan
        val errors = validatePlan(actualPlan)
        require(errors.isEmpty()) { errors.joinToString("\n") }
        val zone = ZoneId.systemDefault()
        val date = LocalDate.now(zone).toString()
        val settings = if (actualPlan.kind == PlanKind.WORKOUT && document.settings.firstWorkoutDate == null)
            document.settings.copy(firstWorkoutDate = date) else document.settings
        val before = engine.state
        var run = engine.start(actualPlan, UUID.randomUUID().toString(), System.currentTimeMillis(), date, zone.id)
        if (immediate && run.stage == TimerStage.READY) run = engine.dispatch(TimerAction.StartSet, run.revision, System.currentTimeMillis())
        document = document.copy(settings = settings)
        publish(run, true)
        emitCue(before, run)
    }

    private fun command(action: TimerAction, expectedRevision: Long) = perform {
        val before = engine.state
        val updated = engine.dispatch(action, expectedRevision, System.currentTimeMillis())
        publish(updated, updated.revision != before.revision)
        emitCue(before, updated)
    }

    private fun withRun(base: BackupDocument, run: RunState): BackupDocument {
        val session = run.session
        val sessions = if (session == null) base.sessions else
            (base.sessions.filterNot { it.id == session.id } + session).sortedByDescending { it.startedAt }
        return base.copy(sessions = sessions, runtime = if (session == null) null else run)
    }

    private fun publish(run: RunState, force: Boolean) {
        val replacement = withRun(document, run)
        val now = monotonicMs()
        if (force || now - lastCheckpoint >= 5_000) {
            store!!.save(replacement)
            lastCheckpoint = now
        }
        document = replacement
        refresh()
    }

    private fun commit(replacement: BackupDocument) {
        store!!.save(replacement)
        document = replacement
        refresh()
    }

    private fun refresh() {
        mutableState.value = mutableState.value.copy(templates = document.templates,
            sessions = document.sessions, settings = document.settings, run = engine.state, loading = false)
    }

    private fun perform(block: () -> Unit) {
        worker.launch {
            ready.await()
            synchronized(gate) {
                if (closed) return@synchronized
                if (!initialized) { message("PC用データを開けなかったため保存できません。保存先と起動中の別ウィンドウを確認してください。"); return@synchronized }
                mutableState.value = mutableState.value.copy(busy = true)
                try { block() }
                catch (error: Exception) { fail(error) }
                finally { mutableState.value = mutableState.value.copy(busy = false) }
            }
        }
    }

    private fun fail(error: Exception) {
        if (engine.state.active && engine.state.stage != TimerStage.PAUSED) {
            val paused = engine.dispatch(TimerAction.Pause)
            document = withRun(document, paused)
            runCatching { store?.save(document) }
            refresh()
        }
        message(if (error is IllegalArgumentException) error.message ?: "入力内容を確認してください。"
            else "PC用ファイルを保存できないため一時停止しました。保存先と空き容量を確認してください。${error.message.orEmpty()}")
    }

    private fun message(text: String) { mutableState.value = mutableState.value.copy(message = text) }

    private fun exportBackup() {
        worker.launch {
            ready.await()
            try {
                val selected = chooseFile(save = true) ?: return@launch
                synchronized(gate) {
                    if (!initialized || closed) return@synchronized
                    val run = engine.tick(System.currentTimeMillis())
                    publish(run, true)
                    DesktopStore.writeDocument(selected, document)
                    message("PC版のバックアップを書き出しました。")
                }
            } catch (error: Exception) { synchronized(gate) { message(error.message ?: "バックアップを書き出せませんでした。") } }
        }
    }

    private fun pickImport() {
        worker.launch {
            ready.await()
            try {
                val selected = chooseFile(save = false) ?: return@launch
                val decoded = DesktopStore.readDocument(selected)
                val preview = BackupCodec.preview(decoded)
                synchronized(gate) {
                    if (!initialized || closed) return@synchronized
                    pendingImport = decoded
                    mutableState.value = mutableState.value.copy(importPreview = preview.summary)
                }
            } catch (error: Exception) { synchronized(gate) { message(error.message ?: "バックアップを読み込めませんでした。") } }
        }
    }

    private fun confirmImport() = perform {
        val imported = pendingImport ?: return@perform
        if (engine.state.active && engine.state.stage != TimerStage.PAUSED) publish(engine.dispatch(TimerAction.Pause), true)
        val before = engine.state
        try {
            val replacement = BackupCodec.forRestore(imported)
            val restored = engine.restore(replacement.runtime ?: RunState())
            val actual = if (replacement.runtime != null) withRun(replacement, restored) else replacement
            commit(actual)
            pendingImport = null
            mutableState.value = mutableState.value.copy(importPreview = null)
            message("PC版にバックアップを取り込みました。進行中の記録は一時停止しています。")
        } catch (error: Exception) {
            engine.restore(before)
            document = withRun(document, engine.state)
            refresh()
            throw error
        }
    }

    private fun chooseFile(save: Boolean): Path? {
        var result: Path? = null
        val dialog = Runnable {
            val chooser = JFileChooser(dataPath.toAbsolutePath().parent.toFile()).apply {
                dialogTitle = if (save) "PC版のバックアップを書き出す" else "つづトレのバックアップを選択"
                fileFilter = FileNameExtensionFilter("つづトレのバックアップ (*.json)", "json")
                if (save) selectedFile = java.io.File("tsuzutore-PC-${LocalDate.now()}.json")
            }
            val accepted = if (save) chooser.showSaveDialog(null) else chooser.showOpenDialog(null)
            if (accepted == JFileChooser.APPROVE_OPTION) {
                val candidate = chooser.selectedFile.toPath().toAbsolutePath().normalize()
                if (candidate == dataPath.toAbsolutePath().normalize() || candidate == dataPath.toAbsolutePath().normalize().resolveSibling(dataPath.fileName.toString() + ".lock")) {
                    JOptionPane.showMessageDialog(null, "PC版の保存ファイルとは別のファイルを選択してください。")
                } else if (!save || !Files.exists(candidate) || JOptionPane.showConfirmDialog(null,
                        "${candidate.fileName}を上書きしますか？", "バックアップの上書き", JOptionPane.YES_NO_OPTION) == JOptionPane.YES_OPTION) result = candidate
            }
        }
        if (EventQueue.isDispatchThread()) dialog.run() else EventQueue.invokeAndWait(dialog)
        return result
    }

    private fun beep() {
        if (document.settings.soundEnabled && !GraphicsEnvironment.isHeadless()) runCatching { Toolkit.getDefaultToolkit().beep() }
    }

    private fun emitCue(before: RunState, after: RunState) {
        val changed = before.session?.id != after.session?.id || before.stage != after.stage ||
            before.exerciseIndex != after.exerciseIndex || before.setNumber != after.setNumber || before.side != after.side
        if (changed) {
            previousCueSecond = null
            if (after.session != null && after.stage in setOf(TimerStage.WARMUP, TimerStage.READY, TimerStage.SIDE_SWITCH,
                    TimerStage.EXERCISING, TimerStage.RESTING, TimerStage.COMPLETED, TimerStage.INTERRUPTED)) beep()
        }
        val countdown = after.stage in setOf(TimerStage.WARMUP, TimerStage.PREPARING, TimerStage.RESTING) ||
            (after.stage == TimerStage.EXERCISING && after.exercise?.mode == ExerciseMode.TIME)
        val second = ceil(after.remainingMs / 1000.0).toLong()
        if (countdown && second in 1..3 && previousCueSecond != second) beep()
        previousCueSecond = if (countdown) second else null
    }

    /** Window close is synchronous so the final checkpoint finishes before the JVM exits. */
    fun close() {
        synchronized(gate) {
            if (closed) return
            closed = true
            job.cancel()
            try {
                if (initialized) {
                    val run = if (engine.state.active) engine.dispatch(TimerAction.Pause) else engine.state
                    publish(run, true)
                }
            } catch (error: Exception) {
                System.err.println("つづトレPC版: 最終保存に失敗しました: ${error.message}")
            } finally { store?.close() }
        }
    }

    private companion object { fun monotonicMs(): Long = System.nanoTime() / 1_000_000 }
}