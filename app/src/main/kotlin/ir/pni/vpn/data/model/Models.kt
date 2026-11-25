package ir.pni.vpn.data.model

import kotlinx.serialization.Serializable

@Serializable
enum class ProtocolType(val labelFa: String, val labelEn: String, val engine: EngineKind) {
    VLESS("VLESS", "VLESS", EngineKind.XRAY),
    VMESS("VMess", "VMess", EngineKind.XRAY),
    TROJAN("Trojan", "Trojan", EngineKind.XRAY),
    SHADOWSOCKS("Shadowsocks", "Shadowsocks", EngineKind.XRAY),
    WIREGUARD("WireGuard", "WireGuard", EngineKind.XRAY),
    HYSTERIA2("Hysteria2", "Hysteria2", EngineKind.SINGBOX),
    TUIC("TUIC v5", "TUIC v5", EngineKind.SINGBOX),
    SSH("SSH Tunnel", "SSH Tunnel", EngineKind.SINGBOX),
    LANTERN_STEALTH("Lantern / Outline", "Lantern / Outline", EngineKind.SINGBOX),
    AETHER_WARP("Aether WARP", "Aether WARP", EngineKind.AETHER),
    AETHER_MASQUE("Aether MASQUE", "Aether MASQUE", EngineKind.AETHER),
    DNS_TUNNEL("تونل DNS (DNSTT / AM Tunnel)", "DNS Tunnel (DNSTT)", EngineKind.DNSTT),
    PSIPHON("سایفون (Psiphon)", "Psiphon", EngineKind.PSIPHON),
    TOR("تور (Tor + Bridge)", "Tor + Bridge", EngineKind.TOR),
    CUSTOM_JSON("کانفیگ JSON سفارشی", "Custom JSON", EngineKind.XRAY)
}

@Serializable
enum class EngineKind {
    XRAY,
    SINGBOX,
    AETHER,
    DNSTT,
    PSIPHON,
    TOR
}

@Serializable
enum class NetworkType(val titleFa: String, val categoryFa: String, val isMobile: Boolean) {
    MCI("همراه اول (Hamrah-e Aval)", "اینترنت موبایل", true),
    IRANCELL_MOBILE("ایرانسل (Irancell Mobile)", "اینترنت موبایل", true),
    RIGHTEL("رایتل (Rightel)", "اینترنت موبایل", true),
    SHATEL_MOBILE("شاتل موبایل (Shatel Mobile)", "اینترنت موبایل", true),
    TD_LTE("مودم TD-LTE (ایرانسل / مبین‌نت / زیتل)", "مودم سیم‌کارتی ثابت", false),
    ADSL_VDSL("مودم ADSL / VDSL (مخابرات / شاتل / آسیاتک)", "مودم خط ثابت", false),
    FIBER("فیبر نوری (FTTH)", "اینترنت پرسرعت فیبر", false)
}

@Serializable
enum class CdnProvider(val id: String, val titleFa: String, val defaultSni: String, val assetFile: String) {
    CLOUDFLARE("cloudflare", "کلودفلر (Cloudflare)", "speed.cloudflare.com", "scanner/cf_v4.txt"),
    AKAMAI("akamai", "آکمای (Akamai Edge)", "www.akamai.com", "scanner/akamai_v4.txt"),
    FASTLY("fastly", "فستلی (Fastly CDN)", "www.fastly.com", "scanner/fastly_v4.txt"),
    GCORE("gcore", "جی‌کور (Gcore CDN)", "gcore.com", "scanner/gcore_v4.txt"),
    WARP("warp", "کلودفلر WARP / MASQUE", "consumer-masque.cloudflareclient.com", "scanner/warp_cidrs.txt"),
    CUSTOM("custom", "لیست دلخواه (IP / CIDR / دامنه)", "speed.cloudflare.com", "")
}

@Serializable
data class ProxyProfile(
    val id: String,
    val name: String,
    val protocol: ProtocolType,
    val server: String,
    val port: Int,
    val uuidOrPassword: String = "",
    val network: String = "ws",
    val security: String = "tls",
    val sni: String = "",
    val host: String = "",
    val path: String = "/",
    val alpn: String = "",
    val fingerprint: String = "chrome",
    val cipherSuites: String = "",
    val unsafeTls: Boolean = false,
    val allowUnencryptedPublic: Boolean = false,
    val publicKey: String = "",
    val shortId: String = "",
    val spiderX: String = "",
    val flow: String = "",
    val obfsPassword: String = "",
    val fragmentEnabled: Boolean = false,
    val fragmentPackets: String = "tlshello",
    val fragmentLength: String = "10-20",
    val fragmentInterval: String = "10-20",
    val noiseEnabled: Boolean = false,
    val noiseType: String = "rand",
    val noisePacket: String = "50-100",
    val noiseDelay: String = "10-15",
    val chainNextProfileId: String? = null,
    val spoofEnabled: Boolean = false,
    val spoofTargetAddr: String = "",
    val spoofFakeSni: String = "",
    val aetherMode: String = "warp",
    val torBridgeType: String = "obfs4",
    val dnsTransport: String = "udp",
    val dnsResolver: String = "8.8.8.8:53",
    val dnsDomain: String = "",
    val dnsPubkey: String = "",
    val dnsInnerMode: String = "ssh",
    val dnsInnerUser: String = "",
    val dnsInnerPass: String = "",
    val dnsUtls: String = "3*Firefox,2*Chrome,1*iOS",
    val rawShareLink: String = "",
    val rawCustomJson: String = "",
    val latencyMs: Int? = null,
    val group: String = "عمومی"
)

@Serializable
data class IspPreset(
    val networkType: NetworkType,
    val recommendedProtocols: List<ProtocolType>,
    val preferredCdn: CdnProvider,
    val fragmentEnabled: Boolean,
    val fragmentPackets: String,
    val fragmentLength: String,
    val fragmentInterval: String,
    val noiseEnabled: Boolean,
    val scannerGentle: Boolean,
    val scannerAntiDpi: Boolean,
    val mtu: Int,
    val dnsPrimary: String,
    val dnsSecondary: String,
    val preferIpv6: Boolean,
    val adviceFa: String,
    val warningsFa: String
)

@Serializable
data class ScanOptions(
    val provider: CdnProvider = CdnProvider.CLOUDFLARE,
    val targetsText: String = "",
    val count: Int = 200,
    val portsCsv: String = "443,8443,2053",
    val workers: Int = 20,
    val timeoutMs: Int = 4500,
    val attempts: Int = 2,
    val sni: String = "speed.cloudflare.com",
    val requireWs: Boolean = false,
    val wsPath: String = "/",
    val neighborScan: Boolean = false,
    val gentle: Boolean = true,
    val skipPhase1: Boolean = false,
    val antiDpi: Boolean = true,
    val antiDpiLengths: String = "1/104/1",
    val antiDpiDelayMs: Int = 2,
    val shareLinkForPhase2: String = "",
    val phase2TopN: Int = 10
)

@Serializable
data class ScanResultItem(
    val ip: String,
    val port: Int,
    val provider: String = "",
    val latencyMs: Int = 0,
    val lossPct: Double = 0.0,
    val colo: String = "",
    val country: String = "",
    val cdnHeader: String = "",
    val healthy: Boolean = false,
    val wsOk: Boolean = false,
    val phase2Tested: Boolean = false,
    val phase2Ok: Boolean = false,
    val ttfbMs: Int = 0,
    val downloadMbps: Double = 0.0,
    val antiDpiUsed: Boolean = false,
    val score: Double = 0.0
)

@Serializable
data class AppSettings(
    val activeNetworkType: NetworkType = NetworkType.MCI,
    val activeProfileId: String? = null,
    val splitTunnelEnabled: Boolean = false,
    val excludedPackages: Set<String> = emptySet(),
    val bypassIranDomains: Boolean = true,
    val bypassIranIps: Boolean = true,
    val blockAds: Boolean = true,
    val dnsMode: String = "https://1.1.1.1/dns-query",
    val fallbackDns: String = "9.9.9.9",
    val mtu: Int = 1280,
    val ipv6Enabled: Boolean = false,
    val enableLocalSniSpoof: Boolean = false,
    val localSniSpoofPort: Int = 40443,
    val spoofFakeSni: String = "www.speedtest.net",
    val spoofFragLen: Int = 40,
    val cfApiToken: String = "",
    val cfAccountId: String = "",
    val cfWorkerName: String = "pni-edge",
    val cfWorkerUuid: String = "",
    val cfFallbackUrl: String = "https://www.speedtest.net",
    val cfProxyIps: String = ""
)

enum class ConnectionState {
    DISCONNECTED,
    CONNECTING,
    CONNECTED,
    DISCONNECTING,
    ERROR
}

@Serializable
data class DiagnosticReport(
    val timestampMs: Long,
    val networkLabelFa: String,
    val dnsPoisoned: Boolean,
    val dnsLatencyMs: Int,
    val cloudflareTlsOk: Boolean,
    val akamaiTlsOk: Boolean,
    val sniFragmentHelps: Boolean,
    val udpReachable: Boolean,
    val recommendationFa: String,
    val suggestedProtocols: List<ProtocolType>
)

@Serializable
data class GuideSection(
    val id: String,
    val categoryFa: String,
    val titleFa: String,
    val summaryFa: String,
    val stepsFa: List<String>,
    val tipsFa: List<String>
)
