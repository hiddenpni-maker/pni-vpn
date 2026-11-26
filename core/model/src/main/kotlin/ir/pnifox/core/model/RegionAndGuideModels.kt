package ir.pnifox.core.model

data class RegionOption(
    val code: String,
    val flag: String,
    val nameEn: String,
    val nameFa: String,
)

data class UsageGuideCard(
    val badgeEn: String,
    val badgeFa: String,
    val titleEn: String,
    val titleFa: String,
    val whenToUseEn: String,
    val whenToUseFa: String,
    val howToUseEn: String,
    val howToUseFa: String,
)

object PnifoxRegionsCatalog {
    val regions: List<RegionOption> = listOf(
        RegionOption("AUTO", "⚡", "Fastest (Any Country)", "خودکار (سریع‌ترین)"),
        RegionOption("DE", "🇩🇪", "Germany (DE)", "آلمان (DE)"),
        RegionOption("NL", "🇳🇱", "Netherlands (NL)", "هلند (NL)"),
        RegionOption("GB", "🇬🇧", "United Kingdom (GB)", "انگلستان (GB)"),
        RegionOption("US", "🇺🇸", "United States (US)", "آمریکا (US)"),
        RegionOption("FR", "🇫🇷", "France (FR)", "فرانسه (FR)"),
        RegionOption("AT", "🇦🇹", "Austria (AT)", "اتریش (AT)"),
        RegionOption("CH", "🇨🇭", "Switzerland (CH)", "سوئیس (CH)"),
        RegionOption("FI", "🇫🇮", "Finland (FI)", "فنلاند (FI)"),
        RegionOption("SE", "🇸🇪", "Sweden (SE)", "سوئد (SE)"),
        RegionOption("NO", "🇳🇴", "Norway (NO)", "نروژ (NO)"),
        RegionOption("DK", "🇩🇰", "Denmark (DK)", "دانمارک (DK)"),
        RegionOption("BE", "🇧🇪", "Belgium (BE)", "بلژیک (BE)"),
        RegionOption("CA", "🇨🇦", "Canada (CA)", "کانادا (CA)"),
        RegionOption("AU", "🇦🇺", "Australia (AU)", "استرالیا (AU)"),
        RegionOption("JP", "🇯🇵", "Japan (JP)", "ژاپن (JP)"),
        RegionOption("SG", "🇸🇬", "Singapore (SG)", "سنگاپور (SG)"),
        RegionOption("IT", "🇮🇹", "Italy (IT)", "ایتالیا (IT)"),
        RegionOption("ES", "🇪🇸", "Spain (ES)", "اسپانیا (ES)"),
        RegionOption("PL", "🇵🇱", "Poland (PL)", "لهستان (PL)"),
        RegionOption("CZ", "🇨🇿", "Czechia (CZ)", "جمهوری چک (CZ)"),
        RegionOption("RO", "🇷🇴", "Romania (RO)", "رومانی (RO)"),
        RegionOption("BG", "🇧🇬", "Bulgaria (BG)", "بلغارستان (BG)"),
        RegionOption("EE", "🇪🇪", "Estonia (EE)", "استونی (EE)"),
        RegionOption("LT", "🇱🇹", "Lithuania (LT)", "لیتوانی (LT)"),
        RegionOption("IE", "🇮🇪", "Ireland (IE)", "ایرلند (IE)"),
        RegionOption("BR", "🇧🇷", "Brazil (BR)", "برزیل (BR)"),
        RegionOption("IN", "🇮🇳", "India (IN)", "هند (IN)"),
        RegionOption("ID", "🇮🇩", "Indonesia (ID)", "اندونزی (ID)"),
        RegionOption("RS", "🇷🇸", "Serbia (RS)", "صربستان (RS)"),
    )

    fun findByCode(code: String): RegionOption =
        regions.firstOrNull { it.code.equals(code.trim(), ignoreCase = true) } ?: regions.first()
}

object PnifoxUsageGuideCatalog {
    val cards: List<UsageGuideCard> = listOf(
        UsageGuideCard(
            badgeEn = "START HERE",
            badgeFa = "شروع سریع در ۳ قدم",
            titleEn = "How to Connect in 3 Easy Steps",
            titleFa = "چطور خیلی ساده به برنامه وصل شویم؟",
            whenToUseEn = "First time using pnifox on Wi-Fi or mobile data (Irancell / MCI).",
            whenToUseFa = "مناسب شروع کار روی اینترنت موبایل (ایرانسل / همراه اول) و وای‌فای.",
            howToUseEn = "1) In Step 1 tap 'Generate WARP Keys (all)' once until all badges turn green.\n" +
                "2) Tap the red Connect button: on the first connect it scans & saves the best Cloudflare IP automatically.\n" +
                "3) Every future connect reuses that saved endpoint in seconds without re-scanning unless you tap Re-scan.",
            howToUseFa = "۱) در مرحله ۱ فقط یک بار روی «ساخت کلید WARP (all)» بزنید تا وضعیت‌ها سبز شوند.\n" +
                "۲) دکمه قرمز اتصال را بزنید: بار اول بهترین اندپوینت کلودفلر پیدا و ذخیره می‌شود.\n" +
                "۳) از دفعات بعدی بدون اسکن مجدد در چند ثانیه وصل می‌شوید (فقط اگر خط عوض شد یا سرعت افت کرد، دکمه «اسکن مجدد» را بزنید).",
        ),
        UsageGuideCard(
            badgeEn = "GAMING & LOW PING",
            badgeFa = "بهترین حالت برای گیم و پینگ پایین",
            titleEn = "Fast WARP (MASQUE HTTP/2) / WireGuard",
            titleFa = "حالت گیم، استریم و وب‌گردی پرسرعت",
            whenToUseEn = "Best for online gaming (Call of Duty, PUBG, Valorant), Telegram, Instagram, and lowest latency.",
            whenToUseFa = "عالی برای بازی آنلاین (پابجی، کال‌آف، فیفا)، تلگرام، یوتیوب و پایین‌ترین پینگ.",
            howToUseEn = "Choose 'Fast WARP (MASQUE H2)' and keep Exit Node on Default (no Psiphon/Tor chain). For even lower ping, open Scanner -> run Cloudflare Scan -> tap Apply on a green S+/A IP with 0% Loss.",
            howToUseFa = "حالت «WARP سریع (Masque HTTP/2)» را انتخاب کنید و Psiphon/Tor را خاموش بگذارید تا پینگ بالا نرود. برای پینگ بهتر، در تب «اسکنر» یک آی‌پی سبز S+ یا A با Loss 0% پیدا کنید و روی Apply بزنید.",
        ),
        UsageGuideCard(
            badgeEn = "ANTI-SANCTION",
            badgeFa = "بهترین حالت برای رفع تحریم",
            titleEn = "WARP → Psiphon (Anti-Sanction) + Region Flag",
            titleFa = "حالت رفع تحریم سرویس‌ها و سایت‌های خارجی",
            whenToUseEn = "Best for ChatGPT, Gemini, Google Play, banking/developer tools, and blocked services where standard WARP shows an IR-tagged IP.",
            whenToUseFa = "عالی برای ChatGPT، هوش مصنوعی، گوگل‌پلی، اسپاتیفای، ابزارهای برنامه‌نویسی و سایت‌هایی که آی‌پی ایران را تحریم کرده‌اند.",
            howToUseEn = "Select 'WARP → Psiphon (Anti-Sanction)' and pick a country flag (e.g. Germany 🇩🇪, Netherlands 🇳🇱, United States 🇺🇸) in the Region selector. WARP bypasses local filtering while Psiphon gives you a clean foreign IP.",
            howToUseFa = "گزینه «WARP → Psiphon (ضدتحریم)» را بزنید و از بخش «ریجن / کشور خروجی» پرچم کشور دلخواه (مثل آلمان 🇩🇪، هلند 🇳🇱 یا آمریکا 🇺🇸) را انتخاب کنید. با این روش علاوه بر عبور از فیلترینگ، آی‌پی شما کاملاً خارجی می‌شود.",
        ),
        UsageGuideCard(
            badgeEn = "DOUBLE HOP",
            badgeFa = "تغییر IP بدون سایفون",
            titleEn = "WG over MASQUE (--gool) & MASQUE-in-MASQUE (--mim)",
            titleFa = "حالت دولایه وارپ (--gool و --mim)",
            whenToUseEn = "Use when you want a non-Iran Cloudflare exit IP faster than Psiphon/Tor.",
            whenToUseFa = "مناسب زمانی که می‌خواهید بدون کندی سایفون/تور، با سرعت بالای کلودفلر آی‌پی غیر از ایران داشته باشید.",
            howToUseEn = "One WARP hop tunnels inside another Cloudflare WARP hop so the outer edge sees clean foreign egress.",
            howToUseFa = "یک تونل وارپ از داخل تونل دوم عبور می‌کند تا هم سرعت کلودفلر حفظ شود و هم موقعیت آی‌پی تغییر کند.",
        ),
        UsageGuideCard(
            badgeEn = "CUSTOM & EMERGENCY",
            badgeFa = "سرور دلخواه و تونل اضطراری",
            titleEn = "Servers (Xray / Sing-box) & DNS Tunnel (DNSTT)",
            titleFa = "تب سرورها (VLESS/VMess) و تب DNS (شرایط بحرانی)",
            whenToUseEn = "For personal VPN servers/subscriptions OR strict firewall emergencies.",
            whenToUseFa = "مناسب کانفیگ‌های شخصی یا شرایط محدودیت شدید اینترنت.",
            howToUseEn = "• Servers Tab: Import your own VLESS/Reality/VMess/Trojan/Hysteria2 subscription for fixed-IP stability and heavy downloads.\n• DNS Tab: Emergency fallback using DNSTT when HTTPS/UDP protocols fail.",
            howToUseFa = "• تب سرورها: اگر کانفیگ شخصی VLESS / Reality / VMess / Trojan یا لینک ساب دارید از این تب وصل شوید (پایدارترین گزینه برای دانلود سنگین).\n• تب DNS (سبک AM Tunnel): فقط در شرایط اختلال شدید شبکه که هیچ روشی وصل نمی‌شود برای چت و پیام‌رسان استفاده کنید.",
        ),
        UsageGuideCard(
            badgeEn = "TROUBLESHOOTING",
            badgeFa = "نکات مهم رفع خطا",
            titleEn = "When to Use Fragment or Re-scan",
            titleFa = "چه زمانی Fragment روشن باشد یا اسکن مجدد بزنیم؟",
            whenToUseEn = "Quick fixes if handshakes drop or speed decreases on mobile/Wi-Fi.",
            whenToUseFa = "راه‌حل سریع در صورت خطای دست‌دهی TLS یا افت سرعت.",
            howToUseEn = "• Keep 'Fragment ClientHello' OFF if Cloudflare shows 'TLS unexpected EOF'. Turn it ON only on networks that drop normal SNIs.\n• On Servers tab port 8080/80 WS links without TLS, leave SNI Spoof OFF.\n• Switch from Wi-Fi to Irancell/MCI? Tap 'Re-scan Endpoint'.",
            howToUseFa = "• اگر در لاگ خطای «TLS handshake failed unexpected EOF» دیدید، حتماً گزینه Fragment را خاموش بگذارید (در برخی اینترنت‌ها پکت تکه شده بسته می‌شود).\n• در تب سرورها روی لینک‌های بدون TLS (مثل پورت 8080/80) تیک SNI Spoof را خاموش بگذارید.\n• هنگام تعویض وای‌فای به اینترنت سیم‌کارت، یک بار «اسکن مجدد» را بزنید.",
        ),
        UsageGuideCard(
            badgeEn = "HOTSPOT SHARE",
            badgeFa = "اشتراک به لپ‌تاپ/iOS",
            titleEn = "Share Phone VPN to Laptop & iOS (Hotspot / LAN)",
            titleFa = "اشتراک‌گذاری VPN گوشی روی لپ‌تاپ و آیفون (هات‌اسپات)",
            whenToUseEn = "Let Windows, macOS, Linux, or iOS bypass sanctions through your phone's active tunnel without installing extra VPN apps on them.",
            whenToUseFa = "بدون نصب وی‌پی‌ان روی لپ‌تاپ یا آیفون، اینترنت بدون تحریم گوشی را از طریق هات‌اسپات به اشتراک بگذارید.",
            howToUseEn = "1. Open Settings in Pnifox and turn ON 'Allow LAN'.\n" +
                "2. Turn ON Mobile Hotspot and connect your laptop or iPhone/iPad to it.\n" +
                "3. Connect VPN in Pnifox and note your Phone IP shown in Settings -> Hotspot Sharing (usually 192.168.43.1).\n" +
                "4. On iOS: Wi-Fi -> (i) -> Configure Proxy -> Manual -> Server = Phone IP, Port = 10821.\n" +
                "5. On Windows/macOS: Set HTTP Proxy to Phone IP : 10821 (or SOCKS5 to Phone IP : 10820).",
            howToUseFa = "۱. در بخش تنظیمات (Settings)، گزینه Allow LAN را روشن کنید.\n" +
                "۲. نقطه اتصال (Hotspot) گوشی را روشن کرده و لپ‌تاپ یا آیفون را به آن وصل کنید.\n" +
                "۳. VPN را در Pnifox وصل کنید و IP گوشی که در تنظیمات نمایش داده شده را ببینید (معمولاً 192.168.43.1).\n" +
                "۴. در آیفون (iOS): وارد Wi-Fi شوید <- آیکون (i) <- Configure Proxy <- حالت Manual <- آدرس سرور را آی‌پی گوشی و پورت را 10821 بزنید.\n" +
                "۵. در ویندوز یا مک: در تنظیمات Proxy، آدرس HTTP را روی آی‌پی گوشی و پورت 10821 (یا SOCKS5 روی پورت 10820) قرار دهید.",
        ),
        UsageGuideCard(
            badgeEn = "MHR RELAY",
            badgeFa = "روش MHR",
            titleEn = "MasterHttpRelay (Google Apps Script Tunnel)",
            titleFa = "آموزش راه‌اندازی روش MHR (مستر رله با گوگل اسکریپت)",
            whenToUseEn = "Disguises HTTPS traffic via Google fronting + your own Google Apps Script Web App to beat strict DPI filtering.",
            whenToUseFa = "عبور از فیلترینگ شدید با مخفی‌سازی SNI روی آی‌پی گوگل و رله شخصی Google Apps Script.",
            howToUseEn = "1. If script.google.com is blocked, connect MHR in Mode = 'Direct (SNI Front)' first as a bootstrap.\n" +
                "2. Open script.google.com -> New project -> paste Code.gs (or CodeFull.gs), set AUTH_KEY, Deploy -> Web app (Who has access: Anyone).\n" +
                "3. Paste the Web App URL / Deployment ID and AUTH_KEY in the MHR tab.\n" +
                "4. Tap 'MITM CA Cert' -> Export PEM -> open Android Settings -> search 'CA certificate' -> install from Downloads (not needed for Full Tunnel mode).\n" +
                "5. Switch Mode to 'Apps Script (MITM)', Auto-detect google_ip, and tap Connect!",
            howToUseFa = "۱. اگر سایت script.google.com فیلتر است، ابتدا در تب MHR حالت را روی Direct (بدون رله) بگذارید و وصل شوید.\n" +
                "۲. وارد script.google.com شوید <- New project بسازید <- کد Code.gs را جایگزین کنید، یک AUTH_KEY بگذارید و Deploy -> Web app (دسترسی Anyone) بزنید.\n" +
                "۳. لینک یا ID دیپلویمنت و AUTH_KEY را در تب MHR وارد و اضافه کنید.\n" +
                "۴. دکمه «نصب گواهی CA» را بزنید <- از تنظیمات گوشی عبارت CA certificate را جستجو و فایل را از Downloads نصب کنید (حالت Full بدون گواهی است).\n" +
                "۵. حالت را روی Apps Script (MITM) بگذارید و متصل شوید!",
        ),
    )
}
