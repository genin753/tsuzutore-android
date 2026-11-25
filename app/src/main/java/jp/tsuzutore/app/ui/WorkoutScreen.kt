package jp.tsuzutore.app.ui

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import jp.tsuzutore.app.domain.*

private data class StageLook(val name: String, val icon: ImageVector, val ring: Color, val container: Color)

@Composable
private fun stageLook(stage: TimerStage?): StageLook {
    val colors = MaterialTheme.colorScheme
    return when (stage) {
        TimerStage.WARMUP -> StageLook("準備運動", Symbols.SelfImprovement, colors.secondary, colors.secondaryContainer)
        TimerStage.READY -> StageLook("開始待ち", Symbols.PlayArrow, colors.secondary, colors.secondaryContainer)
        TimerStage.PREPARING -> StageLook("まもなく開始", Symbols.Timer, colors.primary, colors.primaryContainer)
        TimerStage.EXERCISING -> StageLook("運動中", Symbols.FitnessCenter, colors.primary, colors.primaryContainer)
        TimerStage.SIDE_SWITCH -> StageLook("右側へ切り替え", Symbols.SwapHoriz, colors.secondary, colors.secondaryContainer)
        TimerStage.RESTING -> StageLook("休憩中", Symbols.Bedtime, colors.tertiary, colors.tertiaryContainer)
        else -> StageLook("準備中", Symbols.Timer, colors.secondary, colors.secondaryContainer)
    }
}

@Composable
internal fun WorkoutScreen(state: AppUiState, actions: AppUiActions, home: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    val run = state.run
    val session = run.session ?: return
    val exercise = run.exercise
    val finished = run.stage == TimerStage.COMPLETED || run.stage == TimerStage.INTERRUPTED
    var finishConfirmation by remember { mutableStateOf(false) }
    var skipConfirmation by remember { mutableStateOf<String?>(null) }
    var skipAction by remember { mutableStateOf<(() -> Unit)?>(null) }
    // Optional count for the current set; starts unrecorded again for every set and side.
    var reps by remember(session.id, run.exerciseIndex, run.setNumber, run.side) { mutableStateOf<Int?>(null) }
    val suggestion = remember(session.results, state.sessions, exercise?.id) {
        exercise?.let { suggestedReps(it, session, state.sessions) } ?: 1
    }
    val workoutScroll = rememberScrollState()
    LaunchedEffect(run.stage) {
        if (run.stage == TimerStage.PREPARING || run.stage == TimerStage.EXERCISING) workoutScroll.scrollTo(0)
    }

    KeepWorkoutScreenOn(run.active && state.settings.keepScreenOn)
    AppBackHandler { home() }
    Column(
        Modifier.widthIn(max = 720.dp).fillMaxSize().verticalScroll(workoutScroll).padding(horizontal = TtSpace.Large, vertical = TtSpace.Small),
        verticalArrangement = Arrangement.spacedBy(TtSpace.Large),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        if (finished) {
            CompletedWorkout(session, home)
        } else {
            val interval = session.plan.kind == PlanKind.INTERVAL
            val paused = run.stage == TimerStage.PAUSED
            val effectiveStage = if (paused) run.pausedStage else run.stage
            val look = stageLook(effectiveStage)
            val displayTimer = effectiveStage in setOf(TimerStage.WARMUP, TimerStage.PREPARING, TimerStage.RESTING) ||
                (exercise?.mode == ExerciseMode.TIME && effectiveStage == TimerStage.EXERCISING)
            val exercisingReps = exercise?.mode == ExerciseMode.REPS && effectiveStage == TimerStage.EXERCISING
            val canChooseExercise = !interval && effectiveStage in setOf(TimerStage.READY, TimerStage.RESTING, TimerStage.SIDE_SWITCH)

            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                IconButton(home) { Icon(Symbols.ArrowBack, contentDescription = "ホームへ") }
                Column(Modifier.weight(1f)) {
                    Text(
                        if (interval) "自由タイマー" else if (session.plan.shortened) "今日の短縮メニュー" else "今日のトレーニング",
                        style = MaterialTheme.typography.titleMedium,
                    )
                    Text(session.plan.title, style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant)
                }
                if (effectiveStage != TimerStage.WARMUP && exercise != null) {
                    Text(
                        "${if (interval) "ラウンド" else "セット"} ${run.setNumber} / ${exercise.sets}",
                        style = MaterialTheme.typography.titleMedium.merge(TimerDigits),
                        modifier = Modifier.padding(end = TtSpace.Small),
                    )
                }
            }

            StatusPill(
                if (paused) "一時停止中" else look.name,
                Modifier.semantics { liveRegion = LiveRegionMode.Polite },
                container = if (paused) colors.surfaceContainerHighest else look.container,
                icon = if (paused) Symbols.Pause else look.icon,
            )
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(TtSpace.XSmall)) {
                Text(
                    if (effectiveStage == TimerStage.WARMUP) "体をあたためましょう" else exercise?.name ?: session.plan.title,
                    style = MaterialTheme.typography.headlineMedium,
                    textAlign = TextAlign.Center,
                )
                if (effectiveStage != TimerStage.WARMUP && exercise != null) {
                    Text(
                        targetText(exercise) + when (run.side) { Side.LEFT -> " · 左側"; Side.RIGHT -> " · 右側"; else -> "" },
                        style = MaterialTheme.typography.bodyLarge,
                        color = colors.onSurfaceVariant,
                    )
                }
            }

            val total = run.elapsedMs + run.remainingMs
            TimerRing(
                progress = if (displayTimer && total > 0) run.remainingMs.toFloat() / total else null,
                color = look.ring,
                dimmed = paused,
            ) {
                when {
                    displayTimer -> RingClock(clockText(run.remainingMs), if (paused) "一時停止中" else "残り時間", "残り時間 ${clockText(run.remainingMs)}。表示は自動更新されます")
                    exercisingReps -> RingClock(clockText(run.elapsedMs), if (paused) "一時停止中" else "経過時間 · 自分のペースで", "セットの経過時間 ${clockText(run.elapsedMs)}")
                    else -> {
                        Icon(
                            if (effectiveStage == TimerStage.SIDE_SWITCH) Symbols.SwapHoriz else Symbols.PlayArrow,
                            contentDescription = null,
                            tint = colors.primary,
                            modifier = Modifier.size(64.dp),
                        )
                        Text(
                            if (effectiveStage == TimerStage.SIDE_SWITCH) "右側へ" else "準備OK？",
                            style = MaterialTheme.typography.headlineSmall,
                            modifier = Modifier.clearAndSetSemantics { },
                        )
                    }
                }
            }
            if (displayTimer) CountdownAdjustments(actions.adjustTime, !state.busy)

            when (run.stage) {
                TimerStage.WARMUP -> SubButton("準備運動をスキップ", actions.skipWarmup, Modifier.fillMaxWidth(), !state.busy, Symbols.SkipNext)
                TimerStage.READY, TimerStage.SIDE_SWITCH -> {
                    MainButton(
                        if (run.stage == TimerStage.SIDE_SWITCH) "右側の準備完了・開始" else "${exercise?.name ?: "セット"}を開始",
                        actions.beginSet, Modifier.fillMaxWidth(), !state.busy, Symbols.PlayArrow, LargeButtonHeight,
                    )
                    HelpText(if (run.stage == TimerStage.SIDE_SWITCH) "右側の準備ができたら、開始してください。" else "姿勢と道具を整えたら、開始してください。")
                }
                TimerStage.EXERCISING -> {
                    if (exercise?.mode == ExerciseMode.REPS) {
                        MainButton(
                            if (run.side == Side.LEFT) "左側を完了" else "セット完了",
                            {
                                val count = reps
                                if (count != null) actions.completeSetWithReps(count) else actions.completeSet()
                            },
                            Modifier.fillMaxWidth(), !state.busy, Symbols.Check, LargeButtonHeight,
                        )
                        RepEntry(reps, suggestion, { reps = it }, !state.busy)
                    } else {
                        SubButton("このセットをここまでで終了", actions.finishTimedSet, Modifier.fillMaxWidth(), !state.busy, Symbols.Flag)
                        if (exercise?.load?.isNotBlank() == true) HelpText("負荷：${exercise.load}")
                        HelpText("時間が来たら、このセットを記録します。")
                    }
                }
                TimerStage.RESTING -> HelpText(if (interval) "休憩後、次のラウンドが自動で始まります。" else "休憩が終わったら、準備完了の操作を待ちます。")
                TimerStage.PREPARING -> HelpText("姿勢を整えましょう。カウント後に始まります。")
                else -> Unit
            }
            if (run.stage != TimerStage.READY && run.stage != TimerStage.SIDE_SWITCH) {
                if (paused) MainButton("再開する", actions.pauseResume, Modifier.fillMaxWidth(), !state.busy, Symbols.PlayArrow, LargeButtonHeight)
                else OutlineAction("一時停止", actions.pauseResume, Modifier.fillMaxWidth(), !state.busy, Symbols.Pause)
            }
            if (paused) HelpText("計時を止めています。準備ができたら再開してください。")
            if (canChooseExercise) ExerciseSelection(session, run.exerciseIndex, !state.busy, actions.selectExercise)
            when {
                run.stage == TimerStage.WARMUP -> HelpText("足踏み・肩回し・軽いスクワット。無理のない範囲で動きましょう。")
                exercise?.bilateral == true && !paused -> HelpText("左→右の両側で1セットです。切り替えは準備ができてから。")
            }
            if (interval) {
                val nextText = when {
                    effectiveStage == TimerStage.WARMUP -> exercise?.name
                    effectiveStage == TimerStage.RESTING -> "${exercise?.name} · ${run.setNumber}ラウンド目"
                    run.setNumber < (exercise?.sets ?: 0) -> "${exercise?.name} · ${run.setNumber + 1}ラウンド目"
                    else -> null
                }
                TtCard(Modifier.fillMaxWidth(), containerColor = colors.surfaceContainerLow) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(TtSpace.Medium)) {
                        IconBadge(Symbols.SkipNext, container = colors.tertiaryContainer)
                        Column {
                            Text("このあと", style = MaterialTheme.typography.labelLarge, color = colors.onSurfaceVariant)
                            Text(nextText ?: "最後のラウンドです。", style = MaterialTheme.typography.titleMedium)
                        }
                    }
                }
            } else if (!canChooseExercise) {
                HelpText("セット間に、次にやる種目を自由に選べます。")
            }
            if (run.stage in setOf(TimerStage.READY, TimerStage.PREPARING, TimerStage.EXERCISING, TimerStage.SIDE_SWITCH, TimerStage.RESTING)) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(TtSpace.Small)) {
                    TextButton({ skipAction = actions.skipSet; skipConfirmation = "set" }, Modifier.weight(1f).heightIn(min = MinTouchTarget), enabled = !state.busy) {
                        Icon(Symbols.SkipNext, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("セットをスキップ")
                    }
                    TextButton({ skipAction = actions.skipExercise; skipConfirmation = "exercise" }, Modifier.weight(1f).heightIn(min = MinTouchTarget), enabled = !state.busy) {
                        Icon(Symbols.SkipNext, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("種目をスキップ")
                    }
                }
            }
            HorizontalDivider(color = colors.outlineVariant)
            TextButton(
                { finishConfirmation = true },
                Modifier.fillMaxWidth().heightIn(min = MinTouchTarget),
                enabled = !state.busy,
                colors = ButtonDefaults.textButtonColors(contentColor = colors.error),
            ) {
                Icon(Symbols.Stop, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text("今日はここで終了する")
            }
            HelpText("ホームへ戻っても進行は続きます。止めたいときは一時停止してください。")
        }
        Spacer(Modifier.height(TtSpace.Large))
    }
    if (finishConfirmation) {
        AlertDialog(
            onDismissRequest = { finishConfirmation = false },
            icon = { Icon(Symbols.Stop, contentDescription = null) },
            title = { Text("ここまでで終了しますか？") },
            text = { Text("完了したセットと、時間制の計測分を保存して終了します。") },
            confirmButton = {
                TextButton({ finishConfirmation = false; actions.finishWorkout() }, colors = ButtonDefaults.textButtonColors(contentColor = colors.error)) {
                    Text("保存して終了")
                }
            },
            dismissButton = { TextButton({ finishConfirmation = false }) { Text("トレーニングに戻る") } },
        )
    }
    skipConfirmation?.let { which ->
        AlertDialog(
            onDismissRequest = { skipConfirmation = null },
            icon = { Icon(Symbols.SkipNext, contentDescription = null) },
            title = { Text(if (which == "set") "このセットをスキップしますか？" else "この種目をスキップしますか？") },
            text = { Text("スキップしたセットは実施済みに数えません。") },
            confirmButton = { TextButton({ skipConfirmation = null; skipAction?.invoke(); skipAction = null }) { Text("スキップする") } },
            dismissButton = { TextButton({ skipConfirmation = null }) { Text("戻る") } },
        )
    }
}

/** Remaining time as an M3-style circular progress ring; the arc moves smoothly between the 1 s screen updates. */
@Composable
private fun TimerRing(progress: Float?, color: Color, dimmed: Boolean, content: @Composable ColumnScope.() -> Unit) {
    val track = MaterialTheme.colorScheme.surfaceContainerHighest
    val sweep by animateFloatAsState(progress ?: 0f, tween(durationMillis = 900, easing = LinearEasing), label = "timer-ring")
    BoxWithConstraints(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
        val diameter = minOf(maxWidth, 320.dp)
        Box(Modifier.size(diameter), contentAlignment = Alignment.Center) {
            Canvas(Modifier.fillMaxSize()) {
                val stroke = 18.dp.toPx()
                val topLeft = Offset(stroke / 2, stroke / 2)
                val arcSize = Size(size.width - stroke, size.height - stroke)
                drawArc(track, 0f, 360f, useCenter = false, topLeft = topLeft, size = arcSize, style = Stroke(stroke))
                if (progress != null && sweep > 0f) {
                    drawArc(
                        color.copy(alpha = if (dimmed) 0.45f else 1f), -90f, 360f * sweep.coerceIn(0f, 1f), useCenter = false,
                        topLeft = topLeft, size = arcSize, style = Stroke(stroke, cap = StrokeCap.Round),
                    )
                }
            }
            Column(
                Modifier.padding(36.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(TtSpace.XSmall),
                content = content,
            )
        }
    }
}

@Composable
private fun RingClock(value: String, label: String, description: String) {
    // Deliberately no live region: TalkBack must not announce every display tick.
    BoxWithConstraints(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
        val fontScale = LocalDensity.current.fontScale
        val size = minOf(76f, maxWidth.value / (value.length * 0.62f) / fontScale).sp
        Text(
            value,
            style = TimerDigits.copy(fontSize = size, lineHeight = size * 1.1f),
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            softWrap = false,
            modifier = Modifier.clearAndSetSemantics { contentDescription = description },
        )
    }
    Text(label, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
}

/** Recording the count stays optional: one tap on "セット完了" is still enough. */
@Composable
private fun RepEntry(reps: Int?, suggestion: Int, change: (Int?) -> Unit, enabled: Boolean) {
    if (reps == null) {
        TextButton({ change(suggestion) }, Modifier.fillMaxWidth().heightIn(min = MinTouchTarget), enabled = enabled) {
            Text("＋ 回数も記録する（任意）")
        }
    } else {
        TtCard(Modifier.fillMaxWidth()) {
            SectionTitle("このセットの回数")
            NumberStepper("回数", reps, 1, Limits.TARGET) { change(it) }
            HelpText("完了ボタンで、この回数も記録します。前回比較と負荷の目安に使います。")
            TextButton({ change(null) }, Modifier.heightIn(min = MinTouchTarget), enabled = enabled) { Text("回数は記録しない") }
        }
    }
}

@Composable
private fun CountdownAdjustments(adjust: (Int) -> Unit, enabled: Boolean) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(TtSpace.Medium)) {
        listOf(-10, 10).forEach { seconds ->
            FilledTonalButton(
                onClick = { adjust(seconds) },
                modifier = Modifier.weight(1f).heightIn(min = ButtonHeight).semantics {
                    contentDescription = if (seconds < 0) "残り時間を10秒短くする" else "残り時間を10秒長くする"
                },
                enabled = enabled,
                shape = TtShapes.Full,
            ) {
                Icon(if (seconds < 0) Symbols.Replay10 else Symbols.Forward10, contentDescription = null, modifier = Modifier.size(22.dp))
                Spacer(Modifier.width(TtSpace.Small))
                Text(if (seconds < 0) "−10秒" else "+10秒", style = MaterialTheme.typography.titleMedium)
            }
        }
    }
}

@Composable
private fun ExerciseSelection(session: WorkoutSession, currentIndex: Int, enabled: Boolean, select: (String) -> Unit) {
    val colors = MaterialTheme.colorScheme
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(TtSpace.Small)) {
        SectionTitle("次にやる種目を選ぶ")
        HelpText("好きな順番で開始できます。途中の種目は続きから再開します。")
        session.plan.exercises.forEachIndexed { index, option ->
            val pending = nextPendingSet(option, session.results)
            val finishedSets = completedExerciseSets(option, session.results)
            val available = pending != null
            Surface(
                onClick = { select(option.id) },
                enabled = enabled && available,
                modifier = Modifier.fillMaxWidth().heightIn(min = 72.dp),
                shape = TtShapes.LargeIncreased,
                color = if (index == currentIndex && available) colors.secondaryContainer else colors.surfaceContainer,
            ) {
                Row(
                    Modifier.padding(horizontal = TtSpace.Large, vertical = TtSpace.Medium),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(TtSpace.Medium),
                ) {
                    if (available) NumberDot(index + 1) else IconBadge(Symbols.Check, container = colors.surfaceContainerHighest, size = 32.dp)
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text(option.name, style = MaterialTheme.typography.titleSmall, color = if (available) colors.onSurface else colors.onSurfaceVariant)
                        Text(
                            "$finishedSets / ${option.sets}セット終了" + if (pending?.side == Side.RIGHT) " · 次は右側" else "",
                            style = MaterialTheme.typography.bodySmall,
                            color = colors.onSurfaceVariant,
                        )
                    }
                    if (available) Icon(Symbols.PlayArrow, contentDescription = null, tint = colors.primary)
                    else Text("終了", style = MaterialTheme.typography.labelLarge, color = colors.onSurfaceVariant)
                }
            }
        }
    }
}

@Composable
private fun CompletedWorkout(session: WorkoutSession, home: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    val performed = session.results.count { it.isPerformed() }
    val skipped = session.results.count { it.outcome == SetOutcome.SKIPPED }
    val title = when {
        performed == 0 -> "記録を保存しました"
        session.status == SessionStatus.COMPLETE -> "おつかれさまでした！"
        else -> "今日の一歩を記録しました"
    }
    Spacer(Modifier.height(TtSpace.XLarge))
    // A static finish mark respects the system's reduced-animation preference automatically.
    IconBadge(if (performed > 0) Symbols.Check else Symbols.Flag, container = colors.primaryContainer, size = 112.dp)
    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(TtSpace.XSmall)) {
        Text(title, style = MaterialTheme.typography.headlineMedium, textAlign = TextAlign.Center)
        Text(sessionLabel(session), style = MaterialTheme.typography.titleMedium, color = colors.primary)
    }
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(TtSpace.Medium)) {
        StatCard("実施した記録", performed, colors.primaryContainer, Modifier.weight(1f))
        StatCard("スキップ", skipped, colors.surfaceContainerHigh, Modifier.weight(1f))
    }
    TtCard(Modifier.fillMaxWidth()) {
        Text(session.plan.title, style = MaterialTheme.typography.titleLarge)
        HelpText(if (performed > 0) "自分のペースで積み重ねた分が、記録に残っています。" else "今回は実施セットがないため、週間の実施日には数えません。")
        if (session.plan.exercises.any { it.bilateral }) HelpText("左右の実績はそれぞれ1件として表示しています。")
    }
    MainButton("ホームに戻る", home, Modifier.fillMaxWidth(), icon = Symbols.Home, height = LargeButtonHeight)
}

@Composable
private fun StatCard(label: String, value: Int, container: Color, modifier: Modifier = Modifier) {
    Surface(modifier, shape = TtShapes.LargeIncreased, color = container, contentColor = contentColorFor(container)) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(value.toString(), style = MaterialTheme.typography.displayMedium.merge(TimerDigits))
            Text(label, style = MaterialTheme.typography.labelLarge)
        }
    }
}

internal fun sessionLabel(session: WorkoutSession): String {
    val kind = if (session.plan.kind == PlanKind.INTERVAL) "自由タイマー" else if (session.plan.shortened) "短縮メニュー" else "通常メニュー"
    val status = when (session.status) {
        SessionStatus.COMPLETE -> "完了"
        SessionStatus.PARTIAL -> "一部実施"
        SessionStatus.INTERRUPTED -> if (session.hasPerformed()) "中断・実績あり" else "中断・未実施"
        SessionStatus.ACTIVE -> "進行中"
    }
    return "$kind · $status"
}
