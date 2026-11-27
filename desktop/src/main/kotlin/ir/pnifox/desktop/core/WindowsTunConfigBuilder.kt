package ir.pnifox.desktop.core

import ir.pnifox.core.model.AppSettings
import ir.pnifox.core.model.ParsedRuleKind
import ir.pnifox.core.model.PnifoxRulesetsCatalog
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject

/**
 * Generates Windows `sing-box` 1.14+ configurations for both:
 * 1. Full VPN / Wintun Layer-3 TUN (`buildTunBridgeConfig`)
 * 2. Local System Proxy Dual-Stack Gateway (`buildSystemGatewayConfig`)
 * Both expose authenticated Clash API (`/traffic`) on `127.0.0.1:clashApiPort` so download/upload
 * speeds, totals, AdBlock, and Iran whitelist rulesets work identically across all engines & modes.
 */
object WindowsTunConfigBuilder {

    fun buildTunBridgeConfig(
        appSettings: AppSettings,
        socksPort: Int = 10818,
        clashApiPort: Int = 19090,
        clashSecret: String = "",
        bypassTargetIps: List<String> = emptyList(),
        dnsChangerOnly: Boolean = false,
        overrideResolver: String? = null,
    ): String {
        val excludeCidrs = linkedSetOf<String>()
        if (appSettings.bypassPrivateLanEnabled) {
            excludeCidrs += listOf(
                "10.0.0.0/8",
                "172.16.0.0/12",
                "192.168.0.0/16",
                "169.254.0.0/16",
            )
        }
        val ipv4Regex = Regex("""^\d{1,3}(\.\d{1,3}){3}$""")
        bypassTargetIps.map { it.trim() }.filter { ipv4Regex.matches(it) }.forEach { ip ->
            excludeCidrs += "$ip/32"
        }

        val routeRules = buildRouteRules(appSettings, bypassTargetIps, isTunMode = true)
        val inbounds = buildJsonArray {
            add(
                buildJsonObject {
                    put("type", "tun")
                    put("tag", "tun-in")
                    put("interface_name", "pnifox-tun")
                    putJsonArray("address") {
                        add(JsonPrimitive("172.19.0.1/30"))
                    }
                    put("mtu", appSettings.mtu.coerceIn(1280, 1500))
                    put("auto_route", true)
                    put("strict_route", false)
                    put("stack", "gvisor")
                    if (excludeCidrs.isNotEmpty()) {
                        putJsonArray("route_exclude_address") {
                            excludeCidrs.forEach { add(JsonPrimitive(it)) }
                        }
                    }
                },
            )
        }
        return assembleConfig(
            appSettings = appSettings,
            upstreamSocksPort = socksPort,
            clashApiPort = clashApiPort,
            clashSecret = clashSecret,
            inbounds = inbounds,
            routeRules = routeRules,
            isTunMode = true,
            dnsChangerOnly = dnsChangerOnly,
            overrideResolver = overrideResolver,
        )
    }

    fun buildSystemGatewayConfig(
        appSettings: AppSettings,
        socksListenPort: Int = 10820,
        httpListenPort: Int = 10821,
        upstreamSocksPort: Int = 10818,
        clashApiPort: Int = 19090,
        clashSecret: String = "",
        bypassTargetIps: List<String> = emptyList(),
        dnsChangerOnly: Boolean = false,
        overrideResolver: String? = null,
    ): String {
        val listenAddr = if (appSettings.allowLan) "0.0.0.0" else "127.0.0.1"
        val safeHttpPort = if (httpListenPort == socksListenPort) socksListenPort + 1 else httpListenPort
        val routeRules = buildRouteRules(appSettings, bypassTargetIps, isTunMode = false)
        val inbounds = buildJsonArray {
            add(
                buildJsonObject {
                    put("type", "socks")
                    put("tag", "socks-in")
                    put("listen", listenAddr)
                    put("listen_port", socksListenPort)
                },
            )
            add(
                buildJsonObject {
                    put("type", "http")
                    put("tag", "http-in")
                    put("listen", listenAddr)
                    put("listen_port", safeHttpPort)
                },
            )
        }
        return assembleConfig(
            appSettings = appSettings,
            upstreamSocksPort = upstreamSocksPort,
            clashApiPort = clashApiPort,
            clashSecret = clashSecret,
            inbounds = inbounds,
            routeRules = routeRules,
            isTunMode = false,
            dnsChangerOnly = dnsChangerOnly,
            overrideResolver = overrideResolver,
        )
    }

    private fun assembleConfig(
        appSettings: AppSettings,
        upstreamSocksPort: Int,
        clashApiPort: Int,
        clashSecret: String,
        inbounds: JsonArray,
        routeRules: List<JsonObject>,
        isTunMode: Boolean,
        dnsChangerOnly: Boolean = false,
        overrideResolver: String? = null,
    ): String {
        val resolvedDns = buildSingBoxDnsServer(
            raw = overrideResolver?.ifBlank { null } ?: appSettings.localDns,
            detourTag = if (dnsChangerOnly) null else "socks-out",
        )
        val root = buildJsonObject {
            putJsonObject("log") {
                put("level", "info")
            }
            putJsonObject("experimental") {
                putJsonObject("clash_api") {
                    put("external_controller", "127.0.0.1:$clashApiPort")
                    if (clashSecret.isNotBlank()) {
                        put("secret", clashSecret)
                    }
                }
            }
            putJsonObject("dns") {
                putJsonArray("servers") {
                    add(resolvedDns)
                    add(
                        buildJsonObject {
                            put("tag", "dns-direct")
                            put("type", "udp")
                            put("server", appSettings.antiSanctionDns.trim().substringBefore(':').ifBlank { "178.22.122.100" })
                        },
                    )
                }
                put("final", "dns-remote")
            }
            put("inbounds", inbounds)
            putJsonArray("outbounds") {
                add(
                    buildJsonObject {
                        put("type", "socks")
                        put("tag", "socks-out")
                        put("server", "127.0.0.1")
                        put("server_port", upstreamSocksPort)
                    },
                )
                add(
                    buildJsonObject {
                        put("type", "direct")
                        put("tag", "direct")
                    },
                )
            }
            putJsonObject("route") {
                if (isTunMode) {
                    put("auto_detect_interface", true)
                }
                put("default_domain_resolver", "dns-direct")
                put("rules", JsonArray(routeRules))
                put("final", if (dnsChangerOnly) "direct" else "socks-out")
            }
        }
        return root.toString()
    }

    private fun buildSingBoxDnsServer(raw: String, detourTag: String?): JsonObject {
        val clean = raw.trim().ifBlank { "1.1.1.1" }
        val (type, host, port) = when {
            clean.startsWith("https://", ignoreCase = true) -> {
                val u = clean.removePrefix("https://").substringBefore('/')
                Triple("https", u.substringBefore(':'), u.substringAfter(':', "").toIntOrNull() ?: 443)
            }
            clean.startsWith("quic://", ignoreCase = true) -> {
                val u = clean.removePrefix("quic://").substringBefore('/')
                Triple("quic", u.substringBefore(':'), u.substringAfter(':', "").toIntOrNull() ?: 853)
            }
            clean.startsWith("tls://", ignoreCase = true) -> {
                val u = clean.removePrefix("tls://").substringBefore('/')
                Triple("tls", u.substringBefore(':'), u.substringAfter(':', "").toIntOrNull() ?: 853)
            }
            else -> Triple(if (detourTag != null) "tcp" else "udp", clean.substringBefore(':'), clean.substringAfter(':', "").toIntOrNull() ?: 53)
        }
        return buildJsonObject {
            put("tag", "dns-remote")
            put("type", type)
            put("server", host)
            if (port != 53 && type == "udp") put("server_port", port)
            if (detourTag != null) put("detour", detourTag)
        }
    }

    private fun buildRouteRules(
        appSettings: AppSettings,
        bypassTargetIps: List<String>,
        isTunMode: Boolean,
    ): List<JsonObject> {
        val rules = mutableListOf<JsonObject>()
        rules += buildJsonObject { put("action", "sniff") }

        // CRITICAL: Bypass local core daemons & JVM process BEFORE hijack-dns so their own
        // upstream DNS/TLS/UDP sockets never loop back into WinTUN -> hijack-dns -> socks-out!
        val processBypass = listOf(
            "aether.exe",
            "xray.exe",
            "sing-box.exe",
            "mhrv-rs.exe",
            "dnstt-client.exe",
            "tor.exe",
            "psiphon-tunnel-core.exe",
            "lyrebird.exe",
            "pnifox.exe",
            "java.exe",
            "javaw.exe",
        )
        rules += buildJsonObject {
            putJsonArray("process_name") { processBypass.forEach { add(JsonPrimitive(it)) } }
            put("outbound", "direct")
        }

        if (isTunMode) {
            rules += buildJsonObject { put("protocol", "dns"); put("action", "hijack-dns") }
        }

        val ipv4Regex = Regex("""^\d{1,3}(\.\d{1,3}){3}$""")
        val directHosts = mutableListOf<String>()
        val directCidrs = mutableListOf<String>()
        bypassTargetIps.map { it.trim() }.filter { it.isNotBlank() }.forEach { target ->
            if (ipv4Regex.matches(target)) directCidrs += "$target/32"
            else directHosts += target
        }
        if (directCidrs.isNotEmpty() || directHosts.isNotEmpty()) {
            rules += buildJsonObject {
                if (directCidrs.isNotEmpty()) putJsonArray("ip_cidr") { directCidrs.forEach { add(JsonPrimitive(it)) } }
                if (directHosts.isNotEmpty()) putJsonArray("domain_suffix") { directHosts.forEach { add(JsonPrimitive(it)) } }
                put("outbound", "direct")
            }
        }

        if (appSettings.blockAdsEnabled || appSettings.customBlockRules.isNotBlank()) {
            val custom = PnifoxRulesetsCatalog.parseCustomRules(appSettings.customBlockRules)
            val suffixes = mutableListOf<String>()
            val exact = mutableListOf<String>()
            val keywords = mutableListOf<String>()
            val cidrs = mutableListOf<String>()
            if (appSettings.blockAdsEnabled) {
                suffixes += PnifoxRulesetsCatalog.adBlockDomains
                keywords += PnifoxRulesetsCatalog.adBlockKeywords
            }
            custom.forEach { entry ->
                when (entry.kind) {
                    ParsedRuleKind.DOMAIN_SUFFIX -> suffixes += entry.value
                    ParsedRuleKind.DOMAIN_EXACT -> exact += entry.value
                    ParsedRuleKind.DOMAIN_KEYWORD -> keywords += entry.value
                    ParsedRuleKind.IP_OR_CIDR -> cidrs += if (entry.value.contains('/')) entry.value else "${entry.value}/32"
                    ParsedRuleKind.DOMAIN_REGEX -> Unit
                }
            }
            if (suffixes.isNotEmpty() || exact.isNotEmpty() || keywords.isNotEmpty() || cidrs.isNotEmpty()) {
                rules += buildJsonObject {
                    if (suffixes.isNotEmpty()) putJsonArray("domain_suffix") { suffixes.distinct().forEach { add(JsonPrimitive(it)) } }
                    if (exact.isNotEmpty()) putJsonArray("domain") { exact.distinct().forEach { add(JsonPrimitive(it)) } }
                    if (keywords.isNotEmpty()) putJsonArray("domain_keyword") { keywords.distinct().forEach { add(JsonPrimitive(it)) } }
                    if (cidrs.isNotEmpty()) putJsonArray("ip_cidr") { cidrs.distinct().forEach { add(JsonPrimitive(it)) } }
                    put("action", "reject")
                }
            }
        }

        val customDirect = PnifoxRulesetsCatalog.parseCustomRules(appSettings.customDirectRules)
        if (appSettings.bypassIranEnabled || appSettings.bypassPrivateLanEnabled || customDirect.isNotEmpty()) {
            val suffixes = mutableListOf<String>()
            val exact = mutableListOf<String>()
            val keywords = mutableListOf<String>()
            val cidrs = mutableListOf<String>()
            if (appSettings.bypassIranEnabled) {
                suffixes += "ir"
                suffixes += PnifoxRulesetsCatalog.iranPopularNonIrDomains
            }
            customDirect.forEach { entry ->
                when (entry.kind) {
                    ParsedRuleKind.DOMAIN_SUFFIX -> suffixes += entry.value
                    ParsedRuleKind.DOMAIN_EXACT -> exact += entry.value
                    ParsedRuleKind.DOMAIN_KEYWORD -> keywords += entry.value
                    ParsedRuleKind.IP_OR_CIDR -> cidrs += if (entry.value.contains('/')) entry.value else "${entry.value}/32"
                    ParsedRuleKind.DOMAIN_REGEX -> Unit
                }
            }
            if (suffixes.isNotEmpty() || exact.isNotEmpty() || keywords.isNotEmpty() || cidrs.isNotEmpty()) {
                rules += buildJsonObject {
                    if (suffixes.isNotEmpty()) putJsonArray("domain_suffix") { suffixes.distinct().forEach { add(JsonPrimitive(it)) } }
                    if (exact.isNotEmpty()) putJsonArray("domain") { exact.distinct().forEach { add(JsonPrimitive(it)) } }
                    if (keywords.isNotEmpty()) putJsonArray("domain_keyword") { keywords.distinct().forEach { add(JsonPrimitive(it)) } }
                    if (cidrs.isNotEmpty()) putJsonArray("ip_cidr") { cidrs.distinct().forEach { add(JsonPrimitive(it)) } }
                    put("outbound", "direct")
                }
            }
            if (appSettings.bypassPrivateLanEnabled) {
                rules += buildJsonObject {
                    put("ip_is_private", true)
                    put("outbound", "direct")
                }
            }
        }
        return rules
    }
}
