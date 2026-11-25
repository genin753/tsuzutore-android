package jp.tsuzutore.app

import android.Manifest
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.SystemBarStyle
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
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.light(android.graphics.Color.rgb(245, 245, 242), android.graphics.Color.BLACK),
            navigationBarStyle = SystemBarStyle.dark(android.graphics.Color.rgb(17, 17, 17)),
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
                    if (Build.VERSION.SDK_INT >= 33) notificationLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                    else viewModel.notificationResult(ReminderScheduler.hasNotificationPermission(this))
                },
                exportBackup = { exportLauncher.launch("tsuzutore-${LocalDate.now()}.json") },
                pickImport = { importLauncher.launch(arrayOf("application/json", "text/*", "application/octet-stream")) },
                confirmImport = viewModel::confirmImport,
                cancelImport = viewModel::cancelImport,
                dismissMessage = viewModel::dismissMessage,
                confirmForm = viewModel::confirmForm,
            ))
        }
    }
}
