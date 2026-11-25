package jp.tsuzutore.app.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import jp.tsuzutore.app.domain.*
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.temporal.TemporalAdjusters
import java.time.DayOfWeek
import java.util.Locale
import java.util.UUID

internal fun SetResult.isPerformed(): Boolean = wasPerformed()
internal fun WorkoutSession.hasPerformed(): Boolean = wasPerformed()
internal val japaneseDate: DateTimeFormatter = DateTimeFormatter.ofPattern("M月d日（E）", Locale.JAPANESE)

@Composable
internal fun HomeScreen(state: AppUiState, resume: () -> Unit, preview: (MenuTemplate) -> Unit, interval: () -> Unit, menus: () -> Unit, calendar: () -> Unit) {
    val today = rememberToday()
    val monday = today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
    val days = remember(state.sessions) {
        state.sessions.filter { it.plan.kind == PlanKind.WORKOUT && it.hasPerformed() }
            .mapNotNull { runCatching { LocalDate.parse(it.localDate) }.getOrNull() }
            .toSet()
    }
    val weekly = days.count { it >= monday && it < monday.plusDays(7) }
    val todayPlanned = today.dayOfWeek.value in state.settings.plannedDays || state.settings.scheduledWorkouts.any { it.date == today.toString() }
    val completedToday = today in days
    val nextDay = nextPlannedDate(state.settings, today)
    val scheduledTemplateId = sortedSchedule(state.settings.scheduledWorkouts).firstOrNull { scheduled -> scheduled.date == today.toString() && state.templates.any { it.id == scheduled.templateId } }?.templateId
    val template = state.templates.find { it.id == scheduledTemplateId } ?: state.templates.firstOrNull()
    val firstWeek = isFirstWeek(state.settings, today)
    val plannedSets = template?.exercises?.sumOf { if (firstWeek) 2 else it.sets } ?: 0
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Box(Modifier.size(8.dp).background(TtStyle.Accent))
            Text("つづトレ", style = MaterialTheme.typography.titleMedium, color = TtStyle.Ink)
        }
        Text(today.format(japaneseDate), style = MaterialTheme.typography.bodyMedium, color = TtStyle.Muted)
    }
    Surface(Modifier.fillMaxWidth(), color = TtStyle.Dark, shape = RoundedCornerShape(10.dp)) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            TimerHeroTitle()
            Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
                Text(
                    when {
                        state.run.active -> "トレーニング進行中"
                        completedToday -> "今日のトレーニング完了"
                        todayPlanned -> "今日のトレーニング"
                        else -> "今日は休養日"
                    },
                    style = MaterialTheme.typography.labelLarge,
                    color = TtStyle.Accent,
                )
                Text(
                    if (state.run.active) state.run.exercise?.name ?: state.run.session?.plan?.title.orEmpty()
                    else template?.name ?: "最初のメニューを作ろう",
                    color = TtStyle.LightInk,
                    style = MaterialTheme.typography.headlineMedium,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                if (state.run.active) {
                    Text(state.run.session?.plan?.title.orEmpty(), color = TtStyle.LightInk.copy(alpha = .68f), style = MaterialTheme.typography.bodyMedium)
                } else if (template != null) {
                    Text(
                        "${template.exercises.size}種目  /  ${plannedSets}セット${if (firstWeek) "  ·  初週モード" else ""}",
                        color = TtStyle.LightInk.copy(alpha = .68f),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }
            when {
                state.run.active -> MainButton("トレーニングに戻る  →", resume, Modifier.fillMaxWidth())
                template != null -> MainButton("トレーニングを開始  →", { preview(template) }, Modifier.fillMaxWidth(), !state.busy)
                else -> MainButton("メニューを作る  ＋", menus, Modifier.fillMaxWidth())
            }
            if (!state.run.active && template != null) {
                Text(
                    if (todayPlanned) state.settings.cueMemo.ifBlank { "好きな種目をタップして開始できます" }
                    else "体調に合わせて、休養日もトレーニングできます",
                    color = TtStyle.LightInk.copy(alpha = .68f),
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        }
    }
    TtCard(Modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
            Column(Modifier.weight(1f).padding(end = 12.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text("今週のトレーニング", style = MaterialTheme.typography.titleMedium)
                Text(
                    "${monday.monthValue}/${monday.dayOfMonth} — ${monday.plusDays(6).monthValue}/${monday.plusDays(6).dayOfMonth}",
                    color = TtStyle.Muted,
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
            Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(weekly.toString(), fontFamily = TtStyle.SportFont, fontWeight = FontWeight.Black, fontSize = 48.sp, lineHeight = 50.sp, color = TtStyle.Ink)
                Text("/ ${state.settings.weeklyGoal}日", Modifier.padding(bottom = 5.dp), style = MaterialTheme.typography.labelLarge, color = TtStyle.Muted)
            }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            listOf("月", "火", "水", "木", "金", "土", "日").forEachIndexed { index, label ->
                val date = monday.plusDays(index.toLong())
                val done = date in days
                val planned = index + 1 in state.settings.plannedDays || state.settings.scheduledWorkouts.any { it.date == date.toString() }
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(7.dp),
                    modifier = Modifier.weight(1f).semantics {
                        contentDescription = "${date.format(japaneseDate)}、${if (done) "実施済み" else if (planned) "トレーニング予定" else "休養日"}${if (date == today) "、今日" else ""}"
                    },
                ) {
                    Text(label, style = MaterialTheme.typography.bodyMedium, color = TtStyle.Muted)
                    Surface(
                        Modifier.fillMaxWidth().height(40.dp),
                        shape = RoundedCornerShape(4.dp),
                        color = if (done) TtStyle.Accent else if (planned) TtStyle.BlueLight else TtStyle.Background,
                        border = if (date == today) BorderStroke(1.5.dp, TtStyle.Ink) else null,
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Text(if (done) "✓" else date.dayOfMonth.toString(), color = TtStyle.Ink, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                }
            }
        }
        LinearProgressIndicator(
            progress = { (weekly.toFloat() / state.settings.weeklyGoal.coerceAtLeast(1)).coerceIn(0f, 1f) },
            modifier = Modifier.fillMaxWidth().height(4.dp),
            color = TtStyle.Ink,
            trackColor = TtStyle.Border,
        )
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Box(Modifier.size(8.dp).background(TtStyle.Accent, RoundedCornerShape(1.dp)))
            Text("実施済み", color = TtStyle.Muted, style = MaterialTheme.typography.bodyMedium)
            Spacer(Modifier.width(8.dp))
            Box(Modifier.size(8.dp).background(TtStyle.BlueLight, RoundedCornerShape(1.dp)))
            Text("予定日", color = TtStyle.Muted, style = MaterialTheme.typography.bodyMedium)
        }
    }
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text("次の予定", style = MaterialTheme.typography.labelLarge)
            Text(nextDay?.format(japaneseDate) ?: "曜日は設定から選べます", color = TtStyle.Muted, style = MaterialTheme.typography.bodyMedium)
        }
        TextButton(calendar, Modifier.heightIn(min = TtStyle.Target)) { Text("カレンダー  →") }
    }
    TtCard(Modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            Box(Modifier.width(4.dp).height(44.dp).background(TtStyle.Accent))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text("INTERVAL", fontFamily = TtStyle.SportFont, fontWeight = FontWeight.Black, fontSize = 28.sp, lineHeight = 30.sp)
                Text("自由タイマー", style = MaterialTheme.typography.bodyMedium, color = TtStyle.Muted)
            }
        }
        HelpText("運動・休憩・ラウンドを自由に設定。初期値は20秒＋10秒を8回。")
        SubButton("インターバルを設定  →", interval, Modifier.fillMaxWidth(), !state.run.active)
    }
}

@Composable
private fun TimerHeroTitle() {
    BoxWithConstraints(Modifier.fillMaxWidth().height(88.dp).semantics { contentDescription = "タイマー" }) {
        val blockWidth = maxWidth * .34f
        val fontScale = LocalDensity.current.fontScale
        val titleSize = ((maxWidth.value * .28f).coerceIn(74f, 92f) / fontScale).sp
        val titleStyle = MaterialTheme.typography.displayLarge.copy(
            fontFamily = TtStyle.SportFont,
            fontWeight = FontWeight.Black,
            fontSize = titleSize,
            lineHeight = (88f / fontScale).sp,
            letterSpacing = (-1f / fontScale).sp,
        )
        Box(Modifier.width(blockWidth).fillMaxHeight().background(TtStyle.Accent))
        Text("TIMER", Modifier.padding(start = 12.dp).clearAndSetSemantics {}, color = TtStyle.LightInk, style = titleStyle, softWrap = false, maxLines = 1)
        Text("TIMER", Modifier.width(blockWidth).clipToBounds().padding(start = 12.dp).clearAndSetSemantics {}, color = TtStyle.Ink, style = titleStyle, softWrap = false, maxLines = 1)
    }
}

@Composable
internal fun MenuScreen(state: AppUiState, preview: (MenuTemplate) -> Unit, edit: (MenuTemplate) -> Unit, actions: AppUiActions) {
    var deleting by remember { mutableStateOf<MenuTemplate?>(null) }
    SportHeading("MENU", "マイメニュー", subtitle = "自分のペースに合わせて、内容を整えられます。")
    MainButton("＋ メニューを追加", { edit(MenuTemplate(UUID.randomUUID().toString(), "新しいメニュー", emptyList())) }, Modifier.fillMaxWidth())
    if (state.templates.isEmpty()) TtCard(Modifier.fillMaxWidth()) { Text("メニューがありません"); HelpText("種目を追加して、最初のメニューを作りましょう。") }
    state.templates.forEachIndexed { templateIndex, template ->
        TtCard(Modifier.fillMaxWidth()) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                Text(
                    (templateIndex + 1).toString().padStart(2, '0'),
                    color = TtStyle.Ink,
                    fontFamily = TtStyle.SportFont,
                    fontWeight = FontWeight.Black,
                    fontSize = 44.sp,
                    lineHeight = 48.sp,
                )
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(template.name, style = MaterialTheme.typography.titleLarge)
                    HelpText("${template.exercises.size}種目  /  ${template.exercises.sumOf { it.sets }}セット")
                }
            }
            HorizontalDivider(color = TtStyle.Border)
            template.exercises.forEachIndexed { index, exercise ->
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Box(Modifier.size(28.dp).background(TtStyle.Background, RoundedCornerShape(3.dp)), contentAlignment = Alignment.Center) {
                        Text((index + 1).toString().padStart(2, '0'), color = TtStyle.Ink, fontFamily = TtStyle.SportFont, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                    }
                    Column(Modifier.weight(1f)) {
                        Text(exercise.name, fontWeight = FontWeight.Bold)
                        HelpText("${targetText(exercise)} × ${exercise.sets}セット${if (exercise.bilateral) "（左→右）" else ""} · 休憩${exercise.restSeconds}秒")
                    }
                }
            }
            MainButton("種目を選んで開始  →", { preview(template) }, Modifier.fillMaxWidth(), !state.run.active && !state.busy)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                SubButton("編集", { edit(template) }, Modifier.weight(1f))
                SubButton("複製", {
                    actions.saveTemplate(template.copy(id = UUID.randomUUID().toString(), name = "${template.name} のコピー", exercises = template.exercises.map { it.copy(id = UUID.randomUUID().toString()) }))
                }, Modifier.weight(1f), !state.busy)
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton({
                    val ids = state.templates.map { it.id }.toMutableList()
                    val moved = ids.removeAt(templateIndex)
                    ids.add(templateIndex - 1, moved)
                    actions.reorderTemplates(ids)
                }, Modifier.weight(1f).heightIn(min = TtStyle.Target), enabled = templateIndex > 0 && !state.busy) { Text("↑ 上へ") }
                TextButton({
                    val ids = state.templates.map { it.id }.toMutableList()
                    val moved = ids.removeAt(templateIndex)
                    ids.add(templateIndex + 1, moved)
                    actions.reorderTemplates(ids)
                }, Modifier.weight(1f).heightIn(min = TtStyle.Target), enabled = templateIndex < state.templates.lastIndex && !state.busy) { Text("↓ 下へ") }
            }
            TextButton({ deleting = template }, Modifier.align(Alignment.End).heightIn(min = TtStyle.Target)) { Text("メニューを削除", color = TtStyle.Red) }
        }
    }
    HelpText("開始時の内容は記録に保存されます。あとから編集しても、過去の記録は変わりません。")
    deleting?.let { template ->
        AlertDialog(onDismissRequest = { deleting = null }, title = { Text("メニューを削除しますか？") }, text = { Text("「${template.name}」を削除します。これまでのトレーニング記録は残ります。") },
            confirmButton = { TextButton({ actions.deleteTemplate(template.id); deleting = null }) { Text("削除する", color = TtStyle.Red) } },
            dismissButton = { TextButton({ deleting = null }) { Text("キャンセル") } })
    }
}
@Composable
internal fun PlanPreview(template: MenuTemplate, settings: AppSettings, busy: Boolean, back: () -> Unit, start: (WorkoutPlan) -> Unit) {
    val today = rememberToday()
    val firstDate = settings.firstWorkoutDate?.let { runCatching { LocalDate.parse(it) }.getOrNull() }
    val firstWeek = isFirstWeek(settings, today)
    val exercises = remember(template, firstWeek) { template.exercises.map { if (firstWeek) it.copy(sets = 2) else it } }
    var shortened by remember(template.id) { mutableStateOf(false) }
    var selected by remember(exercises) { mutableStateOf(exercises.associate { it.id to true }) }
    var counts by remember(exercises) { mutableStateOf(exercises.associate { it.id to it.sets }) }
    var warmup by remember(settings.warmupSeconds) { mutableStateOf(settings.warmupSeconds.toString()) }
    val warmupValue = warmup.toIntOrNull()
    val actual = exercises.filter { !shortened || selected[it.id] == true }.map { if (shortened) it.copy(sets = counts[it.id] ?: it.sets) else it }
    EditorPage("開始前の確認", back) {
        Text(template.name, style = MaterialTheme.typography.titleLarge)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(!shortened, { shortened = false }, { Text("通常メニュー") }, modifier = Modifier.heightIn(min = TtStyle.Target))
            FilterChip(shortened, { shortened = true }, { Text("短縮メニュー") }, modifier = Modifier.heightIn(min = TtStyle.Target))
        }
        TtCard(Modifier.fillMaxWidth(), if (firstWeek) TtStyle.GreenLight else TtStyle.Background) {
            Text(if (firstWeek) "初週モード · 全種目2セット" else "通常のセット数", style = MaterialTheme.typography.titleMedium)
            HelpText(when {
                firstWeek && firstDate == null -> "今日を初回開始日にすると、${today.format(japaneseDate)}〜${today.plusDays(6).format(japaneseDate)}に適用されます。設定から変更できます。"
                firstWeek -> "${firstDate!!.format(japaneseDate)}〜${firstDate.plusDays(6).format(japaneseDate)}に適用。設定から変更できます。"
                settings.firstWeekEnabled && firstDate != null -> "初週期間は終了しました。今回から通常のセット数で進めます。"
                else -> "初週モードは設定でOFFになっています。"
            })
        }
        if (shortened) HelpText("今日行う種目を選び、セット数を減らせます。元のメニューはそのまま保存されます。")
        exercises.forEach { exercise ->
            TtCard(Modifier.fillMaxWidth()) {
                if (shortened) Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(selected[exercise.id] == true, { selected = selected + (exercise.id to it) }, Modifier.semantics { contentDescription = "${exercise.name}を今日のメニューに含める" })
                    Text(exercise.name, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                } else Text(exercise.name, style = MaterialTheme.typography.titleMedium)
                Text("${targetText(exercise)}${if (exercise.bilateral) " · 左→右で1セット" else ""}")
                if (shortened && selected[exercise.id] == true) NumberStepper("セット数", counts[exercise.id] ?: exercise.sets, 1, exercise.sets) { counts = counts + (exercise.id to it) }
                else if (!shortened) HelpText("${exercise.sets}セット · セット間休憩${exercise.restSeconds}秒")
            }
        }
        TtCard(Modifier.fillMaxWidth()) {
            Text("準備運動", style = MaterialTheme.typography.titleMedium)
            HelpText("足踏み・肩回し・軽いスクワット。0秒なら省略します。開始後もスキップできます。")
            NumericField("準備運動（秒）", warmup, { warmup = it }, warmupValue == null || warmupValue !in 0..Limits.WARMUP_SECONDS, "0〜${Limits.WARMUP_SECONDS}秒で入力してください")
        }
        Text("今日の内容：${actual.size}種目・${actual.sumOf { it.sets }}セット", style = MaterialTheme.typography.titleMedium)
        if (actual.isEmpty()) Text("少なくとも1種目を選んでください。", color = TtStyle.Red)
        MainButton("この内容で始める", {
            start(WorkoutPlan(template.id, template.name, actual, warmupValue!!, shortened, firstWeek))
        }, Modifier.fillMaxWidth(), !busy && actual.isNotEmpty() && warmupValue != null && warmupValue in 0..Limits.WARMUP_SECONDS)
    }
}

@Composable
internal fun TemplateEditor(template: MenuTemplate, back: () -> Unit, save: (MenuTemplate) -> Unit) {
    var name by remember(template.id) { mutableStateOf(template.name) }
    var exercises by remember(template.id) { mutableStateOf(template.exercises) }
    var editing by remember { mutableStateOf<Exercise?>(null) }
    var deleting by remember { mutableStateOf<Exercise?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    EditorPage("メニューを編集", back) {
        OutlinedTextField(
            name,
            { name = it; error = null },
            label = { Text("メニュー名") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            isError = name.isBlank() || name.length > Limits.NAME,
            supportingText = { Text("1〜${Limits.NAME}文字") },
        )
        HelpText("表示順を変えられます。トレーニング中はセット間に好きな種目を選べます。")
        exercises.forEachIndexed { index, exercise ->
            TtCard(Modifier.fillMaxWidth()) {
                Text("${index + 1}. ${exercise.name}", style = MaterialTheme.typography.titleMedium)
                HelpText("${targetText(exercise)} × ${exercise.sets}セット · 休憩${exercise.restSeconds}秒${if (exercise.bilateral) " · 左右あり" else ""}")
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    SubButton("編集", { editing = exercise }, Modifier.weight(1f))
                    SubButton("複製", { exercises = exercises.toMutableList().also { it.add(index + 1, exercise.copy(id = UUID.randomUUID().toString(), name = "${exercise.name} のコピー")) } }, Modifier.weight(1f))
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TextButton({ exercises = exercises.toMutableList().also { val moved = it.removeAt(index); it.add(index - 1, moved) } }, enabled = index > 0, modifier = Modifier.weight(1f).heightIn(min = TtStyle.Target).semantics { contentDescription = "${exercise.name}を上へ移動" }) { Text("↑ 上へ") }
                    TextButton({ exercises = exercises.toMutableList().also { val moved = it.removeAt(index); it.add(index + 1, moved) } }, enabled = index < exercises.lastIndex, modifier = Modifier.weight(1f).heightIn(min = TtStyle.Target).semantics { contentDescription = "${exercise.name}を下へ移動" }) { Text("↓ 下へ") }
                    TextButton({ deleting = exercise }, modifier = Modifier.weight(1f).heightIn(min = TtStyle.Target)) { Text("削除", color = TtStyle.Red) }
                }
            }
        }
        SubButton("＋ 種目を追加", { editing = Exercise(UUID.randomUUID().toString(), "") }, Modifier.fillMaxWidth(), exercises.size < Limits.TEMPLATE_EXERCISES)
        if (exercises.size >= Limits.TEMPLATE_EXERCISES) HelpText("1つのメニューに入れられる種目は${Limits.TEMPLATE_EXERCISES}までです。")
        error?.let { Text(it, color = TtStyle.Red) }
        MainButton("メニューを保存", {
            error = when {
                name.isBlank() -> "メニュー名を入力してください。"
                name.length > Limits.NAME -> "メニュー名は${Limits.NAME}文字以内にしてください。"
                exercises.isEmpty() -> "種目を1つ以上追加してください。"
                else -> null
            }
            if (error == null) save(template.copy(name = name.trim(), exercises = exercises))
        }, Modifier.fillMaxWidth())
        HelpText("保存するまでは元のメニューに反映されません。")
    }
    editing?.let { exercise -> ExerciseDialog(exercise, { editing = null }) { updated ->
        exercises = if (exercises.any { it.id == updated.id }) exercises.map { if (it.id == updated.id) updated else it } else exercises + updated
        editing = null
        error = null
    } }
    deleting?.let { exercise -> AlertDialog(onDismissRequest = { deleting = null }, title = { Text("種目を削除しますか？") }, text = { Text("このメニューから「${exercise.name}」を削除します。過去の実績は残ります。") }, confirmButton = { TextButton({ exercises = exercises.filterNot { it.id == exercise.id }; deleting = null }) { Text("削除する", color = TtStyle.Red) } }, dismissButton = { TextButton({ deleting = null }) { Text("キャンセル") } }) }
}

@Composable
private fun ExerciseDialog(exercise: Exercise, dismiss: () -> Unit, save: (Exercise) -> Unit) {
    var name by remember(exercise.id) { mutableStateOf(exercise.name) }
    var mode by remember(exercise.id) { mutableStateOf(exercise.mode) }
    var sets by remember(exercise.id) { mutableStateOf(exercise.sets.toString()) }
    var min by remember(exercise.id) { mutableStateOf(exercise.targetMin.toString()) }
    var max by remember(exercise.id) { mutableStateOf(exercise.targetMax.toString()) }
    var seconds by remember(exercise.id) { mutableStateOf(exercise.targetSeconds.toString()) }
    var rest by remember(exercise.id) { mutableStateOf(exercise.restSeconds.toString()) }
    var bilateral by remember(exercise.id) { mutableStateOf(exercise.bilateral) }
    var load by remember(exercise.id) { mutableStateOf(exercise.load) }
    var difficulty by remember(exercise.id) { mutableStateOf(exercise.difficulty) }
    var error by remember { mutableStateOf<String?>(null) }
    Dialog(dismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(Modifier.fillMaxWidth().padding(12.dp).heightIn(max = 850.dp), shape = MaterialTheme.shapes.large) {
            Column(Modifier.verticalScroll(rememberScrollState()).padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("種目を編集", style = MaterialTheme.typography.titleLarge)
                OutlinedTextField(name, { name = it }, label = { Text("種目名") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(mode == ExerciseMode.REPS, { mode = ExerciseMode.REPS }, { Text("回数制") }, modifier = Modifier.heightIn(min = TtStyle.Target))
                    FilterChip(mode == ExerciseMode.TIME, {
                        if (mode != ExerciseMode.TIME) {
                            min = "20"
                            max = "40"
                            seconds = "30"
                        }
                        mode = ExerciseMode.TIME
                    }, { Text("時間制") }, modifier = Modifier.heightIn(min = TtStyle.Target))
                }
                NumericField("セット数（1〜${Limits.SETS}）", sets, { sets = it })
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    NumericField("目標の下限（${if (mode == ExerciseMode.REPS) "回" else "秒"}）", min, { min = it }, modifier = Modifier.weight(1f))
                    NumericField("目標の上限（${if (mode == ExerciseMode.REPS) "回" else "秒"}）", max, { max = it }, modifier = Modifier.weight(1f))
                }
                HelpText("目標範囲は1〜${Limits.TARGET}${if (mode == ExerciseMode.REPS) "回" else "秒"}で設定できます。")
                if (mode == ExerciseMode.TIME) NumericField("今回の目標時間（範囲内の秒数）", seconds, { seconds = it })
                NumericField("セット間休憩（0〜${Limits.REST_SECONDS}秒）", rest, { rest = it })
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(90, 120, 180).forEach { value -> FilterChip(rest == value.toString(), { rest = value.toString() }, { Text("${value}秒") }, modifier = Modifier.heightIn(min = TtStyle.Target)) }
                }
                SettingSwitch("左右のある種目", "左→右の両側で1セット。切り替え時に開始を待ちます。", bilateral, { bilateral = it })
                OutlinedTextField(load, { load = it }, label = { Text("負荷（任意）") }, placeholder = { Text("リュック 5kg など") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                OutlinedTextField(difficulty, { difficulty = it }, label = { Text("難度・やり方") }, placeholder = { Text("通常、膝つき など") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                error?.let { Text(it, color = TtStyle.Red) }
                MainButton("この種目を保存", {
                    error = when {
                        name.isBlank() || name.length > Limits.NAME -> "種目名を1〜${Limits.NAME}文字で入力してください。"
                        sets.toIntOrNull() !in 1..Limits.SETS -> "セット数を1〜${Limits.SETS}で入力してください。"
                        min.toIntOrNull() !in 1..Limits.TARGET || max.toIntOrNull() !in 1..Limits.TARGET || min.toInt() > max.toInt() ->
                            "目標範囲は1〜${Limits.TARGET}で、上限を下限以上にしてください。"
                        mode == ExerciseMode.TIME && (seconds.toIntOrNull() !in 1..Limits.TARGET || seconds.toInt() !in min.toInt()..max.toInt()) ->
                            "目標時間を、設定した目標範囲の中で入力してください。"
                        rest.toIntOrNull() !in 0..Limits.REST_SECONDS -> "休憩時間を0〜${Limits.REST_SECONDS}秒で入力してください。"
                        load.length > Limits.TEXT || difficulty.isBlank() || difficulty.length > Limits.TEXT ->
                            "負荷は${Limits.TEXT}文字以内、難度は1〜${Limits.TEXT}文字で入力してください。"
                        else -> null
                    }
                    if (error == null) save(exercise.copy(name = name.trim(), mode = mode, sets = sets.toInt(), targetMin = min.toInt(), targetMax = max.toInt(), targetSeconds = if (mode == ExerciseMode.TIME) seconds.toInt() else exercise.targetSeconds, restSeconds = rest.toInt(), bilateral = bilateral, load = load.trim(), difficulty = difficulty.trim()))
                }, Modifier.fillMaxWidth())
                SubButton("キャンセル", dismiss, Modifier.fillMaxWidth())
            }
        }
    }
}

@Composable
internal fun IntervalSetup(busy: Boolean, back: () -> Unit, start: (WorkoutPlan) -> Unit) {
    var work by remember { mutableStateOf("20") }
    var rest by remember { mutableStateOf("10") }
    var rounds by remember { mutableStateOf("8") }
    val valid = work.toIntOrNull() in 1..Limits.TARGET_SECONDS && rest.toIntOrNull() in 0..Limits.REST_SECONDS && rounds.toIntOrNull() in 1..Limits.SETS
    EditorPage("自由タイマー", back) {
        PageTitle("運動と休憩を繰り返す", "最初の準備カウントのあと、ラウンドが自動で進みます。")
        TtCard(Modifier.fillMaxWidth()) {
            NumericField("運動時間（1〜${Limits.TARGET_SECONDS}秒）", work, { work = it }, work.toIntOrNull() !in 1..Limits.TARGET_SECONDS)
            NumericField("休憩時間（0〜${Limits.REST_SECONDS}秒）", rest, { rest = it }, rest.toIntOrNull() !in 0..Limits.REST_SECONDS)
            NumericField("ラウンド数（1〜${Limits.SETS}）", rounds, { rounds = it }, rounds.toIntOrNull() !in 1..Limits.SETS)
            SubButton("20秒＋10秒 × 8回に戻す", { work = "20"; rest = "10"; rounds = "8" }, Modifier.fillMaxWidth())
        }
        HelpText("この数字は時間の設定です。特定の研究と同じ運動強度や効果を表すものではありません。筋トレメニューとは別の記録に保存されます。")
        MainButton("タイマーを開始", {
            start(WorkoutPlan("custom-interval", "自由インターバル", listOf(Exercise("custom-interval-exercise", "インターバル", ExerciseMode.TIME, rounds.toInt(), targetSeconds = work.toInt(), restSeconds = rest.toInt())), warmupSeconds = 0, kind = PlanKind.INTERVAL))
        }, Modifier.fillMaxWidth(), valid && !busy)
    }
}

@Composable
internal fun EditorPage(title: String, back: () -> Unit, content: @Composable ColumnScope.() -> Unit) {
    Column(Modifier.widthIn(max = 720.dp).fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        TextButton(back, Modifier.heightIn(min = TtStyle.Target)) { Text("〈 戻る") }
        Text(title, style = MaterialTheme.typography.headlineMedium)
        content()
        Spacer(Modifier.height(16.dp))
    }
}

@Composable
internal fun NumericField(label: String, value: String, change: (String) -> Unit, isError: Boolean = false, error: String? = null, modifier: Modifier = Modifier) {
    OutlinedTextField(value, { next -> if (next.length <= 5 && next.all(Char::isDigit)) change(next) }, label = { Text(label) }, modifier = modifier.fillMaxWidth(), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), singleLine = true, isError = isError, supportingText = if (isError && error != null) ({ Text(error) }) else null)
}

@Composable
internal fun NumberStepper(label: String, value: Int, min: Int, max: Int, change: (Int) -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(label, Modifier.weight(1f))
        SubButton("−", { change(value - 1) }, enabled = value > min, modifier = Modifier.semantics { contentDescription = "${label}を減らす" })
        Text(value.toString(), style = MaterialTheme.typography.titleMedium)
        SubButton("＋", { change(value + 1) }, enabled = value < max, modifier = Modifier.semantics { contentDescription = "${label}を増やす" })
    }
}
