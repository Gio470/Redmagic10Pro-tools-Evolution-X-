package com.elitedarkkaiser.redmagic.ui

import android.app.Activity
import android.app.Application
import android.view.View
import android.view.ViewGroup
import android.view.ContextThemeWrapper
import android.widget.FrameLayout
import android.widget.LinearLayout
import androidx.coordinatorlayout.widget.CoordinatorLayout
import androidx.core.view.ViewCompat
import com.google.android.material.appbar.AppBarLayout
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [31], application = Application::class)
class PageLayoutTest {
    @Test fun densityPreviewReflowsWithoutMovingTheSliderBelowIt() {
        val context = ContextThemeWrapper(RuntimeEnvironment.getApplication(),
            com.google.android.material.R.style.Theme_Material3_DayNight_NoActionBar)
        val preview = DisplayDensityPreview(context)
        val dpi = context.resources.displayMetrics.densityDpi
        val widthSpec = View.MeasureSpec.makeMeasureSpec(800, View.MeasureSpec.EXACTLY)
        val heightSpec = View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED)
        preview.showDensity(dpi)
        preview.measure(widthSpec, heightSpec)
        val originalHeight = preview.measuredHeight
        val originalContentWidth = preview.getChildAt(0).measuredWidth

        preview.showDensity(dpi * 5 / 4)
        preview.measure(widthSpec, heightSpec)
        assertEquals(800, preview.measuredWidth)
        assertEquals(originalHeight, preview.measuredHeight)
        assertTrue(preview.getChildAt(0).measuredWidth < originalContentWidth)

        preview.showDensity(dpi * 3 / 4)
        preview.measure(widthSpec, heightSpec)
        assertEquals(originalHeight, preview.measuredHeight)
        assertTrue(preview.getChildAt(0).measuredWidth > originalContentWidth)
    }

    @Test fun shortPageDoesNotScrollOrCollapseItsTitle() {
        for (wrapped in listOf(false, true)) {
            val activity = Robolectric.buildActivity(Activity::class.java).setup().get()
            activity.setTheme(com.google.android.material.R.style.Theme_Material3_DayNight_NoActionBar)
            val host = FrameLayout(activity)
            activity.setContentView(host)
            PageHost.attach(activity, host)
            val body = LinearLayout(activity).apply {
                orientation = LinearLayout.VERTICAL
                addView(M3Dialog.title(activity, "Short page"))
                addView(View(activity), LinearLayout.LayoutParams(100, 200))
            }
            val scroller = M3Dialog.scroll(activity, body)
            val content = if (wrapped) M3Dialog.panel(activity).apply {
                addView(FrameLayout(activity).apply { addView(scroller) },
                    LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))
            } else scroller
            PageHost.push(activity, content, cancelable = true)
            host.measure(View.MeasureSpec.makeMeasureSpec(1080, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(1920, View.MeasureSpec.EXACTLY))
            host.layout(0, 0, 1080, 1920)
            val coordinator = (host.getChildAt(0) as FrameLayout).getChildAt(0) as CoordinatorLayout
            val appBar = coordinator.getChildAt(0) as AppBarLayout
            val titleBottom = appBar.bottom
            assertTrue(titleBottom > 0)
            ViewCompat.startNestedScroll(scroller, ViewCompat.SCROLL_AXIS_VERTICAL)
            val consumed = IntArray(2)
            ViewCompat.dispatchNestedPreScroll(scroller, 0, appBar.height, consumed, null)
            assertEquals("wrapped=$wrapped", 0, consumed[1])
            assertEquals(titleBottom, appBar.bottom)
            assertFalse(scroller.canScrollVertically(1))
        }
    }

    @Test fun pageTitleScrollsAwayAndReturnsForBareAndWrappedScrollers() {
        for (wrapped in listOf(false, true)) {
            val activity = Robolectric.buildActivity(Activity::class.java).setup().get()
            activity.setTheme(com.google.android.material.R.style.Theme_Material3_DayNight_NoActionBar)
            val host = FrameLayout(activity)
            activity.setContentView(host)
            PageHost.attach(activity, host)
            val body = LinearLayout(activity).apply {
                orientation = LinearLayout.VERTICAL
                addView(M3Dialog.title(activity, "Display density"))
                addView(View(activity), LinearLayout.LayoutParams(100, 3000))
            }
            val scroller = M3Dialog.scroll(activity, body)
            val content = if (wrapped) M3Dialog.panel(activity).apply {
                addView(FrameLayout(activity).apply { addView(scroller) },
                    LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))
            } else scroller
            PageHost.push(activity, content, cancelable = true)
            host.measure(View.MeasureSpec.makeMeasureSpec(1080, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(1920, View.MeasureSpec.EXACTLY))
            host.layout(0, 0, 1080, 1920)
            val page = host.getChildAt(0) as FrameLayout
            val coordinator = page.getChildAt(0) as CoordinatorLayout
            val appBar = coordinator.getChildAt(0) as AppBarLayout
            val pageContent = coordinator.getChildAt(1)
            val titleHeight = appBar.height
            assertTrue(titleHeight > 0)
            val originalContentTop = pageContent.top
            assertTrue(ViewCompat.startNestedScroll(scroller, ViewCompat.SCROLL_AXIS_VERTICAL))
            val consumed = IntArray(2)
            ViewCompat.dispatchNestedPreScroll(scroller, 0, titleHeight, consumed, null)
            assertEquals(titleHeight, consumed[1])
            assertEquals(0, appBar.bottom)
            assertTrue(pageContent.top < originalContentTop)

            ViewCompat.dispatchNestedScroll(scroller, 0, 0, 0, -titleHeight, null)
            assertEquals(titleHeight, appBar.bottom)
            assertEquals(originalContentTop, pageContent.top)
            ViewCompat.stopNestedScroll(scroller)
            activity.finish()
        }
    }
}
