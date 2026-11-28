package ir.pnifox.core.model

import kotlinx.serialization.Serializable

@Serializable
enum class AppLanguage(val code: String, val displayLabel: String) {
    ENGLISH("en", "English"),
    PERSIAN("fa", "فارسی"),
}

@Serializable
enum class ActiveSectionEngine(val titleEn: String, val titleFa: String) {
    AETHER("Unblock (MASQUE + Psiphon)", "رفع فیلتر"),
    GAMING("Gaming & Low Ping", "گیم و پینگ پایین"),
    SERVER("Custom Server (Xray / Sing-box)", "سرور دلخواه"),
    DNS("DNS Tunnel (DNSTT)", "تونل DNS"),
    MHR("MasterHttpRelay (mhrv-rs)", "رله MHR (مستر رله)"),
}

@Serializable
enum class ConnectionMode(val labelEn: String, val labelFa: String) {
    VPN("VPN mode", "حالت VPN"),
    PROXY("Proxy mode", "حالت پروکسی"),
}

@Serializable
enum class TransportMode(val labelEn: String, val labelFa: String) {
    NORMAL("Normal", "عادی"),
    FRAGMENT("Fragment", "فرگمنت ضدتحریم"),
}

@Serializable
enum class ExitNode(val labelEn: String, val labelFa: String, val badge: String) {
    DEFAULT("Default", "پیش‌فرض", "TCP · UDP"),
    PSIPHON("Psiphon", "سایفون (ضدتحریم)", "TCP · UDP"),
    TOR("Tor", "شبکه تور", "TCP"),
}

@Serializable
enum class CoreEngine(val displayEn: String, val displayFa: String, val binaryName: String?) {
    AETHER("Unblock Core", "موتور رفع فیلتر", "libaether.so"),
    XRAY("Xray-core", "هسته Xray", "libxray.so"),
    SING_BOX("Sing-box Core", "هسته Sing-box", "libpnisingbox.so"),
    PSIPHON("Psiphon", "سایفون", "libpsiphon.so"),
    TOR("Tor Network", "شبکه تور", "libtor.so"),
    DNSTT("DNSTT Tunnel", "تونل DNSTT", "libdnstt.so"),
    MHR("MasterHttpRelay", "هسته MHR", "libmhrv_rs.so"),
    LOCAL_DESYNC("Local DPI Desync", "ضدDPI محلی (بدون سرور)", null),
    SERVERLESS("Serverless Fragment", "بدون سرور", null),
}

@Serializable
enum class OneTapPreset(
    val titleEn: String,
    val titleFa: String,
    val subtitleEn: String,
    val subtitleFa: String,
    val isChain: Boolean = false,
) {
    WARP_FAST_MASQUE_H2(
        titleEn = "Fast WARP (MASQUE H2)",
        titleFa = "WARP سریع (Masque HTTP/2)",
        subtitleEn = "Low latency HTTP/2 MASQUE over Cloudflare edge",
        subtitleFa = "پینگ پایین • مناسب مرور و استریم",
    ),
    WARP_TO_PSIPHON(
        titleEn = "WARP → Psiphon (Anti-Sanction)",
        titleFa = "WARP → Psiphon (ضدتحریم)",
        subtitleEn = "Chained WARP exit through Psiphon for bypass & region unlock",
        subtitleFa = "تغییر IP و عبور از تحریم و فیلترینگ شدید",
        isChain = true,
    ),
    PSIPHON_AKAMAI_CDN(
        titleEn = "Akamai / CDN Fronting (Shir o Khorshid)",
        titleFa = "فرانتینگ Akamai / CDN (شیر و خورشید)",
        subtitleEn = "Standalone Psiphon CDN fronting with Akamai edge IPs & custom SNIs",
        subtitleFa = "اتصال با آی‌پی لبه اکامای / CDN و SNI دلخواه بدون نیاز به WARP",
        isChain = true,
    ),
    WG_OVER_MASQUE(
        titleEn = "WireGuard over MASQUE (--gool)",
        titleFa = "WireGuard روی MASQUE",
        subtitleEn = "Dual-hop WireGuard carried inside MASQUE tunnel",
        subtitleFa = "تونل تو در تو با IP خروجی متفاوت",
        isChain = true,
    ),
    MASQUE_IN_MASQUE(
        titleEn = "MASQUE-in-MASQUE (--mim)",
        titleFa = "MASQUE-in-MASQUE",
        subtitleEn = "Nested MASQUE hops for altered exit IP",
        subtitleFa = "دو لایه MASQUE برای تغییر آی‌پی",
        isChain = true,
    ),
    WARP_TO_TOR(
        titleEn = "WARP → Tor Exit",
        titleFa = "WARP → تور (Tor)",
        subtitleEn = "Tor anonymity routed cleanly inside WARP tunnel",
        subtitleFa = "مسیریابی تور از داخل تونل وارپ",
        isChain = true,
    ),
}

@Serializable
enum class ProxyProtocol(val scheme: String, val displayName: String) {
    VLESS("vless", "VLESS"),
    VMESS("vmess", "VMess"),
    TROJAN("trojan", "Trojan"),
    SHADOWSOCKS("ss", "Shadowsocks"),
    HYSTERIA2("hy2", "Hysteria2"),
    TUIC("tuic", "TUIC v5"),
    ANYTLS("anytls", "AnyTLS"),
    WIREGUARD("wg", "WireGuard"),
    MASQUE("masque", "MASQUE"),
}

enum class RecoveryAction(val labelEn: String, val labelFa: String) {
    ENABLE_FRAGMENT("Enable Fragment", "روشن کردن Fragment"),
    OPEN_CLOUDFLARE_SCANNER("Open IP Scanner", "اسکنر IP کلودفلر"),
    SWITCH_WARP_PSIPHON("Switch to WARP → Psiphon", "سوییچ به WARP → Psiphon"),
    TRY_DNS_TUNNEL("Try DNS Tunnel", "اتصال اضطراری DNS"),
    RENEW_WARP_KEYS("Generate WARP Keys", "ساخت کلید WARP"),
    OPEN_LOGS("View Logs", "مشاهده لاگ خطا"),
}

@Serializable
sealed interface ConnectionError {
    val titleEn: String
    val titleFa: String
    val reasonEn: String
    val reasonFa: String
    val suggestions: List<RecoveryAction>

    @Serializable
    data class MissingBinary(
        val binaryName: String,
        val expectedAbiPath: String,
        val buildHint: String,
    ) : ConnectionError {
        override val titleEn: String = "Native Core Binary Missing"
        override val titleFa: String = "فایل هسته بومی موجود نیست"
        override val reasonEn: String =
            "Executable '$binaryName' was not found in '$expectedAbiPath'. Real tunnel execution requires native binaries packaged in jniLibs."
        override val reasonFa: String =
            "فایل اجرایی «$binaryName» در مسیر «$expectedAbiPath» یافت نشد. برای اتصال واقعی هسته‌های بومی باید در jniLibs قرار بگیرند."
        override val suggestions: List<RecoveryAction> = listOf(RecoveryAction.OPEN_LOGS)
    }

    @Serializable
    data class PermissionDenied(
        override val titleEn: String = "VPN Permission Required",
        override val titleFa: String = "مجوز VPN رد شد",
        override val reasonEn: String = "Android VPN permission was not granted.",
        override val reasonFa: String = "مجوز ساخت تونل VPN در اندروید تایید نشد.",
    ) : ConnectionError {
        override val suggestions: List<RecoveryAction> = emptyList()
    }

    @Serializable
    data class HandshakeTimeout(
        override val reasonEn: String = "Local SOCKS5 listener on 127.0.0.1:10820 did not become ready within timeout.",
        override val reasonFa: String = "پاسخ TLS/SNI یا پورت محلی 10820 در زمان مقرر آماده نشد.",
        override val suggestions: List<RecoveryAction> = listOf(
            RecoveryAction.ENABLE_FRAGMENT,
            RecoveryAction.OPEN_CLOUDFLARE_SCANNER,
            RecoveryAction.SWITCH_WARP_PSIPHON,
        ),
    ) : ConnectionError {
        override val titleEn: String = "Connection Timed Out"
        override val titleFa: String = "عدم پاسخ‌گویی سرور"
    }

    @Serializable
    data class DnsBlocked(
        override val reasonEn: String = "DNS or Port 53 appears throttled or blocked on this network.",
        override val reasonFa: String = "پاسخ‌گویی DNS یا پورت ۵۳ توسط شبکه مسدود یا مختل شده است.",
        override val suggestions: List<RecoveryAction> = listOf(
            RecoveryAction.TRY_DNS_TUNNEL,
            RecoveryAction.SWITCH_WARP_PSIPHON,
            RecoveryAction.OPEN_LOGS,
        ),
    ) : ConnectionError {
        override val titleEn: String = "DNS Interference Detected"
        override val titleFa: String = "اختلال در DNS شبکه"
    }

    @Serializable
    data class ProcessExited(
        val exitCode: Int,
        val lastLogLine: String,
        override val reasonEn: String = "Core process exited with code $exitCode: $lastLogLine",
        override val reasonFa: String = "موتور اتصال با کد خطای $exitCode متوقف شد: $lastLogLine",
        override val suggestions: List<RecoveryAction> = listOf(
            RecoveryAction.RENEW_WARP_KEYS,
            RecoveryAction.ENABLE_FRAGMENT,
            RecoveryAction.OPEN_LOGS,
        ),
    ) : ConnectionError {
        override val titleEn: String = "Core Stopped Unexpectedly"
        override val titleFa: String = "توقف غیرمنتظره هسته"
    }
}

sealed interface TunnelState {
    data object Disconnected : TunnelState

    data class Connecting(
        val section: ActiveSectionEngine,
        val stageLabelEn: String,
        val stageLabelFa: String,
        val targetLabel: String,
    ) : TunnelState

    data class Connected(
        val section: ActiveSectionEngine,
        val engine: CoreEngine,
        val localSocksPort: Int = 10820,
        val activeEndpoint: String,
        val resolvedIp: String,
        val pingMs: Int?,
        val connectedAtEpochMs: Long,
        val exitIp: String = "",
        val exitLocation: String = "",
        val activeDnsSummary: String = "",
        val networkChangeSummary: String = "",
    ) : TunnelState

    data class Error(
        val section: ActiveSectionEngine,
        val error: ConnectionError,
    ) : TunnelState
}
