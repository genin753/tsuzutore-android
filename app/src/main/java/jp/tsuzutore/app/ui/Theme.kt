package jp.tsuzutore.app.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import jp.tsuzutore.app.domain.Exercise
import jp.tsuzutore.app.domain.ExerciseMode

/*
 * Design system: "Dark Athletic"
 * A Material Design 3 dark theme (https://m3.material.io/styles/color/system/overview) styled after
 * sports training apps: a black canvas, one neon accent, and condensed display type for numbers.
 *
 * Color  Always dark, so a workout in a dark room is not dazzling. Surfaces are neutral near-black
 *        with no hue tint, and text is off-white rather than pure white. One accent, Volt, marks
 *        actions, work and progress; Ice marks rest; Flare marks the last seconds of a countdown.
 *        Every text color meets WCAG AA (4.5:1) on the surfaces it is used on.
 * Type   Anton (SIL Open Font License 1.1) for English display words and numbers; it is tall and
 *        narrow, so a timer can fill the screen. It has no Japanese glyphs, so Japanese stays in the
 *        system font with a line height of at least 150% (Digital Agency Design System guideline).
 * Shape  The M3 Expressive corner scale (androidx.compose.material3 1.4 ShapeTokens).
 */

/** The accent and stage colors, for places that need them outside the color scheme roles. */
internal object Athletic {
    /** Neon yellow-green: primary actions, work and progress. 17:1 on the canvas. */
    val Volt = Color(0xFFD4FF3A)
    val OnVolt = Color(0xFF0A0A0A)
    /** Rest phases. 12:1 on black. */
    val Ice = Color(0xFF3DDCFF)
    /** The last seconds of a countdown. 6.7:1 on black. */
    val Flare = Color(0xFFFF5A1F)
    /** The deep red the timer background pulses to in the last seconds: a cue, never a white flash. */
    val Ember = Color(0xFF4A1405)
    /** Behind the running timer. */
    val Black = Color(0xFF000000)
    /** Behind every other screen: not pure black, so OLED pixels do not smear while scrolling. */
    val Canvas = Color(0xFF0A0A0A)
    val Ink = Color(0xFFEDEDED)
    val InkMuted = Color(0xFFA3A3A3)
}

internal val AthleticColors = darkColorScheme(
    primary = Athletic.Volt,
    onPrimary = Athletic.OnVolt,
    primaryContainer = Color(0xFF2A3400),
    onPrimaryContainer = Athletic.Volt,
    inversePrimary = Color(0xFF4D6000),
    secondary = Athletic.Ice,
    onSecondary = Color(0xFF00222B),
    secondaryContainer = Color(0xFF262626),
    onSecondaryContainer = Athletic.Ink,
    tertiary = Athletic.Flare,
    onTertiary = Athletic.OnVolt,
    tertiaryContainer = Color(0xFF3D1607),
    onTertiaryContainer = Color(0xFFFFC2A8),
    background = Athletic.Canvas,
    onBackground = Athletic.Ink,
    surface = Athletic.Canvas,
    onSurface = Athletic.Ink,
    surfaceVariant = Color(0xFF262626),
    onSurfaceVariant = Athletic.InkMuted,
    // Neutral, so tonal elevation lightens a surface instead of tinting it green.
    surfaceTint = Color(0xFF8A8A8A),
    inverseSurface = Athletic.Ink,
    inverseOnSurface = Color(0xFF1A1A1A),
    error = Color(0xFFFFB4AB),
    onError = Color(0xFF690005),
    errorContainer = Color(0xFF93000A),
    onErrorContainer = Color(0xFFFFDAD6),
    outline = Color(0xFF6B6B6B),
    outlineVariant = Color(0xFF2E2E2E),
    scrim = Color(0xFF000000),
    surfaceBright = Color(0xFF2C2C2C),
    surfaceContainer = Color(0xFF161616),
    surfaceContainerHigh = Color(0xFF1E1E1E),
    surfaceContainerHighest = Color(0xFF262626),
    surfaceContainerLow = Color(0xFF111111),
    surfaceContainerLowest = Color(0xFF050505),
    surfaceDim = Athletic.Canvas,
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

private val Emphasized = FontWeight.Bold

/** The M3 type scale in the system font, used by every Japanese text and by the M3 components. */
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

private val TightLines = LineHeightStyle(alignment = LineHeightStyle.Alignment.Center, trim = LineHeightStyle.Trim.Both)

/** Anton display type. Only for English words and numbers: it has no Japanese glyphs. */
internal object TtType {
    val Hero = TextStyle(fontFamily = AntonFamily, fontSize = 56.sp, lineHeight = 60.sp, letterSpacing = 0.5.sp, lineHeightStyle = TightLines)
    val Display = TextStyle(fontFamily = AntonFamily, fontSize = 44.sp, lineHeight = 48.sp, letterSpacing = 0.5.sp, lineHeightStyle = TightLines)
    val Headline = TextStyle(fontFamily = AntonFamily, fontSize = 30.sp, lineHeight = 34.sp, letterSpacing = 0.5.sp, lineHeightStyle = TightLines)
    val Title = TextStyle(fontFamily = AntonFamily, fontSize = 20.sp, lineHeight = 24.sp, letterSpacing = 1.sp, lineHeightStyle = TightLines)
    val Label = TextStyle(fontFamily = AntonFamily, fontSize = 13.sp, lineHeight = 16.sp, letterSpacing = 1.5.sp)
}

/** Tabular digits in the system font, for small times such as "19:00". */
internal val TimerDigits = TextStyle(fontWeight = FontWeight.Bold, fontFeatureSettings = "tnum", letterSpacing = 0.sp)

@Composable
fun TsuzuToreTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = AthleticColors,
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

/** A filled M3 card: a step lighter than the canvas, no border, generous corners. */
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

/** Primary action: Volt fill with black text, pill-shaped. */
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
    ) { ButtonLabel(text, icon, if (height >= LargeButtonHeight) 17.sp else 15.sp) }
}

/** Secondary action: dark gray fill, pill-shaped. */
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
private fun ButtonLabel(text: String, icon: ImageVector?, size: TextUnit = 15.sp) {
    if (icon != null) {
        Icon(icon, contentDescription = null, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(TtSpace.Small))
    }
    Text(text, style = MaterialTheme.typography.labelLarge.copy(fontSize = size, fontWeight = FontWeight.Bold))
}

/** A filter chip whose selected state is unmistakable on black: Volt fill, black label. */
@Composable
internal fun TtChip(selected: Boolean, onClick: () -> Unit, label: @Composable () -> Unit, modifier: Modifier = Modifier) {
    val colors = MaterialTheme.colorScheme
    FilterChip(
        selected = selected,
        onClick = onClick,
        label = label,
        modifier = modifier.heightIn(min = MinTouchTarget),
        shape = TtShapes.Full,
        colors = FilterChipDefaults.filterChipColors(
            selectedContainerColor = colors.primary,
            selectedLabelColor = colors.onPrimary,
            selectedLeadingIconColor = colors.onPrimary,
        ),
    )
}

/**
 * Page header: the Japanese title (a heading for screen readers) over a large English display word.
 * The English word repeats the title for impact, so it is hidden from screen readers.
 */
@Composable
internal fun PageHeader(display: String, title: String, subtitle: String? = null, trailing: (@Composable RowScope.() -> Unit)? = null) {
    Column(Modifier.fillMaxWidth().padding(top = TtSpace.Small), verticalArrangement = Arrangement.spacedBy(TtSpace.XSmall)) {
        Text(title, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary, modifier = Modifier.semantics { heading() })
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(display, style = TtType.Display, modifier = Modifier.weight(1f).clearAndSetSemantics { })
            trailing?.invoke(this)
        }
        if (subtitle != null) Text(subtitle, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

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

/** Back button and title for the editor-style pages, with an optional English display word below. */
@Composable
internal fun BackTitle(title: String, back: () -> Unit, display: String? = null) {
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(TtSpace.XSmall)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            IconButton(back, Modifier.size(MinTouchTarget)) { Icon(Symbols.ArrowBack, contentDescription = "戻る") }
            Spacer(Modifier.width(TtSpace.XSmall))
            Text(title, style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f).semantics { heading() })
        }
        if (display != null) {
            Text(display, style = TtType.Display, modifier = Modifier.padding(start = TtSpace.Small).clearAndSetSemantics { })
        }
    }
}

/** A circular progress ring with content in the middle; the arc eases to each new value. */
@Composable
internal fun ProgressRing(
    progress: Float,
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.primary,
    stroke: Dp = 12.dp,
    content: @Composable BoxScope.() -> Unit = {},
) {
    val track = MaterialTheme.colorScheme.surfaceContainerHighest
    val sweep by animateFloatAsState(progress.coerceIn(0f, 1f), tween(durationMillis = 600), label = "progress-ring")
    Box(modifier, contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val width = stroke.toPx()
            val topLeft = Offset(width / 2, width / 2)
            val arc = Size(size.width - width, size.height - width)
            drawArc(track, 0f, 360f, useCenter = false, topLeft = topLeft, size = arc, style = Stroke(width))
            if (sweep > 0f) {
                drawArc(color, -90f, 360f * sweep, useCenter = false, topLeft = topLeft, size = arc, style = Stroke(width, cap = StrokeCap.Round))
            }
        }
        content()
    }
}

/** Anton's advance widths in em: every digit but "1" is 0.494, the colon 0.242. Cells are a little wider. */
private fun cellEm(char: Char): Float = when {
    char.isDigit() -> 0.5f
    char == ':' -> 0.26f
    else -> 0.5f
}

/** Width of [text] in em when drawn by [StableNumber]; for sizing a number to the space it has. */
internal fun stableNumberEm(text: String): Float = text.fold(0f) { width, char -> width + cellEm(char) }

/**
 * A number that keeps its width while it changes, such as a running clock. Anton has no tabular
 * figures (its "1" is narrower), so every character sits in a fixed-width cell.
 */
@Composable
internal fun StableNumber(text: String, style: TextStyle, color: Color, modifier: Modifier = Modifier) {
    val density = LocalDensity.current
    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        text.forEach { char ->
            Box(Modifier.width(with(density) { (style.fontSize * cellEm(char)).toDp() }), contentAlignment = Alignment.Center) {
                Text(
                    char.toString(),
                    style = style,
                    color = color,
                    maxLines = 1,
                    softWrap = false,
                    modifier = Modifier.wrapContentWidth(unbounded = true),
                )
            }
        }
    }
}

internal fun clockText(ms: Long): String {
    val seconds = displaySeconds(ms)
    return "%02d:%02d".format(seconds / 60, seconds % 60)
}

/** "0:45" style, for the big timer digits: no leading zero, so the digits can be larger. */
internal fun shortClockText(ms: Long): String {
    val seconds = displaySeconds(ms)
    return "%d:%02d".format(seconds / 60, seconds % 60)
}

internal fun targetText(exercise: Exercise): String =
    if (exercise.mode == ExerciseMode.REPS) "${exercise.targetMin}〜${exercise.targetMax}回" else "${exercise.targetSeconds}秒"
