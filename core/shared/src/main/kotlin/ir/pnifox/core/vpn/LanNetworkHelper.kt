package ir.pnifox.core.vpn

import java.net.Inet4Address
import java.net.NetworkInterface

object LanNetworkHelper {

    /**
     * Finds non-loopback, non-TUN local IPv4 addresses (hotspot `ap0`/`wlan*` or Wi-Fi LAN),
     * ordering hotspot AP subnets (e.g. 192.168.43.x / 192.168.137.x) first.
     */
    fun detectLocalIpv4Addresses(): List<String> = runCatching {
        val candidates = mutableListOf<Pair<Int, String>>()
        val interfaces = NetworkInterface.getNetworkInterfaces() ?: return emptyList()
        for (iface in interfaces) {
            if (!iface.isUp || iface.isLoopback) continue
            val ifaceName = iface.name.lowercase()
            if (ifaceName.startsWith("tun") || ifaceName.startsWith("ppp") || ifaceName.startsWith("rmnet")) continue
            for (addr in iface.inetAddresses) {
                if (addr !is Inet4Address || addr.isLoopbackAddress) continue
                val ip = addr.hostAddress ?: continue
                if (ip.startsWith("10.233.233.")) continue
                val priority = when {
                    ifaceName.contains("ap") || ifaceName.contains("hotspot") || ifaceName.contains("tether") -> 0
                    ip.startsWith("192.168.43.") || ip.startsWith("192.168.137.") -> 0
                    ifaceName.startsWith("wlan") || ifaceName.startsWith("eth") -> 1
                    else -> 2
                }
                candidates += priority to ip
            }
        }
        candidates.sortedBy { it.first }.map { it.second }.distinct()
    }.getOrDefault(emptyList())
}
