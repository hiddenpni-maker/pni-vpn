package ir.pnifox.desktop.core

import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File
import java.util.concurrent.TimeUnit

@Serializable
private data class ProxyBackupState(
    val previousEnable: Int = 0,
    val previousServer: String = "",
    val previousOverride: String = "",
)

class WindowsSystemProxy(
    private val stateDir: File,
) {
    private val json = Json { ignoreUnknownKeys = true }
    private val backupFile = File(stateDir, "system-proxy-backup.json")

    @Volatile
    var isApplied: Boolean = false
        private set

    fun restoreIfStaleOnStartup(): Boolean {
        if (!backupFile.exists()) return false
        return restore()
    }

    fun apply(httpPort: Int, bypassIran: Boolean): Result<Unit> = runCatching {
        stateDir.mkdirs()
        if (!backupFile.exists()) {
            val backup = ProxyBackupState(
                previousEnable = queryRegDword("ProxyEnable") ?: 0,
                previousServer = queryRegString("ProxyServer").orEmpty(),
                previousOverride = queryRegString("ProxyOverride").orEmpty(),
            )
            backupFile.writeText(json.encodeToString(backup), Charsets.UTF_8)
        }

        val overrideList = buildString {
            append("localhost;127.*;10.*;172.16.*;172.17.*;172.18.*;172.19.*;172.20.*;192.168.*;<local>")
            if (bypassIran) {
                append(";*.ir")
            }
        }
        runReg("add", REG_PATH, "/v", "ProxyEnable", "/t", "REG_DWORD", "/d", "1", "/f")
        runReg("add", REG_PATH, "/v", "ProxyServer", "/t", "REG_SZ", "/d", "127.0.0.1:$httpPort", "/f")
        runReg("add", REG_PATH, "/v", "ProxyOverride", "/t", "REG_SZ", "/d", overrideList, "/f")
        notifyWinInetChanged()
        isApplied = true
    }

    fun restore(): Boolean {
        val hadBackup = backupFile.exists()
        val res = runCatching {
            val backup = if (hadBackup) {
                runCatching { json.decodeFromString<ProxyBackupState>(backupFile.readText(Charsets.UTF_8)) }.getOrNull()
            } else {
                null
            }
            val prevEnable = backup?.previousEnable ?: 0
            runReg("add", REG_PATH, "/v", "ProxyEnable", "/t", "REG_DWORD", "/d", prevEnable.toString(), "/f")
            if (!backup?.previousServer.isNullOrBlank()) {
                runReg("add", REG_PATH, "/v", "ProxyServer", "/t", "REG_SZ", "/d", backup!!.previousServer, "/f")
            } else if (hadBackup) {
                runCatching { runReg("delete", REG_PATH, "/v", "ProxyServer", "/f") }
            }
            if (!backup?.previousOverride.isNullOrBlank()) {
                runReg("add", REG_PATH, "/v", "ProxyOverride", "/t", "REG_SZ", "/d", backup!!.previousOverride, "/f")
            }
            notifyWinInetChanged()
            runCatching { backupFile.delete() }
            isApplied = false
            true
        }.getOrDefault(false)
        return res
    }

    private fun runReg(vararg args: String) {
        val cmd = listOf("reg.exe") + args
        val proc = ProcessBuilder(cmd).redirectErrorStream(true).start()
        val ok = proc.waitFor(4, TimeUnit.SECONDS) && proc.exitValue() == 0
        if (!ok) {
            val out = proc.inputStream.bufferedReader().readText()
            error("reg ${args.firstOrNull()} failed: $out")
        }
    }

    private fun queryRegDword(name: String): Int? = runCatching {
        val out = queryRegRaw(name) ?: return null
        val hex = Regex("""0x([0-9a-fA-F]+)""").find(out)?.groupValues?.get(1) ?: return null
        hex.toInt(16)
    }.getOrNull()

    private fun queryRegString(name: String): String? = runCatching {
        val out = queryRegRaw(name) ?: return null
        val line = out.lineSequence().firstOrNull { it.contains(name) } ?: return null
        val idx = line.indexOf("REG_SZ")
        if (idx < 0) null else line.substring(idx + 6).trim()
    }.getOrNull()

    private fun queryRegRaw(name: String): String? {
        val proc = ProcessBuilder("reg.exe", "query", REG_PATH, "/v", name)
            .redirectErrorStream(true)
            .start()
        val out = proc.inputStream.bufferedReader(Charsets.UTF_8).readText()
        proc.waitFor(3, TimeUnit.SECONDS)
        return if (proc.exitValue() == 0) out else null
    }

    private fun notifyWinInetChanged() {
        runCatching {
            val script = """
                Add-Type -TypeDefinition 'using System; using System.Runtime.InteropServices; public class PniInet { [DllImport("wininet.dll", SetLastError=true)] public static extern bool InternetSetOption(IntPtr hInternet, int dwOption, IntPtr lpBuffer, int dwBufferLength); }';
                [PniInet]::InternetSetOption([IntPtr]::Zero, 39, [IntPtr]::Zero, 0) | Out-Null;
                [PniInet]::InternetSetOption([IntPtr]::Zero, 37, [IntPtr]::Zero, 0) | Out-Null
            """.trimIndent()
            ProcessBuilder("powershell.exe", "-NoProfile", "-WindowStyle", "Hidden", "-Command", script)
                .start()
                .waitFor(5, TimeUnit.SECONDS)
        }
    }

    private companion object {
        const val REG_PATH = """HKCU\Software\Microsoft\Windows\CurrentVersion\Internet Settings"""
    }
}
