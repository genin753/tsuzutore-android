package jp.tsuzutore.app.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import jp.tsuzutore.app.domain.*
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.util.Locale
import java.util.UUID

@Composable
internal fun ColumnScope.CalendarScreen(
    state: AppUiState,
    onSaveSchedule: (List<ScheduledWorkout>) -> Unit,
    onStart: (MenuTemplate) -> Unit,
) {
    val today = rememberToday()
    var selectedDay by rememberSaveable { mutableStateOf(today.toString()) }
    var visibleMonth by rememberSaveable { mutableStateOf(YearMonth.from(today).toString()) }
    var showEditor by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf<ScheduledWorkout?>(null) }
    var deleting by remember { mutableStateOf<ScheduledWorkout?>(null) }
    val selected = LocalDate.parse(selectedDay)
    val month = YearMonth.parse(visibleMonth)
    val schedule = sortedSchedule(state.settings.scheduledWorkouts)
    val eventsByDay = schedule.groupBy { it.date }
    val performedDays = state.sessions.filter { it.plan.kind == PlanKind.WORKOUT && it.wasPerformed() }.map { it.localDate }.toSet()
    val selectedEvents = eventsByDay[selectedDay].orEmpty()
    val selectedRecords = state.sessions.filter { it.localDate == selectedDay && it.wasPerformed() }.sortedBy { it.startedAt }
    val newEvent = {
        editing = null
        showEditor = true
    }

    SportHeading("CALENDAR", "トレーニングの予定")
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Column(Modifier.weight(1f)) {
            Text("${month.year}年 ${month.monthValue}月", style = MaterialTheme.typography.headlineSmall)
            HelpText("${schedule.count { it.date.startsWith(month.toString() + "-") }}件の予定")
        }
        TextButton(onClick = {
            selectedDay = today.toString()
            visibleMonth = YearMonth.from(today).toString()
        }, modifier = Modifier.heightIn(min = 48.dp)) { Text("今日") }
        OutlinedButton(onClick = { visibleMonth = month.minusMonths(1).toString() }, modifier = Modifier.size(48.dp).semantics { contentDescription = "前の月" }, contentPadding = PaddingValues(0.dp)) { Text("‹", fontSize = 26.sp) }
        OutlinedButton(onClick = { visibleMonth = month.plusMonths(1).toString() }, modifier = Modifier.size(48.dp).semantics { contentDescription = "次の月" }, contentPadding = PaddingValues(0.dp)) { Text("›", fontSize = 26.sp) }
    }

    Surface(shape = RoundedCornerShape(TtStyle.Radius), color = Color.White, border = BorderStroke(1.dp, TtStyle.Border)) {
        Column(Modifier.padding(horizontal = 4.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Row(Modifier.fillMaxWidth()) {
                listOf("月", "火", "水", "木", "金", "土", "日").forEachIndexed { index, label ->
                    Text(label, Modifier.weight(1f).padding(vertical = 6.dp), textAlign = TextAlign.Center, color = if (index == 6) TtStyle.Red else TtStyle.Muted, style = MaterialTheme.typography.labelMedium)
                }
            }
            monthCalendarDates(month).chunked(7).forEach { week ->
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                    week.forEach { date ->
                        val key = date.toString()
                        val currentMonth = YearMonth.from(date) == month
                        val count = eventsByDay[key]?.size ?: 0
                        val performed = key in performedDays
                        val recurring = date.dayOfWeek.value in state.settings.plannedDays
                        CalendarDay(
                            date, key == selectedDay, date == today, currentMonth,
                            count, performed, recurring,
                            Modifier.weight(1f),
                        ) {
                            selectedDay = key
                            if (!currentMonth) visibleMonth = YearMonth.from(date).toString()
                        }
                    }
                }
            }
        }
    }
    HelpText("● 予定　✓ 実施済み　薄黄：毎週の予定曜日")

    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(selected.format(DateTimeFormatter.ofPattern("M月d日（E）", Locale.JAPANESE)), style = MaterialTheme.typography.titleLarge)
            if (selected == today) Text("TODAY", fontFamily = TtStyle.SportFont, color = TtStyle.Muted, style = MaterialTheme.typography.labelLarge)
        }
        Text("${selectedEvents.size} PLANS", fontFamily = TtStyle.SportFont, fontWeight = FontWeight.Bold)
    }
    MainButton("＋ 予定を追加", newEvent, Modifier.fillMaxWidth(), !state.busy && state.templates.isNotEmpty() && schedule.size < 5000)
    if (state.templates.isEmpty()) HelpText("メニューを作ると、カレンダーに予定を追加できます。")
    if (schedule.size >= 5000) HelpText("予定が5,000件に達しています。不要な予定を削除してください。")
    if (selected.dayOfWeek.value in state.settings.plannedDays) {
        Text("毎週の予定曜日", color = TtStyle.Amber, style = MaterialTheme.typography.labelLarge)
        if (selectedEvents.isEmpty()) HelpText("この日はトレーニングの予定曜日です。メニューと時刻を決めて予定を追加できます。")
    }
    if (selectedEvents.isEmpty()) {
        TtCard(Modifier.fillMaxWidth()) {
            Text("この日の予定はまだありません", fontWeight = FontWeight.Bold)
            HelpText("日付を選んで、トレーニングする日を決めましょう。")
        }
    }
    selectedEvents.forEach { event ->
        val template = state.templates.find { it.id == event.templateId }
        TtCard(Modifier.fillMaxWidth()) {
            Row(verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(event.timeMinutes?.let { "%02d:%02d".format(it / 60, it % 60) } ?: "時刻なし", fontFamily = TtStyle.SportFont, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleLarge)
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(event.title, style = MaterialTheme.typography.titleMedium)
                    if (template != null && template.name != event.title) HelpText(template.name)
                }
            }
            if (template == null) HelpText("メニューが削除されています。編集からメニューを選び直してください。")
            MainButton("このメニューを開始", { template?.let(onStart) }, Modifier.fillMaxWidth(), template != null && !state.busy && !state.run.active)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                SubButton("編集", { editing = event; showEditor = true }, Modifier.weight(1f), !state.busy)
                TextButton(onClick = { deleting = event }, enabled = !state.busy, modifier = Modifier.weight(1f).heightIn(min = TtStyle.Target), colors = ButtonDefaults.textButtonColors(contentColor = TtStyle.Red)) { Text("削除") }
            }
        }
    }
    if (state.run.active && selectedEvents.isNotEmpty()) HelpText("進行中のトレーニングがあります。ホームから再開できます。")
    if (selectedRecords.isNotEmpty()) {
        Text("この日の実施記録", style = MaterialTheme.typography.titleMedium)
        selectedRecords.forEach { session ->
            TtCard(Modifier.fillMaxWidth(), TtStyle.GreenLight) {
                Text("✓ ${session.plan.title}", fontWeight = FontWeight.Bold, color = TtStyle.Green)
                HelpText("${session.results.count { it.wasPerformed() }}セットを実施")
            }
        }
    }
    HelpText("毎週の予定曜日は設定から変更できます。日付ごとの予定はこの画面で管理します。")

    if (showEditor) {
        ScheduleEditor(
            editing, selectedDay, state.templates, schedule, state.busy,
            onDismiss = { showEditor = false },
            onSave = { event ->
                onSaveSchedule(sortedSchedule(schedule.filterNot { it.id == event.id } + event))
                selectedDay = event.date
                visibleMonth = YearMonth.from(LocalDate.parse(event.date)).toString()
                showEditor = false
            },
        )
    }
    deleting?.let { event ->
        AlertDialog(
            onDismissRequest = { deleting = null },
            title = { Text("この予定を削除しますか？") },
            text = { Text("${event.date}\n${event.title}") },
            confirmButton = { TextButton(onClick = { onSaveSchedule(schedule.filterNot { it.id == event.id }); deleting = null }, enabled = !state.busy) { Text("削除", color = TtStyle.Red) } },
            dismissButton = { TextButton(onClick = { deleting = null }) { Text("キャンセル") } },
        )
    }
}

@Composable
private fun CalendarDay(
    date: LocalDate,
    isSelected: Boolean,
    isToday: Boolean,
    currentMonth: Boolean,
    eventCount: Int,
    performed: Boolean,
    recurring: Boolean,
    modifier: Modifier = Modifier,
    onSelect: () -> Unit,
) {
    val shape = RoundedCornerShape(4.dp)
    val fill = when {
        isSelected -> TtStyle.Ink
        recurring && currentMonth -> TtStyle.AccentLight
        else -> Color.White
    }
    val foreground = when {
        isSelected -> Color.White
        !currentMonth -> TtStyle.Muted
        else -> TtStyle.Ink
    }
    val description = buildString {
        append(date.format(DateTimeFormatter.ofPattern("yyyy年M月d日 E曜日", Locale.JAPANESE)))
        if (isToday) append("、今日")
        if (eventCount > 0) append("、予定${eventCount}件")
        if (performed) append("、トレーニング実施済み")
        if (recurring) append("、毎週の予定曜日")
    }
    Column(
        modifier.heightIn(min = 44.dp).aspectRatio(0.88f)
            .background(fill, shape)
            .then(if (isToday) Modifier.border(2.dp, TtStyle.Accent, shape) else Modifier)
            .selectable(selected = isSelected, role = Role.Button, onClick = onSelect)
            .semantics(mergeDescendants = true) { contentDescription = description },
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(date.dayOfMonth.toString(), color = foreground, fontWeight = if (isSelected || isToday) FontWeight.Bold else FontWeight.Medium, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.clearAndSetSemantics { })
        Row(Modifier.height(14.dp).clearAndSetSemantics { }, horizontalArrangement = Arrangement.spacedBy(3.dp), verticalAlignment = Alignment.CenterVertically) {
            if (eventCount > 0) Box(Modifier.size(5.dp).background(if (isSelected) TtStyle.Accent else TtStyle.Ink, RoundedCornerShape(3.dp)))
            if (performed) Text("✓", color = if (isSelected) TtStyle.Accent else TtStyle.Green, fontSize = 10.sp, lineHeight = 12.sp, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun ScheduleEditor(
    event: ScheduledWorkout?,
    initialDate: String,
    templates: List<MenuTemplate>,
    schedule: List<ScheduledWorkout>,
    busy: Boolean,
    onDismiss: () -> Unit,
    onSave: (ScheduledWorkout) -> Unit,
) {
    var date by remember(event) { mutableStateOf(event?.date ?: initialDate) }
    var templateId by remember(event) { mutableStateOf(event?.templateId ?: templates.firstOrNull()?.id.orEmpty()) }
    var title by remember(event) { mutableStateOf(event?.title ?: templates.firstOrNull()?.name.orEmpty().take(100)) }
    var time by remember(event) { mutableStateOf(event?.timeMinutes?.let { "%02d:%02d".format(it / 60, it % 60) }.orEmpty()) }
    var menuOpen by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val selectedTemplate = templates.find { it.id == templateId }
    val eventId = remember(event) { event?.id ?: UUID.randomUUID().toString() }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (event == null) "トレーニングの予定を追加" else "トレーニングの予定を編集") },
        text = {
            Column(Modifier.heightIn(max = 480.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(date, { if (it.length <= 10) date = it }, label = { Text("日付") }, placeholder = { Text("YYYY-MM-DD") }, singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Ascii), modifier = Modifier.fillMaxWidth())
                Box(Modifier.fillMaxWidth()) {
                    SubButton(selectedTemplate?.name ?: "メニューを選ぶ", { menuOpen = true }, Modifier.fillMaxWidth(), templates.isNotEmpty())
                    DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                        templates.forEach { template ->
                            DropdownMenuItem(text = { Text(template.name) }, onClick = { templateId = template.id; title = template.name.take(100); menuOpen = false })
                        }
                    }
                }
                OutlinedTextField(title, { if (it.length <= 100) title = it }, label = { Text("予定名") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(time, { if (it.length <= 5) time = it }, label = { Text("時刻（任意）") }, placeholder = { Text("19:00") }, supportingText = { Text("空欄なら時刻なしで保存します") }, singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Ascii), modifier = Modifier.fillMaxWidth())
                HelpText("当日はカレンダーの予定からトレーニングを開始できます。")
                error?.let { Text(it, color = TtStyle.Red) }
            }
        },
        confirmButton = {
            TextButton(enabled = !busy, onClick = {
                val timeValue = if (time.isBlank()) null else if (time.matches(Regex("\\d{2}:\\d{2}"))) {
                    val hour = time.substringBefore(':').toInt()
                    val minute = time.substringAfter(':').toInt()
                    if (hour in 0..23 && minute in 0..59) hour * 60 + minute else -1
                } else -1
                val candidate = ScheduledWorkout(eventId, date, templateId, title.trim(), timeValue)
                error = when {
                    selectedTemplate == null -> "メニューを選んでください"
                    else -> validateSchedule(schedule.filterNot { it.id == eventId } + candidate).firstOrNull()
                }
                if (error == null) onSave(candidate)
            }) { Text("保存") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("キャンセル") } },
    )
}