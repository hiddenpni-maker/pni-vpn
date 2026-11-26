package ir.pnifox.desktop

import ir.pnifox.core.vpn.JvmCdnScanner
import ir.pnifox.core.vpn.SniSpoofServer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SniSpoofAndScannerTest {

    @Test
    fun `sni spoof rewrites clienthello sni extension and updates all tls length headers`() {
        // Minimal valid TLS 1.2 ClientHello packet with SNI extension for "old.example.com"
        val oldHost = "old.example.com".toByteArray(Charsets.US_ASCII)
        val extPayload = byteArrayOf(
            0x00, 0x00, // extension type: server_name (0)
            0x00, (5 + oldHost.size).toByte(), // extension len
            0x00, (3 + oldHost.size).toByte(), // list len
            0x00, // host_name type
            0x00, oldHost.size.toByte(), // host_name len
        ) + oldHost
        val hsBody = ByteArray(2 + 32 + 1 + 2 + 2 + 1 + 1 + 2 + extPayload.size)
        hsBody[0] = 0x03; hsBody[1] = 0x03 // TLS 1.2
        val csPos = 35
        hsBody[csPos] = 0x00; hsBody[csPos + 1] = 0x02 // cipher suites len = 2
        val compPos = csPos + 4
        hsBody[compPos] = 0x01 // compression methods len = 1
        val extLenPos = compPos + 2
        hsBody[extLenPos] = ((extPayload.size ushr 8) and 0xFF).toByte()
        hsBody[extLenPos + 1] = (extPayload.size and 0xFF).toByte()
        System.arraycopy(extPayload, 0, hsBody, extLenPos + 2, extPayload.size)

        val hsLen = hsBody.size
        val recLen = 4 + hsLen
        val pkt = byteArrayOf(
            0x16, 0x03, 0x01, ((recLen ushr 8) and 0xFF).toByte(), (recLen and 0xFF).toByte(),
            0x01, ((hsLen ushr 16) and 0xFF).toByte(), ((hsLen ushr 8) and 0xFF).toByte(), (hsLen and 0xFF).toByte(),
        ) + hsBody

        val rewritten = SniSpoofServer.rewriteClientHelloSni(pkt, "new-front.google.com")
        val asAscii = rewritten.toString(Charsets.US_ASCII)
        assertTrue(asAscii.contains("new-front.google.com"))
        assertTrue(!asAscii.contains("old.example.com"))
        val newRecLen = ((rewritten[3].toInt() and 0xFF) shl 8) or (rewritten[4].toInt() and 0xFF)
        assertEquals(rewritten.size - 5, newRecLen)
    }

    @Test
    fun `jvm cdn scanner expands cidrs cleanly within sample cap`() {
        val ips = JvmCdnScanner.expandCidrs(
            listOf("162.159.198.0/24", "188.114.97.0/24", "1.1.1.1"),
            maxSamples = 12,
        )
        assertTrue(ips.isNotEmpty())
        assertTrue(ips.size <= 12)
        assertTrue(ips.any { it.startsWith("162.159.198.") })
        assertTrue(ips.contains("1.1.1.1"))
    }
}
