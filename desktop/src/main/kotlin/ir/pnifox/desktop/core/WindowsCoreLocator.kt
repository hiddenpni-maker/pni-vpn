package ir.pnifox.desktop.core

import ir.pnifox.desktop.model.WindowsBinaryStatus
import java.io.File

class WindowsCoreLocator {

    fun resolveCoresDirectory(): File {
        val candidates = buildList {
            System.getProperty("compose.application.resources.dir")?.takeIf { it.isNotBlank() }?.let {
                add(File(it))
                add(File(it, "windows-x64"))
            }
            val userDir = File(System.getProperty("user.dir"))
            add(File(userDir, "cores/windows-x64"))
            add(File(userDir, "desktop/cores/windows-x64"))
            add(File(userDir, "app/resources"))
            add(File(userDir, "resources"))
        }
        return candidates.firstOrNull { File(it, "xray.exe").exists() || File(it, "aether.exe").exists() }
            ?: candidates.first()
    }

    fun binaryFile(fileName: String): File = File(resolveCoresDirectory(), fileName)

    fun assetsDir(): File = File(resolveCoresDirectory(), "assets")

    fun ensureGeoAssets(targetGeoDir: File): Boolean {
        targetGeoDir.mkdirs()
        val srcDir = assetsDir()
        val coreDir = resolveCoresDirectory()
        for (name in listOf("geoip.dat", "geosite.dat")) {
            val dest = File(targetGeoDir, name)
            if (dest.exists() && dest.length() > 100_000L) continue
            val src = listOf(File(srcDir, name), File(coreDir, name)).firstOrNull { it.exists() && it.length() > 100_000L }
            if (src != null) {
                runCatching { src.copyTo(dest, overwrite = true) }
            }
        }
        return File(targetGeoDir, "geoip.dat").exists() && File(targetGeoDir, "geosite.dat").exists()
    }

    fun readBundledResourceText(pathInResources: String): String {
        // Prefer bundled classpath resource first, fallback to cores/assets
        val stream = javaClass.classLoader.getResourceAsStream(pathInResources)
        if (stream != null) {
            return stream.bufferedReader(Charsets.UTF_8).use { it.readText() }
        }
        val fileName = pathInResources.substringAfterLast('/')
        val fallback = File(assetsDir(), fileName)
        return if (fallback.exists()) fallback.readText(Charsets.UTF_8) else ""
    }

    fun inspectAll(): List<WindowsBinaryStatus> {
        val dir = resolveCoresDirectory()
        val specs = listOf(
            "aether.exe" to "AETHER Core (MASQUE / WARP / WG-in-MASQUE)",
            "xray.exe" to "Xray-core (VLESS / Reality / VMess / Trojan / MASQUE JSON)",
            "sing-box.exe" to "Sing-box + Wintun (Hysteria2 / WireGuard / SSH / TUN)",
            "mhrv-rs.exe" to "MasterHttpRelay Engine (Apps Script / SNI Front)",
            "dnstt-client.exe" to "DNSTT / SlowDNS Client (UDP / DoH / DoT)",
            "psiphon-tunnel-core.exe" to "Psiphon Anti-Sanction Exit",
            "tor.exe" to "Tor Network Binary",
            "lyrebird.exe" to "Tor obfs4 Pluggable Transport",
            "wintun.dll" to "Wintun Windows Layer-3 TUN Driver",
        )
        return specs.map { (name, role) ->
            val f = File(dir, name)
            WindowsBinaryStatus(
                name = name,
                role = role,
                path = f.absolutePath,
                available = f.exists() && f.isFile && f.length() > 1024L,
                sizeBytes = if (f.exists()) f.length() else 0L,
            )
        }
    }
}
