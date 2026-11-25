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
import jp.tsuzutore.app.ui.displaysSameAs
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*

class AppViewModel(application: Application) : AndroidViewModel(application) {
    private val app = application as TsuzuToreApplication
    private val repository = app.repository
    private val controller = app.controller
    private val mutableState = MutableStateFlow(AppUiState())
    val state: StateFlow<AppUiState> = mutableState.asStateFlow()
    private val notificationsAllowed = MutableStateFlow(ReminderScheduler.notificationsAllowed(app))
    private var pendingImport: String? = null
    private var operations = 0

    init {
        viewModelScope.launch {
            controller.awaitReady()
            // The engine is sampled 5 times a second; the screen changes at most once a second.
            val displayedRun = controller.run.distinctUntilChanged { old, new -> old.displaysSameAs(new) }
            combine(repository.templates, repository.sessions, repository.settings, displayedRun, notificationsAllowed) { templates, sessions, settings, run, allowed ->
                AppUiState(templates, sessions, settings, run, loading = false, notificationsAllowed = allowed)
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

    /** Applies the settings screen's edits to the latest stored settings (calendar plans stay intact). */
    fun saveSettings(change: (AppSettings) -> AppSettings) = perform {
        repository.updateSettings(change)
        val saved = repository.settings.first()
        ReminderScheduler.schedule(app, saved)
        refreshNotificationPermission()
        message(
            if (saved.reminderEnabled && !notificationsAllowed.value) "設定を保存しました。通知が許可されていないため、リマインドは届きません。"
            else "設定を保存しました。"
        )
    }

    fun confirmForm(id: String, confirmed: Boolean) = perform { controller.confirmForm(id, confirmed) }
    fun previewCues() { app.cues.preview(state.value.settings) }

    fun deleteSession(id: String) = perform {
        controller.deleteSession(id)
        message("記録を削除しました。")
    }

    fun updateSetReps(sessionId: String, resultId: String, reps: Int?) = perform {
        controller.updateSetReps(sessionId, resultId, reps)
        message(if (reps == null) "回数を未記録に戻しました。" else "回数を記録しました。")
    }

    /** Re-read on every resume: the user may change the permission in system settings at any time. */
    fun refreshNotificationPermission() {
        notificationsAllowed.value = ReminderScheduler.notificationsAllowed(app)
    }

    /** The permission answer never rewrites a setting; it only reschedules reminders and reports the result. */
    fun notificationResult(granted: Boolean) = perform {
        refreshNotificationPermission()
        ReminderScheduler.schedule(app, repository.settings.first())
        message(
            if (granted && notificationsAllowed.value) "通知を許可しました。リマインドと実行中タイマーの通知が表示されます。"
            else "通知は許可されていません。タイマーと記録は使えます。許可するときは「端末の通知設定を開く」から変更してください。"
        )
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
