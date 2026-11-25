package jp.tsuzutore.app.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import jp.tsuzutore.app.domain.Exercise
import jp.tsuzutore.app.domain.ExerciseMode

/*
 * Design system: Material Design 3 (Expressive, 2025) — https://m3.material.io
 *
 * Color  Android 12+ uses the colors of the user's wallpaper (dynamic color). Elsewhere the brand
 *        scheme below is used; it is generated from seed #2E9E5B (Vibrant variant) with Google's
 *        material-color-utilities, so every role keeps the M3 contrast guarantees in light and dark.
 * Shape  The M3 Expressive corner scale, as defined by androidx.compose.material3 1.4 ShapeTokens.
 * Type   The M3 type scale. Japanese body text gets a line height of at least 150% (Digital Agency
 *        Design System guideline) and no Latin letter spacing; headlines use emphasized weights.
 */

internal val BrandLight = lightColorScheme(
    primary = Color(0xFF006D38),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFF5FFF9B),
    onPrimaryContainer = Color(0xFF005229),
    inversePrimary = Color(0xFF00E47B),
    secondary = Color(0xFF3E6656),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFC0ECD7),
    onSecondaryContainer = Color(0xFF264E3F),
    tertiary = Color(0xFF1E695F),
    onTertiary = Color(0xFFFFFFFF),
    tertiaryContainer = Color(0xFFA9F0E3),
    onTertiaryContainer = Color(0xFF005048),
    background = Color(0xFFF1FDEF),
    onBackground = Color(0xFF141E16),
    surface = Color(0xFFF1FDEF),
    onSurface = Color(0xFF141E16),
    surfaceVariant = Color(0xFFD8E6D7),
    onSurfaceVariant = Color(0xFF3D4A3F),
    surfaceTint = Color(0xFF006D38),
    inverseSurface = Color(0xFF29332A),
    inverseOnSurface = Color(0xFFE9F4E7),
    error = Color(0xFFBA1A1A),
    onError = Color(0xFFFFFFFF),
    errorContainer = Color(0xFFFFDAD6),
    onErrorContainer = Color(0xFF93000A),
    outline = Color(0xFF6D7B6E),
    outlineVariant = Color(0xFFBCCABB),
    scrim = Color(0xFF000000),
    surfaceBright = Color(0xFFF1FDEF),
    surfaceContainer = Color(0xFFE6F1E4),
    surfaceContainerHigh = Color(0xFFE0EBDE),
    surfaceContainerHighest = Color(0xFFDAE6D9),
    surfaceContainerLow = Color(0xFFEBF7EA),
    surfaceContainerLowest = Color(0xFFFFFFFF),
    surfaceDim = Color(0xFFD2DDD0),
)

internal val BrandDark = darkColorScheme(
    primary = Color(0xFF00E47B),
    onPrimary = Color(0xFF00391A),
    primaryContainer = Color(0xFF005229),
    onPrimaryContainer = Color(0xFF5FFF9B),
    inversePrimary = Color(0xFF006D38),
    secondary = Color(0xFFA4D0BC),
    onSecondary = Color(0xFF0C3729),
    secondaryContainer = Color(0xFF264E3F),
    onSecondaryContainer = Color(0xFFC0ECD7),
    tertiary = Color(0xFF8DD4C7),
    onTertiary = Color(0xFF003731),
    tertiaryContainer = Color(0xFF005048),
    onTertiaryContainer = Color(0xFFA9F0E3),
    background = Color(0xFF0C160E),
    onBackground = Color(0xFFDAE6D9),
    surface = Color(0xFF0C160E),
    onSurface = Color(0xFFDAE6D9),
    surfaceVariant = Color(0xFF3D4A3F),
    onSurfaceVariant = Color(0xFFBCCABB),
    surfaceTint = Color(0xFF00E47B),
    inverseSurface = Color(0xFFDAE6D9),
    inverseOnSurface = Color(0xFF29332A),
    error = Color(0xFFFFB4AB),
    onError = Color(0xFF690005),
    errorContainer = Color(0xFF93000A),
    onErrorContainer = Color(0xFFFFDAD6),
    outline = Color(0xFF869487),
    outlineVariant = Color(0xFF3D4A3F),
    scrim = Color(0xFF000000),
    surfaceBright = Color(0xFF323C33),
    surfaceContainer = Color(0xFF18221A),
    surfaceContainerHigh = Color(0xFF222C24),
    surfaceContainerHighest = Color(0xFF2D372E),
    surfaceContainerLow = Color(0xFF141E16),
    surfaceContainerLowest = Color(0xFF071009),
    surfaceDim = Color(0xFF0C160E),
)

/** The M3 Expressive corner scale. */
internal object TtShapes {
    val ExtraSmall = RoundedCornerShape(4.dp)
    val Small = RoundedCornerShape(8.dp)
    val Medium = RoundedCornerShape(12.dp)
    val Large = RoundedCornerShape(16.dp)
    val LargeIncreased = RoundedCornerShape(20.dp)
    val ExtraLarge = RoundedCornerShape(28.dp)
    val ExtraLargeIncreased = RoundedCornerShape(32.dp)
    val ExtraExtraLarge = RoundedCornerShape(48.dp)
    val Full = CircleShape
}

/** Spacing on the 4dp grid. */
internal object TtSpace {
    val XSmall = 4.dp
    val Small = 8.dp
    val Medium = 12.dp
    val Large = 16.dp
    val XLarge = 24.dp
    val XXLarge = 32.dp
}

/** M3 minimum touch target, and the heights of the M3 Expressive medium and large buttons. */
internal val MinTouchTarget = 48.dp
internal val ButtonHeight = 56.dp
internal val LargeButtonHeight = 72.dp

/** Replace the wallpaper/brand colors or force light/dark, for previews and screenshot tests. */
internal val LocalColorSchemeOverride = staticCompositionLocalOf<ColorScheme?> { null }
internal val LocalDarkThemeOverride = staticCompositionLocalOf<Boolean?> { null }

private val Emphasized = FontWeight.Bold

private val TtTypography = Typography(
    displayLarge = TextStyle(fontSize = 57.sp, lineHeight = 64.sp, fontWeight = Emphasized, letterSpacing = 0.sp),
    displayMedium = TextStyle(fontSize = 45.sp, lineHeight = 52.sp, fontWeight = Emphasized, letterSpacing = 0.sp),
    displaySmall = TextStyle(fontSize = 36.sp, lineHeight = 44.sp, fontWeight = Emphasized, letterSpacing = 0.sp),
    headlineLarge = TextStyle(fontSize = 32.sp, lineHeight = 44.sp, fontWeight = Emphasized, letterSpacing = 0.sp),
    headlineMedium = TextStyle(fontSize = 28.sp, lineHeight = 38.sp, fontWeight = Emphasized, letterSpacing = 0.sp),
    headlineSmall = TextStyle(fontSize = 24.sp, lineHeight = 34.sp, fontWeight = Emphasized, letterSpacing = 0.sp),
    titleLarge = TextStyle(fontSize = 22.sp, lineHeight = 32.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 0.sp),
    titleMedium = TextStyle(fontSize = 16.sp, lineHeight = 24.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 0.sp),
    titleSmall = TextStyle(fontSize = 14.sp, lineHeight = 21.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 0.sp),
    bodyLarge = TextStyle(fontSize = 16.sp, lineHeight = 26.sp, letterSpacing = 0.sp),
    bodyMedium = TextStyle(fontSize = 14.sp, lineHeight = 22.sp, letterSpacing = 0.sp),
    bodySmall = TextStyle(fontSize = 12.sp, lineHeight = 18.sp, letterSpacing = 0.sp),
    labelLarge = TextStyle(fontSize = 14.sp, lineHeight = 20.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 0.sp),
    labelMedium = TextStyle(fontSize = 12.sp, lineHeight = 18.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 0.sp),
    labelSmall = TextStyle(fontSize = 11.sp, lineHeight = 16.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 0.sp),
)

/** Tabular digits, so a running timer does not shift sideways as numbers change. */
internal val TimerDigits = TextStyle(fontWeight = FontWeight.Bold, fontFeatureSettings = "tnum", letterSpacing = 0.sp)

@Composable
fun TsuzuToreTheme(content: @Composable () -> Unit) {
    val dark = LocalDarkThemeOverride.current ?: isSystemInDarkTheme()
    val colors = LocalColorSchemeOverride.current ?: platformDynamicColorScheme(dark) ?: if (dark) BrandDark else BrandLight
    MaterialTheme(
        colorScheme = colors,
        typography = TtTypography,
        shapes = Shapes(
            extraSmall = TtShapes.ExtraSmall,
            small = TtShapes.Small,
            medium = TtShapes.Medium,
            large = TtShapes.Large,
            extraLarge = TtShapes.ExtraLarge,
        ),
        content = content,
    )
}

/** A filled M3 card: tinted container, no border, generous corners. */
@Composable
internal fun TtCard(
    modifier: Modifier = Modifier,
    containerColor: Color = MaterialTheme.colorScheme.surfaceContainer,
    shape: Shape = TtShapes.LargeIncreased,
    content: @Composable ColumnScope.() -> Unit,
) {
    Surface(modifier = modifier, shape = shape, color = containerColor, contentColor = contentColorFor(containerColor)) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(TtSpace.Medium), content = content)
    }
}

/** Primary action: filled, pill-shaped, M3 Expressive medium height. */
@Composable
internal fun MainButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    icon: ImageVector? = null,
    height: Dp = ButtonHeight,
) {
    Button(
        onClick = onClick,
        modifier = modifier.heightIn(min = height),
        enabled = enabled,
        shape = TtShapes.Full,
        contentPadding = PaddingValues(horizontal = TtSpace.XLarge, vertical = TtSpace.Medium),
    ) { ButtonLabel(text, icon) }
}

/** Secondary action: filled tonal, pill-shaped. */
@Composable
internal fun SubButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    icon: ImageVector? = null,
) {
    FilledTonalButton(
        onClick = onClick,
        modifier = modifier.heightIn(min = MinTouchTarget),
        enabled = enabled,
        shape = TtShapes.Full,
        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 10.dp),
    ) { ButtonLabel(text, icon) }
}

/** Low-emphasis action with an outline. */
@Composable
internal fun OutlineAction(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    icon: ImageVector? = null,
) {
    OutlinedButton(
        onClick = onClick,
        modifier = modifier.heightIn(min = MinTouchTarget),
        enabled = enabled,
        shape = TtShapes.Full,
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 10.dp),
    ) { ButtonLabel(text, icon) }
}

@Composable
private fun ButtonLabel(text: String, icon: ImageVector?) {
    if (icon != null) {
        Icon(icon, contentDescription = null, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(TtSpace.Small))
    }
    Text(text, style = MaterialTheme.typography.labelLarge.copy(fontSize = 15.sp))
}

/** Large page title, as in an M3 large top app bar. */
@Composable
internal fun PageHeader(title: String, subtitle: String? = null, trailing: (@Composable RowScope.() -> Unit)? = null) {
    Column(Modifier.fillMaxWidth().padding(top = TtSpace.Small), verticalArrangement = Arrangement.spacedBy(TtSpace.XSmall)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(title, style = MaterialTheme.typography.headlineLarge, modifier = Modifier.weight(1f).semantics { heading() })
            trailing?.invoke(this)
        }
        if (subtitle != null) Text(subtitle, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/** Kept for existing callers: a page title with an optional description. */
@Composable
internal fun PageTitle(title: String, description: String? = null) = PageHeader(title, description)

@Composable
internal fun SectionTitle(text: String, modifier: Modifier = Modifier) {
    Text(text, style = MaterialTheme.typography.titleMedium, modifier = modifier.semantics { heading() })
}

@Composable
internal fun HelpText(text: String) = Text(text, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium)

/** Small pill label for states such as "運動中" or "完了". */
@Composable
internal fun StatusPill(
    text: String,
    modifier: Modifier = Modifier,
    container: Color = MaterialTheme.colorScheme.secondaryContainer,
    icon: ImageVector? = null,
) {
    Surface(modifier = modifier, shape = TtShapes.Full, color = container, contentColor = contentColorFor(container)) {
        Row(Modifier.padding(horizontal = 12.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            if (icon != null) {
                Icon(icon, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(6.dp))
            }
            Text(text, style = MaterialTheme.typography.labelLarge)
        }
    }
}

/** An icon inside a filled circle, used to lead list rows and cards. */
@Composable
internal fun IconBadge(
    icon: ImageVector,
    modifier: Modifier = Modifier,
    container: Color = MaterialTheme.colorScheme.secondaryContainer,
    size: Dp = 40.dp,
) {
    Surface(modifier = modifier.size(size), shape = TtShapes.Full, color = container, contentColor = contentColorFor(container)) {
        Box(contentAlignment = Alignment.Center) { Icon(icon, contentDescription = null, modifier = Modifier.size(size * 0.55f)) }
    }
}

/** Page container shared by the editor-style pages: back button, title, then content. */
@Composable
internal fun BackTitle(title: String, back: () -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        IconButton(back, Modifier.size(MinTouchTarget)) { Icon(Symbols.ArrowBack, contentDescription = "戻る") }
        Spacer(Modifier.width(TtSpace.XSmall))
        Text(title, style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f).semantics { heading() })
    }
}

internal fun clockText(ms: Long): String {
    val seconds = displaySeconds(ms)
    return "%02d:%02d".format(seconds / 60, seconds % 60)
}

internal fun targetText(exercise: Exercise): String =
    if (exercise.mode == ExerciseMode.REPS) "${exercise.targetMin}〜${exercise.targetMax}回" else "${exercise.targetSeconds}秒"
