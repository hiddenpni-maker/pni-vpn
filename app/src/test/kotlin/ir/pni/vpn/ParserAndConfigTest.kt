package ir.pni.vpn

import ir.pni.vpn.core.SingBoxConfigBuilder
import ir.pni.vpn.core.XrayConfigBuilder
import ir.pni.vpn.data.model.AppSettings
import ir.pni.vpn.data.model.NetworkType
import ir.pni.vpn.data.model.ProtocolType
import ir.pni.vpn.data.preset.IranNetworkPresets
import ir.pni.vpn.parser.ConfigEncryptor
import ir.pni.vpn.parser.ShareLinkParser
import ir.pni.vpn.parser.SpoofConverter
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ParserAndConfigTest {

    @Test
    fun parseVlessWithPattNExtensionsAndBuildXrayJson() {
        val uri = "vless://11111111-2222-3333-4444-555555555555@104.16.1.1:443?type=ws&security=tls&sni=edge.example.com&host=edge.example.com&path=%2Fws&fp=unsafe&cs=TLS_AES_128_GCM_SHA256#PNI-Test"
        val profile = ShareLinkParser.parseSingle(uri)
        assertNotNull(profile)
        assertEquals(ProtocolType.VLESS, profile!!.protocol)
        assertEquals("unsafe", profile.fingerprint)
        assertTrue(profile.unsafeTls)
        assertEquals("TLS_AES_128_GCM_SHA256", profile.cipherSuites)

        val xrayJson = XrayConfigBuilder.build(
            profile = profile.copy(fragmentEnabled = true),
            settings = AppSettings()
        )
        assertTrue(xrayJson.contains("\"cipherSuites\""))
        assertTrue(xrayJson.contains("\"fragment\""))
        assertTrue(xrayJson.contains("geosite:ir"))
    }

    @Test
    fun parseHysteria2AndBuildSingBoxJson() {
        val uri = "hy2://secretpass@hy.example.com:8443?sni=hy.example.com&obfs=salamander&obfs-password=abc#HyTest"
        val profile = ShareLinkParser.parseSingle(uri)
        assertNotNull(profile)
        assertEquals(ProtocolType.HYSTERIA2, profile!!.protocol)
        val sbJson = SingBoxConfigBuilder.build(profile, AppSettings())
        assertTrue(sbJson.contains("\"hysteria2\""))
        assertTrue(sbJson.contains("\"salamander\""))
    }

    @Test
    fun parseAndSerializeDnsTunnelAmStyle() {
        val uri = "dnstt://0123456789abcdef0123456789abcdef@ns1.example.com?mode=udp&resolver=8.8.8.8%3A53&inner=ssh&user=pniuser&pass=pnipass#SlowDNS"
        val profile = ShareLinkParser.parseSingle(uri)
        assertNotNull(profile)
        assertEquals(ProtocolType.DNS_TUNNEL, profile!!.protocol)
        assertEquals("udp", profile.dnsTransport)
        assertEquals("8.8.8.8:53", profile.dnsResolver)
        assertEquals("ns1.example.com", profile.dnsDomain)
        assertEquals("ssh", profile.dnsInnerMode)
        assertEquals("pniuser", profile.dnsInnerUser)
        val roundTrip = ShareLinkParser.toShareLink(profile)
        assertTrue(roundTrip.startsWith("dnstt://"))
        assertTrue(roundTrip.contains("ns1.example.com"))
    }

    @Test
    fun encryptDecryptAndSpoofConverterRoundTrip() {
        val uri = "trojan://mypass@cdn.example.com:443?type=ws&security=tls&sni=cdn.example.com#TrojanTest"
        val encrypted = ConfigEncryptor.encrypt(uri, "test-pass")
        assertTrue(encrypted.startsWith("pni://enc:"))
        val decrypted = ConfigEncryptor.decrypt(encrypted, "test-pass")
        assertEquals(uri, decrypted)

        val profile = ShareLinkParser.parseSingle(uri)!!
        val converted = SpoofConverter.convertToLocalSpoof(profile, localPort = 40443, fakeSni = "www.speedtest.net")
        assertNotNull(converted)
        assertEquals("127.0.0.1", converted!!.profile.server)
        assertEquals(40443, converted.profile.port)
        assertEquals("cdn.example.com:443", converted.originalTarget)
    }

    @Test
    fun allIranNetworkPresetsPresent() {
        NetworkType.entries.forEach { net ->
            val preset = IranNetworkPresets.get(net)
            assertTrue(preset.recommendedProtocols.isNotEmpty())
            assertTrue(preset.adviceFa.isNotBlank())
        }
    }
}
