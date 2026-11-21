package com.elitedarkkaiser.redmagic.parts

import android.content.Context
import android.os.Handler
import org.json.JSONObject
import java.io.File
import java.io.OutputStreamWriter
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

internal class PartsClient(private val context: Context, private val worker: Handler,
    private val changed: (JSONObject) -> Unit) : AutoCloseable {
    private var child: Process? = null
    private var outputThread: Thread? = null
    @Volatile private var helperFailure: String? = null
    private var input: OutputStreamWriter? = null
    private var lastConfig: String? = null
    private var lastPoll = 0L
    private var retryAt = 0L
    private var error = "Root helper did not start"
    fun invalidate() { lastConfig = null }
    fun send(config: JSONObject) {
        if (child?.isAlive != true) {
            if (android.os.SystemClock.elapsedRealtime() < retryAt) return
            close()
            retryAt = android.os.SystemClock.elapsedRealtime() + 10_000
            if (!start()) { PartsRuntime.status = error; return }
        }
        val text = config.toString()
        val now = android.os.SystemClock.elapsedRealtime()
        if (text == lastConfig && now - lastPoll < 10_000) return
        val line = if (text == lastConfig) "POLL" else text
        try { input!!.write(line + "\n"); input!!.flush(); lastConfig = text; lastPoll = now }
        catch (e: Exception) { PartsRuntime.status = e.message ?: "Root helper stopped"; close() }
    }
    private fun quote(value: String) = "'" + value.replace("'", "'\\''") + "'"
    private fun start(): Boolean {
        val folder = File(context.filesDir, "nubia-parts").apply { mkdirs() }
        context.assets.list("nubia").orEmpty().forEach { name ->
            context.assets.open("nubia/$name").use { source -> File(folder, name).outputStream().use { source.copyTo(it) } }
        }
        val latch = CountDownLatch(1)
        var ready = false
        helperFailure = null
        val process = runCatching { ProcessBuilder("su").start() }.getOrElse { error = "Root access is required"; return false }
        val writer = OutputStreamWriter(process.outputStream)
        writer.write("exec env CLASSPATH=${quote(context.applicationInfo.sourceDir)} /system/bin/app_process /system/bin " +
            "com.elitedarkkaiser.redmagic.parts.DevicePartsCli ${quote(context.applicationInfo.nativeLibraryDir)} ${quote(folder.path)}\n")
        writer.flush()
        child = process; input = writer
        outputThread = Thread({
            runCatching { process.inputStream.bufferedReader().forEachLine { line ->
                when {
                    line == "READY" -> { ready = true; latch.countDown() }
                    line.startsWith("ERROR ") -> { error = line.removePrefix("ERROR "); helperFailure = error; latch.countDown() }
                    line.startsWith("STATUS ") -> runCatching { JSONObject(line.removePrefix("STATUS ")) }.onSuccess { status ->
                        worker.post { if (child === process) changed(status) }
                    }
                }
            } }
            latch.countDown()
            worker.post { if (child === process) { child = null; input = null; lastConfig = null; PartsRuntime.status = "Root helper stopped" } }
        }, "parts-output").apply { isDaemon = true; start() }
        Thread({ runCatching { process.errorStream.bufferedReader().forEachLine { android.util.Log.w("RedmagicParts", it) } } },
            "parts-errors").apply { isDaemon = true; start() }
        latch.await(6, TimeUnit.SECONDS)
        if (!ready) close()
        return ready
    }
    override fun close() { close(false); Unit }
    fun close(waitForExit: Boolean): String? {
        val old = child
        child = null; lastConfig = null
        runCatching { input?.write("QUIT\n"); input?.flush(); input?.close() }; input = null
        if (old != null) {
            val finish = { if (!old.waitFor(8, TimeUnit.SECONDS)) { helperFailure = "Device cleanup timed out"; old.destroyForcibly() }; outputThread?.join(500) }
            if (waitForExit) finish() else Thread({ finish() }, "parts-stop").apply { isDaemon = true; start() }
        }
        return if (waitForExit) helperFailure else null
    }
}
