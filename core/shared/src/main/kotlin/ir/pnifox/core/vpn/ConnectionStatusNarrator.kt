package ir.pnifox.core.vpn

import ir.pnifox.core.model.TunnelState

/**
 * Maps real-time core log events and tunnel states into lively, friendly, and accurate
 * Persian/English status messages so users always know what is happening under the hood.
 */
object ConnectionStatusNarrator {

    data class FunStage(
        val en: String,
        val fa: String,
    )

    fun fromLogLine(line: String): FunStage? {
        val lower = line.lowercase()
        return when {
            lower.contains("tryDatastoreOpenDB".lowercase()) || lower.contains("psiphon stopped before it was ready") ->
                FunStage(
                    en = "Psiphon cache got stuck — cleaning & retrying! 🔧",
                    fa = "سایفون قفل کرده بود، کش رو پاک کردم دوباره می‌زنم 🔧",
                )
            lower.contains("scan deadline reached") || lower.contains("no usable masque gateway") || lower.contains("no clean endpoint found") ->
                FunStage(
                    en = "Oops, no clean gateway replied — rescanning! 😅",
                    fa = "ای وای این مسیر جواب نداد! داره دوباره اسکن می‌کنه 😅",
                )
            lower.contains("hunting for") || lower.contains("scan mode=") ->
                FunStage(
                    en = "Hunting for the cleanest Cloudflare IP... 🔍",
                    fa = "داره دنبال تمیزترین آی‌پی می‌گرده، یه لحظه... 🔍",
                )
            lower.contains("candidate ok") ->
                FunStage(
                    en = "Found a fast IP candidate, checking ping... ⚡",
                    fa = "یه آی‌پی باحال پیدا شد، داره پینگش رو می‌سنجـه ⚡",
                )
            lower.contains("best gateway") || lower.contains("selected masque") || lower.contains("selected wireguard") || lower.contains("using forced peer") ->
                FunStage(
                    en = "Locked onto the best IP — almost there! 🎯",
                    fa = "بهترین آی‌پی انتخاب شد، الان وصل میشه! 🎯",
                )
            lower.contains("tls established") || lower.contains("connect-ip request sent") ->
                FunStage(
                    en = "TLS handshake OK — opening tunnel... 🤝",
                    fa = "دست‌دهی امن اوکی شد، صبور باش داره وصل میشه... 🤝",
                )
            lower.contains("connect-ip status: 200") || lower.contains("validating data-plane") ->
                FunStage(
                    en = "Tunnel responded 200 OK — final check... 🚀",
                    fa = "تونل جواب داد! تست نهایی، الان وصل میشه... 🚀",
                )
            lower.contains("tunnel validated") || lower.contains("socks5 server listening") ->
                FunStage(
                    en = "Tunnel verified & listening — finalizing! ✨",
                    fa = "تونل تایید شد، آخرای کاره... ✨",
                )
            lower.contains("psiphon is waiting") ->
                FunStage(
                    en = "WARP warming up, Psiphon on standby... ⏳",
                    fa = "وارپ داره آماده میشه، سایفون تو نوبته... ⏳",
                )
            lower.contains("starting psiphon") ->
                FunStage(
                    en = "Chaining with Psiphon — hold tight! 🔗",
                    fa = "الان نوبت چین با سایفونه، یه کوچولو صبر کن... 🔗",
                )
            lower.contains("psiphon reached a server") || lower.contains("psiphon is ready") ->
                FunStage(
                    en = "Psiphon locked in — ready to roll! 🎉",
                    fa = "سایفون قفل کرد! الان وصل میشه 🎉",
                )
            lower.contains("bootstrapping tor") ->
                FunStage(
                    en = "Spinning up Tor relays... 🧅",
                    fa = "داره مسیر تور رو می‌سازه، یه کم صبر کن... 🧅",
                )
            lower.contains("disconnecting tunnel") ->
                FunStage(
                    en = "Disconnecting cleanly... 👋",
                    fa = "داره قطع می‌کنه... فعلاً خداحافظ 👋",
                )
            else -> null
        }
    }

    fun headlineForState(state: TunnelState, isFa: Boolean, latestLogLine: String = ""): String {
        val fromLog = if (state is TunnelState.Connecting && latestLogLine.isNotBlank()) {
            fromLogLine(latestLogLine)
        } else {
            null
        }
        return when (state) {
            is TunnelState.Connecting -> {
                if (fromLog != null) {
                    if (isFa) fromLog.fa else fromLog.en
                } else {
                    if (isFa) state.stageLabelFa.ifBlank { "صبور باش داره وصل میشه... 🚀" }
                    else state.stageLabelEn.ifBlank { "Hang tight, connecting... 🚀" }
                }
            }
            is TunnelState.Connected -> {
                if (isFa) "بوم! وصل شدی، اینترنت آزاد شد 🎉"
                else "Boom! Connected & ready 🎉"
            }
            is TunnelState.Error -> {
                if (isFa) "ای وای ارور داد! 😅 ${state.error.titleFa}"
                else "Oops, hit a snag! 😅 ${state.error.titleEn}"
            }
            TunnelState.Disconnected -> {
                if (isFa) "آماده پرواز! دکمه اتصال رو بزن 🐾"
                else "Ready to roll — tap Connect 🐾"
            }
        }
    }
}
