package jp.tsuzutore.app.ui

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import jp.tsuzutore.app.domain.*

@Composable
internal fun QuickStartScreen(
    state: AppUiState,
    initialTemplate: MenuTemplate,
    back: () -> Unit,
    start: (WorkoutPlan) -> Unit,
    customize: (MenuTemplate) -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    var selectedId by rememberSaveable(initialTemplate.id) { mutableStateOf(initialTemplate.id) }
    val template = state.templates.find { it.id == selectedId } ?: initialTemplate
    val today = rememberToday()
    val plan = createPlan(template, state.settings, today)
    val enabled = !state.busy && !state.run.active
    EditorPage("トレーニングを開始", back) {
        Column(verticalArrangement = Arrangement.spacedBy(TtSpace.XSmall)) {
            Text("始める種目をタップ", style = MaterialTheme.typography.headlineSmall)
            HelpText("選んだ種目からすぐに開始。セット間も自由に切り替えられます。")
        }
        if (state.templates.size > 1) {
            Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(TtSpace.Small)) {
                state.templates.forEach { menu ->
                    FilterChip(menu.id == template.id, { selectedId = menu.id }, { Text(menu.name) }, modifier = Modifier.heightIn(min = MinTouchTarget))
                }
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(TtSpace.Small)) {
            Text(template.name, style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
            if (plan.firstWeek) StatusPill("初週モード · 各種目2セット", container = colors.tertiaryContainer, icon = Symbols.LocalFireDepartment)
        }
        plan.exercises.forEachIndexed { index, exercise ->
            Surface(
                onClick = { start(createQuickStartPlan(template, state.settings, today, exercise.id)) },
                enabled = enabled,
                modifier = Modifier.fillMaxWidth().heightIn(min = 80.dp),
                shape = TtShapes.LargeIncreased,
                color = colors.surfaceContainer,
            ) {
                Row(
                    Modifier.padding(horizontal = TtSpace.Large, vertical = TtSpace.Medium),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(TtSpace.Large),
                ) {
                    NumberDot(index + 1)
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text(exercise.name, style = MaterialTheme.typography.titleMedium)
                        Text(
                            "${targetText(exercise)} × ${exercise.sets}セット${if (exercise.bilateral) " · 左右" else ""}",
                            style = MaterialTheme.typography.bodyMedium,
                            color = colors.onSurfaceVariant,
                        )
                    }
                    IconBadge(Symbols.PlayArrow, container = if (enabled) colors.primary else colors.surfaceContainerHighest, size = 44.dp)
                }
            }
        }
        OutlineAction("内容を調整・準備運動を入れる", { customize(template) }, Modifier.fillMaxWidth(), enabled, Symbols.Edit)
    }
}
