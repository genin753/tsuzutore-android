package jp.tsuzutore.app.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.text.font.FontWeight
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
    TsuzuToreTheme {
        Scaffold(
            snackbarHost = { SnackbarHost(snackbar) },
            bottomBar = {
                if (!(showWorkout && state.run.session != null && state.run.stage != TimerStage.IDLE) && quickStart == null && preview == null && editTemplate == null && !showInterval) {
                    Column(Modifier.background(TtStyle.Dark)) {
                        HorizontalDivider(thickness = 2.dp, color = TtStyle.Accent)
                        NavigationBar(containerColor = TtStyle.Dark, tonalElevation = 0.dp) {
                            listOf("ホーム", "メニュー", "カレンダー", "記録", "設定").forEachIndexed { index, label ->
                                val selected = page == index
                                NavigationBarItem(
                                    selected = selected,
                                    onClick = { page = index },
                                    icon = {
                                        NavGlyph(
                                            index,
                                            if (selected) TtStyle.Ink else Color(0xFFB8B8B3),
                                            if (selected) TtStyle.Accent else TtStyle.Dark,
                                        )
                                    },
                                    label = { Text(label, style = MaterialTheme.typography.labelSmall, maxLines = 1, softWrap = false, fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium) },
                                    colors = NavigationBarItemDefaults.colors(
                                        selectedIconColor = TtStyle.Ink,
                                        selectedTextColor = TtStyle.Accent,
                                        indicatorColor = TtStyle.Accent,
                                        unselectedIconColor = Color(0xFFB8B8B3),
                                        unselectedTextColor = Color(0xFFB8B8B3),
                                    ),
                                )
                            }
                        }
                    }
                } else {
                    Spacer(
                        Modifier.fillMaxWidth()
                            .windowInsetsBottomHeight(WindowInsets.navigationBars)
                            .background(TtStyle.Dark),
                    )
                }
            },
        ) { insets ->
            Box(Modifier.fillMaxSize().padding(insets), contentAlignment = Alignment.TopCenter) {
                when {
                    state.loading -> Column(Modifier.align(Alignment.Center), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(16.dp)) {
                        CircularProgressIndicator()
                        Text("メニューを準備しています")
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
                    showWorkout && state.run.session != null && state.run.stage != TimerStage.IDLE -> {
                        WorkoutScreen(state, actions) { showWorkout = false }
                    }
                    else -> {
                        Column(
                            Modifier.widthIn(max = 720.dp).fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 20.dp),
                            verticalArrangement = Arrangement.spacedBy(20.dp),
                        ) {
                            when (page) {
                                0 -> HomeScreen(state, { showWorkout = true }, { quickStart = it }, { showInterval = true }, { page = 1 }, { page = 2 })
                                1 -> MenuScreen(state, { quickStart = it }, { editTemplate = it }, actions)
                                2 -> CalendarScreen(state, actions.saveSchedule, { quickStart = it })
                                3 -> HistoryScreen(state, actions)
                                4 -> SettingsScreen(state, actions)
                            }
                            Spacer(Modifier.height(12.dp))
                        }
                    }
                }
                if (state.busy) LinearProgressIndicator(Modifier.fillMaxWidth().align(Alignment.TopCenter).semantics { contentDescription = "保存しています" })
            }
        }
        state.importPreview?.let { previewText ->
            AlertDialog(
                onDismissRequest = actions.cancelImport,
                title = { Text("バックアップを取り込みますか？") },
                text = { Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(previewText)
                    Text("現在のメニュー・設定・記録を、この内容に置き換えます。進行中の記録は一時停止として取り込みます。")
                } },
                confirmButton = { TextButton(actions.confirmImport, enabled = !state.busy) { Text("置き換えて取り込む") } },
                dismissButton = { TextButton(actions.cancelImport) { Text("キャンセル") } },
            )
        }
    }
}

@Composable
private fun NavGlyph(index: Int, color: Color, background: Color) {
    Canvas(Modifier.size(24.dp)) {
        val s = size.width / 24f
        val stroke = Stroke(1.8f * s)
        fun p(x: Float, y: Float) = Offset(x * s, y * s)
        when (index) {
            0 -> {
                val roof = Path().apply { moveTo(3*s, 11*s); lineTo(12*s, 3*s); lineTo(21*s, 11*s) }
                drawPath(roof, color, style = stroke)
                val walls = Path().apply { moveTo(5*s, 10*s); lineTo(5*s, 21*s); lineTo(10*s, 21*s); lineTo(10*s, 15*s); lineTo(14*s, 15*s); lineTo(14*s, 21*s); lineTo(19*s, 21*s); lineTo(19*s, 10*s) }
                drawPath(walls, color, style = stroke)
            }
            1 -> for (y in listOf(5f, 12f, 19f)) {
                drawCircle(color, 1.4f*s, p(4f,y))
                drawLine(color, p(9f,y), p(21f,y), 1.8f*s)
            }
            2 -> {
                drawRoundRect(color, p(3f, 5f), Size(18 * s, 16 * s), CornerRadius(2 * s), style = stroke)
                drawLine(color, p(3f, 10f), p(21f, 10f), 1.8f * s)
                drawLine(color, p(8f, 2f), p(8f, 7f), 1.8f * s)
                drawLine(color, p(16f, 2f), p(16f, 7f), 1.8f * s)
                drawLine(color, p(8f, 15f), p(11f, 18f), 1.8f * s)
                drawLine(color, p(11f, 18f), p(17f, 12f), 1.8f * s)
            }
            3 -> {
                // Records: a bar chart, so it never looks like the calendar tab.
                drawLine(color, p(3f, 21f), p(21f, 21f), 1.8f * s)
                drawRoundRect(color, p(5f, 12f), Size(3.5f * s, 7f * s), CornerRadius(1f * s), style = stroke)
                drawRoundRect(color, p(10.25f, 4f), Size(3.5f * s, 15f * s), CornerRadius(1f * s), style = stroke)
                drawRoundRect(color, p(15.5f, 8f), Size(3.5f * s, 11f * s), CornerRadius(1f * s), style = stroke)
            }
            else -> {
                for ((y, x) in listOf(5f to 8f, 12f to 16f, 19f to 9f)) {
                    drawLine(color,p(2f,y),p(22f,y),1.8f*s)
                    drawCircle(background,3f*s,p(x,y))
                    drawCircle(color,3f*s,p(x,y),style=stroke)
                }
            }
        }
    }
}
