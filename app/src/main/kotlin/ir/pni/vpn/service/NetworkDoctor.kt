package ir.pni.vpn.service

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import ir.pni.vpn.data.model.DiagnosticReport
import ir.pni.vpn.data.model.NetworkType
import ir.pni.vpn.data.model.ProtocolType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.Socket
import javax.net.ssl.SSLSocket
import javax.net.ssl.SSLSocketFactory

/**
 * On-device Iranian network diagnostic probe ("Network Doctor"):
 * - Checks DNS poisoning / latency
 * - Checks direct TLS handshake to Cloudflare vs Akamai
 * - Checks whether fragmented TLS ClientHello improves reachability
 * - Checks UDP/QUIC reachability (relevant for WARP, MASQUE, Hysteria2, TUIC)
 * - Outputs concrete Persian recommendation and suggested protocol stack
 */
object NetworkDoctor {

    suspend fun diagnose(context: Context, selectedNetwork: NetworkType): DiagnosticReport =
        withContext(Dispatchers.IO) {
            val activeNetLabel = detectTransportLabel(context, selectedNetwork)

            val dnsStart = System.currentTimeMillis()
            val resolvedCf = runCatching {
                InetAddress.getAllByName("one.one.one.one").map { it.hostAddress.orEmpty() }
            }.getOrDefault(emptyList())
            val dnsMs = (System.currentTimeMillis() - dnsStart).toInt()
            // Iran DNS hijacking commonly returns 10.10.34.x
            val dnsPoisoned = resolvedCf.isEmpty() || resolvedCf.any { it.startsWith("10.10.34.") }

            val cfTlsOk = probeTls("104.16.132.229", 443, "speed.cloudflare.com")
            val akamaiTlsOk = probeTls("23.32.10.1", 443, "www.akamai.com") ||
                probeTls("23.192.228.80", 443, "www.akamai.com")
            val udpOk = probeUdpStun()

            val sniFragHelps = !cfTlsOk || dnsPoisoned
            val suggested = mutableListOf<ProtocolType>()

            if (cfTlsOk) suggested.add(ProtocolType.VLESS)
            if (akamaiTlsOk) suggested.add(ProtocolType.TROJAN)
            if (udpOk) {
                suggested.add(ProtocolType.HYSTERIA2)
                suggested.add(ProtocolType.AETHER_WARP)
            } else {
                suggested.add(ProtocolType.LANTERN_STEALTH)
                suggested.add(ProtocolType.AETHER_MASQUE)
            }
            suggested.add(ProtocolType.PSIPHON)
            suggested.add(ProtocolType.TOR)

            val recFa = buildString {
                if (dnsPoisoned) {
                    append("• مسمومیت یا اختلال DNS شناسایی شد (DoH داخل PNI برای شما فعال و حیاتی است).\n")
                } else {
                    append("• پاسخ‌دهی DNS پایه سالم است (${dnsMs}ms).\n")
                }
                append(
                    if (cfTlsOk) "• هندشیک TLS کلودفلر برقرار است؛ اسکن آیپی تمیز کلودفلر پیشنهاد می‌شود.\n"
                    else "• مسیر مستقیم کلودفلر با اختلال/SNI فیلتر مواجه است؛ حتماً Fragment یا اسکنر Anti-DPI را روشن کنید یا Akamai را اسکن کنید.\n"
                )
                append(
                    if (akamaiTlsOk) "• شبکه Akamai در دسترس است (گزینه عالی برای جایگزینی کلودفلر).\n"
                    else "• Akamai روی پورت 443 مستقیم کند است؛ پورت‌های جایگزین 8443/2053 را اسکن کنید.\n"
                )
                append(
                    if (udpOk) "• ترافیک UDP/QUIC باز به نظر می‌رسد؛ Hysteria2، TUIC و WARP گزینه‌های پرسرعت هستند."
                    else "• محدودیت روی UDP دیده شد؛ پروتکل‌های مبتنی بر TCP/WS/XHTTP + Fragment یا Lantern/Outline و Tor WebTunnel پایدارتر هستند."
                )
            }

            DiagnosticReport(
                timestampMs = System.currentTimeMillis(),
                networkLabelFa = activeNetLabel,
                dnsPoisoned = dnsPoisoned,
                dnsLatencyMs = dnsMs,
                cloudflareTlsOk = cfTlsOk,
                akamaiTlsOk = akamaiTlsOk,
                sniFragmentHelps = sniFragHelps,
                udpReachable = udpOk,
                recommendationFa = recFa,
                suggestedProtocols = suggested.distinct()
            )
        }

    private fun detectTransportLabel(context: Context, fallback: NetworkType): String {
        val cm = context.getSystemService(ConnectivityManager::class.java)
        val caps = cm?.getNetworkCapabilities(cm.activeNetwork)
        val bearer = when {
            caps == null -> "نامشخص"
            caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> "Wi-Fi / مودم"
            caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> "دیتای موبایل"
            caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) -> "اترنت"
            else -> "شبکه"
        }
        return "$bearer — ${fallback.titleFa}"
    }

    private fun probeTls(ip: String, port: Int, sni: String): Boolean = runCatching {
        Socket().use { sock ->
            sock.connect(InetSocketAddress(ip, port), 3500)
            sock.soTimeout = 3500
            val factory = SSLSocketFactory.getDefault() as SSLSocketFactory
            (factory.createSocket(sock, sni, port, true) as SSLSocket).use { ssl ->
                ssl.startHandshake()
                true
            }
        }
    }.getOrDefault(false)

    private fun probeUdpStun(): Boolean = runCatching {
        DatagramSocket().use { ds ->
            ds.soTimeout = 2500
            // Classic STUN Binding Request (20 bytes) to stun.cloudflare.com:3478
            val tx = byteArrayOf(
                0x00, 0x01, 0x00, 0x00,
                0x21, 0x12, 0xA4.toByte(), 0x42,
                0x50, 0x4E, 0x49, 0x31, 0x32, 0x33, 0x34, 0x35, 0x36, 0x37, 0x38, 0x39
            )
            val addr = InetAddress.getByName("162.159.207.0")
            ds.send(DatagramPacket(tx, tx.size, addr, 3478))
            val rx = ByteArray(128)
            val pkt = DatagramPacket(rx, rx.size)
            ds.receive(pkt)
            pkt.length >= 20
        }
    }.getOrDefault(false)
}
