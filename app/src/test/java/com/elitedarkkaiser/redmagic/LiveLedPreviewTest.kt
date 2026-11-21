package com.elitedarkkaiser.redmagic

import com.elitedarkkaiser.redmagic.LiveLedPreview.Zone
import com.elitedarkkaiser.redmagic.state.LedState
import org.junit.Assert.*
import org.junit.Test

class LiveLedPreviewTest {
    private val red = LedState(true, "steady", 1)
    private val blue = LedState(true, "breathe", 7)

    @Test fun theMasterAloneEnablesPreviewAndUpdatesRemainUnsaved() {
        var master = false
        var owners = 0
        var restores = 0
        val writes = mutableListOf<Pair<Zone, LedState>>()
        val preview = LiveLedPreview({ master }, { zone, state -> writes += zone to state },
            { owners++ }, { owners--; restores++ })
        preview.update(Zone.FAN, red)
        assertTrue(writes.isEmpty())
        assertEquals(0, owners)
        master = true
        preview.update(Zone.FAN, red)
        preview.update(Zone.FAN, blue)
        preview.update(Zone.LOGO, red)
        assertEquals(listOf(Zone.FAN to red, Zone.FAN to blue, Zone.LOGO to red), writes)
        assertEquals(1, owners)
        assertEquals(0, restores)
        preview.close()
        assertEquals(0, owners)
        assertEquals(1, restores)
    }

    @Test fun cancelOrBackRestoresOnceAndCannotReplayOldChanges() {
        var restores = 0
        var disposals = 0
        val writes = mutableListOf<LedState>()
        val preview = LiveLedPreview({ true }, { _, state -> writes += state },
            restore = { restores++ }, dispose = { disposals++ })
        preview.update(Zone.LOGO, red)
        preview.close()
        preview.close()
        preview.update(Zone.LOGO, blue)
        preview.resume()
        assertEquals(listOf(red), writes)
        assertEquals(1, restores)
        assertEquals(1, disposals)
    }

    @Test fun switchingTheMasterOffReleasesPreviewUntilEnabledAgain() {
        var master = true
        var restores = 0
        val writes = mutableListOf<LedState>()
        val preview = LiveLedPreview({ master }, { _, state -> writes += state }, restore = { restores++ })
        preview.update(Zone.FAN, red)
        master = false
        preview.update(Zone.FAN, blue)
        assertEquals(listOf(red), writes)
        assertEquals(1, restores)
        master = true
        preview.update(Zone.FAN, blue)
        assertEquals(listOf(red, blue), writes)
        preview.close()
        assertEquals(2, restores)
    }

    @Test fun leavingTheAppResumesAutomationAndReturningReplaysOnlyTheCurrentDraft() {
        var owners = 0
        val writes = mutableListOf<LedState>()
        val preview = LiveLedPreview({ true }, { _, state -> writes += state },
            { owners++ }, { owners-- })
        preview.update(Zone.FAN, red)
        preview.pause()
        assertEquals(0, owners)
        preview.update(Zone.FAN, blue)
        assertEquals(listOf(red), writes)
        preview.resume()
        assertEquals(listOf(red, blue), writes)
        assertEquals(1, owners)
        preview.close()
        assertEquals(0, owners)
    }

    @Test fun closingAnUnchangedOrDisabledPickerDoesNotWriteOrRestoreHardware() {
        var writes = 0
        val preview = LiveLedPreview({ false }, { _, _ -> writes++ }, restore = { writes++ })
        preview.update(Zone.FAN, red)
        preview.close()
        assertEquals(0, writes)
    }
}
