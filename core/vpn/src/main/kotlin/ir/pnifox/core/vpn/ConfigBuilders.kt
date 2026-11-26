package ir.pnifox.core.vpn

import ir.pnifox.core.model.AppSettings
import ir.pnifox.core.model.DnsTunnelMode
import ir.pnifox.core.model.DnsTunnelSettings
import ir.pnifox.core.model.ParsedRuleKind
import ir.pnifox.core.model.PnifoxRulesetsCatalog
import ir.pnifox.core.model.ProxyProfile
import ir.pnifox.core.model.ProxyProtocol
import ir.pnifox.core.model.TransportMode
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject

object ConfigBuilders {

    private val parserJson = Json { ignoreUnknownKeys = true }

    fun buildXrayConfig(
        profile: ProxyProfile,
        appSettings: AppSettings,
        socksPort: Int = 10820,
        errorLogPath: String? = null,
        hasGeoAssets: Boolean = true,
    ): String {
        val rawTrim = profile.rawUri.trimStart()
        if (rawTrim.startsWith("{")) {
            buildFromFullXrayJson(rawTrim, appSettings, socksPort, errorLogPath, hasGeoAssets)?.let { return it }
        }
        val useFragment = profile.fragmentEnabled || appSettings.transportMode == TransportMode.FRAGMENT
        val targetHost = if (profile.spoofLocalhost40443) "127.0.0.1" else profile.address
        val targetPort = if (profile.spoofLocalhost40443) 40443 else profile.port

        val outbound = buildXrayOutbound(profile, targetHost, targetPort, useFragment)
        val outbounds = mutableListOf(outbound)
        if (useFragment) {
            outbounds += buildJsonObject {
                put("tag", "fragment")
                put("protocol", "freedom")
                putJsonObject("settings") {
                    put("domainStrategy", "UseIP")
                    putJsonObject("fragment") {
                        put("packets", "tlshello")
                        put("length", "10-20")
                        put("interval", "5-15")
                    }
                }
            }
        }
        outbounds += buildJsonObject {
            put("tag", "direct")
            put("protocol", "freedom")
        }
        outbounds += buildJsonObject {
            put("tag", "block")
            put("protocol", "blackhole")
        }

        val rulesetRules = buildXrayRulesetRules(appSettings, hasGeoAssets)
        val root = buildJsonObject {
            putJsonObject("log") {
                put("loglevel", "warning")
                if (!errorLogPath.isNullOrBlank()) {
                    put("error", errorLogPath)
                }
            }
            put("dns", buildXrayDnsObject(appSettings, hasGeoAssets))
            putJsonArray("inbounds") {
                add(buildSocksInbound(appSettings, socksPort))
                if (appSettings.allowLan) {
                    add(buildHttpInbound(appSettings))
                }
            }
            put("outbounds", JsonArray(outbounds))
            putJsonObject("routing") {
                put("domainStrategy", if (rulesContainGeoOrIp(rulesetRules)) "IPIfNonMatch" else "AsIs")
                if (rulesetRules.isNotEmpty()) {
                    put("rules", JsonArray(rulesetRules))
                }
            }
        }
        return root.toString()
    }

    private fun buildXrayOutbound(
        profile: ProxyProfile,
        targetHost: String,
        targetPort: Int,
        useFragment: Boolean,
    ): JsonObject = when (profile.protocol) {
        ProxyProtocol.VLESS -> buildJsonObject {
            put("tag", "proxy")
            put("protocol", "vless")
            putJsonObject("settings") {
                putJsonArray("vnext") {
                    add(
                        buildJsonObject {
                            put("address", targetHost)
                            put("port", targetPort)
                            putJsonArray("users") {
                                add(
                                    buildJsonObject {
                                        put("id", profile.secret)
                                        put("encryption", "none")
                                        if (profile.realityPublicKey.isNotBlank()) {
                                            put("flow", "xtls-rprx-vision")
                                        }
                                    },
                                )
                            }
                        },
                    )
                }
            }
            put("streamSettings", buildStreamSettings(profile, useFragment))
        }
        ProxyProtocol.VMESS -> buildJsonObject {
            put("tag", "proxy")
            put("protocol", "vmess")
            putJsonObject("settings") {
                putJsonArray("vnext") {
                    add(
                        buildJsonObject {
                            put("address", targetHost)
                            put("port", targetPort)
                            putJsonArray("users") {
                                add(
                                    buildJsonObject {
                                        put("id", profile.secret)
                                        put("security", "auto")
                                    },
                                )
                            }
                        },
                    )
                }
            }
            put("streamSettings", buildStreamSettings(profile, useFragment))
        }
        ProxyProtocol.TROJAN -> buildJsonObject {
            put("tag", "proxy")
            put("protocol", "trojan")
            putJsonObject("settings") {
                putJsonArray("servers") {
                    add(
                        buildJsonObject {
                            put("address", targetHost)
                            put("port", targetPort)
                            put("password", profile.secret)
                        },
                    )
                }
            }
            put("streamSettings", buildStreamSettings(profile, useFragment))
        }
        ProxyProtocol.SHADOWSOCKS -> {
            val method = profile.secret.substringBefore(':', "aes-256-gcm")
            val pwd = profile.secret.substringAfter(':', profile.secret)
            buildJsonObject {
                put("tag", "proxy")
                put("protocol", "shadowsocks")
                putJsonObject("settings") {
                    putJsonArray("servers") {
                        add(
                            buildJsonObject {
                                put("address", targetHost)
                                put("port", targetPort)
                                put("method", method)
                                put("password", pwd)
                            },
                        )
                    }
                }
            }
        }
        else -> buildJsonObject {
            put("tag", "proxy")
            put("protocol", "freedom")
        }
    }

    private fun buildFromFullXrayJson(
        rawJson: String,
        appSettings: AppSettings,
        socksPort: Int,
        errorLogPath: String?,
        hasGeoAssets: Boolean,
    ): String? = runCatching {
        val parsed = parserJson.parseToJsonElement(rawJson).jsonObject
        val origOutbounds = (parsed["outbounds"] as? JsonArray)
            ?.mapNotNull { it as? JsonObject }
            ?.toMutableList()
            ?: mutableListOf()

        if (origOutbounds.none { it["tag"]?.jsonPrimitive?.content == "direct" }) {
            origOutbounds += buildJsonObject { put("tag", "direct"); put("protocol", "freedom") }
        }
        if (origOutbounds.none { it["tag"]?.jsonPrimitive?.content == "block" }) {
            origOutbounds += buildJsonObject { put("tag", "block"); put("protocol", "blackhole") }
        }

        val rulesetRules = buildXrayRulesetRules(appSettings, hasGeoAssets)
        val origRouting = parsed["routing"] as? JsonObject
        val origStrategy = (origRouting?.get("domainStrategy") as? JsonPrimitive)?.content ?: "IPIfNonMatch"
        val sanitizedOrigRules = (origRouting?.get("rules") as? JsonArray)
            ?.mapNotNull { el -> (el as? JsonObject)?.let { sanitizeRuleForGeo(it, hasGeoAssets) } }
            ?: emptyList()
        val combinedRules = rulesetRules + sanitizedOrigRules

        val rebuilt = buildJsonObject {
            for ((k, v) in parsed) {
                when (k) {
                    "remarks", "log", "inbounds", "outbounds", "routing" -> continue
                    else -> put(k, v)
                }
            }
            val origLog = parsed["log"] as? JsonObject
            val logLevel = (origLog?.get("loglevel") as? JsonPrimitive)?.content ?: "warning"
            putJsonObject("log") {
                put("loglevel", logLevel)
                if (!errorLogPath.isNullOrBlank()) {
                    put("error", errorLogPath)
                }
            }
            putJsonArray("inbounds") {
                add(buildSocksInbound(appSettings, socksPort))
                if (appSettings.allowLan) {
                    add(buildHttpInbound(appSettings))
                }
            }
            put("outbounds", JsonArray(origOutbounds))
            putJsonObject("routing") {
                put("domainStrategy", origStrategy)
                put("rules", JsonArray(combinedRules))
            }
        }
        rebuilt.toString()
    }.getOrNull()

    private fun buildHttpInbound(appSettings: AppSettings): JsonObject = buildJsonObject {
        put("tag", "http-in")
        put("listen", "0.0.0.0")
        put("port", appSettings.httpProxyPort)
        put("protocol", "http")
        putJsonObject("settings") {
            put("allowTransparent", false)
        }
        putJsonObject("sniffing") {
            put("enabled", true)
            putJsonArray("destOverride") {
                add(JsonPrimitive("http"))
                add(JsonPrimitive("tls"))
                add(JsonPrimitive("quic"))
            }
            put("routeOnly", true)
        }
    }

    private fun buildSocksInbound(appSettings: AppSettings, socksPort: Int): JsonObject = buildJsonObject {
        put("tag", "socks-in")
        put("listen", if (appSettings.allowLan) "0.0.0.0" else "127.0.0.1")
        put("port", socksPort)
        put("protocol", "socks")
        putJsonObject("settings") {
            put("auth", "noauth")
            put("udp", true)
        }
        putJsonObject("sniffing") {
            put("enabled", true)
            putJsonArray("destOverride") {
                add(JsonPrimitive("http"))
                add(JsonPrimitive("tls"))
                add(JsonPrimitive("quic"))
            }
            put("routeOnly", true)
        }
    }

    private fun buildXrayDnsObject(appSettings: AppSettings, hasGeoAssets: Boolean): JsonObject = buildJsonObject {
        putJsonArray("servers") {
            add(JsonPrimitive(appSettings.localDns.ifBlank { "1.1.1.1" }))
            if (appSettings.antiSanctionDns.isNotBlank()) {
                add(JsonPrimitive(appSettings.antiSanctionDns))
            }
        }
        if (appSettings.blockAdsEnabled || appSettings.customBlockRules.isNotBlank()) {
            val hostsMap = buildXrayAdHostsMap(appSettings, hasGeoAssets)
            if (hostsMap.isNotEmpty()) {
                putJsonObject("hosts") {
                    hostsMap.forEach { (k, v) -> put(k, v) }
                }
            }
        }
    }

    private fun buildXrayAdHostsMap(appSettings: AppSettings, hasGeoAssets: Boolean): Map<String, String> {
        val hosts = linkedMapOf<String, String>()
        if (appSettings.blockAdsEnabled) {
            if (hasGeoAssets) {
                hosts["geosite:category-ads-all"] = "0.0.0.0"
            } else {
                PnifoxRulesetsCatalog.adBlockDomains.forEach { hosts["domain:$it"] = "0.0.0.0" }
            }
        }
        PnifoxRulesetsCatalog.parseCustomRules(appSettings.customBlockRules).forEach { entry ->
            when (entry.kind) {
                ParsedRuleKind.DOMAIN_EXACT -> hosts["full:${entry.value}"] = "0.0.0.0"
                ParsedRuleKind.DOMAIN_SUFFIX -> hosts["domain:${entry.value}"] = "0.0.0.0"
                else -> Unit
            }
        }
        return hosts
    }

    private fun buildXrayRulesetRules(appSettings: AppSettings, hasGeoAssets: Boolean): List<JsonObject> {
        val rules = mutableListOf<JsonObject>()

        // 1. Block Ads & custom block rules first
        val customBlock = PnifoxRulesetsCatalog.parseCustomRules(appSettings.customBlockRules)
        val blockDomains = mutableListOf<String>()
        val blockIps = mutableListOf<String>()
        if (appSettings.blockAdsEnabled) {
            if (hasGeoAssets) {
                blockDomains += "geosite:category-ads-all"
            } else {
                PnifoxRulesetsCatalog.adBlockDomains.forEach { blockDomains += "domain:$it" }
            }
        }
        customBlock.forEach { entry ->
            when (entry.kind) {
                ParsedRuleKind.IP_OR_CIDR -> blockIps += entry.value
                ParsedRuleKind.DOMAIN_EXACT -> blockDomains += "full:${entry.value}"
                ParsedRuleKind.DOMAIN_KEYWORD -> blockDomains += "keyword:${entry.value}"
                ParsedRuleKind.DOMAIN_REGEX -> blockDomains += "regexp:${entry.value}"
                ParsedRuleKind.DOMAIN_SUFFIX -> blockDomains += "domain:${entry.value}"
            }
        }
        if (blockDomains.isNotEmpty()) {
            rules += buildJsonObject {
                put("type", "field")
                putJsonArray("domain") { blockDomains.distinct().forEach { add(JsonPrimitive(it)) } }
                put("outboundTag", "block")
            }
        }
        if (blockIps.isNotEmpty()) {
            rules += buildJsonObject {
                put("type", "field")
                putJsonArray("ip") { blockIps.distinct().forEach { add(JsonPrimitive(it)) } }
                put("outboundTag", "block")
            }
        }

        // 2. Bypass private LAN
        if (appSettings.bypassPrivateLanEnabled) {
            rules += buildJsonObject {
                put("type", "field")
                putJsonArray("ip") {
                    if (hasGeoAssets) add(JsonPrimitive("geoip:private"))
                    else PnifoxRulesetsCatalog.privateCidrs.forEach { add(JsonPrimitive(it)) }
                }
                put("outboundTag", "direct")
            }
        }

        // 3. Whitelist Iran domestic sites & custom direct rules
        val customDirect = PnifoxRulesetsCatalog.parseCustomRules(appSettings.customDirectRules)
        val directDomains = mutableListOf<String>()
        val directIps = mutableListOf<String>()
        if (appSettings.bypassIranEnabled) {
            if (hasGeoAssets) {
                directDomains += "geosite:category-ir"
                directIps += "geoip:ir"
            }
            directDomains += "regexp:.*\\.ir$"
            PnifoxRulesetsCatalog.iranPopularNonIrDomains.forEach { directDomains += "domain:$it" }
        }
        customDirect.forEach { entry ->
            when (entry.kind) {
                ParsedRuleKind.IP_OR_CIDR -> directIps += entry.value
                ParsedRuleKind.DOMAIN_EXACT -> directDomains += "full:${entry.value}"
                ParsedRuleKind.DOMAIN_KEYWORD -> directDomains += "keyword:${entry.value}"
                ParsedRuleKind.DOMAIN_REGEX -> directDomains += "regexp:${entry.value}"
                ParsedRuleKind.DOMAIN_SUFFIX -> directDomains += "domain:${entry.value}"
            }
        }
        if (directDomains.isNotEmpty()) {
            rules += buildJsonObject {
                put("type", "field")
                putJsonArray("domain") { directDomains.distinct().forEach { add(JsonPrimitive(it)) } }
                put("outboundTag", "direct")
            }
        }
        if (directIps.isNotEmpty()) {
            rules += buildJsonObject {
                put("type", "field")
                putJsonArray("ip") { directIps.distinct().forEach { add(JsonPrimitive(it)) } }
                put("outboundTag", "direct")
            }
        }

        return rules
    }

    private fun sanitizeRuleForGeo(rule: JsonObject, hasGeoAssets: Boolean): JsonObject? {
        if (hasGeoAssets) return rule
        val ipArr = (rule["ip"] as? JsonArray)?.mapNotNull { it as? JsonPrimitive }
        val domArr = (rule["domain"] as? JsonArray)?.mapNotNull { it as? JsonPrimitive }

        val cleanIps = ipArr?.flatMap { prim ->
            val txt = prim.content
            when {
                txt.equals("geoip:private", ignoreCase = true) -> PnifoxRulesetsCatalog.privateCidrs
                txt.startsWith("geoip:", ignoreCase = true) -> emptyList()
                else -> listOf(txt)
            }
        }
        val cleanDomains = domArr?.flatMap { prim ->
            val txt = prim.content
            when {
                txt.equals("geosite:category-ir", ignoreCase = true) || txt.equals("geosite:ir", ignoreCase = true) ->
                    listOf("regexp:.*\\.ir$") + PnifoxRulesetsCatalog.iranPopularNonIrDomains.map { "domain:$it" }
                txt.startsWith("geosite:", ignoreCase = true) -> emptyList()
                else -> listOf(txt)
            }
        }
        if (ipArr != null && cleanIps.isNullOrEmpty() && domArr == null) return null
        if (domArr != null && cleanDomains.isNullOrEmpty() && ipArr == null) return null

        return buildJsonObject {
            for ((k, v) in rule) {
                when (k) {
                    "ip" -> if (!cleanIps.isNullOrEmpty()) {
                        putJsonArray("ip") { cleanIps.forEach { add(JsonPrimitive(it)) } }
                    }
                    "domain" -> if (!cleanDomains.isNullOrEmpty()) {
                        putJsonArray("domain") { cleanDomains.forEach { add(JsonPrimitive(it)) } }
                    }
                    else -> put(k, v)
                }
            }
        }
    }

    private fun rulesContainGeoOrIp(rules: List<JsonObject>): Boolean =
        rules.any { it.containsKey("ip") }

    private fun isExplicitPlaintext(profile: ProxyProfile): Boolean {
        val raw = profile.rawUri.lowercase()
        if (raw.contains("security=none") || raw.contains("security=raw")) return true
        val plainPorts = setOf(80, 8080, 8880, 2052, 2082, 2086, 2095)
        if (profile.port in plainPorts && !raw.contains("security=tls") && !raw.contains("security=reality")) {
            return true
        }
        return false
    }

    private fun isExplicitTls(profile: ProxyProfile): Boolean {
        val raw = profile.rawUri.lowercase()
        if (isExplicitPlaintext(profile)) return false
        if (raw.contains("security=tls")) return true
        val tlsPorts = setOf(443, 2053, 2083, 2087, 2096, 8443)
        return profile.port in tlsPorts || profile.sni.isNotBlank()
    }

    private fun buildStreamSettings(profile: ProxyProfile, useFragment: Boolean): JsonObject = buildJsonObject {
        val net = profile.transport.lowercase().ifBlank { "tcp" }
        put("network", net)
        if (profile.realityPublicKey.isNotBlank()) {
            put("security", "reality")
            putJsonObject("realitySettings") {
                put("serverName", profile.sni.ifBlank { profile.address })
                put("fingerprint", profile.tlsFingerprint.ifBlank { "chrome" })
                put("publicKey", profile.realityPublicKey)
                put("shortId", profile.realityShortId)
            }
        } else if (isExplicitTls(profile)) {
            put("security", "tls")
            putJsonObject("tlsSettings") {
                put("serverName", profile.sni.ifBlank { profile.address })
                put("fingerprint", profile.tlsFingerprint.ifBlank { "chrome" })
                put("allowInsecure", false)
            }
        }
        when (net) {
            "ws" -> putJsonObject("wsSettings") {
                put("path", profile.pathOrServiceName.ifBlank { "/" })
                if (profile.sni.isNotBlank()) {
                    putJsonObject("headers") { put("Host", profile.sni) }
                }
            }
            "grpc" -> putJsonObject("grpcSettings") {
                put("serviceName", profile.pathOrServiceName)
            }
        }
        if (useFragment) {
            putJsonObject("sockopt") {
                put("dialerProxy", "fragment")
            }
        }
    }

    fun buildDnsChangerXrayConfig(
        dnsSettings: DnsTunnelSettings,
        appSettings: AppSettings,
        socksPort: Int = 10820,
        errorLogPath: String? = null,
        hasGeoAssets: Boolean = true,
    ): String {
        val raw = dnsSettings.resolverAddress.trim().ifBlank { "1.1.1.1" }
        val formattedServer = when {
            raw.startsWith("http://", ignoreCase = true) ||
                raw.startsWith("https://", ignoreCase = true) ||
                raw.startsWith("quic://", ignoreCase = true) ||
                raw.startsWith("tls://", ignoreCase = true) -> raw
            dnsSettings.mode == DnsTunnelMode.DOH -> "https://$raw/dns-query"
            dnsSettings.mode == DnsTunnelMode.DOT -> "tls://${raw.substringBefore(':')}"
            dnsSettings.mode == DnsTunnelMode.PDOQ -> "quic://${raw.substringBefore(':')}"
            else -> raw.substringBefore(':')
        }

        val adHosts = if (appSettings.blockAdsEnabled || appSettings.customBlockRules.isNotBlank()) {
            buildXrayAdHostsMap(appSettings, hasGeoAssets)
        } else {
            emptyMap()
        }

        val root = buildJsonObject {
            putJsonObject("log") {
                put("loglevel", "warning")
                if (!errorLogPath.isNullOrBlank()) {
                    put("error", errorLogPath)
                }
            }
            putJsonObject("dns") {
                putJsonArray("servers") {
                    add(JsonPrimitive(formattedServer))
                    add(JsonPrimitive("1.1.1.1"))
                }
                if (adHosts.isNotEmpty()) {
                    putJsonObject("hosts") {
                        adHosts.forEach { (k, v) -> put(k, v) }
                    }
                }
            }
            putJsonArray("inbounds") {
                add(buildSocksInbound(appSettings, socksPort))
                if (appSettings.allowLan) {
                    add(buildHttpInbound(appSettings))
                }
            }
            putJsonArray("outbounds") {
                // Keep direct first as default outbound so non-53 fallback traffic is never blackholed
                add(buildJsonObject { put("tag", "direct"); put("protocol", "freedom") })
                add(buildJsonObject { put("tag", "dns-out"); put("protocol", "dns") })
                add(buildJsonObject { put("tag", "block"); put("protocol", "blackhole") })
            }
            putJsonObject("routing") {
                put("domainStrategy", "AsIs")
                putJsonArray("rules") {
                    add(
                        buildJsonObject {
                            put("type", "field")
                            put("port", "53")
                            put("outboundTag", "dns-out")
                        },
                    )
                }
            }
        }
        return root.toString()
    }

    /**
     * Builds an Xray config that chains a VLESS profile through local DNSTT port [innerDnsttPort].
     */
    fun buildDnsttVlessXrayConfig(
        profile: ProxyProfile,
        innerDnsttPort: Int,
        appSettings: AppSettings,
        socksPort: Int = 10820,
        errorLogPath: String? = null,
        hasGeoAssets: Boolean = true,
    ): String {
        val routedProfile = profile.copy(
            address = "127.0.0.1",
            port = innerDnsttPort,
            sni = profile.sni.ifBlank { profile.address },
            spoofLocalhost40443 = false,
            rawUri = "",
        )
        return buildXrayConfig(
            profile = routedProfile,
            appSettings = appSettings,
            socksPort = socksPort,
            errorLogPath = errorLogPath,
            hasGeoAssets = hasGeoAssets,
        )
    }

    /**
     * Builds a sing-box config (v1.11+/v1.14+ schema) for DNSTT -> SSH tunneling
     * (commonly used with SlowDNS/OpenTunnel SSH accounts).
     */
    fun buildSingBoxDnsttSshConfig(
        dnsSettings: DnsTunnelSettings,
        appSettings: AppSettings,
        innerDnsttPort: Int = 10823,
        socksPort: Int = 10820,
    ): String {
        val outbound = buildJsonObject {
            put("type", "ssh")
            put("tag", "proxy")
            put("server", "127.0.0.1")
            put("server_port", innerDnsttPort)
            put("user", dnsSettings.sshUsername.trim())
            if (dnsSettings.sshPassword.isNotEmpty()) {
                put("password", dnsSettings.sshPassword)
            }
        }
        return assembleSingBoxJson(outbound, appSettings, socksPort)
    }

    fun buildSingBoxConfig(profile: ProxyProfile, appSettings: AppSettings, socksPort: Int = 10820): String {
        val outbound = when (profile.protocol) {
            ProxyProtocol.HYSTERIA2 -> buildJsonObject {
                put("type", "hysteria2")
                put("tag", "proxy")
                put("server", profile.address)
                put("server_port", profile.port)
                put("password", profile.secret)
                putJsonObject("tls") {
                    put("enabled", true)
                    put("server_name", profile.sni.ifBlank { profile.address })
                }
            }
            else -> buildJsonObject {
                put("type", "wireguard")
                put("tag", "proxy")
                put("server", profile.address)
                put("server_port", profile.port)
                put("private_key", profile.secret)
                put("peer_public_key", profile.realityPublicKey)
                putJsonArray("local_address") { add(JsonPrimitive("172.16.0.2/32")) }
            }
        }
        return assembleSingBoxJson(outbound, appSettings, socksPort)
    }

    private fun assembleSingBoxJson(outbound: JsonObject, appSettings: AppSettings, socksPort: Int): String {
        val rules = buildSingBoxRouteRules(appSettings)
        val root = buildJsonObject {
            putJsonObject("log") { put("level", "info") }
            putJsonArray("inbounds") {
                add(
                    buildJsonObject {
                        put("type", "socks")
                        put("tag", "socks-in")
                        put("listen", if (appSettings.allowLan) "0.0.0.0" else "127.0.0.1")
                        put("listen_port", socksPort)
                    },
                )
                if (appSettings.allowLan) {
                    add(
                        buildJsonObject {
                            put("type", "http")
                            put("tag", "http-in")
                            put("listen", "0.0.0.0")
                            put("listen_port", appSettings.httpProxyPort)
                        },
                    )
                }
            }
            putJsonArray("outbounds") {
                add(outbound)
                add(buildJsonObject { put("type", "direct"); put("tag", "direct") })
            }
            if (rules.isNotEmpty()) {
                putJsonObject("route") {
                    put("rules", JsonArray(rules))
                    put("final", "proxy")
                }
            }
        }
        return root.toString()
    }

    private fun buildSingBoxRouteRules(appSettings: AppSettings): List<JsonObject> {
        val rules = mutableListOf<JsonObject>()
        if (appSettings.blockAdsEnabled || appSettings.customBlockRules.isNotBlank()) {
            val custom = PnifoxRulesetsCatalog.parseCustomRules(appSettings.customBlockRules)
            val suffixes = mutableListOf<String>()
            val exact = mutableListOf<String>()
            val keywords = mutableListOf<String>()
            val regexes = mutableListOf<String>()
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
                    ParsedRuleKind.DOMAIN_REGEX -> regexes += entry.value
                    ParsedRuleKind.IP_OR_CIDR -> cidrs += if (entry.value.contains('/')) entry.value else "${entry.value}/32"
                }
            }
            if (suffixes.isNotEmpty() || exact.isNotEmpty() || keywords.isNotEmpty() || regexes.isNotEmpty() || cidrs.isNotEmpty()) {
                rules += buildJsonObject {
                    if (exact.isNotEmpty()) putJsonArray("domain") { exact.distinct().forEach { add(JsonPrimitive(it)) } }
                    if (suffixes.isNotEmpty()) putJsonArray("domain_suffix") { suffixes.distinct().forEach { add(JsonPrimitive(it)) } }
                    if (keywords.isNotEmpty()) putJsonArray("domain_keyword") { keywords.distinct().forEach { add(JsonPrimitive(it)) } }
                    if (regexes.isNotEmpty()) putJsonArray("domain_regex") { regexes.distinct().forEach { add(JsonPrimitive(it)) } }
                    if (cidrs.isNotEmpty()) putJsonArray("ip_cidr") { cidrs.distinct().forEach { add(JsonPrimitive(it)) } }
                    put("action", "reject")
                }
            }
        }
        if (appSettings.bypassPrivateLanEnabled) {
            rules += buildJsonObject {
                put("ip_is_private", true)
                put("action", "route")
                put("outbound", "direct")
            }
        }
        if (appSettings.bypassIranEnabled || appSettings.customDirectRules.isNotBlank()) {
            val custom = PnifoxRulesetsCatalog.parseCustomRules(appSettings.customDirectRules)
            val suffixes = mutableListOf<String>()
            val exact = mutableListOf<String>()
            val keywords = mutableListOf<String>()
            val regexes = mutableListOf<String>()
            val cidrs = mutableListOf<String>()
            if (appSettings.bypassIranEnabled) {
                suffixes += ".ir"
                suffixes += PnifoxRulesetsCatalog.iranPopularNonIrDomains
            }
            custom.forEach { entry ->
                when (entry.kind) {
                    ParsedRuleKind.DOMAIN_SUFFIX -> suffixes += entry.value
                    ParsedRuleKind.DOMAIN_EXACT -> exact += entry.value
                    ParsedRuleKind.DOMAIN_KEYWORD -> keywords += entry.value
                    ParsedRuleKind.DOMAIN_REGEX -> regexes += entry.value
                    ParsedRuleKind.IP_OR_CIDR -> cidrs += if (entry.value.contains('/')) entry.value else "${entry.value}/32"
                }
            }
            if (suffixes.isNotEmpty() || exact.isNotEmpty() || keywords.isNotEmpty() || regexes.isNotEmpty() || cidrs.isNotEmpty()) {
                rules += buildJsonObject {
                    if (exact.isNotEmpty()) putJsonArray("domain") { exact.distinct().forEach { add(JsonPrimitive(it)) } }
                    if (suffixes.isNotEmpty()) putJsonArray("domain_suffix") { suffixes.distinct().forEach { add(JsonPrimitive(it)) } }
                    if (keywords.isNotEmpty()) putJsonArray("domain_keyword") { keywords.distinct().forEach { add(JsonPrimitive(it)) } }
                    if (regexes.isNotEmpty()) putJsonArray("domain_regex") { regexes.distinct().forEach { add(JsonPrimitive(it)) } }
                    if (cidrs.isNotEmpty()) putJsonArray("ip_cidr") { cidrs.distinct().forEach { add(JsonPrimitive(it)) } }
                    put("action", "route")
                    put("outbound", "direct")
                }
            }
        }
        return rules
    }
}
