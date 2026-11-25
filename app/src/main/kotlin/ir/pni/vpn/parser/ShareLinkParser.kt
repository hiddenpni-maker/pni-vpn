package ir.pni.vpn.parser

import ir.pni.vpn.data.model.ProtocolType
import ir.pni.vpn.data.model.ProxyProfile
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.net.URI
import java.net.URLDecoder
import java.net.URLEncoder
import java.util.UUID
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi

/**
 * Unified URI parser and serializer supporting:
 * - Xray links: vless://, vmess://, trojan://, ss://, wg://
 * - Sing-box / Lantern links: hysteria2://, hy2://, tuic://, ssh://, lantern://
 * - Aether / MASQUE links: aether://, masque://
 * - PattN/PattNG custom extensions: `cs` (cipherSuites), `fp=unsafe`, `allowInsecure=1`, unencrypted public mode
 * - Soldier-Client encrypted links: `pni://enc:...` or `soldier://enc:...`
 */
object ShareLinkParser {

    private val json = Json { ignoreUnknownKeys = true }

    fun parseMany(rawInput: String): List<ProxyProfile> {
        val text = rawInput.trim()
        if (text.isEmpty()) return emptyList()

        val expanded = if (isProbablyBase64Sub(text)) {
            decodeBase64Safe(text) ?: text
        } else {
            text
        }

        return expanded.lineSequence()
            .map { it.trim() }
            .filter { it.isNotEmpty() && !it.startsWith("#") }
            .mapNotNull { parseSingle(it) }
            .toList()
    }

    fun parseSingle(rawLine: String): ProxyProfile? {
        val line = rawLine.trim()
        return runCatching {
            when {
                line.startsWith("pni://enc:", ignoreCase = true) ||
                    line.startsWith("soldier://enc:", ignoreCase = true) -> {
                    val decrypted = ConfigEncryptor.decrypt(line)
                    if (decrypted != null && decrypted != line) parseSingle(decrypted) else null
                }
                line.startsWith("vless://", ignoreCase = true) -> parseVlessOrTrojan(line, ProtocolType.VLESS)
                line.startsWith("trojan://", ignoreCase = true) -> parseVlessOrTrojan(line, ProtocolType.TROJAN)
                line.startsWith("vmess://", ignoreCase = true) -> parseVmess(line)
                line.startsWith("ss://", ignoreCase = true) -> parseShadowsocks(line)
                line.startsWith("hysteria2://", ignoreCase = true) ||
                    line.startsWith("hy2://", ignoreCase = true) -> parseHysteria2(line)
                line.startsWith("tuic://", ignoreCase = true) -> parseTuic(line)
                line.startsWith("ssh://", ignoreCase = true) -> parseSsh(line)
                line.startsWith("masque://", ignoreCase = true) -> parseMasque(line)
                line.startsWith("aether://", ignoreCase = true) -> parseAether(line)
                line.startsWith("dnstt://", ignoreCase = true) ||
                    line.startsWith("dns://", ignoreCase = true) -> parseDnsTunnel(line)
                line.startsWith("wg://", ignoreCase = true) ||
                    line.startsWith("wireguard://", ignoreCase = true) -> parseWireguard(line)
                line.startsWith("{") -> ProxyProfile(
                    id = UUID.randomUUID().toString(),
                    name = "Custom JSON",
                    protocol = ProtocolType.CUSTOM_JSON,
                    server = "127.0.0.1",
                    port = 443,
                    rawCustomJson = line,
                    rawShareLink = line
                )
                else -> null
            }
        }.getOrNull()
    }

    private fun parseVlessOrTrojan(line: String, protocol: ProtocolType): ProxyProfile? {
        val uri = URI(fixUrl(line))
        val host = uri.host ?: return null
        val port = if (uri.port > 0) uri.port else 443
        val q = parseQuery(uri.rawQuery)
        val remark = decodeUrl(uri.rawFragment).ifBlank { "${protocol.labelEn}-$host" }

        val fp = q["fp"].orEmpty().ifBlank { if (q["unsafe"] == "1") "unsafe" else "chrome" }
        val security = q["security"].orEmpty().ifBlank { "none" }
        val allowUnencrypted = protocol == ProtocolType.VLESS &&
            (q["encryption"].orEmpty().equals("none", ignoreCase = true)) &&
            security == "none"

        return ProxyProfile(
            id = UUID.randomUUID().toString(),
            name = remark,
            protocol = protocol,
            server = host,
            port = port,
            uuidOrPassword = decodeUrl(uri.userInfo),
            network = q["type"].orEmpty().ifBlank { "tcp" },
            security = security,
            sni = q["sni"].orEmpty(),
            host = q["host"].orEmpty(),
            path = decodeUrl(q["path"]).ifBlank { "/" },
            alpn = decodeUrl(q["alpn"]),
            fingerprint = fp,
            cipherSuites = decodeUrl(q["cs"]),
            unsafeTls = fp.equals("unsafe", ignoreCase = true) || q["allowInsecure"] == "1",
            allowUnencryptedPublic = allowUnencrypted,
            publicKey = q["pbk"].orEmpty(),
            shortId = q["sid"].orEmpty(),
            spiderX = decodeUrl(q["spx"]),
            flow = q["flow"].orEmpty(),
            rawShareLink = line
        )
    }

    private fun parseVmess(line: String): ProxyProfile? {
        val payload = line.substringAfter("://").trim()
        val decoded = decodeBase64Safe(payload) ?: return null
        val obj = json.parseToJsonElement(decoded).jsonObject
        val add = obj["add"]?.jsonPrimitive?.content ?: return null
        val port = obj["port"]?.jsonPrimitive?.intOrNull ?: 443
        val ps = obj["ps"]?.jsonPrimitive?.content.orEmpty().ifBlank { "VMess-$add" }
        val fp = obj["fp"]?.jsonPrimitive?.content.orEmpty().ifBlank { "chrome" }
        return ProxyProfile(
            id = UUID.randomUUID().toString(),
            name = ps,
            protocol = ProtocolType.VMESS,
            server = add,
            port = port,
            uuidOrPassword = obj["id"]?.jsonPrimitive?.content.orEmpty(),
            network = obj["net"]?.jsonPrimitive?.content.orEmpty().ifBlank { "ws" },
            security = obj["tls"]?.jsonPrimitive?.content.orEmpty().ifBlank { "none" },
            sni = obj["sni"]?.jsonPrimitive?.content.orEmpty(),
            host = obj["host"]?.jsonPrimitive?.content.orEmpty(),
            path = obj["path"]?.jsonPrimitive?.content.orEmpty().ifBlank { "/" },
            alpn = obj["alpn"]?.jsonPrimitive?.content.orEmpty(),
            fingerprint = fp,
            unsafeTls = fp.equals("unsafe", ignoreCase = true),
            rawShareLink = line
        )
    }

    private fun parseShadowsocks(line: String): ProxyProfile? {
        val uri = URI(fixUrl(line))
        val host = uri.host ?: return null
        val port = if (uri.port > 0) uri.port else 8388
        val userInfoRaw = uri.userInfo.orEmpty()
        val decodedUser = decodeBase64Safe(userInfoRaw) ?: userInfoRaw
        val method = decodedUser.substringBefore(":", "chacha20-ietf-poly1305")
        val password = decodedUser.substringAfter(":", decodedUser)
        val remark = decodeUrl(uri.rawFragment).ifBlank { "SS-$host" }
        return ProxyProfile(
            id = UUID.randomUUID().toString(),
            name = remark,
            protocol = ProtocolType.SHADOWSOCKS,
            server = host,
            port = port,
            uuidOrPassword = "$method:$password",
            network = "tcp",
            security = "none",
            rawShareLink = line
        )
    }

    private fun parseHysteria2(line: String): ProxyProfile? {
        val normalized = if (line.startsWith("hy2://", ignoreCase = true)) {
            "hysteria2://" + line.substringAfter("://")
        } else {
            line
        }
        val uri = URI(fixUrl(normalized))
        val host = uri.host ?: return null
        val port = if (uri.port > 0) uri.port else 443
        val q = parseQuery(uri.rawQuery)
        val remark = decodeUrl(uri.rawFragment).ifBlank { "Hy2-$host" }
        return ProxyProfile(
            id = UUID.randomUUID().toString(),
            name = remark,
            protocol = ProtocolType.HYSTERIA2,
            server = host,
            port = port,
            uuidOrPassword = decodeUrl(uri.userInfo),
            network = "udp",
            security = "tls",
            sni = q["sni"].orEmpty().ifBlank { host },
            alpn = q["alpn"].orEmpty().ifBlank { "h3" },
            obfsPassword = q["obfs-password"].orEmpty(),
            unsafeTls = q["insecure"] == "1",
            rawShareLink = line
        )
    }

    private fun parseTuic(line: String): ProxyProfile? {
        val uri = URI(fixUrl(line))
        val host = uri.host ?: return null
        val port = if (uri.port > 0) uri.port else 443
        val q = parseQuery(uri.rawQuery)
        val remark = decodeUrl(uri.rawFragment).ifBlank { "TUIC-$host" }
        return ProxyProfile(
            id = UUID.randomUUID().toString(),
            name = remark,
            protocol = ProtocolType.TUIC,
            server = host,
            port = port,
            uuidOrPassword = decodeUrl(uri.userInfo),
            network = "udp",
            security = "tls",
            sni = q["sni"].orEmpty().ifBlank { host },
            alpn = q["alpn"].orEmpty().ifBlank { "h3" },
            unsafeTls = q["allow_insecure"] == "1",
            rawShareLink = line
        )
    }

    private fun parseSsh(line: String): ProxyProfile? {
        val uri = URI(fixUrl(line))
        val host = uri.host ?: return null
        val port = if (uri.port > 0) uri.port else 22
        val remark = decodeUrl(uri.rawFragment).ifBlank { "SSH-$host" }
        return ProxyProfile(
            id = UUID.randomUUID().toString(),
            name = remark,
            protocol = ProtocolType.SSH,
            server = host,
            port = port,
            uuidOrPassword = decodeUrl(uri.userInfo),
            network = "tcp",
            security = "none",
            rawShareLink = line
        )
    }

    private fun parseMasque(line: String): ProxyProfile? {
        val uri = URI(fixUrl(line))
        val host = uri.host ?: return null
        val port = if (uri.port > 0) uri.port else 443
        val q = parseQuery(uri.rawQuery)
        val remark = decodeUrl(uri.rawFragment).ifBlank { "MASQUE-$host" }
        return ProxyProfile(
            id = UUID.randomUUID().toString(),
            name = remark,
            protocol = ProtocolType.AETHER_MASQUE,
            server = host,
            port = port,
            uuidOrPassword = decodeUrl(uri.userInfo),
            publicKey = q["publickey"].orEmpty(),
            sni = q["sni"].orEmpty().ifBlank { "consumer-masque.cloudflareclient.com" },
            alpn = q["alpn"].orEmpty().ifBlank { "h3" },
            aetherMode = "masque",
            rawShareLink = line
        )
    }

    private fun parseAether(line: String): ProxyProfile? {
        val uri = URI(fixUrl(line))
        val q = parseQuery(uri.rawQuery)
        val mode = q["protocol"].orEmpty().ifBlank { "warp" }
        val proto = if (mode.contains("masque", ignoreCase = true)) {
            ProtocolType.AETHER_MASQUE
        } else {
            ProtocolType.AETHER_WARP
        }
        val remark = decodeUrl(uri.rawFragment).ifBlank { "Aether-${mode.uppercase()}" }
        return ProxyProfile(
            id = UUID.randomUUID().toString(),
            name = remark,
            protocol = proto,
            server = uri.host ?: "162.159.192.1",
            port = if (uri.port > 0) uri.port else 2408,
            sni = q["sni"].orEmpty(),
            aetherMode = mode,
            rawShareLink = line
        )
    }

    private fun parseDnsTunnel(line: String): ProxyProfile? {
        val uri = URI(fixUrl(line))
        val domain = uri.host ?: return null
        val q = parseQuery(uri.rawQuery)
        val pubkey = decodeUrl(uri.userInfo).ifBlank { q["pubkey"].orEmpty() }
        val transport = q["mode"].orEmpty().ifBlank { q["transport"].orEmpty() }.ifBlank { "udp" }
        val resolver = decodeUrl(q["resolver"]).ifBlank {
            when (transport) {
                "doh" -> "https://1.1.1.1/dns-query"
                "dot" -> "1.1.1.1:853"
                else -> "8.8.8.8:53"
            }
        }
        val inner = q["inner"].orEmpty().ifBlank { "ssh" }
        val remark = decodeUrl(uri.rawFragment).ifBlank { "DNSTT-$domain" }
        return ProxyProfile(
            id = UUID.randomUUID().toString(),
            name = remark,
            protocol = ProtocolType.DNS_TUNNEL,
            server = domain,
            port = 53,
            dnsTransport = transport,
            dnsResolver = resolver,
            dnsDomain = domain,
            dnsPubkey = pubkey,
            dnsInnerMode = inner,
            dnsInnerUser = decodeUrl(q["user"]),
            dnsInnerPass = decodeUrl(q["pass"]),
            dnsUtls = decodeUrl(q["utls"]),
            rawShareLink = line
        )
    }

    private fun parseWireguard(line: String): ProxyProfile? {
        val uri = URI(fixUrl(line))
        val host = uri.host ?: return null
        val port = if (uri.port > 0) uri.port else 2408
        val q = parseQuery(uri.rawQuery)
        val remark = decodeUrl(uri.rawFragment).ifBlank { "WG-$host" }
        return ProxyProfile(
            id = UUID.randomUUID().toString(),
            name = remark,
            protocol = ProtocolType.WIREGUARD,
            server = host,
            port = port,
            uuidOrPassword = decodeUrl(uri.userInfo),
            publicKey = q["publickey"].orEmpty(),
            network = "udp",
            security = "none",
            rawShareLink = line
        )
    }

    fun toShareLink(profile: ProxyProfile): String {
        if (profile.rawShareLink.isNotBlank() &&
            profile.protocol !in setOf(ProtocolType.VLESS, ProtocolType.TROJAN)
        ) {
            return profile.rawShareLink
        }
        return when (profile.protocol) {
            ProtocolType.VLESS, ProtocolType.TROJAN -> {
                val scheme = if (profile.protocol == ProtocolType.VLESS) "vless" else "trojan"
                val params = linkedMapOf<String, String>()
                params["type"] = profile.network
                params["security"] = profile.security
                if (profile.protocol == ProtocolType.VLESS) params["encryption"] = "none"
                if (profile.sni.isNotBlank()) params["sni"] = profile.sni
                if (profile.host.isNotBlank()) params["host"] = profile.host
                if (profile.path.isNotBlank()) params["path"] = profile.path
                if (profile.alpn.isNotBlank()) params["alpn"] = profile.alpn
                if (profile.fingerprint.isNotBlank()) params["fp"] = profile.fingerprint
                if (profile.cipherSuites.isNotBlank()) params["cs"] = profile.cipherSuites
                if (profile.unsafeTls) params["allowInsecure"] = "1"
                if (profile.publicKey.isNotBlank()) params["pbk"] = profile.publicKey
                if (profile.shortId.isNotBlank()) params["sid"] = profile.shortId
                if (profile.flow.isNotBlank()) params["flow"] = profile.flow

                val query = params.entries.joinToString("&") { (k, v) ->
                    "${encodeUrl(k)}=${encodeUrl(v)}"
                }
                val user = encodeUrl(profile.uuidOrPassword)
                val frag = encodeUrl(profile.name)
                "$scheme://$user@${profile.server}:${profile.port}?$query#$frag"
            }
            ProtocolType.DNS_TUNNEL -> {
                val params = linkedMapOf(
                    "mode" to profile.dnsTransport,
                    "resolver" to profile.dnsResolver,
                    "inner" to profile.dnsInnerMode
                )
                if (profile.dnsUtls.isNotBlank()) params["utls"] = profile.dnsUtls
                if (profile.dnsInnerUser.isNotBlank()) params["user"] = profile.dnsInnerUser
                if (profile.dnsInnerPass.isNotBlank()) params["pass"] = profile.dnsInnerPass
                val query = params.entries.joinToString("&") { (k, v) ->
                    "${encodeUrl(k)}=${encodeUrl(v)}"
                }
                val pub = encodeUrl(profile.dnsPubkey)
                val dom = profile.dnsDomain.ifBlank { profile.server }
                val frag = encodeUrl(profile.name)
                "dnstt://$pub@$dom?$query#$frag"
            }
            else -> profile.rawShareLink
        }
    }

    private fun parseQuery(rawQuery: String?): Map<String, String> {
        if (rawQuery.isNullOrBlank()) return emptyMap()
        return rawQuery.split("&")
            .mapNotNull { part ->
                val idx = part.indexOf('=')
                if (idx <= 0) null
                else decodeUrl(part.substring(0, idx)) to decodeUrl(part.substring(idx + 1))
            }
            .toMap()
    }

    private fun decodeUrl(s: String?): String =
        if (s.isNullOrEmpty()) "" else runCatching { URLDecoder.decode(s, "UTF-8") }.getOrDefault(s)

    private fun encodeUrl(s: String): String =
        runCatching { URLEncoder.encode(s, "UTF-8").replace("+", "%20") }.getOrDefault(s)

    private fun fixUrl(s: String): String = s.replace(" ", "%20")

    private fun isProbablyBase64Sub(s: String): Boolean {
        if (s.contains("://") || s.startsWith("{")) return false
        return s.length > 16 && s.all { it.isLetterOrDigit() || it in "+/=\r\n_-" }
    }

    @OptIn(ExperimentalEncodingApi::class)
    private fun decodeBase64Safe(s: String): String? = runCatching {
        val clean = s.trim().replace("\n", "").replace("\r", "")
        val padded = when (clean.length % 4) {
            2 -> "$clean=="
            3 -> "$clean="
            else -> clean
        }
        val bytes = if (padded.contains('-') || padded.contains('_')) {
            Base64.UrlSafe.decode(padded)
        } else {
            Base64.Default.decode(padded)
        }
        String(bytes, Charsets.UTF_8)
    }.getOrNull()
}
