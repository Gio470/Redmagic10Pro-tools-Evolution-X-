package com.elitedarkkaiser.redmagic.gametrigger

import android.app.Application
import android.content.Context
import android.view.ContextThemeWrapper
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.activity.ComponentActivity
import com.elitedarkkaiser.redmagic.ui.GameTriggerUi
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** Builds every game-trigger screen and presses everything on it; any exception fails the test. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [31], application = Application::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class TriggerScreensSmokeTest {
    private lateinit var context: Context
    private val landscapeDisplay = TriggerDisplayState(2688, 1216, 1)
    private val portraitDisplay = TriggerDisplayState(1216, 2688, 0)
    private val landscape = TriggerPlacement.mapping(landscapeDisplay, NativeTgkOrientation.LANDSCAPE,
        600f, 500f, 1900f, 700f, 18)

    @Before fun reset() {
        context = RuntimeEnvironment.getApplication()
        context.getSharedPreferences("native_tgk_profiles", Context.MODE_PRIVATE).edit().clear().commit()
        assertTrue(NativeTgkStorage.saveProfile(context,
            NativeTgkProfile("example.game", "Example Game", landscape = landscape)))
    }

    private fun themed() = ContextThemeWrapper(context,
        com.elitedarkkaiser.redmagic.R.style.Theme_RedmagicTools)

    /** Measures, lays out and draws [view] into a bitmap, the way a window would. */
    private fun draw(view: View, width: Int = 1080) {
        view.measure(View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(4000, View.MeasureSpec.AT_MOST))
        view.layout(0, 0, view.measuredWidth, view.measuredHeight)
        val bitmap = android.graphics.Bitmap.createBitmap(width, maxOf(1, view.measuredHeight),
            android.graphics.Bitmap.Config.ARGB_8888)
        view.draw(android.graphics.Canvas(bitmap))
    }

    private fun switches(root: View, out: MutableList<android.widget.CompoundButton> = mutableListOf()):
        List<android.widget.CompoundButton> {
        if (root is android.widget.CompoundButton) out += root
        if (root is ViewGroup) for (i in 0 until root.childCount) switches(root.getChildAt(i), out)
        return out
    }

    private fun clickables(root: View, out: MutableList<View> = mutableListOf()): List<View> {
        if (root.isClickable && root.isEnabled && root.visibility == View.VISIBLE) out += root
        if (root is ViewGroup) for (i in 0 until root.childCount) clickables(root.getChildAt(i), out)
        return out
    }

    private fun find(root: View, label: String): View? {
        if (root is TextView && root.text.toString() == label) return root
        if (root is ViewGroup) for (i in 0 until root.childCount) find(root.getChildAt(i), label)?.let { return it }
        return null
    }

    @Test fun editorMenuEveryTabAndEveryControl() {
        val profile = NativeTgkStorage.getProfile(context, "example.game")!!
        for (display in listOf(landscapeDisplay, portraitDisplay)) {
            val draft = TriggerEditorDraft(profile, NativeTgkOrientation.LANDSCAPE)
            if (display == landscapeDisplay) draft.attachDisplay(display, 18)
            val menu = TriggerEditorMenu(themed(), draft, display, TriggerEditorMenu.State(),
                updated = {}, orientationChanged = {}, save = {}, cancel = {})
            for (tab in listOf(TriggerEditorMenu.TAB_PLACE, TriggerEditorMenu.TAB_TRIGGERS,
                TriggerEditorMenu.TAB_LAYOUTS, TriggerEditorMenu.TAB_OPTIONS)) {
                menu.state.tab = tab
                menu.render()
                // Press each control once; the panel re-renders after most of them.
                var index = 0
                while (index < 40) {
                    val views = clickables(menu.view)
                    if (index >= views.size) break
                    menu.state.tab = tab
                    views[index].performClick()
                    draw(menu.view, 1000)
                    index++
                }
                menu.select(false)
                menu.showError("Example error")
                menu.refreshPosition()
            }
            menu.state.collapsed = true
            menu.render()
            draw(menu.view, 1000)
        }
    }

    @Test fun mappingPagesBuildOpenDrawAndToggleEverything() {
        val activity = Robolectric.buildActivity(ComponentActivity::class.java).setup().get()
        activity.setTheme(com.elitedarkkaiser.redmagic.R.style.Theme_RedmagicTools)
        // As MainActivity does: setContentView is what gives the window the lifecycle owners
        // the Compose controls on these pages need.
        activity.setContentView(android.widget.FrameLayout(activity))
        val looper = shadowOf(android.os.Looper.getMainLooper())
        GameTriggerUi.show(activity)
        looper.idle()
        draw(activity.window.decorView)
        // Every switch on the library page, twice, so each one returns to where it started.
        repeat(2) {
            switches(activity.window.decorView).forEach { it.toggle(); looper.idle() }
            draw(activity.window.decorView)
        }
        val game = find(activity.window.decorView, "Example Game")
        assertNotNull("game row", game)
        var row: View = game!!
        while (!row.isClickable) row = row.parent as View
        row.performClick()
        looper.idle()
        draw(activity.window.decorView)
        repeat(2) {
            switches(activity.window.decorView).forEach { it.toggle(); looper.idle() }
            draw(activity.window.decorView)
        }
        listOf("Left trigger (L)", "Right trigger (R)", "Layout in use").forEach { label ->
            val view = find(activity.window.decorView, label)
            assertNotNull(label, view)
            var target: View = view!!
            while (!target.isClickable && target.parent is View) target = target.parent as View
            target.performClick()
            looper.idle()
        }
        activity.finish()
        looper.idle()
    }

    /** Presses every control on the mapping library and on a game's page, one at a time. */
    @Test fun mappingPagesPressEveryControl() {
        val failures = java.util.Collections.synchronizedList(mutableListOf<Throwable>())
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { _, error -> failures += error }
        try {
            val activity = Robolectric.buildActivity(ComponentActivity::class.java).setup().get()
            activity.setTheme(com.elitedarkkaiser.redmagic.R.style.Theme_RedmagicTools)
            activity.setContentView(android.widget.FrameLayout(activity))
            val looper = shadowOf(android.os.Looper.getMainLooper())
            fun settle() { looper.idle(); Thread.sleep(50); looper.idle() }
            fun pressAll(skip: Set<String>) {
                var index = 0
                while (index < 60) {
                    val views = clickables(activity.window.decorView)
                    if (index >= views.size) break
                    val view = views[index++]
                    if (skip.any { find(view, it) != null }) continue
                    view.performClick()
                    settle()
                    draw(activity.window.decorView)
                }
            }
            GameTriggerUi.show(activity)
            settle()
            // The library page, minus the game row (opened explicitly below).
            pressAll(setOf("Example Game"))
            val game = find(activity.window.decorView, "Example Game")
            assertNotNull("game row", game)
            var row: View = game!!
            while (!row.isClickable) row = row.parent as View
            row.performClick()
            settle()
            pressAll(setOf("Example Game"))
            activity.finish()
            settle()
        } finally {
            Thread.setDefaultUncaughtExceptionHandler(previous)
        }
        if (failures.isNotEmpty()) throw AssertionError("Background thread crashed", failures.first())
    }
}
