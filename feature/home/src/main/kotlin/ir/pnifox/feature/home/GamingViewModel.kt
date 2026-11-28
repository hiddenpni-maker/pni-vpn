package ir.pnifox.feature.home

import android.content.Intent
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import ir.pnifox.core.database.PnifoxDataRepository
import ir.pnifox.core.database.PreferencesRepository
import ir.pnifox.core.model.ActiveSectionEngine
import ir.pnifox.core.model.AppSettings
import ir.pnifox.core.model.ExitNode
import ir.pnifox.core.model.GamingMode
import ir.pnifox.core.model.GamingSettings
import ir.pnifox.core.model.MasqueTransport
import ir.pnifox.core.model.OneTapPreset
import ir.pnifox.core.model.ProxyProfile
import ir.pnifox.core.model.ScenarioGoalId
import ir.pnifox.core.model.ScenarioProfile
import ir.pnifox.core.model.TunnelState
import ir.pnifox.core.vpn.DnsE2EScanner
import ir.pnifox.core.vpn.DnsSpyProbeResult
import ir.pnifox.core.vpn.GameLivePingResult
import ir.pnifox.core.vpn.WhiteGameOptimizer
import ir.pnifox.core.vpn.LogEntry
import ir.pnifox.core.vpn.LogRingBuffer
import ir.pnifox.core.vpn.VpnController
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class GamingUiState(
    val tunnelState: TunnelState = TunnelState.Disconnected,
    val gaming: GamingSettings = GamingSettings(),
    val appSettings: AppSettings = AppSettings(),
    val activeProfile: ProxyProfile? = null,
    val selectedScenario: ScenarioGoalId? = null,
    val isTestingDnsPing: Boolean = false,
    val dnsPingMap: Map<String, Int?> = emptyMap(),
    val isTestingActivePing: Boolean = false,
    val isRunningDnsSpy: Boolean = false,
    val dnsSpyResults: List<DnsSpyProbeResult> = emptyList(),
    val isProbingGames: Boolean = false,
    val gameCatalogResults: List<GameLivePingResult> = emptyList(),
    val rxSpeedBps: Long = 0L,
    val txSpeedBps: Long = 0L,
    val sessionRxBytes: Long = 0L,
    val sessionTxBytes: Long = 0L,
    val logs: List<LogEntry> = emptyList(),
)

private data class GamingRuntimeFlags(
    val isTestingDnsPing: Boolean = false,
    val dnsPingMap: Map<String, Int?> = emptyMap(),
    val isTestingActivePing: Boolean = false,
    val isRunningDnsSpy: Boolean = false,
    val dnsSpyResults: List<DnsSpyProbeResult> = emptyList(),
    val isProbingGames: Boolean = false,
    val gameCatalogResults: List<GameLivePingResult> = emptyList(),
)

private data class GamingTrafficTuple(
    val rxSpeedBps: Long,
    val txSpeedBps: Long,
    val sessionRxBytes: Long,
    val sessionTxBytes: Long,
)

@HiltViewModel
class GamingViewModel @Inject constructor(
    private val vpnController: VpnController,
    private val preferencesRepo: PreferencesRepository,
    private val dataRepo: PnifoxDataRepository,
    private val logRingBuffer: LogRingBuffer,
) : ViewModel() {

    private val rtFlags = MutableStateFlow(GamingRuntimeFlags())

    private val trafficFlow = combine(
        vpnController.rxSpeedBps,
        vpnController.txSpeedBps,
        vpnController.sessionRxBytes,
        vpnController.sessionTxBytes,
    ) { rx, tx, sRx, sTx -> GamingTrafficTuple(rx, tx, sRx, sTx) }

    val uiState: StateFlow<GamingUiState> = combine(
        vpnController.tunnelState,
        preferencesRepo.gamingSettingsFlow,
        preferencesRepo.appSettingsFlow,
        dataRepo.observeProfiles(),
        preferencesRepo.selectedScenarioIdFlow,
        rtFlags,
        trafficFlow,
        logRingBuffer.logsFlow,
    ) { values ->
        val state = values[0] as TunnelState
        val gaming = values[1] as GamingSettings
        val app = values[2] as AppSettings
        @Suppress("UNCHECKED_CAST")
        val profiles = values[3] as List<ProxyProfile>
        val scenario = values[4] as ScenarioGoalId?
        val rt = values[5] as GamingRuntimeFlags
        val tr = values[6] as GamingTrafficTuple
        @Suppress("UNCHECKED_CAST")
        val logList = values[7] as List<LogEntry>

        GamingUiState(
            tunnelState = state,
            gaming = gaming,
            appSettings = app,
            activeProfile = profiles.firstOrNull { it.id == app.activeProfileId },
            selectedScenario = scenario,
            isTestingDnsPing = rt.isTestingDnsPing,
            dnsPingMap = rt.dnsPingMap,
            isTestingActivePing = rt.isTestingActivePing,
            isRunningDnsSpy = rt.isRunningDnsSpy,
            dnsSpyResults = rt.dnsSpyResults,
            isProbingGames = rt.isProbingGames,
            gameCatalogResults = rt.gameCatalogResults,
            rxSpeedBps = tr.rxSpeedBps,
            txSpeedBps = tr.txSpeedBps,
            sessionRxBytes = tr.sessionRxBytes,
            sessionTxBytes = tr.sessionTxBytes,
            logs = logList,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000L), GamingUiState())

    fun prepareVpnIntent(): Intent? = vpnController.prepareVpnPermissionIntent()

    fun toggleGamingConnection() {
        viewModelScope.launch { vpnController.connectOrToggle(ActiveSectionEngine.GAMING) }
    }

    fun startGamingAfterPermission() {
        viewModelScope.launch { vpnController.startConnection(ActiveSectionEngine.GAMING) }
    }

    fun updateGaming(transform: (GamingSettings) -> GamingSettings) {
        viewModelScope.launch { preferencesRepo.updateGamingSettings(transform) }
    }

    fun testActivePing() {
        if (rtFlags.value.isTestingActivePing) return
        viewModelScope.launch {
            rtFlags.value = rtFlags.value.copy(isTestingActivePing = true)
            vpnController.refreshActiveRealDelay()
            rtFlags.value = rtFlags.value.copy(isTestingActivePing = false)
        }
    }

    fun testGamingDnsPings() {
        if (rtFlags.value.isTestingDnsPing) return
        viewModelScope.launch {
            rtFlags.value = rtFlags.value.copy(isTestingDnsPing = true)
            val targets = listOf("10.202.10.10", "185.55.226.26", "10.202.10.202", "178.22.122.100", "78.157.42.100", "1.1.1.1", "9.9.9.10")
            val results = DnsE2EScanner.scanResolvers(
                extraIpsCsv = targets.joinToString(","),
                probeDomain = "google.com",
            ).associate { res ->
                res.ip to if (res.isReachable && !res.isPoisoned) res.latencyMs.toInt() else null
            }
            rtFlags.value = rtFlags.value.copy(isTestingDnsPing = false, dnsPingMap = results)
        }
    }

    fun applyScenario(profile: ScenarioProfile, autoConnect: Boolean, onNavigateSection: (ActiveSectionEngine) -> Unit) {
        viewModelScope.launch {
            preferencesRepo.saveSelectedScenarioId(profile.id)
            preferencesRepo.updateAppSettings {
                it.copy(
                    activeSection = profile.recommendedSection,
                    activePreset = profile.recommendedAetherPreset,
                    exitNode = if (profile.recommendedPsiphon) ExitNode.PSIPHON else ExitNode.DEFAULT,
                )
            }
            preferencesRepo.updateAetherSettings {
                it.copy(
                    protocol = profile.recommendedAetherProtocol,
                    masqueTransport = if (profile.recommendedQuicV2) MasqueTransport.HTTP3 else MasqueTransport.HTTP2,
                    psiphonEnabled = profile.recommendedPsiphon,
                    psiphonMode = profile.recommendedPsiphonMode ?: it.psiphonMode,
                    psiphonCdnSets = if (profile.recommendedPsiphonMode == ir.pnifox.core.model.PsiphonChainMode.STANDALONE_CDN && it.psiphonCdnSets.isBlank()) {
                        "psiphon-akamai"
                    } else {
                        it.psiphonCdnSets
                    },
                    torEnabled = false,
                    noizeProfile = profile.recommendedNoiseProfile,
                    echEnabled = profile.recommendedEch,
                    quicV2Enabled = profile.recommendedQuicV2,
                )
            }
            preferencesRepo.updateGamingSettings {
                it.copy(mode = profile.recommendedGamingMode, preferQuicV2H3 = profile.recommendedQuicV2)
            }
            preferencesRepo.updateDnsSettings {
                it.copy(engineType = profile.recommendedDnsEngine)
            }
            if (profile.recommendedSection != ActiveSectionEngine.GAMING) {
                onNavigateSection(profile.recommendedSection)
            }
            if (autoConnect) {
                vpnController.startConnection(profile.recommendedSection)
            }
        }
    }

    fun runDnsSpyOptimizer() {
        if (rtFlags.value.isRunningDnsSpy) return
        viewModelScope.launch {
            rtFlags.value = rtFlags.value.copy(isRunningDnsSpy = true)
            val res = WhiteGameOptimizer.runDnsSpyScan()
            rtFlags.value = rtFlags.value.copy(isRunningDnsSpy = false, dnsSpyResults = res)
        }
    }

    fun runGameCatalogPing() {
        if (rtFlags.value.isProbingGames) return
        viewModelScope.launch {
            rtFlags.value = rtFlags.value.copy(isProbingGames = true)
            val res = WhiteGameOptimizer.probeGameCatalog()
            rtFlags.value = rtFlags.value.copy(isProbingGames = false, gameCatalogResults = res)
        }
    }

    fun exportLogs(): String = logRingBuffer.exportPlainText()
    fun clearLogs() = logRingBuffer.clear()
}
