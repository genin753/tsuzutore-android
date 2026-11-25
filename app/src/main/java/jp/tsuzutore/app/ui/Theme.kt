package jp.tsuzutore.app.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.util.Locale

/** High-contrast training style with clear Japanese labels and generous touch targets. */
object TtStyle {
    val Ink = Color(0xFF111111)
    val Dark = Ink
    val LightInk = Color(0xFFF5F5F2)
    val Accent = Color(0xFFFFDC32)
    val OnAccent = Ink
    val AccentLight = Color(0xFFFFF5BF)
    val Muted = Color(0xFF5C5C57)
    val Background = LightInk
    val Border = Color(0xFFD6D6D1)
    val Green = Color(0xFF145C37)
    val GreenLight = Color(0xFFE4F0E7)
    val Amber = Color(0xFF755200)
    val AmberLight = Color(0xFFFFF1C7)
    val Red = Color(0xFFAA251B)

    // Existing screens use Blue for foreground text as well as fills.
    val Blue = Ink
    val BlueLight = AccentLight

    val SportFont = platformSportFont()
    val Space = 16.dp
    val Radius = 6.dp
    val Target = 52.dp
}

@Composable
fun TsuzuToreTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = lightColorScheme(
            primary = TtStyle.Ink,
            onPrimary = Color.White,
            primaryContainer = TtStyle.Accent,
            onPrimaryContainer = TtStyle.OnAccent,
            secondary = TtStyle.Ink,
            onSecondary = Color.White,
            secondaryContainer = TtStyle.AccentLight,
            onSecondaryContainer = TtStyle.Ink,
            tertiary = TtStyle.Green,
            onTertiary = Color.White,
            tertiaryContainer = TtStyle.GreenLight,
            onTertiaryContainer = TtStyle.Green,
            background = TtStyle.Background,
            onBackground = TtStyle.Ink,
            surface = Color.White,
            onSurface = TtStyle.Ink,
            surfaceVariant = Color(0xFFEEEDE7),
            onSurfaceVariant = TtStyle.Muted,
            surfaceTint = Color.Transparent,
            outline = Color(0xFF777770),
            outlineVariant = TtStyle.Border,
            inverseSurface = TtStyle.Dark,
            inverseOnSurface = TtStyle.LightInk,
            inversePrimary = TtStyle.Accent,
            error = TtStyle.Red,
            onError = Color.White,
            errorContainer = Color(0xFFFCE5E0),
            onErrorContainer = TtStyle.Red,
        ),
        typography = Typography(
            displayLarge = TextStyle(fontFamily = TtStyle.SportFont, fontSize = 64.sp, lineHeight = 68.sp, fontWeight = FontWeight.Black, letterSpacing = (-1).sp),
            displayMedium = TextStyle(fontFamily = TtStyle.SportFont, fontSize = 52.sp, lineHeight = 56.sp, fontWeight = FontWeight.Black, letterSpacing = (-0.5).sp),
            displaySmall = TextStyle(fontFamily = TtStyle.SportFont, fontSize = 40.sp, lineHeight = 44.sp, fontWeight = FontWeight.Bold),
            headlineLarge = TextStyle(fontSize = 30.sp, lineHeight = 40.sp, fontWeight = FontWeight.ExtraBold),
            headlineMedium = TextStyle(fontSize = 26.sp, lineHeight = 36.sp, fontWeight = FontWeight.ExtraBold),
            headlineSmall = TextStyle(fontSize = 23.sp, lineHeight = 32.sp, fontWeight = FontWeight.Bold),
            titleLarge = TextStyle(fontSize = 21.sp, lineHeight = 30.sp, fontWeight = FontWeight.Bold),
            titleMedium = TextStyle(fontSize = 18.sp, lineHeight = 27.sp, fontWeight = FontWeight.Bold),
            titleSmall = TextStyle(fontSize = 16.sp, lineHeight = 24.sp, fontWeight = FontWeight.Bold),
            bodyLarge = TextStyle(fontSize = 16.sp, lineHeight = 25.sp),
            bodyMedium = TextStyle(fontSize = 14.sp, lineHeight = 22.sp),
            bodySmall = TextStyle(fontSize = 12.sp, lineHeight = 19.sp),
            labelLarge = TextStyle(fontSize = 15.sp, lineHeight = 22.sp, fontWeight = FontWeight.Bold),
            labelMedium = TextStyle(fontSize = 13.sp, lineHeight = 20.sp, fontWeight = FontWeight.Bold),
            labelSmall = TextStyle(fontSize = 11.sp, lineHeight = 17.sp, fontWeight = FontWeight.Bold),
        ),
        shapes = Shapes(
            extraSmall = RoundedCornerShape(4.dp),
            small = RoundedCornerShape(6.dp),
            medium = RoundedCornerShape(TtStyle.Radius),
            large = RoundedCornerShape(8.dp),
            extraLarge = RoundedCornerShape(8.dp),
        ),
        content = content,
    )
}

@Composable
internal fun TtCard(
    modifier: Modifier = Modifier,
    containerColor: Color = MaterialTheme.colorScheme.surface,
    content: @Composable ColumnScope.() -> Unit,
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(TtStyle.Radius),
        color = containerColor,
        border = BorderStroke(1.dp, TtStyle.Border),
        tonalElevation = 0.dp,
        shadowElevation = 0.dp,
    ) {
        Column(Modifier.padding(TtStyle.Space), verticalArrangement = Arrangement.spacedBy(12.dp), content = content)
    }
}

@Composable
internal fun MainButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true) {
    Button(
        onClick = onClick,
        modifier = modifier.heightIn(min = TtStyle.Target),
        enabled = enabled,
        shape = RoundedCornerShape(TtStyle.Radius),
        colors = ButtonDefaults.buttonColors(
            containerColor = TtStyle.Accent,
            contentColor = TtStyle.OnAccent,
            disabledContainerColor = Color(0xFFE8E8E1),
            disabledContentColor = Color(0xFF6A6A62),
        ),
        elevation = ButtonDefaults.buttonElevation(defaultElevation = 0.dp, pressedElevation = 0.dp),
        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 15.dp),
    ) {
        Text(text, style = MaterialTheme.typography.labelLarge)
    }
}

@Composable
internal fun SubButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true) {
    OutlinedButton(
        onClick = onClick,
        modifier = modifier.heightIn(min = TtStyle.Target),
        enabled = enabled,
        shape = RoundedCornerShape(TtStyle.Radius),
        border = BorderStroke(1.dp, if (enabled) TtStyle.Ink else TtStyle.Border),
        colors = ButtonDefaults.outlinedButtonColors(contentColor = TtStyle.Ink, disabledContentColor = TtStyle.Muted),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 14.dp),
    ) {
        Text(text, style = MaterialTheme.typography.labelLarge)
    }
}

@Composable
internal fun SportHeading(
    english: String,
    japanese: String,
    modifier: Modifier = Modifier,
    dark: Boolean = false,
    subtitle: String? = null,
) {
    val foreground = if (dark) TtStyle.LightInk else TtStyle.Ink
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Box(Modifier.width(8.dp).height(48.dp).background(TtStyle.Accent))
            Text(
                english.uppercase(Locale.ROOT),
                modifier = Modifier.weight(1f),
                color = foreground,
                fontFamily = TtStyle.SportFont,
                fontWeight = FontWeight.Black,
                fontSize = 60.sp,
                lineHeight = 64.sp,
                letterSpacing = (-0.8).sp,
            )
        }
        Text(
            japanese,
            color = foreground,
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.semantics { heading() },
        )
        if (subtitle != null) {
            Text(
                subtitle,
                color = if (dark) Color(0xFFC9C9C1) else TtStyle.Muted,
                style = MaterialTheme.typography.bodyMedium,
            )
        }
    }
}

@Composable
internal fun PageTitle(title: String, description: String? = null) {
    val english = when (title) {
        "記録" -> "RECORD"
        "設定" -> "SETTINGS"
        "メニュー" -> "MENU"
        "運動と休憩を繰り返す" -> "INTERVAL"
        else -> null
    }
    if (english != null) {
        SportHeading(english, title, subtitle = description)
    } else {
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Box(Modifier.width(40.dp).height(4.dp).background(TtStyle.Accent))
            Text(title, style = MaterialTheme.typography.headlineMedium, modifier = Modifier.semantics { heading() })
            if (description != null) Text(description, color = TtStyle.Muted, style = MaterialTheme.typography.bodyMedium)
        }
    }
}

@Composable
internal fun HelpText(text: String) = Text(text, color = TtStyle.Muted, style = MaterialTheme.typography.bodyMedium)

internal fun clockText(ms: Long): String {
    val seconds = displaySeconds(ms)
    return "%02d:%02d".format(seconds / 60, seconds % 60)
}

internal fun targetText(exercise: jp.tsuzutore.app.domain.Exercise): String =
    if (exercise.mode == jp.tsuzutore.app.domain.ExerciseMode.REPS) "${exercise.targetMin}〜${exercise.targetMax}回" else "${exercise.targetSeconds}秒"
