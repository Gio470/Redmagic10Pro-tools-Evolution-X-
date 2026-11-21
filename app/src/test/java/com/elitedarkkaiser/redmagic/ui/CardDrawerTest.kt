package com.elitedarkkaiser.redmagic.ui

import android.app.Activity
import android.app.Application
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.GradientDrawable
import android.os.Looper
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.FrameLayout
import androidx.core.widget.NestedScrollView
import com.google.android.material.bottomsheet.BottomSheetDialog
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowDialog

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [31], application = Application::class)
class CardDrawerTest {
    private fun activity(): Activity = Robolectric.buildActivity(Activity::class.java).setup().get().apply {
        setTheme(com.google.android.material.R.style.Theme_Material3_DayNight_NoActionBar)
    }

    @Test fun drawerUsesItsOwnSolidSurfaceAndHasNoSecondColoredSheet() {
        val activity = activity()
        val host = FrameLayout(activity)
        val originalCard = View(activity)
        host.addView(originalCard)
        activity.setContentView(host)
        val body = M3Dialog.panel(activity).apply { addView(M3(activity).row("Root access", supporting = "Granted")) }
        val panel = M3Dialog.drawer(activity, body)
        val dialog = ShadowDialog.getLatestDialog() as BottomSheetDialog
        val decor = dialog.window!!.decorView
        decor.measure(View.MeasureSpec.makeMeasureSpec(1080, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(1920, View.MeasureSpec.EXACTLY))
        decor.layout(0, 0, 1080, 1920)
        val sheet = dialog.findViewById<FrameLayout>(com.google.android.material.R.id.design_bottom_sheet)!!
        assertTrue(CardBackdrop.sharesWindow(host, originalCard))
        assertFalse(CardBackdrop.sharesWindow(host, body))
        assertEquals(Color.TRANSPARENT, (sheet.background as ColorDrawable).color)
        val card = body.parent.parent as CardDrawer.DrawerColumn
        val fill = (card.background as GradientDrawable).color!!.defaultColor
        assertEquals(255, Color.alpha(fill))
        assertEquals(M3(activity).rowSurface, fill)
        panel.dismiss()
        activity.finish()
    }

    @Test fun longDetailsScrollWhileActionsStayVisibleAndShortPromptsWrap() {
        val activity = activity()
        fun measure(bodyHeight: Int, available: Int): Pair<CardDrawer.DrawerColumn, NestedScrollView> {
            val body = M3Dialog.panel(activity).apply {
                addView(View(activity), LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, bodyHeight))
                addView(M3Dialog.buttonRow(activity, M3Dialog.filledButton(activity, "Save") {}))
            }
            val card = CardDrawer.buildContent(activity, body, true) {}
            card.measure(View.MeasureSpec.makeMeasureSpec(800, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(1200, View.MeasureSpec.AT_MOST))
            val shortHeight = card.measuredHeight
            card.measure(View.MeasureSpec.makeMeasureSpec(800, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(available, View.MeasureSpec.AT_MOST))
            card.layout(0, 0, card.measuredWidth, card.measuredHeight)
            val actions = card.getChildAt(2)
            assertTrue(actions.height > 0)
            assertTrue(actions.bottom <= card.height)
            if (bodyHeight < 100) assertEquals(shortHeight, card.height)
            return card to (card.getChildAt(1) as NestedScrollView)
        }
        val short = measure(50, 1200).first
        val (tall, scroll) = measure(3000, 1200)
        assertTrue(short.height < tall.height)
        assertTrue(tall.height <= 1020)
        assertTrue(scroll.canScrollVertically(1))
        // A smaller viewport, including one reduced by the keyboard, still exposes the footer.
        assertTrue(measure(3000, 600).first.height <= 510)
        activity.finish()
    }

    @Test fun swipeCancelAndExplicitDismissNotifyOnceAndDensityCannotBeDraggedAway() {
        val activity = activity()
        var cancelled = 0
        var dismissed = 0
        val panel = M3Dialog.drawer(activity, M3Dialog.panel(activity).apply {
            addView(M3Dialog.title(activity, "System status"))
        })
        panel.setOnCancelListener { cancelled++ }
        panel.setOnDismissListener { dismissed++ }
        (ShadowDialog.getLatestDialog() as BottomSheetDialog).cancel()
        shadowOf(Looper.getMainLooper()).idle()
        panel.dismiss()
        assertFalse(panel.isShowing)
        assertEquals(1, cancelled)
        assertEquals(1, dismissed)

        val confirmation = M3Dialog.drawer(activity, M3Dialog.panel(activity).apply {
            addView(M3Dialog.title(activity, "Keep this density?"))
            addView(M3Dialog.buttonRow(activity, M3Dialog.filledButton(activity, "Keep") {}))
        }, cancelable = false)
        val dialog = ShadowDialog.getLatestDialog() as BottomSheetDialog
        assertFalse(dialog.behavior.isDraggable)
        assertFalse(dialog.behavior.isHideable)
        confirmation.setOnCancelListener { cancelled++ }
        confirmation.setOnDismissListener { dismissed++ }
        confirmation.dismiss()
        shadowOf(Looper.getMainLooper()).idle()
        assertFalse(dialog.isShowing)
        assertEquals(1, cancelled)
        assertEquals(2, dismissed)
        activity.finish()
    }
}

