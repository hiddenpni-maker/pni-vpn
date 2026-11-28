package ir.pnifox.core.vpn

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.Socket
import javax.net.ssl.SNIHostName
import javax.net.ssl.SSLSocket
import javax.net.ssl.SSLSocketFactory

data class DnsSpyResolverEntry(
    val id: String,
    val nameEn: String,
    val nameFa: String,
    val ip: String,
    val category: String, // "Gaming", "Anti-Sanction", "Global", "Private"
    val requiresIpRegistration: Boolean = false,
)

data class DnsSpyProbeResult(
    val resolver: DnsSpyResolverEntry,
    val latencyMs: Int?,
    val hijacked: Boolean,
    val resolvedSampleIp: String,
    val statusNoteEn: String,
    val statusNoteFa: String,
)

data class GameCatalogEntry(
    val id: String,
    val title: String,
    val genre: String,
    val region: String,
    val targetPingMs: Int,
    val probeHost: String,
    val probePort: Int = 443,
)

data class GameLivePingResult(
    val game: GameCatalogEntry,
    val latencyMs: Int?,
    val lossPct: Int,
    val isGoodForGaming: Boolean,
)

/**
 * WhiteGame v7.3.0-inspired Network & Game Optimizer:
 * - DNS Spy & DNS Hijacking Detector (detects 10.10.34.x ISP sinkholes & poisoned DNS)
 * - No-IP-Registration Private/Anti-Sanction Gaming DNS ranking
 * - Game Publisher Live TLS/HTTPS Latency & Loss Catalog (COD Mobile, Steam, Valorant, etc.)
 */
object WhiteGameOptimizer {

    val dnsCatalog: List<DnsSpyResolverEntry> = listOf(
        DnsSpyResolverEntry("radar_1", "Radar Game", "رادار گیم", "10.202.10.10", "Gaming"),
        DnsSpyResolverEntry("radar_2", "Radar Game #2", "رادار گیم ۲", "10.202.10.11", "Gaming"),
        DnsSpyResolverEntry("electro_1", "Electro Gaming", "الکترو گیم", "78.157.42.100", "Gaming"),
        DnsSpyResolverEntry("electro_2", "Electro #2", "الکترو ۲", "78.157.42.101", "Gaming"),
        DnsSpyResolverEntry("403_1", "403.online", "۴۰۳ آنلاین", "10.202.10.202", "Anti-Sanction"),
        DnsSpyResolverEntry("403_2", "403.online #2", "۴۰۳ آنلاین ۲", "10.202.10.102", "Anti-Sanction"),
        DnsSpyResolverEntry("shecan_1", "Shecan Pro (No-Reg)", "شکن (بدون ثبت آی‌پی)", "178.22.122.100", "Anti-Sanction"),
        DnsSpyResolverEntry("shecan_2", "Shecan #2", "شکن ۲", "185.51.200.2", "Anti-Sanction"),
        DnsSpyResolverEntry("begzar_1", "Begzar", "بگذر", "185.55.226.26", "Anti-Sanction"),
        DnsSpyResolverEntry("hostiran", "HostIran", "هاست ایران", "172.29.0.100", "Anti-Sanction"),
        DnsSpyResolverEntry("cf_1", "Cloudflare", "کلودفلر", "1.1.1.1", "Global"),
        DnsSpyResolverEntry("google_1", "Google DNS", "گوگل DNS", "8.8.8.8", "Global"),
        DnsSpyResolverEntry("quad9_1", "Quad9", "کواد۹", "9.9.9.9", "Global"),
        DnsSpyResolverEntry("adguard_1", "AdGuard Private", "ادگارد پرایوت", "94.140.14.14", "Private"),
    )

    val gameCatalog: List<GameCatalogEntry> = listOf(
        GameCatalogEntry("codm", "COD Mobile", "FPS", "EU/ME/SG", 75, "www.activision.com", 443),
        GameCatalogEntry("steam", "Steam", "Platform", "EU/US", 120, "store.steampowered.com", 443),
        GameCatalogEntry("valorant", "Valorant / Riot", "FPS", "EU/TR/ME", 85, "auth.riotgames.com", 443),
        GameCatalogEntry("pubg", "PUBG Mobile", "Battle Royale", "EU/ME", 90, "www.pubgmobile.com", 443),
        GameCatalogEntry("fortnite", "Fortnite / Epic", "Battle Royale", "EU/ME", 95, "www.epicgames.com", 443),
        GameCatalogEntry("ea_fc", "EA Sports FC / FIFA", "Sports", "EU/ME", 90, "www.ea.com", 443),
        GameCatalogEntry("brawlstars", "Supercell / Clash", "Mobile", "EU", 100, "supercell.com", 443),
    )

    private val sinkholePrefixes = listOf("10.10.34.", "10.10.35.", "10.10.36.", "127.0.0.")

    suspend fun runDnsSpyScan(
        resolvers: List<DnsSpyResolverEntry> = dnsCatalog,
        probeDomain: String = "youtube.com",
        timeoutMs: Int = 2200,
    ): List<DnsSpyProbeResult> = withContext(Dispatchers.IO) {
        val sem = Semaphore(8)
        coroutineScope {
            resolvers.map { entry ->
                async {
                    sem.withPermit {
                        probeSingleDnsSpy(entry, probeDomain, timeoutMs)
                    }
                }
            }.awaitAll()
        }.sortedWith(
            compareBy<DnsSpyProbeResult> { it.latencyMs == null }
                .thenBy { it.hijacked }
                .thenBy { it.latencyMs ?: Int.MAX_VALUE },
        )
    }

    private fun probeSingleDnsSpy(
        entry: DnsSpyResolverEntry,
        probeDomain: String,
        timeoutMs: Int,
    ): DnsSpyProbeResult {
        val query = buildDnsAQuery(probeDomain)
        return runCatching {
            val t0 = System.nanoTime()
            val rxBuf = ByteArray(512)
            val rxLen: Int
            DatagramSocket().use { sock ->
                sock.soTimeout = timeoutMs
                sock.send(DatagramPacket(query, query.size, InetAddress.getByName(entry.ip), 53))
                val resp = DatagramPacket(rxBuf, rxBuf.size)
                sock.receive(resp)
                rxLen = resp.length
            }
            val ms = ((System.nanoTime() - t0) / 1_000_000L).toInt().coerceAtLeast(1)
            val resolvedIp = extractFirstIpv4Answer(rxBuf, rxLen).orEmpty()
            val hijacked = sinkholePrefixes.any { resolvedIp.startsWith(it) }
            DnsSpyProbeResult(
                resolver = entry,
                latencyMs = ms,
                hijacked = hijacked,
                resolvedSampleIp = resolvedIp,
                statusNoteEn = if (hijacked) "DNS Hijacked ($resolvedIp)" else "Clean • ${ms}ms",
                statusNoteFa = if (hijacked) "جعل DNS شناسایی شد ($resolvedIp)" else "سالم • ${ms}ms",
            )
        }.getOrElse {
            DnsSpyProbeResult(
                resolver = entry,
                latencyMs = null,
                hijacked = false,
                resolvedSampleIp = "",
                statusNoteEn = "Timeout / Blocked",
                statusNoteFa = "تایم‌اوت / مسدود",
            )
        }
    }

    suspend fun probeGameCatalog(
        games: List<GameCatalogEntry> = gameCatalog,
        timeoutMs: Int = 2500,
    ): List<GameLivePingResult> = withContext(Dispatchers.IO) {
        val sem = Semaphore(6)
        coroutineScope {
            games.map { game ->
                async {
                    sem.withPermit {
                        val samples = mutableListOf<Int>()
                        repeat(2) {
                            val ms = measureTlsDelayMs(game.probeHost, game.probePort, timeoutMs)
                            if (ms != null) samples += ms
                        }
                        val lossPct = (((2 - samples.size) * 100) / 2).coerceIn(0, 100)
                        val avg = if (samples.isNotEmpty()) samples.average().toInt() else null
                        GameLivePingResult(
                            game = game,
                            latencyMs = avg,
                            lossPct = lossPct,
                            isGoodForGaming = avg != null && avg <= game.targetPingMs * 2 && lossPct == 0,
                        )
                    }
                }
            }.awaitAll()
        }
    }

    private fun measureTlsDelayMs(host: String, port: Int, timeoutMs: Int): Int? = runCatching {
        val factory = SSLSocketFactory.getDefault() as SSLSocketFactory
        val t0 = System.nanoTime()
        Socket().use { raw ->
            raw.connect(InetSocketAddress(host, port), timeoutMs)
            raw.soTimeout = timeoutMs
            (factory.createSocket(raw, host, port, true) as SSLSocket).use { tls ->
                tls.soTimeout = timeoutMs
                val params = tls.sslParameters
                params.serverNames = listOf(SNIHostName(host))
                tls.sslParameters = params
                tls.startHandshake()
            }
        }
        ((System.nanoTime() - t0) / 1_000_000L).toInt().coerceAtLeast(1)
    }.getOrNull()

    private fun buildDnsAQuery(domain: String): ByteArray {
        val baos = ByteArrayOutputStream()
        baos.write(byteArrayOf(0x31, 0x77, 0x01, 0x00, 0x00, 0x01, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00))
        domain.trim('.').split('.').forEach { label ->
            val b = label.toByteArray(Charsets.US_ASCII)
            baos.write(b.size)
            baos.write(b)
        }
        baos.write(0x00)
        baos.write(byteArrayOf(0x00, 0x01, 0x00, 0x01))
        return baos.toByteArray()
    }

    private fun extractFirstIpv4Answer(buf: ByteArray, len: Int): String? {
        if (len < 12) return null
        val anCount = ((buf[6].toInt() and 0xFF) shl 8) or (buf[7].toInt() and 0xFF)
        if (anCount <= 0) return null
        var pos = 12
        // Skip QNAME
        while (pos < len) {
            val l = buf[pos].toInt() and 0xFF
            if (l == 0) {
                pos += 1
                break
            }
            if ((l and 0xC0) == 0xC0) {
                pos += 2
                break
            }
            pos += l + 1
        }
        pos += 4 // QTYPE + QCLASS
        repeat(anCount) {
            if (pos + 12 > len) return null
            // Skip NAME
            while (pos < len) {
                val l = buf[pos].toInt() and 0xFF
                if (l == 0) {
                    pos += 1
                    break
                }
                if ((l and 0xC0) == 0xC0) {
                    pos += 2
                    break
                }
                pos += l + 1
            }
            if (pos + 10 > len) return null
            val type = ((buf[pos].toInt() and 0xFF) shl 8) or (buf[pos + 1].toInt() and 0xFF)
            val rdLen = ((buf[pos + 8].toInt() and 0xFF) shl 8) or (buf[pos + 9].toInt() and 0xFF)
            pos += 10
            if (type == 1 && rdLen == 4 && pos + 4 <= len) {
                return "${buf[pos].toInt() and 0xFF}.${buf[pos + 1].toInt() and 0xFF}.${buf[pos + 2].toInt() and 0xFF}.${buf[pos + 3].toInt() and 0xFF}"
            }
            pos += rdLen
        }
        return null
    }
}
