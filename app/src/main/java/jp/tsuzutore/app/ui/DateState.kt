package jp.tsuzutore.app.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import kotlinx.coroutines.delay
import java.time.LocalDate

/** Updates local calendar UI when midnight, a clock change, or a timezone change occurs. */
@Composable
internal fun rememberToday(): LocalDate {
    val today by produceState(initialValue = LocalDate.now()) {
        while (true) {
            value = LocalDate.now()
            delay(30_000L)
        }
    }
    return today
}
