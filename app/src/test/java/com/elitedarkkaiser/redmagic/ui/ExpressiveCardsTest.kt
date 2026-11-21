package com.elitedarkkaiser.redmagic.ui

import android.app.Application
import android.os.Looper
import android.view.View
import android.view.ViewGroup
import android.widget.CompoundButton
import android.widget.FrameLayout
import android.widget.LinearLayout
import androidx.activity.ComponentActivity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.time.Duration

/**
 * Builds the Hardware tab's cards exactly as the tab decorates them, draws them, flips every
 * switch and presses every card; any exception fails the test.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [31], application = Application::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ExpressiveCardsTest {
    private fun activity(): ComponentActivity =
        Robolectric.buildActivity(ComponentActivity::class.java).setup().get().apply {
            setTheme(com.elitedarkkaiser.redmagic.R.style.Theme_RedmagicTools)
        }

    private fun draw(view: View) {
        view.measure(View.MeasureSpec.makeMeasureSpec(1080, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED))
        view.layout(0, 0, view.measuredWidth, view.measuredHeight)
        val bitmap = android.graphics.Bitmap.createBitmap(1080, maxOf(1, view.measuredHeight),
            android.graphics.Bitmap.Config.ARGB_8888)
        view.draw(android.graphics.Canvas(bitmap))
    }

    private fun <T : View> all(root: View, type: Class<T>, out: MutableList<T> = mutableListOf()): List<T> {
        if (type.isInstance(root)) out += type.cast(root)!!
        if (root is ViewGroup) for (i in 0 until root.childCount) all(root.getChildAt(i), type, out)
        return out
    }

    private class Toggle(var on: Boolean) {
        val master = IconifyGrid.Master(read = { on }, write = { on = it })
    }

    @Test fun hardwareCardsBuildDrawToggleAndPress() {
        val activity = activity()
        val host = FrameLayout(activity)
        activity.setContentView(host)
        LiveHardware.update(4200, "41°C", 3, true)

        val titles = listOf("Fan", "Magic Key", "Triggers", "Haptics", "Display density", "Game Mode",
            "Call lighting", "Charging", "Fan LED", "Logo LED", "Performance mods", "Performance mode", "Ambient display", "Charge separation", "Game trigger mapping", "Something new")
        val toggles = titles.associateWith { Toggle(it.length % 2 == 0) }
        var opened = 0
        val items = titles.map { title ->
            val item = IconifyGrid.Item(title, "Supporting line", Icons.FAN,
                toggles.getValue(title).master) { opened++ }
            if (title in setOf("Ambient display", "Display density", "Performance mods")) {
                SoftwareTabUi.decorate(activity, item)
            } else HardwareCards.decorate(activity, item)
        }
        val grid = IconifyGrid.build(activity, items, chips = HardwareCards.chips())
        host.addView(grid, FrameLayout.LayoutParams(1080, ViewGroup.LayoutParams.WRAP_CONTENT))
        draw(grid)

        val cards = all(grid, ExpressiveCard::class.java)
        assertEquals("every feature has a card", titles.size, cards.size)

        // Every switch both ways, with the springs and the live ticker running in between.
        all(grid, CompoundButton::class.java).forEach { it.toggle(); it.toggle(); it.toggle() }
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(2000))
        draw(grid)
        assertTrue(toggles.values.any { it.on })

        // Every card pressed: the on ones open, the off ones don't.
        val tappable = cards.map { it.getChildAt(0) }.filter { it.isClickable }
        tappable.forEach { it.performClick() }
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(500))
        assertEquals("only cards whose feature is on open", toggles.values.count { it.on }, tappable.size)
        assertEquals(tappable.size, opened)

        // Laid out again from a shorter list, as the Software tab does when a card drops out.
        IconifyGrid.fill(grid as LinearLayout, items.drop(3))
        draw(grid)
        assertEquals(titles.size - 3, all(grid, ExpressiveCard::class.java).size)
    }

    @Test fun shapesMorphPastTheirEnds() {
        val path = android.graphics.Path()
        CardShape.entries.forEach { shape ->
            CardShape.outline(path, 50f, 50f, 40f, CardShape.CIRCLE, shape, 1.15f, 0.3f)
            CardShape.outline(path, 50f, 50f, 40f, shape, CardShape.CIRCLE, -0.1f, 0f)
        }
    }
}
