package com.elitedarkkaiser.redmagic

import android.Manifest
import android.app.KeyguardManager
import android.content.Context
import android.content.pm.PackageManager
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.media.AudioManager
import android.os.Handler
import android.os.Looper
import android.os.Process
import java.util.concurrent.ConcurrentHashMap

/** Actions run serially off the UI thread. Only effects this slider owns are restored on release. */
internal class MagicKeyRunner(private val context: Context) : AutoCloseable {
    private val cameras = context.getSystemService(CameraManager::class.java)
    private val torchStates = ConcurrentHashMap<String, Boolean>()
    private var ownedTorch: Pair<String, Boolean>? = null
    private var previousRinger: Int? = null
    private var performanceOwned = false
    private val torchCallback = object : CameraManager.TorchCallback() {
        override fun onTorchModeChanged(cameraId: String, enabled: Boolean) { torchStates[cameraId] = enabled }
    }
    init { runCatching { cameras.registerTorchCallback(torchCallback, Handler(Looper.getMainLooper())) } }

    fun change(mode: MagicKeyMode, on: Boolean, app: String?) {
        if (!on) { release(); return }
        release()
        when (mode) {
            MagicKeyMode.DISABLED -> Unit
            MagicKeyMode.PERFORMANCE -> { performanceOwned = true; com.elitedarkkaiser.redmagic.parts.PartsRuntime.magic(context, true) }
            MagicKeyMode.CAMERA -> {
                val locked = context.getSystemService(KeyguardManager::class.java).isKeyguardLocked
                command("am start --user current -a android.media.action.STILL_IMAGE_CAMERA${if (locked) "_SECURE" else ""}")
            }
            MagicKeyMode.GAME_SPACE -> openFirst(listOf("io.chaldeaprjkt.gamespace", "org.lineageos.gamespace",
                "com.evolutionx.gamespace"), "Game Space")
            MagicKeyMode.RECORDER -> {
                if (!open(listOf("org.lineageos.recorder", "com.android.soundrecorder",
                    "com.google.android.apps.recorder")))
                    command("am start --user current -a android.provider.MediaStore.RECORD_SOUND")
            }
            MagicKeyMode.APP -> command(MagicKeyCommands.launchPackage(
                app ?: error("Choose an app for Magic Key")))
            MagicKeyMode.SOUND -> {
                val before = context.getSystemService(AudioManager::class.java).ringerMode
                command(MagicKeyCommands.ringer(AudioManager.RINGER_MODE_VIBRATE))
                previousRinger = before
            }
            MagicKeyMode.FLASHLIGHT -> {
                if (context.checkSelfPermission(Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
                    MagicKeyRuntime.root("pm grant --user ${Process.myUid() / 100_000} ${context.packageName} android.permission.CAMERA")
                }
                check(context.checkSelfPermission(Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
                    "Flashlight needs camera permission"
                }
                val ids = cameras.cameraIdList.filter { cameras.getCameraCharacteristics(it)
                    .get(CameraCharacteristics.FLASH_INFO_AVAILABLE) == true }
                val id = ids.firstOrNull { cameras.getCameraCharacteristics(it)
                    .get(CameraCharacteristics.LENS_FACING) == CameraCharacteristics.LENS_FACING_BACK }
                    ?: ids.firstOrNull() ?: error("No flashlight is available")
                val before = torchStates[id] ?: false
                cameras.setTorchMode(id, true)
                ownedTorch = id to before
            }
        }
    }

    private fun open(packages: List<String>) = packages.any {
        MagicKeyCommands.succeeded(MagicKeyRuntime.root(MagicKeyCommands.launchPackage(it)))
    }
    private fun openFirst(packages: List<String>, label: String) { check(open(packages)) { "$label isn't available on this ROM" } }
    private fun command(command: String) {
        check(MagicKeyCommands.succeeded(MagicKeyRuntime.root(command))) { "The selected action could not run" }
    }

    fun release() {
        if (performanceOwned) { performanceOwned = false; com.elitedarkkaiser.redmagic.parts.PartsRuntime.magic(context, false) }
        val torch = ownedTorch.also { ownedTorch = null }
        val ringer = previousRinger.also { previousRinger = null }
        torch?.let { runCatching { cameras.setTorchMode(it.first, it.second) } }
        ringer?.let { runCatching { command(MagicKeyCommands.ringer(it)) } }
    }
    override fun close() { release(); runCatching { cameras.unregisterTorchCallback(torchCallback) } }
}
