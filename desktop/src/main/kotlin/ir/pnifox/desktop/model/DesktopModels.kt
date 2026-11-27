package ir.pnifox.desktop.model

import ir.pnifox.core.model.AetherSettings
import ir.pnifox.core.model.AppSettings
import ir.pnifox.core.model.DnsTunnelSettings
import ir.pnifox.core.model.GamingSettings
import ir.pnifox.core.model.MhrSettings
import ir.pnifox.core.model.ProxyProfile
import ir.pnifox.core.model.ScenarioGoalId
import ir.pnifox.core.model.SubscriptionGroup
import ir.pnifox.core.model.WarpKey
import kotlinx.serialization.Serializable
import java.io.File

@Serializable
enum class WindowsTunnelMode(
    val labelEn: String,
    val labelFa: String,
    val descEn: String,
    val descFa: String,
) {
    SYSTEM_PROXY(
        labelEn = "System Proxy (No Admin)",
        labelFa = "پروکسی سیستم (بدون نیاز به ادمین)",
        descEn = "Automatically sets Windows HTTP proxy (Chrome, Edge, Telegram, apps) without UAC.",
        descFa = "تنظیم خودکار پروکسی ویندوز (مرورگرها، تلگرام و برنامه‌ها) بدون نیاز به اجرای ادمین.",
    ),
    TUN_WINTUN(
        labelEn = "Full VPN / TUN Mode (Wintun)",
        labelFa = "حالت VPN کامل / TUN (مناسب گیم)",
        descEn = "Captures ALL Windows system & game traffic via Wintun adapter (requires Administrator).",
        descFa = "عبور تمام ترافیک ویندوز و بازی‌ها از کارت شبکه مجازی Wintun (نیازمند Run as Admin).",
    ),
}

enum class DesktopTab(
    val labelEn: String,
    val labelFa: String,
) {
    AETHER("AETHER (WARP)", "موتور AETHER"),
    GAMING("Gaming & Presets", "گیم و حالت‌ها"),
    SERVERS("Servers", "سرورها"),
    MHR("MHR Relay", "رله MHR"),
    DNS("DNS Tunnel", "تونل DNS"),
    SCANNER("CF Scanner", "اسکنر IP"),
    SETTINGS("Settings & Guide", "تنظیمات و راهنما"),
}

@Serializable
data class DesktopPersistedState(
    val appSettings: AppSettings = AppSettings(),
    val aetherSettings: AetherSettings = AetherSettings(),
    val gamingSettings: GamingSettings = GamingSettings(),
    val dnsSettings: DnsTunnelSettings = DnsTunnelSettings(),
    val mhrSettings: MhrSettings = MhrSettings(),
    val windowsTunnelMode: WindowsTunnelMode = WindowsTunnelMode.SYSTEM_PROXY,
    val selectedScenarioId: ScenarioGoalId? = null,
    val banditStateJson: String = "",
    val tunPromptDismissed: Boolean = false,
    val profiles: List<ProxyProfile> = emptyList(),
    val subscriptions: List<SubscriptionGroup> = emptyList(),
    val warpKeys: List<WarpKey> = emptyList(),
    val totalRxBytes: Long = 0L,
    val totalTxBytes: Long = 0L,
)

data class WindowsBinaryStatus(
    val name: String,
    val role: String,
    val path: String,
    val available: Boolean,
    val sizeBytes: Long,
) {
    val file: File get() = File(path)
}
