package jp.tsuzutore.app.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
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
    var selectedId by rememberSaveable(initialTemplate.id) { mutableStateOf(initialTemplate.id) }
    val template = state.templates.find { it.id == selectedId } ?: initialTemplate
    val today = rememberToday()
    val plan = createPlan(template, state.settings, today)
    EditorPage("トレーニングを開始", back) {
        Text("始める種目をタップ", style = MaterialTheme.typography.headlineSmall)
        HelpText("選んだ種目からすぐに開始。セット間も自由に切り替えられます。")
        if (state.templates.size > 1) {
            Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                state.templates.forEach { menu ->
                    FilterChip(selected = menu.id == template.id, onClick = { selectedId = menu.id }, label = { Text(menu.name) }, modifier = Modifier.heightIn(min = TtStyle.Target))
                }
            }
        }
        Text(template.name, style = MaterialTheme.typography.titleLarge)
        if (plan.firstWeek) HelpText("初週モード · 各種目2セット")
        plan.exercises.forEach { exercise ->
            OutlinedButton(
                onClick = { start(createQuickStartPlan(template, state.settings, today, exercise.id)) },
                enabled = !state.busy && !state.run.active,
                modifier = Modifier.fillMaxWidth().heightIn(min = 80.dp),
                shape = RoundedCornerShape(6.dp),
                border = BorderStroke(1.dp, TtStyle.Ink),
                contentPadding = PaddingValues(16.dp),
            ) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(exercise.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        Text("${targetText(exercise)} × ${exercise.sets}セット${if (exercise.bilateral) " · 左右" else ""}", style = MaterialTheme.typography.bodyMedium)
                    }
                    Text("開始 →", fontWeight = FontWeight.Bold)
                }
            }
        }
        SubButton("内容を調整・準備運動を入れる", { customize(template) }, Modifier.fillMaxWidth(), !state.busy && !state.run.active)
    }
}