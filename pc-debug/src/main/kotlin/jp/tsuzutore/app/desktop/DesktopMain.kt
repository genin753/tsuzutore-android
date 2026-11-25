package jp.tsuzutore.app.desktop

import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.*
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import jp.tsuzutore.app.ui.DesktopBackDispatcher
import jp.tsuzutore.app.ui.TsuzuToreApp
import jp.tsuzutore.app.ui.TsuzuToreTheme
import java.nio.file.Path

fun main() = application {
    val scope = rememberCoroutineScope()
    val path = remember {
        Path.of(System.getProperty("tsuzutore.desktop.data", "../.tooling/pc-debug-data/state.json")).toAbsolutePath().normalize()
    }
    val controller = remember { DesktopController(scope, path) }
    val state by controller.state.collectAsState()
    DisposableEffect(controller) { onDispose { controller.close() } }
    Window(
        onCloseRequest = { controller.close(); exitApplication() },
        title = "つづトレ — PCデバッグ",
        state = rememberWindowState(width = 430.dp, height = 860.dp),
        onPreviewKeyEvent = { event -> event.key == Key.Escape && event.type == KeyEventType.KeyUp && DesktopBackDispatcher.back() },
    ) {
        TsuzuToreTheme {
            Column(Modifier.fillMaxSize()) {
                Surface(color = MaterialTheme.colorScheme.tertiaryContainer, modifier = Modifier.fillMaxWidth()) {
                    Text(
                        "PCデバッグ · スマホとは別の記録",
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
                        style = MaterialTheme.typography.labelMedium,
                    )
                }
                Box(Modifier.weight(1f)) { TsuzuToreApp(state, controller.actions(state.run)) }
            }
        }
    }
}
