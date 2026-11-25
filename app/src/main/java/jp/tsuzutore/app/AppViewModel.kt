package jp.tsuzutore.app

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import jp.tsuzutore.app.data.BackupCodec
import jp.tsuzutore.app.domain.*
import jp.tsuzutore.app.platform.ReminderScheduler
import jp.tsuzutore.app.platform.WorkoutService
import jp.tsuzutore.app.ui.AppUiState
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*

class AppViewModel(application: Application) : AndroidViewModel(application) {
    private val app = application as TsuzuToreApplication
    private val repository = app.repository
    private val controller = app.controller
    private val mutableState = MutableStateFlow(AppUiState())
    val state: StateFlow<AppUiState> = mutableState.asStateFlow()
    private var pendingImport: String? = null
    private var operations = 0

    init {
        viewModelScope.launch {
            controller.awaitReady()
            combine(repository.templates, repository.sessions, repository.settings, controller.run) { templates, sessions, settings, run ->
                AppUiState(templates, sessions, settings, run, loading = false)
            }.catch { message("記録を読み込めませんでした。アプリを開き直してください。"); mutableState.update { it.copy(loading = false) } }
                .collect { data -> mutableState.update { data.copy(busy = it.busy, message = it.message, importPreview = it.importPreview) } }
        }
        viewModelScope.launch { controller.error.filterNotNull().collect { message(it) } }
    }

    fun startWorkout(plan: WorkoutPlan) = perform {
        if (controller.start(plan)) startServiceSafely()
        else message("進行中のワークアウトに戻ってください。")
    }

    fun startQuickWorkout(plan: WorkoutPlan) = perform {
        if (controller.start(plan, startImmediately = true)) startServiceSafely()
        else message("進行中のワークアウトに戻ってください。")
    }

    fun saveSchedule(schedule: List<ScheduledWorkout>) = perform {
        repository.updateSettings { it.copy(scheduledWorkouts = sortedSchedule(schedule)) }
        ReminderScheduler.schedule(app, repository.settings.first())
        message("トレーニングの予定を保存しました。")
    }

    fun command(action: TimerAction, revision: Long) = perform {
        val changed = controller.command(action, revision)
        if (changed && controller.run.value.active) startServiceSafely()
    }

    fun saveTemplate(template: MenuTemplate) = perform { repository.saveTemplate(template); message("メニューを保存しました。") }
    fun deleteTemplate(id: String) = perform { repository.deleteTemplate(id); message("メニューを削除しました。") }
    fun reorderTemplates(ids: List<String>) = perform { repository.reorderTemplates(ids) }
    fun saveSettings(settings: AppSettings) = perform {
        repository.updateSettings { settings }
        ReminderScheduler.schedule(app, repository.settings.first())
        message("設定を保存しました。")
    }
    fun confirmForm(id: String, confirmed: Boolean) = perform { controller.confirmForm(id, confirmed) }
    fun previewCues() { app.cues.preview(state.value.settings) }

    fun notificationResult(granted: Boolean) = perform {
        repository.updateSettings { it.copy(notificationsEnabled = granted) }
        ReminderScheduler.schedule(app, repository.settings.first())
        message(if (granted) "通知を有効にしました。" else "通知は許可されていません。画面上のタイマーや記録は利用できます。")
    }

    fun exportTo(uri: Uri) = perform {
        val text = repository.exportBackup()
        withContext(Dispatchers.IO) {
            val output = app.contentResolver.openOutputStream(uri, "wt") ?: error("ファイルを開けませんでした。")
            output.bufferedWriter(Charsets.UTF_8).use { it.write(text) }
        }
        message("バックアップを書き出しました。")
    }

    fun previewImport(uri: Uri) = perform {
        val text = withContext(Dispatchers.IO) {
            val input = app.contentResolver.openInputStream(uri) ?: error("ファイルを開けませんでした。")
            input.use {
                val bytes = java.io.ByteArrayOutputStream()
                val buffer = ByteArray(8192)
                var count = it.read(buffer)
                while (count != -1) {
                    require(bytes.size() + count <= BackupCodec.MAX_BYTES) { "バックアップは8MB以内のファイルを選んでください。" }
                    bytes.write(buffer, 0, count)
                    count = it.read(buffer)
                }
                bytes.toString("UTF-8")
            }
        }
        val preview = withContext(Dispatchers.Default) { repository.previewImport(text) }
        pendingImport = text
        mutableState.update { it.copy(importPreview = preview.summary) }
    }

    fun confirmImport() = perform {
        val text = pendingImport ?: return@perform
        WorkoutService.stop(app)
        controller.replaceFromBackup(text)
        pendingImport = null
        mutableState.update { it.copy(importPreview = null) }
        ReminderScheduler.schedule(app, repository.settings.first())
        message("バックアップを取り込みました。進行中の記録は一時停止しています。")
    }

    fun cancelImport() { pendingImport = null; mutableState.update { it.copy(importPreview = null) } }
    fun dismissMessage() { mutableState.update { it.copy(message = null) }; controller.clearError() }
    fun message(text: String) { mutableState.update { it.copy(message = text) } }

    private fun startServiceSafely() {
        try { WorkoutService.start(app) }
        catch (_: Exception) { message("バックグラウンド動作を開始できませんでした。画面を開いた状態で利用してください。") }
    }

    private fun perform(block: suspend () -> Unit) {
        viewModelScope.launch {
            operations++
            mutableState.update { it.copy(busy = true) }
            try { block() }
            catch (e: CancellationException) { throw e }
            catch (e: Exception) {
                android.util.Log.e("TsuzuTore", "Operation failed", e)
                message(if (e is IllegalArgumentException) e.message ?: "入力内容を確認してください。"
                    else if (e is java.io.IOException) "ファイルを読み書きできませんでした。保存先と空き容量を確認してください。"
                    else "処理できませんでした。もう一度お試しください。")
            }
            finally {
                operations--
                mutableState.update { it.copy(busy = operations > 0) }
            }
        }
    }
}
