package com.elitedarkkaiser.redmagic

import java.io.BufferedReader
import java.io.DataOutputStream
import java.io.InputStream
import java.io.InputStreamReader

object RootShell {

    fun hasRoot(): Boolean {
        val output = execForOutput("id")
        return output?.contains("uid=0") == true
    }

    fun exec(command: String): Boolean {
        return execInteractive(command) || execSuC(command)
    }

    fun execForOutput(command: String): String? {
        return execForOutputInteractive(command) ?: execForOutputSuC(command)
    }

    /**
     * Reads a stream to the end on a thread of its own, so nothing upstream of [drain] can ever
     * be the thing that fills the pipe and stalls the child.
     */
    private fun drain(stream: InputStream): Thread {
        val reader = BufferedReader(InputStreamReader(stream))
        val thread = Thread { runCatching { while (reader.readLine() != null) { /* discarded */ } } }
        thread.isDaemon = true
        thread.start()
        return thread
    }

    /** Same as [drain], but keeps what it read instead of discarding it. */
    private fun collect(stream: InputStream): Pair<Thread, StringBuilder> {
        val builder = StringBuilder()
        val reader = BufferedReader(InputStreamReader(stream))
        val thread = Thread {
            runCatching {
                var line: String?
                while (true) {
                    line = reader.readLine() ?: break
                    if (builder.isNotEmpty()) builder.append('\n')
                    builder.append(line)
                }
            }
        }
        thread.isDaemon = true
        thread.start()
        return thread to builder
    }

    private fun execInteractive(command: String): Boolean {
        return try {
            val process = Runtime.getRuntime().exec("su")
            val os = DataOutputStream(process.outputStream)

            os.writeBytes("$command\n")
            os.writeBytes("exit\n")
            os.flush()
            os.close()

            val out = drain(process.inputStream)
            val err = drain(process.errorStream)
            val result = process.waitFor() == 0
            out.join()
            err.join()
            result
        } catch (e: Exception) {
            false
        }
    }

    private fun execSuC(command: String): Boolean {
        return try {
            val process = Runtime.getRuntime().exec(arrayOf("su", "-c", command))
            val out = drain(process.inputStream)
            val err = drain(process.errorStream)
            val result = process.waitFor() == 0
            out.join()
            err.join()
            result
        } catch (e: Exception) {
            false
        }
    }

    private fun execForOutputInteractive(command: String): String? {
        return try {
            val process = Runtime.getRuntime().exec("su")
            val os = DataOutputStream(process.outputStream)

            os.writeBytes("$command\n")
            os.writeBytes("exit\n")
            os.flush()
            os.close()

            val (outThread, stdout) = collect(process.inputStream)
            val (errThread, stderr) = collect(process.errorStream)
            process.waitFor()
            outThread.join()
            errThread.join()

            val result = buildString {
                if (stdout.isNotBlank()) append(stdout)
                if (stderr.isNotBlank()) {
                    if (isNotEmpty()) append("\n")
                    append(stderr)
                }
            }.trim()

            result.ifEmpty { null }
        } catch (e: Exception) {
            null
        }
    }

    private fun execForOutputSuC(command: String): String? {
        return try {
            val process = Runtime.getRuntime().exec(arrayOf("su", "-c", command))

            val (outThread, stdout) = collect(process.inputStream)
            val (errThread, stderr) = collect(process.errorStream)
            process.waitFor()
            outThread.join()
            errThread.join()

            val result = buildString {
                if (stdout.isNotBlank()) append(stdout)
                if (stderr.isNotBlank()) {
                    if (isNotEmpty()) append("\n")
                    append(stderr)
                }
            }.trim()

            result.ifEmpty { null }
        } catch (e: Exception) {
            null
        }
    }
}
