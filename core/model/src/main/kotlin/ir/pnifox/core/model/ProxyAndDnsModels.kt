package ir.pnifox.core.model

import kotlinx.serialization.Serializable

@Serializable
data class ProxyProfile(
    val id: String,
    val groupId: String = "manual",
    val name: String,
    val protocol: ProxyProtocol,
    val address: String,
    val port: Int,
    val secret: String = "",
    val sni: String = "",
    val transport: String = "tcp",
    val pathOrServiceName: String = "",
    val tlsFingerprint: String = "chrome",
    val realityPublicKey: String = "",
    val realityShortId: String = "",
    val fragmentEnabled: Boolean = false,
    val spoofLocalhost40443: Boolean = false,
    val spoofFakeSni: String = "",
    val rawUri: String = "",
    val lastPingMs: Int? = null,
    val isPinned: Boolean = false,
    val updatedAtEpochMs: Long = 0L,
)

@Serializable
data class SubscriptionGroup(
    val id: String,
    val name: String,
    val url: String,
    val autoUpdateHours: Int = 12,
    val lastUpdatedEpochMs: Long = 0L,
    val profileCount: Int = 0,
)

@Serializable
enum class DnsTunnelMode(val label: String, val subtitleEn: String, val subtitleFa: String) {
    UDP("UDP", "Direct Port 53 UDP query", "پورت ۵۳ مستقیم"),
    SUPER_DNS("SuperDNS", "High-frequency parallel resolver", "پرس‌وجوی موازی سریع"),
    FAST_DNS("FastDNS", "Lightweight low-latency framing", "بسته سبک و کم‌پینگ"),
    PDOQ("PDoQ", "DNS over QUIC / TLS resilience", "DNS روی QUIC مقاوم"),
    DOH("DoH", "Encrypted DNS over HTTPS", "DNS امن HTTPS"),
    DOT("DoT", "Encrypted DNS over TLS :853", "DNS روی TLS"),
}

@Serializable
enum class DnsInnerProtocol(val label: String) {
    SOCKS5("SOCKS5"),
    SSH("SSH"),
    VLESS("VLESS"),
}

@Serializable
enum class DnsEngineType(
    val labelEn: String,
    val labelFa: String,
    val subtitleEn: String,
    val subtitleFa: String,
) {
    AUTO_TUNNEL(
        labelEn = "One-Tap Auto Tunnel",
        labelFa = "اتصال خودکار (بدون کلید)",
        subtitleEn = "Uses built-in AETHER core + selected DNS (no VPS keys needed)",
        subtitleFa = "اتصال فوری با هسته داخلی و DNS انتخابی بدون نیاز به سرور دستی",
    ),
    DNS_CHANGER(
        labelEn = "Anti-Sanction DNS",
        labelFa = "فقط تغییر DNS (ضدتحریم)",
        subtitleEn = "Lightweight DoH / DoT / Shecan DNS VPN without proxy slowdown",
        subtitleFa = "تغییر DNS رمزنگاری‌شده بدون افت سرعت گیم و دانلود",
    ),
    CUSTOM_DNSTT(
        labelEn = "Custom SlowDNS",
        labelFa = "DNSTT اختصاصی",
        subtitleEn = "Requires your own VPS Nameserver & Server Public Key",
        subtitleFa = "نیازمند دامنه Nameserver و کلید عمومی سرور شخصی",
    ),
}

@Serializable
data class DnsResolverPreset(
    val id: String,
    val titleEn: String,
    val titleFa: String,
    val address: String,
    val mode: DnsTunnelMode,
    val ispHintEn: String,
    val ispHintFa: String,
    val badge: String,
)

@Serializable
data class DnsTunnelSettings(
    val engineType: DnsEngineType = DnsEngineType.AUTO_TUNNEL,
    val mode: DnsTunnelMode = DnsTunnelMode.UDP,
    val selectedPresetId: String = "cf_udp_1",
    val resolverAddress: String = "1.1.1.1:53",
    val tunnelFrequency: Int = 1,
    val timeoutMs: Int = 4000,
    val nameserver: String = "",
    val publicKeyHex: String = "",
    val innerProtocol: DnsInnerProtocol = DnsInnerProtocol.SOCKS5,
    val sshUsername: String = "",
    val sshPassword: String = "",
    val vlessUri: String = "",
    val probeDomain: String = "google.com",
)

@Serializable
enum class ScanProvider(val labelEn: String, val labelFa: String, val defaultSni: String) {
    CLOUDFLARE("Cloudflare", "کلودفلر", "speed.cloudflare.com"),
    AKAMAI("Akamai", "آکامای", "www.akamai.com"),
    FASTLY("Fastly", "فستلی", "www.fastly.com"),
    GCORE("GCore", "جی‌کور", "gcore.com"),
    WARP("WARP Endpoints", "اندپوینت WARP", "consumer-masque.cloudflareclient.com"),
    SPOOF_SCANNER("Spoof Scanner", "اسکنر Spoof", "speed.cloudflare.com"),
    CUSTOM("Custom CIDRs", "رنج دلخواه", "speed.cloudflare.com"),
}

@Serializable
enum class ScanSpeedTier(val badge: String, val labelEn: String) {
    S_PLUS("S+", "Ultra Fast"),
    A("A", "Fast"),
    B("B", "Balanced"),
    C("C", "Fallback"),
}

enum class ScanCategoryFilter(val labelEn: String, val labelFa: String) {
    ALL("All Ranked", "همه"),
    ULTRA_FAST("Ultra Fast (<130ms)", "پرسرعت (<130ms)"),
    ZERO_LOSS("0% Loss / Low Jitter", "بدون قطعی (0% Loss)"),
    HIGH_THROUGHPUT("Best Download Speed", "بالاترین سرعت دانلود"),
    TLS_OK("TLS & SNI Verified", "تاییدشده TLS"),
}

enum class ScanSortBy(val labelEn: String, val labelFa: String) {
    SCORE("Overall Score", "امتیاز کلی"),
    LATENCY("Lowest Ping (ms)", "کمترین پینگ"),
    SPEED("Download Speed (KB/s)", "سرعت دانلود"),
    JITTER("Lowest Jitter (ms)", "کمترین نوسان"),
    LOSS("Lowest Loss (%)", "کمترین Packet Loss"),
}

@Serializable
data class ScanResultItem(
    val id: String,
    val ip: String,
    val provider: ScanProvider,
    val port: Int,
    val latencyMs: Int,
    val minLatencyMs: Int = latencyMs,
    val jitterMs: Int = 0,
    val packetLossPct: Int = 0,
    val tlsHandshakeMs: Int? = null,
    val downloadSpeedKbps: Int = 0,
    val colo: String = "",
    val stabilityScore: Int = 0,
    val tier: ScanSpeedTier = ScanSpeedTier.B,
    val scannedAtEpochMs: Long = 0L,
)

enum class TrafficTimeframe(val labelEn: String, val labelFa: String) {
    SESSION("Session", "نشست فعلی"),
    DAILY("Today", "امروز"),
    WEEKLY("7 Days", "هفتگی"),
    MONTHLY("30 Days", "ماهانه"),
}

data class TrafficChartPoint(
    val label: String,
    val rxBytes: Long,
    val txBytes: Long,
)

data class TrafficSummary(
    val rxSpeedBps: Long = 0L,
    val txSpeedBps: Long = 0L,
    val sessionRxBytes: Long = 0L,
    val sessionTxBytes: Long = 0L,
    val todayRxBytes: Long = 0L,
    val todayTxBytes: Long = 0L,
    val weekRxBytes: Long = 0L,
    val weekTxBytes: Long = 0L,
    val monthRxBytes: Long = 0L,
    val monthTxBytes: Long = 0L,
    val chartPoints: List<TrafficChartPoint> = emptyList(),
)

@Serializable
enum class ThemeModePref(val labelEn: String, val labelFa: String) {
    SYSTEM("System", "سیستم"),
    LIGHT("Light", "روشن"),
    DARK("Dark", "تیره"),
}

@Serializable
enum class AccentColorPref(val colorHex: Long, val labelEn: String, val labelFa: String) {
    FOX_ORANGE(0xFFF97316, "Fox Orange", "نارنجی روباهی"),
    CRIMSON_MAKIMA(0xFFDC2626, "Makima Crimson", "قرمز ماکیما"),
    COBALT(0xFF3B82F6, "Cobalt", "آبی کبالت"),
    EMERALD(0xFF10B981, "Emerald", "سبز زمردی"),
    TEAL(0xFF14B8A6, "Teal", "فیروزه‌ای"),
    VIOLET(0xFF8B5CF6, "Violet", "بنفش"),
}

@Serializable
data class AppSettings(
    val language: AppLanguage = AppLanguage.ENGLISH,
    val activeSection: ActiveSectionEngine = ActiveSectionEngine.AETHER,
    val connectionMode: ConnectionMode = ConnectionMode.VPN,
    val transportMode: TransportMode = TransportMode.NORMAL,
    val exitNode: ExitNode = ExitNode.DEFAULT,
    val regionCode: String = "AUTO",
    val activePreset: OneTapPreset = OneTapPreset.WARP_FAST_MASQUE_H2,
    val activeProfileId: String? = null,
    val themeMode: ThemeModePref = ThemeModePref.DARK,
    val accentColor: AccentColorPref = AccentColorPref.FOX_ORANGE,
    val chainProxies: Boolean = false,
    val localDns: String = "1.1.1.1",
    val antiSanctionDns: String = "178.22.122.100",
    val fakeDnsEnabled: Boolean = true,
    val ipv6Enabled: Boolean = false,
    val allowLan: Boolean = false,
    val httpProxyPort: Int = 10821,
    val mtu: Int = 1500,
    val perAppWhitelistMode: Boolean = false,
    val perAppPackages: Set<String> = emptySet(),
    val cleanIpsMultiline: String = "162.159.198.1\n162.159.192.1\n188.114.97.3",
    val bypassIranEnabled: Boolean = true,
    val bypassPrivateLanEnabled: Boolean = true,
    val blockAdsEnabled: Boolean = false,
    val customDirectRules: String = "",
    val customBlockRules: String = "",
    val pauseTelemetryOnScreenOff: Boolean = true,
    val telegramUrl: String = "https://t.me/otaqe_pni",
    val hasSelectedLanguage: Boolean = false,
    val hasPromptedTelegramJoin: Boolean = false,
    val autoCheckUpdates: Boolean = true,
    val lastDismissedUpdateTag: String = "",
    val geoAssetsLastUpdatedLabel: String = "Chocolate4U Geo Assets ready",
)
