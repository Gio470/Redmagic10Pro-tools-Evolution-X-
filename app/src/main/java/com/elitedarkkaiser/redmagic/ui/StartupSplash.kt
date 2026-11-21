package com.elitedarkkaiser.redmagic.ui

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ValueAnimator
import android.annotation.SuppressLint
import android.app.Activity
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.drawable.Drawable
import android.os.Build
import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.animation.LinearInterpolator
import android.view.animation.PathInterpolator
import androidx.core.graphics.ColorUtils
import com.elitedarkkaiser.redmagic.BuildConfig
import com.elitedarkkaiser.redmagic.R
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.floor
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

/**
 * The launch animation, in Material 3 Expressive style. KernelSU-Next's six-turn spin is the heart
 * of it; around that:
 */
object StartupSplash {
    private const val PREFS = "appearance"
    private const val KEY = "startup_animation"

    fun enabled(context: Context): Boolean =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getBoolean(KEY, true)

    fun setEnabled(context: Context, value: Boolean) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putBoolean(KEY, value).apply()
    }

    fun show(activity: Activity) {
        if (!enabled(activity) || !ValueAnimator.areAnimatorsEnabled()) return
        val host = activity.findViewById<ViewGroup>(android.R.id.content) ?: return
        val app = host.getChildAt(0)
        val splash = SplashView(activity, app)
        host.addView(splash, ViewGroup.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
        splash.start()
    }

    // ---- Timeline (ms) -----------------------------------------------------------------------
    private const val SPIN_END = 1400f
    private const val BLOOM_START = 1330f
    private const val BLOOM_END = 1650f
    private const val TITLE_START = 850f
    private const val TITLE_END = 1350f
    private const val EXIT_START = 1800f
    private const val EXIT_END = 2280f
    private const val TURNS = 6

    private val standard = PathInterpolator(0.4f, 0f, 0.2f, 1f)
    private val decelerate = PathInterpolator(0.05f, 0.7f, 0.1f, 1f)
    private val accelerate = PathInterpolator(0.3f, 0f, 0.8f, 0.15f)

    private fun seg(t: Float, start: Float, end: Float) = ((t - start) / (end - start)).coerceIn(0f, 1f)

    // ---- Expressive shapes ---------------------------------------------------------------------

    /**
     * A smooth polar outline with [lobes] bumps [depth] deep, normalised so its outermost point
     * sits on the radius. Close enough to M3's MaterialShapes at this size, and cheap to morph:
     * two shapes blend by blending their radii angle by angle.
     */
    private class Shape(val lobes: Int, val depth: Float) {
        fun radius(theta: Float) = (1f + depth * cos(lobes * theta)) / (1f + depth)
    }

    private val SOFT_BURST = Shape(10, 0.10f)
    private val COOKIE_9 = Shape(9, 0.07f)
    private val PENTAGON = Shape(5, 0.055f)
    private val PILL = Shape(2, 0.16f)
    private val SUNNY = Shape(8, 0.06f)
    private val COOKIE_4 = Shape(4, 0.11f)
    private val COOKIE_12 = Shape(12, 0.045f)

    /** The loading indicator's order, ending where it began so the last morph lands cleanly. */
    private val SEQUENCE = listOf(SOFT_BURST, COOKIE_9, PENTAGON, PILL, SUNNY, COOKIE_4, SOFT_BURST)

    private const val OUTLINE_STEPS = 144
    private const val TWO_PI = (2 * PI).toFloat()

    /** Writes the outline of [from] morphed [f] of the way to [to] into [path]. */
    private fun shape(path: Path, cx: Float, cy: Float, r: Float, from: Shape, to: Shape, f: Float,
        rotation: Float) {
        path.rewind()
        for (i in 0..OUTLINE_STEPS) {
            val theta = i.toFloat() / OUTLINE_STEPS * TWO_PI
            val a = from.radius(theta)
            val radius = r * (a + (to.radius(theta) - a) * f)
            val x = cx + radius * cos(theta + rotation)
            val y = cy + radius * sin(theta + rotation)
            if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }
        path.close()
    }

    @SuppressLint("ViewConstructor")
    private class SplashView(context: Context, private val app: View?) : View(context) {
        private val m3 = M3(context)
        private val dp = context.resources.displayMetrics.density
        private val sp = context.resources.displayMetrics.scaledDensity
        private val background = m3.surface
        private val primary = m3.primary
        private val tertiary = m3.tertiary
        private val primaryContainer = m3.primaryContainer

        private val mono: Drawable = context.getDrawable(R.mipmap.ic_launcher_monochrome)!!.mutate()
            .apply { setTint(primary) }
        private val color: Drawable = context.getDrawable(R.drawable.ic_launcher_fg)!!.mutate()

        /**
         * The expressive spatial spring, as a curve over 0..1: Q-bouncy overshoots ~16% and
         * settles, Soft overshoots ~4%, Off is a plain deceleration.
         */
        private val spring: (Float) -> Float = when (MotionPrefs.effective(context)) {
            MotionPrefs.Preset.Q_BOUNCY -> { p -> if (p >= 1f) 1f else 1f - exp(-7f * p) * cos(12f * p) }
            MotionPrefs.Preset.SOFT -> { p -> if (p >= 1f) 1f else 1f - exp(-7f * p) * cos(7f * p) }
            MotionPrefs.Preset.OFF -> { p -> decelerate.getInterpolation(p) }
        }

        // The artwork's mark fills about half its square.
        private val iconSize = 230 * dp
        private val containerRadius = 96 * dp
        private val ringRadius = 122 * dp

        private val fill = Paint(Paint.ANTI_ALIAS_FLAG)
        private val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE; strokeCap = Paint.Cap.ROUND; strokeJoin = Paint.Join.ROUND
        }
        private val title = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            typeface = android.graphics.Typeface.create(android.graphics.Typeface.SANS_SERIF, 800, false)
            textSize = 25 * sp
            color = m3.onSurface
        }
        private val pillText = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            typeface = android.graphics.Typeface.create(android.graphics.Typeface.SANS_SERIF, 700, false)
            textSize = 13 * sp
            color = m3.onSecondaryContainer
            textAlign = Paint.Align.CENTER
        }
        private val pillColor = m3.secondaryContainer
        private val accentA = m3.tertiaryContainer
        private val accentB = m3.secondaryContainer
        private val name = context.getString(R.string.app_name)
        private val version = "v${BuildConfig.VERSION_NAME}"
        private val letterWidths = FloatArray(name.length)
        private val nameWidth: Float
        private var titleScale = 1f

        private val path = Path()
        private val rect = RectF()
        private var hapticDone = false
        private var t = 0f

        private val clock = ValueAnimator.ofFloat(0f, EXIT_END).apply {
            duration = EXIT_END.toLong()
            interpolator = LinearInterpolator()
            addUpdateListener {
                t = it.animatedValue as Float
                tick()
                invalidate()
            }
            addListener(object : AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: Animator) = finish()
            })
        }

        init {
            // Takes every touch, so nothing underneath is pressed while it plays.
            isClickable = true
            importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO
            app?.let { it.scaleX = 0.92f; it.scaleY = 0.92f }
            title.getTextWidths(name, letterWidths)
            nameWidth = letterWidths.sum()
        }

        fun start() = clock.start()

        @SuppressLint("ClickableViewAccessibility")
        override fun onTouchEvent(event: MotionEvent): Boolean {
            // A tap skips to the reveal; the reveal itself is short enough to let finish.
            if (event.actionMasked == MotionEvent.ACTION_UP && clock.currentPlayTime < EXIT_START) {
                clock.currentPlayTime = EXIT_START.toLong()
            }
            return true
        }

        override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
            // Keeps the name on one line on narrow screens or with large font sizes.
            titleScale = if (nameWidth > 0f) min(1f, (w - 48 * dp) / nameWidth) else 1f
        }

        private fun tick() {
            if (!hapticDone && t >= SPIN_END) {
                hapticDone = true
                performHapticFeedback(
                    if (Build.VERSION.SDK_INT >= 30) HapticFeedbackConstants.CONFIRM
                    else HapticFeedbackConstants.CONTEXT_CLICK
                )
            }
            app?.let {
                val s = 0.92f + 0.08f * spring(seg(t, EXIT_START + 40f, EXIT_END + 200f))
                it.pivotX = it.width / 2f; it.pivotY = it.height / 2f
                it.scaleX = s; it.scaleY = s
            }
        }

        private fun finish() {
            app?.let { it.scaleX = 1f; it.scaleY = 1f }
            (parent as? ViewGroup)?.removeView(this)
        }

        override fun onDetachedFromWindow() {
            super.onDetachedFromWindow()
            clock.removeAllListeners()
            clock.cancel()
            app?.let { it.scaleX = 1f; it.scaleY = 1f }
        }

        private fun withAlpha(color: Int, a: Float) =
            ColorUtils.setAlphaComponent(color, (Color.alpha(color) * a.coerceIn(0f, 1f)).toInt())

        override fun onDraw(canvas: Canvas) {
            val cx = width / 2f
            val cy = height / 2f - 40 * dp
            val exitP = seg(t, EXIT_START, EXIT_END)
            val exit = accelerate.getInterpolation(exitP)
            val fadeOut = 1f - seg(t, EXIT_START, EXIT_START + 240f)
            val spin = standard.getInterpolation(seg(t, 0f, SPIN_END))

            // 4. The iris: a soft burst, turning, whose lobes round off into a circle as it opens.
            canvas.save()
            if (exit > 0f) {
                val reach = exit * hypot(max(cx, width - cx), max(cy, height - cy)) * 1.25f
                path.rewind()
                for (i in 0..OUTLINE_STEPS) {
                    val theta = i.toFloat() / OUTLINE_STEPS * TWO_PI
                    val r = reach * (1f + (SOFT_BURST.radius(theta) - 1f) * (1f - exitP))
                    val x = cx + r * cos(theta + exitP * 1.2f)
                    val y = cy + r * sin(theta + exitP * 1.2f)
                    if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
                }
                path.close()
                canvas.clipOutPath(path)
            }
            canvas.drawColor(background)

            // Accent shapes peeking in from opposite corners, springing in and turning slowly.
            val accent = spring(seg(t, 120f, 1000f)) * fadeOut
            if (accent > 0f) {
                fill.color = accentA
                shape(path, width + 10 * dp, height - 60 * dp, 150 * dp * accent, COOKIE_4, COOKIE_4, 0f,
                    -t / 2400f + 0.3f)
                canvas.drawPath(path, fill)
                fill.color = accentB
                shape(path, -20 * dp, 70 * dp, 120 * dp * accent, SUNNY, SUNNY, 0f, t / 2000f)
                canvas.drawPath(path, fill)
            }
            canvas.restore()

            // 1. Wavy progress ring: grows with the spin over a gapped track; the wave flattens to
            // a clean line as it completes.
            val ringIn = spring(seg(t, 60f, 700f))
            val ringOut = 1f - seg(t, SPIN_END + 60f, SPIN_END + 300f)
            if (ringIn > 0f && ringOut > 0f) {
                val sweep = (0.07f + 0.93f * spin) * TWO_PI
                val start = -PI.toFloat() / 2f + spin * PI.toFloat() * 1.5f
                val amp = 4.2f * dp * (1f - seg(t, SPIN_END - 250f, SPIN_END + 40f))
                val phase = -t / 90f
                val r = ringRadius * (0.8f + 0.2f * ringIn)
                stroke.strokeWidth = 5 * dp
                val gap = 0.12f
                if (sweep < TWO_PI - 2 * gap) {
                    stroke.color = withAlpha(primaryContainer, ringOut)
                    rect.set(cx - r, cy - r, cx + r, cy + r)
                    canvas.drawArc(rect, Math.toDegrees((start + sweep + gap).toDouble()).toFloat(),
                        Math.toDegrees((TWO_PI - sweep - 2 * gap).toDouble()).toFloat(), false, stroke)
                }
                stroke.color = withAlpha(primary, ringOut)
                path.rewind()
                val steps = 220
                for (i in 0..steps) {
                    val a = start + sweep * i / steps
                    val rr = r + amp * sin(a * 14f + phase)
                    val x = cx + rr * cos(a)
                    val y = cy + rr * sin(a)
                    if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
                }
                canvas.drawPath(path, stroke)
            }

            // 2. Two cookie outlines ripple out as it lands, primary then tertiary.
            for (i in 0..1) {
                val p = seg(t, SPIN_END + i * 100f, SPIN_END + i * 100f + 400f)
                if (p <= 0f || p >= 1f) continue
                stroke.strokeWidth = (6f * (1f - p) + 1f) * dp
                stroke.color = withAlpha(if (i == 0) primary else tertiary, 0.75f * (1f - p))
                shape(path, cx, cy, 104 * dp * (1f + 0.6f * decelerate.getInterpolation(p)),
                    COOKIE_12, COOKIE_12, 0f, p * 0.8f + i * 0.3f)
                canvas.drawPath(path, stroke)
            }

            // 1-2. The container: morphs through the loading-indicator shapes as it turns, then
            // springs into a 12-sided cookie as the spin lands.
            val grow = spring(seg(t, 0f, 900f))
            val pop = 0.1f * sin(PI.toFloat() * seg(t, SPIN_END - 40f, SPIN_END + 360f))
            val turns = 360f * TURNS * spin
            val settle = spring(seg(t, SPIN_END - 120f, SPIN_END + 450f))
            val from: Shape
            val to: Shape
            val f: Float
            if (settle > 0f) {
                from = SOFT_BURST; to = COOKIE_12; f = min(1.15f, settle)
            } else {
                val last = SEQUENCE.size - 1
                val pos = min(last - 1e-4f, spin * last)
                val index = floor(pos).toInt()
                from = SEQUENCE[index]; to = SEQUENCE[index + 1]
                f = spring(min(1f, (pos - index) * 1.6f))
            }
            val enter = seg(t, 0f, 160f) * fadeOut
            if (enter > 0f) {
                fill.color = withAlpha(primaryContainer, enter)
                shape(path, cx, cy, containerRadius * (0.3f + 0.7f * grow + pop) * (1f + 0.5f * exit),
                    from, to, f, Math.toRadians(turns * 0.35).toFloat())
                canvas.drawPath(path, fill)
            }

            // The mark spins its six turns, then blooms into full colour.
            val scale = (0.3f + 0.7f * grow + pop) * (1f + 0.5f * exit)
            val markAlpha = seg(t, 0f, 200f) * fadeOut
            val bloom = standard.getInterpolation(seg(t, BLOOM_START, BLOOM_END))
            val half = (iconSize / 2f).toInt()
            canvas.save()
            canvas.translate(cx, cy)
            canvas.rotate(turns)
            canvas.scale(scale, scale)
            mono.setBounds(-half, -half, half, half)
            color.setBounds(-half, -half, half, half)
            if (bloom < 1f) { mono.alpha = (255 * markAlpha * (1f - bloom)).toInt(); mono.draw(canvas) }
            if (bloom > 0f) { color.alpha = (255 * markAlpha * bloom).toInt(); color.draw(canvas) }
            canvas.restore()

            // 3. The name, letter by letter in heavy type; the version on a tonal pill.
            val textOut = 1f - seg(t, EXIT_START, EXIT_START + 200f)
            if (t > TITLE_START && textOut > 0f) {
                val baseY = cy + ringRadius + 72 * dp
                canvas.save()
                canvas.translate(cx, baseY)
                canvas.scale(titleScale, titleScale)
                var x = -nameWidth / 2f
                for (i in name.indices) {
                    val p = seg(t, TITLE_START + i * 18f, TITLE_START + i * 18f + 520f)
                    val w = letterWidths[i]
                    if (p > 0f) {
                        val s = spring(p)
                        title.alpha = (255 * min(1f, p * 3f) * textOut).toInt()
                        canvas.save()
                        canvas.translate(x + w / 2f, 26 * dp * (1f - s))
                        canvas.scale(0.6f + 0.4f * s, 0.6f + 0.4f * s)
                        canvas.drawText(name, i, i + 1, -w / 2f, 0f, title)
                        canvas.restore()
                    }
                    x += w
                }
                canvas.restore()

                val pp = spring(seg(t, TITLE_START + 380f, TITLE_END + 380f))
                if (pp > 0f) {
                    val pillHalf = pillText.measureText(version) / 2f + 16 * dp
                    canvas.save()
                    canvas.translate(cx, baseY + 42 * dp)
                    canvas.scale(pp, pp)
                    val a = min(1f, pp) * textOut
                    fill.color = withAlpha(pillColor, a)
                    rect.set(-pillHalf, -15 * dp, pillHalf, 15 * dp)
                    canvas.drawRoundRect(rect, 15 * dp, 15 * dp, fill)
                    pillText.alpha = (255 * a).toInt()
                    canvas.drawText(version, 0f, 5 * dp, pillText)
                    canvas.restore()
                }
            }
        }
    }
}
