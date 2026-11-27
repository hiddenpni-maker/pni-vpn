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
import ir.pnifox.core.vpn.LogEntry
import ir.pnifox.core.vpn.LogRingBuffer
import ir.pnifox.core.vpn.SmartConnectOrchestrator
import ir.pnifox.core.vpn.StrategyArm
import ir.pnifox.core.vpn.VpnController
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
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
    val isSmartConnecting: Boolean = false,
    val smartConnectStatus: String? = null,
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
    val isSmartConnecting: Boolean = false,
    val smartConnectStatus: String? = null,
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
            isSmartConnecting = rt.isSmartConnecting,
            smartConnectStatus = rt.smartConnectStatus,
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

    fun smartAutoConnect() {
        if (rtFlags.value.isSmartConnecting) return
        viewModelScope.launch {
            rtFlags.value = rtFlags.value.copy(isSmartConnecting = true, smartConnectStatus = "Testing ranked routes...")
            val banditJson = preferencesRepo.banditScoresFlow.first()
            val arms = SmartConnectOrchestrator.rankedArms(uiState.value.selectedScenario, banditJson).take(3)
            var currentJson = banditJson
            for ((idx, arm) in arms.withIndex()) {
                rtFlags.value = rtFlags.value.copy(smartConnectStatus = "Trying ${arm.titleEn} (${idx + 1}/${arms.size})...")
                val section = applyArm(arm)
                val t0 = System.currentTimeMillis()
                vpnController.startConnection(section)
                delay(400L)
                val deadline = System.currentTimeMillis() + 60_000L
                while (System.currentTimeMillis() < deadline && vpnController.tunnelState.value is TunnelState.Connecting) {
                    delay(350L)
                }
                val st = vpnController.tunnelState.value
                val elapsed = (System.currentTimeMillis() - t0).coerceAtLeast(50L)
                val ok = st is TunnelState.Connected && (vpnController.refreshActiveRealDelay() != null || arm == StrategyArm.GAMING_DNS)
                currentJson = SmartConnectOrchestrator.recordOutcome(currentJson, arm, ok, elapsed)
                preferencesRepo.saveBanditScores(currentJson)
                if (ok) {
                    rtFlags.value = rtFlags.value.copy(isSmartConnecting = false, smartConnectStatus = "Connected via ${arm.titleEn} ✓")
                    return@launch
                }
            }
            rtFlags.value = rtFlags.value.copy(isSmartConnecting = false, smartConnectStatus = "Smart Auto-Connect tested top routes.")
        }
    }

    private suspend fun applyArm(arm: StrategyArm): ActiveSectionEngine = when (arm) {
        StrategyArm.GAMING_DNS -> {
            preferencesRepo.updateGamingSettings { it.copy(mode = GamingMode.DNS_ANTI_SANCTION) }
            ActiveSectionEngine.GAMING
        }
        StrategyArm.LOCAL_DPI_DESYNC -> {
            preferencesRepo.updateGamingSettings { it.copy(mode = GamingMode.LOCAL_DPI_DESYNC) }
            ActiveSectionEngine.GAMING
        }
        StrategyArm.AETHER_MASQUE_H3 -> {
            preferencesRepo.updateGamingSettings { it.copy(mode = GamingMode.AETHER_UDP_QUIC, preferQuicV2H3 = true) }
            ActiveSectionEngine.GAMING
        }
        StrategyArm.AETHER_MASQUE_H2 -> {
            preferencesRepo.updateAppSettings { it.copy(activePreset = OneTapPreset.WARP_FAST_MASQUE_H2, exitNode = ExitNode.DEFAULT) }
            ActiveSectionEngine.AETHER
        }
        StrategyArm.AETHER_WG_NOIZE -> {
            preferencesRepo.updateAppSettings { it.copy(activePreset = OneTapPreset.WG_OVER_MASQUE, exitNode = ExitNode.DEFAULT) }
            ActiveSectionEngine.AETHER
        }
        StrategyArm.AETHER_PSIPHON -> {
            preferencesRepo.updateAppSettings { it.copy(activePreset = OneTapPreset.WARP_TO_PSIPHON, exitNode = ExitNode.PSIPHON) }
            preferencesRepo.updateAetherSettings { it.copy(psiphonEnabled = true) }
            ActiveSectionEngine.AETHER
        }
        StrategyArm.MHR_RELAY -> ActiveSectionEngine.MHR
    }

    fun exportLogs(): String = logRingBuffer.exportPlainText()
    fun clearLogs() = logRingBuffer.clear()
}
