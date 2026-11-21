package com.elitedarkkaiser.redmagic.ui

import org.junit.Assert.*
import org.junit.Test
import java.util.Locale

class AppPickerTest {
    private val apps = listOf(
        AppEntry("com.roblox.client", "Roblox", game = true),
        AppEntry("example.game", "Space game", game = true),
        AppEntry("moe.rukamori.archivetune", "ArchiveTune"),
        AppEntry("com.android.settings", "Settings", system = true))
    private val selected = setOf("com.roblox.client", "moe.rukamori.archivetune")

    @Test fun filtersSplitGamesSelectedDownloadedAndSystemApps() {
        assertEquals(apps.take(2), AppPicker.filter(apps, AppFilter.GAMES, ""))
        assertEquals(apps, AppPicker.filter(apps, AppFilter.ALL, ""))
        assertEquals(listOf(apps[0], apps[2]), AppPicker.filter(apps, AppFilter.SELECTED, "", selected))
        assertEquals(apps.take(3), AppPicker.filter(apps, AppFilter.DOWNLOADED, ""))
        assertEquals(listOf(apps[3]), AppPicker.filter(apps, AppFilter.SYSTEM, ""))
    }

    @Test fun searchMatchesNamesAndPackagesWithinTheSelectedFilter() {
        assertEquals(listOf(apps[0]), AppPicker.filter(apps, AppFilter.GAMES, " CLIENT "))
        assertEquals(listOf(apps[2]), AppPicker.filter(apps, AppFilter.ALL, "ARCHIVETUNE"))
        assertTrue(AppPicker.filter(apps, AppFilter.GAMES, "ARCHIVETUNE").isEmpty())
        assertTrue(AppPicker.filter(apps, AppFilter.SELECTED, "Space", selected).isEmpty())
        assertTrue(AppPicker.filter(apps, AppFilter.ALL, "uninstalled").isEmpty())
        assertEquals(apps, AppPicker.filter(apps, AppFilter.ALL, "   "))
    }

    @Test fun searchIgnoresTheDeviceLocale() {
        val previous = Locale.getDefault()
        try {
            Locale.setDefault(Locale.forLanguageTag("tr-TR"))
            val indigo = AppEntry("org.example.indigo", "INDIGO")
            assertEquals(listOf(indigo), AppPicker.filter(listOf(indigo), AppFilter.ALL, " indigo "))
        } finally {
            Locale.setDefault(previous)
        }
    }
}
