package com.elitedarkkaiser.redmagic.ui

import android.content.Context
import android.content.res.ColorStateList
import android.view.Gravity
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.graphics.ColorUtils
import com.elitedarkkaiser.redmagic.gametrigger.NativeTgkStorage
import com.google.android.material.slider.LabelFormatter
import com.google.android.material.slider.Slider
import kotlin.math.roundToInt

/** Shared by the game profile and touch-only overlay; a drag commits one count when released. */
internal class RapidFireCountControl(context: Context, initial: Int, private val changed: (Int) -> Unit) {
    private val m3 = M3(context)
    private var committed = initial.coerceIn(NativeTgkStorage.rapidFireRange)
    private var dragging = false
    val view = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL }
    private val count = TextView(context).apply {
        gravity = Gravity.CENTER
        m3.styleText(this, M3.Type.titleSmall, m3.onSurface)
        accessibilityLiveRegion = android.view.View.ACCESSIBILITY_LIVE_REGION_POLITE
    }
    private val minus = m3.button("−", M3.ButtonKind.Tonal) { adjust(-1) }.apply {
        contentDescription = "Decrease rapid-fire tap count"
        minimumWidth = m3.dp(48)
        setPadding(m3.dp(12), 0, m3.dp(12), 0)
    }
    private val plus = m3.button("+", M3.ButtonKind.Tonal) { adjust(1) }.apply {
        contentDescription = "Increase rapid-fire tap count"
        minimumWidth = m3.dp(48)
        setPadding(m3.dp(12), 0, m3.dp(12), 0)
    }
    private val slider = Slider(context).apply {
        valueFrom = NativeTgkStorage.MIN_RAPID_FIRE_COUNT.toFloat()
        valueTo = NativeTgkStorage.MAX_RAPID_FIRE_COUNT.toFloat()
        stepSize = 1f
        value = committed.toFloat()
        labelBehavior = LabelFormatter.LABEL_GONE
        isTickVisible = false
        thumbTintList = ColorStateList.valueOf(m3.primary)
        trackActiveTintList = ColorStateList.valueOf(m3.primary)
        trackInactiveTintList = ColorStateList.valueOf(ColorUtils.setAlphaComponent(m3.primary, 48))
        haloTintList = ColorStateList.valueOf(ColorUtils.setAlphaComponent(m3.primary, 48))
        contentDescription = "Rapid-fire tap count, 1 to 100"
        addOnChangeListener { _, value, fromUser ->
            show(value.roundToInt())
            // Touch-down can change the value before tracking starts. Defer this fallback so
            // accessibility/keyboard edits commit without rebuilding the editor under a drag.
            if (fromUser && !dragging) view.post {
                if (!dragging) commit(this.value.roundToInt())
            }
        }
        addOnSliderTouchListener(object : Slider.OnSliderTouchListener {
            override fun onStartTrackingTouch(slider: Slider) { dragging = true }
            override fun onStopTrackingTouch(slider: Slider) {
                dragging = false
                commit(slider.value.roundToInt())
            }
        })
    }

    init {
        view.addView(LinearLayout(context).apply {
            gravity = Gravity.CENTER_VERTICAL
            addView(minus, LinearLayout.LayoutParams(m3.dp(48), m3.dp(48)))
            addView(count, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
            addView(plus, LinearLayout.LayoutParams(m3.dp(48), m3.dp(48)))
        })
        view.addView(slider, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, m3.dp(48)))
        view.addView(TextView(context).apply {
            text = "1–100 taps per press"
            m3.styleText(this, M3.Type.bodySmall, m3.onSurfaceVariant)
        })
        show(committed)
    }

    private fun show(value: Int) {
        count.text = "$value ${if (value == 1) "tap" else "taps"}"
        minus.isEnabled = value > NativeTgkStorage.MIN_RAPID_FIRE_COUNT
        plus.isEnabled = value < NativeTgkStorage.MAX_RAPID_FIRE_COUNT
    }
    private fun adjust(delta: Int) {
        val value = (slider.value.roundToInt() + delta).coerceIn(NativeTgkStorage.rapidFireRange)
        slider.value = value.toFloat()
        commit(value)
    }
    private fun commit(value: Int) {
        if (value == committed) return
        committed = value
        changed(value)
    }
}
