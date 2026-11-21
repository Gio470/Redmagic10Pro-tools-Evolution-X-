package com.elitedarkkaiser.redmagic.ui

import android.animation.ValueAnimator
import android.content.Context
import androidx.dynamicanimation.animation.SpringAnimation

/** Motion intensity: how springy every press in the app is. */
object MotionPrefs {
    enum class Preset(val key: String, val label: String) {
        OFF("off", "Off"),
        SOFT("soft", "Soft"),
        Q_BOUNCY("q_bouncy", "Q-bouncy");

        companion object {
            fun fromKey(key: String?): Preset = entries.firstOrNull { it.key == key } ?: Q_BOUNCY
        }
    }

    /** What is moving. Cards and tiles scale; buttons morph their corners. */
    enum class Role { Scale, Button }

    private const val PREFS = "appearance"
    private const val KEY = "motion_preset"

    @Volatile
    private var cached: Preset? = null

    fun preset(context: Context): Preset =
        cached ?: Preset.fromKey(
            context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY, null)
        ).also { cached = it }

    fun setPreset(context: Context, preset: Preset) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString(KEY, preset.key).apply()
        cached = preset
    }

    /** The preset actually in force: Off whenever the system has animations switched off. */
    fun effective(context: Context): Preset =
        if (!ValueAnimator.areAnimatorsEnabled()) Preset.OFF else preset(context)

    /**
     * Damping ratio and stiffness for [role], [pressing] down or letting go; null for Off, which
     * means jump straight to the target.
     */
    internal fun spring(preset: Preset, role: Role, pressing: Boolean): Pair<Float, Float>? {
        if (preset == Preset.OFF) return null
        if (!pressing) return when (preset) {
            Preset.Q_BOUNCY -> 0.38f to 620f
            else -> 1f to 650f
        }
        return when (preset) {
            Preset.Q_BOUNCY -> when (role) {
                Role.Scale -> 0.32f to 700f
                Role.Button -> 0.36f to 560f
            }
            else -> when (role) {
                Role.Scale -> 0.72f to 650f
                Role.Button -> 0.78f to 430f
            }
        }
    }

    /**
     * Moves [animation] to [target] with the current preset's spring, or snaps it there when
     * motion is off. [snap] sets the value directly, since a cancelled spring stays where it was.
     */
    internal fun animate(
        context: Context,
        animation: SpringAnimation,
        role: Role,
        pressing: Boolean,
        target: Float,
        snap: (Float) -> Unit
    ) {
        val params = spring(effective(context), role, pressing)
        if (params == null) {
            animation.cancel()
            snap(target)
            return
        }
        val force = animation.spring ?: androidx.dynamicanimation.animation.SpringForce(target)
            .also { animation.spring = it }
        force.dampingRatio = params.first
        force.stiffness = params.second
        animation.animateToFinalPosition(target)
    }

    /**
     * How far a press sinks. Q-bouncy presses deeper than [base] so its overshoot and rebound
     * have room to show; Soft and Off use [base] as given.
     */
    internal fun pressedScale(context: Context, base: Float): Float =
        if (effective(context) == Preset.Q_BOUNCY) 1f - (1f - base) * 1.7f else base

    /**
     * The press every card and tile shares: [target] sinks while [touch] is held and springs
     * back on release. Scale only, on the view's own properties, so each frame is a transform
     * and nothing is redrawn.
     *
     * [claim] says whether [touch] should take the gesture itself. A view that is not clickable
     * has to, or it is never told the finger lifted; a clickable one keeps its ripple and click.
     */
    internal fun bindScalePress(
        touch: android.view.View,
        target: android.view.View = touch,
        baseScale: Float,
        claim: () -> Boolean = { false }
    ) {
        val x = SpringAnimation(target, SpringAnimation.SCALE_X)
        val y = SpringAnimation(target, SpringAnimation.SCALE_Y)
        touch.setOnTouchListener { v, event ->
            when (event.actionMasked) {
                android.view.MotionEvent.ACTION_DOWN ->
                    animateScale(target, x, y, pressing = true, target = pressedScale(v.context, baseScale))
                android.view.MotionEvent.ACTION_UP,
                android.view.MotionEvent.ACTION_CANCEL ->
                    animateScale(target, x, y, pressing = false, target = 1f)
            }
            claim()
        }
    }

    private val slides = java.util.WeakHashMap<android.view.View, SpringAnimation>()

    /**
     * Brings a page or tab in from [fromPx] to the side. The slide rides the preset's container
     * spring -- Q-bouncy runs past its resting place and settles back -- and the fade is a short
     * tween so the content is readable while it moves.
     */
    fun slideIn(view: android.view.View, fromPx: Float) {
        cancelSlide(view)
        view.animate().cancel()
        val preset = effective(view.context)
        if (preset == Preset.OFF) {
            view.translationX = 0f
            view.alpha = 1f
            return
        }
        view.translationX = fromPx
        view.alpha = 0f
        view.animate().alpha(1f).setDuration(180).start()
        val (damping, stiffness) = when (preset) {
            Preset.Q_BOUNCY -> 0.38f to 460f
            else -> 0.82f to 380f
        }
        slides[view] = SpringAnimation(view, SpringAnimation.TRANSLATION_X, 0f).apply {
            spring.dampingRatio = damping
            spring.stiffness = stiffness
            addEndListener { _, _, _, _ -> slides.remove(view) }
            start()
        }
    }

    /**
     * Sends a closing page off to [toPx] on the preset's release spring while it fades, then runs
     * [then]. The same springs as [slideIn], so opening and going back feel alike.
     */
    fun slideOut(view: android.view.View, toPx: Float, then: () -> Unit) {
        cancelSlide(view)
        view.animate().cancel()
        val preset = effective(view.context)
        if (preset == Preset.OFF) {
            then()
            return
        }
        view.animate().alpha(0f).setDuration(160).start()
        val (damping, stiffness) = when (preset) {
            Preset.Q_BOUNCY -> 0.38f to 620f
            else -> 1f to 650f
        }
        var done = false
        fun finish() { if (!done) { done = true; then() } }
        slides[view] = SpringAnimation(view, SpringAnimation.TRANSLATION_X, toPx).apply {
            spring.dampingRatio = damping
            spring.stiffness = stiffness
            addEndListener { _, _, _, _ ->
                slides.remove(view)
                finish()
            }
            start()
        }
        // The page is invisible well before a bouncy spring settles; don't hold it that long.
        view.postDelayed({ finish() }, 260)
    }

    /** Stops a [slideIn] still in flight, so an exit animation is not fighting it. */
    fun cancelSlide(view: android.view.View) {
        slides.remove(view)?.cancel()
    }

    /** Convenience for the scale springs on a view. */
    internal fun animateScale(view: android.view.View, x: SpringAnimation, y: SpringAnimation,
        pressing: Boolean, target: Float) {
        animate(view.context, x, Role.Scale, pressing, target) { view.scaleX = it }
        animate(view.context, y, Role.Scale, pressing, target) { view.scaleY = it }
    }
}
