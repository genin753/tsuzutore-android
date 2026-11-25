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
import android.util.Log
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

/** Keeps a started workout alive in the background: notification and CPU wake lock. Cues live in [CueCoordinator]. */
class WorkoutService : Service() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate +
        CoroutineExceptionHandler { _, failure -> Log.w("TsuzuTore", "Workout service failure", failure) })
    private val app get() = application as TsuzuToreApplication
    private var wakeLock: PowerManager.WakeLock? = null
    private var lockToken: String? = null
    private var previous: RunState? = null
    private var notificationKey = ""
    private var lastStartId = 0

    override fun onCreate() {
        super.onCreate()
        ReminderScheduler.createChannels(this)
        enterForeground()
        scope.launch {
            app.controller.awaitReady()
            app.controller.run.combine(app.controller.settings) { state, settings -> state to settings }.collectLatest { (state, settings) ->
                try {
                    previous = state
                    maintainWakeLock(state)
                    if (state.session != null) updateNotification(state, settings)
                    if (!state.active) {
                        // Let the final cue finish before stopping. Starting another workout cancels this wait.
                        // A cleared run (its record was deleted) has nothing left to announce.
                        if (state.session != null) delay(FINISH_GRACE_MS)
                        releaseWakeLock()
                        stopForeground(STOP_FOREGROUND_REMOVE)
                        // Stops only if no newer start arrived meanwhile; that start brings the service back to the foreground.
                        stopSelfResult(lastStartId)
                    }
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    Log.w("TsuzuTore", "Workout service stopped after a failure", e)
                    releaseWakeLock()
                    app.controller.pauseForServiceShutdown()
                    stopSelf()
                }
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        lastStartId = startId
        if (intent?.action == ACTION_TOGGLE) {
            val revision = intent.getLongExtra(EXTRA_REVISION, -1)
            val paused = intent.getBooleanExtra(EXTRA_PAUSED, false)
            scope.launch { runCatching { app.controller.command(if (paused) TimerAction.Resume else TimerAction.Pause, revision) } }
        } else {
            // Every startForegroundService() must be answered with startForeground(), including one that
            // reaches this instance while it is about to stop after a finished workout.
            runCatching { enterForeground() }.onFailure { Log.w("TsuzuTore", "Could not keep the timer in the foreground", it) }
        }
        return START_NOT_STICKY
    }

    private fun enterForeground() {
        val type = if (Build.VERSION.SDK_INT >= 34) ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE else 0
        ServiceCompat.startForeground(this, NOTIFICATION_ID, notification(app.controller.run.value), type)
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        releaseWakeLock()
        val sessionId = previous?.session?.id
        val revision = previous?.revision
        if (app.controller.run.value.active && app.controller.run.value.revision == revision) app.cues.silence()
        scope.cancel()
        CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate).launch {
            runCatching { app.controller.pauseForServiceShutdown(sessionId, revision) }
        }
        super.onDestroy()
    }

    /**
     * Countdowns hold the CPU until their deadline. When one ends, the lock is kept briefly instead of
     * being released at once, so the cue for that transition is not cut off while the screen is off.
     */
    private fun maintainWakeLock(state: RunState) {
        val counting = state.active && (state.stage in setOf(TimerStage.WARMUP, TimerStage.PREPARING, TimerStage.RESTING) ||
            (state.stage == TimerStage.EXERCISING && state.exercise?.mode == ExerciseMode.TIME))
        when {
            counting -> {
                val token = "${state.session?.id}:${state.revision}"
                if (lockToken == token && wakeLock?.isHeld == true) return
                holdWakeLock((state.remainingMs + 15_000L).coerceIn(15_000L, 7_215_000L), token)
            }
            state.session == null || state.stage == TimerStage.PAUSED -> releaseWakeLock()
            lockToken != null && lockToken?.endsWith(GRACE_SUFFIX) != true ->
                holdWakeLock(CUE_GRACE_MS, "${state.session?.id}$GRACE_SUFFIX")
        }
    }

    private fun holdWakeLock(timeoutMs: Long, token: String) {
        val lock = wakeLock ?: getSystemService(PowerManager::class.java)
            .newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "tsuzutore:countdown")
            .apply { setReferenceCounted(false) }
        // Re-acquiring a non-counted lock replaces its timeout.
        lock.acquire(timeoutMs)
        wakeLock = lock
        lockToken = token
    }

    private fun releaseWakeLock() {
        wakeLock?.let { if (it.isHeld) it.release() }
        lockToken = null
    }

    private fun updateNotification(state: RunState, settings: AppSettings) {
        val key = "${state.session?.id}:${state.revision}:${ceil(state.remainingMs / 1000.0).toLong()}:${settings.notificationsEnabled}"
        if (key == notificationKey) return
        notificationKey = key
        if (ReminderScheduler.hasNotificationPermission(this)) {
            getSystemService(NotificationManager::class.java).notify(NOTIFICATION_ID, notification(state))
        }
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
                TimerStage.COMPLETED -> "おつかれさまでした。記録を保存しました"
                TimerStage.INTERRUPTED -> "ここまでの記録を保存しました"
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
        private const val GRACE_SUFFIX = ":cue-grace"
        private const val CUE_GRACE_MS = 8_000L
        private const val FINISH_GRACE_MS = 5_000L
        fun start(context: Context) { ContextCompat.startForegroundService(context, Intent(context, WorkoutService::class.java)) }
        fun stop(context: Context) { context.stopService(Intent(context, WorkoutService::class.java)) }
    }
}
