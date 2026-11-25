package ir.pni.vpn.service

import android.content.Context
import android.os.ParcelFileDescriptor
import java.io.File

/**
 * JNI wrapper for `libhev-socks5-tunnel.so` compiled with `-DPKGNAME=ir/pni/vpn/service`.
 * Bridges Android VpnService's TUN file descriptor directly to the local SOCKS5 proxy.
 */
class TProxyService(private val context: Context) {

    companion object {
        @JvmStatic
        private external fun TProxyStartService(configPath: String, fd: Int)

        @JvmStatic
        private external fun TProxyStopService()

        @JvmStatic
        private external fun TProxyGetStats(): LongArray?

        private var loaded = false

        @Synchronized
        fun ensureLoaded(): Boolean {
            if (loaded) return true
            loaded = runCatching {
                System.loadLibrary("hev-socks5-tunnel")
                true
            }.getOrDefault(false)
            return loaded
        }
    }

    fun start(
        vpnInterface: ParcelFileDescriptor,
        mtu: Int,
        socksPort: Int,
        ipv6Enabled: Boolean
    ): Result<Unit> = runCatching {
        check(ensureLoaded()) { "کتابخانه بومی libhev-socks5-tunnel.so یافت نشد" }

        val yaml = buildString {
            appendLine("tunnel:")
            appendLine("  mtu: $mtu")
            appendLine("  ipv4: 10.233.233.1")
            if (ipv6Enabled) {
                appendLine("  ipv6: 'fd00:233::1'")
            }
            appendLine("socks5:")
            appendLine("  port: $socksPort")
            appendLine("  address: 127.0.0.1")
            appendLine("  udp: 'udp'")
            appendLine("misc:")
            appendLine("  tcp-read-write-timeout: 300000")
            appendLine("  udp-read-write-timeout: 60000")
            appendLine("  log-level: warn")
        }
        val cfgFile = File(context.filesDir, "hev-socks5-tunnel.yaml").apply {
            writeText(yaml)
        }
        TProxyStartService(cfgFile.absolutePath, vpnInterface.fd)
    }

    fun stop() {
        if (!loaded) return
        runCatching { TProxyStopService() }
    }

    /** Returns [txPackets, txBytes, rxPackets, rxBytes] or null if unavailable. */
    fun getStats(): LongArray? =
        if (!loaded) null else runCatching { TProxyGetStats() }.getOrNull()
}
