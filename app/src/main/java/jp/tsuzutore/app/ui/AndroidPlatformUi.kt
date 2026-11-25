package jp.tsuzutore.app.ui

import androidx.activity.compose.BackHandler
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import jp.tsuzutore.app.R

/** Anton (SIL Open Font License 1.1) from res/font; the license ships in assets/licenses/OFL-Anton.txt. */
internal val AntonFamily: FontFamily = FontFamily(Font(R.font.anton_regular))

@Composable
internal fun AppBackHandler(onBack: () -> Unit) { BackHandler(onBack = onBack) }

@Composable
internal fun KeepWorkoutScreenOn(enabled: Boolean) {
    val view = LocalView.current
    DisposableEffect(view, enabled) {
        view.keepScreenOn = enabled
        onDispose { view.keepScreenOn = false }
    }
}
