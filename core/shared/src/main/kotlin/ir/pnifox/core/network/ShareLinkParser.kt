package ir.pnifox.core.network

import ir.pnifox.core.model.ProxyProfile
import ir.pnifox.core.model.ProxyProtocol
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.net.URI
import java.net.URLDecoder
import java.util.UUID
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi

/**
 * Parses real proxy URIs (`vless://`, `vmess://`, `trojan://`, `ss://`, `hy2://`, `wg://`),
 * Base64 subscription payloads, and full Xray/MASQUE JSON config objects into [ProxyProfile].
 */
object ShareLinkParser {

    private val json = Json { ignoreUnknownKeys = true }

    fun parseBatch(rawInput: String, groupId: String = "manual"): List<ProxyProfile> {
        val trimmed = rawInput.trim()
        if (trimmed.isEmpty()) return emptyList()

        // 1. Direct full Xray JSON object or JSON array of configs
        if (trimmed.startsWith("{") || trimmed.startsWith("[")) {
            val fromJson = parseJsonBatch(trimmed, groupId)
            if (fromJson.isNotEmpty()) return fromJson
        }

        // 2. Decode Base64 payload if no URI scheme present (could decode to JSON or URI lines)
        val expanded = if (!trimmed.contains("://")) {
            val decoded = decodeBase64Safe(trimmed)
            if (!decoded.isNullOrBlank()) {
                val decTrim = decoded.trim()
                if (decTrim.startsWith("{") || decTrim.startsWith("[")) {
                    val fromJson = parseJsonBatch(decTrim, groupId)
                    if (fromJson.isNotEmpty()) return fromJson
                }
                decoded
            } else {
                trimmed
            }
        } else {
            trimmed
        }

        // 3. URI lines (plus optional inline JSON objects on lines)
        val results = mutableListOf<ProxyProfile>()
        expanded.lineSequence()
            .map { it.trim() }
            .forEach { line ->
                when {
                    line.contains("://") -> parseSingle(line, groupId)?.let(results::add)
                    line.startsWith("{") -> results += parseJsonBatch(line, groupId)
                }
            }
        return results
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
        // Wrapper object containing configs/servers array
        for (wrapperKey in listOf("configs", "servers", "profiles")) {
            val nested = obj[wrapperKey] as? JsonArray
            if (nested != null) {
                return nested.mapNotNull { el ->
                    (el as? JsonObject)?.let { parseXrayJsonObject(it, groupId, now) }
                }
            }
        }
        return listOfNotNull(parseXrayJsonObject(obj, groupId, now))
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

    fun parseSingle(uriText: String, groupId: String = "manual"): ProxyProfile? {
        val raw = uriText.trim()
        val now = System.currentTimeMillis()
        return when {
            raw.startsWith("vless://", ignoreCase = true) -> parseStandardUri(raw, ProxyProtocol.VLESS, groupId, now)
            raw.startsWith("trojan://", ignoreCase = true) -> parseStandardUri(raw, ProxyProtocol.TROJAN, groupId, now)
            raw.startsWith("hy2://", ignoreCase = true) ||
                raw.startsWith("hysteria2://", ignoreCase = true) -> parseStandardUri(raw, ProxyProtocol.HYSTERIA2, groupId, now)
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
        val parsed = URI(raw)
        val host = parsed.host ?: return null
        val port = if (parsed.port > 0) parsed.port else 443
        val params = parseQueryParams(parsed.rawQuery)
        val fragment = parsed.rawFragment?.let { decodeUrl(it) }.orEmpty()
        val displayName = fragment.ifBlank { "${protocol.displayName} • $host:$port" }

        ProxyProfile(
            id = UUID.nameUUIDFromBytes(raw.toByteArray()).toString(),
            groupId = groupId,
            name = displayName,
            protocol = protocol,
            address = host,
            port = port,
            secret = parsed.userInfo.orEmpty(),
            sni = params["sni"].orEmpty().ifBlank { params["host"].orEmpty() },
            transport = params["type"].orEmpty().ifBlank { "tcp" },
            pathOrServiceName = params["path"].orEmpty().ifBlank { params["serviceName"].orEmpty() },
            tlsFingerprint = params["fp"].orEmpty().ifBlank { "chrome" },
            realityPublicKey = params["pbk"].orEmpty(),
            realityShortId = params["sid"].orEmpty(),
            rawUri = raw,
            updatedAtEpochMs = now,
        )
    }.getOrNull()

    private fun parseVmess(raw: String, groupId: String, now: Long): ProxyProfile? = runCatching {
        val b64 = raw.substringAfter("vmess://").trim()
        val jsonText = decodeBase64Safe(b64) ?: return null
        val obj = json.parseToJsonElement(jsonText).jsonObject
        val address = obj["add"]?.jsonPrimitive?.content ?: return null
        val port = obj["port"]?.jsonPrimitive?.content?.toIntOrNull() ?: 443
        val ps = obj["ps"]?.jsonPrimitive?.content?.ifBlank { null } ?: "VMess • $address:$port"
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
        val withoutScheme = raw.substringAfter("ss://")
        val remark = withoutScheme.substringAfter("#", "").let { decodeUrl(it) }
        val mainPart = withoutScheme.substringBefore("#")
        val atIdx = mainPart.lastIndexOf('@')
        if (atIdx <= 0) return null
        val userPart = mainPart.substring(0, atIdx)
        val hostPort = mainPart.substring(atIdx + 1).substringBefore('?')
        val host = hostPort.substringBeforeLast(':')
        val port = hostPort.substringAfterLast(':').toIntOrNull() ?: 8388
        val secret = decodeBase64Safe(userPart) ?: userPart
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
        val cleaned = raw.trim().replace("\n", "").replace("\r", "")
        val urlSafe = cleaned.contains('-') || cleaned.contains('_')
        val decoder = if (urlSafe) Base64.UrlSafe else Base64.Default
        val padded = cleaned.let { s ->
            val rem = s.length % 4
            if (rem == 0) s else s + "=".repeat(4 - rem)
        }
        decoder.decode(padded).decodeToString()
    }.getOrNull()
}
