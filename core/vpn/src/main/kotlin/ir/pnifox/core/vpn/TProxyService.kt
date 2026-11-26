package ir.pnifox.core.vpn

import android.content.Context
import android.os.ParcelFileDescriptor
import java.io.File

/**
 * JNI wrapper for `libhev-socks5-tunnel.so` compiled with `-DPKGNAME=ir/pnifox/core/vpn`.
 * Bridges Android VpnService's TUN file descriptor directly to local SOCKS5 (`127.0.0.1:10820`).
 */
class TProxyService(private val context: Context) {

    companion object {
        @JvmStatic
        private external fun TProxyStartService(configPath: String, fd: Int): Boolean

        @JvmStatic
        private external fun TProxyStopService(): Boolean

        @JvmStatic
        private external fun TProxyIsRunning(): Boolean

        @JvmStatic
        private external fun TProxyGetStats(): LongArray?

        private var loaded = false
        @Volatile var lastLoadError: String? = null
            private set

        @Synchronized
        fun ensureLoaded(): Boolean {
            if (loaded) return true
            loaded = runCatching {
                System.loadLibrary("hev-socks5-tunnel")
                lastLoadError = null
                true
            }.getOrElse { err ->
                lastLoadError = err.message ?: err::class.java.simpleName
                false
            }
            return loaded
        }
    }

    fun start(
        vpnInterface: ParcelFileDescriptor,
        mtu: Int,
        socksPort: Int = 10820,
        ipv6Enabled: Boolean = false,
        useMapDns: Boolean = true,
        mapDnsAddress: String = "10.233.233.2",
        enableUdp: Boolean = true,
    ): Result<Unit> = runCatching {
        check(ensureLoaded()) {
            "libhev-socks5-tunnel.so failed to load (${lastLoadError ?: "JNI error"})."
        }

        val yaml = buildString {
            appendLine("tunnel:")
            appendLine("  mtu: ${mtu.coerceIn(1200, 1500)}")
            appendLine("  ipv4: 10.233.233.1")
            if (ipv6Enabled) {
                appendLine("  ipv6: 'fd00:233::1'")
            }
            appendLine("socks5:")
            appendLine("  port: $socksPort")
            appendLine("  address: 127.0.0.1")
            appendLine("  udp: 'udp'")
            if (useMapDns) {
                appendLine("mapdns:")
                appendLine("  address: $mapDnsAddress")
                appendLine("  port: 53")
                appendLine("  network: 198.18.0.0")
                appendLine("  netmask: 255.254.0.0")
                appendLine("  cache-size: 10000")
            }
            appendLine("misc:")
            appendLine("  tcp-read-write-timeout: 300000")
            appendLine("  udp-read-write-timeout: 60000")
            appendLine("  log-level: warn")
        }
        val cfgFile = File(context.filesDir, "hev-socks5-tunnel.yaml").apply {
            writeText(yaml)
        }
        val started = TProxyStartService(cfgFile.absolutePath, vpnInterface.fd)
        check(started) { "TProxyStartService returned false for fd=${vpnInterface.fd}" }
    }

    fun isRunning(): Boolean =
        if (!loaded) false else runCatching { TProxyIsRunning() }.getOrDefault(false)

    fun stop() {
        if (!loaded) return
        runCatching { TProxyStopService() }
    }

    /** Returns [txPackets, txBytes, rxPackets, rxBytes] or null if unavailable. */
    fun getStats(): LongArray? =
        if (!loaded) null else runCatching { TProxyGetStats() }.getOrNull()
}
