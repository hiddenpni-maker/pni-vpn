package ir.pnifox.core.vpn

import android.content.Context
import com.therealaleph.mhrv.Native
import dagger.hilt.android.qualifiers.ApplicationContext
import ir.pnifox.core.model.AetherSettings
import ir.pnifox.core.model.AppSettings
import ir.pnifox.core.model.ConnectionError
import ir.pnifox.core.model.LogLevel
import ir.pnifox.core.model.MhrDefaultSniPool
import ir.pnifox.core.model.MhrMode
import ir.pnifox.core.model.MhrSettings
import ir.pnifox.core.model.MhrSniTestResult
import ir.pnifox.core.model.MhrStatsSnapshot
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import kotlinx.serialization.json.put
import java.io.File
import java.net.Inet4Address
import java.net.InetAddress
import javax.inject.Inject
import javax.inject.Singleton

object MhrConfigBuilder {

    private val parserJson = Json { ignoreUnknownKeys = true }

    fun buildConfigJson(
        appSettings: AppSettings,
        aetherSettings: AetherSettings,
        mhr: MhrSettings,
    ): String {
        val cleanIds = mhr.scriptIds
            .map { MhrDefaultSniPool.extractDeploymentId(it) }
            .filter { it.isNotBlank() }
            .distinct()

        val httpListenPort = if (appSettings.allowLan) appSettings.httpProxyPort else mhr.listenPort
        val listenHost = if (appSettings.allowLan) "0.0.0.0" else "127.0.0.1"

        val obj = buildJsonObject {
            put("mode", mhr.mode.wireValue)
            put("listen_host", listenHost)
            put("listen_port", httpListenPort)
            put("socks5_port", aetherSettings.localSocksPort)
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

@Singleton
class MhrEngineManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val logs: LogRingBuffer,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var monitorJob: Job? = null

    @Volatile
    private var activeHandle: Long = 0L

    private val _statsFlow = MutableStateFlow<MhrStatsSnapshot?>(null)
    val statsFlow: StateFlow<MhrStatsSnapshot?> = _statsFlow.asStateFlow()

    val isRunning: Boolean
        get() = activeHandle > 0L

    fun mhrDataDir(): File = File(context.filesDir, "mhr").apply { mkdirs() }

    fun coreVersion(): String = if (Native.ensureLoaded()) {
        runCatching { Native.version() }.getOrDefault("1.9.38")
    } else {
        "unavailable"
    }

    fun start(
        appSettings: AppSettings,
        aetherSettings: AetherSettings,
        mhrSettings: MhrSettings,
    ): ConnectionError? {
        stop()

        if (!Native.ensureLoaded()) {
            return ConnectionError.MissingBinary(
                binaryName = "libmhrv_rs.so",
                expectedAbiPath = "app/src/main/jniLibs/arm64-v8a/libmhrv_rs.so",
                buildHint = "Run scripts/build-binaries.sh to bundle libmhrv_rs.so.",
            )
        }

        val cleanIds = mhrSettings.scriptIds
            .map { MhrDefaultSniPool.extractDeploymentId(it) }
            .filter { it.isNotBlank() }

        if ((mhrSettings.mode == MhrMode.APPS_SCRIPT || mhrSettings.mode == MhrMode.FULL) && cleanIds.isEmpty()) {
            return ConnectionError.ProcessExited(
                -1,
                "Add at least one Google Apps Script Deployment ID (or switch MHR Mode to 'Direct (SNI Front)' first to open script.google.com).",
            )
        }

        val dir = mhrDataDir()
        Native.setDataDir(dir.absolutePath)

        val configJson = MhrConfigBuilder.buildConfigJson(appSettings, aetherSettings, mhrSettings)
        File(dir, "config.json").writeText(configJson)

        logs.append(
            "MHR",
            LogLevel.INFO,
            "Starting mhrv-rs v${coreVersion()} mode=${mhrSettings.mode.wireValue} ip=${mhrSettings.googleIp} socks5=127.0.0.1:${aetherSettings.localSocksPort}",
        )

        val handle = Native.startProxy(configJson)
        drainIntoLogs()
        if (handle <= 0L) {
            return ConnectionError.ProcessExited(
                -1,
                "MHR native proxy failed to start (check google_ip, deployment IDs, and logs).",
            )
        }
        activeHandle = handle

        monitorJob = scope.launch {
            while (isActive && activeHandle == handle) {
                drainIntoLogs()
                val rawStats = runCatching { Native.statsJson(handle) }.getOrDefault("")
                if (rawStats.isNotBlank()) {
                    _statsFlow.value = MhrConfigBuilder.parseStatsJson(rawStats)
                }
                delay(700L)
            }
        }
        return null
    }

    fun stop() {
        monitorJob?.cancel()
        monitorJob = null
        val h = activeHandle
        activeHandle = 0L
        if (h > 0L) {
            runCatching { Native.stopProxy(h) }
            drainIntoLogs()
            logs.append("MHR", LogLevel.INFO, "Stopped MasterHttpRelay engine.")
        }
        _statsFlow.value = null
    }

    suspend fun testSni(googleIp: String, sni: String): MhrSniTestResult = withContext(Dispatchers.IO) {
        if (!Native.ensureLoaded()) {
            return@withContext MhrSniTestResult(host = sni, ok = false, error = "libmhrv_rs.so not loaded")
        }
        Native.setDataDir(mhrDataDir().absolutePath)
        val raw = runCatching { Native.testSni(googleIp.trim(), sni.trim()) }.getOrElse { ex ->
            return@withContext MhrSniTestResult(host = sni, ok = false, error = ex.message ?: "JNI error")
        }
        drainIntoLogs()
        MhrConfigBuilder.parseTestSniJson(sni.trim(), raw)
    }

    suspend fun autoDetectGoogleIp(frontDomain: String = "www.google.com"): String? = withContext(Dispatchers.IO) {
        val host = frontDomain.trim().ifBlank { "www.google.com" }
        val resolved = runCatching {
            InetAddress.getAllByName(host)
                .filterIsInstance<Inet4Address>()
                .firstOrNull()
                ?.hostAddress
        }.getOrNull()
        if (resolved != null) {
            logs.append("MHR", LogLevel.INFO, "Resolved Google edge IP for $host -> $resolved")
        } else {
            logs.append("MHR", LogLevel.WARN, "Failed to auto-detect IPv4 for $host via system DNS")
        }
        resolved
    }

    fun readBundledScript(fullTunnel: Boolean): String {
        val assetPath = if (fullTunnel) "mhr/CodeFull.gs" else "mhr/Code.gs"
        return runCatching {
            context.assets.open(assetPath).bufferedReader(Charsets.UTF_8).use { it.readText() }
        }.getOrDefault("// Unable to load bundled $assetPath")
    }

    private fun drainIntoLogs() {
        val blob = runCatching { Native.drainLogs() }.getOrDefault("")
        if (blob.isBlank()) return
        blob.lineSequence()
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .forEach { line ->
                val lvl = when {
                    line.contains("ERROR", ignoreCase = true) -> LogLevel.ERROR
                    line.contains("WARN", ignoreCase = true) -> LogLevel.WARN
                    else -> LogLevel.INFO
                }
                logs.append("MHR", lvl, line)
            }
    }
}
