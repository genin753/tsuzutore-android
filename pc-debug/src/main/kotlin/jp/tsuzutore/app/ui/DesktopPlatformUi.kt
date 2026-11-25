package jp.tsuzutore.app.ui

import androidx.compose.runtime.*
import androidx.compose.ui.text.font.FontFamily
import java.util.UUID

internal fun platformSportFont(): FontFamily = FontFamily.SansSerif

internal object DesktopBackDispatcher {
    private val callbacks = linkedMapOf<String, () -> Unit>()
    fun register(id: String, callback: () -> Unit) { callbacks[id] = callback }
    fun remove(id: String) { callbacks.remove(id) }
    fun back(): Boolean {
        val callback = callbacks.values.lastOrNull() ?: return false
        callback()
        return true
    }
}

@Composable
internal fun AppBackHandler(onBack: () -> Unit) {
    val latest by rememberUpdatedState(onBack)
    val id = remember { UUID.randomUUID().toString() }
    DisposableEffect(id) {
        DesktopBackDispatcher.register(id) { latest() }
        onDispose { DesktopBackDispatcher.remove(id) }
    }
}

@Composable
internal fun KeepWorkoutScreenOn(enabled: Boolean) { /* Android display flags have no desktop equivalent. */ }