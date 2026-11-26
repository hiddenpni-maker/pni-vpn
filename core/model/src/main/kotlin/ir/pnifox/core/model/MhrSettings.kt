package ir.pnifox.core.model

import kotlinx.serialization.Serializable

@Serializable
enum class MhrMode(
    val wireValue: String,
    val labelEn: String,
    val labelFa: String,
    val descEn: String,
    val descFa: String,
) {
    APPS_SCRIPT(
        wireValue = "apps_script",
        labelEn = "Apps Script (MITM)",
        labelFa = "اپس‌اسکریپت (MITM)",
        descEn = "Full DPI bypass through your deployed Google Apps Script relay (requires CA cert + Deployment ID).",
        descFa = "عبور کامل از فیلترینگ با رله شخصی Google Apps Script (نیاز به گواهی CA و شناسه Deployment).",
    ),
    DIRECT(
        wireValue = "direct",
        labelEn = "Direct (SNI Front)",
        labelFa = "مستقیم (بدون رله)",
        descEn = "SNI front without Apps Script relay — ideal bootstrap to open script.google.com and deploy Code.gs.",
        descFa = "بدون رله اپس‌اسکریپت — مناسب برای باز کردن script.google.com و ساخت رله شخصی.",
    ),
    FULL(
        wireValue = "full",
        labelEn = "Full Tunnel (No Cert)",
        labelFa = "تونل کامل (بدون گواهی)",
        descEn = "All traffic tunneled end-to-end via CodeFull.gs Apps Script without MITM certificate installation.",
        descFa = "عبور تمام ترافیک از طریق رله کامل CodeFull.gs بدون نیاز به نصب گواهی CA.",
    ),
}

object MhrDefaultSniPool {
    val defaults: List<String> = listOf(
        "www.google.com",
        "mail.google.com",
        "drive.google.com",
        "docs.google.com",
        "calendar.google.com",
        "accounts.google.com",
        "scholar.google.com",
        "maps.google.com",
        "chat.google.com",
        "translate.google.com",
        "play.google.com",
        "lens.google.com",
        "chromewebstore.google.com",
    )

    fun extractDeploymentId(input: String): String {
        var s = input.trim()
        if (s.isEmpty()) return s
        val marker = "/macros/s/"
        val idx = s.indexOf(marker)
        if (idx >= 0) s = s.substring(idx + marker.length)
        val slash = s.indexOf('/')
        if (slash >= 0) s = s.substring(0, slash)
        val q = s.indexOf('?')
        if (q >= 0) s = s.substring(0, q)
        return s.trim()
    }
}

@Serializable
data class MhrSettings(
    val mode: MhrMode = MhrMode.APPS_SCRIPT,
    val scriptIds: List<String> = emptyList(),
    val authKey: String = "",
    val frontDomain: String = "www.google.com",
    val sniHosts: List<String> = MhrDefaultSniPool.defaults,
    val googleIp: String = "216.239.38.120",
    val listenPort: Int = 8085,
    val socks5Port: Int = 8086,
    val shareOnLan: Boolean = false,
    val proxyOnly: Boolean = false,
    val parallelRelay: Int = 2,
    val verifySsl: Boolean = true,
    val forceHttp1: Boolean = false,
    val blockQuic: Boolean = true,
    val blockStun: Boolean = true,
    val tunnelDoh: Boolean = true,
    val blockDoh: Boolean = true,
    val youtubeViaRelay: Boolean = false,
    val upstreamSocks5: String = "",
    val passthroughHosts: List<String> = emptyList(),
    val bypassDohHosts: List<String> = emptyList(),
    val logLevel: String = "info",
)

data class MhrStatsSnapshot(
    val todayCalls: Long = 0L,
    val todayBytes: Long = 0L,
    val todayKey: String = "",
    val todayResetSecs: Long = 0L,
    val freeQuotaPerDay: Long = 20_000L,
)

data class MhrSniTestResult(
    val host: String,
    val ok: Boolean,
    val latencyMs: Long = 0L,
    val status: String = "",
    val error: String = "",
)
