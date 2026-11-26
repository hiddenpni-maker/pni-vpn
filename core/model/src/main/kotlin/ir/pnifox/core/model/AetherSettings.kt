package ir.pnifox.core.model

import kotlinx.serialization.Serializable

@Serializable
enum class AetherProtocol(val cliFlag: String, val label: String, val descriptionEn: String, val descriptionFa: String) {
    MASQUE("--masque", "MASQUE", "Fast HTTPS/QUIC or HTTP/2 WARP tunnel", "تونل HTTPS سریع روی HTTP/2 یا HTTP/3"),
    WIREGUARD("--wg", "WireGuard", "Classic UDP WireGuard WARP tunnel", "پروتکل کلاسیک UDP با رمزنگاری سبک"),
    WARP_IN_WARP("--gool-classic", "WARP-in-WARP", "Dual WireGuard nested hops", "دو لایه تونل تو در تو روی کلاینت"),
    MASQUE_IN_MASQUE("--mim", "MASQUE-in-MASQUE", "Dual MASQUE hops for new exit IP", "دو لایه MASQUE برای تغییر IP"),
    WIREGUARD_OVER_MASQUE("--gool", "WG over MASQUE", "WireGuard inner hop carried inside MASQUE", "عبور وایرگارد از داخل MASQUE"),
}

@Serializable
enum class AetherIdentityTarget(val cliValue: String, val label: String) {
    ALL("all", "All (MASQUE + WG + Gool)"),
    MASQUE("masque", "MASQUE only"),
    WIREGUARD("wg", "WireGuard only"),
    GOOL("gool", "WG over MASQUE (--gool)"),
    MIM("mim", "MASQUE-in-MASQUE (--mim)"),
}

@Serializable
enum class MasqueTransport(val cliFlag: String, val label: String) {
    HTTP2("--h2", "HTTP/2 (TCP)"),
    HTTP3("--h3", "HTTP/3 (QUIC)"),
}

@Serializable
enum class AetherScanMode(val cliValue: String, val label: String) {
    TURBO("turbo", "Turbo"),
    BALANCED("balanced", "Balanced"),
    THOROUGH("thorough", "Thorough"),
    VERIFIED("verified", "Verified"),
    IRONCLAD("ironclad", "Ironclad"),
}

@Serializable
enum class AetherNoizeProfile(val cliValue: String, val label: String) {
    OFF("off", "Off"),
    LIGHT("light", "Light"),
    FIREWALL("firewall", "Firewall"),
    BALANCED("balanced", "Balanced"),
    GFW("gfw", "GFW"),
    AGGRESSIVE("aggressive", "Aggressive"),
}

@Serializable
enum class TlsFingerprint(val cliValue: String, val label: String) {
    CHROME("chrome", "Chrome"),
    FIREFOX("firefox", "Firefox"),
    SAFARI("safari", "Safari"),
    IOS("ios", "iOS"),
    ANDROID("android", "Android"),
    RANDOM("random", "Randomized"),
}

@Serializable
enum class IpVersionMode(val cliValue: String, val label: String) {
    V4("v4", "IPv4 (-4)"),
    V6("v6", "IPv6 (-6)"),
    BOTH("both", "Dual-Stack"),
}

@Serializable
enum class ExitDialMode(val cliValue: String, val label: String) {
    FREEDOM("freedom", "freedom"),
    DIRECT("direct", "direct"),
    SOCKS("socks", "socks"),
}

@Serializable
enum class PsiphonChainMode(val cliMode: String, val labelEn: String, val labelFa: String) {
    WARP_TO_PSIPHON("auto", "WARP → Psiphon", "WARP → Psiphon (ضدتحریم)"),
    CDN_FRONTING("cdn", "Psiphon CDN Fronting", "عبور CDN سایفون"),
    STANDALONE("auto", "Psiphon Only", "سایفون مستقیم"),
}

@Serializable
data class AetherSettings(
    val protocol: AetherProtocol = AetherProtocol.MASQUE,
    val masqueTransport: MasqueTransport = MasqueTransport.HTTP2,
    val endpointIp: String = "162.159.198.1",
    val endpointPort: Int = 443,
    val innerEndpointPeer: String = "",
    val sni: String = "www.cloudflare.com",
    val ipVersion: IpVersionMode = IpVersionMode.V4,
    val scanMode: AetherScanMode = AetherScanMode.BALANCED,
    val scanForEndpoint: Boolean = true,
    val noizeProfile: AetherNoizeProfile = AetherNoizeProfile.FIREWALL,
    val tlsFingerprint: TlsFingerprint = TlsFingerprint.CHROME,
    val fragmentEnabled: Boolean = false,
    val fragmentSizeBytes: String = "8-16",
    val fragmentDelayMs: String = "2-10",
    val echEnabled: Boolean = false,
    val echDns: String = "udp://1.1.1.1",
    val echDomain: String = "cloudflare-ech.com",
    val exitDialMode: ExitDialMode = ExitDialMode.FREEDOM,
    val finalMaskRawJson: String = "",
    val psiphonEnabled: Boolean = false,
    val psiphonMode: PsiphonChainMode = PsiphonChainMode.WARP_TO_PSIPHON,
    val psiphonCdnFrontingIps: String = "",
    val psiphonExitCountry: String = "AUTO",
    val psiphonUseBundledServersDat: Boolean = true,
    val torEnabled: Boolean = false,
    val torUseBridgesImmediately: Boolean = false,
    val customTorBridges: String = "",
    val warpApiPrimaryHost: String = "api.devices.cloudflare.com",
    val warpApiFallbackHost: String = "api.cloudflareclient.com",
    val warpApiCustomAddress: String = "",
    val warpApiSni: String = "",
    val warpApiUseFragment: Boolean = false,
    val warpApiUseEch: Boolean = false,
    val masqueAccountRegistered: Boolean = false,
    val wgAccountRegistered: Boolean = false,
    val goolAccountRegistered: Boolean = false,
    val localSocksPort: Int = 10820,
    val innerAetherBindPort: Int = 10822,
)
