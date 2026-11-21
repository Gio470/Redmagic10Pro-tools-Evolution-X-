package com.elitedarkkaiser.redmagic

import org.junit.Assert.*
import org.junit.Test

class MagicKeyCommandsTest {
    @Test fun packageLaunchIsScopedToTheChosenAppAndCurrentUser() {
        val command = MagicKeyCommands.launchPackage("com.roblox.client")
        assertTrue(command.contains("-p com.roblox.client"))
        assertTrue(command.contains("com.roblox.client/*"))
        assertTrue(command.contains("am start --user current -n"))
    }
    @Test fun malformedPackageNamesCannotBecomeShellCommands() {
        listOf("", "com.game;id", "com.game\nreboot", "com.game/Activity", "\u0024(id)").forEach { pkg ->
            assertTrue(runCatching { MagicKeyCommands.launchPackage(pkg) }.isFailure)
        }
    }
    @Test fun soundModesUseTheCaseSensitiveAndroidShellNames() {
        assertEquals("cmd audio set-ringer-mode VIBRATE", MagicKeyCommands.ringer(1))
        assertEquals("cmd audio set-ringer-mode NORMAL", MagicKeyCommands.ringer(2))
        assertEquals("cmd audio set-ringer-mode SILENT", MagicKeyCommands.ringer(0))
        assertTrue(runCatching { MagicKeyCommands.ringer(3) }.isFailure)
    }
    @Test fun activityErrorsCannotBeReportedAsSuccessfulActions() {
        assertFalse(MagicKeyCommands.succeeded(null))
        assertFalse(MagicKeyCommands.succeeded("Error: Activity not found"))
        assertFalse(MagicKeyCommands.succeeded("java.lang.SecurityException: denied"))
        assertTrue(MagicKeyCommands.succeeded("Starting: Intent { act=android.intent.action.MAIN }"))
    }
}
