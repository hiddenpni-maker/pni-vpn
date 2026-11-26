package ir.pnifox.core.vpn

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
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
    val lossPct: Double,
    val jitterMs: Int,
    val grade: String,
    val score: Double,
)

data class JvmScanProgress(
    val tested: Int,
    val total: Int,
    val healthy: Int,
)

object JvmCdnScanner {

    fun expandCidrs(cidrs: List<String>, maxSamples: Int): List<String> {
        val cleaned = cidrs.map { it.trim() }.filter { it.isNotBlank() && !it.startsWith("#") }
        if (cleaned.isEmpty()) return emptyList()
        val picked = linkedSetOf<String>()
        val perCidr = maxOf(1, (maxSamples + cleaned.size - 1) / cleaned.size)
        for (token in cleaned) {
            if (picked.size >= maxSamples) break
            if (!token.contains('/')) {
                if (isIpv4(token)) picked += token
                continue
            }
            val netIp = token.substringBefore('/')
            val prefix = token.substringAfter('/').toIntOrNull() ?: continue
            val base = ipToLong(netIp) ?: continue
            val hostBits = (32 - prefix).coerceIn(0, 24)
            val totalHosts = (1L shl hostBits).coerceAtLeast(1L)
            repeat(perCidr) {
                if (picked.size >= maxSamples) return@repeat
                val offset = if (totalHosts <= 2L) 0L else Random.nextLong(1L, totalHosts - 1L)
                picked += longToIp((base + offset) and 0xFFFFFFFFL)
            }
        }
        return picked.take(maxSamples)
    }

    suspend fun scanIps(
        ips: List<String>,
        port: Int = 443,
        provider: String = "cloudflare",
        sni: String = "speed.cloudflare.com",
        attempts: Int = 2,
        timeoutMs: Int = 2500,
        concurrency: Int = 24,
        onProgress: (JvmScanProgress) -> Unit = {},
        onResult: (JvmScanCandidateResult) -> Unit = {},
    ): List<JvmScanCandidateResult> = withContext(Dispatchers.IO) {
        val semaphore = Semaphore(concurrency.coerceIn(1, 64))
        val tested = AtomicInteger(0)
        val healthy = AtomicInteger(0)
        val total = ips.size

        val results = coroutineScope {
            ips.map { ip ->
                async {
                    semaphore.withPermit {
                        val res = probeSingleIp(ip, port, provider, sni, attempts, timeoutMs)
                        val t = tested.incrementAndGet()
                        val h = if (res != null) {
                            onResult(res)
                            healthy.incrementAndGet()
                        } else {
                            healthy.get()
                        }
                        onProgress(JvmScanProgress(tested = t, total = total, healthy = h))
                        res
                    }
                }
            }.awaitAll().filterNotNull()
        }
        results.sortedByDescending { it.score }
    }

    private fun probeSingleIp(
        ip: String,
        port: Int,
        provider: String,
        sni: String,
        attempts: Int,
        timeoutMs: Int,
    ): JvmScanCandidateResult? {
        val latencies = mutableListOf<Int>()
        repeat(attempts.coerceIn(1, 4)) { idx ->
            try {
                val ms = measureSingleTlsOrTcp(ip, port, sni, timeoutMs, doTls = idx == 0)
                if (ms != null) latencies += ms
            } catch (ce: CancellationException) {
                throw ce
            } catch (_: Throwable) {
            }
        }
        if (latencies.isEmpty()) return null
        val avg = latencies.average().toInt().coerceAtLeast(1)
        val lossPct = ((attempts - latencies.size).toDouble() / attempts.toDouble()) * 100.0
        val jitter = if (latencies.size >= 2) abs(latencies.max() - latencies.min()) else 0
        val score = (1000.0 / avg.toDouble()) * (1.0 - (lossPct / 100.0)) - (jitter * 0.15)
        val grade = when {
            lossPct == 0.0 && avg <= 160 -> "S+"
            lossPct <= 10.0 && avg <= 260 -> "A"
            lossPct <= 25.0 && avg <= 420 -> "B"
            else -> "C"
        }
        return JvmScanCandidateResult(
            ip = ip,
            port = port,
            provider = provider,
            latencyMs = avg,
            lossPct = lossPct,
            jitterMs = jitter,
            grade = grade,
            score = score,
        )
    }

    private fun measureSingleTlsOrTcp(
        ip: String,
        port: Int,
        sni: String,
        timeoutMs: Int,
        doTls: Boolean,
    ): Int? = runCatching {
        val start = System.nanoTime()
        Socket().use { raw ->
            raw.connect(InetSocketAddress(ip, port), timeoutMs)
            raw.soTimeout = timeoutMs
            if (doTls && port != 2408 && port != 80) {
                val factory = SSLSocketFactory.getDefault() as SSLSocketFactory
                val ssl = factory.createSocket(raw, ip, port, true) as SSLSocket
                ssl.use { tls ->
                    tls.soTimeout = timeoutMs
                    val cleanSni = sni.trim()
                    if (cleanSni.isNotBlank()) {
                        val params = tls.sslParameters
                        params.serverNames = listOf(SNIHostName(cleanSni))
                        tls.sslParameters = params
                    }
                    tls.startHandshake()
                }
            }
        }
        ((System.nanoTime() - start) / 1_000_000L).toInt().coerceAtLeast(1)
    }.getOrNull()

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
}
