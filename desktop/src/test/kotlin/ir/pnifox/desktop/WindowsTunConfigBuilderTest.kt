package ir.pnifox.desktop

import ir.pnifox.core.model.AppSettings
import ir.pnifox.desktop.core.WindowsSecurityHelper
import ir.pnifox.desktop.core.WindowsTunConfigBuilder
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WindowsTunConfigBuilderTest {

    @Test
    fun `tun bridge config sets wintun interface, socks detour, process bypass, and iran rules`() {
        val app = AppSettings(
            bypassIranEnabled = true,
            bypassPrivateLanEnabled = true,
            blockAdsEnabled = true,
            mtu = 1420,
        )
        val json = WindowsTunConfigBuilder.buildTunBridgeConfig(
            appSettings = app,
            socksPort = 10818,
            clashApiPort = 19090,
            clashSecret = "tok123",
            bypassTargetIps = listOf("162.159.198.2", "216.239.38.120"),
        )
        assertTrue(json.contains("\"type\":\"tun\""))
        assertTrue(json.contains("\"stack\":\"gvisor\""))
        assertTrue(json.contains("\"auto_route\":true"))
        assertTrue(json.contains("\"162.159.198.2/32\""))
        assertTrue(json.contains("\"216.239.38.120/32\""))
        assertTrue(json.contains("\"server_port\":10818"))
        assertTrue(json.contains("\"aether.exe\""))
        assertTrue(json.contains("\"pnifox.exe\""))
        assertTrue(json.contains("\"mhrv-rs.exe\""))
        assertTrue(json.indexOf("\"process_name\"") < json.indexOf("\"hijack-dns\""))
        assertTrue(json.contains("\"external_controller\":\"127.0.0.1:19090\""))
        assertTrue(json.contains("\"secret\":\"tok123\""))
        assertTrue(json.contains("\"ip_is_private\":true"))

        val dnsOnlyJson = WindowsTunConfigBuilder.buildTunBridgeConfig(
            appSettings = app,
            socksPort = 10818,
            bypassTargetIps = listOf("10.202.10.10"),
            dnsChangerOnly = true,
            overrideResolver = "10.202.10.10",
        )
        assertTrue(dnsOnlyJson.contains("\"final\":\"direct\""))
        assertTrue(dnsOnlyJson.contains("\"server\":\"10.202.10.10\""))
        assertFalse(dnsOnlyJson.contains("\"server\":\"10.202.10.10\",\"detour\":\"socks-out\""))
    }

    @Test
    fun `system gateway config exposes authenticated localhost ports and sanitizes secrets in logs`() {
        val gwJson = WindowsTunConfigBuilder.buildSystemGatewayConfig(
            appSettings = AppSettings(allowLan = false, bypassIranEnabled = true, blockAdsEnabled = true),
            socksListenPort = 10820,
            httpListenPort = 10821,
            upstreamSocksPort = 10818,
            clashApiPort = 19090,
            clashSecret = "secretXYZ",
        )
        assertTrue(gwJson.contains("\"type\":\"socks\""))
        assertTrue(gwJson.contains("\"type\":\"http\""))
        assertTrue(gwJson.contains("\"listen\":\"127.0.0.1\""))
        assertTrue(gwJson.contains("\"listen_port\":10820"))
        assertTrue(gwJson.contains("\"listen_port\":10821"))
        assertTrue(gwJson.contains("\"secret\":\"secretXYZ\""))
        assertTrue(!gwJson.contains("\"0.0.0.0\""))

        val redacted = WindowsSecurityHelper.sanitizeLogLine("""Connecting vless://my-secret-uuid@host.com:443 Authorization: Bearer abc12345 "auth_key":"TopSecretKey" """)
        assertFalse(redacted.contains("my-secret-uuid"))
        assertFalse(redacted.contains("abc12345"))
        assertFalse(redacted.contains("TopSecretKey"))
        assertTrue(redacted.contains("***"))
    }
}
