package ir.pnifox.core.network

import ir.pnifox.core.model.MhrDefaultSniPool
import ir.pnifox.core.model.MhrMode
import ir.pnifox.core.model.MhrSettings
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import java.io.ByteArrayOutputStream
import java.util.zip.DeflaterOutputStream
import java.util.zip.Inflater
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi

object MhrShareCodec {

    const val PREFIX = "mhrv-rs://"
    private val json = Json { ignoreUnknownKeys = true }

    @OptIn(ExperimentalEncodingApi::class)
    fun encode(settings: MhrSettings): String {
        val obj = buildJsonObject {
            put("mode", settings.mode.wireValue)
            put("script_ids", buildJsonArray { settings.scriptIds.forEach { add(JsonPrimitive(it)) } })
            put("auth_key", settings.authKey)
            put("front_domain", settings.frontDomain)
            put("google_ip", settings.googleIp)
            put("sni_hosts", buildJsonArray { settings.sniHosts.forEach { add(JsonPrimitive(it)) } })
            put("verify_ssl", settings.verifySsl)
            put("parallel_relay", settings.parallelRelay)
            put("force_http1", settings.forceHttp1)
            put("youtube_via_relay", settings.youtubeViaRelay)
            put("block_doh", settings.blockDoh)
            put("tunnel_doh", settings.tunnelDoh)
        }.toString().toByteArray(Charsets.UTF_8)

        val compressed = ByteArrayOutputStream().use { bos ->
            DeflaterOutputStream(bos).use { it.write(obj) }
            bos.toByteArray()
        }
        val b64 = Base64.UrlSafe.encode(compressed).trimEnd('=')
        return "$PREFIX$b64"
    }

    @OptIn(ExperimentalEncodingApi::class)
    fun decode(rawInput: String, base: MhrSettings = MhrSettings()): Result<MhrSettings> = runCatching {
        val trimmed = rawInput.trim()
        require(trimmed.startsWith(PREFIX, ignoreCase = true)) {
            "Expected mhrv-rs:// share link"
        }
        val payload = trimmed.substring(PREFIX.length).substringBefore('#').trim()
        val padded = payload + "=".repeat((4 - (payload.length % 4)) % 4)
        val rawBytes = Base64.UrlSafe.decode(padded)
        val jsonText = inflateOrRaw(rawBytes)
        val obj = json.parseToJsonElement(jsonText) as? JsonObject
            ?: error("Invalid mhrv-rs payload")

        val mode = when (obj["mode"]?.jsonPrimitive?.content) {
            "direct", "google_only" -> MhrMode.DIRECT
            "full" -> MhrMode.FULL
            else -> MhrMode.APPS_SCRIPT
        }
        val ids = ((obj["script_ids"] as? JsonArray) ?: (obj["apps_script_urls"] as? JsonArray))
            ?.mapNotNull { MhrDefaultSniPool.extractDeploymentId(it.jsonPrimitive.content).ifBlank { null } }
            ?.distinct()
            ?: base.scriptIds
        val snis = (obj["sni_hosts"] as? JsonArray)
            ?.mapNotNull { it.jsonPrimitive.content.trim().ifBlank { null } }
            ?.distinct()
            ?.ifEmpty { base.sniHosts }
            ?: base.sniHosts

        base.copy(
            mode = mode,
            scriptIds = ids,
            authKey = obj["auth_key"]?.jsonPrimitive?.content ?: base.authKey,
            frontDomain = obj["front_domain"]?.jsonPrimitive?.content?.ifBlank { base.frontDomain } ?: base.frontDomain,
            googleIp = obj["google_ip"]?.jsonPrimitive?.content?.ifBlank { base.googleIp } ?: base.googleIp,
            sniHosts = snis,
            verifySsl = obj["verify_ssl"]?.jsonPrimitive?.booleanOrNull ?: base.verifySsl,
            parallelRelay = obj["parallel_relay"]?.jsonPrimitive?.intOrNull ?: base.parallelRelay,
            forceHttp1 = obj["force_http1"]?.jsonPrimitive?.booleanOrNull ?: base.forceHttp1,
            youtubeViaRelay = obj["youtube_via_relay"]?.jsonPrimitive?.booleanOrNull ?: base.youtubeViaRelay,
            blockDoh = obj["block_doh"]?.jsonPrimitive?.booleanOrNull ?: base.blockDoh,
            tunnelDoh = obj["tunnel_doh"]?.jsonPrimitive?.booleanOrNull ?: base.tunnelDoh,
        )
    }

    private fun inflateOrRaw(bytes: ByteArray): String = runCatching {
        val inflater = Inflater()
        inflater.setInput(bytes)
        val out = ByteArrayOutputStream()
        val buf = ByteArray(1024)
        while (!inflater.finished()) {
            val count = inflater.inflate(buf)
            if (count == 0) break
            out.write(buf, 0, count)
        }
        inflater.end()
        out.toByteArray().toString(Charsets.UTF_8)
    }.getOrElse {
        bytes.toString(Charsets.UTF_8)
    }
}
