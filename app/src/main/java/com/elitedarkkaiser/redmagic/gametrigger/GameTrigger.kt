package com.elitedarkkaiser.redmagic.gametrigger

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log
import java.util.concurrent.Executors

/**
 * Programs the root touch mapping ([RootTouchInjector]) when a mapped game comes to the foreground
 * and turns it off when the game leaves. Work runs on one worker thread through
 * [LatestMappingQueue], so a burst of window events does not restart the helper each time.
 */
object GameTrigger {
    private const val TAG = "RedmagicGameTrigger"
    private val worker = Executors.newSingleThreadExecutor { r -> Thread(r, "game-trigger").apply { isDaemon = true } }
    @Volatile private var foreground: String? = null
    @Volatile private var owned = false
    @Volatile private var context: Context? = null
    @Volatile var lastMessage = "No game mapping active"
        private set
    @Volatile private var lastApply = "No mapping has been attempted"
    @Volatile private var foregroundSource = "Not detected"

    private data class Target(val profile: NativeTgkProfile, val orientation: NativeTgkOrientation,
        val width: Int, val height: Int, val rotation: Int)
    private val queue = LatestMappingQueue<Target>(worker, ::apply, ::disableOwned)

    /** The touch engine, for the mapping page's status. Known once the helper has started. */
    fun engine(): String = RootTouchInjector.mode?.let { "Root touch · $it" } ?: "Root touch"
    fun ownsTriggers(): Boolean = queue.requested() || owned
    fun currentForeground(): String? = foreground

    fun currentOrientation(context: Context): NativeTgkOrientation {
        return TriggerDisplay.read(context).orientation
    }

    fun onForeground(context: Context, packageName: String, source: String = "Accessibility") {
        this.context = context.applicationContext
        foreground = packageName
        foregroundSource = source
        TriggerEditorService.foregroundChanged(packageName)
        request(context, packageName)
    }

    private fun request(context: Context, packageName: String?, force: Boolean = false) {
        val display = TriggerDisplay.read(context)
        val orientation = display.orientation
        val profile = packageName?.let { NativeTgkStorage.getProfile(context, it) }
        val target = profile?.takeIf { display.isValid() && NativeTgkStorage.enabled(context) && it.enabled &&
            it.hasCompleteMapping(orientation) && !TriggerEditorService.isEditing(it.packageName) }
            ?.let { Target(it, orientation, display.width, display.height, display.rotation) }
        if (target == null) {
            lastMessage = when {
                TriggerEditorService.isEditing(packageName.orEmpty()) -> "Placing ${orientation.name.lowercase()} targets"
                !NativeTgkStorage.enabled(context) -> "Game trigger mapping is switched off"
                packageName == null -> "Waiting for a foreground game"
                profile == null -> "No saved mapping for $packageName"
                !profile.enabled -> "Mapping for ${profile.appLabel} is switched off"
                !display.isValid() -> "Waiting for display dimensions"
                else -> "No ${orientation.name.lowercase()} targets saved for ${profile.appLabel}"
            }
        }
        queue.submit(target, force)
    }

    fun diagnostics(context: Context): String {
        val display = TriggerDisplay.read(context)
        val profiles = NativeTgkStorage.readProfiles(context).joinToString("\n") {
            "${it.packageName}: enabled=${it.enabled}; layout=${it.activeLayout()?.name ?: "Default"}; " +
                "portrait=${it.hasCompleteMapping(NativeTgkOrientation.PORTRAIT)}; " +
                "landscape=${it.hasCompleteMapping(NativeTgkOrientation.LANDSCAPE)}"
        }
        return "Mapping enabled: ${NativeTgkStorage.enabled(context)}\n" +
            TriggerRuntimeService.diagnostics() + "\nEditor: ${NativeTgkStorage.editorStatus(context)}\n" +
            "Foreground: $foreground ($foregroundSource)\n" +
            "Display: ${display.width}x${display.height}, ${display.orientation}, rotation=${display.rotation}\n" +
            "Root touch: ${RootTouchInjector.lastStatus}\n" +
            "Root touch helper: ${RootTouchInjector.helperLine ?: "not started yet (open a mapped game first)"}\n" +
            "Profiles:\n${profiles.ifBlank { "None" }}\nStatus: $lastMessage\nLast apply: $lastApply"
    }

    fun refresh(context: Context) {
        this.context = context.applicationContext
        TriggerRuntimeService.sync(context)
        request(context, foreground, force = true)
    }
    fun recover(context: Context) {
        this.context = context.applicationContext
        owned = context.getSharedPreferences("native_tgk_profiles", Context.MODE_PRIVATE).getBoolean("runtime_owned", false)
        foregroundUnavailable(context)
        queue.submit(null, force = true)
    }
    fun foregroundUnavailable(context: Context) {
        this.context = context.applicationContext
        foreground = null
        foregroundSource = "Waiting for a fresh foreground sample"
        request(context, null)
    }
    fun onConfigurationChanged(context: Context) = request(context, foreground)
    fun stop(context: Context) {
        this.context = context.applicationContext
        foreground = null
        TriggerEditorService.close(context)
        queue.submit(null, force = true)
    }

    private fun apply(target: Target, current: () -> Boolean) {
        val app = context ?: return
        runCatching {
            if (!current()) return
            owned = true
            app.getSharedPreferences("native_tgk_profiles", Context.MODE_PRIVATE).edit().putBoolean("runtime_owned", true).commit()
            lastMessage = "Applying ${target.orientation.name.lowercase()} targets for ${target.profile.appLabel}…"
            check(com.elitedarkkaiser.redmagic.HardwareController.enableTriggers()) { "Couldn't enable the shoulder triggers" }
            val profile = target.profile
            val mapping = profile.mappingFor(target.orientation) ?: return
            RootTouchInjector.onPress = {
                Thread {
                    val strength = com.elitedarkkaiser.redmagic.HapticStrength.get(app)
                    com.elitedarkkaiser.redmagic.HardwareController.vibrate(30,
                        com.elitedarkkaiser.redmagic.HapticStrength.scale(180, strength))
                }.start()
            }
            val result = RootTouchInjector.apply(app,
                mapping.left!!.scaledTo(target.width, target.height),
                mapping.right!!.scaledTo(target.width, target.height),
                profile.effectiveLeftBehavior(), profile.effectiveRightBehavior(),
                profile.effectiveLeftRapidFireCount(), profile.effectiveRightRapidFireCount(),
                target.rotation, target.width, target.height, profile.hapticsEnabled)
            lastApply = "${profile.packageName} ${target.orientation}: ${result.message}"
            lastMessage = if (result.success) "Active: ${profile.appLabel} · ${target.orientation.name.lowercase()}" else result.message
            if (result.success && current()) {
                Log.i(TAG, "Applied ${target.orientation} mapping for ${profile.packageName}")
            } else if (!result.success) {
                disableOwned()
                if (current()) Handler(Looper.getMainLooper()).post {
                    android.widget.Toast.makeText(app, "Couldn't apply trigger mapping: ${result.message}",
                        android.widget.Toast.LENGTH_LONG).show()
                }
            }
        }.onFailure {
            lastMessage = it.message ?: "Trigger mapping failed"
            lastApply = "${target.profile.packageName} ${target.orientation}: $lastMessage"
            Log.e(TAG, "Mapping failed", it)
            disableOwned()
        }
    }

    private fun disableOwned() {
        RootTouchInjector.off()
        if (!owned) return
        val app = context ?: return
        runCatching {
            owned = false
            app.getSharedPreferences("native_tgk_profiles", Context.MODE_PRIVATE).edit().putBoolean("runtime_owned", false).commit()
            if (!com.elitedarkkaiser.redmagic.readTriggerPrefsSnapshot(app).triggerEnabled) {
                com.elitedarkkaiser.redmagic.HardwareController.disableTriggers()
            }
        }.onFailure { Log.e(TAG, "Mapping cleanup failed", it) }
    }
}
