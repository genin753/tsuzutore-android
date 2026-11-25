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

private fun historyActualText(result: SetResult): String = when {
    result.outcome == SetOutcome.SKIPPED -> "スキップ"
    result.mode == ExerciseMode.TIME -> "${result.elapsedMs / 1000}秒"
    result.actualReps != null -> "${result.actualReps}回"
    result.outcome == SetOutcome.COMPLETE -> "完了"
    else -> "回数の記録なし"
}

@Composable
internal fun HistoryScreen(state: AppUiState, actions: AppUiActions) {
    var filter by remember { mutableIntStateOf(0) }
    var expanded by remember { mutableStateOf<String?>(null) }
    var showTrends by remember { mutableStateOf(false) }
    val sessions = state.sessions.sortedByDescending { it.startedAt }.filter { when (filter) { 1 -> it.plan.kind == PlanKind.WORKOUT; 2 -> it.plan.kind == PlanKind.INTERVAL; else -> true } }
    PageTitle("記録", "できた分を残して、次のトレーニングの目安に。")
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        listOf("すべて", "筋トレ", "自由タイマー").forEachIndexed { index, label ->
            FilterChip(filter == index, { filter = index }, { Text(label) }, modifier = Modifier.heightIn(min = TtStyle.Target))
        }
    }
    SubButton(if (showTrends) "種目ごとの推移を閉じる" else "種目ごとの推移・前回比較", { showTrends = !showTrends }, Modifier.fillMaxWidth(), state.sessions.any { it.plan.kind == PlanKind.WORKOUT && it.hasPerformed() })
    if (showTrends) ExerciseTrends(state.sessions)
    if (sessions.isEmpty()) TtCard(Modifier.fillMaxWidth()) {
        Text("まだ記録がありません", style = MaterialTheme.typography.titleMedium)
        HelpText("トレーニングを始めると、日付ごとに実績が並びます。短いメニューも記録できます。")
    }
    sessions.groupBy { it.localDate }.forEach { (date, dateSessions) ->
        Text(runCatching { LocalDate.parse(date).format(japaneseDate) }.getOrDefault(date), style = MaterialTheme.typography.titleMedium)
        dateSessions.forEach { session ->
            TtCard(Modifier.fillMaxWidth()) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(session.plan.title, style = MaterialTheme.typography.titleMedium)
                        Text(sessionLabel(session), color = TtStyle.Blue, style = MaterialTheme.typography.labelLarge)
                    }
                    Text(runCatching { Instant.ofEpochMilli(session.startedAt).atZone(ZoneId.of(session.zoneId)).format(DateTimeFormatter.ofPattern("HH:mm")) }.getOrDefault(""), color = TtStyle.Muted)
                }
                Text("実施 ${session.results.count { it.isPerformed() }}件 · スキップ ${session.results.count { it.outcome == SetOutcome.SKIPPED }}件")
                SubButton(if (expanded == session.id) "詳細を閉じる" else "実績を見る", { expanded = if (expanded == session.id) null else session.id }, Modifier.fillMaxWidth())
                if (expanded == session.id) {
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
                                val side = when (result.side) { Side.LEFT -> "・左"; Side.RIGHT -> "・右"; else -> "" }
                                val actual = historyActualText(result)
                                Text("${result.setNumber}セット$side：$actual${if (result.outcome == SetOutcome.PARTIAL) "（途中終了）" else ""}")
                                if (result.load.isNotBlank()) HelpText("負荷：${result.load} · ${result.difficulty}")
                            }
                        }
                    }
                    if (session.plan.kind == PlanKind.WORKOUT && session.results.any { it.isPerformed() && it.mode == ExerciseMode.REPS && it.actualReps != null }) SettingSwitch("フォームを保てた", "負荷を上げる目安に使います。", session.formConfirmed, { actions.confirmForm(session.id, it) })
                }
            }
        }
    }
}

@Composable
private fun ExerciseTrends(sessions: List<WorkoutSession>) {
    val workouts = sessions.filter { it.plan.kind == PlanKind.WORKOUT && it.hasPerformed() }.sortedByDescending { it.startedAt }
    val exercises = workouts.flatMap { it.plan.exercises }.distinctBy { it.id }
    Text("種目ごとの推移", style = MaterialTheme.typography.titleLarge)
    exercises.forEach { exercise ->
        val appearances = workouts.filter { session -> session.results.any { it.exerciseId == exercise.id && it.isPerformed() } }.take(3)
        if (appearances.isNotEmpty()) TtCard(Modifier.fillMaxWidth()) {
            Text(exercise.name, style = MaterialTheme.typography.titleMedium)
            HelpText("直近${appearances.size}回（新しい順）")
            appearances.forEach { session ->
                val results = session.results.filter { it.exerciseId == exercise.id && it.isPerformed() }.sortedWith(compareBy({ it.setNumber }, { it.side.ordinal }))
                Text(runCatching { LocalDate.parse(session.localDate).format(japaneseDate) }.getOrDefault(session.localDate), fontWeight = FontWeight.Bold)
                Text(results.joinToString(" / ") { "${if (it.side == Side.LEFT) "左" else if (it.side == Side.RIGHT) "右" else ""}${historyActualText(it)}${if (it.outcome == SetOutcome.PARTIAL) "途中" else ""}" })
                if (results.any { it.actualReps != null || it.load.isNotBlank() }) HelpText("負荷：${results.map { it.load.ifBlank { "記録なし" } }.distinct().joinToString("、")} · ${results.map { it.difficulty }.distinct().joinToString("、")}")
            }
            if (appearances.size >= 2) {
                val latest = appearances[0].results.filter { it.exerciseId == exercise.id && it.isPerformed() }
                val previous = appearances[1].results.filter { it.exerciseId == exercise.id && it.isPerformed() }
                val hasRepCounts = (latest + previous).none { it.mode == ExerciseMode.REPS && it.actualReps == null }
                if (hasRepCounts && latest.size == previous.size && latest.map { it.mode } == previous.map { it.mode }) {
                    val repsMode = latest.all { it.mode == ExerciseMode.REPS }
                    val delta = if (repsMode) latest.sumOf { requireNotNull(it.actualReps) } - previous.sumOf { requireNotNull(it.actualReps) } else ((latest.sumOf { it.elapsedMs } - previous.sumOf { it.elapsedMs }) / 1000).toInt()
                    Text("前回との合計差：${if (delta > 0) "+" else ""}$delta${if (repsMode) "回" else "秒"}", color = TtStyle.Blue)
                    HelpText("負荷・難度・フォームもあわせて振り返ってください。")
                } else if (hasRepCounts) HelpText("セット構成が異なるため、上の実績で比較してください。")
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
    var draft by remember(state.settings) { mutableStateOf(state.settings) }
    var goal by remember(state.settings) { mutableStateOf(state.settings.weeklyGoal.toString()) }
    var hour by remember(state.settings) { mutableStateOf(state.settings.reminderHour.toString()) }
    var minute by remember(state.settings) { mutableStateOf(state.settings.reminderMinute.toString()) }
    var warmup by remember(state.settings) { mutableStateOf(state.settings.warmupSeconds.toString()) }
    var firstDate by remember(state.settings) { mutableStateOf(state.settings.firstWorkoutDate.orEmpty()) }
    var error by remember { mutableStateOf<String?>(null) }
    val save = {
        error = when {
            goal.toIntOrNull() !in 1..7 -> "週間目標を1〜7日で入力してください。"
            hour.toIntOrNull() !in 0..23 || minute.toIntOrNull() !in 0..59 -> "通知時刻は0〜23時、0〜59分で入力してください。"
            warmup.toIntOrNull() !in 0..1800 -> "準備運動を0〜1800秒で入力してください。"
            firstDate.isNotBlank() && runCatching { LocalDate.parse(firstDate) }.isFailure -> "初回開始日は YYYY-MM-DD の形式で入力してください。"
            draft.cueMemo.length > 200 -> "きっかけのメモは200文字以内にしてください。"
            else -> null
        }
        if (error == null) {
            actions.saveSettings(draft.copy(weeklyGoal = goal.toInt(), reminderHour = hour.toInt(), reminderMinute = minute.toInt(), warmupSeconds = warmup.toInt(), firstWorkoutDate = firstDate.ifBlank { null }))
        }
    }
    PageTitle("設定", "自分に合った、続けやすい形に。")
    HelpText("変更したら「設定を保存」を押してください。")
    TtCard(Modifier.fillMaxWidth()) {
        Text("習慣と予定", style = MaterialTheme.typography.titleLarge)
        Text("トレーニングする曜日", fontWeight = FontWeight.Bold)
        listOf(listOf(1, 2, 3, 4), listOf(5, 6, 7)).forEach { dayRow ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                dayRow.forEach { day -> FilterChip(day in draft.plannedDays, { draft = draft.copy(plannedDays = if (day in draft.plannedDays) draft.plannedDays - day else draft.plannedDays + day) }, { Text(listOf("月", "火", "水", "木", "金", "土", "日")[day - 1]) }, modifier = Modifier.heightIn(min = TtStyle.Target)) }
            }
        }
        NumericField("週間目標（1〜7日）", goal, { goal = it })
        OutlinedTextField(draft.cueMemo, { if (it.length <= 200) draft = draft.copy(cueMemo = it) }, label = { Text("運動を始めるきっかけ") }, placeholder = { Text("帰宅して着替えたら") }, modifier = Modifier.fillMaxWidth())
        SettingSwitch("習慣リマインド", "予定日に通知します。当日すでに実施していれば届きません。", draft.reminderEnabled, { draft = draft.copy(reminderEnabled = it) })
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            NumericField("通知時刻（時）", hour, { hour = it }, modifier = Modifier.weight(1f))
            NumericField("通知時刻（分）", minute, { minute = it }, modifier = Modifier.weight(1f))
        }
        HelpText("通知時刻は目安です。省電力設定などにより遅れることがあります。")
    }
    TtCard(Modifier.fillMaxWidth()) {
        Text("初週と準備運動", style = MaterialTheme.typography.titleLarge)
        SettingSwitch("初週モード", "初回開始日から7暦日、全種目を2セットにします。", draft.firstWeekEnabled, { draft = draft.copy(firstWeekEnabled = it) })
        OutlinedTextField(firstDate, { if (it.length <= 10) firstDate = it }, label = { Text("初回開始日（任意）") }, placeholder = { Text("YYYY-MM-DD") }, singleLine = true, modifier = Modifier.fillMaxWidth(), supportingText = { Text("空欄の場合は、次に始めた日を初回開始日にします。") })
        val date = runCatching { LocalDate.parse(firstDate) }.getOrNull()
        if (date != null) HelpText("適用期間：${date.format(japaneseDate)}〜${date.plusDays(6).format(japaneseDate)}")
        NumericField("準備運動の初期値（0〜1800秒）", warmup, { warmup = it })
    }
    TtCard(Modifier.fillMaxWidth()) {
        Text("合図と画面", style = MaterialTheme.typography.titleLarge)
        SettingSwitch("効果音", "開始・終了と最後の3秒を音で知らせます。", draft.soundEnabled, { draft = draft.copy(soundEnabled = it) })
        SettingSwitch("振動", "区切りを振動で知らせます。", draft.vibrationEnabled, { draft = draft.copy(vibrationEnabled = it) })
        SettingSwitch("日本語の音声案内", "端末で利用できる音声を使います。音声がなくても画面と効果音で使えます。", draft.voiceEnabled, { draft = draft.copy(voiceEnabled = it) })
        SubButton("保存済みの設定で合図を試す", actions.previewCues, Modifier.fillMaxWidth())
        HelpText("音量・サイレント設定は端末側で調整できます。")
        SettingSwitch("運動画面を点灯したままにする", "トレーニング画面を表示している間だけ有効です。", draft.keepScreenOn, { draft = draft.copy(keepScreenOn = it) })
    }
    TtCard(Modifier.fillMaxWidth()) {
        Text("通知", style = MaterialTheme.typography.titleLarge)
        SettingSwitch("通知から一時停止・再開する", "進行中の通知に操作ボタンを表示します。", draft.notificationsEnabled, { draft = draft.copy(notificationsEnabled = it) })
        HelpText("バックグラウンドでタイマーを動かす間は、進行中の通知が表示されます。")
        SubButton("端末の通知許可を確認", actions.requestNotificationPermission, Modifier.fillMaxWidth())
        HelpText("通知を許可しなくても、画面での計時・メニュー・記録は使えます。")
    }
    error?.let { Text(it, color = TtStyle.Red) }
    MainButton("設定を保存", save, Modifier.fillMaxWidth(), !state.busy)
    TtCard(Modifier.fillMaxWidth()) {
        Text("バックアップ", style = MaterialTheme.typography.titleLarge)
        HelpText("メニュー・設定・実績をファイルに保存します。アカウントや通信は不要です。")
        SubButton("ファイルに書き出す", actions.exportBackup, Modifier.fillMaxWidth(), !state.busy && !state.run.active)
        SubButton("ファイルから取り込む", actions.pickImport, Modifier.fillMaxWidth(), !state.busy && !state.run.active)
        if (state.run.active) HelpText("トレーニングを終了してから、書き出し・取り込みができます。")
        else HelpText("取り込み前に内容を確認できます。取り込むと現在のデータを置き換えます。")
    }
    HelpText("つづトレ · 登録不要・オフラインで使える個人用タイマー")
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
