package com.elitedarkkaiser.redmagic

import android.content.Context
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Keeps the stack trace of a crash so it can be read back after the app restarts. */
object CrashLog {

    private const val FILE = "crash.log"
    /** Two is enough to see a repeat without the file growing without bound. */
    private const val KEEP = 2

    fun install(context: Context) {
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        val appContext = context.applicationContext

        Thread.setDefaultUncaughtExceptionHandler { thread, error ->
            runCatching { record(appContext, thread, error) }
            previous?.uncaughtException(thread, error)
        }
    }

    private fun record(context: Context, thread: Thread, error: Throwable) {
        val stamp = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.ENGLISH).format(Date())
        val entry = buildString {
            appendLine("---- $stamp  on ${thread.name}")
            appendLine("app ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})")
            appendLine(android.util.Log.getStackTraceString(error).trim())
            appendLine()
        }

        val file = File(context.filesDir, FILE)
        val kept = if (file.exists()) {
            file.readText().split("---- ").filter { it.isNotBlank() }.takeLast(KEEP - 1)
                .joinToString("") { "---- $it" }
        } else {
            ""
        }
        file.writeText(kept + entry)
    }

    fun read(context: Context): String {
        val file = File(context.filesDir, FILE)
        return if (file.exists()) file.readText().trim() else ""
    }

    fun clear(context: Context) {
        File(context.filesDir, FILE).delete()
    }
}
