package ir.pnifox.desktop

import ir.pnifox.core.model.AppSettings
import ir.pnifox.desktop.core.WindowsTunConfigBuilder
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
            socksPort = 10820,
            clashApiPort = 19090,
            bypassTargetIps = listOf("162.159.198.2", "216.239.38.120"),
        )
        assertTrue(json.contains("\"type\":\"tun\""))
        assertTrue(json.contains("\"stack\":\"gvisor\""))
        assertTrue(json.contains("\"auto_route\":true"))
        assertTrue(json.contains("\"162.159.198.2/32\""))
        assertTrue(json.contains("\"216.239.38.120/32\""))
        assertTrue(json.contains("\"server_port\":10820"))
        assertTrue(json.contains("\"aether.exe\""))
        assertTrue(json.contains("\"mhrv-rs.exe\""))
        assertTrue(json.contains("\"external_controller\":\"127.0.0.1:19090\""))
        assertTrue(json.contains("\"ip_is_private\":true"))
    }
}
