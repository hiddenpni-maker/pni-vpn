package ir.pni.vpn.data.preset

import ir.pni.vpn.data.model.CdnProvider
import ir.pni.vpn.data.model.IspPreset
import ir.pni.vpn.data.model.NetworkType
import ir.pni.vpn.data.model.ProtocolType

object IranNetworkPresets {

    val all: Map<NetworkType, IspPreset> = mapOf(
        NetworkType.MCI to IspPreset(
            networkType = NetworkType.MCI,
            recommendedProtocols = listOf(
                ProtocolType.VLESS,
                ProtocolType.TROJAN,
                ProtocolType.LANTERN_STEALTH,
                ProtocolType.AETHER_MASQUE,
                ProtocolType.DNS_TUNNEL,
                ProtocolType.TOR
            ),
            preferredCdn = CdnProvider.CLOUDFLARE,
            fragmentEnabled = true,
            fragmentPackets = "tlshello",
            fragmentLength = "10-25",
            fragmentInterval = "10-20",
            noiseEnabled = false,
            scannerGentle = true,
            scannerAntiDpi = true,
            mtu = 1280,
            dnsPrimary = "https://1.1.1.1/dns-query",
            dnsSecondary = "8.8.4.4",
            preferIpv6 = false,
            adviceFa = "همراه اول روی SNI حساسیت بالایی دارد. ترکیب VLESS/Trojan روی کلودفلر یا Akamai همراه با Fragment (`10-25`) و Anti-DPI بهترین پایداری را می‌دهد. اگر کلودفلر کند بود از Hysteria2 یا Aether MASQUE و در شرایط اختلال شدید از Tor با پل WebTunnel/obfs4 استفاده کنید.",
            warningsFa = "اسکن همزمان با تعداد کارگر بالا روی همراه اول باعث افت موقت آنتن/دیتا می‌شود؛ حالت Gentle را روشن نگه دارید."
        ),
        NetworkType.IRANCELL_MOBILE to IspPreset(
            networkType = NetworkType.IRANCELL_MOBILE,
            recommendedProtocols = listOf(
                ProtocolType.VLESS,
                ProtocolType.AETHER_WARP,
                ProtocolType.HYSTERIA2,
                ProtocolType.TROJAN,
                ProtocolType.DNS_TUNNEL,
                ProtocolType.PSIPHON
            ),
            preferredCdn = CdnProvider.AKAMAI,
            fragmentEnabled = true,
            fragmentPackets = "tlshello",
            fragmentLength = "20-50",
            fragmentInterval = "5-15",
            noiseEnabled = true,
            scannerGentle = true,
            scannerAntiDpi = true,
            mtu = 1280,
            dnsPrimary = "https://dns.quad9.net/dns-query",
            dnsSecondary = "1.0.0.1",
            preferIpv6 = false,
            adviceFa = "روی اینترنت موبایل ایرانسل، Akamai و رنج‌های تمیز Cloudflare به همراه XHTTP/WS و Fragment سریع متصل می‌شوند. در صورت باز بودن UDP، پروفایل‌های Aether WARP با Noise فعال و Hysteria2 سرعت بسیار خوبی دارند.",
            warningsFa = "اگر روی 5G/4G سرعت دانلود افت کرد، پورت 8443 یا 2053 را به‌جای 443 اسکن کنید."
        ),
        NetworkType.RIGHTEL to IspPreset(
            networkType = NetworkType.RIGHTEL,
            recommendedProtocols = listOf(
                ProtocolType.VLESS,
                ProtocolType.AETHER_MASQUE,
                ProtocolType.PSIPHON,
                ProtocolType.TOR
            ),
            preferredCdn = CdnProvider.CLOUDFLARE,
            fragmentEnabled = true,
            fragmentPackets = "tlshello",
            fragmentLength = "10-30",
            fragmentInterval = "10-20",
            noiseEnabled = true,
            scannerGentle = true,
            scannerAntiDpi = true,
            mtu = 1280,
            dnsPrimary = "https://1.1.1.1/dns-query",
            dnsSecondary = "9.9.9.9",
            preferIpv6 = false,
            adviceFa = "رایتل در برخی دکل‌ها روی شبکه همراه اول رومینگ می‌شود؛ استفاده از Fragment + faked/unsafe fingerprint (PattN) و یا Aether MASQUE/Psiphon در زمان اختلال توصیه می‌شود.",
            warningsFa = "در حالت رومینگ ملی، ممکن است رفتار شبکه دقیقاً مشابه همراه اول شود."
        ),
        NetworkType.SHATEL_MOBILE to IspPreset(
            networkType = NetworkType.SHATEL_MOBILE,
            recommendedProtocols = listOf(
                ProtocolType.VLESS,
                ProtocolType.HYSTERIA2,
                ProtocolType.AETHER_WARP,
                ProtocolType.SSH
            ),
            preferredCdn = CdnProvider.CLOUDFLARE,
            fragmentEnabled = true,
            fragmentPackets = "tlshello",
            fragmentLength = "15-40",
            fragmentInterval = "5-15",
            noiseEnabled = true,
            scannerGentle = true,
            scannerAntiDpi = true,
            mtu = 1280,
            dnsPrimary = "https://1.1.1.1/dns-query",
            dnsSecondary = "8.8.8.8",
            preferIpv6 = false,
            adviceFa = "شاتل موبایل معمولاً با Reality، XHTTP و آیپی تمیز کلودفلر با Fragment متوسط نتیجه خوبی می‌دهد.",
            warningsFa = "در صورت قطعی، سرور جایگزین SSH یا Psiphon را امتحان کنید."
        ),
        NetworkType.TD_LTE to IspPreset(
            networkType = NetworkType.TD_LTE,
            recommendedProtocols = listOf(
                ProtocolType.VLESS,
                ProtocolType.TROJAN,
                ProtocolType.LANTERN_STEALTH,
                ProtocolType.AETHER_WARP,
                ProtocolType.TOR
            ),
            preferredCdn = CdnProvider.CLOUDFLARE,
            fragmentEnabled = true,
            fragmentPackets = "tlshello",
            fragmentLength = "8-20",
            fragmentInterval = "10-25",
            noiseEnabled = true,
            scannerGentle = true,
            scannerAntiDpi = true,
            mtu = 1260,
            dnsPrimary = "https://dns.google/dns-query",
            dnsSecondary = "1.1.1.1",
            preferIpv6 = false,
            adviceFa = "مودم‌های TD-LTE (ایرانسل، مبین‌نت، زیتل) گاهی روی MTU حساس هستند و بسته‌های بزرگ TLS را دراپ می‌کنند. تنظیم MTU روی 1260 به همراه Fragment کوچک (`8-20`) یا موتور محلی SNI Spoof بسیار مؤثر است.",
            warningsFa = "در پنل مودم TD-LTE گزینه IPv6 را غیرفعال کرده و در صورت امکان DNS مودم را دستی تغییر ندهید (PNI خودش DoH امن را در تونل ست می‌کند)."
        ),
        NetworkType.ADSL_VDSL to IspPreset(
            networkType = NetworkType.ADSL_VDSL,
            recommendedProtocols = listOf(
                ProtocolType.VLESS,
                ProtocolType.LANTERN_STEALTH,
                ProtocolType.SSH,
                ProtocolType.PSIPHON,
                ProtocolType.TOR
            ),
            preferredCdn = CdnProvider.AKAMAI,
            fragmentEnabled = true,
            fragmentPackets = "1-3",
            fragmentLength = "5-15",
            fragmentInterval = "10-20",
            noiseEnabled = false,
            scannerGentle = true,
            scannerAntiDpi = true,
            mtu = 1280,
            dnsPrimary = "https://1.1.1.1/dns-query",
            dnsSecondary = "9.9.9.9",
            preferIpv6 = false,
            adviceFa = "روی خطوط ثابت مخابرات و ADSL، فیلترینگ SNI و مسدودسازی UDP (QUIC/WireGuard) رایج‌تر است. از TCP/WS + Fragment (`1-3` و طول `5-15`)، آیپی تمیز Akamai/Cloudflare، یا پروتکل‌های ضدسانسور Lantern/Outline و Tor WebTunnel استفاده کنید.",
            warningsFa = "برای مخابرات، اسکنر را حتماً در حالت Gentle بگذارید تا مودم ریست یا پورت موقتاً محدود نشود."
        ),
        NetworkType.FIBER to IspPreset(
            networkType = NetworkType.FIBER,
            recommendedProtocols = listOf(
                ProtocolType.VLESS,
                ProtocolType.HYSTERIA2,
                ProtocolType.TUIC,
                ProtocolType.AETHER_MASQUE
            ),
            preferredCdn = CdnProvider.CLOUDFLARE,
            fragmentEnabled = true,
            fragmentPackets = "tlshello",
            fragmentLength = "20-60",
            fragmentInterval = "5-10",
            noiseEnabled = true,
            scannerGentle = false,
            scannerAntiDpi = true,
            mtu = 1380,
            dnsPrimary = "https://1.1.1.1/dns-query",
            dnsSecondary = "8.8.8.8",
            preferIpv6 = true,
            adviceFa = "روی فیبر نوری (FTTH)، تاخیر بسیار پایین است و می‌توان از Reality، Hysteria2، TUIC و Worker کلودفلر با حداکثر سرعت بهره برد.",
            warningsFa = "در صورت استفاده از فیبر مخابرات، در ساعات اوج ممکن است کاهش سرعت UDP رخ دهد که در آن زمان VLESS+WS/XHTTP+Fragment جایگزین بهتری است."
        )
    )

    fun get(type: NetworkType): IspPreset = all[type] ?: all.getValue(NetworkType.MCI)
}
