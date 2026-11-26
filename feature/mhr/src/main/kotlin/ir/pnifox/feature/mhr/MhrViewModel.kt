package ir.pnifox.feature.mhr

import android.content.Context
import android.content.Intent
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import ir.pnifox.core.database.PreferencesRepository
import ir.pnifox.core.model.ActiveSectionEngine
import ir.pnifox.core.model.AppSettings
import ir.pnifox.core.model.LogEntry
import ir.pnifox.core.model.LogLevel
import ir.pnifox.core.model.MhrDefaultSniPool
import ir.pnifox.core.model.MhrMode
import ir.pnifox.core.model.MhrSettings
import ir.pnifox.core.model.MhrSniTestResult
import ir.pnifox.core.model.MhrStatsSnapshot
import ir.pnifox.core.model.TunnelState
import ir.pnifox.core.network.MhrShareCodec
import ir.pnifox.core.vpn.LogRingBuffer
import ir.pnifox.core.vpn.MhrCaInstaller
import ir.pnifox.core.vpn.MhrCaStatus
import ir.pnifox.core.vpn.MhrEngineManager
import ir.pnifox.core.vpn.VpnController
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

private data class MhrRuntimeFlags(
    val isTestingPing: Boolean = false,
    val isDetectingIp: Boolean = false,
    val isTestingSnis: Boolean = false,
    val testingSniHost: String? = null,
    val sniResults: Map<String, MhrSniTestResult> = emptyMap(),
    val caStatus: MhrCaStatus = MhrCaStatus(),
    val statusMessage: String? = null,
)

private data class MhrTrafficTuple(
    val rxSpeedBps: Long = 0L,
    val txSpeedBps: Long = 0L,
    val sessionRxBytes: Long = 0L,
    val sessionTxBytes: Long = 0L,
    val stats: MhrStatsSnapshot? = null,
)

data class MhrUiState(
    val tunnelState: TunnelState = TunnelState.Disconnected,
    val appSettings: AppSettings = AppSettings(),
    val settings: MhrSettings = MhrSettings(),
    val mhrVersion: String = "1.9.38",
    val isTestingPing: Boolean = false,
    val isDetectingIp: Boolean = false,
    val isTestingSnis: Boolean = false,
    val testingSniHost: String? = null,
    val sniResults: Map<String, MhrSniTestResult> = emptyMap(),
    val caStatus: MhrCaStatus = MhrCaStatus(),
    val statusMessage: String? = null,
    val rxSpeedBps: Long = 0L,
    val txSpeedBps: Long = 0L,
    val sessionRxBytes: Long = 0L,
    val sessionTxBytes: Long = 0L,
    val mhrStats: MhrStatsSnapshot? = null,
    val logs: List<LogEntry> = emptyList(),
)

@HiltViewModel
class MhrViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val preferencesRepo: PreferencesRepository,
    private val vpnController: VpnController,
    private val mhrEngineManager: MhrEngineManager,
    private val logRingBuffer: LogRingBuffer,
) : ViewModel() {

    private val runtimeFlags = MutableStateFlow(MhrRuntimeFlags())
    private val coreVersion = mhrEngineManager.coreVersion()

    init {
        refreshCaStatus()
    }

    private val trafficFlow = combine(
        vpnController.rxSpeedBps,
        vpnController.txSpeedBps,
        vpnController.sessionRxBytes,
        vpnController.sessionTxBytes,
        mhrEngineManager.statsFlow,
    ) { rx, tx, sRx, sTx, stats ->
        MhrTrafficTuple(rx, tx, sRx, sTx, stats)
    }

    val uiState: StateFlow<MhrUiState> = combine(
        vpnController.tunnelState,
        preferencesRepo.appSettingsFlow,
        preferencesRepo.mhrSettingsFlow,
        runtimeFlags,
        trafficFlow,
        logRingBuffer.logsFlow,
    ) { values ->
        val tunnel = values[0] as TunnelState
        val app = values[1] as AppSettings
        val mhr = values[2] as MhrSettings
        val flags = values[3] as MhrRuntimeFlags
        val traffic = values[4] as MhrTrafficTuple
        @Suppress("UNCHECKED_CAST")
        val logList = values[5] as List<LogEntry>

        MhrUiState(
            tunnelState = tunnel,
            appSettings = app,
            settings = mhr,
            mhrVersion = coreVersion,
            isTestingPing = flags.isTestingPing,
            isDetectingIp = flags.isDetectingIp,
            isTestingSnis = flags.isTestingSnis,
            testingSniHost = flags.testingSniHost,
            sniResults = flags.sniResults,
            caStatus = flags.caStatus,
            statusMessage = flags.statusMessage,
            rxSpeedBps = traffic.rxSpeedBps,
            txSpeedBps = traffic.txSpeedBps,
            sessionRxBytes = traffic.sessionRxBytes,
            sessionTxBytes = traffic.sessionTxBytes,
            mhrStats = traffic.stats,
            logs = logList,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000L),
        initialValue = MhrUiState(mhrVersion = coreVersion),
    )

    fun prepareVpnIntent(): Intent? = vpnController.prepareVpnPermissionIntent()

    fun toggleMhrConnection() {
        viewModelScope.launch {
            vpnController.connectOrToggle(ActiveSectionEngine.MHR)
        }
    }

    fun startMhrAfterPermission() {
        viewModelScope.launch {
            vpnController.startConnection(ActiveSectionEngine.MHR)
        }
    }

    fun reconnectMhr() {
        viewModelScope.launch {
            val st = vpnController.tunnelState.value
            if (st is TunnelState.Connected || st is TunnelState.Connecting) {
                vpnController.disconnect()
                delay(400L)
            }
            vpnController.startConnection(ActiveSectionEngine.MHR)
        }
    }

    fun updateApp(transform: (AppSettings) -> AppSettings) = viewModelScope.launch {
        preferencesRepo.updateAppSettings(transform)
    }

    fun updateMhr(transform: (MhrSettings) -> MhrSettings) = viewModelScope.launch {
        preferencesRepo.updateMhrSettings(transform)
    }

    fun setMode(mode: MhrMode) = updateMhr { it.copy(mode = mode) }

    fun addScriptIds(rawInput: String) {
        val tokens = rawInput
            .split('\n', '\r', ' ', '\t', ',', ';')
            .map { MhrDefaultSniPool.extractDeploymentId(it) }
            .filter { it.isNotBlank() }
        if (tokens.isEmpty()) {
            runtimeFlags.value = runtimeFlags.value.copy(
                statusMessage = "Paste a valid Apps Script Web App URL or Deployment ID.",
            )
            return
        }
        viewModelScope.launch {
            var addedCount = 0
            preferencesRepo.updateMhrSettings { cur ->
                val merged = (cur.scriptIds + tokens).distinct()
                addedCount = merged.size - cur.scriptIds.size
                cur.copy(scriptIds = merged)
            }
            runtimeFlags.value = runtimeFlags.value.copy(
                statusMessage = "Added $addedCount Deployment ID(s).",
            )
        }
    }

    fun removeScriptId(id: String) = updateMhr { cur ->
        cur.copy(scriptIds = cur.scriptIds.filterNot { it == id })
    }

    fun setAuthKey(v: String) = updateMhr { it.copy(authKey = v) }
    fun setGoogleIp(v: String) = updateMhr { it.copy(googleIp = v) }
    fun setFrontDomain(v: String) = updateMhr { it.copy(frontDomain = v) }

    fun autoDetectGoogleIp() {
        if (runtimeFlags.value.isDetectingIp) return
        val domain = uiState.value.settings.frontDomain.trim().ifBlank { "www.google.com" }
        viewModelScope.launch {
            runtimeFlags.value = runtimeFlags.value.copy(isDetectingIp = true, statusMessage = "Resolving $domain edge IP...")
            val resolved = mhrEngineManager.autoDetectGoogleIp(domain)
            if (resolved != null) {
                preferencesRepo.updateMhrSettings { it.copy(googleIp = resolved) }
                runtimeFlags.value = runtimeFlags.value.copy(
                    isDetectingIp = false,
                    statusMessage = "Detected google_ip = $resolved",
                )
            } else {
                runtimeFlags.value = runtimeFlags.value.copy(
                    isDetectingIp = false,
                    statusMessage = "Failed to auto-detect IPv4 for $domain",
                )
            }
        }
    }

    fun addSniHost(host: String) {
        val clean = host.trim()
        if (clean.isBlank()) return
        updateMhr { cur ->
            cur.copy(sniHosts = (cur.sniHosts + clean).distinct())
        }
    }

    fun removeSniHost(host: String) = updateMhr { cur ->
        cur.copy(sniHosts = cur.sniHosts.filterNot { it == host })
    }

    fun resetSniPool() = updateMhr { it.copy(sniHosts = MhrDefaultSniPool.defaults) }

    fun testSingleSni(host: String) {
        if (runtimeFlags.value.testingSniHost != null) return
        val ip = uiState.value.settings.googleIp.trim().ifBlank { "216.239.38.120" }
        viewModelScope.launch {
            runtimeFlags.value = runtimeFlags.value.copy(testingSniHost = host)
            val res = mhrEngineManager.testSni(ip, host)
            runtimeFlags.value = runtimeFlags.value.copy(
                testingSniHost = null,
                sniResults = runtimeFlags.value.sniResults + (host to res),
                statusMessage = if (res.ok) "$host -> ${res.latencyMs}ms OK" else "$host failed: ${res.error}",
            )
        }
    }

    fun testAllSnis() {
        if (runtimeFlags.value.isTestingSnis) return
        val ip = uiState.value.settings.googleIp.trim().ifBlank { "216.239.38.120" }
        val pool = uiState.value.settings.sniHosts
        if (pool.isEmpty()) return
        viewModelScope.launch {
            runtimeFlags.value = runtimeFlags.value.copy(isTestingSnis = true, statusMessage = "Testing ${pool.size} SNIs via $ip...")
            var okCount = 0
            for (sni in pool) {
                runtimeFlags.value = runtimeFlags.value.copy(testingSniHost = sni)
                val res = mhrEngineManager.testSni(ip, sni)
                if (res.ok) okCount++
                runtimeFlags.value = runtimeFlags.value.copy(
                    sniResults = runtimeFlags.value.sniResults + (sni to res),
                )
            }
            runtimeFlags.value = runtimeFlags.value.copy(
                isTestingSnis = false,
                testingSniHost = null,
                statusMessage = "SNI Probe finished: $okCount/${pool.size} reachable on $ip",
            )
        }
    }

    fun refreshCaStatus() {
        viewModelScope.launch {
            val status = withContext(Dispatchers.IO) { MhrCaInstaller.exportAndInspect(context) }
            runtimeFlags.value = runtimeFlags.value.copy(caStatus = status)
        }
    }

    fun exportCaAndGetSettingsIntent(onReady: (Intent, MhrCaStatus) -> Unit) {
        viewModelScope.launch {
            val res = withContext(Dispatchers.IO) { MhrCaInstaller.exportAndSaveToDownloads(context) }
            res.onSuccess { status ->
                runtimeFlags.value = runtimeFlags.value.copy(
                    caStatus = status,
                    statusMessage = "Saved CA certificate to ${status.savedLocationLabel}",
                )
                logRingBuffer.append("MHR-CA", LogLevel.INFO, "Exported MITM CA PEM -> ${status.savedLocationLabel} (SHA-256 ${status.fingerprintHex})")
                onReady(MhrCaInstaller.buildOpenSettingsForCaIntent(), status)
            }.onFailure { err ->
                runtimeFlags.value = runtimeFlags.value.copy(
                    statusMessage = "CA export failed: ${err.message}",
                )
            }
        }
    }

    fun importShareUri(raw: String) {
        val decoded = MhrShareCodec.decode(raw, uiState.value.settings)
        decoded.onSuccess { imported ->
            viewModelScope.launch {
                preferencesRepo.updateMhrSettings { imported }
                runtimeFlags.value = runtimeFlags.value.copy(
                    statusMessage = "Imported mhrv-rs:// config (${imported.scriptIds.size} IDs, mode=${imported.mode.labelEn})",
                )
            }
        }.onFailure { err ->
            runtimeFlags.value = runtimeFlags.value.copy(
                statusMessage = err.message ?: "Invalid mhrv-rs:// config",
            )
        }
    }

    fun exportShareUri(): String = MhrShareCodec.encode(uiState.value.settings)

    fun readCodeGs(fullTunnel: Boolean): String = mhrEngineManager.readBundledScript(fullTunnel)

    fun testActiveRealPing() {
        if (runtimeFlags.value.isTestingPing) return
        viewModelScope.launch {
            runtimeFlags.value = runtimeFlags.value.copy(isTestingPing = true)
            vpnController.refreshActiveRealDelay()
            runtimeFlags.value = runtimeFlags.value.copy(isTestingPing = false)
        }
    }

    fun dismissBanner() {
        runtimeFlags.value = runtimeFlags.value.copy(statusMessage = null)
    }

    fun clearLogs() = logRingBuffer.clear()
    fun exportLogs(): String = logRingBuffer.exportPlainText()
}
