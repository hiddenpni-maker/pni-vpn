package ir.pnifox.core.model

enum class LogLevel { INFO, WARN, ERROR }

data class LogEntry(
    val timestamp: String,
    val tag: String,
    val level: LogLevel,
    val message: String,
)

enum class ParsedRuleKind {
    IP_OR_CIDR,
    DOMAIN_SUFFIX,
    DOMAIN_EXACT,
    DOMAIN_KEYWORD,
    DOMAIN_REGEX,
}

data class ParsedRuleEntry(
    val kind: ParsedRuleKind,
    val value: String,
    val rawToken: String,
)

/**
 * Shared curated rulesets & custom rule token parser for Xray, Sing-box, and AETHER routing engines.
 */
object PnifoxRulesetsCatalog {

    val privateCidrs: List<String> = listOf(
        "10.0.0.0/8",
        "172.16.0.0/12",
        "192.168.0.0/16",
        "127.0.0.0/8",
        "169.254.0.0/16",
        "100.64.0.0/10",
    )

    val iranPopularNonIrDomains: List<String> = listOf(
        "digikala.com",
        "basalam.com",
        "aparat.com",
        "telewebion.com",
        "filimo.com",
        "tamasha.com",
        "eitaa.com",
        "bale.ai",
        "rubika.com",
        "neshan.org",
        "torob.com",
        "sheypoor.com",
        "blubank.com",
        "taaghche.com",
        "fidibo.com",
        "varzesh3.com",
        "digiato.com",
        "zoomit.com",
        "shahrekhabar.com",
        "khabaronline.ir",
        "shaparak.com",
        "sb24.com",
        "bankmellat.com",
        "mellatbank.com",
    )

    val adBlockDomains: List<String> = listOf(
        "doubleclick.net",
        "googlesyndication.com",
        "googleadservices.com",
        "adservice.google.com",
        "pagead2.googlesyndication.com",
        "app-measurement.com",
        "google-analytics.com",
        "analytics.google.com",
        "yektanet.com",
        "adivery.com",
        "tapsell.ir",
        "applovin.com",
        "applvn.com",
        "unityads.unity3d.com",
        "vungle.com",
        "ironsrc.com",
        "is.com",
        "adjust.com",
        "appsflyer.com",
        "taboola.com",
        "outbrain.com",
        "criteo.com",
        "pubmatic.com",
        "adnxs.com",
        "rubiconproject.com",
        "moatads.com",
        "adsrvr.org",
        "bidswitch.net",
        "scorecardresearch.com",
    )

    val adBlockKeywords: List<String> = listOf(
        "doubleclick",
        "googlesyndication",
        "googleadservices",
        "adservice",
    )

    fun parseCustomRules(rawMultiline: String): List<ParsedRuleEntry> {
        if (rawMultiline.isBlank()) return emptyList()
        return rawMultiline
            .replace(',', '\n')
            .lineSequence()
            .map { it.substringBefore('#').trim() }
            .filter { it.isNotBlank() }
            .mapNotNull(::classifyRuleToken)
            .distinctBy { it.rawToken }
            .toList()
    }

    private fun classifyRuleToken(token: String): ParsedRuleEntry? {
        val clean = token
            .removePrefix("https://")
            .removePrefix("http://")
            .substringBefore('/')
            .trim()
        if (clean.isBlank()) return null

        return when {
            clean.startsWith("full:", ignoreCase = true) -> {
                val v = clean.substringAfter(':').trim().lowercase()
                if (v.isBlank()) null else ParsedRuleEntry(ParsedRuleKind.DOMAIN_EXACT, v, "full:$v")
            }
            clean.startsWith("keyword:", ignoreCase = true) -> {
                val v = clean.substringAfter(':').trim().lowercase()
                if (v.isBlank()) null else ParsedRuleEntry(ParsedRuleKind.DOMAIN_KEYWORD, v, "keyword:$v")
            }
            clean.startsWith("regexp:", ignoreCase = true) -> {
                val v = clean.substringAfter(':').trim()
                if (v.isBlank()) null else ParsedRuleEntry(ParsedRuleKind.DOMAIN_REGEX, v, "regexp:$v")
            }
            clean.startsWith("domain:", ignoreCase = true) -> {
                val v = clean.substringAfter(':').trim().removePrefix(".").lowercase()
                if (v.isBlank()) null else ParsedRuleEntry(ParsedRuleKind.DOMAIN_SUFFIX, v, v)
            }
            isIpOrCidr(clean) -> ParsedRuleEntry(ParsedRuleKind.IP_OR_CIDR, clean, clean)
            else -> {
                val v = clean.removePrefix("*.").removePrefix(".").lowercase()
                if (v.isBlank()) null else ParsedRuleEntry(ParsedRuleKind.DOMAIN_SUFFIX, v, v)
            }
        }
    }

    private val ipv4OrCidrRegex = Regex("""^\d{1,3}(?:\.\d{1,3}){3}(?:/\d{1,2})?$""")

    private fun isIpOrCidr(token: String): Boolean {
        if (ipv4OrCidrRegex.matches(token)) return true
        // IPv6 address or CIDR
        if (token.contains(':') && !token.startsWith("port:", ignoreCase = true)) return true
        return false
    }
}
