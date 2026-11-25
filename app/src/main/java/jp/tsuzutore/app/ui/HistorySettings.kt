package jp.tsuzutore.app.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import jp.tsuzutore.app.domain.*
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/** History shows this many days at first and grows on request, so long histories stay fast. */
private const val HISTORY_PAGE_DAYS = 20

private fun historyActualText(result: SetResult): String = when {
    result.outcome == SetOutcome.SKIPPED -> "スキップ"
    result.mode == ExerciseMode.TIME -> "${result.elapsedMs / 1000}秒"
    result.actualReps != null -> "${result.actualReps}回"
    result.outcome == SetOutcome.COMPLETE -> "完了"
    else -> "回数の記録なし"
}

private fun dateLabel(date: String): String = runCatching { LocalDate.parse(date).format(japaneseDate) }.getOrDefault(date)

private fun sideLabel(side: Side): String = when (side) {
    Side.LEFT -> "・左"
    Side.RIGHT -> "・右"
    else -> ""
}

@Composable
internal fun HistoryScreen(state: AppUiState, actions: AppUiActions) {
    var filter by remember { mutableIntStateOf(0) }
    var expanded by remember { mutableStateOf<String?>(null) }
    var showTrends by remember { mutableStateOf(false) }
    var visibleDays by remember { mutableIntStateOf(HISTORY_PAGE_DAYS) }
    var deleting by remember { mutableStateOf<WorkoutSession?>(null) }
    var editing by remember { mutableStateOf<Pair<WorkoutSession, SetResult>?>(null) }
    // Regrouping every record is redone only when the records or the filter change.
    val groups = remember(state.sessions, filter) {
        state.sessions.sortedByDescending { it.startedAt }
            .filter { when (filter) { 1 -> it.plan.kind == PlanKind.WORKOUT; 2 -> it.plan.kind == PlanKind.INTERVAL; else -> true } }
            .groupBy { it.localDate }
            .toList()
    }
    val hasWorkouts = remember(state.sessions) { state.sessions.any { it.plan.kind == PlanKind.WORKOUT && it.hasPerformed() } }
    PageTitle("記録", "できた分を残して、次のトレーニングの目安に。")
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        listOf("すべて", "筋トレ", "自由タイマー").forEachIndexed { index, label ->
            FilterChip(
                filter == index,
                { filter = index; visibleDays = HISTORY_PAGE_DAYS },
                { Text(label) },
                modifier = Modifier.heightIn(min = TtStyle.Target),
            )
        }
    }
    SubButton(if (showTrends) "種目ごとの推移を閉じる" else "種目ごとの推移・前回比較", { showTrends = !showTrends }, Modifier.fillMaxWidth(), hasWorkouts)
    if (showTrends) ExerciseTrends(state.sessions)
    if (groups.isEmpty()) TtCard(Modifier.fillMaxWidth()) {
        Text("まだ記録がありません", style = MaterialTheme.typography.titleMedium)
        HelpText("トレーニングを始めると、日付ごとに実績が並びます。短いメニューも記録できます。")
    }
    groups.take(visibleDays).forEach { (date, dateSessions) ->
        Text(dateLabel(date), style = MaterialTheme.typography.titleMedium)
        dateSessions.forEach { session ->
            val running = state.run.active && state.run.session?.id == session.id
            TtCard(Modifier.fillMaxWidth()) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(session.plan.title, style = MaterialTheme.typography.titleMedium)
                        Text(sessionLabel(session), color = TtStyle.Blue, style = MaterialTheme.typography.labelLarge)
                    }
                    Text(
                        runCatching {
                            Instant.ofEpochMilli(session.startedAt).atZone(ZoneId.of(session.zoneId)).format(DateTimeFormatter.ofPattern("HH:mm"))
                        }.getOrDefault(""),
                        color = TtStyle.Muted,
                    )
                }
                Text("実施 ${session.results.count { it.isPerformed() }}件 · スキップ ${session.results.count { it.outcome == SetOutcome.SKIPPED }}件")
                SubButton(
                    if (expanded == session.id) "詳細を閉じる" else "実績を見る",
                    { expanded = if (expanded == session.id) null else session.id },
                    Modifier.fillMaxWidth(),
                )
                if (expanded == session.id) {
                    SessionDetails(session, running, state, actions, editReps = { result -> editing = session to result })
                    HorizontalDivider()
                    if (running) {
                        HelpText("進行中の記録は、トレーニングの終了後に削除・修正できます。")
                    } else {
                        TextButton(
                            { deleting = session },
                            Modifier.align(Alignment.End).heightIn(min = TtStyle.Target),
                            enabled = !state.busy,
                        ) { Text("この記録を削除", color = TtStyle.Red) }
                    }
                }
            }
        }
    }
    if (groups.size > visibleDays) {
        SubButton("さらに表示（残り${groups.size - visibleDays}日分）", { visibleDays += HISTORY_PAGE_DAYS }, Modifier.fillMaxWidth())
    }
    deleting?.let { session ->
        AlertDialog(
            onDismissRequest = { deleting = null },
            title = { Text("この記録を削除しますか？") },
            text = { Text("${dateLabel(session.localDate)}「${session.plan.title}」の記録を削除します。削除すると元に戻せません。") },
            confirmButton = {
                TextButton({
                    actions.deleteSession(session.id)
                    if (expanded == session.id) expanded = null
                    deleting = null
                }) { Text("削除する", color = TtStyle.Red) }
            },
            dismissButton = { TextButton({ deleting = null }) { Text("キャンセル") } },
        )
    }
    editing?.let { (session, result) ->
        RepsDialog(session, result, state.sessions, state.busy, { editing = null }) { reps ->
            actions.updateSetReps(session.id, result.id, reps)
            editing = null
        }
    }
}

@Composable
private fun SessionDetails(
    session: WorkoutSession,
    running: Boolean,
    state: AppUiState,
    actions: AppUiActions,
    editReps: (SetResult) -> Unit,
) {
    HelpText("開始日のタイムゾーン：${session.zoneId}")
    if (session.plan.firstWeek) HelpText("初週モードで実施")
    if (session.plan.shortened) HelpText("その日の短縮内容を保存しています")
    if (session.results.isEmpty()) HelpText("確定されたセットはありません。")
    session.plan.exercises.forEach { exercise ->
        val results = session.results.filter { it.exerciseId == exercise.id }.sortedWith(compareBy({ it.setNumber }, { it.side.ordinal }))
        if (results.isNotEmpty()) {
            HorizontalDivider()
            Text(exercise.name, fontWeight = FontWeight.Bold)
            HelpText("開始時の目標：${targetText(exercise)} × ${exercise.sets}セット")
            results.forEach { result ->
                val editable = !running && result.mode == ExerciseMode.REPS && result.outcome == SetOutcome.COMPLETE
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "${result.setNumber}セット${sideLabel(result.side)}：${historyActualText(result)}" +
                            if (result.outcome == SetOutcome.PARTIAL) "（途中終了）" else "",
                        Modifier.weight(1f),
                    )
                    if (editable) {
                        TextButton({ editReps(result) }, Modifier.heightIn(min = TtStyle.Target), enabled = !state.busy) {
                            Text(if (result.actualReps == null) "回数を入力" else "回数を修正")
                        }
                    }
                }
                if (result.load.isNotBlank()) HelpText("負荷：${result.load} · ${result.difficulty}")
            }
        }
    }
    val hasRepCounts = session.results.any { it.isPerformed() && it.mode == ExerciseMode.REPS && it.actualReps != null }
    if (session.plan.kind == PlanKind.WORKOUT && hasRepCounts) {
        SettingSwitch("フォームを保てた", "負荷を上げる目安に使います。", session.formConfirmed, { actions.confirmForm(session.id, it) })
    } else if (session.plan.kind == PlanKind.WORKOUT && !running && session.results.any { it.mode == ExerciseMode.REPS && it.outcome == SetOutcome.COMPLETE }) {
        HelpText("回数を入力すると、前回比較と負荷の目安が使えるようになります。")
    }
}

@Composable
private fun RepsDialog(
    session: WorkoutSession,
    result: SetResult,
    history: List<WorkoutSession>,
    busy: Boolean,
    dismiss: () -> Unit,
    save: (Int?) -> Unit,
) {
    val exercise = session.plan.exercises.find { it.id == result.exerciseId }
    var reps by remember(result.id) {
        mutableIntStateOf(result.actualReps ?: exercise?.let { suggestedReps(it, session, history) } ?: 1)
    }
    AlertDialog(
        onDismissRequest = dismiss,
        title = { Text("${result.exerciseName} · ${result.setNumber}セット${sideLabel(result.side)}の回数") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                NumberStepper("回数", reps, 1, Limits.TARGET) { reps = it }
                HelpText("入力した回数は、前回比較と負荷の目安に使います。")
                if (result.actualReps != null) {
                    TextButton({ save(null) }, Modifier.heightIn(min = TtStyle.Target), enabled = !busy) { Text("未記録に戻す") }
                }
            }
        },
        confirmButton = { TextButton({ save(reps) }, enabled = !busy) { Text("保存") } },
        dismissButton = { TextButton(dismiss) { Text("キャンセル") } },
    )
}

@Composable
private fun ExerciseTrends(sessions: List<WorkoutSession>) {
    val workouts = remember(sessions) { sessions.filter { it.plan.kind == PlanKind.WORKOUT && it.hasPerformed() }.sortedByDescending { it.startedAt } }
    val exercises = remember(workouts) { workouts.flatMap { it.plan.exercises }.distinctBy { it.id } }
    Text("種目ごとの推移", style = MaterialTheme.typography.titleLarge)
    exercises.forEach { exercise ->
        val appearances = workouts.filter { session -> session.results.any { it.exerciseId == exercise.id && it.isPerformed() } }.take(3)
        if (appearances.isNotEmpty()) TtCard(Modifier.fillMaxWidth()) {
            Text(exercise.name, style = MaterialTheme.typography.titleMedium)
            HelpText("直近${appearances.size}回（新しい順）")
            appearances.forEach { session ->
                val results = session.results.filter { it.exerciseId == exercise.id && it.isPerformed() }.sortedWith(compareBy({ it.setNumber }, { it.side.ordinal }))
                Text(dateLabel(session.localDate), fontWeight = FontWeight.Bold)
                Text(results.joinToString(" / ") {
                    "${if (it.side == Side.LEFT) "左" else if (it.side == Side.RIGHT) "右" else ""}${historyActualText(it)}${if (it.outcome == SetOutcome.PARTIAL) "途中" else ""}"
                })
                if (results.any { it.actualReps != null || it.load.isNotBlank() }) {
                    HelpText("負荷：${results.map { it.load.ifBlank { "記録なし" } }.distinct().joinToString("、")} · ${results.map { it.difficulty }.distinct().joinToString("、")}")
                }
            }
            if (appearances.size >= 2) {
                val latest = appearances[0].results.filter { it.exerciseId == exercise.id && it.isPerformed() }
                val previous = appearances[1].results.filter { it.exerciseId == exercise.id && it.isPerformed() }
                val hasRepCounts = (latest + previous).none { it.mode == ExerciseMode.REPS && it.actualReps == null }
                if (hasRepCounts && latest.size == previous.size && latest.map { it.mode } == previous.map { it.mode }) {
                    val repsMode = latest.all { it.mode == ExerciseMode.REPS }
                    val delta = if (repsMode) {
                        latest.sumOf { requireNotNull(it.actualReps) } - previous.sumOf { requireNotNull(it.actualReps) }
                    } else ((latest.sumOf { it.elapsedMs } - previous.sumOf { it.elapsedMs }) / 1000).toInt()
                    Text("前回との合計差：${if (delta > 0) "+" else ""}$delta${if (repsMode) "回" else "秒"}", color = TtStyle.Blue)
                    HelpText("負荷・難度・フォームもあわせて振り返ってください。")
                } else if (hasRepCounts) {
                    HelpText("セット構成が異なるため、上の実績で比較してください。")
                } else {
                    HelpText("回数を記録したセット同士で、前回との差を表示します。")
                }
            }
            if (progressionSuggested(workouts, exercise.id)) {
                HorizontalDivider()
                Text("負荷を少し上げることを検討できます", color = TtStyle.Green, fontWeight = FontWeight.Bold)
                HelpText("同じ負荷・難度・セット構成で、全セットの上限を2回続けて達成しました。フォームを保てたか確認し、無理のない範囲で検討してください。負荷は自動で変わりません。")
            }
        }
    }
}

@Composable
internal fun SettingsScreen(state: AppUiState, actions: AppUiActions) {
    var source by remember { mutableStateOf(state.settings) }
    var form by remember { mutableStateOf(SettingsForm.of(state.settings)) }
    var error by remember { mutableStateOf<String?>(null) }
    var pickReminderTime by remember { mutableStateOf(false) }
    var pickFirstDate by remember { mutableStateOf(false) }
    // Saved or imported settings replace the form only while it holds no unsaved edits.
    LaunchedEffect(state.settings) {
        if (state.settings != source) {
            if (form == SettingsForm.of(source)) form = SettingsForm.of(state.settings)
            source = state.settings
        }
    }
    val dirty = form != SettingsForm.of(source)
    val allowed = state.notificationsAllowed
    val requestIfNeeded: (Boolean) -> Unit = { enabled -> if (enabled && !allowed) actions.requestNotificationPermission() }
    val firstDate = form.firstWorkoutDate?.let { runCatching { LocalDate.parse(it) }.getOrNull() }

    PageTitle("設定", "自分に合った、続けやすい形に。")
    HelpText("変更したら「設定を保存」を押してください。")
    TtCard(Modifier.fillMaxWidth()) {
        Text("習慣と予定", style = MaterialTheme.typography.titleLarge)
        Text("トレーニングする曜日", fontWeight = FontWeight.Bold)
        listOf(listOf(1, 2, 3, 4), listOf(5, 6, 7)).forEach { dayRow ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                dayRow.forEach { day ->
                    FilterChip(
                        day in form.plannedDays,
                        { form = form.copy(plannedDays = if (day in form.plannedDays) form.plannedDays - day else form.plannedDays + day) },
                        { Text(listOf("月", "火", "水", "木", "金", "土", "日")[day - 1]) },
                        modifier = Modifier.heightIn(min = TtStyle.Target),
                    )
                }
            }
        }
        NumericField("週間目標（1〜7日）", form.weeklyGoal, { form = form.copy(weeklyGoal = it) })
        OutlinedTextField(
            form.cueMemo,
            { if (it.length <= Limits.CUE_MEMO) form = form.copy(cueMemo = it) },
            label = { Text("運動を始めるきっかけ") },
            placeholder = { Text("帰宅して着替えたら") },
            modifier = Modifier.fillMaxWidth(),
        )
        SettingSwitch("習慣リマインド", "予定日に通知します。当日すでに実施していれば届きません。", form.reminderEnabled, {
            form = form.copy(reminderEnabled = it)
            requestIfNeeded(it)
        })
        if (form.reminderEnabled && !allowed) {
            Text("通知が許可されていないため、リマインドは届きません。下の「通知」で許可してください。", color = TtStyle.Red, style = MaterialTheme.typography.bodyMedium)
        }
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text("通知時刻", fontWeight = FontWeight.Medium)
                Text(timeText(form.reminderHour, form.reminderMinute), style = MaterialTheme.typography.titleLarge)
            }
            SubButton("変更", { pickReminderTime = true }, Modifier.semantics { contentDescription = "通知時刻を変更" })
        }
        HelpText("通知時刻は目安です。省電力設定などにより遅れることがあります。")
    }
    TtCard(Modifier.fillMaxWidth()) {
        Text("初週と準備運動", style = MaterialTheme.typography.titleLarge)
        SettingSwitch("初週モード", "初回開始日から7暦日、全種目を2セットにします。", form.firstWeekEnabled, { form = form.copy(firstWeekEnabled = it) })
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text("初回開始日", fontWeight = FontWeight.Medium)
                HelpText(firstDate?.format(japaneseDate) ?: "未設定（次に始めた日を初回開始日にします）")
            }
            SubButton("選ぶ", { pickFirstDate = true }, Modifier.semantics { contentDescription = "初回開始日を選ぶ" })
        }
        if (firstDate != null) {
            HelpText("適用期間：${firstDate.format(japaneseDate)}〜${firstDate.plusDays(6).format(japaneseDate)}")
            TextButton({ form = form.copy(firstWorkoutDate = null) }, Modifier.heightIn(min = TtStyle.Target)) { Text("未設定に戻す") }
        }
        NumericField("準備運動の初期値（0〜${Limits.WARMUP_SECONDS}秒）", form.warmupSeconds, { form = form.copy(warmupSeconds = it) })
        HelpText("「内容を調整・準備運動を入れる」から始めるときの初期値です。種目をタップしてすぐ始めるときは、準備運動なしで始まります。")
    }
    TtCard(Modifier.fillMaxWidth()) {
        Text("合図と画面", style = MaterialTheme.typography.titleLarge)
        SettingSwitch("効果音", "開始・終了と最後の3秒を音で知らせます。", form.soundEnabled, { form = form.copy(soundEnabled = it) })
        SettingSwitch("振動", "区切りを振動で知らせます。", form.vibrationEnabled, { form = form.copy(vibrationEnabled = it) })
        SettingSwitch("日本語の音声案内", "端末で利用できる音声を使います。音声がなくても画面と効果音で使えます。", form.voiceEnabled, { form = form.copy(voiceEnabled = it) })
        SettingSwitch("マナーモード中も音を鳴らす", "オフのときも、イヤホン接続中は効果音と音声を鳴らします。", form.cuesInSilentMode, { form = form.copy(cuesInSilentMode = it) })
        SubButton("保存済みの設定で合図を試す", actions.previewCues, Modifier.fillMaxWidth())
        HelpText("音量は端末側で調整できます。")
        SettingSwitch("運動画面を点灯したままにする", "トレーニング画面を表示している間だけ有効です。", form.keepScreenOn, { form = form.copy(keepScreenOn = it) })
    }
    TtCard(Modifier.fillMaxWidth()) {
        Text("通知", style = MaterialTheme.typography.titleLarge)
        if (allowed) {
            HelpText("通知は許可されています。")
        } else {
            Text("通知が許可されていません。リマインドと実行中タイマーの通知は表示されません。", color = TtStyle.Red, style = MaterialTheme.typography.bodyMedium)
            MainButton("通知を許可する", actions.requestNotificationPermission, Modifier.fillMaxWidth())
            SubButton("端末の通知設定を開く", actions.openNotificationSettings, Modifier.fillMaxWidth())
            HelpText("許可の確認が表示されないときは、端末の通知設定から許可してください。")
        }
        SettingSwitch("通知から一時停止・再開する", "進行中の通知に操作ボタンを表示します。", form.notificationsEnabled, {
            form = form.copy(notificationsEnabled = it)
            requestIfNeeded(it)
        })
        HelpText("バックグラウンドでタイマーを動かす間は、進行中の通知が表示されます。")
        HelpText("通知を許可しなくても、画面での計時・メニュー・記録は使えます。")
    }
    error?.let { Text(it, color = TtStyle.Red) }
    if (dirty) Text("未保存の変更があります。", color = TtStyle.Amber, style = MaterialTheme.typography.labelLarge)
    MainButton("設定を保存", {
        val problem = form.error()
        error = problem
        if (problem == null) {
            val edited = form
            actions.saveSettings { edited.applyTo(it) }
        }
    }, Modifier.fillMaxWidth(), !state.busy)
    TtCard(Modifier.fillMaxWidth()) {
        Text("バックアップ", style = MaterialTheme.typography.titleLarge)
        HelpText("メニュー・設定・実績をファイルに保存します。アカウントや通信は不要です。")
        SubButton("ファイルに書き出す", actions.exportBackup, Modifier.fillMaxWidth(), !state.busy && !state.run.active)
        SubButton("ファイルから取り込む", actions.pickImport, Modifier.fillMaxWidth(), !state.busy && !state.run.active)
        if (state.run.active) HelpText("トレーニングを終了してから、書き出し・取り込みができます。")
        else HelpText("取り込み前に内容を確認できます。取り込むと現在のデータを置き換えます。")
    }
    HelpText("つづトレ · 登録不要・オフラインで使える個人用タイマー")

    if (pickReminderTime) {
        TimePickDialog("通知時刻", form.reminderHour, form.reminderMinute, { pickReminderTime = false }) { hour, minute ->
            form = form.copy(reminderHour = hour, reminderMinute = minute)
            pickReminderTime = false
        }
    }
    if (pickFirstDate) {
        DatePickDialog(firstDate ?: LocalDate.now(), { pickFirstDate = false }) { date ->
            form = form.copy(firstWorkoutDate = date.toString())
            pickFirstDate = false
        }
    }
}

@Composable
internal fun SettingSwitch(title: String, description: String, checked: Boolean, change: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().heightIn(min = TtStyle.Target), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(title, fontWeight = FontWeight.Medium)
            HelpText(description)
        }
        Switch(checked, change, modifier = Modifier.sizeIn(minWidth = TtStyle.Target, minHeight = TtStyle.Target).semantics { contentDescription = title })
    }
}
