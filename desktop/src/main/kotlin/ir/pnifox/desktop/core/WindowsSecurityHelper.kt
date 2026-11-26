package ir.pnifox.desktop.core

import java.awt.Desktop
import java.io.File
import java.io.RandomAccessFile
import java.net.URI
import java.nio.channels.FileLock
import java.security.SecureRandom
import java.util.concurrent.TimeUnit

object WindowsSecurityHelper {

    private val random = SecureRandom()

    fun generateSessionToken(byteCount: Int = 24): String {
        val bytes = ByteArray(byteCount)
        random.nextBytes(bytes)
        return bytes.joinToString("") { "%02x".format(it) }
    }

    /**
     * Restricts NTFS ACLs on sensitive app data directories to the current Windows user only.
     */
    fun hardenDirectoryAcl(dir: File) {
        if (!dir.exists()) dir.mkdirs()
        val username = System.getenv("USERNAME")?.takeIf { it.isNotBlank() } ?: return
        val domain = System.getenv("USERDOMAIN")?.takeIf { it.isNotBlank() }
        val principal = if (domain != null) "$domain\\$username" else username
        runCatching {
            ProcessBuilder(
                "icacls.exe",
                dir.absolutePath,
                "/inheritance:r",
                "/grant:r",
                "$principal:(OI)(CI)F",
            )
                .redirectErrorStream(true)
                .start()
                .waitFor(4, TimeUnit.SECONDS)
        }
    }

    fun secureWipeAndDelete(file: File) {
        if (!file.exists() || !file.isFile) return
        runCatching {
            val len = file.length()
            if (len in 1..4_194_304L) {
                RandomAccessFile(file, "rw").use { raf ->
                    raf.seek(0L)
                    raf.write(ByteArray(len.toInt()))
                }
            }
        }
        runCatching { file.delete() }
    }

    fun cleanupTempConfigs(vararg dirs: File) {
        val tempNames = setOf(
            "win-tun-bridge.json",
            "win-gateway.json",
            "xray-server.json",
            "singbox-server.json",
            "xray-dnschanger.json",
            "singbox-dnstt-ssh.json",
            "xray-dnstt-vless.json",
            "config.json",
        )
        for (dir in dirs) {
            if (!dir.exists()) continue
            dir.listFiles()?.forEach { f ->
                if (f.isFile && (f.name in tempNames || (f.name.startsWith("ping-") && f.name.endsWith(".json")))) {
                    secureWipeAndDelete(f)
                }
            }
        }
    }

    fun acquireSingleInstanceLock(dataDir: File): FileLock? = runCatching {
        dataDir.mkdirs()
        val lockFile = File(dataDir, "pnifox.lock")
        val channel = RandomAccessFile(lockFile, "rw").channel
        channel.tryLock()
    }.getOrNull()

    private val secretPatterns = listOf(
        Regex("""("auth_key"\s*:\s*")([^"]+)(")"""),
        Regex("""("password"\s*:\s*")([^"]+)(")"""),
        Regex("""("private_key"\s*:\s*")([^"]+)(")"""),
        Regex("""("privateKey"\s*:\s*")([^"]+)(")"""),
        Regex("""(--access-(?:secret|token)\s+)(\S+)"""),
        Regex("""(Authorization:\s*Bearer\s+)(\S+)""", RegexOption.IGNORE_CASE),
        Regex("""((?:vless|trojan|hy2|hysteria2|ss)://)([^@\s]+)(@)""", RegexOption.IGNORE_CASE),
    )

    fun sanitizeLogLine(raw: String): String {
        var sanitized = raw
        secretPatterns.forEach { rx ->
            sanitized = rx.replace(sanitized) { m ->
                when (m.groupValues.size) {
                    4 -> "${m.groupValues[1]}***${m.groupValues[3]}"
                    3 -> "${m.groupValues[1]}***"
                    else -> "***"
                }
            }
        }
        return sanitized
    }

    fun openExternalUrl(rawUrl: String): Result<Unit> = runCatching {
        val trimmed = rawUrl.trim()
        val uri = URI(trimmed)
        val scheme = uri.scheme?.lowercase().orEmpty()
        require(scheme == "https" || scheme == "http" || scheme == "tg") {
            "Blocked unsupported external URL scheme: $scheme"
        }
        val opened = runCatching {
            if (Desktop.isDesktopSupported() && Desktop.getDesktop().isSupported(Desktop.Action.BROWSE)) {
                Desktop.getDesktop().browse(uri)
                true
            } else {
                false
            }
        }.getOrDefault(false)
        if (!opened) {
            // Safe fallback on Windows (especially under elevated Admin tokens where Desktop.browse may fail)
            ProcessBuilder("explorer.exe", trimmed).start()
        }
        Unit
    }
}
