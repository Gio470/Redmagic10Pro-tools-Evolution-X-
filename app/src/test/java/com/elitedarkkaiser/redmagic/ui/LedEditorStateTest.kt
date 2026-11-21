package com.elitedarkkaiser.redmagic.ui

import com.elitedarkkaiser.redmagic.state.LedState
import org.junit.Assert.*
import org.junit.Test

class LedEditorStateTest {
    @Test fun editingANormalLightSavesEachChoiceWithoutSwitchingItsCardMasterOn() {
        val saved = mutableListOf<LedState>()
        val editor = LedEditorState(LedState(false, "steady", 1), fan = false, allowOff = false) { saved += it }
        editor.colour(7)
        editor.animation("breathe")
        assertEquals(listOf(LedState(false, "steady", 7), LedState(false, "breathe", 7)), saved)
    }

    @Test fun anAutomaticZoneCanBeTurnedOffAndResumedWithoutLosingItsColour() {
        var saved = LedState(true, "breathe", 7)
        val editor = LedEditorState(saved, fan = true, allowOff = true) { saved = it }
        editor.animation("off")
        assertEquals(LedState(false, "breathe", 7), saved)
        editor.colour(1)
        assertEquals(7, saved.color)
        editor.animation("steady")
        assertEquals(LedState(true, "steady", 7), saved)
    }

    @Test fun reopeningUsesTheLastSavedChoiceWithoutASeparateSaveAction() {
        var saved = LedState(true, "steady", 1)
        LedEditorState(saved, fan = false, allowOff = false) { saved = it }.colour(9)
        val reopened = LedEditorState(saved, fan = false, allowOff = false) { saved = it }
        assertEquals(LedState(true, "steady", 9), reopened.state)
        reopened.animation("flashing")
        assertEquals(LedState(true, "flashing", 9), saved)
    }

    @Test fun switchingFromAnOldPresetToAnAnimationRestoresAUsableColour() {
        val editor = LedEditorState(LedState(true, "preset:0x3002101", -1), true, true) {}
        editor.animation("flow")
        assertEquals(LedState(true, "flow", 5), editor.state)
        editor.preset("0x3002102")
        editor.colour(7)
        assertEquals(LedState(true, "steady", 7), editor.state)
    }

    @Test fun choosingOffCannotDisableANormalCardsMaster() {
        val editor = LedEditorState(LedState(true, "steady", 1), false, false) {}
        editor.animation("off")
        assertTrue(editor.state.enabled)
        assertEquals("steady", editor.state.effect)
    }
}
