package ir.pnifox.core.model

enum class NativeBinaryId(
    val fileName: String,
    val displayTitle: String,
    val requiredFor: String,
) {
    HEV_SOCKS5_TUNNEL(
        fileName = "libhev-socks5-tunnel.so",
        displayTitle = "tun2socks (libhev-socks5-tunnel.so)",
        requiredFor = "VPN Mode TUN → 127.0.0.1:10820 bridge",
    ),
    XRAY(
        fileName = "libxray.so",
        displayTitle = "Xray-core (pni-core.aar / libxray.so)",
        requiredFor = "VLESS / VMess / Trojan / Shadowsocks / Fragment / SNI Spoof",
    ),
    SING_BOX(
        fileName = "libpnisingbox.so",
        displayTitle = "Sing-box (libpnisingbox.so)",
        requiredFor = "Hysteria2 / TUIC / WireGuard custom servers",
    ),
    AETHER(
        fileName = "libaether.so",
        displayTitle = "AETHER Core (libaether.so)",
        requiredFor = "MASQUE / WARP / WG over MASQUE / ECH / WARP → Psiphon",
    ),
    PSIPHON(
        fileName = "libpsiphon.so",
        displayTitle = "Psiphon Core (libpsiphon.so)",
        requiredFor = "Psiphon chained & CDN-fronted exit",
    ),
    TOR(
        fileName = "libtor.so",
        displayTitle = "Tor Expert Bundle (libtor.so)",
        requiredFor = "Tor Onion routing",
    ),
    LYREBIRD(
        fileName = "liblyrebird.so",
        displayTitle = "Lyrebird obfs4 (liblyrebird.so)",
        requiredFor = "Tor obfs4 / Snowflake pluggable transports",
    ),
    DNSTT(
        fileName = "libdnstt.so",
        displayTitle = "DNSTT Client (libdnstt.so)",
        requiredFor = "DNS Tunneling (UDP / DoH / DoT → 127.0.0.1:10820)",
    ),
    MHR(
        fileName = "libmhrv_rs.so",
        displayTitle = "MasterHttpRelay (libmhrv_rs.so)",
        requiredFor = "Google Apps Script / SNI Front HTTP Relay → 127.0.0.1:10820",
    ),
}

data class NativeBinaryStatus(
    val binary: NativeBinaryId,
    val isAvailable: Boolean,
    val resolvedPath: String,
    val sizeBytes: Long,
)

data class GuideTopic(
    val id: String,
    val carrierTag: String,
    val titleEn: String,
    val titleFa: String,
    val summaryEn: String,
    val summaryFa: String,
    val stepsEn: List<String>,
    val stepsFa: List<String>,
)

object IranPresetsCatalog {
    val dnsPresets: List<DnsResolverPreset> = listOf(
        DnsResolverPreset("cf_udp_1", "Cloudflare UDP 1", "کلودفلر UDP ۱", "1.1.1.1:53", DnsTunnelMode.UDP, "Best on Wi-Fi / ADSL", "مناسب وای‌فای و ADSL", "UDP"),
        DnsResolverPreset("cf_udp_2", "Cloudflare UDP 2", "کلودفلر UDP ۲", "1.0.0.1:53", DnsTunnelMode.UDP, "Cloudflare secondary", "پشتیبان کلودفلر", "UDP"),
        DnsResolverPreset("google_udp_1", "Google UDP 1", "گوگل UDP ۱", "8.8.8.8:53", DnsTunnelMode.UDP, "Stable on most carriers", "پایدار روی بیشتر خطوط", "UDP"),
        DnsResolverPreset("google_udp_2", "Google UDP 2", "گوگل UDP ۲", "8.8.4.4:53", DnsTunnelMode.UDP, "Google secondary", "پشتیبان گوگل", "UDP"),
        DnsResolverPreset("iran_ap_v4", "Iran Access Point IPv4", "دسترسی ایران IPv4", "178.22.122.100:53", DnsTunnelMode.SUPER_DNS, "Shecan • MCI / Irancell", "شکن / همراه اول و ایرانسل", "IR IPv4"),
        DnsResolverPreset("electro_v4", "Electro / 403 Bypass", "الکترو / ۴۰۳ ضدتحریم", "78.157.42.100:53", DnsTunnelMode.FAST_DNS, "Gaming & sanctions unlock", "رفع تحریم گیمینگ و ابزارها", "Anti-Sanction"),
        DnsResolverPreset("google_doh", "Google DoH", "گوگل DoH امن", "https://dns.google/dns-query", DnsTunnelMode.DOH, "HTTPS encrypted DNS", "ضد شنود DNS", "DoH"),
        DnsResolverPreset("cf_doh", "Cloudflare DoH", "کلودفلر DoH امن", "https://cloudflare-dns.com/dns-query", DnsTunnelMode.DOH, "Encrypted HTTPS port 443", "رمزنگاری HTTPS پورت ۴۴۳", "DoH"),
        DnsResolverPreset("cf_pdoq", "Cloudflare PDoQ", "کلودفلر PDoQ", "quic://dns.cloudflare.com", DnsTunnelMode.PDOQ, "QUIC low-latency DNS", "پاسخ سریع روی QUIC", "PDoQ"),
    )

    val carrierGuides: List<GuideTopic> = listOf(
        GuideTopic(
            id = "irancell",
            carrierTag = "Irancell 4G/5G / TD-LTE",
            titleEn = "Recommended Profile for Irancell",
            titleFa = "بهترین تنظیمات برای اینترنت ایرانسل",
            summaryEn = "Irancell handles MASQUE HTTP/2 + short TLS ClientHello Fragment well; lower latency comes from Pro Scanner Cloudflare IPs.",
            summaryFa = "روی ایرانسل معمولاً Fragment کوتاه یا Masque HTTP/2 بهترین پایداری را دارد.",
            stepsEn = listOf(
                "In AETHER Studio: generate WARP keys first, scan for a MASQUE endpoint, then use Fast WARP (MASQUE H2).",
                "Enable ClientHello Fragment (size 8-16 bytes, delay 2-10ms) if handshakes drop.",
                "Pick a Sub-120ms 0%-loss IP in the Pro Scanner and tap 'Apply to AETHER'.",
            ),
            stepsFa = listOf(
                "در تب AETHER ابتدا کلید WARP بسازید و اندپوینت را اسکن کنید.",
                "در صورت قطعی دست‌دهی، Fragment با سایز 8-16 بایت را فعال کنید.",
                "از اسکنر حرفه‌ای یک IP با پینگ زیر ۱۲۰ میلی‌ثانیه و Loss صفر انتخاب و اعمال کنید.",
            ),
        ),
        GuideTopic(
            id = "mci",
            carrierTag = "MCI (Hamrah-e Avval)",
            titleEn = "Recommended Profile for MCI",
            titleFa = "بهترین تنظیمات برای سیم‌کارت همراه اول",
            summaryEn = "MCI inspects plaintext SNI aggressively; enable ECH or chain WARP → Psiphon.",
            summaryFa = "همراه اول حساسیت بالایی روی SNI دارد؛ ECH یا زنجیره WARP → Psiphon توصیه می‌شود.",
            stepsEn = listOf(
                "Turn on Encrypted Client Hello (ECH auto) with udp://1.1.1.1 and cloudflare-ech.com.",
                "Try WG over MASQUE (--gool) or WARP → Psiphon if direct WARP IPs are throttled.",
                "Keep IPv4 enabled unless your SIM assigns a routable native IPv6 address.",
            ),
            stepsFa = listOf(
                "گزینه ECH را با udp://1.1.1.1 و دامنه cloudflare-ech.com روشن کنید.",
                "در صورت محدودیت، پیش‌تنظیم WARP → Psiphon یا WG over MASQUE را انتخاب کنید.",
                "نسخه IP را روی IPv4 بگذارید مگر اینکه شبکه IPv6 بومی بدهد.",
            ),
        ),
        GuideTopic(
            id = "adsl_wifi",
            carrierTag = "ADSL / VDSL / FTTH Wi-Fi",
            titleEn = "Fixed Broadband & Modem Tuning",
            titleFa = "تنظیمات ADSL، VDSL و فیبر نوری",
            summaryEn = "Fixed lines usually allow HTTP/3 QUIC MASQUE and custom VLESS Reality servers at full throughput.",
            summaryFa = "روی خطوط ثابت معمولاً HTTP/3 و سرورهای VLESS Reality سرعت کامل می‌دهند.",
            stepsEn = listOf(
                "Benchmark candidate CDN IPs with Download Throughput enabled in the Scanner tab.",
                "Use the DNS tab in SuperDNS or DoH mode if ISP DNS resolvers hijack domains.",
            ),
            stepsFa = listOf(
                "در تب اسکنر، تست سرعت دانلود را فعال کرده و پرسرعت‌ترین IP را انتخاب کنید.",
                "هنگام اختلال DNS مخابرات از تب DNS استفاده کنید.",
            ),
        ),
    )
}
