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
    val smartEchBootstrap: Boolean = true,
    val echBase64Override: String = "",
    val echDns: String = "udp://1.1.1.1",
    val echDomain: String = "cloudflare-ech.com",
    val quicV2Enabled: Boolean = true,
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

@Serializable
enum class GamingMode(
    val titleEn: String,
    val titleFa: String,
    val subtitleEn: String,
    val subtitleFa: String,
) {
    DNS_ANTI_SANCTION(
        titleEn = "Anti-Sanction Gaming DNS (0ms Extra Ping)",
        titleFa = "تغییر DNS گیمینگ ضدتحریم (بدون افت پینگ)",
        subtitleEn = "Uses Radar/Electro/403/Shecan/DoQ DNS to unlock Valorant, CoD, Fortnite & EA without proxying UDP packets.",
        subtitleFa = "رفع تحریم بازی‌های آنلاین (وارزون، ولورانت، EA، سوپرسل) بدون عبور ترافیک از پروکسی و بدون افزایش پینگ.",
    ),
    LOCAL_DPI_DESYNC(
        titleEn = "Local DPI Desync (Zero Server / Original Ping)",
        titleFa = "دورزدن محلی DPI (بدون سرور، پینگ اصلی)",
        subtitleEn = "Splits TLS ClientHello & uses clean DoH locally on device with zero remote hop — original ISP ping preserved.",
        subtitleFa = "خرد کردن بسته‌های فیلترینگ روی خود دستگاه بدون سرور واسط + DNS ضدتحریم (پینگ و سرعت مستقیم).",
    ),
    AETHER_UDP_QUIC(
        titleEn = "AETHER Gaming UDP (WARP WG / MASQUE H3 + QUIC v2)",
        titleFa = "تونل گیمینگ WARP (WireGuard / MASQUE QUIC v2)",
        subtitleEn = "Full UDP tunneling over scanned low-jitter Cloudflare endpoint with MTU tuning & Iran game-relay bypass.",
        subtitleFa = "عبور کامل ترافیک بازی روی UDP (مناسب زمانی که پورت بازی یا لابی کاملاً فیلتر است).",
    ),
    CUSTOM_SERVER(
        titleEn = "Low-Ping Gaming Server (WG / Hysteria2 / VLESS)",
        titleFa = "سرور گیمینگ اختصاصی (WireGuard / Hy2 / VLESS)",
        subtitleEn = "Connects your lowest-latency personal WireGuard, Hysteria2, TUIC v5, or VLESS Reality server.",
        subtitleFa = "اتصال به کم‌پینگ‌ترین سرور شخصی واردشده از بخش سرورها (مناسب WireGuard و Hysteria2).",
    ),
}

@Serializable
data class GamingSettings(
    val mode: GamingMode = GamingMode.DNS_ANTI_SANCTION,
    val selectedDnsIp: String = "10.202.10.10",
    val blockQuicUdp443: Boolean = false,
    val desyncChunkLen: Int = 6,
    val fakeSniHost: String = "www.cloudflare.com",
    val udpMtu: Int = 1380,
    val preferQuicV2H3: Boolean = true,
    val bypassIranGameTraffic: Boolean = true,
)
