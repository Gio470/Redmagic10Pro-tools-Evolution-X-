package com.elitedarkkaiser.redmagic.ui

import android.content.Context
import android.view.View
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.asComposePath
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.elitedarkkaiser.redmagic.ui.backgrounds.AnimatedBackground
import com.elitedarkkaiser.redmagic.ui.backgrounds.BackgroundType
import kotlin.math.hypot

/**
 * Settings › Background animation: a grid of small windows, each showing what its background looks
 * like. The picked one plays the real animation; the rest are still drawings, so only one runs.
 */
class BackgroundPicker(
    context: Context,
    private val options: List<BackgroundType>,
    selected: BackgroundType,
    private val onSelect: (BackgroundType) -> Unit
) {
    private val selectedState = mutableStateOf(selected)

    var value: BackgroundType
        get() = selectedState.value
        set(v) { selectedState.value = v }

    val view: View = ComposeView(context).apply {
        setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
        setContent {
            val m3 = M3(context)
            MaterialTheme(colorScheme = m3.composeColorScheme()) {
                val colors = ChoiceColors.of(m3)
                // Room above and beside the grid for the check badge, which sits over a tile's corner.
                Column(Modifier.padding(top = 6.dp, end = 6.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    options.chunked(3).forEach { row ->
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            row.forEach { type ->
                                Tile(context, type, type == selectedState.value, colors,
                                    Modifier.weight(1f)) {
                                    if (selectedState.value != type) {
                                        selectedState.value = type
                                        onSelect(type)
                                    }
                                }
                            }
                            repeat(3 - row.size) { Spacer(Modifier.weight(1f)) }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun Tile(context: Context, type: BackgroundType, on: Boolean, colors: ChoiceColors.Palette,
    modifier: Modifier, onClick: () -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val outer by animateDpAsState(if (on) 12.dp else 22.dp, spring(0.55f, 380f), label = "outer")
    val inner by animateDpAsState(if (on) 8.dp else 16.dp, spring(0.55f, 380f), label = "inner")
    val squash by animateFloatAsState(if (pressed) 0.94f else 1f, spring(0.55f, 380f), label = "press")
    val fill by animateColorAsState(if (on) colors.accent else colors.tonal, label = "fill")
    val ink by animateColorAsState(if (on) colors.onAccent else colors.onTonal, label = "ink")
    Box(modifier.scale(squash)) {
        Column(
            Modifier.fillMaxWidth()
                .clip(RoundedCornerShape(outer))
                .background(fill)
                .semantics { role = Role.RadioButton; selected = on }
                .clickable(interaction, indication = null, onClick = onClick)
                .padding(start = 6.dp, end = 6.dp, top = 6.dp, bottom = 9.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(Modifier.fillMaxWidth().height(60.dp).clip(RoundedCornerShape(inner)).background(colors.track)) {
                if (on && type != BackgroundType.NONE && type != BackgroundType.RANDOM) {
                    AnimatedBackground(type = type, enableParallax = false)
                } else {
                    Canvas(Modifier.fillMaxSize()) { thumbnail(type, colors.accent, colors.onTonal) }
                }
            }
            Text(type.displayName, Modifier.padding(top = 7.dp), color = ink, fontSize = 13.sp,
                fontWeight = FontWeight.Bold, maxLines = 1)
        }
        if (on) CheckBadge(colors, Modifier.align(Alignment.TopEnd).offset(6.dp, (-6).dp))
    }
}

@Composable
private fun CheckBadge(colors: ChoiceColors.Palette, modifier: Modifier) {
    val pop by animateFloatAsState(1f, spring(0.45f, 300f), label = "pop")
    val path = remember { android.graphics.Path() }
    Canvas(modifier.size(26.dp).scale(pop)) {
        CardShape.outline(path, size.width / 2, size.height / 2, size.minDimension / 2,
            CardShape.COOKIE_9, CardShape.COOKIE_9, 1f, 0.3f)
        drawPath(path.asComposePath(), colors.track)
        val inner = android.graphics.Path()
        CardShape.outline(inner, size.width / 2, size.height / 2, size.minDimension / 2 - 2.5.dp.toPx(),
            CardShape.COOKIE_9, CardShape.COOKIE_9, 1f, 0.3f)
        drawPath(inner.asComposePath(), colors.accent)
        val check = Path().apply {
            moveTo(size.width * 0.31f, size.height * 0.52f)
            lineTo(size.width * 0.44f, size.height * 0.65f)
            lineTo(size.width * 0.70f, size.height * 0.38f)
        }
        drawPath(check, colors.onAccent, style = Stroke(2.2.dp.toPx(), cap = StrokeCap.Round))
    }
}

/** A still sketch of each background, drawn into the tile's window. */
private fun DrawScope.thumbnail(type: BackgroundType, accent: Color, ink: Color) {
    val w = size.width
    val h = size.height
    val line = Stroke(1.5.dp.toPx(), cap = StrokeCap.Round)
    fun x(f: Float) = w * f
    fun y(f: Float) = h * f
    when (type) {
        BackgroundType.NONE -> {
            drawCircle(ink.copy(alpha = 0.35f), h * 0.26f, Offset(x(.5f), y(.5f)), style = line)
            drawLine(ink.copy(alpha = 0.35f), Offset(x(.5f) - h * .2f, y(.5f) - h * .2f),
                Offset(x(.5f) + h * .2f, y(.5f) + h * .2f), 1.5.dp.toPx())
        }
        BackgroundType.CIRCLES -> {
            drawCircle(accent.copy(alpha = .35f), h * .32f, Offset(x(.34f), y(.38f)))
            drawCircle(accent.copy(alpha = .6f), h * .24f, Offset(x(.66f), y(.66f)))
            drawCircle(accent.copy(alpha = .25f), h * .13f, Offset(x(.58f), y(.22f)))
        }
        BackgroundType.RINGS -> listOf(.14f, .26f, .38f, .5f).forEach {
            drawCircle(accent.copy(alpha = 1f - it), h * it, Offset(x(.5f), y(.5f)), style = line)
        }
        BackgroundType.MESH -> listOf(.28f, .45f, .62f, .79f).forEachIndexed { i, f ->
            val p = Path().apply {
                moveTo(0f, y(f))
                quadraticTo(x(.25f), y(f - .18f), x(.5f), y(f))
                quadraticTo(x(.75f), y(f + .18f), w, y(f))
            }
            drawPath(p, accent.copy(alpha = .3f + i * .15f), style = line)
        }
        BackgroundType.SPACE -> repeat(22) { i ->
            drawCircle(Color.White.copy(alpha = .4f + (i % 3) * .2f), if (i % 5 == 0) 1.8.dp.toPx() else 0.9.dp.toPx(),
                Offset(x(((i * 37) % 110) / 110f), y(((i * 23) % 58) / 58f)))
        }
        BackgroundType.SHAPES -> {
            rotate(18f, Offset(x(.25f), y(.42f))) {
                drawRect(accent, Offset(x(.25f) - h * .15f, y(.42f) - h * .15f), Size(h * .3f, h * .3f), style = line)
            }
            drawCircle(accent, h * .12f, Offset(x(.45f), y(.68f)), style = line)
            drawPath(Path().apply {
                moveTo(x(.62f), y(.72f)); lineTo(x(.71f), y(.38f)); lineTo(x(.8f), y(.72f)); close()
            }, accent, style = line)
        }
        BackgroundType.SNOW -> repeat(16) { i ->
            drawCircle(Color.White.copy(alpha = .7f), (1 + i % 3) * 0.9.dp.toPx(),
                Offset(x(((i * 29 + 7) % 110) / 110f), y(((i * 17 + 5) % 58) / 58f)))
        }
        BackgroundType.GRID -> for (i in 0 until 40) {
            val cx = x((8 + (i % 10) * 10.5f) / 110f)
            val cy = y((8 + (i / 10) * 13f) / 58f)
            val d = hypot(cx - x(.5f), cy - y(.5f)) / h
            drawCircle(accent.copy(alpha = (1f - d).coerceIn(.2f, 1f)), (3f - d * 3f).coerceAtLeast(.8f).dp.toPx(),
                Offset(cx, cy))
        }
        BackgroundType.PARTICLES -> {
            val pts = listOf(.13f to .2f, .36f to .52f, .27f to .86f, .6f to .28f, .76f to .69f, .89f to .2f, .53f to .8f)
                .map { Offset(x(it.first), y(it.second)) }
            pts.forEachIndexed { i, a ->
                pts.drop(i + 1).filter { hypot(a.x - it.x, a.y - it.y) < w * .32f }.forEach {
                    drawLine(accent.copy(alpha = .45f), a, it, 1.dp.toPx())
                }
            }
            pts.forEach { drawCircle(accent, 2.2.dp.toPx(), it) }
        }
        BackgroundType.MATRIX -> for (c in 0 until 9) for (r in 0 until 5) {
            drawRect(accent.copy(alpha = (r / 5f) * (.4f + (c % 2) * .5f)),
                Offset(x((6 + c * 12f) / 110f), y((4 + r * 11f + (c % 3) * 4) / 58f)),
                Size(3.dp.toPx(), 5.dp.toPx()))
        }
        BackgroundType.RANDOM -> {
            val s = h * .56f
            val o = Offset(x(.5f) - s / 2, y(.5f) - s / 2)
            drawRoundRect(accent, o, Size(s, s), androidx.compose.ui.geometry.CornerRadius(s * .22f), style = line)
            listOf(.28f to .28f, .5f to .5f, .72f to .72f).forEach { (fx, fy) ->
                drawCircle(accent, s * .08f, Offset(o.x + s * fx, o.y + s * fy))
            }
        }
    }
}
