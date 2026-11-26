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
import java.io.File
import java.net.Inet4Address
import java.net.InetAddress
import javax.inject.Inject
import javax.inject.Singleton

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

        val configJson = MhrConfigBuilder.buildConfigJson(
            appSettings = appSettings,
            aetherSettings = aetherSettings,
            mhr = mhrSettings,
            overrideSocks5Port = aetherSettings.localSocksPort,
            overrideListenPort = if (appSettings.allowLan) appSettings.httpProxyPort else mhrSettings.listenPort,
            overrideListenHost = if (appSettings.allowLan || mhrSettings.shareOnLan) "0.0.0.0" else "127.0.0.1",
        )
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
