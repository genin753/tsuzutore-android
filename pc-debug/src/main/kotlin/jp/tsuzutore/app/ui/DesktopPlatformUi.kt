package jp.tsuzutore.app.ui

import androidx.compose.material3.ColorScheme
import androidx.compose.runtime.*
import java.util.UUID

/** The desktop has no wallpaper colors; the brand scheme is used. */
@Composable
internal fun platformDynamicColorScheme(dark: Boolean): ColorScheme? = null

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
