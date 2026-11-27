package ir.pnifox.core.network

import ir.pnifox.core.model.ProxyProfile
import ir.pnifox.core.model.ProxyProtocol
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.net.URI
import java.net.URLDecoder
import java.util.UUID
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi

/**
 * Parses real proxy URIs (`vless://`, `vmess://`, `trojan://`, `ss://`, `hy2://`, `wg://`),
 * Base64 / `sub://` subscription payloads, Clash/Mihomo YAML (`proxies:`), Sing-box JSON,
 * and full Xray/MASQUE JSON config objects into [ProxyProfile].
 */
object ShareLinkParser {

    private val json = Json { ignoreUnknownKeys = true }
    private val proxySchemes = listOf(
        "vless://",
        "vmess://",
        "trojan://",
        "ss://",
        "hy2://",
        "hysteria2://",
        "tuic://",
        "anytls://",
        "wg://",
        "wireguard://",
    )

    fun parseBatch(rawInput: String, groupId: String = "manual"): List<ProxyProfile> =
        parseBatchInternal(rawInput, groupId, depth = 0)
            .distinctBy { "${it.protocol}:${it.address}:${it.port}:${it.secret}:${it.name}" }

    private fun parseBatchInternal(rawInput: String, groupId: String, depth: Int): List<ProxyProfile> {
        if (depth > 3) return emptyList()
        val trimmed = stripBomAndNoise(rawInput)
        if (trimmed.isEmpty()) return emptyList()

        // 0. sub:// Base64 link wrapper
        if (trimmed.startsWith("sub://", ignoreCase = true)) {
            val inner = decodeBase64Safe(trimmed.substringAfter("sub://").substringBefore('#'))
            if (!inner.isNullOrBlank()) {
                val subParsed = parseBatchInternal(inner, groupId, depth + 1)
                if (subParsed.isNotEmpty()) return subParsed
            }
        }

        // 1. Direct full Xray / Sing-box JSON object or JSON array of configs
        if (trimmed.startsWith("{") || trimmed.startsWith("[")) {
            val fromJson = parseJsonBatch(trimmed, groupId)
            if (fromJson.isNotEmpty()) return fromJson
        }

        // 2. Clash / Mihomo YAML subscription (`proxies:`)
        if (looksLikeClashYaml(trimmed)) {
            val fromYaml = parseClashYaml(trimmed, groupId)
            if (fromYaml.isNotEmpty()) return fromYaml
        }

        // 3. Direct URI lines / inline JSON objects (note: ignore comment lines when checking!)
        val directResults = mutableListOf<ProxyProfile>()
        val nonCommentLines = trimmed.lineSequence()
            .map { it.trim() }
            .filter { it.isNotBlank() && !it.startsWith("#") && !it.startsWith("//") }
            .toList()

        for (line in nonCommentLines) {
            when {
                hasProxyScheme(line) -> parseSingle(line, groupId)?.let(directResults::add)
                line.startsWith("{") -> directResults += parseJsonBatch(line, groupId)
            }
        }
        if (directResults.isNotEmpty()) return directResults

        // 4. Base64-encoded subscription payload (handles header comments like `#profile-title`
        //    even if a comment line contained `https://...`, and supports multi-line or double base64)
        val candidateB64 = nonCommentLines
            .filterNot { it.startsWith("http://", ignoreCase = true) || it.startsWith("https://", ignoreCase = true) }
            .joinToString("")
        val decoded = decodeBase64Safe(candidateB64)
        if (!decoded.isNullOrBlank() && decoded != trimmed) {
            val nested = parseBatchInternal(decoded, groupId, depth + 1)
            if (nested.isNotEmpty()) return nested
        }

        // 5. Fallback: scan individual Base64 lines
        val b64LineResults = mutableListOf<ProxyProfile>()
        for (line in nonCommentLines) {
            if (line.length >= 24 && !line.contains("://")) {
                val decLine = decodeBase64Safe(line)
                if (!decLine.isNullOrBlank()) {
                    b64LineResults += parseBatchInternal(decLine, groupId, depth + 1)
                }
            }
        }
        return b64LineResults
    }

    private fun stripBomAndNoise(input: String): String {
        var s = input.replace("\uFEFF", "").trim()
        if (s.startsWith("```") && s.endsWith("```") && s.length > 6) {
            s = s.substringAfter('\n').substringBeforeLast("```").trim()
        }
        return s
    }

    private fun hasProxyScheme(line: String): Boolean {
        val low = line.lowercase()
        return proxySchemes.any { low.startsWith(it) }
    }

    private fun parseJsonBatch(rawJson: String, groupId: String): List<ProxyProfile> = runCatching {
        val now = System.currentTimeMillis()
        when (val elem = json.parseToJsonElement(rawJson)) {
            is JsonObject -> parseJsonElementProfiles(elem, groupId, now)
            is JsonArray -> elem.flatMap { item ->
                if (item is JsonObject) parseJsonElementProfiles(item, groupId, now) else emptyList()
            }
            else -> emptyList()
        }
    }.getOrDefault(emptyList())

    private fun parseJsonElementProfiles(obj: JsonObject, groupId: String, now: Long): List<ProxyProfile> {
        for (wrapperKey in listOf("configs", "servers", "profiles")) {
            val nested = obj[wrapperKey] as? JsonArray
            if (nested != null) {
                return nested.flatMap { el ->
                    (el as? JsonObject)?.let { parseJsonElementProfiles(it, groupId, now) } ?: emptyList()
                }
            }
        }
        val singBoxProfiles = parseSingBoxOutbounds(obj, groupId, now)
        if (singBoxProfiles.isNotEmpty()) return singBoxProfiles
        return listOfNotNull(parseXrayJsonObject(obj, groupId, now))
    }

    private fun parseSingBoxOutbounds(obj: JsonObject, groupId: String, now: Long): List<ProxyProfile> {
        val outbounds = (obj["outbounds"] as? JsonArray)?.mapNotNull { it as? JsonObject }
            ?: if (obj.containsKey("type") && obj.containsKey("server")) listOf(obj) else emptyList()
        if (outbounds.isEmpty()) return emptyList()
        // Distinguish Sing-box ("type") from Xray ("protocol")
        if (outbounds.none { it["type"] != null && it["protocol"] == null }) return emptyList()

        val skipTypes = setOf("direct", "block", "dns", "selector", "urltest", "url-test")
        return outbounds.mapNotNull { ob ->
            val type = ob["type"]?.jsonPrimitive?.content?.lowercase().orEmpty()
            if (type.isBlank() || type in skipTypes) return@mapNotNull null
            val server = ob["server"]?.jsonPrimitive?.content?.trim().orEmpty()
            val port = ob["server_port"]?.jsonPrimitive?.intOrNull ?: 443
            if (server.isBlank() || port !in 1..65535) return@mapNotNull null

            val proto = when (type) {
                "vless" -> ProxyProtocol.VLESS
                "vmess" -> ProxyProtocol.VMESS
                "trojan" -> ProxyProtocol.TROJAN
                "shadowsocks" -> ProxyProtocol.SHADOWSOCKS
                "hysteria2", "hy2" -> ProxyProtocol.HYSTERIA2
                "tuic" -> ProxyProtocol.TUIC
                "anytls" -> ProxyProtocol.ANYTLS
                "wireguard" -> ProxyProtocol.WIREGUARD
                "masque" -> ProxyProtocol.MASQUE
                else -> return@mapNotNull null
            }

            val secret = when (proto) {
                ProxyProtocol.SHADOWSOCKS -> {
                    val m = ob["method"]?.jsonPrimitive?.content ?: "aes-256-gcm"
                    val p = ob["password"]?.jsonPrimitive?.content.orEmpty()
                    "$m:$p"
                }
                ProxyProtocol.TUIC -> {
                    val u = ob["uuid"]?.jsonPrimitive?.content.orEmpty()
                    val p = ob["password"]?.jsonPrimitive?.content.orEmpty()
                    if (p.isNotBlank()) "$u:$p" else u
                }
                ProxyProtocol.WIREGUARD -> ob["private_key"]?.jsonPrimitive?.content.orEmpty()
                else -> ob["uuid"]?.jsonPrimitive?.content
                    ?: ob["password"]?.jsonPrimitive?.content
                    ?: ""
            }
            val tlsObj = ob["tls"] as? JsonObject
            val realityObj = tlsObj?.get("reality") as? JsonObject
            val utlsObj = tlsObj?.get("utls") as? JsonObject
            val transportObj = ob["transport"] as? JsonObject
            val headersObj = transportObj?.get("headers") as? JsonObject
            val sni = tlsObj?.get("server_name")?.jsonPrimitive?.content
                ?: headersObj?.get("Host")?.jsonPrimitive?.content
                ?: ""
            val transport = transportObj?.get("type")?.jsonPrimitive?.content ?: "tcp"
            val path = transportObj?.get("path")?.jsonPrimitive?.content
                ?: transportObj?.get("service_name")?.jsonPrimitive?.content
                ?: ""
            val fp = utlsObj?.get("fingerprint")?.jsonPrimitive?.content ?: "chrome"
            val pbk = realityObj?.get("public_key")?.jsonPrimitive?.content
                ?: ob["peer_public_key"]?.jsonPrimitive?.content
                ?: ""
            val sid = realityObj?.get("short_id")?.jsonPrimitive?.content.orEmpty()
            val tag = ob["tag"]?.jsonPrimitive?.content?.trim()?.ifBlank { null }
                ?: "${proto.displayName} • $server:$port"

            val fingerprintId = "$groupId|$proto|$server|$port|$secret|$tag"
            ProxyProfile(
                id = UUID.nameUUIDFromBytes(fingerprintId.toByteArray()).toString(),
                groupId = groupId,
                name = tag,
                protocol = proto,
                address = server,
                port = port,
                secret = secret,
                sni = sni,
                transport = transport,
                pathOrServiceName = path,
                tlsFingerprint = fp,
                realityPublicKey = pbk,
                realityShortId = sid,
                rawUri = "",
                updatedAtEpochMs = now,
            )
        }
    }

    private fun parseXrayJsonObject(obj: JsonObject, groupId: String, now: Long): ProxyProfile? {
        val outbounds = (obj["outbounds"] as? JsonArray)?.mapNotNull { it as? JsonObject }
            ?: listOfNotNull(obj["outbound"] as? JsonObject)
        if (outbounds.isEmpty()) return null

        val nonProxyTags = setOf("freedom", "blackhole", "dns", "loopback")
        val primary = outbounds.firstOrNull {
            it["tag"]?.jsonPrimitive?.content.equals("proxy", ignoreCase = true)
        } ?: outbounds.firstOrNull {
            val proto = it["protocol"]?.jsonPrimitive?.content?.lowercase().orEmpty()
            proto.isNotBlank() && proto !in nonProxyTags
        } ?: outbounds.first()

        val protoName = primary["protocol"]?.jsonPrimitive?.content?.lowercase().orEmpty()
        val protocol = when (protoName) {
            "masque" -> ProxyProtocol.MASQUE
            "vless" -> ProxyProtocol.VLESS
            "vmess" -> ProxyProtocol.VMESS
            "trojan" -> ProxyProtocol.TROJAN
            "shadowsocks" -> ProxyProtocol.SHADOWSOCKS
            "wireguard" -> ProxyProtocol.WIREGUARD
            "hysteria2", "hy2" -> ProxyProtocol.HYSTERIA2
            else -> ProxyProtocol.VLESS
        }

        val settings = primary["settings"] as? JsonObject
        val vnextFirst = (settings?.get("vnext") as? JsonArray)?.firstOrNull() as? JsonObject
        val serversFirst = (settings?.get("servers") as? JsonArray)?.firstOrNull() as? JsonObject
        val wgPeerEndpoint = (settings?.get("peers") as? JsonArray)?.firstOrNull()
            ?.let { it as? JsonObject }?.get("endpoint")?.jsonPrimitive?.content

        var address = settings?.get("address")?.jsonPrimitive?.content
            ?: vnextFirst?.get("address")?.jsonPrimitive?.content
            ?: serversFirst?.get("address")?.jsonPrimitive?.content
            ?: wgPeerEndpoint?.substringBeforeLast(':')
            ?: "xray-json"
        if (address.startsWith("[") && address.endsWith("]")) {
            address = address.trim('[', ']')
        }

        val port = settings?.get("port")?.jsonPrimitive?.intOrNull
            ?: vnextFirst?.get("port")?.jsonPrimitive?.intOrNull
            ?: serversFirst?.get("port")?.jsonPrimitive?.intOrNull
            ?: wgPeerEndpoint?.substringAfterLast(':')?.toIntOrNull()
            ?: 443

        val stream = primary["streamSettings"] as? JsonObject
        val tlsSettings = stream?.get("tlsSettings") as? JsonObject
        val realitySettings = stream?.get("realitySettings") as? JsonObject
        val wsHeaders = (stream?.get("wsSettings") as? JsonObject)?.get("headers") as? JsonObject
        val sni = tlsSettings?.get("serverName")?.jsonPrimitive?.content
            ?: realitySettings?.get("serverName")?.jsonPrimitive?.content
            ?: wsHeaders?.get("Host")?.jsonPrimitive?.content
            ?: ""

        val transport = stream?.get("network")?.jsonPrimitive?.content
            ?: stream?.get("method")?.jsonPrimitive?.content
            ?: if (protocol == ProxyProtocol.MASQUE) "masque" else "tcp"

        val remark = obj["remarks"]?.jsonPrimitive?.content?.trim()
            ?.ifBlank { null }
            ?: obj["ps"]?.jsonPrimitive?.content?.trim()?.ifBlank { null }
            ?: "${protocol.displayName} JSON • $address:$port"

        val canonical = obj.toString()
        return ProxyProfile(
            id = UUID.nameUUIDFromBytes(canonical.toByteArray()).toString(),
            groupId = groupId,
            name = remark,
            protocol = protocol,
            address = address,
            port = port,
            sni = sni,
            transport = transport,
            tlsFingerprint = tlsSettings?.get("fingerprint")?.jsonPrimitive?.content
                ?: realitySettings?.get("fingerprint")?.jsonPrimitive?.content
                ?: "chrome",
            realityPublicKey = realitySettings?.get("publicKey")?.jsonPrimitive?.content.orEmpty(),
            realityShortId = realitySettings?.get("shortId")?.jsonPrimitive?.content.orEmpty(),
            rawUri = canonical,
            updatedAtEpochMs = now,
        )
    }

    private fun looksLikeClashYaml(text: String): Boolean =
        text.contains("proxies:", ignoreCase = true) ||
            (text.contains("server:", ignoreCase = true) && text.contains("type:", ignoreCase = true))

    private fun parseClashYaml(yaml: String, groupId: String): List<ProxyProfile> {
        val now = System.currentTimeMillis()
        val lines = yaml.lines()
        val results = mutableListOf<ProxyProfile>()
        val inProxiesSection = lines.any { it.trim().startsWith("proxies:") }
        var started = !inProxiesSection
        var curMap: MutableMap<String, String>? = null

        fun flushCur() {
            val map = curMap ?: return
            curMap = null
            clashMapToProfile(map, groupId, now)?.let(results::add)
        }

        for (rawLine in lines) {
            val commentStripped = rawLine.trimEnd()
            val trimmed = commentStripped.trim()
            if (trimmed.isEmpty() || trimmed.startsWith("#")) continue
            if (!started) {
                if (trimmed.startsWith("proxies:")) started = true
                continue
            }
            if (!rawLine.startsWith(" ") && !rawLine.startsWith("\t") && !trimmed.startsWith("-") &&
                trimmed.endsWith(":") && !trimmed.startsWith("proxies:")
            ) {
                flushCur()
                break
            }
            if (trimmed.startsWith("- {") || trimmed.startsWith("-{")) {
                flushCur()
                val inside = trimmed.substringAfter('{').substringBeforeLast('}')
                val map = parseInlineClashObject(inside)
                clashMapToProfile(map, groupId, now)?.let(results::add)
                continue
            }
            if (trimmed.startsWith("- ")) {
                flushCur()
                curMap = linkedMapOf()
                val rest = trimmed.removePrefix("- ").trim()
                val colon = rest.indexOf(':')
                if (colon > 0) {
                    val k = rest.substring(0, colon).trim().lowercase()
                    val v = rest.substring(colon + 1).trim().trim('"', '\'')
                    curMap!![k] = v
                }
                continue
            }
            val map = curMap
            if (map != null) {
                val colon = trimmed.indexOf(':')
                if (colon > 0) {
                    val k = trimmed.substring(0, colon).trim().lowercase()
                    val v = trimmed.substring(colon + 1).trim().trim('"', '\'')
                    if (k.isNotBlank() && v.isNotBlank() && !v.startsWith("{")) {
                        map[k] = v
                    }
                    if (k == "servername" || k == "sni" || k == "host" || k == "public-key" || k == "short-id" || k == "path" || k == "client-fingerprint") {
                        map[k] = v
                    }
                    // Also handle nested flow map like ws-opts: {path: /ws, headers: {Host: foo}}
                    if (k == "ws-opts" || k == "grpc-opts" || k == "reality-opts") {
                        val inner = v.removeSurrounding("{", "}")
                        parseInlineClashObject(inner).forEach { (ik, iv) ->
                            map.putIfAbsent(ik, iv)
                        }
                    }
                }
            }
        }
        flushCur()
        return results
    }

    private fun parseInlineClashObject(inside: String): MutableMap<String, String> {
        val map = linkedMapOf<String, String>()
        val tokens = mutableListOf<String>()
        val cur = StringBuilder()
        var braceDepth = 0
        var inQuote = false
        var quoteChar = '"'
        for (ch in inside) {
            when {
                inQuote -> {
                    cur.append(ch)
                    if (ch == quoteChar) inQuote = false
                }
                ch == '"' || ch == '\'' -> {
                    inQuote = true
                    quoteChar = ch
                    cur.append(ch)
                }
                ch == '{' || ch == '[' -> {
                    braceDepth++
                    cur.append(ch)
                }
                ch == '}' || ch == ']' -> {
                    if (braceDepth > 0) braceDepth--
                    cur.append(ch)
                }
                ch == ',' && braceDepth == 0 -> {
                    tokens += cur.toString()
                    cur.clear()
                }
                else -> cur.append(ch)
            }
        }
        if (cur.isNotEmpty()) tokens += cur.toString()

        for (tok in tokens) {
            val colon = tok.indexOf(':')
            if (colon <= 0) continue
            val k = tok.substring(0, colon).trim().lowercase()
            val vRaw = tok.substring(colon + 1).trim()
            if (vRaw.startsWith("{") && vRaw.endsWith("}")) {
                parseInlineClashObject(vRaw.removeSurrounding("{", "}")).forEach { (ik, iv) ->
                    map.putIfAbsent(ik, iv)
                }
            } else {
                map[k] = vRaw.trim('"', '\'', ' ')
            }
        }
        return map
    }

    private fun clashMapToProfile(map: Map<String, String>, groupId: String, now: Long): ProxyProfile? {
        val type = map["type"]?.lowercase() ?: return null
        val protocol = when (type) {
            "vless" -> ProxyProtocol.VLESS
            "vmess" -> ProxyProtocol.VMESS
            "trojan" -> ProxyProtocol.TROJAN
            "ss", "shadowsocks" -> ProxyProtocol.SHADOWSOCKS
            "hysteria2", "hy2" -> ProxyProtocol.HYSTERIA2
            "tuic" -> ProxyProtocol.TUIC
            "anytls" -> ProxyProtocol.ANYTLS
            "wireguard" -> ProxyProtocol.WIREGUARD
            else -> return null
        }
        val server = map["server"]?.trim().orEmpty()
        val port = map["port"]?.toIntOrNull() ?: return null
        if (server.isBlank() || port !in 1..65535) return null
        val name = map["name"]?.trim()?.ifBlank { null } ?: "${protocol.displayName} • $server:$port"
        val secret = when (protocol) {
            ProxyProtocol.SHADOWSOCKS -> "${map["cipher"] ?: "aes-256-gcm"}:${map["password"].orEmpty()}"
            ProxyProtocol.TUIC -> {
                val u = map["uuid"].orEmpty()
                val p = map["password"].orEmpty()
                if (p.isNotBlank()) "$u:$p" else u
            }
            ProxyProtocol.WIREGUARD -> map["private-key"].orEmpty()
            else -> map["uuid"] ?: map["password"] ?: ""
        }
        val sni = map["servername"] ?: map["sni"] ?: map["host"] ?: ""
        val net = map["network"] ?: "tcp"
        val path = map["path"] ?: map["grpc-service-name"] ?: map["servicename"] ?: ""
        val fp = map["client-fingerprint"] ?: map["fingerprint"] ?: "chrome"
        val pbk = map["public-key"] ?: ""
        val sid = map["short-id"] ?: ""
        val idKey = "$groupId|$protocol|$server|$port|$secret|$name"
        return ProxyProfile(
            id = UUID.nameUUIDFromBytes(idKey.toByteArray()).toString(),
            groupId = groupId,
            name = name,
            protocol = protocol,
            address = server,
            port = port,
            secret = secret,
            sni = sni,
            transport = net,
            pathOrServiceName = path,
            tlsFingerprint = fp,
            realityPublicKey = pbk,
            realityShortId = sid,
            rawUri = "",
            updatedAtEpochMs = now,
        )
    }

    fun parseSingle(uriText: String, groupId: String = "manual"): ProxyProfile? {
        val raw = uriText.trim()
        val now = System.currentTimeMillis()
        return when {
            raw.startsWith("vless://", ignoreCase = true) -> parseStandardUri(raw, ProxyProtocol.VLESS, groupId, now)
            raw.startsWith("trojan://", ignoreCase = true) -> parseStandardUri(raw, ProxyProtocol.TROJAN, groupId, now)
            raw.startsWith("hy2://", ignoreCase = true) ||
                raw.startsWith("hysteria2://", ignoreCase = true) -> parseStandardUri(raw, ProxyProtocol.HYSTERIA2, groupId, now)
            raw.startsWith("tuic://", ignoreCase = true) -> parseStandardUri(raw, ProxyProtocol.TUIC, groupId, now)
            raw.startsWith("anytls://", ignoreCase = true) -> parseStandardUri(raw, ProxyProtocol.ANYTLS, groupId, now)
            raw.startsWith("vmess://", ignoreCase = true) -> parseVmess(raw, groupId, now)
            raw.startsWith("ss://", ignoreCase = true) -> parseShadowsocks(raw, groupId, now)
            raw.startsWith("wg://", ignoreCase = true) ||
                raw.startsWith("wireguard://", ignoreCase = true) -> parseStandardUri(raw, ProxyProtocol.WIREGUARD, groupId, now)
            else -> null
        }
    }

    fun toggleSoldierSniSpoof(profile: ProxyProfile): ProxyProfile {
        val next = !profile.spoofLocalhost40443
        return profile.copy(
            spoofLocalhost40443 = next,
            updatedAtEpochMs = System.currentTimeMillis(),
        )
    }

    private fun parseStandardUri(
        raw: String,
        protocol: ProxyProtocol,
        groupId: String,
        now: Long,
    ): ProxyProfile? = runCatching {
        // Parse resiliently without letting spaces/Unicode/pipes in `#fragment` break java.net.URI
        val afterScheme = raw.substringAfter("://")
        val rawFragment = afterScheme.substringAfter('#', "")
        val withoutFragment = afterScheme.substringBefore('#')
        val rawQuery = withoutFragment.substringAfter('?', "")
        val authorityAndPath = withoutFragment.substringBefore('?')
        val authority = authorityAndPath.substringBefore('/')

        val atIdx = authority.lastIndexOf('@')
        val userInfo = if (atIdx >= 0) decodeUrl(authority.substring(0, atIdx)) else ""
        val hostPort = if (atIdx >= 0) authority.substring(atIdx + 1) else authority

        val host: String
        val port: Int
        if (hostPort.startsWith("[")) {
            val closeIdx = hostPort.indexOf(']')
            if (closeIdx <= 1) return null
            host = hostPort.substring(1, closeIdx).trim()
            val afterBracket = hostPort.substring(closeIdx + 1)
            port = afterBracket.removePrefix(":").toIntOrNull() ?: 443
        } else {
            val colonIdx = hostPort.lastIndexOf(':')
            if (colonIdx > 0) {
                host = hostPort.substring(0, colonIdx).trim()
                port = hostPort.substring(colonIdx + 1).toIntOrNull() ?: 443
            } else {
                host = hostPort.trim()
                port = 443
            }
        }
        if (host.isBlank() || port !in 1..65535) return null

        val params = parseQueryParams(rawQuery)
        val fragment = decodeUrl(rawFragment).trim()
        val displayName = fragment.ifBlank { "${protocol.displayName} • $host:$port" }

        ProxyProfile(
            id = UUID.nameUUIDFromBytes(raw.toByteArray()).toString(),
            groupId = groupId,
            name = displayName,
            protocol = protocol,
            address = host,
            port = port,
            secret = userInfo,
            sni = params["sni"].orEmpty().ifBlank { params["host"].orEmpty() },
            transport = when (val t = params["type"].orEmpty().ifBlank { "tcp" }.lowercase()) {
                "splithttp" -> "xhttp"
                else -> t
            },
            pathOrServiceName = params["path"].orEmpty().ifBlank { params["serviceName"].orEmpty() },
            tlsFingerprint = params["fp"].orEmpty().ifBlank { "chrome" },
            realityPublicKey = params["pbk"].orEmpty(),
            realityShortId = params["sid"].orEmpty(),
            rawUri = raw,
            updatedAtEpochMs = now,
        )
    }.getOrNull()

    private fun parseVmess(raw: String, groupId: String, now: Long): ProxyProfile? = runCatching {
        val b64 = raw.substringAfter("vmess://").substringBefore('#').trim()
        val jsonText = decodeBase64Safe(b64) ?: return null
        val obj = json.parseToJsonElement(jsonText).jsonObject
        val address = obj["add"]?.jsonPrimitive?.content?.trim() ?: return null
        val port = obj["port"]?.jsonPrimitive?.intOrNull
            ?: obj["port"]?.jsonPrimitive?.content?.toIntOrNull()
            ?: 443
        if (address.isBlank() || port !in 1..65535) return null
        val ps = obj["ps"]?.jsonPrimitive?.content?.trim()?.ifBlank { null } ?: "VMess • $address:$port"
        ProxyProfile(
            id = UUID.nameUUIDFromBytes(raw.toByteArray()).toString(),
            groupId = groupId,
            name = ps,
            protocol = ProxyProtocol.VMESS,
            address = address,
            port = port,
            secret = obj["id"]?.jsonPrimitive?.content.orEmpty(),
            sni = obj["sni"]?.jsonPrimitive?.content.orEmpty().ifBlank {
                obj["host"]?.jsonPrimitive?.content.orEmpty()
            },
            transport = obj["net"]?.jsonPrimitive?.content ?: "tcp",
            pathOrServiceName = obj["path"]?.jsonPrimitive?.content.orEmpty(),
            rawUri = raw,
            updatedAtEpochMs = now,
        )
    }.getOrNull()

    private fun parseShadowsocks(raw: String, groupId: String, now: Long): ProxyProfile? = runCatching {
        val withoutScheme = raw.substringAfter("ss://").trim()
        val remark = withoutScheme.substringAfter("#", "").let { decodeUrl(it) }.trim()
        val mainRaw = withoutScheme.substringBefore("#").trim()

        val mainPart = if (!mainRaw.contains('@')) {
            // Legacy SIP001: ss://base64(method:password@host:port)#remark
            val b64Only = mainRaw.substringBefore('?')
            decodeBase64Safe(b64Only) ?: mainRaw
        } else {
            mainRaw
        }
        val atIdx = mainPart.lastIndexOf('@')
        if (atIdx <= 0) return null
        val userPart = mainPart.substring(0, atIdx)
        val hostPort = mainPart.substring(atIdx + 1).substringBefore('?').substringBefore('/')
        val host = if (hostPort.startsWith("[")) {
            hostPort.substringAfter('[').substringBefore(']')
        } else {
            hostPort.substringBeforeLast(':')
        }
        val port = hostPort.substringAfterLast(':').toIntOrNull() ?: 8388
        if (host.isBlank() || port !in 1..65535) return null

        val decodedUser = decodeBase64Safe(userPart)
        val secret = if (decodedUser != null && decodedUser.contains(':')) decodedUser else decodeUrl(userPart)

        ProxyProfile(
            id = UUID.nameUUIDFromBytes(raw.toByteArray()).toString(),
            groupId = groupId,
            name = remark.ifBlank { "Shadowsocks • $host:$port" },
            protocol = ProxyProtocol.SHADOWSOCKS,
            address = host,
            port = port,
            secret = secret,
            rawUri = raw,
            updatedAtEpochMs = now,
        )
    }.getOrNull()

    private fun parseQueryParams(query: String?): Map<String, String> {
        if (query.isNullOrBlank()) return emptyMap()
        return query.split('&')
            .mapNotNull { part ->
                val idx = part.indexOf('=')
                if (idx <= 0) null
                else decodeUrl(part.substring(0, idx)) to decodeUrl(part.substring(idx + 1))
            }
            .toMap()
    }

    private val prettyJson = Json { ignoreUnknownKeys = true; prettyPrint = true }

    fun applyQuickEdit(
        existing: ProxyProfile,
        name: String,
        address: String,
        port: Int,
        secret: String,
        sni: String,
        transport: String,
        pathOrServiceName: String,
        tlsFingerprint: String,
        realityPublicKey: String,
        realityShortId: String,
        fragmentEnabled: Boolean,
        spoofLocalhost40443: Boolean,
        spoofFakeSni: String,
    ): ProxyProfile {
        val cleanName = name.trim().ifBlank { existing.name }
        val cleanAddr = address.trim().ifBlank { existing.address }
        val cleanPort = port.coerceIn(1, 65535)
        val cleanSni = sni.trim()
        val updatedRawUri = if (existing.rawUri.trimStart().startsWith("{")) {
            patchRawXrayJson(
                rawJson = existing.rawUri,
                remarks = cleanName,
                address = cleanAddr,
                port = cleanPort,
                secret = secret.trim(),
                sni = cleanSni,
                transport = transport.trim(),
                pathOrServiceName = pathOrServiceName.trim(),
            ) ?: existing.rawUri
        } else {
            existing.rawUri
        }
        return existing.copy(
            name = cleanName,
            address = cleanAddr,
            port = cleanPort,
            secret = secret.trim(),
            sni = cleanSni,
            transport = transport.trim().ifBlank { "tcp" },
            pathOrServiceName = pathOrServiceName.trim(),
            tlsFingerprint = tlsFingerprint.trim().ifBlank { "chrome" },
            realityPublicKey = realityPublicKey.trim(),
            realityShortId = realityShortId.trim(),
            fragmentEnabled = fragmentEnabled,
            spoofLocalhost40443 = spoofLocalhost40443,
            spoofFakeSni = spoofFakeSni.trim(),
            rawUri = updatedRawUri,
            updatedAtEpochMs = System.currentTimeMillis(),
        )
    }

    fun applyRawJsonEdit(existing: ProxyProfile, rawJsonText: String): Result<ProxyProfile> = runCatching {
        val obj = json.parseToJsonElement(rawJsonText.trim()) as? JsonObject
            ?: error("JSON root must be an object { ... }")
        val parsed = parseXrayJsonObject(obj, existing.groupId, System.currentTimeMillis())
            ?: error("JSON must contain at least one proxy outbound")
        parsed.copy(
            id = existing.id,
            groupId = existing.groupId,
            isPinned = existing.isPinned,
            lastPingMs = existing.lastPingMs,
            fragmentEnabled = existing.fragmentEnabled,
            spoofLocalhost40443 = existing.spoofLocalhost40443,
            spoofFakeSni = existing.spoofFakeSni,
        )
    }

    fun formatPrettyJson(rawJson: String): String = runCatching {
        val el = json.parseToJsonElement(rawJson.trim())
        prettyJson.encodeToString(JsonElement.serializer(), el)
    }.getOrDefault(rawJson)

    private fun patchRawXrayJson(
        rawJson: String,
        remarks: String,
        address: String,
        port: Int,
        secret: String,
        sni: String,
        transport: String,
        pathOrServiceName: String,
    ): String? = runCatching {
        val root = json.parseToJsonElement(rawJson).jsonObject.toMutableMap()
        root["remarks"] = kotlinx.serialization.json.JsonPrimitive(remarks)
        val nonProxyTags = setOf("freedom", "blackhole", "dns", "loopback")
        val outboundsArr = root["outbounds"] as? JsonArray ?: return@runCatching null
        var patchedPrimary = false
        val newOutbounds = outboundsArr.map { el ->
            val ob = el as? JsonObject ?: return@map el
            val tag = ob["tag"]?.jsonPrimitive?.content.orEmpty()
            val proto = ob["protocol"]?.jsonPrimitive?.content?.lowercase().orEmpty()
            val isTarget = !patchedPrimary && (
                tag.equals("proxy", ignoreCase = true) ||
                    (proto.isNotBlank() && proto !in nonProxyTags)
                )
            if (!isTarget) return@map el
            patchedPrimary = true
            val obMap = ob.toMutableMap()
            val settingsMap = (ob["settings"] as? JsonObject)?.toMutableMap() ?: mutableMapOf()
            if (settingsMap.containsKey("address") || proto == "masque") {
                settingsMap["address"] = kotlinx.serialization.json.JsonPrimitive(address)
                settingsMap["port"] = kotlinx.serialization.json.JsonPrimitive(port)
            }
            (settingsMap["vnext"] as? JsonArray)?.let { arr ->
                val list = arr.mapIndexed { idx, item ->
                    val entry = item as? JsonObject ?: return@mapIndexed item
                    if (idx != 0) return@mapIndexed item
                    val m = entry.toMutableMap()
                    m["address"] = kotlinx.serialization.json.JsonPrimitive(address)
                    m["port"] = kotlinx.serialization.json.JsonPrimitive(port)
                    if (secret.isNotBlank()) {
                        (m["users"] as? JsonArray)?.let { users ->
                            m["users"] = JsonArray(
                                users.mapIndexed { uIdx, uEl ->
                                    val uObj = uEl as? JsonObject ?: return@mapIndexed uEl
                                    if (uIdx != 0) uEl
                                    else JsonObject(uObj.toMutableMap().apply { put("id", kotlinx.serialization.json.JsonPrimitive(secret)) })
                                },
                            )
                        }
                    }
                    JsonObject(m)
                }
                settingsMap["vnext"] = JsonArray(list)
            }
            (settingsMap["servers"] as? JsonArray)?.let { arr ->
                val list = arr.mapIndexed { idx, item ->
                    val entry = item as? JsonObject ?: return@mapIndexed item
                    if (idx != 0) return@mapIndexed item
                    val m = entry.toMutableMap()
                    m["address"] = kotlinx.serialization.json.JsonPrimitive(address)
                    m["port"] = kotlinx.serialization.json.JsonPrimitive(port)
                    if (secret.isNotBlank() && m.containsKey("password")) {
                        m["password"] = kotlinx.serialization.json.JsonPrimitive(secret)
                    }
                    JsonObject(m)
                }
                settingsMap["servers"] = JsonArray(list)
            }
            obMap["settings"] = JsonObject(settingsMap)

            val streamMap = (ob["streamSettings"] as? JsonObject)?.toMutableMap()
            if (streamMap != null) {
                if (sni.isNotBlank()) {
                    (streamMap["tlsSettings"] as? JsonObject)?.let { tlsObj ->
                        streamMap["tlsSettings"] = JsonObject(
                            tlsObj.toMutableMap().apply { put("serverName", kotlinx.serialization.json.JsonPrimitive(sni)) },
                        )
                    }
                    (streamMap["realitySettings"] as? JsonObject)?.let { realObj ->
                        streamMap["realitySettings"] = JsonObject(
                            realObj.toMutableMap().apply { put("serverName", kotlinx.serialization.json.JsonPrimitive(sni)) },
                        )
                    }
                }
                if (transport.isNotBlank() && streamMap.containsKey("network")) {
                    streamMap["network"] = kotlinx.serialization.json.JsonPrimitive(transport)
                }
                if (pathOrServiceName.isNotBlank()) {
                    (streamMap["wsSettings"] as? JsonObject)?.let { wsObj ->
                        streamMap["wsSettings"] = JsonObject(
                            wsObj.toMutableMap().apply { put("path", kotlinx.serialization.json.JsonPrimitive(pathOrServiceName)) },
                        )
                    }
                    (streamMap["grpcSettings"] as? JsonObject)?.let { grpcObj ->
                        streamMap["grpcSettings"] = JsonObject(
                            grpcObj.toMutableMap().apply { put("serviceName", kotlinx.serialization.json.JsonPrimitive(pathOrServiceName)) },
                        )
                    }
                }
                obMap["streamSettings"] = JsonObject(streamMap)
            }
            JsonObject(obMap)
        }
        root["outbounds"] = JsonArray(newOutbounds)
        JsonObject(root).toString()
    }.getOrNull()

    private fun decodeUrl(value: String): String =
        runCatching { URLDecoder.decode(value, "UTF-8") }.getOrDefault(value)

    @OptIn(ExperimentalEncodingApi::class)
    private fun decodeBase64Safe(raw: String): String? = runCatching {
        val cleaned = raw.trim().replace("\n", "").replace("\r", "").replace(" ", "")
        if (cleaned.length < 4) return null
        val urlSafe = cleaned.contains('-') || cleaned.contains('_')
        val decoder = if (urlSafe) Base64.UrlSafe else Base64.Default
        val unpadded = cleaned.trimEnd('=')
        val padded = unpadded.let { s ->
            val rem = s.length % 4
            if (rem == 0) s else s + "=".repeat(4 - rem)
        }
        val bytes = decoder.decode(padded)
        val str = bytes.decodeToString()
        if (str.any { it.code < 0x09 }) return null
        str
    }.getOrNull()
}
