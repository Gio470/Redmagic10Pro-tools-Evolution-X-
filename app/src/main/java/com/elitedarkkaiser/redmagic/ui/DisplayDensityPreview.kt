package com.elitedarkkaiser.redmagic.ui

import android.content.Context
import android.graphics.Canvas
import android.view.View
import android.view.ViewGroup
import kotlin.math.roundToInt

/** Reflows the sample at the selected density without changing the phone's configuration. */
internal class DisplayDensityPreview(context: Context) : ViewGroup(context) {
    private val sourceDpi = context.resources.displayMetrics.densityDpi.coerceAtLeast(1)
    private val viewportHeight = M3(context).dp(320)
    private val sample = DensitySampleView(context, fillViewport = true).apply {
        renderCurrentTheme()
        importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS
    }
    private var densityScale = 1f

    init {
        addView(sample)
        importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_YES
        showDensity(sourceDpi)
    }

    fun showDensity(dpi: Int) {
        if (dpi <= 0) return
        densityScale = dpi.toFloat() / sourceDpi
        contentDescription = "App preview at $dpi dpi. Sample cards, text, buttons and navigation."
        requestLayout()
        invalidate()
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val width = resolveSize(suggestedMinimumWidth, widthMeasureSpec)
        val height = resolveSize(viewportHeight, heightMeasureSpec)
        // Keep the viewport's height fixed so the density slider cannot move under the finger.
        // Its virtual width changes, allowing text and cards to wrap as they would on the phone.
        sample.measure(
            MeasureSpec.makeMeasureSpec((width / densityScale).roundToInt(), MeasureSpec.EXACTLY),
            MeasureSpec.makeMeasureSpec((height / densityScale).roundToInt(), MeasureSpec.EXACTLY)
        )
        setMeasuredDimension(width, height)
    }

    override fun onLayout(changed: Boolean, left: Int, top: Int, right: Int, bottom: Int) {
        sample.layout(0, 0, sample.measuredWidth, sample.measuredHeight)
    }

    override fun dispatchDraw(canvas: Canvas) {
        val checkpoint = canvas.save()
        canvas.clipRect(0, 0, width, height)
        canvas.scale(densityScale, densityScale)
        super.dispatchDraw(canvas)
        canvas.restoreToCount(checkpoint)
    }
}

