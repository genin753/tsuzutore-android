package jp.tsuzutore.app.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import jp.tsuzutore.app.domain.*
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.temporal.TemporalAdjusters
import java.util.Locale
import java.util.UUID

internal fun SetResult.isPerformed(): Boolean = wasPerformed()
internal fun WorkoutSession.hasPerformed(): Boolean = wasPerformed()
internal val japaneseDate: DateTimeFormatter = DateTimeFormatter.ofPattern("M月d日（E）", Locale.JAPANESE)
internal val weekdayLabels = listOf("月", "火", "水", "木", "金", "土", "日")

@Composable
internal fun HomeScreen(state: AppUiState, resume: () -> Unit, preview: (MenuTemplate) -> Unit, interval: () -> Unit, menus: () -> Unit, calendar: () -> Unit) {
    val colors = MaterialTheme.colorScheme
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
    val scheduledTemplateId = sortedSchedule(state.settings.scheduledWorkouts)
        .firstOrNull { scheduled -> scheduled.date == today.toString() && state.templates.any { it.id == scheduled.templateId } }?.templateId
    val template = state.templates.find { it.id == scheduledTemplateId } ?: state.templates.firstOrNull()
    val firstWeek = isFirstWeek(state.settings, today)
    val plannedSets = template?.exercises?.sumOf { if (firstWeek) 2 else it.sets } ?: 0
    val active = state.run.active
    val running = state.run.session
    // Today's progress: the running session's finished sets, otherwise the sets done today against today's menu.
    val todayKey = today.toString()
    val doneToday = remember(state.sessions, todayKey) {
        state.sessions.filter { it.localDate == todayKey && it.plan.kind == PlanKind.WORKOUT }.sumOf { performedSets(it) }
    }
    val (setsDone, setsTotal) = if (active && running != null) {
        running.plan.exercises.sumOf { completedExerciseSets(it, running.results) } to running.plan.exercises.sumOf { it.sets }
    } else {
        doneToday to plannedSets
    }
    val exerciseCount = if (active && running != null) running.plan.exercises.size else template?.exercises?.size ?: 0
    val percent = if (setsTotal > 0) (setsDone * 100 / setsTotal).coerceAtMost(100) else 0

    Column(Modifier.fillMaxWidth().padding(top = TtSpace.Small), verticalArrangement = Arrangement.spacedBy(TtSpace.XSmall)) {
        Text(today.format(japaneseDate), style = MaterialTheme.typography.titleSmall, color = colors.primary)
        Text(
            when {
                active -> "KEEP GOING"
                completedToday -> "NICE WORK"
                todayPlanned -> "TODAY"
                else -> "REST DAY"
            },
            style = TtType.Hero,
            modifier = Modifier.clearAndSetSemantics { },
        )
    }

    Surface(Modifier.fillMaxWidth(), shape = TtShapes.ExtraLarge, color = colors.surfaceContainer) {
        Column(Modifier.padding(TtSpace.XLarge), verticalArrangement = Arrangement.spacedBy(TtSpace.Large)) {
            Column(verticalArrangement = Arrangement.spacedBy(TtSpace.XSmall)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(TtSpace.Small)) {
                    Box(Modifier.size(8.dp).background(colors.primary, TtShapes.Full))
                    Text(
                        when {
                            active -> "トレーニング進行中"
                            completedToday -> "今日のトレーニング完了"
                            todayPlanned -> "今日のトレーニング"
                            else -> "今日は休養日"
                        },
                        style = MaterialTheme.typography.titleSmall,
                        color = colors.primary,
                        modifier = Modifier.semantics { heading() },
                    )
                }
                Text(
                    if (active) state.run.exercise?.name ?: running?.plan?.title.orEmpty()
                    else template?.name ?: "最初のメニューを作ろう",
                    style = MaterialTheme.typography.headlineMedium,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                val detail = when {
                    active -> running?.plan?.title
                    template == null -> "種目とセット数を決めると、ここからすぐに始められます。"
                    firstWeek -> "初週モード · 各種目2セット"
                    else -> null
                }
                if (detail != null) Text(detail, style = MaterialTheme.typography.bodyLarge, color = colors.onSurfaceVariant)
            }
            if (active || template != null) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(TtSpace.XLarge)) {
                    ProgressRing(
                        percent / 100f,
                        Modifier.size(136.dp).clearAndSetSemantics { contentDescription = "今日の進み具合 ${percent}パーセント" },
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Row(verticalAlignment = Alignment.Bottom) {
                                Text("$percent", style = TtType.Display, color = colors.onSurface)
                                Text("%", style = TtType.Title, color = colors.onSurfaceVariant, modifier = Modifier.padding(bottom = 4.dp))
                            }
                            Text("DONE", style = TtType.Label, color = colors.onSurfaceVariant)
                        }
                    }
                    Column(verticalArrangement = Arrangement.spacedBy(TtSpace.Medium)) {
                        BigStat("$exerciseCount", "EXERCISES", "${exerciseCount}種目")
                        BigStat("$setsDone/$setsTotal", "SETS", "${setsTotal}セット中${setsDone}セット実施")
                    }
                }
            }
            when {
                active -> MainButton("トレーニングに戻る", resume, Modifier.fillMaxWidth(), icon = Symbols.PlayArrow, height = LargeButtonHeight)
                template != null -> MainButton("トレーニングを開始", { preview(template) }, Modifier.fillMaxWidth(), !state.busy, Symbols.PlayArrow, LargeButtonHeight)
                else -> MainButton("メニューを作る", menus, Modifier.fillMaxWidth(), icon = Symbols.Add, height = LargeButtonHeight)
            }
            if (!active && template != null) {
                Text(
                    if (todayPlanned) state.settings.cueMemo.ifBlank { "好きな種目をタップして開始できます" }
                    else "体調に合わせて、休養日もトレーニングできます",
                    style = MaterialTheme.typography.bodyMedium,
                    color = colors.onSurfaceVariant,
                )
            }
        }
    }

    TtCard(Modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Bottom) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text("THIS WEEK", style = TtType.Label, color = colors.primary, modifier = Modifier.clearAndSetSemantics { })
                SectionTitle("今週のトレーニング")
                Text(
                    "${monday.monthValue}/${monday.dayOfMonth} — ${monday.plusDays(6).monthValue}/${monday.plusDays(6).dayOfMonth}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = colors.onSurfaceVariant,
                )
            }
            Row(
                Modifier.clearAndSetSemantics { contentDescription = "今週 ${weekly}日 / 目標${state.settings.weeklyGoal}日" },
                verticalAlignment = Alignment.Bottom,
            ) {
                Text(weekly.toString(), style = TtType.Display, color = colors.primary)
                Text(
                    " / ${state.settings.weeklyGoal}日",
                    style = MaterialTheme.typography.titleMedium,
                    color = colors.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 4.dp),
                )
            }
        }
        Row(Modifier.fillMaxWidth()) {
            weekdayLabels.forEachIndexed { index, label ->
                val date = monday.plusDays(index.toLong())
                val done = date in days
                val planned = index + 1 in state.settings.plannedDays || state.settings.scheduledWorkouts.any { it.date == date.toString() }
                WeekDay(
                    label, date, done, planned, date == today,
                    "${date.format(japaneseDate)}、${if (done) "実施済み" else if (planned) "トレーニング予定" else "休養日"}${if (date == today) "、今日" else ""}",
                )
            }
        }
        LinearProgressIndicator(
            progress = { (weekly.toFloat() / state.settings.weeklyGoal.coerceAtLeast(1)).coerceIn(0f, 1f) },
            modifier = Modifier.fillMaxWidth().height(8.dp),
            color = colors.primary,
            trackColor = colors.surfaceContainerHighest,
            strokeCap = StrokeCap.Round,
        )
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(TtSpace.Small)) {
            LegendDot(colors.primary)
            Text("実施済み", style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant)
            Spacer(Modifier.width(TtSpace.Small))
            LegendDot(colors.surfaceContainerHighest)
            Text("予定日", style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant)
        }
    }

    TtCard(Modifier.fillMaxWidth(), containerColor = colors.surfaceContainerLow) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(TtSpace.Large)) {
            IconBadge(Symbols.Event)
            Column(Modifier.weight(1f)) {
                Text("次の予定", style = MaterialTheme.typography.labelLarge, color = colors.onSurfaceVariant)
                Text(nextDay?.format(japaneseDate) ?: "曜日は設定から選べます", style = MaterialTheme.typography.titleMedium)
            }
            TextButton(calendar, Modifier.heightIn(min = MinTouchTarget)) { Text("カレンダーを見る") }
        }
    }

    TtCard(Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(TtSpace.Large)) {
            IconBadge(Symbols.Timer, container = colors.tertiaryContainer)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text("INTERVAL", style = TtType.Label, color = colors.tertiary, modifier = Modifier.clearAndSetSemantics { })
                SectionTitle("自由タイマー")
                HelpText("運動・休憩・ラウンドを自由に設定。初期値は20秒＋10秒を8回。")
            }
        }
        SubButton("インターバルを設定", interval, Modifier.fillMaxWidth(), !active, Symbols.Timer)
    }
}

/** Sets actually done in [session]; a left/right exercise counts once both of its sides are done. */
private fun performedSets(session: WorkoutSession): Int = session.plan.exercises.sumOf { exercise ->
    val done = session.results.filter { it.exerciseId == exercise.id && it.isPerformed() }.map { it.setNumber to it.side }.toSet()
    val sides = if (exercise.bilateral) listOf(Side.LEFT, Side.RIGHT) else listOf(Side.BOTH)
    (1..exercise.sets).count { set -> sides.all { (set to it) in done } }
}

/** A big Anton number over a small English label; read out as one Japanese phrase. */
@Composable
private fun BigStat(value: String, label: String, description: String) {
    Column(Modifier.clearAndSetSemantics { contentDescription = description }, verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(value, style = TtType.Display, color = MaterialTheme.colorScheme.onSurface, maxLines = 1, softWrap = false)
        Text(label, style = TtType.Label, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun RowScope.WeekDay(label: String, date: LocalDate, done: Boolean, planned: Boolean, isToday: Boolean, description: String) {
    val colors = MaterialTheme.colorScheme
    val container = when {
        done -> colors.primary
        planned -> colors.surfaceContainerHighest
        else -> Color.Transparent
    }
    val content = when {
        done -> colors.onPrimary
        planned -> colors.onSurface
        else -> colors.onSurfaceVariant
    }
    Column(
        Modifier.weight(1f).semantics(mergeDescendants = true) { contentDescription = description },
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text(label, style = MaterialTheme.typography.labelMedium, color = if (isToday) colors.onSurface else colors.onSurfaceVariant)
        Surface(
            Modifier.size(38.dp),
            shape = TtShapes.Full,
            color = container,
            contentColor = content,
            border = when {
                isToday && !done -> BorderStroke(2.dp, colors.onSurface)
                !done && !planned -> BorderStroke(1.dp, colors.outlineVariant)
                else -> null
            },
        ) {
            Box(contentAlignment = Alignment.Center) {
                if (done) Icon(Symbols.Check, contentDescription = null, modifier = Modifier.size(20.dp))
                else Text(date.dayOfMonth.toString(), style = TtType.Title.copy(fontSize = 16.sp, lineHeight = 18.sp))
            }
        }
    }
}

@Composable
private fun LegendDot(color: Color) {
    Surface(Modifier.size(10.dp), shape = TtShapes.Full, color = color) {}
}

/** Numbered circle that leads an exercise row. */
@Composable
internal fun NumberDot(number: Int, modifier: Modifier = Modifier) {
    Surface(modifier.size(32.dp), shape = TtShapes.Full, color = MaterialTheme.colorScheme.surfaceContainerHighest) {
        Box(contentAlignment = Alignment.Center) { Text(number.toString(), style = TtType.Title.copy(fontSize = 16.sp, lineHeight = 18.sp)) }
    }
}

@Composable
internal fun MenuScreen(state: AppUiState, preview: (MenuTemplate) -> Unit, edit: (MenuTemplate) -> Unit, actions: AppUiActions) {
    val colors = MaterialTheme.colorScheme
    var deleting by remember { mutableStateOf<MenuTemplate?>(null) }
    PageHeader("WORKOUTS", "メニュー", "自分のペースに合わせて、内容を整えられます。")
    MainButton("メニューを追加", { edit(MenuTemplate(UUID.randomUUID().toString(), "新しいメニュー", emptyList())) }, Modifier.fillMaxWidth(), icon = Symbols.Add)
    if (state.templates.isEmpty()) {
        TtCard(Modifier.fillMaxWidth()) {
            SectionTitle("メニューがありません")
            HelpText("種目を追加して、最初のメニューを作りましょう。")
        }
    }
    state.templates.forEachIndexed { templateIndex, template ->
        TtCard(Modifier.fillMaxWidth()) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(TtSpace.Medium)) {
                IconBadge(Symbols.FitnessCenter, container = colors.primaryContainer, size = 48.dp)
                Column(Modifier.weight(1f)) {
                    Text(template.name, style = MaterialTheme.typography.titleLarge)
                    HelpText("${template.exercises.size}種目 · ${template.exercises.sumOf { it.sets }}セット")
                }
            }
            Column(verticalArrangement = Arrangement.spacedBy(TtSpace.Medium)) {
                template.exercises.forEachIndexed { index, exercise ->
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(TtSpace.Medium)) {
                        NumberDot(index + 1)
                        Column(Modifier.weight(1f)) {
                            Text(exercise.name, style = MaterialTheme.typography.titleSmall)
                            Text(
                                "${targetText(exercise)} × ${exercise.sets}セット${if (exercise.bilateral) "（左→右）" else ""} · 休憩${exercise.restSeconds}秒",
                                style = MaterialTheme.typography.bodySmall,
                                color = colors.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
            MainButton("種目を選んで開始", { preview(template) }, Modifier.fillMaxWidth(), !state.run.active && !state.busy, Symbols.PlayArrow)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(TtSpace.Small)) {
                OutlineAction("編集", { edit(template) }, Modifier.weight(1f), icon = Symbols.Edit)
                OutlineAction("複製", {
                    actions.saveTemplate(template.copy(
                        id = UUID.randomUUID().toString(),
                        name = "${template.name} のコピー",
                        exercises = template.exercises.map { it.copy(id = UUID.randomUUID().toString()) },
                    ))
                }, Modifier.weight(1f), !state.busy, Symbols.ContentCopy)
            }
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                IconButton({
                    val ids = state.templates.map { it.id }.toMutableList()
                    val moved = ids.removeAt(templateIndex)
                    ids.add(templateIndex - 1, moved)
                    actions.reorderTemplates(ids)
                }, enabled = templateIndex > 0 && !state.busy) { Icon(Symbols.ArrowUpward, contentDescription = "「${template.name}」を上へ移動") }
                IconButton({
                    val ids = state.templates.map { it.id }.toMutableList()
                    val moved = ids.removeAt(templateIndex)
                    ids.add(templateIndex + 1, moved)
                    actions.reorderTemplates(ids)
                }, enabled = templateIndex < state.templates.lastIndex && !state.busy) { Icon(Symbols.ArrowDownward, contentDescription = "「${template.name}」を下へ移動") }
                Spacer(Modifier.weight(1f))
                TextButton({ deleting = template }, colors = ButtonDefaults.textButtonColors(contentColor = colors.error)) {
                    Icon(Symbols.Delete, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("メニューを削除")
                }
            }
        }
    }
    HelpText("開始時の内容は記録に保存されます。あとから編集しても、過去の記録は変わりません。")
    deleting?.let { template ->
        AlertDialog(
            onDismissRequest = { deleting = null },
            icon = { Icon(Symbols.Delete, contentDescription = null) },
            title = { Text("メニューを削除しますか？") },
            text = { Text("「${template.name}」を削除します。これまでのトレーニング記録は残ります。") },
            confirmButton = {
                TextButton({ actions.deleteTemplate(template.id); deleting = null }, colors = ButtonDefaults.textButtonColors(contentColor = colors.error)) { Text("削除する") }
            },
            dismissButton = { TextButton({ deleting = null }) { Text("キャンセル") } },
        )
    }
}

@Composable
internal fun PlanPreview(template: MenuTemplate, settings: AppSettings, busy: Boolean, back: () -> Unit, start: (WorkoutPlan) -> Unit) {
    val colors = MaterialTheme.colorScheme
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
    EditorPage("開始前の確認", back, "READY?") {
        Text(template.name, style = MaterialTheme.typography.headlineSmall)
        Row(horizontalArrangement = Arrangement.spacedBy(TtSpace.Small)) {
            TtChip(!shortened, { shortened = false }, { Text("通常メニュー") }, modifier = Modifier.heightIn(min = MinTouchTarget))
            TtChip(shortened, { shortened = true }, { Text("短縮メニュー") }, modifier = Modifier.heightIn(min = MinTouchTarget))
        }
        TtCard(Modifier.fillMaxWidth(), containerColor = if (firstWeek) colors.tertiaryContainer else colors.surfaceContainer) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(TtSpace.Medium)) {
                Icon(Symbols.LocalFireDepartment, contentDescription = null)
                Text(if (firstWeek) "初週モード · 全種目2セット" else "通常のセット数", style = MaterialTheme.typography.titleMedium)
            }
            Text(
                when {
                    firstWeek && firstDate == null -> "今日を初回開始日にすると、${today.format(japaneseDate)}〜${today.plusDays(6).format(japaneseDate)}に適用されます。設定から変更できます。"
                    firstWeek -> "${firstDate!!.format(japaneseDate)}〜${firstDate.plusDays(6).format(japaneseDate)}に適用。設定から変更できます。"
                    settings.firstWeekEnabled && firstDate != null -> "初週期間は終了しました。今回から通常のセット数で進めます。"
                    else -> "初週モードは設定でOFFになっています。"
                },
                style = MaterialTheme.typography.bodyMedium,
            )
        }
        if (shortened) HelpText("今日行う種目を選び、セット数を減らせます。元のメニューはそのまま保存されます。")
        exercises.forEachIndexed { index, exercise ->
            TtCard(Modifier.fillMaxWidth()) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(TtSpace.Medium)) {
                    if (shortened) {
                        Checkbox(
                            selected[exercise.id] == true,
                            { selected = selected + (exercise.id to it) },
                            Modifier.semantics { contentDescription = "${exercise.name}を今日のメニューに含める" },
                        )
                    } else NumberDot(index + 1)
                    Column(Modifier.weight(1f)) {
                        Text(exercise.name, style = MaterialTheme.typography.titleMedium)
                        Text("${targetText(exercise)}${if (exercise.bilateral) " · 左→右で1セット" else ""}", style = MaterialTheme.typography.bodyMedium, color = colors.onSurfaceVariant)
                    }
                }
                if (shortened && selected[exercise.id] == true) {
                    NumberStepper("セット数", counts[exercise.id] ?: exercise.sets, 1, exercise.sets) { counts = counts + (exercise.id to it) }
                } else if (!shortened) HelpText("${exercise.sets}セット · セット間休憩${exercise.restSeconds}秒")
            }
        }
        TtCard(Modifier.fillMaxWidth()) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(TtSpace.Medium)) {
                Icon(Symbols.SelfImprovement, contentDescription = null)
                SectionTitle("準備運動")
            }
            HelpText("足踏み・肩回し・軽いスクワット。0秒なら省略します。開始後もスキップできます。")
            NumericField(
                "準備運動（秒）", warmup, { warmup = it },
                isError = warmupValue == null || warmupValue !in 0..Limits.WARMUP_SECONDS,
                error = "0〜${Limits.WARMUP_SECONDS}秒で入力してください",
            )
        }
        Text("今日の内容：${actual.size}種目・${actual.sumOf { it.sets }}セット", style = MaterialTheme.typography.titleMedium)
        if (actual.isEmpty()) Text("少なくとも1種目を選んでください。", color = colors.error)
        MainButton(
            "この内容で始める",
            { start(WorkoutPlan(template.id, template.name, actual, warmupValue!!, shortened, firstWeek)) },
            Modifier.fillMaxWidth(),
            !busy && actual.isNotEmpty() && warmupValue != null && warmupValue in 0..Limits.WARMUP_SECONDS,
            Symbols.PlayArrow,
            LargeButtonHeight,
        )
    }
}

@Composable
internal fun TemplateEditor(template: MenuTemplate, back: () -> Unit, save: (MenuTemplate) -> Unit) {
    val colors = MaterialTheme.colorScheme
    var name by remember(template.id) { mutableStateOf(template.name) }
    var exercises by remember(template.id) { mutableStateOf(template.exercises) }
    var editing by remember { mutableStateOf<Exercise?>(null) }
    var deleting by remember { mutableStateOf<Exercise?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    EditorPage("メニューを編集", back, "EDIT") {
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
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(TtSpace.Medium)) {
                    NumberDot(index + 1)
                    Column(Modifier.weight(1f)) {
                        Text(exercise.name, style = MaterialTheme.typography.titleMedium)
                        Text(
                            "${targetText(exercise)} × ${exercise.sets}セット · 休憩${exercise.restSeconds}秒${if (exercise.bilateral) " · 左右あり" else ""}",
                            style = MaterialTheme.typography.bodySmall,
                            color = colors.onSurfaceVariant,
                        )
                    }
                }
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    IconButton({ editing = exercise }) { Icon(Symbols.Edit, contentDescription = "${exercise.name}を編集") }
                    IconButton({
                        exercises = exercises.toMutableList().also {
                            it.add(index + 1, exercise.copy(id = UUID.randomUUID().toString(), name = "${exercise.name} のコピー"))
                        }
                    }) { Icon(Symbols.ContentCopy, contentDescription = "${exercise.name}を複製") }
                    IconButton(
                        { exercises = exercises.toMutableList().also { val moved = it.removeAt(index); it.add(index - 1, moved) } },
                        enabled = index > 0,
                    ) { Icon(Symbols.ArrowUpward, contentDescription = "${exercise.name}を上へ移動") }
                    IconButton(
                        { exercises = exercises.toMutableList().also { val moved = it.removeAt(index); it.add(index + 1, moved) } },
                        enabled = index < exercises.lastIndex,
                    ) { Icon(Symbols.ArrowDownward, contentDescription = "${exercise.name}を下へ移動") }
                    Spacer(Modifier.weight(1f))
                    IconButton({ deleting = exercise }, colors = IconButtonDefaults.iconButtonColors(contentColor = colors.error)) {
                        Icon(Symbols.Delete, contentDescription = "${exercise.name}を削除")
                    }
                }
            }
        }
        SubButton("種目を追加", { editing = Exercise(UUID.randomUUID().toString(), "") }, Modifier.fillMaxWidth(), exercises.size < Limits.TEMPLATE_EXERCISES, Symbols.Add)
        if (exercises.size >= Limits.TEMPLATE_EXERCISES) HelpText("1つのメニューに入れられる種目は${Limits.TEMPLATE_EXERCISES}までです。")
        error?.let { Text(it, color = colors.error) }
        MainButton("メニューを保存", {
            error = when {
                name.isBlank() -> "メニュー名を入力してください。"
                name.length > Limits.NAME -> "メニュー名は${Limits.NAME}文字以内にしてください。"
                exercises.isEmpty() -> "種目を1つ以上追加してください。"
                else -> null
            }
            if (error == null) save(template.copy(name = name.trim(), exercises = exercises))
        }, Modifier.fillMaxWidth(), icon = Symbols.Check)
        HelpText("保存するまでは元のメニューに反映されません。")
    }
    editing?.let { exercise ->
        ExerciseDialog(exercise, { editing = null }) { updated ->
            exercises = if (exercises.any { it.id == updated.id }) exercises.map { if (it.id == updated.id) updated else it } else exercises + updated
            editing = null
            error = null
        }
    }
    deleting?.let { exercise ->
        AlertDialog(
            onDismissRequest = { deleting = null },
            icon = { Icon(Symbols.Delete, contentDescription = null) },
            title = { Text("種目を削除しますか？") },
            text = { Text("このメニューから「${exercise.name}」を削除します。過去の実績は残ります。") },
            confirmButton = {
                TextButton({ exercises = exercises.filterNot { it.id == exercise.id }; deleting = null }, colors = ButtonDefaults.textButtonColors(contentColor = colors.error)) {
                    Text("削除する")
                }
            },
            dismissButton = { TextButton({ deleting = null }) { Text("キャンセル") } },
        )
    }
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
    val unit = if (mode == ExerciseMode.REPS) "回" else "秒"
    Dialog(dismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(
            Modifier.widthIn(max = 560.dp).fillMaxWidth().padding(TtSpace.Large).heightIn(max = 860.dp),
            shape = TtShapes.ExtraLarge,
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
        ) {
            Column(Modifier.verticalScroll(rememberScrollState()).padding(24.dp), verticalArrangement = Arrangement.spacedBy(TtSpace.Medium)) {
                Text("種目を編集", style = MaterialTheme.typography.headlineSmall)
                OutlinedTextField(name, { name = it }, label = { Text("種目名") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                Row(horizontalArrangement = Arrangement.spacedBy(TtSpace.Small)) {
                    TtChip(mode == ExerciseMode.REPS, { mode = ExerciseMode.REPS }, { Text("回数制") }, modifier = Modifier.heightIn(min = MinTouchTarget))
                    TtChip(mode == ExerciseMode.TIME, {
                        if (mode != ExerciseMode.TIME) {
                            min = "20"
                            max = "40"
                            seconds = "30"
                        }
                        mode = ExerciseMode.TIME
                    }, { Text("時間制") }, modifier = Modifier.heightIn(min = MinTouchTarget))
                }
                NumericField("セット数（1〜${Limits.SETS}）", sets, { sets = it })
                Row(horizontalArrangement = Arrangement.spacedBy(TtSpace.Medium)) {
                    NumericField("目標の下限（$unit）", min, { min = it }, Modifier.weight(1f))
                    NumericField("目標の上限（$unit）", max, { max = it }, Modifier.weight(1f))
                }
                HelpText("目標範囲は1〜${Limits.TARGET}${unit}で設定できます。")
                if (mode == ExerciseMode.TIME) NumericField("今回の目標時間（範囲内の秒数）", seconds, { seconds = it })
                NumericField("セット間休憩（0〜${Limits.REST_SECONDS}秒）", rest, { rest = it })
                Row(horizontalArrangement = Arrangement.spacedBy(TtSpace.Small)) {
                    listOf(90, 120, 180).forEach { value ->
                        TtChip(rest == value.toString(), { rest = value.toString() }, { Text("${value}秒") }, modifier = Modifier.heightIn(min = MinTouchTarget))
                    }
                }
                SettingSwitch("左右のある種目", "左→右の両側で1セット。切り替え時に開始を待ちます。", bilateral, { bilateral = it })
                OutlinedTextField(load, { load = it }, label = { Text("負荷（任意）") }, placeholder = { Text("リュック 5kg など") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                OutlinedTextField(difficulty, { difficulty = it }, label = { Text("難度・やり方") }, placeholder = { Text("通常、膝つき など") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
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
                    if (error == null) {
                        save(exercise.copy(
                            name = name.trim(), mode = mode, sets = sets.toInt(), targetMin = min.toInt(), targetMax = max.toInt(),
                            targetSeconds = if (mode == ExerciseMode.TIME) seconds.toInt() else exercise.targetSeconds,
                            restSeconds = rest.toInt(), bilateral = bilateral, load = load.trim(), difficulty = difficulty.trim(),
                        ))
                    }
                }, Modifier.fillMaxWidth(), icon = Symbols.Check)
                TextButton(dismiss, Modifier.fillMaxWidth().heightIn(min = MinTouchTarget)) { Text("キャンセル") }
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
    EditorPage("自由タイマー", back, "INTERVAL") {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(TtSpace.Large)) {
            IconBadge(Symbols.Timer, container = MaterialTheme.colorScheme.tertiaryContainer, size = 56.dp)
            Column(Modifier.weight(1f)) {
                Text("運動と休憩を繰り返す", style = MaterialTheme.typography.headlineSmall)
                HelpText("最初の準備カウントのあと、ラウンドが自動で進みます。")
            }
        }
        TtCard(Modifier.fillMaxWidth()) {
            NumericField("運動時間（1〜${Limits.TARGET_SECONDS}秒）", work, { work = it }, isError = work.toIntOrNull() !in 1..Limits.TARGET_SECONDS)
            NumericField("休憩時間（0〜${Limits.REST_SECONDS}秒）", rest, { rest = it }, isError = rest.toIntOrNull() !in 0..Limits.REST_SECONDS)
            NumericField("ラウンド数（1〜${Limits.SETS}）", rounds, { rounds = it }, isError = rounds.toIntOrNull() !in 1..Limits.SETS)
            OutlineAction("20秒＋10秒 × 8回に戻す", { work = "20"; rest = "10"; rounds = "8" }, Modifier.fillMaxWidth())
        }
        HelpText("この数字は時間の設定です。特定の研究と同じ運動強度や効果を表すものではありません。筋トレメニューとは別の記録に保存されます。")
        MainButton("タイマーを開始", {
            start(WorkoutPlan(
                "custom-interval", "自由インターバル",
                listOf(Exercise("custom-interval-exercise", "インターバル", ExerciseMode.TIME, rounds.toInt(), targetSeconds = work.toInt(), restSeconds = rest.toInt())),
                warmupSeconds = 0, kind = PlanKind.INTERVAL,
            ))
        }, Modifier.fillMaxWidth(), valid && !busy, Symbols.PlayArrow, LargeButtonHeight)
    }
}

@Composable
internal fun EditorPage(title: String, back: () -> Unit, display: String? = null, content: @Composable ColumnScope.() -> Unit) {
    Column(
        Modifier.widthIn(max = 720.dp).fillMaxSize().verticalScroll(rememberScrollState())
            .padding(start = TtSpace.Small, end = TtSpace.Large, top = TtSpace.Small, bottom = TtSpace.Large),
        verticalArrangement = Arrangement.spacedBy(TtSpace.Large),
    ) {
        BackTitle(title, back, display)
        Column(Modifier.padding(start = TtSpace.Small), verticalArrangement = Arrangement.spacedBy(TtSpace.Large), content = content)
        Spacer(Modifier.height(TtSpace.Large))
    }
}

@Composable
internal fun NumericField(
    label: String,
    value: String,
    change: (String) -> Unit,
    modifier: Modifier = Modifier,
    isError: Boolean = false,
    error: String? = null,
) {
    OutlinedTextField(
        value,
        { next -> if (next.length <= 5 && next.all(Char::isDigit)) change(next) },
        label = { Text(label) },
        modifier = modifier.fillMaxWidth(),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        singleLine = true,
        isError = isError,
        supportingText = if (isError && error != null) ({ Text(error) }) else null,
    )
}

@Composable
internal fun NumberStepper(label: String, value: Int, min: Int, max: Int, change: (Int) -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(TtSpace.Medium)) {
        Text(label, Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
        FilledTonalIconButton(
            { change(value - 1) },
            Modifier.size(MinTouchTarget).semantics { contentDescription = "${label}を減らす" },
            enabled = value > min,
        ) { Icon(Symbols.Remove, contentDescription = null) }
        Text(
            value.toString(),
            style = MaterialTheme.typography.headlineSmall.merge(TimerDigits),
            textAlign = TextAlign.Center,
            modifier = Modifier.widthIn(min = 56.dp),
        )
        FilledTonalIconButton(
            { change(value + 1) },
            Modifier.size(MinTouchTarget).semantics { contentDescription = "${label}を増やす" },
            enabled = value < max,
        ) { Icon(Symbols.Add, contentDescription = null) }
    }
}
