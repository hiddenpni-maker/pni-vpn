package ir.pnifox.core.network

import ir.pnifox.core.model.ProxyProtocol
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi

class ShareLinkParserTest {

    @Test
    fun `parses vless reality and toggles soldier spoof`() {
        val link = "vless://9105dd95-eff8-4a04-9381-b531a8e2cdf1@example.com:443?type=ws&security=tls&sni=cdn.example.com#NodeOne"
        val parsed = ShareLinkParser.parseBatch(link)
        assertEquals(1, parsed.size)
        val p = parsed.first()
        assertEquals(ProxyProtocol.VLESS, p.protocol)
        assertEquals("example.com", p.address)
        assertEquals(443, p.port)
        assertEquals("NodeOne", p.name)

        val spoofed = ShareLinkParser.toggleSoldierSniSpoof(p)
        assertTrue(spoofed.spoofLocalhost40443)
    }

    @OptIn(ExperimentalEncodingApi::class)
    @Test
    fun `parses vmess and base64 batch subscription`() {
        val vmessJson = """{"v":"2","ps":"IR-Node","add":"1.2.3.4","port":"8443","id":"uuid-1","net":"ws","sni":"sub.host"}"""
        val vmessUri = "vmess://${Base64.encode(vmessJson.encodeToByteArray())}"
        val hy2Uri = "hy2://pass123@hy.example.com:443?sni=bing.com#HyNode"
        val subPayload = Base64.encode("$vmessUri\n$hy2Uri".encodeToByteArray())

        val results = ShareLinkParser.parseBatch(subPayload)
        assertEquals(2, results.size)
        assertEquals(ProxyProtocol.VMESS, results[0].protocol)
        assertEquals(ProxyProtocol.HYSTERIA2, results[1].protocol)
        assertEquals(8443, results[0].port)
    }

    @Test
    fun `parses full xray masque json config`() {
        val masqueJson = """
            {
              "remarks": "Irancell-H2-1 @otaqe_pni",
              "log": { "loglevel": "warning" },
              "inbounds": [
                { "tag": "socks", "listen": "127.0.0.1", "port": 10808, "protocol": "socks" }
              ],
              "outbounds": [
                {
                  "tag": "proxy",
                  "protocol": "masque",
                  "settings": { "address": "162.159.198.2", "port": 443 },
                  "streamSettings": {
                    "method": "masque",
                    "masqueSettings": {
                      "warp": {
                        "privateKey": "TEST_PRIV",
                        "publicKey": "TEST_PUB",
                        "address": ["172.16.0.2/32"]
                      }
                    },
                    "security": "tls",
                    "tlsSettings": { "serverName": "speed.cloudflare.com", "alpn": ["h2"] }
                  }
                },
                { "tag": "direct", "protocol": "freedom" }
              ]
            }
        """.trimIndent()

        val parsed = ShareLinkParser.parseBatch(masqueJson)
        assertEquals(1, parsed.size)
        val p = parsed.first()
        assertEquals("Irancell-H2-1 @otaqe_pni", p.name)
        assertEquals(ProxyProtocol.MASQUE, p.protocol)
        assertEquals("162.159.198.2", p.address)
        assertEquals(443, p.port)
        assertEquals("speed.cloudflare.com", p.sni)
        assertTrue(p.rawUri.startsWith("{"))
    }

    @Test
    fun `quick edit updates sni and raw masque json remarks and host cleanly`() {
        val masqueJson = """
            {"remarks":"OldName","outbounds":[{"tag":"proxy","protocol":"masque","settings":{"address":"162.159.198.2","port":443},"streamSettings":{"method":"masque","security":"tls","tlsSettings":{"serverName":"speed.cloudflare.com"}}}]}
        """.trimIndent()
        val orig = ShareLinkParser.parseBatch(masqueJson).first()
        val edited = ShareLinkParser.applyQuickEdit(
            existing = orig,
            name = "EditedName",
            address = "162.159.192.9",
            port = 8443,
            secret = "",
            sni = "custom.sni.example",
            transport = "masque",
            pathOrServiceName = "",
            tlsFingerprint = "chrome",
            realityPublicKey = "",
            realityShortId = "",
            fragmentEnabled = true,
            spoofLocalhost40443 = true,
            spoofFakeSni = "spoof.fake.example",
        )
        assertEquals("EditedName", edited.name)
        assertEquals("162.159.192.9", edited.address)
        assertEquals(8443, edited.port)
        assertEquals("custom.sni.example", edited.sni)
        assertEquals("spoof.fake.example", edited.spoofFakeSni)
        assertTrue(edited.rawUri.contains("\"custom.sni.example\""))
        assertTrue(edited.rawUri.contains("\"162.159.192.9\""))
    }

    @Test
    fun `mhr share codec roundtrips and version comparator orders semver cleanly`() {
        val mhr = ir.pnifox.core.model.MhrSettings(
            mode = ir.pnifox.core.model.MhrMode.APPS_SCRIPT,
            scriptIds = listOf("AKfycbxTestDeployment123"),
            authKey = "secret123",
            googleIp = "216.239.38.120",
            frontDomain = "scholar.google.com",
        )
        val uri = MhrShareCodec.encode(mhr)
        assertTrue(uri.startsWith(MhrShareCodec.PREFIX))
        val decoded = MhrShareCodec.decode(uri).getOrThrow()
        assertEquals(listOf("AKfycbxTestDeployment123"), decoded.scriptIds)
        assertEquals("secret123", decoded.authKey)
        assertEquals("scholar.google.com", decoded.frontDomain)

        assertTrue(VersionComparator.isRemoteNewer("2.0.0-phase1", "v2.0.0"))
        assertTrue(VersionComparator.isRemoteNewer("2.0.0", "v2.1.0"))
        assertTrue(!VersionComparator.isRemoteNewer("2.1.0", "v2.0.9"))
    }
}
