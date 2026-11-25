package jp.tsuzutore.app.platform

import android.Manifest
import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import jp.tsuzutore.app.MainActivity
import jp.tsuzutore.app.R
import jp.tsuzutore.app.TsuzuToreApplication
import jp.tsuzutore.app.domain.AppSettings
import jp.tsuzutore.app.domain.PlanKind
import jp.tsuzutore.app.domain.wasPerformed
import java.time.LocalDate
import java.time.ZonedDateTime
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

object ReminderScheduler {
    const val CHANNEL = "habit_reminders"
    private const val DATE_EXTRA = "scheduled_local_date"
    private val deliveryMutex = Mutex()

    fun hasNotificationPermission(context: Context): Boolean =
        Build.VERSION.SDK_INT < 33 || ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED

    /** Permission granted and notifications not switched off for the app in system settings. */
    fun notificationsAllowed(context: Context): Boolean =
        hasNotificationPermission(context) && NotificationManagerCompat.from(context).areNotificationsEnabled()

    fun createChannels(context: Context) {
        val manager = context.getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(NotificationChannel(CHANNEL, "トレーニングの予定", NotificationManager.IMPORTANCE_DEFAULT).apply {
            description = "設定した予定日に、運動を始めるきっかけをお知らせします"
        })
        manager.createNotificationChannel(NotificationChannel(WorkoutService.CHANNEL, "実行中のタイマー", NotificationManager.IMPORTANCE_LOW).apply {
            description = "進行中の種目と一時停止操作を表示します"
            setSound(null, null)
        })
    }

    /** One inexact local alarm; no exact-alarm permission, WorkManager or polling. */
    fun schedule(context: Context, settings: AppSettings, now: ZonedDateTime = ZonedDateTime.now()) {
        val alarms = context.getSystemService(AlarmManager::class.java)
        val intent = Intent(context, ReminderReceiver::class.java)
        val pending = PendingIntent.getBroadcast(context, 701, intent, PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE)
        if (pending != null) alarms.cancel(pending)
        if (!settings.reminderEnabled || (settings.plannedDays.isEmpty() && settings.scheduledWorkouts.isEmpty()) || !hasNotificationPermission(context)) return
        val target = nextReminderTime(settings, now) ?: return
        val next = PendingIntent.getBroadcast(context, 701,
            intent.putExtra(DATE_EXTRA, target.toLocalDate().toString()), PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        alarms.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, target.toInstant().toEpochMilli(), next)
    }

    suspend fun deliver(context: Context, scheduledDate: String?) {
        val app = context.applicationContext as TsuzuToreApplication
        val settings = app.repository.settings.first()
        try {
            deliveryMutex.withLock {
                val today = LocalDate.now()
                val isPlannedDay = today.dayOfWeek.value in settings.plannedDays || settings.scheduledWorkouts.any { it.date == today.toString() }
                if (!settings.reminderEnabled || today.toString() != scheduledDate || !isPlannedDay || !hasNotificationPermission(context)) return@withLock
                if (app.repository.sessions.first().any { it.plan.kind == PlanKind.WORKOUT && it.localDate == today.toString() && it.wasPerformed() }) return@withLock
                val ledger = context.getSharedPreferences("reminder_delivery", Context.MODE_PRIVATE)
                if (ledger.getString("last_date", null) == today.toString()) return@withLock
                createChannels(context)
                val open = PendingIntent.getActivity(context, 700, Intent(context, MainActivity::class.java), PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
                val notification = NotificationCompat.Builder(context, CHANNEL)
                    .setSmallIcon(R.drawable.ic_notification)
                    .setContentTitle("今日のつづトレ")
                    .setContentText(settings.cueMemo.ifBlank { "都合のよいタイミングで、今日のメニューを開いてみませんか" })
                    .setContentIntent(open).setAutoCancel(true).build()
                context.getSystemService(NotificationManager::class.java).notify(702, notification)
                ledger.edit().putString("last_date", today.toString()).apply()
            }
        } finally { schedule(context, settings) }
    }

    fun scheduledDate(intent: Intent): String? = intent.getStringExtra(DATE_EXTRA)
}

/** Calendar scheduling is independently testable, including DST and local date boundaries. */
fun nextReminderTime(settings: AppSettings, now: ZonedDateTime): ZonedDateTime? {
    val weekly = (0L..7L).asSequence().map { days ->
        now.toLocalDate().plusDays(days).atTime(settings.reminderHour, settings.reminderMinute).atZone(now.zone)
    }.filter { it.dayOfWeek.value in settings.plannedDays }
    val dated = settings.scheduledWorkouts.asSequence().mapNotNull { event ->
        val minutes = event.timeMinutes ?: (settings.reminderHour * 60 + settings.reminderMinute)
        runCatching {
            LocalDate.parse(event.date).atTime(minutes / 60, minutes % 60).atZone(now.zone)
        }.getOrNull()
    }
    return (weekly + dated).filter { it.isAfter(now) }.minByOrNull { it.toInstant() }
}

class ReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val pending = goAsync()
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try { runCatching { ReminderScheduler.deliver(context, ReminderScheduler.scheduledDate(intent)) }
                .onFailure { android.util.Log.w("TsuzuTore", "Reminder unavailable", it) } }
            finally { pending.finish() }
        }
    }
}

class ScheduleChangeReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action !in setOf(Intent.ACTION_BOOT_COMPLETED, Intent.ACTION_TIME_CHANGED, Intent.ACTION_TIMEZONE_CHANGED, Intent.ACTION_MY_PACKAGE_REPLACED)) return
        val pending = goAsync()
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                val app = context.applicationContext as TsuzuToreApplication
                runCatching { ReminderScheduler.schedule(context, app.repository.settings.first()) }
                    .onFailure { android.util.Log.w("TsuzuTore", "Reminder rescheduling unavailable", it) }
                // Never start a workout service or resume a timer from a boot/time broadcast.
            } finally { pending.finish() }
        }
    }
}
