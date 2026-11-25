package jp.tsuzutore.app.ui

import android.graphics.Typeface
import androidx.activity.compose.BackHandler
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontFamily

internal fun platformSportFont(): FontFamily = FontFamily(Typeface.create("sans-serif-condensed", Typeface.BOLD))

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