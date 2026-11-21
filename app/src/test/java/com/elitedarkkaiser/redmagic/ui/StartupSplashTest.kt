package com.elitedarkkaiser.redmagic.ui

import android.app.Application
import android.os.Looper
import android.os.SystemClock
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
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

/** Plays the launch animation end to end, drawing every stage; any exception fails the test. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [31], application = Application::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class StartupSplashTest {
    private fun launch(): Pair<ComponentActivity, View> {
        val activity = Robolectric.buildActivity(ComponentActivity::class.java).setup().get()
        activity.setTheme(com.elitedarkkaiser.redmagic.R.style.Theme_RedmagicTools)
        val app = FrameLayout(activity)
        activity.setContentView(app)
        StartupSplash.setEnabled(activity, true)
        StartupSplash.show(activity)
        return activity to app
    }

    private fun host(activity: ComponentActivity) = activity.findViewById<ViewGroup>(android.R.id.content)

    private fun draw(activity: ComponentActivity) {
        val host = host(activity)
        host.measure(View.MeasureSpec.makeMeasureSpec(1080, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(2400, View.MeasureSpec.EXACTLY))
        host.layout(0, 0, 1080, 2400)
        val bitmap = android.graphics.Bitmap.createBitmap(1080, 2400, android.graphics.Bitmap.Config.ARGB_8888)
        host.draw(android.graphics.Canvas(bitmap))
    }

    private fun advance(ms: Long) = shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(ms))

    @Test fun playsEveryStageAndGetsOutOfTheWay() {
        val (activity, app) = launch()
        assertEquals("the splash sits over the app", 2, host(activity).childCount)
        repeat(30) {
            draw(activity)
            advance(100)
        }
        draw(activity)
        assertEquals("the splash removes itself", 1, host(activity).childCount)
        assertEquals(1f, app.scaleX, 0f)
        assertEquals(1f, app.scaleY, 0f)
    }

    @Test fun aTapSkipsToTheReveal() {
        val (activity, app) = launch()
        // Taken straight away: how far the test clock runs the animator between tests varies.
        val splash = host(activity).getChildAt(1)
        draw(activity)
        val now = SystemClock.uptimeMillis()
        splash.dispatchTouchEvent(MotionEvent.obtain(now, now, MotionEvent.ACTION_DOWN, 10f, 10f, 0))
        splash.dispatchTouchEvent(MotionEvent.obtain(now, now + 50, MotionEvent.ACTION_UP, 10f, 10f, 0))
        // Well short of the full 2.3 s: only the reveal is left to play.
        repeat(7) {
            draw(activity)
            advance(100)
        }
        assertEquals("a tap ends it early", 1, host(activity).childCount)
        assertEquals(1f, app.scaleX, 0f)
    }

    @Test fun offMeansNothingIsAdded() {
        val activity = Robolectric.buildActivity(ComponentActivity::class.java).setup().get()
        activity.setContentView(FrameLayout(activity))
        StartupSplash.setEnabled(activity, false)
        StartupSplash.show(activity)
        assertTrue(host(activity).childCount == 1)
    }
}
