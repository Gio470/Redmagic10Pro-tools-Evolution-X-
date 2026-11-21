package com.elitedarkkaiser.redmagic

import android.app.Application
import android.content.Context
import com.elitedarkkaiser.redmagic.storage.AppPrefs
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [31], application = Application::class)
class MagicKeyRuntimeTest {
    @Test fun oldBackendPreferenceCannotDisableTheRootListenerOrChangeItsSavedAction() {
        val context: Context = RuntimeEnvironment.getApplication()
        val prefs = context.getSharedPreferences(AppPrefs.PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().clear().putBoolean("magic_key_root_input", false)
            .putInt("magic_key_root_mode", MagicKeyMode.FLASHLIGHT.value).commit()
        assertTrue(MagicKeyRuntime.wanted(context))
        assertEquals(MagicKeyMode.FLASHLIGHT, MagicKeyRuntime.mode(context))
        prefs.edit().putInt("magic_key_root_mode", MagicKeyMode.DISABLED.value).commit()
        assertFalse(MagicKeyRuntime.wanted(context))
    }
}
