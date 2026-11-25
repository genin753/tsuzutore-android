package jp.tsuzutore.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import jp.tsuzutore.app.domain.*

@Composable
internal fun WorkoutScreen(state: AppUiState, actions: AppUiActions, home: () -> Unit) {
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
        if (run.stage == TimerStage.PREPARING || run.stage == TimerStage.EXERCISING) {
            workoutScroll.scrollTo(0)
        }
    }

    KeepWorkoutScreenOn(run.active && state.settings.keepScreenOn)
    AppBackHandler { home() }
    Column(
        Modifier.widthIn(max = 720.dp).fillMaxSize().verticalScroll(workoutScroll).padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        if (finished) {
            CompletedWorkout(session, home)
        } else {
            Row(
                Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                TextButton(home, Modifier.heightIn(min = TtStyle.Target)) { Text("〈 ホーム") }
                Text(
                    if (session.plan.kind == PlanKind.INTERVAL) "自由タイマー" else if (session.plan.shortened) "今日の短縮メニュー" else "今日のトレーニング",
                    modifier = Modifier.weight(1f),
                    color = TtStyle.Muted,
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
            SportHeading("TIMER", "トレーニングタイマー")

            val stageName = when (run.stage) {
                TimerStage.WARMUP -> "準備運動"
                TimerStage.READY -> "開始待ち"
                TimerStage.PREPARING -> "まもなく開始"
                TimerStage.EXERCISING -> "運動中"
                TimerStage.SIDE_SWITCH -> "右側へ切り替え"
                TimerStage.RESTING -> "休憩中"
                TimerStage.PAUSED -> "一時停止中"
                else -> "準備中"
            }
            val effectiveStage = if (run.stage == TimerStage.PAUSED) run.pausedStage else run.stage
            val displayTimer = effectiveStage in setOf(TimerStage.WARMUP, TimerStage.PREPARING, TimerStage.RESTING) ||
                (exercise?.mode == ExerciseMode.TIME && effectiveStage == TimerStage.EXERCISING)
            val exercisingReps = exercise?.mode == ExerciseMode.REPS && effectiveStage == TimerStage.EXERCISING
            val canChooseExercise = session.plan.kind == PlanKind.WORKOUT &&
                effectiveStage in setOf(TimerStage.READY, TimerStage.RESTING, TimerStage.SIDE_SWITCH)

            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(6.dp),
                color = TtStyle.Dark,
                contentColor = TtStyle.LightInk,
            ) {
                Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            stageName,
                            color = TtStyle.Ink,
                            style = MaterialTheme.typography.labelLarge,
                            modifier = Modifier
                                .background(TtStyle.Accent, RoundedCornerShape(2.dp))
                                .padding(horizontal = 12.dp, vertical = 7.dp)
                                .semantics { liveRegion = LiveRegionMode.Polite },
                        )
                        if (effectiveStage != TimerStage.WARMUP && exercise != null) {
                            Text(
                                "${run.setNumber} / ${exercise.sets}",
                                modifier = Modifier.weight(1f),
                                color = TtStyle.Accent,
                                style = MaterialTheme.typography.titleLarge.copy(
                                    fontFamily = TtStyle.SportFont,
                                    fontFeatureSettings = "tnum",
                                ),
                            )
                        }
                    }
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(
                            if (effectiveStage == TimerStage.WARMUP) "体をあたためましょう" else exercise?.name ?: session.plan.title,
                            color = TtStyle.LightInk,
                            style = MaterialTheme.typography.headlineSmall,
                        )
                        if (effectiveStage != TimerStage.WARMUP && exercise != null) {
                            Text(
                                "${targetText(exercise)} · ${if (session.plan.kind == PlanKind.INTERVAL) "ラウンド" else "セット"}${when (run.side) { Side.LEFT -> " · 左側"; Side.RIGHT -> " · 右側"; else -> "" }}",
                                color = TtStyle.LightInk.copy(alpha = 0.72f),
                                style = MaterialTheme.typography.bodyMedium,
                            )
                        }
                    }

                    HorizontalDivider(color = TtStyle.LightInk.copy(alpha = 0.2f))
                    when {
                        displayTimer -> {
                            WorkoutClock(
                                value = clockText(run.remainingMs),
                                label = "残り時間",
                                description = "残り時間 ${clockText(run.remainingMs)}。表示は自動更新されます",
                            )
                            CountdownAdjustments(actions.adjustTime, !state.busy)
                        }
                        exercisingReps -> {
                            WorkoutClock(
                                value = clockText(run.elapsedMs),
                                label = "経過時間 · 自分のペースで",
                                description = "セットの経過時間 ${clockText(run.elapsedMs)}",
                            )

                        }
                        else -> {
                            BoxWithConstraints(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                                val fontScale = LocalDensity.current.fontScale
                                val word = if (effectiveStage == TimerStage.SIDE_SWITCH) "SWITCH" else "READY"
                                val wordSize = minOf(74f, maxWidth.value / (word.length * 0.58f * fontScale)).sp
                                Text(
                                    word,
                                    color = TtStyle.Accent,
                                    fontFamily = TtStyle.SportFont,
                                    fontWeight = FontWeight.Black,
                                    fontSize = wordSize,
                                    lineHeight = wordSize * 1.1f,
                                    maxLines = 1,
                                    modifier = Modifier.padding(vertical = 12.dp).clearAndSetSemantics { },
                                )
                            }
                        }
                    }
                }
            }


            when (run.stage) {
                TimerStage.WARMUP -> SubButton("準備運動をスキップ", actions.skipWarmup, Modifier.fillMaxWidth(), !state.busy)
                TimerStage.READY, TimerStage.SIDE_SWITCH -> {
                    MainButton(if (run.stage == TimerStage.SIDE_SWITCH) "右側の準備完了・開始" else "${exercise?.name ?: "セット"}を開始", actions.beginSet, Modifier.fillMaxWidth(), !state.busy)
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
                            Modifier.fillMaxWidth(),
                            !state.busy,
                        )
                        RepEntry(reps, suggestion, { reps = it }, !state.busy)
                    } else {
                        SubButton("このセットをここまでで終了", actions.finishTimedSet, Modifier.fillMaxWidth(), !state.busy)
                        if (exercise?.load?.isNotBlank() == true) HelpText("負荷：${exercise.load}")
                        HelpText("時間が来たら、このセットを記録します。")
                    }
                }
                TimerStage.RESTING -> {
                    HelpText(if (session.plan.kind == PlanKind.INTERVAL) "休憩後、次のラウンドが自動で始まります。" else "休憩が終わったら、準備完了の操作を待ちます。")
                }
                TimerStage.PAUSED -> Unit
                TimerStage.PREPARING -> HelpText("姿勢を整えましょう。カウント後に始まります。")
                else -> Unit
            }
            if (run.stage != TimerStage.READY && run.stage != TimerStage.SIDE_SWITCH) {
                if (run.stage == TimerStage.PAUSED) {
                    MainButton("再開する", actions.pauseResume, Modifier.fillMaxWidth(), !state.busy)
                } else {
                    SubButton("一時停止", actions.pauseResume, Modifier.fillMaxWidth(), !state.busy)
                }
            }
            if (canChooseExercise) {
                ExerciseSelection(session, run.exerciseIndex, !state.busy, actions.selectExercise)
            }
            if (run.stage == TimerStage.WARMUP) {
                HelpText("足踏み・肩回し・軽いスクワット。無理のない範囲で動きましょう。")
            } else if (run.stage == TimerStage.PAUSED) {
                HelpText("計時を止めています。準備ができたら再開してください。")
            } else if (exercise?.bilateral == true) {
                HelpText("左→右の両側で1セットです。切り替えは準備ができてから。")
            }
            if (session.plan.kind == PlanKind.INTERVAL) {
                val nextText = when {
                    effectiveStage == TimerStage.WARMUP -> exercise?.name
                    effectiveStage == TimerStage.RESTING -> "${exercise?.name} · ${run.setNumber}ラウンド目"
                    run.setNumber < (exercise?.sets ?: 0) -> "${exercise?.name} · ${run.setNumber + 1}ラウンド目"
                    else -> null
                }
                TtCard(Modifier.fillMaxWidth()) {
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text("NEXT", fontFamily = TtStyle.SportFont, fontWeight = FontWeight.Black, fontSize = 24.sp)
                        Text("このあとの予定", style = MaterialTheme.typography.labelLarge, color = TtStyle.Muted)
                    }
                    Text(nextText ?: "最後のラウンドです。", style = MaterialTheme.typography.titleMedium)
                }
            } else if (!canChooseExercise) {
                HelpText("セット間に、次にやる種目を自由に選べます。")
            }
            if (run.stage in setOf(TimerStage.READY, TimerStage.PREPARING, TimerStage.EXERCISING, TimerStage.SIDE_SWITCH, TimerStage.RESTING)) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TextButton({ skipAction = actions.skipSet; skipConfirmation = "set" }, Modifier.weight(1f).heightIn(min = TtStyle.Target), enabled = !state.busy) { Text("セットをスキップ") }
                    TextButton({ skipAction = actions.skipExercise; skipConfirmation = "exercise" }, Modifier.weight(1f).heightIn(min = TtStyle.Target), enabled = !state.busy) { Text("種目をスキップ") }
                }
            }
            HorizontalDivider()
            TextButton({ finishConfirmation = true }, Modifier.fillMaxWidth().heightIn(min = TtStyle.Target), enabled = !state.busy) { Text("今日はここで終了する", color = TtStyle.Red) }
            HelpText("ホームへ戻っても進行は続きます。止めたいときは一時停止してください。")
        }
        Spacer(Modifier.height(16.dp))
    }
    if (finishConfirmation) {
        AlertDialog(
            onDismissRequest = { finishConfirmation = false },
            title = { Text("ここまでで終了しますか？") },
            text = { Text("完了したセットと、時間制の計測分を保存して終了します。") },
            confirmButton = {
                TextButton({ finishConfirmation = false; actions.finishWorkout() }) { Text("保存して終了", color = TtStyle.Red) }
            },
            dismissButton = { TextButton({ finishConfirmation = false }) { Text("トレーニングに戻る") } },
        )
    }
    skipConfirmation?.let { which ->
        AlertDialog(
            onDismissRequest = { skipConfirmation = null },
            title = { Text(if (which == "set") "このセットをスキップしますか？" else "この種目をスキップしますか？") },
            text = { Text("スキップしたセットは実施済みに数えません。") },
            confirmButton = {
                TextButton({ skipConfirmation = null; skipAction?.invoke(); skipAction = null }) { Text("スキップする") }
            },
            dismissButton = { TextButton({ skipConfirmation = null }) { Text("戻る") } },
        )
    }
}

/** Recording the count stays optional: one tap on "セット完了" is still enough. */
@Composable
private fun RepEntry(reps: Int?, suggestion: Int, change: (Int?) -> Unit, enabled: Boolean) {
    if (reps == null) {
        TextButton({ change(suggestion) }, Modifier.fillMaxWidth().heightIn(min = TtStyle.Target), enabled = enabled) {
            Text("＋ 回数も記録する（任意）")
        }
    } else {
        TtCard(Modifier.fillMaxWidth()) {
            Text("このセットの回数", style = MaterialTheme.typography.titleMedium)
            NumberStepper("回数", reps, 1, Limits.TARGET) { change(it) }
            HelpText("完了ボタンで、この回数も記録します。前回比較と負荷の目安に使います。")
            TextButton({ change(null) }, Modifier.heightIn(min = TtStyle.Target), enabled = enabled) { Text("回数は記録しない") }
        }
    }
}

@Composable
private fun CountdownAdjustments(adjust: (Int) -> Unit, enabled: Boolean) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        listOf(-10, 10).forEach { seconds ->
            OutlinedButton(
                onClick = { adjust(seconds) },
                modifier = Modifier.weight(1f).heightIn(min = TtStyle.Target).semantics {
                    contentDescription = if (seconds < 0) "残り時間を10秒短くする" else "残り時間を10秒長くする"
                },
                enabled = enabled,
                shape = RoundedCornerShape(TtStyle.Radius),
                border = androidx.compose.foundation.BorderStroke(1.dp, TtStyle.Accent.copy(alpha = 0.6f)),
                colors = ButtonDefaults.outlinedButtonColors(
                    contentColor = TtStyle.Accent,
                    disabledContentColor = TtStyle.LightInk.copy(alpha = 0.4f),
                ),
            ) {
                Text(if (seconds < 0) "−10秒" else "+10秒", style = MaterialTheme.typography.titleMedium)
            }
        }
    }
}

@Composable
private fun ExerciseSelection(
    session: WorkoutSession,
    currentIndex: Int,
    enabled: Boolean,
    select: (String) -> Unit,
) {
    TtCard(Modifier.fillMaxWidth()) {
        Text("次にやる種目を選ぶ", style = MaterialTheme.typography.titleMedium)
        HelpText("好きな順番で開始できます。途中の種目は続きから再開します。")
        session.plan.exercises.forEachIndexed { index, option ->
            val pending = nextPendingSet(option, session.results)
            val finishedSets = completedExerciseSets(option, session.results)
            val available = pending != null
            OutlinedButton(
                onClick = { select(option.id) },
                modifier = Modifier.fillMaxWidth().heightIn(min = 72.dp),
                enabled = enabled && available,
                shape = RoundedCornerShape(TtStyle.Radius),
                border = androidx.compose.foundation.BorderStroke(1.dp, if (available) TtStyle.Ink else TtStyle.Border),
                colors = ButtonDefaults.outlinedButtonColors(
                    containerColor = if (index == currentIndex && available) TtStyle.AccentLight else MaterialTheme.colorScheme.surface,
                    contentColor = TtStyle.Ink,
                    disabledContainerColor = TtStyle.Background,
                    disabledContentColor = TtStyle.Muted,
                ),
                contentPadding = PaddingValues(horizontal = 14.dp, vertical = 12.dp),
            ) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text(option.name, style = MaterialTheme.typography.titleSmall)
                        Text(
                            "$finishedSets / ${option.sets}セット終了" + if (pending?.side == Side.RIGHT) " · 次は右側" else "",
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                    Text(if (available) "開始 →" else "終了", style = MaterialTheme.typography.labelLarge)
                }
            }
        }
    }
}

@Composable
private fun WorkoutClock(value: String, label: String, description: String) {
    Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
        // Deliberately no live region: TalkBack must not announce every display tick.
        BoxWithConstraints(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
            val fontScale = LocalDensity.current.fontScale
            val timerSize = minOf(96f, maxWidth.value / (value.length * 0.66f * fontScale)).sp
            Text(
                value,
                style = MaterialTheme.typography.displayLarge.copy(
                    fontFamily = TtStyle.SportFont,
                    fontFeatureSettings = "tnum",
                    fontWeight = FontWeight.Black,
                ),
                fontSize = timerSize,
                lineHeight = timerSize * 1.12f,
                maxLines = 1,
                softWrap = false,
                color = TtStyle.LightInk,
                modifier = Modifier.clearAndSetSemantics { contentDescription = description },
            )
        }
        Text(label, color = TtStyle.LightInk.copy(alpha = 0.72f), style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
private fun ColumnScope.CompletedWorkout(session: WorkoutSession, home: () -> Unit) {
    val performed = session.results.count { it.isPerformed() }
    val skipped = session.results.count { it.outcome == SetOutcome.SKIPPED }
    val title = when {
        performed == 0 -> "記録を保存しました"
        session.status == SessionStatus.COMPLETE -> "おつかれさまでした！"
        else -> "今日の一歩を記録しました"
    }
    Spacer(Modifier.height(16.dp))
    // A static finish mark respects the system's reduced-animation preference automatically.
    Surface(
        Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(6.dp),
        color = TtStyle.Dark,
        contentColor = TtStyle.LightInk,
    ) {
        Column(Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(22.dp)) {
            SportHeading("FINISH", "トレーニング記録", dark = true)
            Text(title, color = TtStyle.LightInk, style = MaterialTheme.typography.headlineMedium)
            HorizontalDivider(color = TtStyle.LightInk.copy(alpha = 0.2f))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(24.dp)) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(
                        performed.toString(),
                        fontFamily = TtStyle.SportFont,
                        fontWeight = FontWeight.Black,
                        color = TtStyle.Accent,
                        fontSize = 58.sp,
                        lineHeight = 64.sp,
                    )
                    Text("実施した記録", style = MaterialTheme.typography.bodyMedium, color = TtStyle.LightInk)
                }
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(
                        skipped.toString(),
                        fontFamily = TtStyle.SportFont,
                        fontWeight = FontWeight.Black,
                        color = TtStyle.LightInk,
                        fontSize = 58.sp,
                        lineHeight = 64.sp,
                    )
                    Text("スキップ", style = MaterialTheme.typography.bodyMedium, color = TtStyle.LightInk)
                }
            }
        }
    }
    HelpText(if (performed > 0) "自分のペースで積み重ねた分が、記録に残っています。" else "今回は実施セットがないため、週間の実施日には数えません。")
    TtCard(Modifier.fillMaxWidth()) {
        Text(session.plan.title, style = MaterialTheme.typography.titleLarge)
        Text(sessionLabel(session), style = MaterialTheme.typography.labelLarge, color = TtStyle.Ink)
        if (session.plan.exercises.any { it.bilateral }) HelpText("左右の実績はそれぞれ1件として表示しています。")
    }

    MainButton("ホームに戻る", home, Modifier.fillMaxWidth())
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
