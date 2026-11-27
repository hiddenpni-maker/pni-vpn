package ir.pnifox.core.vpn

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.util.concurrent.atomic.AtomicInteger

data class DnsResolverCandidate(
    val label: String,
    val ip: String,
    val category: String,
)

data class DnsE2EResult(
    val label: String,
    val ip: String,
    val category: String,
    val latencyMs: Long,
    val isReachable: Boolean,
    val isPoisoned: Boolean,
    val resolvedSampleIp: String?,
    val statusNoteEn: String,
    val statusNoteFa: String,
)

/**
 * Zero-ICMP Port-53 End-to-End DNS Resolver Scanner.
 * Because many Iranian ISPs block ICMP echo (`ping`), this scanner tests real DNS wire
 * resolution over UDP Port 53 and verifies returned A records against ISP sinkholes
 * (`10.10.34.34`, `10.10.34.35`, `10.10.34.36`).
 */
object DnsE2EScanner {

    val builtInCandidates: List<DnsResolverCandidate> = listOf(
        DnsResolverCandidate("Cloudflare Primary", "1.1.1.1", "Global"),
        DnsResolverCandidate("Cloudflare Alt", "1.0.0.1", "Global"),
        DnsResolverCandidate("Google DNS", "8.8.8.8", "Global"),
        DnsResolverCandidate("Google Alt", "8.8.4.4", "Global"),
        DnsResolverCandidate("Quad9 Unfiltered", "9.9.9.10", "DNSTT"),
        DnsResolverCandidate("Quad9 Secure", "9.9.9.9", "Global"),
        DnsResolverCandidate("OpenDNS", "208.67.222.222", "DNSTT"),
        DnsResolverCandidate("OpenDNS Alt", "208.67.220.220", "DNSTT"),
        DnsResolverCandidate("AdGuard DNS", "94.140.14.14", "Global"),
        DnsResolverCandidate("CleanBrowsing", "185.228.168.9", "Global"),
        DnsResolverCandidate("Verisign DNS", "64.6.64.6", "DNSTT"),
        DnsResolverCandidate("Radar Game", "10.202.10.10", "Gaming"),
        DnsResolverCandidate("403 Online", "10.202.10.202", "Gaming"),
        DnsResolverCandidate("Begzar DNS", "185.55.226.26", "Gaming"),
        DnsResolverCandidate("Shecan DNS", "178.22.122.100", "Gaming"),
        DnsResolverCandidate("Electro DNS", "78.157.42.100", "Gaming"),
    )

    suspend fun scanResolvers(
        extraIpsCsv: String = "",
        probeDomain: String = "youtube.com",
        concurrency: Int = 8,
        timeoutMs: Int = 1_800,
        onProgress: ((done: Int, total: Int) -> Unit)? = null,
    ): List<DnsE2EResult> = coroutineScope {
        val extras = extraIpsCsv.split(',', '\n', ' ')
            .map { it.trim().substringBefore(':') }
            .filter { it.count { ch -> ch == '.' } == 3 }
            .map { DnsResolverCandidate("Custom ($it)", it, "Custom") }
        val allCandidates = (builtInCandidates + extras).distinctBy { it.ip }
        val total = allCandidates.size
        val doneCount = AtomicInteger(0)
        val sem = Semaphore(concurrency.coerceIn(1, 16))

        allCandidates.map { candidate ->
            async(Dispatchers.IO) {
                sem.withPermit {
                    val res = probeSingle(candidate, probeDomain, timeoutMs)
                    val cur = doneCount.incrementAndGet()
                    onProgress?.invoke(cur, total)
                    res
                }
            }
        }.awaitAll().sortedWith(
            compareBy<DnsE2EResult>(
                { !it.isReachable || it.isPoisoned },
                { it.latencyMs },
            ),
        )
    }

    fun probeSingle(
        candidate: DnsResolverCandidate,
        probeDomain: String = "youtube.com",
        timeoutMs: Int = 1_800,
    ): DnsE2EResult {
        val start = System.currentTimeMillis()
        val query = LocalDpiDesyncServer.buildDnsARecordQuery(probeDomain, txId = 0x5301, qType = 1)
        val outcome = runCatching {
            DatagramSocket().use { sock ->
                sock.soTimeout = timeoutMs
                val addr = InetAddress.getByName(candidate.ip)
                sock.send(DatagramPacket(query, query.size, addr, 53))
                val recv = ByteArray(512)
                val pkt = DatagramPacket(recv, recv.size)
                sock.receive(pkt)
                val rtt = (System.currentTimeMillis() - start).coerceAtLeast(1L)
                val answerIp = LocalDpiDesyncServer.extractFirstARecord(recv.copyOf(pkt.length))
                rtt to answerIp
            }
        }
        if (outcome.isFailure) {
            return DnsE2EResult(
                label = candidate.label,
                ip = candidate.ip,
                category = candidate.category,
                latencyMs = 9999L,
                isReachable = false,
                isPoisoned = false,
                resolvedSampleIp = null,
                statusNoteEn = "UDP/53 timed out",
                statusNoteFa = "عدم پاسخ پورت ۵۳",
            )
        }
        val (rtt, answerIp) = outcome.getOrThrow()
        val poisoned = answerIp != null && LocalDpiDesyncServer.isPoisonedIp(answerIp)
        return when {
            poisoned -> DnsE2EResult(
                label = candidate.label,
                ip = candidate.ip,
                category = candidate.category,
                latencyMs = rtt,
                isReachable = true,
                isPoisoned = true,
                resolvedSampleIp = answerIp,
                statusNoteEn = "Poisoned ($answerIp sinkhole)",
                statusNoteFa = "مسموم‌سازی شده ($answerIp)",
            )
            answerIp != null -> DnsE2EResult(
                label = candidate.label,
                ip = candidate.ip,
                category = candidate.category,
                latencyMs = rtt,
                isReachable = true,
                isPoisoned = false,
                resolvedSampleIp = answerIp,
                statusNoteEn = "Clean E2E ($answerIp)",
                statusNoteFa = "پاسخ سالم ($answerIp)",
            )
            else -> DnsE2EResult(
                label = candidate.label,
                ip = candidate.ip,
                category = candidate.category,
                latencyMs = rtt,
                isReachable = true,
                isPoisoned = false,
                resolvedSampleIp = null,
                statusNoteEn = "Port 53 open (empty A record)",
                statusNoteFa = "پورت ۵۳ باز (پاسخ بدون A)",
            )
        }
    }
}
