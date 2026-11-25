package jp.tsuzutore.app.ui

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimeInput
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

/** Calendar picker instead of typing YYYY-MM-DD. The picker works in UTC days; the result is a local date. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun DatePickDialog(initial: LocalDate, onDismiss: () -> Unit, onPick: (LocalDate) -> Unit) {
    val state = rememberDatePickerState(initialSelectedDateMillis = initial.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli())
    DatePickerDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(
                onClick = { state.selectedDateMillis?.let { onPick(Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate()) } },
                enabled = state.selectedDateMillis != null,
            ) { Text("決定") }
        },
        dismissButton = { TextButton(onDismiss) { Text("やめる") } },
    ) {
        DatePicker(state = state)
    }
}

/** 24-hour time entry with separate hour and minute fields. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun TimePickDialog(title: String, initialHour: Int, initialMinute: Int, onDismiss: () -> Unit, onPick: (Int, Int) -> Unit) {
    val state = rememberTimePickerState(initialHour = initialHour, initialMinute = initialMinute, is24Hour = true)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { TimeInput(state = state) },
        confirmButton = { TextButton({ onPick(state.hour, state.minute) }) { Text("決定") } },
        dismissButton = { TextButton(onDismiss) { Text("やめる") } },
    )
}

internal fun timeText(hour: Int, minute: Int): String = "%02d:%02d".format(hour, minute)
