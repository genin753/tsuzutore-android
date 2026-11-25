package jp.tsuzutore.app.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
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
    val colors = MaterialTheme.colorScheme
    val today = rememberToday()
    var selectedDay by rememberSaveable { mutableStateOf(today.toString()) }
    var visibleMonth by rememberSaveable { mutableStateOf(YearMonth.from(today).toString()) }
    var showEditor by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf<ScheduledWorkout?>(null) }
    var deleting by remember { mutableStateOf<ScheduledWorkout?>(null) }
    val selected = LocalDate.parse(selectedDay)
    val month = YearMonth.parse(visibleMonth)
    val schedule = remember(state.settings.scheduledWorkouts) { sortedSchedule(state.settings.scheduledWorkouts) }
    val eventsByDay = remember(schedule) { schedule.groupBy { it.date } }
    val performedDays = remember(state.sessions) {
        state.sessions.filter { it.plan.kind == PlanKind.WORKOUT && it.wasPerformed() }.map { it.localDate }.toSet()
    }
    val selectedEvents = eventsByDay[selectedDay].orEmpty()
    val selectedRecords = state.sessions.filter { it.localDate == selectedDay && it.wasPerformed() }.sortedBy { it.startedAt }

    PageHeader("CALENDAR", "カレンダー", "トレーニングの予定")
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text("${month.year}年 ${month.monthValue}月", style = MaterialTheme.typography.titleLarge)
            HelpText("${schedule.count { it.date.startsWith(month.toString() + "-") }}件の予定")
        }
        TextButton(onClick = {
            selectedDay = today.toString()
            visibleMonth = YearMonth.from(today).toString()
        }, modifier = Modifier.heightIn(min = MinTouchTarget)) { Text("今日") }
        IconButton({ visibleMonth = month.minusMonths(1).toString() }) { Icon(Symbols.ChevronLeft, contentDescription = "前の月") }
        IconButton({ visibleMonth = month.plusMonths(1).toString() }) { Icon(Symbols.ChevronRight, contentDescription = "次の月") }
    }

    TtCard(Modifier.fillMaxWidth(), containerColor = colors.surfaceContainerLow) {
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Row(Modifier.fillMaxWidth()) {
                weekdayLabels.forEachIndexed { index, label ->
                    Text(
                        label,
                        Modifier.weight(1f).padding(vertical = TtSpace.XSmall),
                        textAlign = TextAlign.Center,
                        color = if (index == 6) colors.error else colors.onSurfaceVariant,
                        style = MaterialTheme.typography.labelMedium,
                    )
                }
            }
            monthCalendarDates(month).chunked(7).forEach { week ->
                Row(Modifier.fillMaxWidth()) {
                    week.forEach { date ->
                        val key = date.toString()
                        val currentMonth = YearMonth.from(date) == month
                        CalendarDay(
                            date, key == selectedDay, date == today, currentMonth,
                            eventsByDay[key]?.size ?: 0, key in performedDays, date.dayOfWeek.value in state.settings.plannedDays,
                        ) {
                            selectedDay = key
                            if (!currentMonth) visibleMonth = YearMonth.from(date).toString()
                        }
                    }
                }
            }
        }
    }
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(TtSpace.Small)) {
        Surface(Modifier.size(8.dp), shape = TtShapes.Full, color = colors.onSurface) {}
        Text("予定", style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant)
        Spacer(Modifier.width(TtSpace.XSmall))
        Icon(Symbols.Check, contentDescription = null, tint = colors.primary, modifier = Modifier.size(14.dp))
        Text("実施済み", style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant)
        Spacer(Modifier.width(TtSpace.XSmall))
        Surface(Modifier.size(14.dp), shape = TtShapes.Full, color = colors.secondaryContainer) {}
        Text("毎週の予定曜日", style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant)
    }

    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(TtSpace.Small)) {
        Text(selected.format(DateTimeFormatter.ofPattern("M月d日（E）", Locale.JAPANESE)), style = MaterialTheme.typography.titleLarge)
        if (selected == today) StatusPill("今日", container = colors.primaryContainer)
        Spacer(Modifier.weight(1f))
        Text("予定 ${selectedEvents.size}件", style = MaterialTheme.typography.labelLarge, color = colors.onSurfaceVariant)
    }
    MainButton(
        "予定を追加",
        { editing = null; showEditor = true },
        Modifier.fillMaxWidth(),
        !state.busy && state.templates.isNotEmpty() && schedule.size < 5000,
        Symbols.Add,
    )
    if (state.templates.isEmpty()) HelpText("メニューを作ると、カレンダーに予定を追加できます。")
    if (schedule.size >= 5000) HelpText("予定が5,000件に達しています。不要な予定を削除してください。")
    if (selected.dayOfWeek.value in state.settings.plannedDays) {
        StatusPill("毎週の予定曜日", container = colors.secondaryContainer, icon = Symbols.Event)
        if (selectedEvents.isEmpty()) HelpText("この日はトレーニングの予定曜日です。メニューと時刻を決めて予定を追加できます。")
    }
    if (selectedEvents.isEmpty()) {
        TtCard(Modifier.fillMaxWidth()) {
            SectionTitle("この日の予定はまだありません")
            HelpText("日付を選んで、トレーニングする日を決めましょう。")
        }
    }
    selectedEvents.forEach { event ->
        val template = state.templates.find { it.id == event.templateId }
        TtCard(Modifier.fillMaxWidth()) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(TtSpace.Large)) {
                Surface(shape = TtShapes.Large, color = colors.primaryContainer) {
                    Text(
                        event.timeMinutes?.let { timeText(it / 60, it % 60) } ?: "終日",
                        style = MaterialTheme.typography.titleMedium.merge(TimerDigits),
                        modifier = Modifier.padding(horizontal = TtSpace.Medium, vertical = TtSpace.Small),
                    )
                }
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(event.title, style = MaterialTheme.typography.titleMedium)
                    if (template != null && template.name != event.title) HelpText(template.name)
                }
            }
            if (template == null) HelpText("メニューが削除されています。編集からメニューを選び直してください。")
            MainButton("このメニューを開始", { template?.let(onStart) }, Modifier.fillMaxWidth(), template != null && !state.busy && !state.run.active, Symbols.PlayArrow)
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(TtSpace.Small)) {
                OutlineAction("編集", { editing = event; showEditor = true }, Modifier.weight(1f), !state.busy, Symbols.Edit)
                TextButton(
                    onClick = { deleting = event },
                    enabled = !state.busy,
                    modifier = Modifier.weight(1f).heightIn(min = MinTouchTarget),
                    colors = ButtonDefaults.textButtonColors(contentColor = colors.error),
                ) {
                    Icon(Symbols.Delete, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("削除")
                }
            }
        }
    }
    if (state.run.active && selectedEvents.isNotEmpty()) HelpText("進行中のトレーニングがあります。ホームから再開できます。")
    if (selectedRecords.isNotEmpty()) {
        SectionTitle("この日の実施記録")
        selectedRecords.forEach { session ->
            TtCard(Modifier.fillMaxWidth(), containerColor = colors.primaryContainer) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(TtSpace.Medium)) {
                    Icon(Symbols.CheckCircle, contentDescription = null)
                    Column {
                        Text(session.plan.title, style = MaterialTheme.typography.titleMedium)
                        Text("${session.results.count { it.wasPerformed() }}セットを実施", style = MaterialTheme.typography.bodyMedium)
                    }
                }
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
            icon = { Icon(Symbols.Delete, contentDescription = null) },
            title = { Text("この予定を削除しますか？") },
            text = { Text("${event.date}\n${event.title}") },
            confirmButton = {
                TextButton(
                    onClick = { onSaveSchedule(schedule.filterNot { it.id == event.id }); deleting = null },
                    enabled = !state.busy,
                    colors = ButtonDefaults.textButtonColors(contentColor = colors.error),
                ) { Text("削除") }
            },
            dismissButton = { TextButton(onClick = { deleting = null }) { Text("キャンセル") } },
        )
    }
}

@Composable
private fun RowScope.CalendarDay(
    date: LocalDate,
    isSelected: Boolean,
    isToday: Boolean,
    currentMonth: Boolean,
    eventCount: Int,
    performed: Boolean,
    recurring: Boolean,
    onSelect: () -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    val circle = when {
        isSelected -> colors.primary
        recurring && currentMonth -> colors.secondaryContainer
        else -> Color.Transparent
    }
    val foreground = when {
        isSelected -> colors.onPrimary
        !currentMonth -> colors.onSurfaceVariant.copy(alpha = 0.55f)
        recurring -> colors.onSecondaryContainer
        else -> colors.onSurface
    }
    val description = buildString {
        append(date.format(DateTimeFormatter.ofPattern("yyyy年M月d日 E曜日", Locale.JAPANESE)))
        if (isToday) append("、今日")
        if (eventCount > 0) append("、予定${eventCount}件")
        if (performed) append("、トレーニング実施済み")
        if (recurring) append("、毎週の予定曜日")
    }
    Column(
        Modifier.weight(1f).heightIn(min = 56.dp).clip(TtShapes.Medium)
            .selectable(selected = isSelected, role = Role.Button, onClick = onSelect)
            .semantics(mergeDescendants = true) { contentDescription = description }
            .padding(vertical = TtSpace.XSmall),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Surface(
            Modifier.size(36.dp),
            shape = TtShapes.Full,
            color = circle,
            border = if (isToday && !isSelected) BorderStroke(1.5.dp, colors.primary) else null,
        ) {
            Box(contentAlignment = Alignment.Center) {
                Text(
                    date.dayOfMonth.toString(),
                    color = foreground,
                    style = if (isSelected || isToday) MaterialTheme.typography.labelLarge else MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.clearAndSetSemantics { },
                )
            }
        }
        Row(Modifier.height(12.dp).clearAndSetSemantics { }, horizontalArrangement = Arrangement.spacedBy(3.dp), verticalAlignment = Alignment.CenterVertically) {
            if (eventCount > 0) Surface(Modifier.size(6.dp), shape = TtShapes.Full, color = colors.onSurface) {}
            if (performed) Icon(Symbols.Check, contentDescription = null, tint = colors.primary, modifier = Modifier.size(12.dp))
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
    var timeMinutes by remember(event) { mutableStateOf(event?.timeMinutes) }
    var menuOpen by remember { mutableStateOf(false) }
    var pickDate by remember { mutableStateOf(false) }
    var pickTime by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val selectedTemplate = templates.find { it.id == templateId }
    val eventId = remember(event) { event?.id ?: UUID.randomUUID().toString() }
    val parsedDate = runCatching { LocalDate.parse(date) }.getOrNull()
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Symbols.Event, contentDescription = null) },
        title = { Text(if (event == null) "トレーニングの予定を追加" else "トレーニングの予定を編集") },
        text = {
            Column(Modifier.heightIn(max = 480.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(TtSpace.Medium)) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(TtSpace.Small)) {
                    Column(Modifier.weight(1f)) {
                        Text("日付", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(parsedDate?.format(DateTimeFormatter.ofPattern("yyyy年M月d日（E）", Locale.JAPANESE)) ?: date, style = MaterialTheme.typography.titleMedium)
                    }
                    OutlineAction("変更", { pickDate = true }, Modifier.semantics { contentDescription = "日付を変更" }, icon = Symbols.CalendarMonth)
                }
                Box(Modifier.fillMaxWidth()) {
                    OutlineAction(selectedTemplate?.name ?: "メニューを選ぶ", { menuOpen = true }, Modifier.fillMaxWidth(), templates.isNotEmpty(), Symbols.FitnessCenter)
                    DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                        templates.forEach { template ->
                            DropdownMenuItem(text = { Text(template.name) }, onClick = { templateId = template.id; title = template.name.take(100); menuOpen = false })
                        }
                    }
                }
                OutlinedTextField(title, { if (it.length <= 100) title = it }, label = { Text("予定名") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(TtSpace.Small)) {
                    Column(Modifier.weight(1f)) {
                        Text("時刻（任意）", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(timeMinutes?.let { timeText(it / 60, it % 60) } ?: "時刻なし", style = MaterialTheme.typography.titleMedium)
                    }
                    OutlineAction("選ぶ", { pickTime = true }, Modifier.semantics { contentDescription = "時刻を選ぶ" }, icon = Symbols.Schedule)
                }
                if (timeMinutes != null) {
                    TextButton({ timeMinutes = null }, Modifier.heightIn(min = MinTouchTarget)) { Text("時刻なしにする") }
                }
                HelpText("当日はカレンダーの予定からトレーニングを開始できます。")
                error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            }
        },
        confirmButton = {
            TextButton(enabled = !busy, onClick = {
                val candidate = ScheduledWorkout(eventId, date, templateId, title.trim(), timeMinutes)
                error = when {
                    selectedTemplate == null -> "メニューを選んでください"
                    else -> validateSchedule(schedule.filterNot { it.id == eventId } + candidate).firstOrNull()
                }
                if (error == null) onSave(candidate)
            }) { Text("保存") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("キャンセル") } },
    )
    if (pickDate) {
        DatePickDialog(parsedDate ?: LocalDate.now(), { pickDate = false }) { picked ->
            date = picked.toString()
            pickDate = false
        }
    }
    if (pickTime) {
        val initial = timeMinutes ?: (19 * 60)
        TimePickDialog("予定の時刻", initial / 60, initial % 60, { pickTime = false }) { hour, minute ->
            timeMinutes = hour * 60 + minute
            pickTime = false
        }
    }
}
