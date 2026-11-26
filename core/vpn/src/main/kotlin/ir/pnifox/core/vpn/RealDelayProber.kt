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
import java.io.ByteArrayOutputStream
import java.io.File
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.HttpURLConnection
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.Socket
import java.net.URL
import javax.inject.Inject
import javax.inject.Singleton
import javax.net.ssl.SNIHostName
import javax.net.ssl.SSLSocket
import javax.net.ssl.SSLSocketFactory

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
    ): Int? = runCatching {
        val rawSocket = Socket()
        rawSocket.soTimeout = timeoutMs
        rawSocket.connect(InetSocketAddress(socksHost, socksPort), 1500)

        val out = rawSocket.getOutputStream()
        val ins = rawSocket.getInputStream()

        // 1. SOCKS5 NO-AUTH greeting
        out.write(byteArrayOf(0x05, 0x01, 0x00))
        out.flush()
        val greet = ByteArray(2)
        readExact(ins, greet)
        check(greet[0] == 0x05.toByte() && greet[1] == 0x00.toByte()) { "SOCKS5 greeting rejected" }

        val tStart = System.nanoTime()

        // 2. SOCKS5 CONNECT with remote domain resolution (ATYP = 0x03)
        val hostBytes = targetHost.toByteArray(Charsets.US_ASCII)
        val req = ByteArrayOutputStream(7 + hostBytes.size)
        req.write(byteArrayOf(0x05, 0x01, 0x00, 0x03, hostBytes.size.toByte()))
        req.write(hostBytes)
        req.write((targetPort ushr 8) and 0xFF)
        req.write(targetPort and 0xFF)
        out.write(req.toByteArray())
        out.flush()

        val repHead = ByteArray(4)
        readExact(ins, repHead)
        check(repHead[0] == 0x05.toByte() && repHead[1] == 0x00.toByte()) { "SOCKS5 CONNECT error ${repHead[1]}" }
        when (repHead[3].toInt() and 0xFF) {
            0x01 -> readExact(ins, ByteArray(4 + 2))
            0x04 -> readExact(ins, ByteArray(16 + 2))
            0x03 -> {
                val len = ins.read()
                check(len > 0)
                readExact(ins, ByteArray(len + 2))
            }
        }

        // 3. TLS handshake + HTTP/1.1 GET /generate_204 over the tunnel socket
        val factory = SSLSocketFactory.getDefault() as SSLSocketFactory
        val ssl = factory.createSocket(rawSocket, targetHost, targetPort, true) as SSLSocket
        ssl.use { tlsSock ->
            tlsSock.soTimeout = timeoutMs
            val params = tlsSock.sslParameters
            params.serverNames = listOf(SNIHostName(targetHost))
            tlsSock.sslParameters = params
            tlsSock.startHandshake()

            val httpReq = "GET $path HTTP/1.1\r\nHost: $targetHost\r\nUser-Agent: pnifox/2.0\r\nConnection: close\r\n\r\n"
            tlsSock.outputStream.write(httpReq.toByteArray(Charsets.US_ASCII))
            tlsSock.outputStream.flush()

            val line = tlsSock.inputStream.bufferedReader().readLine() ?: error("Empty HTTP response")
            val statusCode = line.split(' ').getOrNull(1)?.toIntOrNull() ?: error("Malformed HTTP line: $line")
            check(statusCode in 200..499) { "HTTP error $statusCode" }
        }
        ((System.nanoTime() - tStart) / 1_000_000L).toInt().coerceAtLeast(1)
    }.getOrNull()

    fun measureDnsResolverLatencyMs(dns: DnsTunnelSettings, timeoutMs: Int = 3500): Int? = runCatching {
        val raw = dns.resolverAddress.trim()
        if (dns.mode == DnsTunnelMode.DOH || raw.startsWith("https://", ignoreCase = true)) {
            val url = if (raw.startsWith("http")) raw else "https://$raw/dns-query"
            val tStart = System.nanoTime()
            val conn = (URL(url).openConnection() as HttpURLConnection).apply {
                connectTimeout = timeoutMs
                readTimeout = timeoutMs
                requestMethod = "GET"
            }
            val code = conn.responseCode
            conn.disconnect()
            check(code < 500)
            return@runCatching ((System.nanoTime() - tStart) / 1_000_000L).toInt().coerceAtLeast(1)
        }
        val host = raw.removePrefix("quic://").removePrefix("tls://").substringBefore('/').substringBefore(':')
        val baos = ByteArrayOutputStream()
        baos.write(byteArrayOf(0x41, 0x42, 0x01, 0x00, 0x00, 0x01, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00))
        "google.com".split('.').forEach { label ->
            val bytes = label.toByteArray(Charsets.US_ASCII)
            baos.write(bytes.size)
            baos.write(bytes)
        }
        baos.write(0x00)
        baos.write(byteArrayOf(0x00, 0x01, 0x00, 0x01))
        val query = baos.toByteArray()
        val tStart = System.nanoTime()
        DatagramSocket().use { s ->
            s.soTimeout = timeoutMs
            s.send(DatagramPacket(query, query.size, InetAddress.getByName(host), 53))
            s.receive(DatagramPacket(ByteArray(512), 512))
        }
        ((System.nanoTime() - tStart) / 1_000_000L).toInt().coerceAtLeast(1)
    }.getOrNull()

    private fun readExact(ins: java.io.InputStream, buf: ByteArray) {
        var offset = 0
        while (offset < buf.size) {
            val n = ins.read(buf, offset, buf.size - offset)
            if (n < 0) error("Unexpected EOF")
            offset += n
        }
    }

    private fun findUnusedLocalPort(): Int? = runCatching {
        ServerSocket(0).use { it.localPort }
    }.getOrNull()

    companion object {
        const val DEFAULT_PROBE_URL = "https://www.gstatic.com/generate_204"
    }
}
