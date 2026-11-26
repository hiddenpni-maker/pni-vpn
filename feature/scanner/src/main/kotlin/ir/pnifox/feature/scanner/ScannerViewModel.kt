package ir.pnifox.feature.scanner

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import ir.pnifox.core.database.PnifoxDataRepository
import ir.pnifox.core.database.PreferencesRepository
import ir.pnifox.core.model.ScanCategoryFilter
import ir.pnifox.core.model.ScanProvider
import ir.pnifox.core.model.ScanResultItem
import ir.pnifox.core.model.ScanSortBy
import ir.pnifox.core.model.ScanSpeedTier
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import java.net.InetSocketAddress
import java.net.Socket
import java.util.UUID
import javax.inject.Inject
import javax.net.ssl.SNIHostName
import javax.net.ssl.SSLSocket
import javax.net.ssl.SSLSocketFactory
import kotlin.random.Random

data class ScannerUiState(
    val provider: ScanProvider = ScanProvider.CLOUDFLARE,
    val port: Int = 443,
    val candidateCount: Int = 40,
    val sniHost: String = "speed.cloudflare.com",
    val gentleMode: Boolean = true,
    val enableTlsAndSpeedTest: Boolean = true,
    val categoryFilter: ScanCategoryFilter = ScanCategoryFilter.ALL,
    val sortBy: ScanSortBy = ScanSortBy.SCORE,
    val isScanning: Boolean = false,
    val progressLabel: String = "",
    val allResults: List<ScanResultItem> = emptyList(),
    val filteredResults: List<ScanResultItem> = emptyList(),
    val cleanIpsMultiline: String = "",
    val statusNote: String? = null,
)

private data class ScanConfigState(
    val provider: ScanProvider = ScanProvider.CLOUDFLARE,
    val port: Int = 443,
    val candidateCount: Int = 40,
    val sniHost: String = ScanProvider.CLOUDFLARE.defaultSni,
    val gentleMode: Boolean = true,
    val enableTlsAndSpeedTest: Boolean = true,
    val categoryFilter: ScanCategoryFilter = ScanCategoryFilter.ALL,
    val sortBy: ScanSortBy = ScanSortBy.SCORE,
    val isScanning: Boolean = false,
    val progressLabel: String = "",
    val statusNote: String? = null,
)

@HiltViewModel
class ScannerViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val dataRepo: PnifoxDataRepository,
    private val preferencesRepo: PreferencesRepository,
) : ViewModel() {

    private val cfg = MutableStateFlow(ScanConfigState())
    private var scanJob: Job? = null

    val uiState: StateFlow<ScannerUiState> = combine(
        cfg,
        dataRepo.observeScanResults(),
        preferencesRepo.appSettingsFlow,
    ) { rt, rows, app ->
        val providerRows = rows.filter { it.provider == rt.provider }
        val categorized = when (rt.categoryFilter) {
            ScanCategoryFilter.ALL -> providerRows
            ScanCategoryFilter.ULTRA_FAST -> providerRows.filter { it.latencyMs < 130 && it.packetLossPct == 0 }
            ScanCategoryFilter.ZERO_LOSS -> providerRows.filter { it.packetLossPct == 0 && it.jitterMs <= 25 }
            ScanCategoryFilter.HIGH_THROUGHPUT -> providerRows.filter { it.downloadSpeedKbps > 0 }
            ScanCategoryFilter.TLS_OK -> providerRows.filter { it.tlsHandshakeMs != null }
        }
        val sorted = when (rt.sortBy) {
            ScanSortBy.SCORE -> categorized.sortedWith(compareByDescending<ScanResultItem> { it.stabilityScore }.thenBy { it.latencyMs })
            ScanSortBy.LATENCY -> categorized.sortedBy { it.latencyMs }
            ScanSortBy.SPEED -> categorized.sortedByDescending { it.downloadSpeedKbps }
            ScanSortBy.JITTER -> categorized.sortedBy { it.jitterMs }
            ScanSortBy.LOSS -> categorized.sortedWith(compareBy<ScanResultItem> { it.packetLossPct }.thenBy { it.latencyMs })
        }

        ScannerUiState(
            provider = rt.provider,
            port = rt.port,
            candidateCount = rt.candidateCount,
            sniHost = rt.sniHost,
            gentleMode = rt.gentleMode,
            enableTlsAndSpeedTest = rt.enableTlsAndSpeedTest,
            categoryFilter = rt.categoryFilter,
            sortBy = rt.sortBy,
            isScanning = rt.isScanning,
            progressLabel = rt.progressLabel,
            allResults = providerRows,
            filteredResults = sorted,
            cleanIpsMultiline = app.cleanIpsMultiline,
            statusNote = rt.statusNote,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000L),
        initialValue = ScannerUiState(),
    )

    fun selectProvider(provider: ScanProvider) {
        val nextPort = if (provider == ScanProvider.WARP) 2408 else 443
        cfg.value = cfg.value.copy(
            provider = provider,
            sniHost = provider.defaultSni,
            port = nextPort,
            statusNote = null,
        )
    }

    fun setPort(portText: String) {
        val parsed = portText.toIntOrNull()?.coerceIn(1, 65535) ?: return
        cfg.value = cfg.value.copy(port = parsed)
    }

    fun setCandidateCount(count: Int) {
        cfg.value = cfg.value.copy(candidateCount = count.coerceIn(12, 120))
    }

    fun setSniHost(sni: String) {
        cfg.value = cfg.value.copy(sniHost = sni)
    }

    fun toggleGentle(enabled: Boolean) {
        cfg.value = cfg.value.copy(gentleMode = enabled)
    }

    fun toggleTlsAndSpeed(enabled: Boolean) {
        cfg.value = cfg.value.copy(enableTlsAndSpeedTest = enabled)
    }

    fun selectCategoryFilter(filter: ScanCategoryFilter) {
        cfg.value = cfg.value.copy(categoryFilter = filter)
    }

    fun selectSortBy(sortBy: ScanSortBy) {
        cfg.value = cfg.value.copy(sortBy = sortBy)
    }

    fun startOrStopScan() {
        if (cfg.value.isScanning) {
            scanJob?.cancel()
            cfg.value = cfg.value.copy(isScanning = false, progressLabel = "Scan paused")
            return
        }

        val snapshot = cfg.value
        scanJob = viewModelScope.launch {
            cfg.value = snapshot.copy(isScanning = true, progressLabel = "Sampling candidate IPs...", statusNote = null)
            val candidates = loadCandidateIps(snapshot.provider, snapshot.candidateCount)
            val workers = if (snapshot.gentleMode) 6 else 12
            val semaphore = Semaphore(workers)
            var done = 0
            val discovered = mutableListOf<ScanResultItem>()

            coroutineScope {
                val jobs = candidates.map { ip ->
                    async(Dispatchers.IO) {
                        semaphore.withPermit {
                            if (!isActive) return@withPermit null
                            val res = benchmarkCandidate(
                                ip = ip,
                                provider = snapshot.provider,
                                port = snapshot.port,
                                sni = snapshot.sniHost,
                                testTlsSpeed = snapshot.enableTlsAndSpeedTest,
                                timeoutMs = if (snapshot.gentleMode) 2200 else 1500,
                            )
                            synchronized(discovered) {
                                done++
                                cfg.value = cfg.value.copy(progressLabel = "Probed $done/${candidates.size} • Clean: ${discovered.size}")
                                if (res != null) discovered += res
                            }
                            res
                        }
                    }
                }
                jobs.awaitAll()
            }

            if (discovered.isNotEmpty()) {
                dataRepo.replaceScanResults(snapshot.provider, discovered.sortedByDescending { it.stabilityScore })
            }
            cfg.value = cfg.value.copy(
                isScanning = false,
                progressLabel = "Finished: ${discovered.size}/${candidates.size} reachable IPs",
                statusNote = if (discovered.isEmpty()) {
                    "No clean TCP/TLS responses on port ${snapshot.port}. Try port 443/2053 or enable Gentle mode."
                } else {
                    "Found ${discovered.size} IPs categorized by ping, loss, jitter, and download speed."
                },
            )
        }
    }

    fun applyToAether(item: ScanResultItem) {
        viewModelScope.launch {
            preferencesRepo.updateAetherSettings {
                it.copy(endpointIp = item.ip, endpointPort = item.port, scanForEndpoint = false)
            }
            cfg.value = cfg.value.copy(statusNote = "Applied ${item.ip}:${item.port} as active AETHER endpoint.")
        }
    }

    fun applyToActiveServer(item: ScanResultItem) {
        viewModelScope.launch {
            val targetId = preferencesRepo.appSettingsFlow.first().activeProfileId
            val prof = targetId?.let { dataRepo.getProfile(it) }
            if (prof == null) {
                cfg.value = cfg.value.copy(statusNote = "Select a custom server in Servers tab first to apply IP.")
            } else {
                dataRepo.saveProfile(prof.copy(address = item.ip, port = item.port, lastPingMs = item.latencyMs))
                cfg.value = cfg.value.copy(statusNote = "Updated server '${prof.name}' host to ${item.ip}:${item.port}.")
            }
        }
    }

    fun saveCleanIpsMultiline(multiline: String) {
        viewModelScope.launch {
            val cleaned = multiline.lineSequence().map { it.trim() }.filter { it.isNotEmpty() }.distinct().joinToString("\n")
            preferencesRepo.updateAppSettings { it.copy(cleanIpsMultiline = cleaned) }
            cfg.value = cfg.value.copy(statusNote = "Saved ${cleaned.lines().size} clean IP entries.")
        }
    }

    private suspend fun benchmarkCandidate(
        ip: String,
        provider: ScanProvider,
        port: Int,
        sni: String,
        testTlsSpeed: Boolean,
        timeoutMs: Int,
    ): ScanResultItem? = withContext(Dispatchers.IO) {
        // 1. 3x TCP ping rounds -> avg, min, jitter, packet loss %
        val samples = mutableListOf<Int>()
        repeat(3) {
            val ms = measureTcpMs(ip, port, timeoutMs)
            if (ms != null) samples += ms
        }
        if (samples.isEmpty()) return@withContext null

        val avgMs = samples.average().toInt().coerceAtLeast(1)
        val minMs = samples.min()
        val maxMs = samples.max()
        val jitterMs = (maxMs - minMs).coerceAtLeast(1)
        val lossPct = (((3 - samples.size) / 3.0) * 100).toInt()

        // 2. Real TLS Handshake + SNI + HTTPS Trace/Down probe on TLS ports
        var tlsMs: Int? = null
        var speedKbps = 0
        var colo = ""

        if (testTlsSpeed && port in setOf(443, 8443, 2053, 2083, 2087, 2096)) {
            runCatching {
                val factory = SSLSocketFactory.getDefault() as SSLSocketFactory
                val startTls = System.nanoTime()
                (factory.createSocket() as SSLSocket).use { sock ->
                    sock.soTimeout = timeoutMs
                    sock.connect(InetSocketAddress(ip, port), timeoutMs)
                    val params = sock.sslParameters
                    if (sni.isNotBlank()) {
                        params.serverNames = listOf(SNIHostName(sni.trim()))
                    }
                    sock.sslParameters = params
                    sock.startHandshake()
                    tlsMs = ((System.nanoTime() - startTls) / 1_000_000L).toInt().coerceAtLeast(1)

                    // Tiny throughput/colo probe
                    val reqHost = sni.ifBlank { "speed.cloudflare.com" }
                    val path = if (provider == ScanProvider.CLOUDFLARE) "/__down?bytes=32768" else "/"
                    val req = "GET $path HTTP/1.1\r\nHost: $reqHost\r\nConnection: close\r\n\r\n"
                    val tStart = System.nanoTime()
                    sock.outputStream.write(req.toByteArray(Charsets.US_ASCII))
                    sock.outputStream.flush()
                    val buf = ByteArray(4096)
                    var totalRead = 0
                    val preview = StringBuilder()
                    while (true) {
                        val n = sock.inputStream.read(buf)
                        if (n <= 0) break
                        totalRead += n
                        if (preview.length < 512) {
                            preview.append(String(buf, 0, n.coerceAtMost(512), Charsets.US_ASCII))
                        }
                        if (totalRead >= 65_536) break
                    }
                    val elapsedMs = ((System.nanoTime() - tStart) / 1_000_000L).coerceAtLeast(1L)
                    if (totalRead > 0) {
                        speedKbps = ((totalRead / 1024.0) / (elapsedMs / 1000.0)).toInt().coerceAtLeast(1)
                    }
                    val cfRayMatch = Regex("cf-ray:\\s*[0-9a-fA-F]+-([A-Z]{3})", RegexOption.IGNORE_CASE).find(preview.toString())
                    colo = cfRayMatch?.groupValues?.getOrNull(1).orEmpty()
                }
            }
        }

        // Composite score (0..100) + Speed Tier
        var score = 100 - (avgMs / 6) - (lossPct / 2) - (jitterMs / 4)
        if (tlsMs != null) score += 10
        if (speedKbps >= 500) score += 12 else if (speedKbps >= 150) score += 6
        val finalScore = score.coerceIn(5, 100)

        val tier = when {
            lossPct == 0 && avgMs < 130 && (tlsMs != null || speedKbps > 200) -> ScanSpeedTier.S_PLUS
            lossPct == 0 && avgMs < 220 -> ScanSpeedTier.A
            lossPct <= 35 && avgMs < 400 -> ScanSpeedTier.B
            else -> ScanSpeedTier.C
        }

        ScanResultItem(
            id = UUID.randomUUID().toString(),
            ip = ip,
            provider = provider,
            port = port,
            latencyMs = avgMs,
            minLatencyMs = minMs,
            jitterMs = jitterMs,
            packetLossPct = lossPct,
            tlsHandshakeMs = tlsMs,
            downloadSpeedKbps = speedKbps,
            colo = colo,
            stabilityScore = finalScore,
            tier = tier,
            scannedAtEpochMs = System.currentTimeMillis(),
        )
    }

    private suspend fun loadCandidateIps(provider: ScanProvider, maxCount: Int): List<String> =
        withContext(Dispatchers.IO) {
            val assetFile = when (provider) {
                ScanProvider.CLOUDFLARE, ScanProvider.SPOOF_SCANNER -> "scanner/cf_v4.txt"
                ScanProvider.AKAMAI -> "scanner/akamai_v4.txt"
                ScanProvider.FASTLY -> "scanner/fastly_v4.txt"
                ScanProvider.GCORE -> "scanner/gcore_v4.txt"
                ScanProvider.WARP -> "scanner/warp_cidrs.txt"
                ScanProvider.CUSTOM -> null
            }
            val lines = if (assetFile != null) {
                runCatching { context.assets.open(assetFile).bufferedReader().readLines() }.getOrDefault(emptyList())
            } else {
                uiState.value.cleanIpsMultiline.lines()
            }.map { it.trim() }.filter { it.isNotBlank() && !it.startsWith("#") }

            val fallback = listOf("162.159.198.1", "162.159.192.1", "188.114.97.3", "104.16.132.229")
            val source = if (lines.isNotEmpty()) lines else fallback
            val out = LinkedHashSet<String>()
            var guard = 0
            while (out.size < maxCount && guard < maxCount * 5) {
                guard++
                val entry = source[Random.nextInt(source.size)]
                if (!entry.contains('/')) {
                    out += entry
                } else {
                    val base = entry.substringBefore('/')
                    val parts = base.split('.')
                    if (parts.size == 4) {
                        out += "${parts[0]}.${parts[1]}.${parts[2]}.${Random.nextInt(1, 254)}"
                    }
                }
            }
            out.toList()
        }

    private fun measureTcpMs(ip: String, port: Int, timeoutMs: Int): Int? = runCatching {
        val start = System.nanoTime()
        Socket().use { s -> s.connect(InetSocketAddress(ip, port), timeoutMs) }
        ((System.nanoTime() - start) / 1_000_000L).toInt().coerceAtLeast(1)
    }.getOrNull()
}
