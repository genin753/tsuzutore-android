package jp.tsuzutore.app

import android.Manifest
import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.runtime.*
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import jp.tsuzutore.app.domain.*
import jp.tsuzutore.app.platform.ReminderScheduler
import jp.tsuzutore.app.ui.AppUiActions
import jp.tsuzutore.app.ui.TsuzuToreApp
import java.time.LocalDate

class MainActivity : ComponentActivity() {
    private val viewModel: AppViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Transparent system bars with light icons: the app is always dark, even when the phone is in light mode.
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
        )
        setContent {
            val state by viewModel.state.collectAsStateWithLifecycle()
            val exportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
                uri?.let(viewModel::exportTo)
            }
            val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
                uri?.let(viewModel::previewImport)
            }
            val notificationLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission(), viewModel::notificationResult)
            val revision = state.run.revision
            TsuzuToreApp(state, AppUiActions(
                startWorkout = viewModel::startWorkout,
                startQuickWorkout = viewModel::startQuickWorkout,
                saveSchedule = viewModel::saveSchedule,
                adjustTime = { viewModel.command(TimerAction.AdjustTime(it), revision) },
                selectExercise = { viewModel.command(TimerAction.SelectExercise(it), revision) },
                beginSet = { viewModel.command(TimerAction.StartSet, revision) },
                completeSet = { viewModel.command(TimerAction.CompleteSet, revision) },
                finishTimedSet = { viewModel.command(TimerAction.FinishTimed(), revision) },
                pauseResume = { viewModel.command(if (state.run.stage == TimerStage.PAUSED) TimerAction.Resume else TimerAction.Pause, revision) },
                extendRest = { viewModel.command(TimerAction.ExtendRest(30), revision) },
                skipSet = { viewModel.command(TimerAction.SkipSet, revision) },
                skipExercise = { viewModel.command(TimerAction.SkipExercise, revision) },
                skipWarmup = { viewModel.command(TimerAction.SkipWarmup, revision) },
                finishWorkout = { viewModel.command(TimerAction.Stop(System.currentTimeMillis()), revision) },
                saveTemplate = viewModel::saveTemplate,
                deleteTemplate = viewModel::deleteTemplate,
                reorderTemplates = viewModel::reorderTemplates,
                saveSettings = viewModel::saveSettings,
                previewCues = viewModel::previewCues,
                requestNotificationPermission = {
                    when {
                        Build.VERSION.SDK_INT >= 33 && !ReminderScheduler.hasNotificationPermission(this) ->
                            notificationLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                        // Granted, but switched off for this app in system settings: only the settings screen can change it.
                        !ReminderScheduler.notificationsAllowed(this) -> openNotificationSettings()
                        else -> viewModel.notificationResult(true)
                    }
                },
                exportBackup = { exportLauncher.launch("tsuzutore-${LocalDate.now()}.json") },
                pickImport = { importLauncher.launch(arrayOf("application/json", "text/*", "application/octet-stream")) },
                confirmImport = viewModel::confirmImport,
                cancelImport = viewModel::cancelImport,
                dismissMessage = viewModel::dismissMessage,
                confirmForm = viewModel::confirmForm,
                completeSetWithReps = { viewModel.command(TimerAction.CompleteReps(it), revision) },
                openNotificationSettings = ::openNotificationSettings,
                deleteSession = viewModel::deleteSession,
                updateSetReps = viewModel::updateSetReps,
            ))
        }
    }

    override fun onResume() {
        super.onResume()
        viewModel.refreshNotificationPermission()
    }

    private fun openNotificationSettings() {
        val appNotifications = Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, packageName)
        try {
            startActivity(appNotifications)
        } catch (_: ActivityNotFoundException) {
            try {
                startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", packageName, null)))
            } catch (_: ActivityNotFoundException) {
                viewModel.message("端末の設定アプリから、つづトレの通知を許可してください。")
            }
        }
    }
}
