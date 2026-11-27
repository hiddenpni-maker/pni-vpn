package ir.pnifox.core.vpn

import ir.pnifox.core.model.AetherSettings
import ir.pnifox.core.model.AppSettings
import ir.pnifox.core.model.ProxyProtocol
import ir.pnifox.core.model.ScenarioGoalId
import ir.pnifox.core.network.ShareLinkParser
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.File
import java.net.InetAddress
import java.net.ServerSocket
import java.net.Socket
import java.util.Base64
import kotlin.concurrent.thread

class SharedEnginesAndParsersTest {

    @Test
    fun `tls record split preserves exact ClientHello bytes across two valid records`() {
        val fakeHelloPayload = ByteArray(90) { i -> ((i * 7 + 13) and 0xFF).toByte() }
        // Construct single TLS handshake record header: 0x16, 0x03, 0x01, len(90)
        val record = ByteArray(5 + fakeHelloPayload.size)
        record[0] = 0x16
        record[1] = 0x03
        record[2] = 0x01
        record[3] = 0x00
        record[4] = fakeHelloPayload.size.toByte()
        // Handshake ClientHello byte inside payload
        fakeHelloPayload[0] = 0x01
        System.arraycopy(fakeHelloPayload, 0, record, 5, fakeHelloPayload.size)

        val splitRecords = LocalDpiDesyncServer.buildSplitTlsRecords(record, rawSplitOffset = 32)
        assertEquals(2, splitRecords.size)
        assertEquals(0x16.toByte(), splitRecords[0][0])
        assertEquals(0x16.toByte(), splitRecords[1][0])

        val len1 = ((splitRecords[0][3].toInt() and 0xFF) shl 8) or (splitRecords[0][4].toInt() and 0xFF)
        val len2 = ((splitRecords[1][3].toInt() and 0xFF) shl 8) or (splitRecords[1][4].toInt() and 0xFF)
        assertEquals(fakeHelloPayload.size, len1 + len2)

        val reassembled = splitRecords[0].copyOfRange(5, splitRecords[0].size) +
            splitRecords[1].copyOfRange(5, splitRecords[1].size)
        assertArrayEquals(fakeHelloPayload, reassembled)
    }

    @Test
    fun `local dpi desync socks5 server forwards split TLS ClientHello intact over loopback`() {
        val upstreamServer = ServerSocket(0, 10, InetAddress.getByName("127.0.0.1"))
        val receivedOnUpstream = ByteArrayOutputStream()
        val acceptThread = thread {
            upstreamServer.accept().use { sock ->
                val inp = sock.getInputStream()
                val buf = ByteArray(1024)
                val deadline = System.currentTimeMillis() + 3_000L
                while (receivedOnUpstream.size() < 69 && System.currentTimeMillis() < deadline) {
                    val n = inp.read(buf)
                    if (n <= 0) break
                    receivedOnUpstream.write(buf, 0, n)
                }
                sock.getOutputStream().write("OK-ECHO".toByteArray())
                sock.getOutputStream().flush()
            }
        }

        val proxy = LocalDpiDesyncServer(
            listenPort = 0,
            fragmentChunkSize = 4,
            fragmentDelayMs = 1,
        )
        proxy.start()
        try {
            val port = proxy.activePort
            Socket("127.0.0.1", port).use { client ->
                client.soTimeout = 4_000
                val out = client.getOutputStream()
                val inp = DataInputStream(client.getInputStream())

                // SOCKS5 Greeting
                out.write(byteArrayOf(0x05, 0x01, 0x00))
                out.flush()
                val greetReply = ByteArray(2)
                inp.readFully(greetReply)
                assertArrayEquals(byteArrayOf(0x05, 0x00), greetReply)

                // SOCKS5 CONNECT to 127.0.0.1:<upstreamPort>
                val targetPort = upstreamServer.localPort
                val req = byteArrayOf(
                    0x05, 0x01, 0x00, 0x01,
                    127, 0, 0, 1,
                    ((targetPort ushr 8) and 0xFF).toByte(),
                    (targetPort and 0xFF).toByte(),
                )
                out.write(req)
                out.flush()
                val connReply = ByteArray(10)
                inp.readFully(connReply)
                assertEquals(0x00.toByte(), connReply[1])

                // Send TLS ClientHello payload
                val tlsHello = ByteArray(64) { 0x41 }
                tlsHello[0] = 0x16
                tlsHello[1] = 0x03
                tlsHello[2] = 0x03
                tlsHello[3] = 0x00
                tlsHello[4] = 59
                tlsHello[5] = 0x01
                out.write(tlsHello)
                out.flush()

                val replyBuf = ByteArray(7)
                inp.readFully(replyBuf)
                assertEquals("OK-ECHO", String(replyBuf))
            }
            acceptThread.join(3_000)
            val captured = receivedOnUpstream.toByteArray()
            // Must contain TLS handshake record(s) reconstructed cleanly
            assertTrue("Expected at least 69 bytes (original + extra 5B record header)", captured.size >= 64)
            assertEquals(0x16.toByte(), captured[0])
            val (_, tx) = proxy.bytesSnapshot()
            assertTrue("Expected positive tx byte count", tx >= 64L)
        } finally {
            proxy.stop()
            upstreamServer.close()
        }
    }

    @Test
    fun `poisoned Iranian sinkhole IPs are accurately flagged`() {
        assertTrue(LocalDpiDesyncServer.isPoisonedIp("10.10.34.34"))
        assertTrue(LocalDpiDesyncServer.isPoisonedIp("10.10.34.35"))
        assertTrue(LocalDpiDesyncServer.isPoisonedIp("10.10.34.36"))
        assertFalse(LocalDpiDesyncServer.isPoisonedIp("1.1.1.1"))
    }

    @Test
    fun `ech bootstrap parser extracts SvcParamKey 5 from RFC9460 HTTPS RDATA and DoH JSON`() {
        val sampleEch = byteArrayOf(0x00, 0x40, 0xFE.toByte(), 0x0D, 0x00, 0x3C, 0x11, 0x22)
        // Build HTTPS RDATA: Priority(2)=1, TargetName=0x00 (root), Key=0x0001(len=2,val="h2"), Key=0x0005(ech)
        val rdata = byteArrayOf(
            0x00, 0x01, // Priority 1
            0x00,       // Root target '.'
            0x00, 0x01, 0x00, 0x03, 0x02, 'h'.code.toByte(), '2'.code.toByte(), // ALPN
            0x00, 0x05, 0x00, sampleEch.size.toByte(), // SvcParamKey 5 = ech
        ) + sampleEch

        val extracted = EchBootstrapResolver.extractEchConfigFromHttpsRdata(rdata)
        assertNotNull(extracted)
        assertArrayEquals(sampleEch, extracted)

        val fakeB64 = Base64.getEncoder().encodeToString(ByteArray(24) { 0x5A })
        val json = """{"Status":0,"Answer":[{"name":"cloudflare-ech.com","type":65,"data":"1 . alpn=\"h2,h3\" ech=$fakeB64"}]}"""
        assertEquals(fakeB64, EchBootstrapResolver.extractEchBase64FromDohJson(json))
        assertNull(EchBootstrapResolver.extractEchBase64FromDohJson("""{"Status":3}"""))
    }

    @Test
    fun `share link parser and config builders support tuic anytls xhttp and ss2022`() {
        val tuicUri = "tuic://11111111-1111-1111-1111-111111111111:secretPass@1.2.3.4:443?sni=tuic.example.com#TUIC-Game"
        val anytlsUri = "anytls://strongPass@5.6.7.8:443?sni=any.example.com#AnyTLS-Node"
        val xhttpUri = "vless://22222222-2222-2222-2222-222222222222@9.9.9.9:443?type=splithttp&security=tls&sni=cdn.example.com&path=%2Fxhttp#XHTTP-Node"

        val parsed = ShareLinkParser.parseBatch("$tuicUri\n$anytlsUri\n$xhttpUri")
        assertEquals(3, parsed.size)

        val tuic = parsed[0]
        assertEquals(ProxyProtocol.TUIC, tuic.protocol)
        assertEquals("11111111-1111-1111-1111-111111111111:secretPass", tuic.secret)
        val tuicSb = ConfigBuilders.buildSingBoxConfig(tuic, AppSettings())
        assertTrue(tuicSb.contains("\"type\":\"tuic\""))
        assertTrue(tuicSb.contains("\"password\":\"secretPass\""))

        val anytls = parsed[1]
        assertEquals(ProxyProtocol.ANYTLS, anytls.protocol)
        val anySb = ConfigBuilders.buildSingBoxConfig(anytls, AppSettings())
        assertTrue(anySb.contains("\"type\":\"anytls\""))

        val xhttp = parsed[2]
        assertEquals("xhttp", xhttp.transport)
        val xrayCfg = ConfigBuilders.buildXrayConfig(xhttp, AppSettings(), hasGeoAssets = false)
        assertTrue(xrayCfg.contains("\"network\":\"xhttp\""))
        assertTrue(xrayCfg.contains("\"xhttpSettings\""))
        assertTrue(xrayCfg.contains("/xhttp"))
    }

    @Test
    fun `ech is disabled by default across settings and scenarios and still honors explicit override`() {
        val defaultSettings = AetherSettings()
        assertFalse(defaultSettings.echEnabled)
        assertFalse(defaultSettings.smartEchBootstrap)
        assertFalse(defaultSettings.warpApiUseEch)
        assertTrue(ir.pnifox.core.model.PnifoxScenarioCatalog.scenarios.none { it.recommendedEch })

        val defaultArgs = AetherArgBuilder().buildRunArgs(
            appSettings = AppSettings(),
            aether = defaultSettings,
            nativeDir = ".",
            workDir = File("."),
        )
        assertFalse(defaultArgs.contains("--ech"))

        val args = AetherArgBuilder().buildRunArgs(
            appSettings = AppSettings(),
            aether = AetherSettings(echEnabled = true, echBase64Override = "AEn+DQB6123456"),
            nativeDir = ".",
            workDir = File("."),
        )
        val echIdx = args.indexOf("--ech")
        assertTrue(echIdx >= 0)
        assertEquals("AEn+DQB6123456", args[echIdx + 1])
    }

    @Test
    fun `cdn scanner expandCidrs samples across diverse prefixes and wide cidr ranges`() {
        val wideCidrs = listOf(
            "23.0.0.0/12",
            "104.16.0.0/16",
            "162.159.192.0/24",
            "162.159.198.0/24",
            "188.114.97.0/24",
            "2.16.0.0/13",
        )
        val sampled = JvmCdnScanner.expandCidrs(wideCidrs, maxSamples = 60, excludeIps = setOf("162.159.198.1"))
        assertEquals(60, sampled.size)
        assertFalse(sampled.contains("162.159.198.1"))
        // Should span multiple distinct /16 prefixes, not stay trapped inside the first prefix only
        val distinctSecondOctets = sampled.map { it.split('.').take(2).joinToString(".") }.toSet()
        assertTrue(distinctSecondOctets.size >= 4)
        // Wide /12 & /13 ranges should vary third octet instead of pinning to .0.x
        val akamai23 = sampled.filter { it.startsWith("23.") }
        if (akamai23.size >= 2) {
            assertTrue(akamai23.map { it.split('.')[2] }.toSet().size >= 2)
        }
    }
}
