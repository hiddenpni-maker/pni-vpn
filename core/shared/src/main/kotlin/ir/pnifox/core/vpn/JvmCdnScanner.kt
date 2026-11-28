package ir.pnifox.core.vpn

import ir.pnifox.core.model.ScanStyle
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import java.net.InetSocketAddress
import java.net.Socket
import java.util.concurrent.atomic.AtomicInteger
import javax.net.ssl.SNIHostName
import javax.net.ssl.SSLSocket
import javax.net.ssl.SSLSocketFactory
import kotlin.math.abs
import kotlin.random.Random

data class JvmScanCandidateResult(
    val ip: String,
    val port: Int,
    val provider: String,
    val latencyMs: Int,
    val minLatencyMs: Int = latencyMs,
    val lossPct: Double,
    val jitterMs: Int,
    val tlsHandshakeMs: Int? = null,
    val downloadSpeedKbps: Int = 0,
    val colo: String = "",
    val grade: String,
    val score: Double,
)

data class JvmScanProgress(
    val tested: Int,
    val total: Int,
    val healthy: Int,
)

object JvmCdnScanner {

    /**
     * Expands diverse random IPv4 samples across ALL supplied CIDR blocks instead of reading
     * linearly from the top of the list, using full 32-bit bitmask arithmetic for any /0../32 prefix.
     */
    fun expandCidrs(
        cidrs: List<String>,
        maxSamples: Int,
        excludeIps: Set<String> = emptySet(),
    ): List<String> {
        val cleaned = cidrs
            .flatMap { it.split(',', ';', '\n', '\r', '\t', ' ') }
            .map { it.trim() }
            .filter { it.isNotBlank() && !it.startsWith("#") && !it.contains(':') }
            .distinct()
        if (cleaned.isEmpty()) return emptyList()

        val singles = mutableListOf<String>()
        val cidrEntries = mutableListOf<Triple<Long, Int, Long>>()
        for (token in cleaned.shuffled(Random)) {
            if (token.contains('-') && !token.contains('/')) {
                val startLong = ipToLong(token.substringBefore('-').trim())
                val endLong = ipToLong(token.substringAfter('-').trim())
                if (startLong != null && endLong != null && endLong >= startLong) {
                    val count = (endLong - startLong + 1L).coerceAtMost(maxSamples.toLong() * 2L)
                    val step = ((endLong - startLong + 1L) / count.coerceAtLeast(1L)).coerceAtLeast(1L)
                    var cur = startLong
                    while (cur <= endLong && singles.size < maxSamples * 2) {
                        val ipStr = longToIp(cur)
                        if (ipStr !in excludeIps) singles += ipStr
                        cur += step
                    }
                }
                continue
            }
            if (!token.contains('/')) {
                if (isIpv4(token) && token !in excludeIps) singles += token
                continue
            }
            val netIp = token.substringBefore('/')
            val prefix = token.substringAfter('/').toIntOrNull()?.coerceIn(0, 32) ?: continue
            val base = ipToLong(netIp) ?: continue
            val hostBits = 32 - prefix
            val mask = if (prefix == 0) 0L else (0xFFFFFFFFL shl hostBits) and 0xFFFFFFFFL
            val netAddr = base and mask
            val totalHosts = if (hostBits >= 32) 0xFFFFFFFFL else 1L shl hostBits
            cidrEntries += Triple(netAddr, hostBits, totalHosts)
        }

        val picked = linkedSetOf<String>()
        singles.forEach { if (picked.size < maxSamples) picked += it }
        if (cidrEntries.isEmpty()) return picked.take(maxSamples)

        // Round-robin across ALL shuffled prefixes first so 100 samples span up to 100 distinct subnets
        var guard = 0
        val maxGuard = maxSamples * 6
        var idx = 0
        val order = cidrEntries.indices.shuffled(Random).toMutableList()
        while (picked.size < maxSamples && guard < maxGuard) {
            guard++
            if (idx >= order.size) {
                order.shuffle(Random)
                idx = 0
            }
            val (netAddr, hostBits, totalHosts) = cidrEntries[order[idx++]]
            val ipLong = when {
                totalHosts <= 2L -> netAddr
                hostBits <= 24 -> {
                    val off = Random.nextLong(1L, totalHosts - 1L)
                    (netAddr + off) and 0xFFFFFFFFL
                }
                else -> {
                    // For /8../7 large supernets, randomize both /24 subnet index and host bits
                    val span = totalHosts - 2L
                    val off = 1L + (Random.nextLong() and 0x7FFFFFFFFFFFFFFFL) % span
                    (netAddr + off) and 0xFFFFFFFFL
                }
            }
            val ip = longToIp(ipLong)
            if (ip !in excludeIps) picked += ip
        }
        return picked.take(maxSamples)
    }

    /**
     * Two-phase high-throughput scanner:
     * 1) High-concurrency quick TCP sieve (64x default) drops unreachable/blocked IPs in ~1.1s.
     * 2) Deep probe verifies RTT stability, jitter, loss, TLS + SNI handshake, and optional HTTP trace/speed.
     */
    suspend fun scanCandidates(
        ips: List<String>,
        ports: List<Int> = listOf(443),
        provider: String = "cloudflare",
        sni: String = "speed.cloudflare.com",
        style: ScanStyle = ScanStyle.TURBO_FAST,
        testTls: Boolean = true,
        measureColoAndSpeed: Boolean = false,
        gentleMode: Boolean = false,
        excludeIps: Set<String> = emptySet(),
        onProgress: (JvmScanProgress) -> Unit = {},
        onResult: (JvmScanCandidateResult) -> Unit = {},
    ): List<JvmScanCandidateResult> = withContext(Dispatchers.IO) {
        val cleanPorts = ports.filter { it in 1..65535 }.distinct().ifEmpty { listOf(443) }
        val candidates = buildList {
            for (ip in ips) {
                if (ip in excludeIps) continue
                for (p in cleanPorts) add(ip to p)
            }
        }.shuffled(Random)
        if (candidates.isEmpty()) return@withContext emptyList()

        val total = candidates.size
        val phase1Tested = AtomicInteger(0)
        val healthyCount = AtomicInteger(0)
        val phase1Workers = if (gentleMode) 6 else style.concurrency.coerceIn(8, 96)
        val quickSem = Semaphore(phase1Workers)

        // Phase 1: Ultra-fast parallel reachability screen
        val alive = coroutineScope {
            candidates.map { (ip, port) ->
                async {
                    quickSem.withPermit {
                        val ms = measureTcpConnectMs(ip, port, style.quickTimeoutMs)
                        val t = phase1Tested.incrementAndGet()
                        onProgress(JvmScanProgress(tested = t, total = total, healthy = healthyCount.get()))
                        if (ms != null) Triple(ip, port, ms) else null
                    }
                }
            }.awaitAll().filterNotNull()
        }

        if (alive.isEmpty()) return@withContext emptyList()

        // Phase 2: Deep multi-sample RTT + TLS/SNI + speed/colo on surviving live candidates
        val deepSem = Semaphore(if (gentleMode) 4 else 24)
        val results = coroutineScope {
            alive.sortedBy { it.third }.map { (ip, port, firstRtt) ->
                async {
                    deepSem.withPermit {
                        val res = deepVerifyCandidate(
                            ip = ip,
                            port = port,
                            firstRttMs = firstRtt,
                            provider = provider,
                            sni = sni,
                            attempts = style.deepAttempts,
                            timeoutMs = (style.quickTimeoutMs * 2).coerceAtLeast(2200),
                            testTls = testTls && port !in setOf(2408, 500, 4500, 1701, 80),
                            measureColoAndSpeed = measureColoAndSpeed && port !in setOf(2408, 500, 4500, 1701, 80),
                        )
                        healthyCount.incrementAndGet()
                        onResult(res)
                        onProgress(JvmScanProgress(tested = total, total = total, healthy = healthyCount.get()))
                        res
                    }
                }
            }.awaitAll()
        }
        results.sortedByDescending { it.score }
    }

    suspend fun scanIps(
        ips: List<String>,
        port: Int = 443,
        provider: String = "cloudflare",
        sni: String = "speed.cloudflare.com",
        attempts: Int = 2,
        timeoutMs: Int = 2500,
        concurrency: Int = 48,
        onProgress: (JvmScanProgress) -> Unit = {},
        onResult: (JvmScanCandidateResult) -> Unit = {},
    ): List<JvmScanCandidateResult> = scanCandidates(
        ips = ips,
        ports = listOf(port),
        provider = provider,
        sni = sni,
        style = ScanStyle.TURBO_FAST.let {
            if (attempts >= 3) ScanStyle.BALANCED_TLS else it
        },
        testTls = port != 2408 && port != 80,
        measureColoAndSpeed = false,
        onProgress = onProgress,
        onResult = onResult,
    )

    private fun deepVerifyCandidate(
        ip: String,
        port: Int,
        firstRttMs: Int,
        provider: String,
        sni: String,
        attempts: Int,
        timeoutMs: Int,
        testTls: Boolean,
        measureColoAndSpeed: Boolean,
    ): JvmScanCandidateResult {
        val latencies = mutableListOf(firstRttMs)
        repeat((attempts - 1).coerceIn(0, 4)) {
            val ms = measureTcpConnectMs(ip, port, timeoutMs)
            if (ms != null) latencies += ms
        }

        var tlsMs: Int? = null
        var speedKbps = 0
        var colo = ""
        if (testTls) {
            runCatching {
                val factory = SSLSocketFactory.getDefault() as SSLSocketFactory
                Socket().use { raw ->
                    raw.connect(InetSocketAddress(ip, port), timeoutMs)
                    raw.soTimeout = timeoutMs
                    val tStart = System.nanoTime()
                    (factory.createSocket(raw, ip, port, true) as SSLSocket).use { tls ->
                        tls.soTimeout = timeoutMs
                        val cleanSni = sni.trim()
                        if (cleanSni.isNotBlank()) {
                            val params = tls.sslParameters
                            params.serverNames = listOf(SNIHostName(cleanSni))
                            tls.sslParameters = params
                        }
                        tls.startHandshake()
                        tlsMs = ((System.nanoTime() - tStart) / 1_000_000L).toInt().coerceAtLeast(1)

                        if (measureColoAndSpeed) {
                            val reqHost = cleanSni.ifBlank { "speed.cloudflare.com" }
                            val path = if (provider.equals("cloudflare", true)) "/__down?bytes=32768" else "/"
                            val req = "GET $path HTTP/1.1\r\nHost: $reqHost\r\nConnection: close\r\n\r\n"
                            val dlStart = System.nanoTime()
                            tls.outputStream.write(req.toByteArray(Charsets.US_ASCII))
                            tls.outputStream.flush()
                            val buf = ByteArray(4096)
                            var totalRead = 0
                            val preview = StringBuilder()
                            while (totalRead < 65_536) {
                                val n = tls.inputStream.read(buf)
                                if (n <= 0) break
                                totalRead += n
                                if (preview.length < 512) {
                                    preview.append(String(buf, 0, n.coerceAtMost(512), Charsets.US_ASCII))
                                }
                            }
                            val elapsedMs = ((System.nanoTime() - dlStart) / 1_000_000L).coerceAtLeast(1L)
                            if (totalRead > 0) {
                                speedKbps = ((totalRead / 1024.0) / (elapsedMs / 1000.0)).toInt().coerceAtLeast(1)
                            }
                            val hdr = preview.toString()
                            Regex("cf-ray:\\s*[0-9a-fA-F]+-([A-Z]{3})", RegexOption.IGNORE_CASE)
                                .find(hdr)
                                ?.let { colo = it.groupValues[1] }
                            if (colo.isBlank() && (hdr.contains("AkamaiGHost", true) || hdr.contains("x-akamai", true))) {
                                colo = "Akamai"
                            } else if (colo.isBlank() && hdr.contains("fastly", true)) {
                                colo = "Fastly"
                            }
                        }
                    }
                }
            }
        }

        val avg = latencies.average().toInt().coerceAtLeast(1)
        val min = latencies.min()
        val jitter = if (latencies.size >= 2) abs(latencies.max() - latencies.min()) else 1
        val lossPct = (((attempts - latencies.size).coerceAtLeast(0).toDouble()) / attempts.toDouble()) * 100.0
        var score = (1000.0 / avg.toDouble()) * (1.0 - (lossPct / 100.0)) - (jitter * 0.15)
        if (tlsMs != null) score += 1.5
        if (speedKbps >= 500) score += 2.0 else if (speedKbps >= 150) score += 1.0
        val grade = when {
            lossPct == 0.0 && avg <= 160 && (!testTls || tlsMs != null) -> "S+"
            lossPct <= 10.0 && avg <= 260 -> "A"
            lossPct <= 25.0 && avg <= 420 -> "B"
            else -> "C"
        }
        return JvmScanCandidateResult(
            ip = ip,
            port = port,
            provider = provider,
            latencyMs = avg,
            minLatencyMs = min,
            lossPct = lossPct,
            jitterMs = jitter,
            tlsHandshakeMs = tlsMs,
            downloadSpeedKbps = speedKbps,
            colo = colo,
            grade = grade,
            score = score,
        )
    }

    private fun measureTcpConnectMs(ip: String, port: Int, timeoutMs: Int): Int? = try {
        val start = System.nanoTime()
        Socket().use { s -> s.connect(InetSocketAddress(ip, port), timeoutMs) }
        ((System.nanoTime() - start) / 1_000_000L).toInt().coerceAtLeast(1)
    } catch (ce: CancellationException) {
        throw ce
    } catch (_: Throwable) {
        null
    }

    private fun isIpv4(s: String): Boolean =
        Regex("""^\d{1,3}(\.\d{1,3}){3}$""").matches(s)

    private fun ipToLong(ip: String): Long? {
        val parts = ip.split('.')
        if (parts.size != 4) return null
        var acc = 0L
        for (p in parts) {
            val v = p.toIntOrNull() ?: return null
            if (v !in 0..255) return null
            acc = (acc shl 8) or v.toLong()
        }
        return acc
    }

    private fun longToIp(v: Long): String =
        "${(v ushr 24) and 0xFF}.${(v ushr 16) and 0xFF}.${(v ushr 8) and 0xFF}.${v and 0xFF}"

    fun exportResultsTxt(results: List<JvmScanCandidateResult>): String =
        results.joinToString("\n") { it.ip }

    fun exportResultsCsv(results: List<JvmScanCandidateResult>): String = buildString {
        appendLine("IP,Port,LatencyMs,JitterMs,LossPct,TlsMs,DownloadKbps,Colo,Grade")
        results.forEach { r ->
            appendLine("${r.ip},${r.port},${r.latencyMs},${r.jitterMs},${r.lossPct.toInt()},${r.tlsHandshakeMs ?: ""},${r.downloadSpeedKbps},${r.colo},${r.grade}")
        }
    }
}
