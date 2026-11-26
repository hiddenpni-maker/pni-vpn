package ir.pnifox.core.vpn

import ir.pnifox.core.model.AetherSettings
import ir.pnifox.core.model.AppSettings
import ir.pnifox.core.model.MhrDefaultSniPool
import ir.pnifox.core.model.MhrSettings
import ir.pnifox.core.model.MhrSniTestResult
import ir.pnifox.core.model.MhrStatsSnapshot
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import kotlinx.serialization.json.put

object MhrConfigBuilder {

    private val parserJson = Json { ignoreUnknownKeys = true }

    fun buildConfigJson(
        appSettings: AppSettings,
        aetherSettings: AetherSettings,
        mhr: MhrSettings,
        overrideSocks5Port: Int? = null,
        overrideListenPort: Int? = null,
        overrideListenHost: String? = null,
    ): String {
        val cleanIds = mhr.scriptIds
            .map { MhrDefaultSniPool.extractDeploymentId(it) }
            .filter { it.isNotBlank() }
            .distinct()

        val httpListenPort = (overrideListenPort ?: mhr.listenPort).coerceIn(1024, 65535)
        val rawSocksPort = (overrideSocks5Port ?: mhr.socks5Port).coerceIn(1024, 65535)
        val socksListenPort = if (rawSocksPort == httpListenPort) {
            if (httpListenPort < 65535) httpListenPort + 1 else httpListenPort - 1
        } else {
            rawSocksPort
        }
        val listenHost = overrideListenHost
            ?: if (mhr.shareOnLan || appSettings.allowLan) "0.0.0.0" else "127.0.0.1"

        val obj = buildJsonObject {
            put("mode", mhr.mode.wireValue)
            put("listen_host", listenHost)
            put("listen_port", httpListenPort)
            put("socks5_port", socksListenPort)
            put("script_ids", buildJsonArray { cleanIds.forEach { add(JsonPrimitive(it)) } })
            put("auth_key", mhr.authKey.trim())
            put("front_domain", mhr.frontDomain.trim().ifBlank { "www.google.com" })
            val cleanSnis = mhr.sniHosts.map { it.trim() }.filter { it.isNotBlank() }.distinct()
            if (cleanSnis.isNotEmpty()) {
                put("sni_hosts", buildJsonArray { cleanSnis.forEach { add(JsonPrimitive(it)) } })
            }
            put("google_ip", mhr.googleIp.trim().ifBlank { "216.239.38.120" })
            put("verify_ssl", mhr.verifySsl)
            put("log_level", mhr.logLevel.ifBlank { "info" })
            put("parallel_relay", mhr.parallelRelay.coerceIn(1, 16))
            if (mhr.forceHttp1) put("force_http1", true)
            put("block_quic", mhr.blockQuic)
            put("block_stun", mhr.blockStun)
            if (mhr.upstreamSocks5.isNotBlank()) {
                put("upstream_socks5", mhr.upstreamSocks5.trim())
            }
            val passthrough = mhr.passthroughHosts.map { it.trim() }.filter { it.isNotBlank() }.distinct()
            if (passthrough.isNotEmpty()) {
                put("passthrough_hosts", buildJsonArray { passthrough.forEach { add(JsonPrimitive(it)) } })
            }
            put("tunnel_doh", mhr.tunnelDoh)
            put("block_doh", mhr.blockDoh)
            if (mhr.youtubeViaRelay) put("youtube_via_relay", true)
            val dohBypass = mhr.bypassDohHosts.map { it.trim() }.filter { it.isNotBlank() }.distinct()
            if (dohBypass.isNotEmpty()) {
                put("bypass_doh_hosts", buildJsonArray { dohBypass.forEach { add(JsonPrimitive(it)) } })
            }
            put("fetch_ips_from_api", false)
            put("max_ips_to_scan", 20)
        }
        return obj.toString()
    }

    fun parseStatsJson(raw: String): MhrStatsSnapshot? {
        if (raw.isBlank()) return null
        return runCatching {
            val obj = parserJson.parseToJsonElement(raw) as? JsonObject ?: return null
            MhrStatsSnapshot(
                todayCalls = obj["today_calls"]?.jsonPrimitive?.longOrNull ?: 0L,
                todayBytes = obj["today_bytes"]?.jsonPrimitive?.longOrNull ?: 0L,
                todayKey = obj["today_key"]?.jsonPrimitive?.content.orEmpty(),
                todayResetSecs = obj["today_reset_secs"]?.jsonPrimitive?.longOrNull ?: 0L,
            )
        }.getOrNull()
    }

    fun parseTestSniJson(host: String, raw: String): MhrSniTestResult {
        if (raw.isBlank()) {
            return MhrSniTestResult(host = host, ok = false, error = "Empty response from native probe")
        }
        return runCatching {
            val obj = parserJson.parseToJsonElement(raw) as? JsonObject
                ?: return MhrSniTestResult(host = host, ok = false, error = "Invalid JSON")
            val ok = obj["ok"]?.jsonPrimitive?.booleanOrNull ?: false
            val latencyMs = obj["latencyMs"]?.jsonPrimitive?.longOrNull ?: 0L
            val err = obj["error"]?.jsonPrimitive?.content.orEmpty()
            MhrSniTestResult(
                host = host,
                ok = ok,
                latencyMs = latencyMs,
                status = if (ok) "${latencyMs}ms" else err.ifBlank { "Failed" },
                error = err,
            )
        }.getOrElse { ex ->
            MhrSniTestResult(host = host, ok = false, error = ex.message ?: "Parse failure")
        }
    }
}
