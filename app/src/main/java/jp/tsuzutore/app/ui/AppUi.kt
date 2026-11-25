package jp.tsuzutore.app.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import jp.tsuzutore.app.domain.*

data class AppUiState(
    val templates: List<MenuTemplate> = emptyList(),
    val sessions: List<WorkoutSession> = emptyList(),
    val settings: AppSettings = AppSettings(),
    val run: RunState = RunState(),
    val loading: Boolean = true,
    val busy: Boolean = false,
    val message: String? = null,
    val importPreview: String? = null,
    /** Whether the OS lets the app post notifications (reminders, running-timer notice). */
    val notificationsAllowed: Boolean = true,
)

data class AppUiActions(
    val startWorkout: (WorkoutPlan) -> Unit,
    val startQuickWorkout: (WorkoutPlan) -> Unit,
    val saveSchedule: (List<ScheduledWorkout>) -> Unit,
    val adjustTime: (Int) -> Unit,
    val selectExercise: (String) -> Unit,
    val beginSet: () -> Unit,
    val completeSet: () -> Unit,
    val finishTimedSet: () -> Unit,
    val pauseResume: () -> Unit,
    val extendRest: () -> Unit,
    val skipSet: () -> Unit,
    val skipExercise: () -> Unit,
    val skipWarmup: () -> Unit,
    val finishWorkout: () -> Unit,
    val saveTemplate: (MenuTemplate) -> Unit,
    val deleteTemplate: (String) -> Unit,
    val reorderTemplates: (List<String>) -> Unit,
    /** Receives a change applied to the latest stored settings, never a stale full copy. */
    val saveSettings: ((AppSettings) -> AppSettings) -> Unit,
    val previewCues: () -> Unit,
    val requestNotificationPermission: () -> Unit,
    val exportBackup: () -> Unit,
    val pickImport: () -> Unit,
    val confirmImport: () -> Unit,
    val cancelImport: () -> Unit,
    val dismissMessage: () -> Unit,
    val confirmForm: (String, Boolean) -> Unit,
    val completeSetWithReps: (Int) -> Unit = {},
    val openNotificationSettings: () -> Unit = {},
    val deleteSession: (String) -> Unit = {},
    /** sessionId, resultId, reps (null clears the recorded count). */
    val updateSetReps: (String, String, Int?) -> Unit = { _, _, _ -> },
)

private data class Destination(val label: String, val icon: ImageVector, val selectedIcon: ImageVector)

private val destinations = listOf(
    Destination("ホーム", Symbols.Home, Symbols.HomeFilled),
    Destination("メニュー", Symbols.FitnessCenter, Symbols.FitnessCenterFilled),
    Destination("カレンダー", Symbols.CalendarMonth, Symbols.CalendarMonthFilled),
    Destination("記録", Symbols.BarChart, Symbols.BarChartFilled),
    Destination("設定", Symbols.Settings, Symbols.SettingsFilled),
)

@Composable
fun TsuzuToreApp(state: AppUiState, actions: AppUiActions) {
    var page by rememberSaveable { mutableIntStateOf(0) }
    var showWorkout by rememberSaveable { mutableStateOf(false) }
    var quickStart by remember { mutableStateOf<MenuTemplate?>(null) }
    var preview by remember { mutableStateOf<MenuTemplate?>(null) }
    var editTemplate by remember { mutableStateOf<MenuTemplate?>(null) }
    var showInterval by remember { mutableStateOf(false) }
    val snackbar = remember { SnackbarHostState() }
    LaunchedEffect(state.run.session?.id) { if (state.run.active) showWorkout = true }
    LaunchedEffect(state.message) {
        state.message?.let { snackbar.showSnackbar(it); actions.dismissMessage() }
    }
    val workoutVisible = showWorkout && state.run.session != null && state.run.stage != TimerStage.IDLE
    val showNavigation = !workoutVisible && quickStart == null && preview == null && editTemplate == null && !showInterval
    TsuzuToreTheme {
        Scaffold(
            containerColor = MaterialTheme.colorScheme.surface,
            snackbarHost = { SnackbarHost(snackbar) },
            bottomBar = {
                if (showNavigation) {
                    NavigationBar {
                        destinations.forEachIndexed { index, destination ->
                            val selected = page == index
                            NavigationBarItem(
                                selected = selected,
                                onClick = { page = index },
                                icon = { Icon(if (selected) destination.selectedIcon else destination.icon, contentDescription = null) },
                                label = { Text(destination.label, maxLines = 1, softWrap = false) },
                            )
                        }
                    }
                }
            },
        ) { insets ->
            Box(Modifier.fillMaxSize().padding(insets), contentAlignment = Alignment.TopCenter) {
                when {
                    state.loading -> Column(
                        Modifier.align(Alignment.Center),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(TtSpace.Large),
                    ) {
                        CircularProgressIndicator()
                        Text("メニューを準備しています", style = MaterialTheme.typography.bodyLarge)
                    }
                    editTemplate != null -> {
                        AppBackHandler { editTemplate = null }
                        TemplateEditor(editTemplate!!, { editTemplate = null }) {
                            actions.saveTemplate(it)
                            editTemplate = null
                        }
                    }
                    quickStart != null -> {
                        AppBackHandler { quickStart = null }
                        QuickStartScreen(state, quickStart!!, { quickStart = null }, {
                            quickStart = null
                            showWorkout = true
                            actions.startQuickWorkout(it)
                        }, {
                            quickStart = null
                            preview = it
                        })
                    }
                    preview != null -> {
                        AppBackHandler { preview = null }
                        PlanPreview(preview!!, state.settings, state.busy, { preview = null }) {
                            preview = null
                            showWorkout = true
                            actions.startWorkout(it)
                        }
                    }
                    showInterval -> {
                        AppBackHandler { showInterval = false }
                        IntervalSetup(state.busy, { showInterval = false }) {
                            showInterval = false
                            showWorkout = true
                            actions.startWorkout(it)
                        }
                    }
                    workoutVisible -> WorkoutScreen(state, actions) { showWorkout = false }
                    else -> {
                        Column(
                            Modifier.widthIn(max = 720.dp).fillMaxSize().verticalScroll(rememberScrollState())
                                .padding(horizontal = TtSpace.Large, vertical = TtSpace.Large),
                            verticalArrangement = Arrangement.spacedBy(TtSpace.Large),
                        ) {
                            when (page) {
                                0 -> HomeScreen(state, { showWorkout = true }, { quickStart = it }, { showInterval = true }, { page = 1 }, { page = 2 })
                                1 -> MenuScreen(state, { quickStart = it }, { editTemplate = it }, actions)
                                2 -> CalendarScreen(state, actions.saveSchedule, { quickStart = it })
                                3 -> HistoryScreen(state, actions)
                                4 -> SettingsScreen(state, actions)
                            }
                            Spacer(Modifier.height(TtSpace.Small))
                        }
                    }
                }
                if (state.busy) {
                    LinearProgressIndicator(Modifier.fillMaxWidth().align(Alignment.TopCenter).semantics { contentDescription = "保存しています" })
                }
            }
        }
        state.importPreview?.let { previewText ->
            AlertDialog(
                onDismissRequest = actions.cancelImport,
                icon = { Icon(Symbols.Event, contentDescription = null) },
                title = { Text("バックアップを取り込みますか？") },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(TtSpace.Medium)) {
                        Text(previewText)
                        Text("現在のメニュー・設定・記録を、この内容に置き換えます。進行中の記録は一時停止として取り込みます。")
                    }
                },
                confirmButton = { TextButton(actions.confirmImport, enabled = !state.busy) { Text("置き換えて取り込む") } },
                dismissButton = { TextButton(actions.cancelImport) { Text("キャンセル") } },
            )
        }
    }
}
