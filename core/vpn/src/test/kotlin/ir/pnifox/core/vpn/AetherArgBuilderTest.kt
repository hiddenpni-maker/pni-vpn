package ir.pnifox.core.vpn

import ir.pnifox.core.model.AetherProtocol
import ir.pnifox.core.model.AetherScanMode
import ir.pnifox.core.model.AetherSettings
import ir.pnifox.core.model.AppSettings
import ir.pnifox.core.model.DnsInnerProtocol
import ir.pnifox.core.model.DnsTunnelMode
import ir.pnifox.core.model.DnsTunnelSettings
import ir.pnifox.core.model.ExitNode
import ir.pnifox.core.model.MasqueTransport
import ir.pnifox.core.model.OneTapPreset
import ir.pnifox.core.model.TransportMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class AetherArgBuilderTest {

    private val builder = AetherArgBuilder()

    @Test
    fun `default warp fast masque h2 binds to 10820 and passes --masque --h2`() {
        val args = builder.buildRunArgs(
            appSettings = AppSettings(activePreset = OneTapPreset.WARP_FAST_MASQUE_H2),
            aether = AetherSettings(scanForEndpoint = true),
            nativeDir = "/data/app/lib/arm64",
            workDir = File("/data/local/aether"),
        )
        assertTrue(args.contains("127.0.0.1:10820"))
        assertTrue(args.contains("--masque"))
        assertTrue(args.contains("--h2"))
        assertTrue(args.contains("--scan"))
    }

    @Test
    fun `warp to psiphon routes psiphon-bind to 10820 with fragment and ech`() {
        val preview = builder.formatCommandPreview(
            appSettings = AppSettings(
                activePreset = OneTapPreset.WARP_TO_PSIPHON,
                transportMode = TransportMode.FRAGMENT,
                exitNode = ExitNode.PSIPHON,
            ),
            aether = AetherSettings(
                protocol = AetherProtocol.MASQUE,
                masqueTransport = MasqueTransport.HTTP2,
                fragmentEnabled = true,
                fragmentSizeBytes = "8-16",
                fragmentDelayMs = "2-10",
                echEnabled = true,
                scanMode = AetherScanMode.IRONCLAD,
                scanForEndpoint = true,
                psiphonEnabled = true,
            ),
        )
        assertTrue(preview.startsWith("libaether.so "))
        assertTrue(preview.contains("--psiphon-bind 127.0.0.1:10820"))
        assertTrue(preview.contains("--fragment-size 8-16"))
        assertTrue(preview.contains("--ech auto"))
        assertTrue(preview.contains("--scan ironclad"))
    }

    @Test
    fun `dnstt arg builder generates real dnstt-client args`() {
        val args = builder.buildDnsttArgs(
            DnsTunnelSettings(
                mode = DnsTunnelMode.DOH,
                resolverAddress = "https://cloudflare-dns.com/dns-query",
                nameserver = "t.example.com",
                publicKeyHex = "aabbcc",
            ),
            localSocksPort = 10820,
        )
        assertTrue(args.contains("-doh"))
        assertTrue(args.contains("-pubkey"))
        assertTrue(args.contains("t.example.com"))
        assertTrue(args.contains("127.0.0.1:10820"))
    }

    @Test
    fun `pinned masque endpoint passes --peer without scan and isolates fragment from server mode`() {
        val args = builder.buildRunArgs(
            appSettings = AppSettings(
                activePreset = OneTapPreset.WARP_FAST_MASQUE_H2,
                transportMode = TransportMode.FRAGMENT,
            ),
            aether = AetherSettings(
                scanForEndpoint = false,
                endpointIp = "162.159.198.34",
                endpointPort = 443,
                fragmentEnabled = false,
            ),
            nativeDir = "/data/app/lib/arm64",
            workDir = File("/data/local/aether"),
        )
        assertTrue(args.contains("--peer"))
        assertTrue(args.contains("162.159.198.34:443"))
        assertTrue(!args.contains("--scan"))
        assertTrue(!args.contains("--fragment"))
    }

    @Test
    fun `full masque xray json rewrites socks inbound port to 10820 and preserves outbounds`() {
        val rawJson = """
            {"remarks":"Irancell-H2-1 @otaqe_pni","inbounds":[{"tag":"socks","listen":"127.0.0.1","port":10808,"protocol":"socks"}],"outbounds":[{"tag":"proxy","protocol":"masque","settings":{"address":"162.159.198.2","port":443},"streamSettings":{"method":"masque","masqueSettings":{"warp":{"privateKey":"PRIV","publicKey":"PUB","address":["172.16.0.2/32"]}},"security":"tls","tlsSettings":{"serverName":"speed.cloudflare.com","alpn":["h2"]}}}],"routing":{"domainStrategy":"IPIfNonMatch","rules":[]}}
        """.trimIndent()
        val profile = ir.pnifox.core.model.ProxyProfile(
            id = "m1",
            name = "Irancell-H2-1 @otaqe_pni",
            protocol = ir.pnifox.core.model.ProxyProtocol.MASQUE,
            address = "162.159.198.2",
            port = 443,
            sni = "speed.cloudflare.com",
            rawUri = rawJson,
        )
        val built = ConfigBuilders.buildXrayConfig(profile, AppSettings(), socksPort = 10820, errorLogPath = "/data/local/xray-error.log", hasGeoAssets = true)
        assertTrue(built.contains("\"port\":10820"))
        assertTrue(built.contains("\"protocol\":\"masque\""))
        assertTrue(built.contains("\"masqueSettings\""))
        assertTrue(built.contains("\"IPIfNonMatch\""))
        assertTrue(!built.contains("\"remarks\""))
        assertTrue(!built.contains("10808"))
    }

    @Test
    fun `rulesets emit aether routes file and xray iran plus adblock rules cleanly`() {
        val appSettings = AppSettings(
            bypassIranEnabled = true,
            bypassPrivateLanEnabled = true,
            blockAdsEnabled = true,
            customDirectRules = "mybank.ir",
            customBlockRules = "ads.badsite.example",
        )
        val routesText = builder.buildRoutesFileContent(appSettings).orEmpty()
        assertTrue(routesText.contains("[block]"))
        assertTrue(routesText.contains("[direct]"))
        assertTrue(routesText.contains("ads.badsite.example"))
        assertTrue(routesText.contains("mybank.ir"))
        assertTrue(routesText.contains("private"))

        val profile = ir.pnifox.core.model.ProxyProfile(
            id = "v1",
            name = "Test",
            protocol = ir.pnifox.core.model.ProxyProtocol.VLESS,
            address = "1.2.3.4",
            port = 443,
            secret = "uuid-1",
        )
        val xrayGeo = ConfigBuilders.buildXrayConfig(profile, appSettings, hasGeoAssets = true)
        assertTrue(xrayGeo.contains("geosite:category-ads-all"))
        assertTrue(xrayGeo.contains("geosite:category-ir"))
        assertTrue(xrayGeo.contains("geoip:ir"))

        val xrayNoGeo = ConfigBuilders.buildXrayConfig(profile, appSettings, hasGeoAssets = false)
        assertTrue(!xrayNoGeo.contains("geoip:"))
        assertTrue(!xrayNoGeo.contains("geosite:"))
        assertTrue(xrayNoGeo.contains("mybank.ir"))
    }

    @Test
    fun `dnstt ssh singbox config and dns changer config build valid schema`() {
        val dns = DnsTunnelSettings(
            mode = DnsTunnelMode.DOH,
            resolverAddress = "https://dns.adguard-dns.com/dns-query",
            innerProtocol = DnsInnerProtocol.SSH,
            sshUsername = "optnl-user",
            sshPassword = "secret",
        )
        val sbJson = ConfigBuilders.buildSingBoxDnsttSshConfig(dns, AppSettings(blockAdsEnabled = true), innerDnsttPort = 10823, socksPort = 10820)
        assertTrue(sbJson.contains("\"type\":\"ssh\""))
        assertTrue(sbJson.contains("\"server_port\":10823"))
        assertTrue(sbJson.contains("\"user\":\"optnl-user\""))
        assertTrue(sbJson.contains("\"action\":\"reject\""))

        val changerJson = ConfigBuilders.buildDnsChangerXrayConfig(dns, AppSettings(blockAdsEnabled = true), hasGeoAssets = true)
        assertTrue(changerJson.contains("https://dns.adguard-dns.com/dns-query"))
        assertTrue(changerJson.contains("geosite:category-ads-all"))
        assertTrue(changerJson.contains("\"tag\":\"direct\""))

        val cleanIp = builder.extractAetherDnsServer(dns)
        assertTrue(cleanIp == "94.140.14.14")
    }

    @Test
    fun `allowLan exposes http proxy 10821 on 0_0_0_0 across xray singbox aether and mhr`() {
        val lanSettings = AppSettings(allowLan = true, httpProxyPort = 10821)
        val profile = ir.pnifox.core.model.ProxyProfile(
            id = "v1",
            name = "Test",
            protocol = ir.pnifox.core.model.ProxyProtocol.VLESS,
            address = "1.2.3.4",
            port = 443,
            secret = "uuid-1",
        )
        val xrayJson = ConfigBuilders.buildXrayConfig(profile, lanSettings, hasGeoAssets = false)
        assertTrue(xrayJson.contains("\"protocol\":\"http\""))
        assertTrue(xrayJson.contains("\"listen\":\"0.0.0.0\""))
        assertTrue(xrayJson.contains("\"port\":10821"))

        val aetherArgs = builder.buildRunArgs(
            appSettings = lanSettings,
            aether = AetherSettings(localSocksPort = 10820),
            nativeDir = "/data/app/lib/arm64",
            workDir = File("/data/local/aether"),
        )
        assertTrue(aetherArgs.contains("0.0.0.0:10820"))

        val mhrJson = MhrConfigBuilder.buildConfigJson(
            appSettings = lanSettings,
            aetherSettings = AetherSettings(localSocksPort = 10820),
            mhr = ir.pnifox.core.model.MhrSettings(
                mode = ir.pnifox.core.model.MhrMode.APPS_SCRIPT,
                scriptIds = listOf("https://script.google.com/macros/s/AKfycbxSample/exec"),
                authKey = "k1",
                googleIp = "216.239.38.120",
                listenPort = 8085,
                socks5Port = 8086,
            ),
        )
        assertTrue(mhrJson.contains("\"mode\":\"apps_script\""))
        assertTrue(mhrJson.contains("\"AKfycbxSample\""))
        assertTrue(mhrJson.contains("\"listen_host\":\"0.0.0.0\""))
        assertTrue(mhrJson.contains("\"listen_port\":8085"))
        assertTrue(mhrJson.contains("\"socks5_port\":8086"))

        val mhrAndroidJson = MhrConfigBuilder.buildConfigJson(
            appSettings = lanSettings,
            aetherSettings = AetherSettings(localSocksPort = 10820),
            mhr = ir.pnifox.core.model.MhrSettings(scriptIds = listOf("AKfycbxSample")),
            overrideSocks5Port = 10820,
            overrideListenPort = 10821,
        )
        assertTrue(mhrAndroidJson.contains("\"listen_port\":10821"))
        assertTrue(mhrAndroidJson.contains("\"socks5_port\":10820"))

        val psiphonHttpArgs = builder.buildRunArgs(
            appSettings = AppSettings(activePreset = OneTapPreset.WARP_TO_PSIPHON, exitNode = ExitNode.PSIPHON, httpProxyPort = 10821),
            aether = AetherSettings(psiphonEnabled = true, localSocksPort = 10820),
            nativeDir = "/data/app/lib/arm64",
            workDir = File("/data/local/aether"),
            exposeLocalHttpProxy = true,
        )
        assertTrue(psiphonHttpArgs.contains("--psiphon-http"))
        assertTrue(!psiphonHttpArgs.contains("--http-proxy"))

        val sniProbe = MhrConfigBuilder.parseTestSniJson("www.google.com", """{"ok":true,"latencyMs":87}""")
        assertTrue(sniProbe.ok && sniProbe.latencyMs == 87L)

        val stats = MhrConfigBuilder.parseStatsJson("""{"today_calls":120,"today_bytes":4096,"today_key":"2026-11-26","today_reset_secs":3600}""")
        assertTrue(stats?.todayCalls == 120L && stats.todayBytes == 4096L)
    }

    @Test
    fun `shir o khorshid akamai cdn fronting emits standalone cdn ips sni and psiphon-akamai set`() {
        val args = builder.buildRunArgs(
            appSettings = AppSettings(
                activePreset = OneTapPreset.PSIPHON_AKAMAI_CDN,
                exitNode = ir.pnifox.core.model.ExitNode.PSIPHON,
            ),
            aether = AetherSettings(
                psiphonEnabled = true,
                psiphonMode = ir.pnifox.core.model.PsiphonChainMode.STANDALONE_CDN,
                psiphonCdnFrontingIps = "23.1.2.3\n104.64.5.6, 23.1.2.3",
                psiphonCdnSniHostnames = "edge1.example.com\nedge2.example.com",
                psiphonCdnSets = "",
            ),
            nativeDir = "/data/app/lib/arm64",
            workDir = File("/data/local/aether"),
        )
        assertTrue(args.contains("--psiphon-only"))
        assertTrue(args.contains("--psiphon-mode"))
        assertEquals("cdn", args[args.indexOf("--psiphon-mode") + 1])
        assertEquals("23.1.2.3,104.64.5.6", args[args.indexOf("--psiphon-cdn-ips") + 1])
        assertEquals("edge1.example.com,edge2.example.com", args[args.indexOf("--psiphon-cdn-sni") + 1])
        // When CDN sets is blank and custom IPs were supplied, no forced psiphon-akamai override is needed unless requested,
        // and when IPs are blank psiphon-akamai defaults automatically:
        val autoAkamaiArgs = builder.buildRunArgs(
            appSettings = AppSettings(activePreset = OneTapPreset.PSIPHON_AKAMAI_CDN),
            aether = AetherSettings(
                psiphonEnabled = true,
                psiphonMode = ir.pnifox.core.model.PsiphonChainMode.STANDALONE_CDN,
            ),
            nativeDir = "/data/app/lib/arm64",
            workDir = File("/data/local/aether"),
        )
        assertEquals("psiphon-akamai", autoAkamaiArgs[autoAkamaiArgs.indexOf("--psiphon-cdn-sets") + 1])

        val reverseArgs = builder.buildRunArgs(
            appSettings = AppSettings(),
            aether = AetherSettings(
                psiphonEnabled = true,
                psiphonMode = ir.pnifox.core.model.PsiphonChainMode.PSIPHON_TO_WARP,
            ),
            nativeDir = "/data/app/lib/arm64",
            workDir = File("/data/local/aether"),
        )
        assertTrue(reverseArgs.contains("--psiphon-reverse"))
        assertTrue(reverseArgs.contains("--masque"))
        assertTrue(reverseArgs.contains("--h2"))
    }
}
