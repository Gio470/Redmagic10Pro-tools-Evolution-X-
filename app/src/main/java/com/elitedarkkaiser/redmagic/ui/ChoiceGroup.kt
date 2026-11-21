package com.elitedarkkaiser.redmagic.ui

import android.content.Context
import android.view.View
import android.widget.Toast
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.asComposePath
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.min

/** One option of a [ChoiceGroup]. [glyph] is an [Icons] glyph. */
data class Choice<T>(val label: String, val value: T, val glyph: String? = null)

/**
 * A single-choice control in one of the app's Material 3 Expressive styles:
 * - [Style.SHAPES]: each option is its own expressive shape; the picked one grows, fills and spins.
 * - [Style.CHIPS]: round icon chips; only the picked one opens up to show its name.
 * - [Style.LIQUID]: a dark track with a liquid blob that stretches to the picked label.
 *
 * [Style.AUTO] picks shapes for two or three options with icons, chips for more, and the liquid
 * track for options without icons.
 */
class ChoiceGroup<T>(
    context: Context,
    private val choices: List<Choice<T>>,
    selected: T,
    style: Style = Style.AUTO,
    private val onSelect: (T) -> Unit = {}
) {
    enum class Style { AUTO, SHAPES, CHIPS, LIQUID }

    private val selectedState = mutableIntStateOf(indexOf(selected).coerceAtLeast(0))
    private val enabledState = mutableStateOf(true)
    private val resolved = when {
        style != Style.AUTO -> style
        choices.any { it.glyph == null } -> Style.LIQUID
        choices.size <= 3 -> Style.SHAPES
        else -> Style.CHIPS
    }

    private fun indexOf(value: T) = choices.indexOfFirst { it.value == value }

    /** Assigning it only moves the selection; [onSelect] fires for taps alone. */
    var value: T
        get() = choices[selectedState.intValue].value
        set(newValue) {
            val index = indexOf(newValue)
            if (index >= 0) selectedState.intValue = index
        }

    var isEnabled: Boolean
        get() = enabledState.value
        set(value) { enabledState.value = value }

    val view: View = ComposeView(context).apply {
        setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
        setContent {
            MaterialTheme(colorScheme = M3(context).composeColorScheme()) {
                val pick: (Int) -> Unit = { index ->
                    if (index != selectedState.intValue) {
                        selectedState.intValue = index
                        onSelect(choices[index].value)
                    }
                }
                val enabled = enabledState.value
                val selectedIndex = selectedState.intValue
                Box(Modifier.alpha(if (enabled) 1f else M3.Metrics.disabledAlpha)) {
                    when (resolved) {
                        Style.SHAPES -> ShapeButtons(context, choices, selectedIndex, enabled, pick)
                        Style.CHIPS -> IconChips(context, choices, selectedIndex, enabled, pick)
                        else -> LiquidSwitch(choices.map { it.label }, selectedIndex, enabled, pick)
                    }
                }
            }
        }
    }
}

internal object ChoiceColors {
    fun of(m3: M3) = Palette(
        track = Color(m3.surfaceContainerLowest),
        tonal = Color(m3.surfaceContainerHighest),
        onTonal = Color(m3.onSurface),
        quiet = Color(m3.onSurfaceVariant),
        accent = Color(m3.primary),
        onAccent = Color(m3.onPrimary)
    )

    data class Palette(
        val track: Color, val tonal: Color, val onTonal: Color, val quiet: Color,
        val accent: Color, val onAccent: Color
    )
}

private val bouncy = spring<Float>(dampingRatio = 0.55f, stiffness = 380f)
private val bouncyDp = spring<Dp>(dampingRatio = 0.55f, stiffness = 380f)

@Composable
internal fun iconFont(context: Context): FontFamily? = remember {
    Icons.typeface(context)?.let { FontFamily(androidx.compose.ui.text.font.Typeface(it)) }
}

// ---- Shape buttons -----------------------------------------------------------------------------

@Composable
private fun <T> ShapeButtons(context: Context, choices: List<Choice<T>>, selected: Int, enabled: Boolean,
    onPick: (Int) -> Unit) {
    val colors = ChoiceColors.of(M3(context))
    val icons = iconFont(context)
    val shapes = listOf(CardShape.COOKIE_9, CardShape.SUNNY, CardShape.CLOVER, CardShape.FLOWER)
    Row(Modifier.fillMaxWidth().padding(top = 4.dp), horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.Bottom) {
        choices.forEachIndexed { index, choice ->
            val on = index == selected
            val interaction = remember { MutableInteractionSource() }
            val pressed by interaction.collectIsPressedAsState()
            val crowded = choices.size >= 4
            val size by animateDpAsState(
                if (on) (if (crowded) 76.dp else 88.dp) else (if (crowded) 58.dp else 68.dp), bouncyDp, label = "size")
            val spin by animateFloatAsState(if (on) 1.05f else 0f, bouncy, label = "spin")
            val squash by animateFloatAsState(if (pressed) 0.9f else 1f, bouncy, label = "press")
            val fill by animateColorAsState(if (on) colors.accent else colors.tonal, label = "fill")
            val ink by animateColorAsState(if (on) colors.onAccent else colors.onTonal, label = "ink")
            val shape = shapes[index % shapes.size]
            Column(
                Modifier
                    .semantics {
                        role = Role.RadioButton; this.selected = on
                        contentDescription = choice.label
                    }
                    .combinedClickable(interaction, indication = null, enabled = enabled) { onPick(index) },
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Box(Modifier.size(size).scale(squash), contentAlignment = Alignment.Center) {
                    val androidPath = remember { android.graphics.Path() }
                    Canvas(Modifier.fillMaxSize()) {
                        CardShape.outline(androidPath, this.size.width / 2, this.size.height / 2,
                            this.size.minDimension / 2, CardShape.CIRCLE, shape, 0.35f + 0.65f * min(1f, spin),
                            0.3f + spin)
                        drawPath(androidPath.asComposePath(), fill)
                    }
                    Text(choice.glyph.orEmpty(), color = ink, fontFamily = icons,
                        fontSize = (size.value * 0.32f).sp)
                }
                Text(choice.label, Modifier.padding(top = 8.dp), fontSize = if (crowded) 12.5.sp else 14.sp,
                    fontWeight = if (on) FontWeight.ExtraBold else FontWeight.SemiBold,
                    color = if (on) colors.onTonal else colors.quiet, maxLines = 1)
            }
        }
    }
}

// ---- Icon chips that open up -------------------------------------------------------------------

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun <T> IconChips(context: Context, choices: List<Choice<T>>, selected: Int, enabled: Boolean,
    onPick: (Int) -> Unit) {
    val colors = ChoiceColors.of(M3(context))
    val icons = iconFont(context)
    FlowRow(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)) {
        choices.forEachIndexed { index, choice ->
            val on = index == selected
            val interaction = remember { MutableInteractionSource() }
            val pressed by interaction.collectIsPressedAsState()
            val corner by animateDpAsState(if (on) 16.dp else 24.dp, bouncyDp, label = "corner")
            val squash by animateFloatAsState(if (pressed) 0.92f else 1f, bouncy, label = "press")
            val fill by animateColorAsState(if (on) colors.accent else colors.tonal, label = "fill")
            val ink by animateColorAsState(if (on) colors.onAccent else colors.onTonal, label = "ink")
            Row(
                Modifier
                    .scale(squash)
                    .heightIn(min = 48.dp)
                    .widthIn(min = 48.dp)
                    .clip(RoundedCornerShape(corner))
                    .background(fill)
                    .semantics {
                        role = Role.RadioButton; this.selected = on
                        contentDescription = choice.label
                    }
                    .combinedClickable(interaction, indication = null, enabled = enabled,
                        onLongClick = { Toast.makeText(context, choice.label, Toast.LENGTH_SHORT).show() }
                    ) { onPick(index) }
                    .animateContentSize(spring(dampingRatio = 0.7f, stiffness = 420f))
                    .padding(horizontal = if (on) 16.dp else 0.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center
            ) {
                Text(choice.glyph.orEmpty(), color = ink, fontFamily = icons, fontSize = 18.sp,
                    textAlign = TextAlign.Center, modifier = if (on) Modifier else Modifier.width(48.dp))
                if (on) {
                    Spacer(Modifier.width(8.dp))
                    Text(choice.label, color = ink, fontSize = 15.sp, fontWeight = FontWeight.ExtraBold,
                        maxLines = 1)
                }
            }
        }
    }
}

// ---- Liquid switch -----------------------------------------------------------------------------

@Composable
internal fun LiquidSwitch(labels: List<String>, selected: Int, enabled: Boolean, onPick: (Int) -> Unit) {
    // Up to four fit a phone width; longer sets wrap into tracks of three.
    val perRow = if (labels.size <= 4) labels.size else 3
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        labels.chunked(perRow).forEachIndexed { row, chunk ->
            val start = row * perRow
            val local = (selected - start).takeIf { it in chunk.indices } ?: -1
            LiquidTrack(chunk, local, enabled) { onPick(start + it) }
        }
    }
}

@Composable
private fun LiquidTrack(labels: List<String>, selected: Int, enabled: Boolean, onPick: (Int) -> Unit) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val colors = ChoiceColors.of(M3(context))
    val head = remember { Animatable(selected.coerceAtLeast(0).toFloat()) }
    val tail = remember { Animatable(selected.coerceAtLeast(0).toFloat()) }
    val shown by animateFloatAsState(if (selected >= 0) 1f else 0f, label = "shown")
    LaunchedEffect(selected) {
        if (selected < 0) return@LaunchedEffect
        if (shown < 0.5f) { head.snapTo(selected.toFloat()); tail.snapTo(selected.toFloat()); return@LaunchedEffect }
        // The front runs ahead on a lively spring; the back follows slowly, so the blob stretches.
        launch { head.animateTo(selected.toFloat(), spring(dampingRatio = 0.62f, stiffness = 420f)) }
        launch { tail.animateTo(selected.toFloat(), spring(dampingRatio = 0.9f, stiffness = 90f)) }
    }
    val density = LocalDensity.current
    BoxWithConstraints(
        Modifier.fillMaxWidth().height(56.dp).clip(RoundedCornerShape(28.dp)).background(colors.track)
    ) {
        val cell = with(density) { maxWidth.toPx() } / labels.size
        val inset = with(density) { 4.dp.toPx() }
        Canvas(Modifier.fillMaxSize().alpha(shown)) {
            val h = size.height - inset * 2
            val r = h / 2
            val y = size.height / 2
            val headX = cell * (head.value + 0.5f)
            val tailX = cell * (tail.value + 0.5f)
            val w = cell - inset * 2
            drawRoundRect(colors.accent, Offset(headX - w / 2, inset), Size(w, h), CornerRadius(r, r))
            val dist = abs(headX - tailX)
            if (dist > 1f) {
                val stretch = min(1f, dist / cell)
                val rt = r * (0.92f - 0.42f * stretch)
                drawCircle(colors.accent, rt, Offset(tailX, y))
                val neck = (rt * 0.55f) * (1f - 0.5f * stretch) + inset * 0.5f
                val mid = (headX + tailX) / 2
                val bridge = Path().apply {
                    moveTo(tailX, y - rt * 0.85f)
                    quadraticTo(mid, y - neck, headX, y - r * 0.85f)
                    lineTo(headX, y + r * 0.85f)
                    quadraticTo(mid, y + neck, tailX, y + rt * 0.85f)
                    close()
                }
                drawPath(bridge, colors.accent)
            }
        }
        Row(Modifier.fillMaxSize()) {
            labels.forEachIndexed { index, label ->
                val over = if (selected < 0) 0f else (1f - abs(head.value - index)).coerceIn(0f, 1f) * shown
                Box(
                    Modifier.weight(1f).fillMaxSize()
                        .semantics { role = Role.RadioButton; this.selected = index == selected }
                        .combinedClickable(remember { MutableInteractionSource() }, indication = null,
                            enabled = enabled) { onPick(index) },
                    contentAlignment = Alignment.Center
                ) {
                    Text(label, color = lerp(colors.quiet, colors.onAccent, over), fontSize = 15.sp,
                        fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(horizontal = 6.dp))
                }
            }
        }
    }
}
