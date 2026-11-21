package com.elitedarkkaiser.redmagic.ui

import android.app.Activity
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.LinearLayout
import androidx.core.graphics.ColorUtils
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.doOnLayout
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import com.elitedarkkaiser.redmagic.R
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog

/** Compact tasks stay over the current page; longer settings still use PageHost. */
internal object CardDrawer {
    fun show(activity: Activity, content: View, cancelable: Boolean): Panel {
        val m3 = M3(activity)
        val dialog = BottomSheetDialog(activity, R.style.ThemeOverlay_RedMagic_CardDrawer)
        val panel = Panel(cancelable) { dialog.dismiss() }
        if (activity.isFinishing || activity.isDestroyed) {
            panel.dismiss()
            return panel
        }
        dialog.setOwnerActivity(activity)
        dialog.setCancelable(cancelable)
        dialog.setCanceledOnTouchOutside(cancelable)
        dialog.setOnCancelListener { panel.cancel() }
        val owner = activity as? LifecycleOwner
        val lifecycle = object : DefaultLifecycleObserver {
            override fun onDestroy(owner: LifecycleOwner) { panel.dismiss() }
        }
        owner?.lifecycle?.addObserver(lifecycle)
        dialog.setOnDismissListener {
            owner?.lifecycle?.removeObserver(lifecycle)
            panel.dismiss()
        }

        val card = buildContent(activity, content, cancelable) { panel.dismiss() }
        // Insets belong outside the rounded surface, including the keyboard for name prompts.
        val gutter = m3.dp(12)
        val outer = FrameLayout(activity).apply {
            setPadding(gutter, 0, gutter, gutter)
            addView(card, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL))
        }
        ViewCompat.setOnApplyWindowInsetsListener(outer) { view, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout())
            val keyboard = insets.getInsets(WindowInsetsCompat.Type.ime())
            view.setPadding(gutter + bars.left, 0, gutter + bars.right,
                gutter + maxOf(bars.bottom, keyboard.bottom))
            insets
        }
        dialog.setContentView(outer)
        dialog.behavior.apply {
            isDraggable = cancelable
            isHideable = cancelable
            skipCollapsed = true
            state = BottomSheetBehavior.STATE_EXPANDED
        }
        dialog.window?.let { window ->
            window.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
            WindowCompat.setDecorFitsSystemWindows(window, false)
            window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE)
            window.navigationBarColor = Color.TRANSPARENT
            window.statusBarColor = Color.TRANSPARENT
            val controller = WindowCompat.getInsetsController(window, window.decorView)
            controller.isAppearanceLightNavigationBars = ColorUtils.calculateLuminance(m3.surface) > 0.5
            controller.systemBarsBehavior = androidx.core.view.WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            if (!ViewCompat.getRootWindowInsets(activity.window.decorView)
                    .let { it?.isVisible(WindowInsetsCompat.Type.statusBars()) ?: false }) {
                controller.hide(WindowInsetsCompat.Type.statusBars())
            }
        }
        dialog.show()
        dialog.findViewById<FrameLayout>(com.google.android.material.R.id.design_bottom_sheet)?.apply {
            // Behavior installs its themed shape during the first layout. Clear it afterwards
            // too, or a second differently colored sheet peeks through the side gutters.
            fun clearSheetSurface() {
                backgroundTintList = null
                background = ColorDrawable(Color.TRANSPARENT)
                elevation = 0f
            }
            clearSheetSurface()
            doOnLayout { clearSheetSurface() }
        }
        ViewCompat.requestApplyInsets(outer)
        return panel
    }

    /** Keep the handle and actions fixed, with only the middle of a tall drawer scrolling. */
    internal fun buildContent(activity: Activity, body: View, cancelable: Boolean, close: () -> Unit): DrawerColumn {
        val m3 = M3(activity)
        val actions = (body as? LinearLayout)?.let { column ->
            column.getChildAt(column.childCount - 1)?.takeIf { it.tag === M3Dialog.ActionRow }
                ?.also { column.removeView(it) }
        } ?: if (cancelable) M3Dialog.buttonRow(activity,
            M3Dialog.textButton(activity, "Close", close)) else null
        return DrawerColumn(activity).apply {
            orientation = LinearLayout.VERTICAL
            background = m3.surfaceShape(m3.rowSurface, M3.Shape.extraLarge)
            clipToOutline = true
            addView(FrameLayout(activity).apply {
                importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS
                addView(View(activity).apply {
                    background = m3.surfaceShape(m3.outlineVariant, M3.Shape.full)
                }, FrameLayout.LayoutParams(m3.dp(32), m3.dp(4), Gravity.CENTER))
            }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, m3.dp(28)))
            addView(M3Dialog.scroll(activity, body).apply { isFillViewport = false },
                LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
            actions?.let { row ->
                row.setPadding(m3.dp(16), m3.dp(8), m3.dp(16), m3.dp(16))
                addView(row, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT))
            }
        }
    }

    /** Wrap short forms, while reserving room above tall drawers and above the keyboard. */
    internal class DrawerColumn(activity: Activity) : LinearLayout(activity) {
        override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
            val available = if (MeasureSpec.getMode(heightMeasureSpec) == MeasureSpec.UNSPECIFIED)
                resources.displayMetrics.heightPixels else MeasureSpec.getSize(heightMeasureSpec)
            super.onMeasure(widthMeasureSpec,
                MeasureSpec.makeMeasureSpec((available * 0.85f).toInt(), MeasureSpec.AT_MOST))
        }
    }
}

