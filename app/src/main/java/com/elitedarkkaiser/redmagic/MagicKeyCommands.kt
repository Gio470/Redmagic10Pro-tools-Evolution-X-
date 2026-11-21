package com.elitedarkkaiser.redmagic

internal object MagicKeyCommands {
    fun launchPackage(pkg: String): String {
        require(pkg.matches(Regex("[A-Za-z][A-Za-z0-9_]*(\\.[A-Za-z0-9_]+)+")))
        return """
            component=${'$'}(cmd package resolve-activity --brief --user current -a android.intent.action.MAIN -c android.intent.category.LAUNCHER -p $pkg | tail -n 1)
            case "${'$'}component" in
              $pkg/*) am start --user current -n "${'$'}component" ;;
              *) component=${'$'}(cmd package resolve-activity --brief --user current -a android.intent.action.MAIN -p $pkg | tail -n 1)
                 case "${'$'}component" in $pkg/*) am start --user current -n "${'$'}component" ;; *) exit 1 ;; esac ;;
            esac
        """.trimIndent()
    }
    fun ringer(mode: Int): String {
        val name = when (mode) { 0 -> "SILENT"; 1 -> "VIBRATE"; 2 -> "NORMAL"; else -> error("Invalid ringer mode") }
        return "cmd audio set-ringer-mode $name"
    }
    fun succeeded(output: String?) = output != null && !output.lineSequence().any {
        it.trimStart().startsWith("Error", true) || it.contains("Exception") || it.contains("Unknown command", true)
    }
}
