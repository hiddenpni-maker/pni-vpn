package ir.pnifox.feature.scanner

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import ir.pnifox.core.database.PnifoxDataRepository
import ir.pnifox.core.database.PreferencesRepository
import ir.pnifox.core.model.ActiveSectionEngine
import ir.pnifox.core.model.ExitNode
import ir.pnifox.core.model.OneTapPreset
import ir.pnifox.core.model.PsiphonChainMode
import ir.pnifox.core.model.ScanCategoryFilter
import ir.pnifox.core.model.ScanProvider
import ir.pnifox.core.model.ScanResultItem
import ir.pnifox.core.model.ScanSortBy
import ir.pnifox.core.model.ScanSpeedTier
import ir.pnifox.core.model.ScanStyle
import ir.pnifox.core.vpn.JvmCdnScanner
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.UUID
import javax.inject.Inject

data class ScannerUiState(
    val provider: ScanProvider = ScanProvider.CLOUDFLARE,
    val scanStyle: ScanStyle = ScanStyle.TURBO_FAST,
    val port: Int = 443,
    val portText: String = "443",
    val candidateCount: Int = 80,
    val candidateCountText: String = "80",
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
    val hasAppliedIps: Boolean = false,
    val statusNote: String? = null,
)

private data class ScanConfigState(
    val provider: ScanProvider = ScanProvider.CLOUDFLARE,
    val scanStyle: ScanStyle = ScanStyle.TURBO_FAST,
    val port: Int = 443,
    val portText: String = "443",
    val candidateCount: Int = 80,
    val candidateCountText: String = "80",
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
        preferencesRepo.aetherSettingsFlow,
    ) { rt, rows, app, aether ->
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
            scanStyle = rt.scanStyle,
            port = rt.port,
            portText = rt.portText,
            candidateCount = rt.candidateCount,
            candidateCountText = rt.candidateCountText,
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
            hasAppliedIps = aether.endpointIp.isNotBlank() || aether.psiphonCdnFrontingIps.isNotBlank(),
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
            portText = nextPort.toString(),
            statusNote = null,
        )
    }

    fun setPort(rawText: String) {
        val digits = rawText.filter { it.isDigit() }.take(5)
        val parsed = digits.toIntOrNull()?.coerceIn(1, 65535)
        cfg.value = cfg.value.copy(
            portText = digits,
            port = parsed ?: cfg.value.port,
        )
    }

    fun setScanStyle(style: ScanStyle) {
        cfg.value = cfg.value.copy(scanStyle = style)
    }

    fun setCandidateCountText(rawText: String) {
        val digits = rawText.filter { it.isDigit() }.take(4)
        val parsed = digits.toIntOrNull()?.coerceIn(10, 500)
        cfg.value = cfg.value.copy(
            candidateCountText = digits,
            candidateCount = parsed ?: cfg.value.candidateCount,
        )
    }

    fun setCandidateCount(count: Int) {
        val c = count.coerceIn(10, 500)
        cfg.value = cfg.value.copy(candidateCount = c, candidateCountText = c.toString())
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
            cfg.value = snapshot.copy(isScanning = true, progressLabel = "Sampling diverse subnets...", statusNote = null)
            val candidates = loadCandidateIps(snapshot.provider, snapshot.candidateCount)
            val effectivePort = if (snapshot.provider == ScanProvider.AKAMAI) 443 else (snapshot.portText.toIntOrNull()?.coerceIn(1, 65535) ?: 443)
            val ports = when {
                snapshot.provider == ScanProvider.AKAMAI -> listOf(443)
                snapshot.provider == ScanProvider.WARP && snapshot.scanStyle == ScanStyle.DEEP_MULTIPORT ->
                    listOf(443, 8443, 4443, 2408)
                snapshot.scanStyle == ScanStyle.DEEP_MULTIPORT ->
                    (listOf(effectivePort) + snapshot.scanStyle.ports).distinct()
                else -> listOf(effectivePort)
            }
            val rawResults = JvmCdnScanner.scanCandidates(
                ips = candidates,
                ports = ports,
                provider = snapshot.provider.name.lowercase(),
                sni = snapshot.sniHost,
                style = snapshot.scanStyle,
                testTls = snapshot.enableTlsAndSpeedTest,
                measureColoAndSpeed = snapshot.enableTlsAndSpeedTest,
                gentleMode = snapshot.gentleMode,
                onProgress = { p ->
                    cfg.value = cfg.value.copy(progressLabel = "Probed ${p.tested}/${p.total} • Clean: ${p.healthy}")
                },
            )
            val discovered = rawResults.map { r ->
                val itemScore = (100 - (r.latencyMs / 6) - (r.lossPct.toInt() / 2) - (r.jitterMs / 4) +
                    (if (r.tlsHandshakeMs != null) 10 else 0) +
                    (if (r.downloadSpeedKbps >= 500) 12 else if (r.downloadSpeedKbps >= 150) 6 else 0)).coerceIn(5, 100)
                val tier = when {
                    r.lossPct == 0.0 && r.latencyMs < 130 && (r.tlsHandshakeMs != null || r.downloadSpeedKbps > 200) -> ScanSpeedTier.S_PLUS
                    r.lossPct == 0.0 && r.latencyMs < 220 -> ScanSpeedTier.A
                    r.lossPct <= 35.0 && r.latencyMs < 400 -> ScanSpeedTier.B
                    else -> ScanSpeedTier.C
                }
                ScanResultItem(
                    id = UUID.randomUUID().toString(),
                    ip = r.ip,
                    provider = snapshot.provider,
                    port = r.port,
                    latencyMs = r.latencyMs,
                    minLatencyMs = r.minLatencyMs,
                    jitterMs = r.jitterMs,
                    packetLossPct = r.lossPct.toInt(),
                    tlsHandshakeMs = r.tlsHandshakeMs,
                    downloadSpeedKbps = r.downloadSpeedKbps,
                    colo = r.colo,
                    stabilityScore = itemScore,
                    tier = tier,
                    scannedAtEpochMs = System.currentTimeMillis(),
                )
            }

            if (discovered.isNotEmpty()) {
                dataRepo.replaceScanResults(snapshot.provider, discovered.sortedByDescending { it.stabilityScore })
            }
            cfg.value = cfg.value.copy(
                isScanning = false,
                progressLabel = "Finished: ${discovered.size}/${candidates.size} diverse reachable IPs",
                statusNote = if (discovered.isEmpty()) {
                    "No clean TCP/TLS responses on port ${snapshot.port}. Try Deep Multi-Port or different CDN provider."
                } else {
                    "Found ${discovered.size} IPs across diverse subnets ranked by ping, loss, jitter, and download speed."
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

    fun clearAppliedEndpointIp() {
        viewModelScope.launch {
            preferencesRepo.updateAetherSettings {
                it.copy(endpointIp = "", scanForEndpoint = true)
            }
            cfg.value = cfg.value.copy(statusNote = "Cleared applied WARP endpoint IP (Auto-Scan enabled).")
        }
    }

    fun clearAppliedCdnIps() {
        viewModelScope.launch {
            preferencesRepo.updateAetherSettings {
                it.copy(psiphonCdnFrontingIps = "")
            }
            cfg.value = cfg.value.copy(statusNote = "Cleared applied Akamai / CDN fronting IPs.")
        }
    }

    fun clearAllAppliedIps() {
        viewModelScope.launch {
            preferencesRepo.updateAetherSettings {
                it.copy(endpointIp = "", scanForEndpoint = true, psiphonCdnFrontingIps = "")
            }
            cfg.value = cfg.value.copy(statusNote = "Cleared all applied IPs.")
        }
    }

    fun applyIpsToPsiphonCdn(ips: List<String>, provider: ScanProvider) {
        val cleaned = ips.map { it.trim() }.filter { it.isNotBlank() }.distinct()
        if (cleaned.isEmpty()) return
        viewModelScope.launch {
            val current = preferencesRepo.aetherSettingsFlow.first()
            val existing = current.psiphonCdnFrontingIps
                .split(',', ';', '\n', ' ')
                .map { it.trim() }
                .filter { it.isNotBlank() }
            val merged = (cleaned + existing).distinct().take(60).joinToString(", ")
            preferencesRepo.updateAppSettings {
                it.copy(
                    activeSection = ActiveSectionEngine.AETHER,
                    activePreset = OneTapPreset.PSIPHON_AKAMAI_CDN,
                    exitNode = ExitNode.PSIPHON,
                )
            }
            preferencesRepo.updateAetherSettings {
                it.copy(
                    psiphonEnabled = true,
                    psiphonMode = PsiphonChainMode.STANDALONE_CDN,
                    psiphonCdnFrontingIps = merged,
                    psiphonCdnSets = if (provider == ScanProvider.AKAMAI) "psiphon-akamai" else it.psiphonCdnSets,
                    torEnabled = false,
                )
            }
            cfg.value = cfg.value.copy(statusNote = "Applied ${cleaned.size} $provider IP(s) to Shir-o-Khorshid Akamai / CDN Fronting.")
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
            JvmCdnScanner.expandCidrs(source, maxCount)
        }
}
