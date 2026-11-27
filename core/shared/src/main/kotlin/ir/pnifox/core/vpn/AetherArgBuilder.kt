package ir.pnifox.core.vpn

import ir.pnifox.core.model.AetherIdentityTarget
import ir.pnifox.core.model.AetherProtocol
import ir.pnifox.core.model.AetherSettings
import ir.pnifox.core.model.AppSettings
import ir.pnifox.core.model.DnsTunnelMode
import ir.pnifox.core.model.DnsTunnelSettings
import ir.pnifox.core.model.ExitNode
import ir.pnifox.core.model.MasqueTransport
import ir.pnifox.core.model.OneTapPreset
import ir.pnifox.core.model.ParsedRuleKind
import ir.pnifox.core.model.PnifoxRulesetsCatalog
import ir.pnifox.core.model.PsiphonChainMode
import ir.pnifox.core.model.TransportMode
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Generates authentic CLI arguments verified against `patterniha/Aether/aether/src/cli.rs`
 * and `Mygod/dnstt/dnstt-client/main.go`.
 */
@Singleton
class AetherArgBuilder @Inject constructor() {

    fun buildRegisterArgs(
        target: AetherIdentityTarget,
        aether: AetherSettings,
        workDir: File,
    ): List<String> = buildList {
        val baseToml = File(workDir, "aether.toml").absolutePath
        add("--config")
        add(baseToml)
        add("--wg-config")
        add(File(workDir, "aether-wg.toml").absolutePath)
        add("--masque-config")
        add(File(workDir, "aether-masque.toml").absolutePath)

        add("--register")
        add(target.cliValue)

        if (aether.warpApiCustomAddress.isNotBlank()) {
            add("--api-address")
            add(aether.warpApiCustomAddress.trim())
        }
        if (aether.warpApiSni.isNotBlank()) {
            add("--api-sni")
            add(aether.warpApiSni.trim())
        }
        if (aether.warpApiPrimaryHost.isNotBlank()) {
            add("--api-host")
            add(aether.warpApiPrimaryHost.trim())
        }
        if (aether.warpApiUseFragment) {
            add("--fragment")
            add("--fragment-size")
            add(aether.fragmentSizeBytes.ifBlank { "8-16" })
            add("--fragment-delay")
            add(aether.fragmentDelayMs.ifBlank { "2-10" })
        }
        if (aether.warpApiUseEch) {
            add("--ech")
            add(aether.echBase64Override.trim().ifBlank { "auto" })
            if (aether.echDns.isNotBlank()) {
                add("--ech-dns")
                add(aether.echDns.trim())
            }
            if (aether.echDomain.isNotBlank()) {
                add("--ech-domain")
                add(aether.echDomain.trim())
            }
        }
        add("--log-level")
        add("info")
    }

    fun normalizeCliCsvTokens(raw: String): String = raw
        .split(',', ';', '\n', '\r', '\t', ' ')
        .map { it.trim() }
        .filter { it.isNotBlank() && !it.startsWith("#") }
        .distinct()
        .joinToString(",")

    fun isPsiphonActive(appSettings: AppSettings, aether: AetherSettings): Boolean =
        aether.psiphonEnabled ||
            appSettings.exitNode == ExitNode.PSIPHON ||
            appSettings.activePreset == OneTapPreset.WARP_TO_PSIPHON ||
            appSettings.activePreset == OneTapPreset.PSIPHON_AKAMAI_CDN

    fun isTorActive(appSettings: AppSettings, aether: AetherSettings): Boolean =
        !isPsiphonActive(appSettings, aether) && (
            aether.torEnabled ||
                appSettings.exitNode == ExitNode.TOR ||
                appSettings.activePreset == OneTapPreset.WARP_TO_TOR
            )

    fun buildRunArgs(
        appSettings: AppSettings,
        aether: AetherSettings,
        nativeDir: String,
        workDir: File,
        psiphonServersDatPath: String? = null,
        routesFile: File? = null,
        exposeLocalHttpProxy: Boolean = false,
    ): List<String> = buildList {
        add("--config")
        add(File(workDir, "aether.toml").absolutePath)
        add("--wg-config")
        add(File(workDir, "aether-wg.toml").absolutePath)
        add("--masque-config")
        add(File(workDir, "aether-masque.toml").absolutePath)

        val usePsiphon = isPsiphonActive(appSettings, aether)
        val useTor = isTorActive(appSettings, aether)

        // When chaining Tor or Psiphon, Aether itself listens on inner port and Tor/Psiphon
        // exposes the final SOCKS5 proxy directly on localSocksPort (127.0.0.1:10820 or 0.0.0.0 when allowLan=true).
        // When psiphonMode.isStandalone, Psiphon exposes localSocksPort directly without WARP outer hop.
        // When psiphonMode.isReverse, Psiphon listens on innerAetherBindPort and WARP dials through it to serve localSocksPort.
        val publicBindHost = if (appSettings.allowLan) "0.0.0.0" else "127.0.0.1"
        val effectivePsiphonMode = if (appSettings.activePreset == OneTapPreset.PSIPHON_AKAMAI_CDN &&
            !aether.psiphonMode.isStandalone && !aether.psiphonMode.isReverse &&
            aether.psiphonMode != PsiphonChainMode.CDN_FRONTING
        ) {
            PsiphonChainMode.STANDALONE_CDN
        } else {
            aether.psiphonMode
        }
        val psiphonStandalone = usePsiphon && effectivePsiphonMode.isStandalone
        val psiphonReverse = usePsiphon && effectivePsiphonMode.isReverse
        if (usePsiphon) {
            when {
                psiphonStandalone -> {
                    add("--bind")
                    add("$publicBindHost:${aether.localSocksPort}")
                    add("--psiphon-only")
                    if (exposeLocalHttpProxy) {
                        add("--psiphon-http")
                        add("$publicBindHost:${appSettings.httpProxyPort}")
                    }
                }
                psiphonReverse -> {
                    add("--bind")
                    add("$publicBindHost:${aether.localSocksPort}")
                    if (exposeLocalHttpProxy) {
                        add("--http-proxy")
                        add("$publicBindHost:${appSettings.httpProxyPort}")
                    }
                    add("--psiphon-reverse")
                    add("--psiphon-bind")
                    add("127.0.0.1:${aether.innerAetherBindPort}")
                }
                else -> {
                    add("--bind")
                    add("127.0.0.1:${aether.innerAetherBindPort}")
                    add("--psiphon")
                    add("--psiphon-bind")
                    add("$publicBindHost:${aether.localSocksPort}")
                    if (exposeLocalHttpProxy) {
                        add("--psiphon-http")
                        add("$publicBindHost:${appSettings.httpProxyPort}")
                    }
                }
            }
            add("--psiphon-mode")
            add(effectivePsiphonMode.cliMode)
            add("--psiphon-dir")
            add(File(workDir, "psiphon-cache").absolutePath)
            val psiphonBin = listOf("psiphon-tunnel-core.exe", "libpsiphon.so")
                .map { File(nativeDir, it) }
                .firstOrNull { it.exists() }
            if (psiphonBin != null) {
                add("--psiphon-bin")
                add(psiphonBin.absolutePath)
            }
            if (aether.psiphonUseBundledServersDat && !psiphonServersDatPath.isNullOrBlank() && File(psiphonServersDatPath).exists()) {
                add("--psiphon-server-entries")
                add(psiphonServersDatPath)
            }
            if (aether.psiphonExitCountry != "AUTO" && aether.psiphonExitCountry.isNotBlank()) {
                add("--psiphon-region")
                add(aether.psiphonExitCountry.trim())
            }
            val cleanCdnIps = normalizeCliCsvTokens(aether.psiphonCdnFrontingIps)
            if (cleanCdnIps.isNotBlank()) {
                add("--psiphon-cdn-ips")
                add(cleanCdnIps)
            }
            val cleanCdnSni = normalizeCliCsvTokens(aether.psiphonCdnSniHostnames)
            if (cleanCdnSni.isNotBlank()) {
                add("--psiphon-cdn-sni")
                add(cleanCdnSni)
            }
            val defaultAkamaiSet = if (effectivePsiphonMode == PsiphonChainMode.STANDALONE_CDN &&
                aether.psiphonCdnSets.isBlank() &&
                cleanCdnIps.isBlank()
            ) {
                "psiphon-akamai"
            } else {
                normalizeCliCsvTokens(aether.psiphonCdnSets)
            }
            if (defaultAkamaiSet.isNotBlank()) {
                add("--psiphon-cdn-sets")
                add(defaultAkamaiSet)
            }
        } else if (useTor) {
            add("--bind")
            add("127.0.0.1:${aether.innerAetherBindPort}")
            add("--tor")
            add("--tor-bind")
            add("$publicBindHost:${aether.localSocksPort}")
            if (exposeLocalHttpProxy) {
                add("--tor-http")
                add("$publicBindHost:${appSettings.httpProxyPort}")
            }
            add("--tor-dir")
            add(File(workDir, "tor-cache").absolutePath)
            val lyrebirdBin = listOf("lyrebird.exe", "liblyrebird.so")
                .map { File(nativeDir, it) }
                .firstOrNull { it.exists() }
            if (lyrebirdBin != null) {
                add("--tor-pt")
                add("obfs4=${lyrebirdBin.absolutePath}")
            }
            if (aether.torUseBridgesImmediately) {
                add("--tor-bridges")
            }
            aether.customTorBridges.lineSequence()
                .map { it.trim() }
                .filter { it.isNotBlank() && !it.startsWith("#") }
                .forEach { bridgeLine ->
                    add("--tor-bridge")
                    add(bridgeLine)
                }
        } else {
            add("--bind")
            add("$publicBindHost:${aether.localSocksPort}")
            if (exposeLocalHttpProxy) {
                add("--http-proxy")
                add("$publicBindHost:${appSettings.httpProxyPort}")
            }
        }

        if (psiphonStandalone) {
            if (routesFile != null && routesFile.exists()) {
                add("--routes")
                add(routesFile.absolutePath)
            }
            add("--log-level")
            add("info")
            return@buildList
        }

        val effectiveProto = if (psiphonReverse) {
            AetherProtocol.MASQUE
        } else {
            when (appSettings.activePreset) {
                OneTapPreset.WARP_FAST_MASQUE_H2, OneTapPreset.WARP_TO_PSIPHON, OneTapPreset.PSIPHON_AKAMAI_CDN, OneTapPreset.WARP_TO_TOR -> AetherProtocol.MASQUE
                OneTapPreset.WG_OVER_MASQUE -> AetherProtocol.WIREGUARD_OVER_MASQUE
                OneTapPreset.MASQUE_IN_MASQUE -> AetherProtocol.MASQUE_IN_MASQUE
            }
        }
        add(effectiveProto.cliFlag)

        val usesMasque = effectiveProto == AetherProtocol.MASQUE ||
            effectiveProto == AetherProtocol.MASQUE_IN_MASQUE ||
            effectiveProto == AetherProtocol.WIREGUARD_OVER_MASQUE

        if (usesMasque) {
            val transport = if (psiphonReverse ||
                appSettings.activePreset == OneTapPreset.WARP_FAST_MASQUE_H2 ||
                appSettings.activePreset == OneTapPreset.WARP_TO_PSIPHON ||
                appSettings.activePreset == OneTapPreset.PSIPHON_AKAMAI_CDN
            ) {
                MasqueTransport.HTTP2
            } else {
                aether.masqueTransport
            }
            add(transport.cliFlag)
        }

        add("--ip")
        add(aether.ipVersion.cliValue)

        if (aether.scanForEndpoint || aether.endpointIp.isBlank()) {
            add("--scan")
            add(aether.scanMode.cliValue)
            add("--no-quick-reconnect")
            if (usesMasque) {
                add("--startup-secs")
                add("20")
            }
        } else {
            val peerSpec = "${aether.endpointIp.trim()}:${aether.endpointPort}"
            when (effectiveProto) {
                AetherProtocol.MASQUE -> {
                    add("--peer")
                    add(peerSpec)
                    if (aether.masqueTransport == MasqueTransport.HTTP2) {
                        add("--h2-peer")
                        add(peerSpec)
                    }
                }
                AetherProtocol.WIREGUARD -> {
                    add("--wg-peer")
                    add(peerSpec)
                }
                AetherProtocol.MASQUE_IN_MASQUE -> {
                    add("--mim-outer")
                    add(peerSpec)
                    if (aether.innerEndpointPeer.isNotBlank()) {
                        add("--mim-inner")
                        add(aether.innerEndpointPeer.trim())
                    }
                }
                AetherProtocol.WARP_IN_WARP -> {
                    add("--wiw-outer")
                    add(peerSpec)
                    if (aether.innerEndpointPeer.isNotBlank()) {
                        add("--wiw-inner")
                        add(aether.innerEndpointPeer.trim())
                    }
                }
                AetherProtocol.WIREGUARD_OVER_MASQUE -> {
                    add("--peer")
                    add(peerSpec)
                    if (aether.innerEndpointPeer.isNotBlank()) {
                        add("--gool-peer")
                        add(aether.innerEndpointPeer.trim())
                    }
                }
            }
        }

        if (usesMasque && aether.sni.isNotBlank()) {
            add("--masque-sni")
            add(aether.sni.trim())
        }

        add("--noize")
        add(aether.noizeProfile.cliValue)

        if (aether.fragmentEnabled) {
            add("--fragment")
            add("--fragment-size")
            add(aether.fragmentSizeBytes.ifBlank { "8-16" })
            add("--fragment-delay")
            add(aether.fragmentDelayMs.ifBlank { "2-10" })
        }

        if (aether.echEnabled) {
            add("--ech")
            add(aether.echBase64Override.trim().ifBlank { "auto" })
            if (aether.echDns.isNotBlank()) {
                add("--ech-dns")
                add(aether.echDns.trim())
            }
            if (aether.echDomain.isNotBlank()) {
                add("--ech-domain")
                add(aether.echDomain.trim())
            }
        }

        if (appSettings.localDns.isNotBlank()) {
            add("--dns")
            add(appSettings.localDns.trim())
        }

        if (routesFile != null && routesFile.exists()) {
            add("--routes")
            add(routesFile.absolutePath)
        }

        add("--log-level")
        add("info")
    }

    /**
     * Generates a `--routes` ruleset file (`[block]` and `[direct]` sections) supported by
     * `libaether.so`, covering Iran domain bypass, private LAN bypass, AdBlock, and custom rules.
     */
    fun buildRoutesFileContent(appSettings: AppSettings): String? {
        val blockLines = buildList {
            if (appSettings.blockAdsEnabled) {
                PnifoxRulesetsCatalog.adBlockKeywords.forEach { add("keyword:$it") }
                addAll(PnifoxRulesetsCatalog.adBlockDomains)
            }
            PnifoxRulesetsCatalog.parseCustomRules(appSettings.customBlockRules).forEach { entry ->
                add(formatAetherEntry(entry.kind, entry.value, entry.rawToken))
            }
        }.distinct()

        val directLines = buildList {
            if (appSettings.bypassPrivateLanEnabled) {
                add("private")
            }
            if (appSettings.bypassIranEnabled) {
                add("ir")
                add("regexp:\\.ir$")
                addAll(PnifoxRulesetsCatalog.iranPopularNonIrDomains)
            }
            PnifoxRulesetsCatalog.parseCustomRules(appSettings.customDirectRules).forEach { entry ->
                add(formatAetherEntry(entry.kind, entry.value, entry.rawToken))
            }
        }.distinct()

        if (blockLines.isEmpty() && directLines.isEmpty()) return null
        return buildString {
            if (blockLines.isNotEmpty()) {
                appendLine("[block]")
                blockLines.forEach(::appendLine)
            }
            if (directLines.isNotEmpty()) {
                if (blockLines.isNotEmpty()) appendLine()
                appendLine("[direct]")
                directLines.forEach(::appendLine)
            }
        }
    }

    private fun formatAetherEntry(kind: ParsedRuleKind, value: String, rawToken: String): String = when (kind) {
        ParsedRuleKind.IP_OR_CIDR -> value
        ParsedRuleKind.DOMAIN_EXACT -> "full:$value"
        ParsedRuleKind.DOMAIN_KEYWORD -> "keyword:$value"
        ParsedRuleKind.DOMAIN_REGEX -> "regexp:$value"
        ParsedRuleKind.DOMAIN_SUFFIX -> value
    }.ifBlank { rawToken }

    fun writeRoutesFileIfNeeded(appSettings: AppSettings, workDir: File): File? {
        val file = File(workDir, "aether-routes.txt")
        val content = buildRoutesFileContent(appSettings)
        if (content.isNullOrBlank()) {
            runCatching { if (file.exists()) file.delete() }
            return null
        }
        file.writeText(content)
        return file
    }

    /**
     * Resolves a plain IP address for `libaether.so --dns <list>` from a DNS preset or DoH/DoT URL.
     */
    fun extractAetherDnsServer(dns: DnsTunnelSettings): String {
        val raw = dns.resolverAddress.trim()
        val hostPart = raw
            .removePrefix("https://")
            .removePrefix("http://")
            .removePrefix("quic://")
            .removePrefix("tls://")
            .substringBefore('/')
            .substringBefore(':')
            .trim()
        val ipv4 = Regex("""^\d{1,3}(?:\.\d{1,3}){3}$""")
        if (ipv4.matches(hostPart)) return hostPart
        return when {
            hostPart.contains("google", ignoreCase = true) -> "8.8.8.8"
            hostPart.contains("adguard", ignoreCase = true) -> "94.140.14.14"
            hostPart.contains("quad9", ignoreCase = true) -> "9.9.9.9"
            hostPart.contains("mullvad", ignoreCase = true) -> "194.242.2.2"
            hostPart.contains("shecan", ignoreCase = true) -> "178.22.122.100"
            hostPart.contains("403", ignoreCase = true) || hostPart.contains("electro", ignoreCase = true) -> "78.157.42.100"
            else -> "1.1.1.1"
        }
    }

    fun buildDnsttArgs(
        dns: DnsTunnelSettings,
        localSocksPort: Int = 10820,
    ): List<String> = buildList {
        val addr = dns.resolverAddress.trim()
        when (dns.mode) {
            DnsTunnelMode.DOH -> {
                add("-doh")
                add(if (addr.startsWith("http")) addr else "https://$addr/dns-query")
            }
            DnsTunnelMode.DOT, DnsTunnelMode.PDOQ -> {
                add("-dot")
                add(addr.removePrefix("quic://").removePrefix("https://"))
            }
            DnsTunnelMode.UDP, DnsTunnelMode.SUPER_DNS, DnsTunnelMode.FAST_DNS -> {
                add("-udp")
                add(if (addr.contains(':')) addr else "$addr:53")
            }
        }
        if (dns.publicKeyHex.isNotBlank()) {
            add("-pubkey")
            add(dns.publicKeyHex.trim())
        }
        add(dns.nameserver.trim())
        add("127.0.0.1:$localSocksPort")
    }

    fun formatCommandPreview(
        appSettings: AppSettings,
        aether: AetherSettings,
        psiphonServersDatPath: String? = null,
    ): String {
        val dummyDir = File("/data/local/aether")
        val args = buildRunArgs(appSettings, aether, "/data/app/lib/arm64", dummyDir, psiphonServersDatPath)
        return buildString {
            append("libaether.so")
            for (token in args) {
                append(' ')
                if (token.any { it.isWhitespace() || it == '=' }) append('"').append(token).append('"')
                else append(token)
            }
        }
    }
}
