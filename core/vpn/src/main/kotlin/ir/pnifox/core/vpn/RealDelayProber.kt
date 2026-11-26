package ir.pnifox.core.vpn

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import ir.pnifox.core.model.AppSettings
import ir.pnifox.core.model.CoreEngine
import ir.pnifox.core.model.DnsEngineType
import ir.pnifox.core.model.DnsTunnelMode
import ir.pnifox.core.model.DnsTunnelSettings
import ir.pnifox.core.model.ProxyProfile
import ir.pnifox.core.model.ProxyProtocol
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.net.ServerSocket
import java.io.File
import java.net.InetSocketAddress
import java.net.Socket
import javax.inject.Inject
import javax.inject.Singleton

sealed interface ProfilePingResult {
    data class Success(val latencyMs: Int) : ProfilePingResult
    data class XrayBusyWithOtherProfile(val activeProfileId: String?) : ProfilePingResult
    data class Failed(val reason: String) : ProfilePingResult
}

/**
 * Measures real end-to-end HTTP delay (`generate_204`) via `Pnixray.measureDelay()` or SOCKS5
 * remote-hostname (`ATYP=0x03`) HTTPS probes rather than misleading raw TCP connect times.
 */
@Singleton
class RealDelayProber @Inject constructor(
    @ApplicationContext private val context: Context,
    private val processManager: NativeProcessManager,
    private val logs: LogRingBuffer,
) {
    private val xrayProbeMutex = Mutex()

    suspend fun measureActiveTunnelDelayMs(
        engine: CoreEngine,
        localSocksPort: Int,
        dnsSettings: DnsTunnelSettings? = null,
        timeoutMs: Int = 5500,
    ): Int? = withContext(Dispatchers.IO) {
        if (dnsSettings?.engineType == DnsEngineType.DNS_CHANGER && engine == CoreEngine.XRAY) {
            return@withContext measureDnsResolverLatencyMs(dnsSettings, timeoutMs)
        }
        if (engine == CoreEngine.XRAY && processManager.isXrayActive()) {
            val ms = measureXrayRunningDelay(DEFAULT_PROBE_URL, timeoutMs)
            if (ms != null) return@withContext ms
        }
        probeHttp204ViaSocks5("127.0.0.1", localSocksPort, timeoutMs = timeoutMs)
    }

    suspend fun measureProfileRealDelayMs(
        profile: ProxyProfile,
        appSettings: AppSettings,
        timeoutMs: Int = 6000,
    ): ProfilePingResult = withContext(Dispatchers.IO) {
        val isRawJson = profile.rawUri.trimStart().startsWith("{")
        if (!isRawJson && (profile.protocol == ProxyProtocol.HYSTERIA2 || profile.protocol == ProxyProtocol.WIREGUARD)) {
            return@withContext measureSingBoxProfileDelay(profile, appSettings, timeoutMs)
        }
        measureXrayProfileDelay(profile, appSettings, timeoutMs)
    }

    private suspend fun measureXrayProfileDelay(
        profile: ProxyProfile,
        appSettings: AppSettings,
        timeoutMs: Int,
    ): ProfilePingResult {
        if (processManager.isXrayActive()) {
            if (processManager.activeXrayProfileId == profile.id) {
                val ms = measureXrayRunningDelay(DEFAULT_PROBE_URL, timeoutMs)
                return if (ms != null) ProfilePingResult.Success(ms)
                else ProfilePingResult.Failed("Timeout / Unreachable")
            }
            return ProfilePingResult.XrayBusyWithOtherProfile(processManager.activeXrayProfileId)
        }

        return xrayProbeMutex.withLock {
            if (processManager.isXrayActive()) {
                return@withLock ProfilePingResult.XrayBusyWithOtherProfile(processManager.activeXrayProfileId)
            }
            val hasGeo = processManager.ensureGeoAssets()
            val freePort = findUnusedLocalPort() ?: 10835
            val cfg = ConfigBuilders.buildXrayConfig(
                profile = profile,
                appSettings = appSettings,
                socksPort = freePort,
                errorLogPath = null,
                hasGeoAssets = hasGeo,
            )
            var startedTemp = false
            try {
                runCatching {
                    Class.forName("go.Seq").getMethod("setContext", Context::class.java).invoke(null, context)
                }
                val cls = Class.forName("ir.pni.core.pnixray.Pnixray")
                cls.getMethod("initEnv", String::class.java).invoke(null, processManager.geoDir.absolutePath)
                cls.getMethod("startXray", String::class.java).invoke(null, cfg)
                startedTemp = true
                val ms = measureXrayRunningDelay(DEFAULT_PROBE_URL, timeoutMs)
                if (ms != null) ProfilePingResult.Success(ms) else ProfilePingResult.Failed("Timeout / No response")
            } catch (t: Throwable) {
                val msg = (t.cause ?: t).message ?: t::class.java.simpleName
                logs.append("RealPing", LogLevel.WARN, "${profile.name}: $msg")
                ProfilePingResult.Failed(msg)
            } finally {
                if (startedTemp) {
                    runCatching {
                        Class.forName("ir.pni.core.pnixray.Pnixray").getMethod("stopXray").invoke(null)
                    }
                }
            }
        }
    }

    private suspend fun measureSingBoxProfileDelay(
        profile: ProxyProfile,
        appSettings: AppSettings,
        timeoutMs: Int,
    ): ProfilePingResult {
        val sbBin = File(context.applicationInfo.nativeLibraryDir, "libpnisingbox.so")
        if (!sbBin.exists()) return ProfilePingResult.Failed("libpnisingbox.so missing")
        val freePort = findUnusedLocalPort() ?: 10836
        val tmpCfg = File(context.cacheDir, "ping-sb-${System.nanoTime()}.json")
        var proc: Process? = null
        return try {
            tmpCfg.writeText(ConfigBuilders.buildSingBoxConfig(profile, appSettings, socksPort = freePort))
            proc = ProcessBuilder(sbBin.absolutePath, "--mode", "singbox", "--config", tmpCfg.absolutePath)
                .redirectErrorStream(true)
                .start()
            val deadline = System.currentTimeMillis() + 2500L
            var portUp = false
            while (System.currentTimeMillis() < deadline) {
                val alive = runCatching { proc.exitValue(); false }.getOrDefault(true)
                if (!alive) break
                val ok = runCatching {
                    Socket().use { s -> s.connect(InetSocketAddress("127.0.0.1", freePort), 250) }
                    true
                }.getOrDefault(false)
                if (ok) {
                    portUp = true
                    break
                }
                delay(120L)
            }
            if (!portUp) return ProfilePingResult.Failed("Sing-box startup timeout")
            val ms = probeHttp204ViaSocks5("127.0.0.1", freePort, timeoutMs = timeoutMs)
            if (ms != null) ProfilePingResult.Success(ms) else ProfilePingResult.Failed("Timeout / No 204 response")
        } catch (t: Throwable) {
            ProfilePingResult.Failed(t.message ?: "Sing-box ping failed")
        } finally {
            runCatching { proc?.destroy() }
            runCatching { proc?.destroyForcibly() }
            runCatching { tmpCfg.delete() }
        }
    }

    private fun measureXrayRunningDelay(probeUrl: String, timeoutMs: Int): Int? = runCatching {
        val cls = Class.forName("ir.pni.core.pnixray.Pnixray")
        val res = cls.getMethod("measureDelay", String::class.java, Int::class.javaPrimitiveType)
            .invoke(null, probeUrl, timeoutMs) as Long
        if (res >= 0L) res.toInt().coerceAtLeast(1) else null
    }.getOrNull()

    fun probeHttp204ViaSocks5(
        socksHost: String,
        socksPort: Int,
        targetHost: String = "www.gstatic.com",
        targetPort: Int = 443,
        path: String = "/generate_204",
        timeoutMs: Int = 5500,
    ): Int? = RealDelaySocketProber.probeHttp204ViaSocks5(
        socksHost = socksHost,
        socksPort = socksPort,
        targetHost = targetHost,
        targetPort = targetPort,
        path = path,
        timeoutMs = timeoutMs,
    )

    fun measureDnsResolverLatencyMs(dns: DnsTunnelSettings, timeoutMs: Int = 3500): Int? =
        RealDelaySocketProber.measureDnsResolverLatencyMs(dns, timeoutMs)

    private fun findUnusedLocalPort(): Int? = runCatching {
        ServerSocket(0).use { it.localPort }
    }.getOrNull()

    companion object {
        const val DEFAULT_PROBE_URL = RealDelaySocketProber.DEFAULT_PROBE_URL
    }
}
