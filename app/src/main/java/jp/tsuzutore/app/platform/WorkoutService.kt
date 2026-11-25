package jp.tsuzutore.app.platform

import android.app.Notification
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import jp.tsuzutore.app.MainActivity
import jp.tsuzutore.app.R
import jp.tsuzutore.app.TsuzuToreApplication
import jp.tsuzutore.app.domain.*
import kotlin.math.ceil
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine

class WorkoutService : Service() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val app get() = application as TsuzuToreApplication
    private var wakeLock: PowerManager.WakeLock? = null
    private var lockToken: String? = null
    private var previous: RunState? = null
    private var previousSecond: Long? = null
    private var notificationKey = ""

    override fun onCreate() {
        super.onCreate()
        ReminderScheduler.createChannels(this)
        val type = if (Build.VERSION.SDK_INT >= 34) ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE else 0
        ServiceCompat.startForeground(this, NOTIFICATION_ID, notification(app.controller.run.value), type)
        scope.launch {
            app.controller.awaitReady()
            app.controller.run.combine(app.controller.settings) { state, settings -> state to settings }.collectLatest { (state, settings) ->
                try {
                    maintainWakeLock(state)
                    emitCues(state)
                    if (state.session != null && !state.active) {
                        releaseWakeLock()
                        stopForeground(STOP_FOREGROUND_REMOVE)
                        stopSelf()
                    } else {
                        val key = "${state.session?.id}:${state.revision}:${ceil(state.remainingMs / 1000.0).toLong()}:${settings.notificationsEnabled}"
                        if (key != notificationKey) {
                            notificationKey = key
                            if (ReminderScheduler.hasNotificationPermission(this@WorkoutService))
                                getSystemService(NotificationManager::class.java).notify(NOTIFICATION_ID, notification(state))
                        }
                    }
                } catch (e: Exception) {
                    releaseWakeLock()
                    app.controller.pauseForServiceShutdown()
                    stopSelf()
                }
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_TOGGLE) {
            val revision = intent.getLongExtra(EXTRA_REVISION, -1)
            val paused = intent.getBooleanExtra(EXTRA_PAUSED, false)
            scope.launch { runCatching { app.controller.command(if (paused) TimerAction.Resume else TimerAction.Pause, revision) } }
        }
        return START_NOT_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        releaseWakeLock()
        val sessionId = previous?.session?.id
        val revision = previous?.revision
        if (app.controller.run.value.active && app.controller.run.value.revision == revision) app.cues.silence()
        scope.cancel()
        CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate).launch { app.controller.pauseForServiceShutdown(sessionId, revision) }
        super.onDestroy()
    }

    private fun maintainWakeLock(state: RunState) {
        val counting = state.active && (state.stage in setOf(TimerStage.WARMUP, TimerStage.PREPARING, TimerStage.RESTING) ||
            (state.stage == TimerStage.EXERCISING && state.exercise?.mode == ExerciseMode.TIME))
        if (!counting) { releaseWakeLock(); return }
        val token = "${state.session?.id}:${state.revision}"
        if (lockToken == token && wakeLock?.isHeld == true) return
        releaseWakeLock()
        val manager = getSystemService(PowerManager::class.java)
        wakeLock = manager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "tsuzutore:countdown").apply {
            setReferenceCounted(false)
            acquire((state.remainingMs + 15_000L).coerceIn(15_000L, 7_215_000L))
        }
        lockToken = token
    }

    private fun releaseWakeLock() {
        wakeLock?.let { if (it.isHeld) it.release() }
        wakeLock = null
        lockToken = null
    }

    private fun emitCues(state: RunState) {
        val old = previous
        val settings = app.controller.settings.value
        val changed = old == null || old.session?.id != state.session?.id || old.stage != state.stage ||
            old.exerciseIndex != state.exerciseIndex || old.setNumber != state.setNumber || old.side != state.side
        if (changed && state.stage == TimerStage.PAUSED) app.cues.silence()
        if (changed && state.session != null && state.stage != TimerStage.PAUSED) {
            val text = when (state.stage) {
                TimerStage.WARMUP -> "準備運動を始めましょう"
                TimerStage.READY -> "${state.exercise?.name.orEmpty()}。準備ができたら開始してください"
                TimerStage.SIDE_SWITCH -> "右側へ切り替えましょう。準備ができたら開始してください"
                TimerStage.EXERCISING -> "開始"
                TimerStage.RESTING -> "セット終了。休憩しましょう"
                TimerStage.COMPLETED -> "おつかれさまでした。記録を保存しました"
                TimerStage.INTERRUPTED -> "ここまでの記録を保存しました"
                else -> null
            }
            text?.let { app.cues.play(it, settings) }
            previousSecond = null
        }
        val countdownStage = state.stage in setOf(TimerStage.WARMUP, TimerStage.PREPARING, TimerStage.RESTING) ||
            (state.stage == TimerStage.EXERCISING && state.exercise?.mode == ExerciseMode.TIME)
        val second = ceil(state.remainingMs / 1000.0).toLong()
        if (countdownStage && second in 1..3 && previousSecond != second) app.cues.play(second.toString(), settings, true)
        previousSecond = if (countdownStage) second else null
        previous = state
    }

    private fun notification(state: RunState): Notification {
        val open = PendingIntent.getActivity(this, 600, Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val builder = NotificationCompat.Builder(this, CHANNEL)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(state.exercise?.name ?: "つづトレ")
            .setContentText(when (state.stage) {
                TimerStage.PAUSED -> "一時停止中"
                TimerStage.RESTING -> "休憩 · 残り${ceil(state.remainingMs / 1000.0).toLong()}秒"
                TimerStage.WARMUP -> "準備運動 · 残り${ceil(state.remainingMs / 1000.0).toLong()}秒"
                TimerStage.EXERCISING -> "${state.setNumber}セット目 · 実施中"
                TimerStage.SIDE_SWITCH -> "右側への切り替え待ち"
                TimerStage.READY -> "準備ができたらアプリで開始"
                TimerStage.PREPARING -> "開始まで${ceil(state.remainingMs / 1000.0).toLong()}秒"
                else -> "タイマーを準備しています"
            })
            .setContentIntent(open).setOngoing(true).setOnlyAlertOnce(true).setSilent(true)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
        if (state.active && app.controller.settings.value.notificationsEnabled) {
            val paused = state.stage == TimerStage.PAUSED
            val toggle = PendingIntent.getService(this, 601, Intent(this, WorkoutService::class.java).apply {
                action = ACTION_TOGGLE
                putExtra(EXTRA_REVISION, state.revision)
                putExtra(EXTRA_PAUSED, paused)
            }, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
            builder.addAction(0, if (paused) "再開" else "一時停止", toggle)
        }
        return builder.build()
    }

    companion object {
        const val CHANNEL = "workout_timer"
        private const val NOTIFICATION_ID = 600
        private const val ACTION_TOGGLE = "jp.tsuzutore.app.TOGGLE_TIMER"
        private const val EXTRA_REVISION = "revision"
        private const val EXTRA_PAUSED = "paused"
        fun start(context: Context) { ContextCompat.startForegroundService(context, Intent(context, WorkoutService::class.java)) }
        fun stop(context: Context) { context.stopService(Intent(context, WorkoutService::class.java)) }
    }
}
