package com.elitedarkkaiser.redmagic.ui

import android.animation.ArgbEvaluator
import android.animation.ValueAnimator
import android.annotation.SuppressLint
import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.RippleDrawable
import android.text.TextUtils
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.ViewTreeObserver
import android.view.animation.LinearInterpolator
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.graphics.ColorUtils
import androidx.dynamicanimation.animation.FloatPropertyCompat
import androidx.dynamicanimation.animation.SpringAnimation
import com.google.android.material.materialswitch.MaterialSwitch
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

/**
 * Material 3 Expressive's shape set, as smooth polar outlines: [lobes] bumps [depth] deep,
 * normalised so the outermost point sits on the radius. Close enough to MaterialShapes at icon
 * size, and cheap to morph -- two shapes blend by blending their radii angle by angle.
 */
enum class CardShape(private val lobes: Int, private val depth: Float) {
    CIRCLE(1, 0f),
    COOKIE_12(12, 0.045f),
    COOKIE_9(9, 0.07f),
    COOKIE_4(4, 0.11f),
    SOFT_BURST(10, 0.10f),
    PENTAGON(5, 0.055f),
    PILL(2, 0.16f),
    SUNNY(8, 0.06f),
    CLOVER(4, 0.2f),
    FLOWER(6, 0.12f);

    fun radius(theta: Float) = (1f + depth * cos(lobes * theta)) / (1f + depth)

    companion object {
        private val ICON_SHAPES = entries.filter { it != CIRCLE }

        /** A shape for [glyph] that stays the same every time the glyph is drawn. */
        fun forGlyph(glyph: String): CardShape = ICON_SHAPES[Math.floorMod(glyph.hashCode(), ICON_SHAPES.size)]

        private const val STEPS = 120
        private const val TWO_PI = (2 * PI).toFloat()

        /** Writes [from] morphed [f] of the way to [to] into [path]; [f] may overshoot. */
        fun outline(path: Path, cx: Float, cy: Float, r: Float, from: CardShape, to: CardShape,
            f: Float, rotation: Float) {
            path.rewind()
            for (i in 0..STEPS) {
                val theta = i.toFloat() / STEPS * TWO_PI
                val a = from.radius(theta)
                val radius = r * (a + (to.radius(theta) - a) * f)
                val x = cx + radius * cos(theta + rotation)
                val y = cy + radius * sin(theta + rotation)
                if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
            }
            path.close()
        }
    }
}

/**
 * A feature's icon in its expressive shape: filled with the accent and in [shape] while the feature
 * is on, a plain grey circle while it is off, and springing between the two on the motion preset.
 */
@SuppressLint("ViewConstructor")
class ShapeIcon(
    context: Context,
    internal val glyph: String,
    private val shape: CardShape,
    tertiary: Boolean
) : View(context) {
    private val m3 = M3(context)
    private val onFill = if (tertiary) m3.tertiary else m3.primary
    private val onInk = if (tertiary) m3.onTertiary else m3.onPrimary
    private val offFill = m3.surfaceContainerHighest
    private val offInk = m3.onSurfaceVariant
    private val path = Path()
    private val fill = Paint(Paint.ANTI_ALIAS_FLAG)
    private val ink = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        typeface = Icons.typeface(context)
        textAlign = Paint.Align.CENTER
    }
    private val evaluator = ArgbEvaluator()

    /** 0 is the off circle, 1 the on shape; a spring may carry it a little past either. */
    private var morph = 0f
    private val spring = SpringAnimation(this, MORPH, 0f)

    init {
        importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO
    }

    fun setOn(on: Boolean, animate: Boolean) {
        val target = if (on) 1f else 0f
        if (!animate) {
            spring.cancel()
            morph = target
            invalidate()
            return
        }
        MotionPrefs.animate(context, spring, MotionPrefs.Role.Button, pressing = on, target) {
            morph = it
            invalidate()
        }
    }

    override fun onDraw(canvas: Canvas) {
        val size = min(width, height).toFloat()
        val cx = width / 2f
        val cy = height / 2f
        val tone = morph.coerceIn(0f, 1f)
        fill.color = evaluator.evaluate(tone, offFill, onFill) as Int
        CardShape.outline(path, cx, cy, size / 2f, CardShape.CIRCLE, shape, morph, 0.3f)
        canvas.drawPath(path, fill)
        ink.color = evaluator.evaluate(tone, offInk, onInk) as Int
        ink.textSize = size * 0.4f
        val metrics = ink.fontMetrics
        canvas.drawText(glyph, cx, cy - (metrics.ascent + metrics.descent) / 2f, ink)
    }

    private companion object {
        val MORPH = object : FloatPropertyCompat<ShapeIcon>("morph") {
            override fun getValue(view: ShapeIcon) = view.morph
            override fun setValue(view: ShapeIcon, value: Float) {
                view.morph = value
                view.invalidate()
            }
        }
    }
}

/**
 * A glyph on a filled expressive shape: the leading icon of rows, blocks and page headers, drawn
 * the way the Home, Hardware and Software cards draw theirs.
 */
@SuppressLint("ViewConstructor")
class ShapeChip(
    context: Context,
    glyph: String,
    fill: Int,
    ink: Int,
    glyphSp: Float = 18f,
    private val shape: CardShape = CardShape.forGlyph(glyph)
) : TextView(context) {
    private val path = Path()
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = fill }

    init {
        text = glyph
        gravity = Gravity.CENTER
        M3(context).styleGlyph(this, glyphSp, ink)
        importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO
    }

    override fun onDraw(canvas: Canvas) {
        val size = min(width, height) / 2f
        CardShape.outline(path, width / 2f, height / 2f, size, shape, shape, 0f, 0.3f)
        canvas.drawPath(path, paint)
        super.onDraw(canvas)
    }
}

/**
 * M3 Expressive's wavy linear progress: a squiggle up to [progress], a gap, the flat track and the
 * stop dot at the end. The wave drifts while the bar is on screen and [moving] is set.
 */
class WavyBar(context: Context) : View(context) {
    private val m3 = M3(context)
    private val dp = context.resources.displayMetrics.density
    private val active = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE; strokeWidth = 5 * dp; strokeCap = Paint.Cap.ROUND; color = m3.primary
    }
    private val track = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE; strokeWidth = 5 * dp; strokeCap = Paint.Cap.ROUND
        color = m3.surfaceContainerHighest
    }
    private val dot = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = m3.primary }
    private val path = Path()
    private var phase = 0f
    private var shown = 0f

    var progress = 0f
        set(value) {
            val v = value.coerceIn(0f, 1f)
            if (v == field) return
            field = v
            ValueAnimator.ofFloat(shown, v).apply {
                duration = 450
                addUpdateListener { shown = it.animatedValue as Float; invalidate() }
                start()
            }
        }

    var moving = true
        set(value) { field = value; syncDrift() }

    private val drift = ValueAnimator.ofFloat(0f, (2 * PI).toFloat()).apply {
        duration = 1600
        repeatCount = ValueAnimator.INFINITE
        interpolator = LinearInterpolator()
        addUpdateListener { phase = it.animatedValue as Float; invalidate() }
    }

    init {
        importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        setMeasuredDimension(MeasureSpec.getSize(widthMeasureSpec), (18 * dp).toInt())
    }

    override fun onAttachedToWindow() { super.onAttachedToWindow(); syncDrift() }
    override fun onDetachedFromWindow() { drift.cancel(); super.onDetachedFromWindow() }
    override fun onVisibilityAggregated(isVisible: Boolean) { super.onVisibilityAggregated(isVisible); syncDrift() }

    private fun syncDrift() {
        val run = moving && isAttachedToWindow && isShown && ValueAnimator.areAnimatorsEnabled()
        if (run && !drift.isStarted) drift.start() else if (!run && drift.isStarted) drift.cancel()
    }

    override fun onDraw(canvas: Canvas) {
        val cy = height / 2f
        val start = 3 * dp
        val end = width - 3 * dp
        val reach = start + (end - start) * shown
        val amplitude = 3 * dp * min(1f, shown * 6f)
        val k = (2 * PI / (22 * dp)).toFloat()
        if (shown > 0.005f) {
            path.rewind()
            var x = start
            path.moveTo(x, cy + amplitude * sin(x * k - phase))
            while (x < reach) {
                x = min(reach, x + 1.5f * dp)
                path.lineTo(x, cy + amplitude * sin(x * k - phase))
            }
            canvas.drawPath(path, active)
        }
        val trackStart = reach + 9 * dp
        if (trackStart < end - 4 * dp) canvas.drawLine(trackStart, cy, end - 4 * dp, cy, track)
        canvas.drawCircle(end - 1 * dp, cy, 2.4f * dp, dot)
    }
}

/** A card extra that has live values to re-read while it is on screen. */
fun interface LiveExtra {
    fun refresh()
}

/**
 * One feature on the Hardware and Software tabs, as a Material 3 Expressive card: its icon in an
 * expressive shape, its switch, its title and what it is doing right now -- "Silent mode",
 * "3 games", "Breathe · Red" -- with the feature's icon drawn large and faint in the corner, and
 * the card's colour and blur from [CardStyle] like every other surface.
 */
@SuppressLint("ViewConstructor")
class ExpressiveCard(
    context: Context,
    private val item: IconifyGrid.Item,
    radii: FloatArray
) : FrameLayout(context) {
    private val m3 = M3(context)
    private val master = item.master
    private val openable = item.onClick != null
    private val wide = item.wide || item.hero

    private val onFill = m3.tintedSurface
    private val offFill = m3.surfaceContainer
    private val fill = BackdropFill().apply {
        cornerRadii = radii
        setColor(offFill)
    }
    private var fillColor = offFill
    private var fillAnimator: ValueAnimator? = null

    private val clip = Path()
    private val surface = object : FrameLayout(context) {
        override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
            clip.rewind()
            clip.addRoundRect(RectF(0f, 0f, w.toFloat(), h.toFloat()), radii, Path.Direction.CW)
        }

        // Keeps the big faint icon inside the card's own corners, whichever they are.
        override fun dispatchDraw(canvas: Canvas) {
            val save = canvas.save()
            canvas.clipPath(clip)
            super.dispatchDraw(canvas)
            canvas.restoreToCount(save)
        }
    }.apply {
        background = fill
        foreground = RippleDrawable(
            ColorStateList.valueOf(ColorUtils.setAlphaComponent(m3.onSurface, 26)), null,
            GradientDrawable().apply { cornerRadii = radii; setColor(Color.WHITE) }
        )
    }

    private val icon = ShapeIcon(context, item.glyph, item.shape, item.tertiary)
    private val watermark = TextView(context).apply {
        text = item.glyph
        gravity = Gravity.CENTER
        m3.styleGlyph(this, 140f, ColorUtils.setAlphaComponent(m3.onSurface, (255 * 0.05f).toInt()))
        translationX = m3.dpF(24f)
        translationY = m3.dpF(24f)
        importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO
    }
    private val titleView = TextView(context).apply {
        text = item.title
        setTextSize(TypedValue.COMPLEX_UNIT_SP, if (item.hero) 22f else 17f)
        typeface = android.graphics.Typeface.create(android.graphics.Typeface.SANS_SERIF, 800, false)
        letterSpacing = -0.01f
        setTextColor(m3.onSurface)
        maxLines = if (wide) 1 else 2
        ellipsize = TextUtils.TruncateAt.END
    }
    private val statusView = TextView(context).apply {
        setTextSize(TypedValue.COMPLEX_UNIT_SP, 13.5f)
        typeface = android.graphics.Typeface.create(android.graphics.Typeface.SANS_SERIF, 700, false)
        maxLines = 2
        ellipsize = TextUtils.TruncateAt.END
        setPadding(0, m3.dp(3), 0, 0)
    }
    private val extra: View? = item.extra?.invoke(context)

    private val mirror: MaterialSwitch? = master?.let { m ->
        MaterialSwitch(context).apply {
            m3.tintSwitch(this, item.glyph)
            bindMirror(this, m)
        }
    }
    private val chevron: TextView? = if (master == null && openable) TextView(context).apply {
        text = Icons.CHEVRON
        m3.styleGlyph(this, 15f, m3.onSurfaceVariant)
    } else null

    private var lastOn: Boolean? = null

    init {
        clipChildren = false
        clipToPadding = false
        addView(surface, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))
        surface.addView(watermark, LayoutParams(m3.dp(160), m3.dp(160), Gravity.BOTTOM or Gravity.END))

        val trailing: View? = mirror ?: chevron
        val iconSize = m3.dp(if (item.hero) 56 else 48)
        val pad = m3.dp(if (item.hero) 18 else 16)
        val body = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(pad, pad, pad, pad)
        }
        if (wide) {
            body.addView(LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                addView(icon, LinearLayout.LayoutParams(iconSize, iconSize))
                addView(LinearLayout(context).apply {
                    orientation = LinearLayout.VERTICAL
                    addView(titleView)
                    addView(statusView)
                }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply {
                    marginStart = m3.dp(14); marginEnd = m3.dp(10)
                })
                trailing?.let { addView(it) }
            })
            extra?.let {
                body.addView(it, LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
                ).apply { topMargin = m3.dp(14) })
            }
        } else {
            body.addView(LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.TOP
                addView(icon, LinearLayout.LayoutParams(iconSize, iconSize))
                addView(View(context), LinearLayout.LayoutParams(0, 1, 1f))
                trailing?.let { addView(it) }
            })
            body.addView(View(context), LinearLayout.LayoutParams(1, 0, 1f))
            body.addView(titleView)
            body.addView(statusView)
        }
        surface.addView(body, LayoutParams(LayoutParams.MATCH_PARENT,
            if (wide) LayoutParams.WRAP_CONTENT else LayoutParams.MATCH_PARENT))

        surface.setOnClickListener {
            performHapticFeedback(android.view.HapticFeedbackConstants.VIRTUAL_KEY)
            item.onClick?.invoke()
        }
        MotionPrefs.bindScalePress(surface, target = this, baseScale = PRESSED_SCALE,
            claim = { !surface.isClickable })
        render(animate = false)
    }

    /** Re-reads the feature: on or off, what it is doing, and the extra's live values. */
    fun render(animate: Boolean) {
        val on = master?.read() ?: true
        if (on != lastOn) {
            val first = lastOn == null
            lastOn = on
            icon.setOn(on, animate && !first)
            setFill(if (on) onFill else offFill, animate && !first)
            watermark.alpha = if (on) 1f else 0.6f
            surface.isClickable = openable && on
            surface.isFocusable = openable && on
            extra?.visibility = if (on) View.VISIBLE else View.GONE
            statusView.setTextColor(if (on) m3.onPrimaryContainer else m3.onSurfaceVariant)
        }
        val status = if (!on) "Off"
            else runCatching { item.status?.invoke() }.getOrNull() ?: item.subtitle ?: ""
        if (statusView.text.toString() != status) statusView.text = status
        if (on) (extra as? LiveExtra)?.refresh()
        surface.contentDescription = "${item.title}. $status"
    }

    private fun setFill(color: Int, animate: Boolean) {
        fillAnimator?.cancel()
        if (!animate) {
            fillColor = color
            fill.setColor(color)
            return
        }
        fillAnimator = ValueAnimator.ofArgb(fillColor, color).apply {
            duration = 220
            addUpdateListener {
                fillColor = it.animatedValue as Int
                fill.setColor(fillColor)
            }
            start()
        }
    }

    /**
     * Keeps the mirror on the real switch, per frame drawn: what moves the real switch is the page
     * this card opens, the status poll, and the hardware putting back a change it refused -- none
     * of which can tell a card. A boolean against a boolean; nothing happens unless they differ.
     */
    private val watch = ViewTreeObserver.OnPreDrawListener {
        val m = master
        val view = mirror
        if (m != null && view != null) {
            if (view.isChecked != m.read()) {
                bindMirror(view, m)
                render(animate = true)
            }
            val usable = m.enabled()
            if (view.isEnabled != usable) view.isEnabled = usable
        }
        true
    }

    /** The live line re-read every so often while the card is on screen. */
    private val ticker = object : Runnable {
        override fun run() {
            if (isShown) render(animate = true)
            handler?.postDelayed(this, TICK_MS)
        }
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        if (mirror != null) viewTreeObserver.addOnPreDrawListener(watch)
        render(animate = false)
        postDelayed(ticker, TICK_MS)
    }

    override fun onDetachedFromWindow() {
        if (mirror != null) viewTreeObserver.removeOnPreDrawListener(watch)
        removeCallbacks(ticker)
        super.onDetachedFromWindow()
    }

    private fun bindMirror(view: MaterialSwitch, master: IconifyGrid.Master) {
        // Assigning isChecked fires the listener exactly as a finger does, so it comes off first.
        view.setOnCheckedChangeListener(null)
        view.isChecked = master.read()
        view.setOnCheckedChangeListener { _, wanted ->
            master.write(wanted)
            // What the real switch settled on, which a feature that refused may have put back.
            if (master.read() != wanted) bindMirror(view, master)
            render(animate = true)
        }
    }

    private companion object {
        const val PRESSED_SCALE = 0.96f
        const val TICK_MS = 1500L
    }
}

/** Small building blocks for the cards' extras. */
object CardExtras {
    /** A tonal chip: an optional dot or glyph, then a few words. */
    fun chip(context: Context, text: String, dot: Int? = null, glyph: String? = null,
        background: Int? = null): LinearLayout {
        val m3 = M3(context)
        return LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(m3.dp(12), 0, m3.dp(12), 0)
            minimumHeight = m3.dp(32)
            this.background = GradientDrawable().apply {
                cornerRadius = m3.dpF(16f)
                setColor(background ?: m3.surfaceContainerHighest)
            }
            if (dot != null) addView(View(context).apply {
                this.background = GradientDrawable().apply { shape = GradientDrawable.OVAL; setColor(dot) }
                tag = TAG_DOT
            }, LinearLayout.LayoutParams(m3.dp(12), m3.dp(12)).apply { marginEnd = m3.dp(7) })
            if (glyph != null) addView(TextView(context).apply {
                this.text = glyph
                m3.styleGlyph(this, 12f, m3.primary)
            }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT).apply { marginEnd = m3.dp(7) })
            addView(TextView(context).apply {
                this.text = text
                tag = TAG_TEXT
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 12.5f)
                typeface = android.graphics.Typeface.create(android.graphics.Typeface.SANS_SERIF, 700, false)
                setTextColor(m3.onSurface)
                maxLines = 1
                ellipsize = TextUtils.TruncateAt.END
            })
        }
    }

    fun setChipText(chip: View, text: String) {
        (chip.findViewWithTag<TextView>(TAG_TEXT))?.let { if (it.text.toString() != text) it.text = text }
    }

    /** A row of [count] rising bars, the first [lit] of them in the accent: a strength meter. */
    class Meter(context: Context, private val count: Int = 10) : View(context) {
        private val m3 = M3(context)
        private val dp = context.resources.displayMetrics.density
        private val on = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = m3.primary }
        private val off = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = m3.surfaceContainerHighest }
        private val rect = RectF()
        var lit = 0
            set(value) { if (field != value) { field = value; invalidate() } }

        override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) =
            setMeasuredDimension((count * 8 * dp).toInt(), (18 * dp).toInt())

        override fun onDraw(canvas: Canvas) {
            for (i in 0 until count) {
                val h = (6 + min(i, 5) * 2.4f) * dp
                rect.set(i * 8 * dp, height - h, i * 8 * dp + 5 * dp, height.toFloat())
                canvas.drawRoundRect(rect, 2.5f * dp, 2.5f * dp, if (i < lit) on else off)
            }
        }
    }

    /** A plain secondary line of text, for under a bar or meter. */
    fun caption(context: Context, text: String = ""): TextView = TextView(context).apply {
        this.text = text
        setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
        typeface = android.graphics.Typeface.create(android.graphics.Typeface.SANS_SERIF, 600, false)
        setTextColor(M3(context).onSurfaceVariant)
    }

    /** A small tonal tile with a bold line and a secondary line under it. */
    fun tile(context: Context, title: String, line: String): LinearLayout {
        val m3 = M3(context)
        return LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(m3.dp(12), m3.dp(10), m3.dp(12), m3.dp(10))
            background = GradientDrawable().apply {
                cornerRadius = m3.dpF(16f); setColor(m3.surfaceContainerHighest)
            }
            addView(TextView(context).apply {
                text = title
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
                typeface = android.graphics.Typeface.create(android.graphics.Typeface.SANS_SERIF, 700, false)
                setTextColor(m3.onSurface)
                maxLines = 1
                ellipsize = TextUtils.TruncateAt.END
            })
            addView(caption(context, line).apply { tag = TAG_TEXT; maxLines = 1; ellipsize = TextUtils.TruncateAt.END })
        }
    }

    fun setTileLine(tile: View, text: String) = setChipText(tile, text)

    private const val TAG_TEXT = "card_extra_text"
    private const val TAG_DOT = "card_extra_dot"
}
