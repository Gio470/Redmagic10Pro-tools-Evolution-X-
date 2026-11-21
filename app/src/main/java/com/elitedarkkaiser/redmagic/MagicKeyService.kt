package com.elitedarkkaiser.redmagic

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.os.IBinder
import java.util.concurrent.Executors

/** A blocking read of the slider's switch input, independent of Nubia's missing stock handler. */
class MagicKeyService : Service() {
    private val actions = Executors.newSingleThreadExecutor { task -> Thread(task, "magic-key-action").apply { isDaemon = true } }
    private lateinit var runner: MagicKeyRunner
    @Volatile private var running = true
    @Volatile private var child: Process? = null
    @Volatile private var readerPid: Int? = null
    private var monitor: Thread? = null
    private lateinit var dispatch: MagicKeyDispatch

    override fun onBind(intent: Intent?): IBinder? = null
    override fun onCreate() {
        super.onCreate()
        getSystemService(NotificationManager::class.java).createNotificationChannel(
            NotificationChannel(CHANNEL, "Magic Key", NotificationManager.IMPORTANCE_LOW))
        startForeground(NOTIFICATION, notification("Starting slider listener"))
        runner = MagicKeyRunner(this)
        dispatch = MagicKeyDispatch({ task -> actions.execute(task) }, ::configuration,
            change = { config, on ->
                runCatching { runner.change(config.mode, on, config.app) }
                    .onSuccess { report("Listening for slider movements") }
                    .onFailure { report(it.message ?: "Could not run the selected action") }
            }, release = { runner.release() })
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == STOP) MagicKeyRuntime.configure(this, MagicKeyMode.DISABLED)
        if (!MagicKeyRuntime.wanted(this)) { stopSelf(); return START_NOT_STICKY }
        dispatch.configure(configuration() ?: return START_NOT_STICKY)
        if (monitor == null) {
            MagicKeyRuntime.status = "Starting slider listener"
            monitor = Thread({ readSlider() }, "magic-key-input").apply { isDaemon = true; start() }
        }
        return START_STICKY
    }

    private fun readSlider() {
        try {
            check(MagicKeyRuntime.root("settings put system fourth_physical_key_function_value 0") != null) { "Root access wasn't available" }
            val dump = MagicKeyRuntime.root("getevent -p") ?: error("Could not read input devices")
            val device = MagicKeyInput.device(dump) ?: error("No supported Magic Key slider input found")
            MagicKeyRuntime.input = "${device.name} (${device.path})"
            val edges = MagicKeyInput.Edges(device.initial)
            val process = ProcessBuilder("su", "-c", "echo MAGIC_PID \u0024\u0024; exec getevent -lt ${device.path}")
                .redirectErrorStream(true).start()
            child = process
            process.inputStream.bufferedReader().useLines { lines ->
                lines.forEach { line ->
                    if (!running) return@forEach
                    if (line.startsWith("MAGIC_PID ")) {
                        readerPid = line.substringAfter(' ').trim().toIntOrNull()?.takeIf { it > 1 }
                        report("Listening for slider movements")
                    } else if (line.contains("Permission denied", true) || line.contains("could not open", true)) {
                        error(line.take(160))
                    } else MagicKeyInput.value(line)?.let { value ->
                        edges.update(value, MagicKeyRuntime.reversed(this))?.let { on ->
                            dispatch.event(on)
                        }
                    }
                }
            }
            if (running) error("Slider input reader stopped")
        } catch (e: Throwable) {
            if (running) { report(e.message ?: "Slider input failed"); stopSelf() }
        }
    }

    private fun report(message: String) {
        if (!running) return
        MagicKeyRuntime.status = message
        if (running) getSystemService(NotificationManager::class.java).notify(NOTIFICATION, notification(message))
    }

    private fun configuration(): MagicKeyConfig? {
        val mode = MagicKeyRuntime.mode(this)
        return if (mode == MagicKeyMode.DISABLED) null else
            MagicKeyConfig(mode, savedMagicKeyAppPackageStorage(this), MagicKeyRuntime.reversed(this))
    }

    private fun notification(message: String): Notification {
        val open = PendingIntent.getActivity(this, NOTIFICATION, Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val stop = PendingIntent.getService(this, NOTIFICATION, Intent(this, javaClass).setAction(STOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        return Notification.Builder(this, CHANNEL).setSmallIcon(android.R.drawable.ic_menu_manage)
            .setContentTitle("Magic Key · ${MagicKeyRuntime.mode(this).label}")
            .setContentText(message).setOngoing(true).setContentIntent(open)
            .addAction(Notification.Action.Builder(null, "Turn off", stop).build()).build()
    }

    override fun onDestroy() {
        running = false
        dispatch.stop()
        child?.destroyForcibly()
        val pid = readerPid
        if (pid != null) Thread({ MagicKeyRuntime.root(
            "case \u0024(cat /proc/$pid/cmdline 2>/dev/null) in *getevent*) kill $pid ;; esac") }, "magic-key-stop").start()
        actions.execute { runner.close() }
        actions.shutdown()
        super.onDestroy()
    }
    companion object {
        private const val CHANNEL = "magic_key_root"
        private const val NOTIFICATION = 245
        private const val STOP = "magic-key-stop"
    }
}
