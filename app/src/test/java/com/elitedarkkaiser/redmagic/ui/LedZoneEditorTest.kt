package com.elitedarkkaiser.redmagic.ui

import android.app.Activity
import android.app.Application
import android.view.View
import android.view.ViewGroup
import android.widget.CompoundButton
import android.widget.TextView
import com.elitedarkkaiser.redmagic.saveLogoLedStateStorage
import com.elitedarkkaiser.redmagic.savedLogoLedStateStorage
import com.elitedarkkaiser.redmagic.state.LedState
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [31], application = Application::class)
class LedZoneEditorTest {
    private fun descendants(view: View): List<View> = listOf(view) +
        if (view is ViewGroup) (0 until view.childCount).flatMap { descendants(view.getChildAt(it)) }
        else emptyList()

    @Test fun pickingAColourPersistsImmediatelyAndReopeningShowsTheSelection() {
        val activity = Robolectric.buildActivity(Activity::class.java).setup().get()
        activity.setTheme(com.google.android.material.R.style.Theme_Material3_DayNight_NoActionBar)
        saveLogoLedStateStorage(activity, LedState(true, "steady", 1))
        var writes = 0
        fun editor() = LedZoneEditor.create(activity, "Light", savedLogoLedStateStorage(activity), allowOff = false) {
            writes++
            saveLogoLedStateStorage(activity, it)
        }
        val view = editor()
        assertEquals(0, writes)
        val blue = descendants(view).single { it.contentDescription?.toString() == "Blue" }
        blue.performClick()
        assertEquals(LedState(true, "steady", 7), savedLogoLedStateStorage(activity))
        assertEquals(1, writes)
        val reopened = editor()
        assertTrue(descendants(reopened).single { it.contentDescription?.toString() == "Blue, selected" }.isSelected)
        assertFalse(descendants(reopened).any { it is CompoundButton })
        assertFalse(descendants(reopened).filterIsInstance<TextView>().any { it.text == "Save" || it.text == "Cancel" })
        activity.finish()
    }

    @Test fun coloursStayInactiveForAnAutomaticZoneWhoseAnimationIsOff() {
        val activity = Robolectric.buildActivity(Activity::class.java).setup().get()
        activity.setTheme(com.google.android.material.R.style.Theme_Material3_DayNight_NoActionBar)
        var writes = 0
        val view = LedZoneEditor.create(activity, "Logo LED", LedState(false, "steady", 1)) { writes++ }
        val blue = descendants(view).single { it.contentDescription?.toString() == "Blue" }
        assertFalse(blue.isEnabled)
        blue.performClick()
        assertEquals(0, writes)
        activity.finish()
    }
}
