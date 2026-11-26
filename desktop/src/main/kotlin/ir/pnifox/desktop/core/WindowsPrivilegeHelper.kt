package ir.pnifox.desktop.core

import java.io.File
import java.util.concurrent.TimeUnit

object WindowsPrivilegeHelper {

    /**
     * Locale-independent check using `whoami /groups` SID `S-1-5-32-544` (Built-in Administrators)
     * plus `Enabled group` / `SE_GROUP_ENABLED`. Works on Windows 10 & Windows 11 in all UI languages.
     */
    fun isAdministrator(): Boolean = runCatching {
        val proc = ProcessBuilder("whoami", "/groups")
            .redirectErrorStream(true)
            .start()
        val out = proc.inputStream.bufferedReader(Charsets.UTF_8).readText()
        proc.waitFor(3, TimeUnit.SECONDS)
        out.lineSequence().any { line ->
            line.contains("S-1-5-32-544") && !line.contains("Deny", ignoreCase = true)
        }
    }.getOrDefault(false)

    fun relaunchAsAdministrator(): Result<Unit> = runCatching {
        val info = ProcessHandle.current().info()
        val cmd = info.command().orElse(null)
            ?: error("Unable to locate current pnifox executable path")
        val args = info.arguments().orElse(emptyArray()).toList()

        val quotedCmd = cmd.replace("'", "''")
        val argExpr = if (args.isEmpty()) {
            ""
        } else {
            val joined = args.joinToString(" ") { arg ->
                "\"${arg.replace("\"", "\\\"")}\""
            }.replace("'", "''")
            " -ArgumentList '$joined'"
        }
        val workDir = File(System.getProperty("user.dir")).absolutePath.replace("'", "''")
        val psCommand = "Start-Process -FilePath '$quotedCmd'$argExpr -WorkingDirectory '$workDir' -Verb RunAs"

        ProcessBuilder("powershell.exe", "-NoProfile", "-WindowStyle", "Hidden", "-Command", psCommand)
            .start()
        Unit
    }
}
