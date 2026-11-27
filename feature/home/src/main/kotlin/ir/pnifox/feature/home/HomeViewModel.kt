package ir.pnifox.feature.home

import android.content.Context
import android.content.Intent
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import ir.pnifox.core.database.PnifoxDataRepository
import ir.pnifox.core.database.PreferencesRepository
import ir.pnifox.core.model.ActiveSectionEngine
import ir.pnifox.core.model.AetherIdentityTarget
import ir.pnifox.core.model.AetherProtocol
import ir.pnifox.core.model.AetherSettings
import ir.pnifox.core.model.AppSettings
import ir.pnifox.core.model.ConnectionMode
import ir.pnifox.core.model.ExitNode
import ir.pnifox.core.model.GamingMode
import ir.pnifox.core.model.MasqueTransport
import ir.pnifox.core.model.OneTapPreset
import ir.pnifox.core.model.PsiphonChainMode
import ir.pnifox.core.model.RecoveryAction
import ir.pnifox.core.model.ScenarioGoalId
import ir.pnifox.core.model.ScenarioProfile
import ir.pnifox.core.model.TransportMode
import ir.pnifox.core.model.TunnelState
import ir.pnifox.core.network.WarpKeyClient
import ir.pnifox.core.vpn.AetherArgBuilder
import ir.pnifox.core.vpn.AetherIdentityFilesState
import ir.pnifox.core.vpn.EchBootstrapResolver
import ir.pnifox.core.vpn.LogEntry
import ir.pnifox.core.vpn.LogLevel
import ir.pnifox.core.vpn.LogRingBuffer
import ir.pnifox.core.vpn.NativeProcessManager
import ir.pnifox.core.vpn.VpnController
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject

data class HomeUiState(
    val tunnelState: TunnelState = TunnelState.Disconnected,
    val appSettings: AppSettings = AppSettings(),
    val aetherSettings: AetherSettings = AetherSettings(),
    val identitiesState: AetherIdentityFilesState = AetherIdentityFilesState(false, false, false),
    val commandPreview: String = "",
    val selectedKeyTarget: AetherIdentityTarget = AetherIdentityTarget.ALL,
    val isRegisteringKeys: Boolean = false,
    val isTestingPing: Boolean = false,
    val isFetchingEch: Boolean = false,
    val selectedScenario: ScenarioGoalId? = null,
    val keyActionMessage: String? = null,
    val rxSpeedBps: Long = 0L,
    val txSpeedBps: Long = 0L,
    val sessionRxBytes: Long = 0L,
    val sessionTxBytes: Long = 0L,
    val logs: List<LogEntry> = emptyList(),
)

private data class AetherRuntimeTuple(
    val selectedKeyTarget: AetherIdentityTarget = AetherIdentityTarget.ALL,
    val isRegisteringKeys: Boolean = false,
    val isTestingPing: Boolean = false,
    val isFetchingEch: Boolean = false,
    val keyActionMessage: String? = null,
    val identities: AetherIdentityFilesState = AetherIdentityFilesState(false, false, false),
)

private data class HomeTrafficTuple(
    val rxSpeedBps: Long,
    val txSpeedBps: Long,
    val sessionRx: Long,
    val sessionTx: Long,
)

@HiltViewModel
class HomeViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val vpnController: VpnController,
    private val processManager: NativeProcessManager,
    private val warpKeyClient: WarpKeyClient,
    private val aetherArgBuilder: AetherArgBuilder,
    private val preferencesRepo: PreferencesRepository,
    private val dataRepo: PnifoxDataRepository,
    private val logRingBuffer: LogRingBuffer,
) : ViewModel() {

    private val aetherRuntime = MutableStateFlow(
        AetherRuntimeTuple(identities = processManager.inspectAetherIdentities()),
    )

    private val trafficFlow = combine(
        vpnController.rxSpeedBps,
        vpnController.txSpeedBps,
        vpnController.sessionRxBytes,
        vpnController.sessionTxBytes,
    ) { rx, tx, sRx, sTx ->
        HomeTrafficTuple(rx, tx, sRx, sTx)
    }

    val uiState: StateFlow<HomeUiState> = combine(
        vpnController.tunnelState,
        preferencesRepo.appSettingsFlow,
        preferencesRepo.aetherSettingsFlow,
        preferencesRepo.selectedScenarioIdFlow,
        aetherRuntime,
        trafficFlow,
        logRingBuffer.logsFlow,
    ) { values ->
        val state = values[0] as TunnelState
        val app = values[1] as AppSettings
        val aether = values[2] as AetherSettings
        val scenario = values[3] as ScenarioGoalId?
        val rt = values[4] as AetherRuntimeTuple
        val traffic = values[5] as HomeTrafficTuple
        @Suppress("UNCHECKED_CAST")
        val logList = values[6] as List<LogEntry>

        val psiphonDat = File(context.filesDir, "aether/psiphon_servers.dat").absolutePath
        HomeUiState(
            tunnelState = state,
            appSettings = app,
            aetherSettings = aether,
            identitiesState = rt.identities,
            commandPreview = aetherArgBuilder.formatCommandPreview(app, aether, psiphonDat),
            selectedKeyTarget = rt.selectedKeyTarget,
            isRegisteringKeys = rt.isRegisteringKeys,
            isTestingPing = rt.isTestingPing,
            isFetchingEch = rt.isFetchingEch,
            selectedScenario = scenario,
            keyActionMessage = rt.keyActionMessage,
            rxSpeedBps = traffic.rxSpeedBps,
            txSpeedBps = traffic.txSpeedBps,
            sessionRxBytes = traffic.sessionRx,
            sessionTxBytes = traffic.sessionTx,
            logs = logList,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000L),
        initialValue = HomeUiState(),
    )

    fun prepareVpnIntent(): Intent? = vpnController.prepareVpnPermissionIntent()

    fun toggleAetherConnection() {
        viewModelScope.launch {
            vpnController.connectOrToggle(ActiveSectionEngine.AETHER)
        }
    }

    fun startAetherAfterPermission() {
        viewModelScope.launch {
            vpnController.startConnection(ActiveSectionEngine.AETHER)
        }
    }

    fun setConnectionMode(mode: ConnectionMode) {
        viewModelScope.launch {
            preferencesRepo.updateAppSettings { it.copy(connectionMode = mode) }
        }
    }

    fun selectKeyTarget(target: AetherIdentityTarget) {
        aetherRuntime.value = aetherRuntime.value.copy(selectedKeyTarget = target)
    }

    fun generateAetherKeys() {
        if (aetherRuntime.value.isRegisteringKeys) return
        val target = aetherRuntime.value.selectedKeyTarget
        val aether = uiState.value.aetherSettings

        viewModelScope.launch {
            aetherRuntime.value = aetherRuntime.value.copy(
                isRegisteringKeys = true,
                keyActionMessage = "Registering ${target.label} keys via AETHER core...",
            )

            // Prefer native libaether.so --register so aether*.toml gets full MASQUE cert + WG keys.
            val nativeRes = processManager.runAetherRegister(target, aether)
            if (nativeRes.isSuccess) {
                val ids = processManager.inspectAetherIdentities()
                preferencesRepo.updateAetherSettings {
                    it.copy(
                        masqueAccountRegistered = ids.hasMasqueToml || it.masqueAccountRegistered,
                        wgAccountRegistered = ids.hasWgToml || it.wgAccountRegistered,
                        goolAccountRegistered = ids.hasGoolToml || it.goolAccountRegistered,
                    )
                }
                aetherRuntime.value = aetherRuntime.value.copy(
                    isRegisteringKeys = false,
                    identities = ids,
                    keyActionMessage = "Keys generated and saved to aether config (${nativeRes.getOrNull()}).",
                )
            } else {
                // Fallback direct WARP registration via Cloudflare API
                warpKeyClient.registerFreshKey(
                    protocolTag = target.name,
                    primaryHost = aether.warpApiPrimaryHost,
                    fallbackHost = aether.warpApiFallbackHost,
                ).onSuccess { key ->
                    dataRepo.saveWarpKey(key)
                    preferencesRepo.updateAetherSettings { it.copy(masqueAccountRegistered = true) }
                    aetherRuntime.value = aetherRuntime.value.copy(
                        isRegisteringKeys = false,
                        keyActionMessage = "API fallback registered key (${key.deviceId.take(8)}).",
                    )
                }.onFailure { err ->
                    aetherRuntime.value = aetherRuntime.value.copy(
                        isRegisteringKeys = false,
                        keyActionMessage = "Key generation failed: ${nativeRes.exceptionOrNull()?.message ?: err.message}",
                    )
                }
            }
        }
    }

    fun updateAether(transform: (AetherSettings) -> AetherSettings) {
        viewModelScope.launch {
            preferencesRepo.updateAetherSettings(transform)
        }
    }

    fun updateApp(transform: (AppSettings) -> AppSettings) {
        viewModelScope.launch {
            preferencesRepo.updateAppSettings(transform)
        }
    }

    fun testActiveRealPing() {
        if (aetherRuntime.value.isTestingPing) return
        viewModelScope.launch {
            aetherRuntime.value = aetherRuntime.value.copy(isTestingPing = true)
            vpnController.refreshActiveRealDelay()
            aetherRuntime.value = aetherRuntime.value.copy(isTestingPing = false)
        }
    }

    fun reconnectAether() {
        viewModelScope.launch {
            val st = vpnController.tunnelState.value
            if (st is TunnelState.Connected || st is TunnelState.Connecting) {
                vpnController.disconnect()
                kotlinx.coroutines.delay(400L)
            }
            vpnController.startConnection(ActiveSectionEngine.AETHER)
        }
    }

    fun clearAppliedEndpointIp() {
        updateAether { it.copy(endpointIp = "", scanForEndpoint = true) }
        aetherRuntime.value = aetherRuntime.value.copy(keyActionMessage = "Cleared saved endpoint IP.")
    }

    fun clearAppliedCdnIps() {
        updateAether { it.copy(psiphonCdnFrontingIps = "") }
        aetherRuntime.value = aetherRuntime.value.copy(keyActionMessage = "Cleared saved Akamai / CDN IPs.")
    }

    fun clearAllAppliedIps() {
        updateAether { it.copy(endpointIp = "", scanForEndpoint = true, psiphonCdnFrontingIps = "") }
        aetherRuntime.value = aetherRuntime.value.copy(keyActionMessage = "Cleared all applied IPs.")
    }

    fun selectRegion(countryCode: String) {
        viewModelScope.launch {
            val clean = countryCode.uppercase()
            preferencesRepo.updateAppSettings { it.copy(regionCode = clean) }
            preferencesRepo.updateAetherSettings { it.copy(psiphonExitCountry = clean) }
        }
    }

    fun enablePsiphonForRegion() {
        applyOneTapPreset(OneTapPreset.WARP_TO_PSIPHON)
    }

    fun testAndSortPsiphonCdnEdges() {
        if (aetherRuntime.value.isFetchingEch) return
        val aether = uiState.value.aetherSettings
        val tokens = aether.psiphonCdnFrontingIps
            .split(',', ';', '\n', '\r', '\t', ' ')
            .map { it.trim() }
            .filter { it.isNotBlank() }
        if (tokens.isEmpty()) {
            aetherRuntime.value = aetherRuntime.value.copy(keyActionMessage = "Enter Akamai/CDN IPs first or pick psiphon-akamai set.")
            return
        }
        viewModelScope.launch {
            aetherRuntime.value = aetherRuntime.value.copy(isFetchingEch = true, keyActionMessage = "Testing ${tokens.size} CDN edge IP(s) on :443...")
            val sni = aether.psiphonCdnSniHostnames.split(',', ';', '\n', ' ').firstOrNull { it.isNotBlank() }.orEmpty()
            val expanded = tokens.flatMap { entry ->
                if (!entry.contains('/')) listOf(entry)
                else {
                    val base = entry.substringBefore('/').split('.')
                    if (base.size == 4) listOf("${base[0]}.${base[1]}.${base[2]}.1", "${base[0]}.${base[1]}.${base[2]}.10") else emptyList()
                }
            }.distinct().take(48)
            val ranked = withContext(Dispatchers.IO) {
                expanded.mapNotNull { ip ->
                    val start = System.nanoTime()
                    val ok = runCatching {
                        java.net.Socket().use { s -> s.connect(java.net.InetSocketAddress(ip, 443), 2200) }
                        if (sni.isNotBlank()) true else true
                    }.isSuccess
                    if (ok) ip to ((System.nanoTime() - start) / 1_000_000L).coerceAtLeast(1L).toInt() else null
                }.sortedBy { it.second }
            }
            if (ranked.isEmpty()) {
                aetherRuntime.value = aetherRuntime.value.copy(
                    isFetchingEch = false,
                    keyActionMessage = "No entered edge IPs answered :443. Built-in psiphon-akamai list will be tried.",
                )
            } else {
                val sortedIps = ranked.map { it.first }.joinToString(", ")
                preferencesRepo.updateAetherSettings { it.copy(psiphonCdnFrontingIps = sortedIps) }
                aetherRuntime.value = aetherRuntime.value.copy(
                    isFetchingEch = false,
                    keyActionMessage = "${ranked.size}/${expanded.size} CDN IPs reachable (fastest: ${ranked.first().first} @ ${ranked.first().second}ms)",
                )
            }
        }
    }

    fun rescanAetherEndpointAndConnect() {
        viewModelScope.launch {
            preferencesRepo.updateAetherSettings {
                it.copy(
                    endpointIp = "",
                    scanForEndpoint = true,
                    scanMode = ir.pnifox.core.model.AetherScanMode.TURBO,
                )
            }
            logRingBuffer.append("Controller", LogLevel.INFO, "Fresh diverse endpoint scan requested (Turbo across all WARP prefixes).")
            val st = vpnController.tunnelState.value
            if (st is TunnelState.Connected || st is TunnelState.Connecting) {
                vpnController.disconnect()
                kotlinx.coroutines.delay(350L)
            }
            vpnController.startConnection(ActiveSectionEngine.AETHER)
        }
    }

    fun applyOneTapPreset(preset: OneTapPreset) {
        viewModelScope.launch {
            preferencesRepo.updateAppSettings { current ->
                when (preset) {
                    OneTapPreset.WARP_FAST_MASQUE_H2 -> current.copy(
                        activePreset = preset,
                        exitNode = ExitNode.DEFAULT,
                    )
                    OneTapPreset.WARP_TO_PSIPHON, OneTapPreset.PSIPHON_AKAMAI_CDN -> current.copy(
                        activePreset = preset,
                        exitNode = ExitNode.PSIPHON,
                    )
                    OneTapPreset.WARP_TO_TOR -> current.copy(
                        activePreset = preset,
                        exitNode = ExitNode.TOR,
                    )
                    else -> current.copy(activePreset = preset, exitNode = ExitNode.DEFAULT)
                }
            }
            preferencesRepo.updateAetherSettings { current ->
                when (preset) {
                    OneTapPreset.WARP_FAST_MASQUE_H2 -> current.copy(
                        protocol = AetherProtocol.MASQUE,
                        masqueTransport = MasqueTransport.HTTP2,
                        fragmentEnabled = false,
                        psiphonEnabled = false,
                        torEnabled = false,
                    )
                    OneTapPreset.WARP_TO_PSIPHON -> current.copy(
                        protocol = AetherProtocol.MASQUE,
                        masqueTransport = MasqueTransport.HTTP2,
                        psiphonEnabled = true,
                        psiphonMode = if (current.psiphonMode.isStandalone) PsiphonChainMode.WARP_TO_PSIPHON else current.psiphonMode,
                        torEnabled = false,
                    )
                    OneTapPreset.PSIPHON_AKAMAI_CDN -> current.copy(
                        protocol = AetherProtocol.MASQUE,
                        masqueTransport = MasqueTransport.HTTP2,
                        psiphonEnabled = true,
                        psiphonMode = PsiphonChainMode.STANDALONE_CDN,
                        psiphonCdnSets = current.psiphonCdnSets.ifBlank { "psiphon-akamai" },
                        torEnabled = false,
                    )
                    OneTapPreset.WG_OVER_MASQUE -> current.copy(
                        protocol = AetherProtocol.WIREGUARD_OVER_MASQUE,
                        psiphonEnabled = false,
                        torEnabled = false,
                    )
                    OneTapPreset.MASQUE_IN_MASQUE -> current.copy(
                        protocol = AetherProtocol.MASQUE_IN_MASQUE,
                        psiphonEnabled = false,
                        torEnabled = false,
                    )
                    OneTapPreset.WARP_TO_TOR -> current.copy(
                        protocol = AetherProtocol.MASQUE,
                        psiphonEnabled = false,
                        torEnabled = true,
                    )
                }
            }
        }
    }

    fun applyRecoveryAction(
        action: RecoveryAction,
        onNavigateScanner: () -> Unit,
        onNavigateDns: () -> Unit,
        onOpenLogs: () -> Unit,
    ) {
        when (action) {
            RecoveryAction.ENABLE_FRAGMENT -> {
                updateAether { it.copy(fragmentEnabled = true) }
                toggleAetherConnection()
            }
            RecoveryAction.SWITCH_WARP_PSIPHON -> {
                applyOneTapPreset(OneTapPreset.WARP_TO_PSIPHON)
                toggleAetherConnection()
            }
            RecoveryAction.OPEN_CLOUDFLARE_SCANNER -> onNavigateScanner()
            RecoveryAction.TRY_DNS_TUNNEL -> onNavigateDns()
            RecoveryAction.RENEW_WARP_KEYS -> generateAetherKeys()
            RecoveryAction.OPEN_LOGS -> onOpenLogs()
        }
    }

    fun applyScenario(
        profile: ScenarioProfile,
        autoConnect: Boolean,
        onNavigateSection: (ActiveSectionEngine) -> Unit,
    ) {
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
                    psiphonCdnSets = if (profile.recommendedPsiphonMode == PsiphonChainMode.STANDALONE_CDN && it.psiphonCdnSets.isBlank()) {
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
            if (profile.recommendedSection != ActiveSectionEngine.AETHER) {
                onNavigateSection(profile.recommendedSection)
            }
            if (autoConnect) {
                vpnController.startConnection(profile.recommendedSection)
            }
        }
    }

    fun fetchEchConfigNow() {
        if (aetherRuntime.value.isFetchingEch) return
        viewModelScope.launch {
            aetherRuntime.value = aetherRuntime.value.copy(isFetchingEch = true)
            val aether = uiState.value.aetherSettings
            val res = withContext(Dispatchers.IO) {
                EchBootstrapResolver.resolveEchConfig(
                    targetDomain = aether.echDomain.ifBlank { "cloudflare-ech.com" },
                    customDohOrDns = aether.echDns,
                    onLog = { logRingBuffer.append("ECH-Bootstrap", LogLevel.INFO, it) },
                )
            }
            if (res != null) {
                preferencesRepo.updateAetherSettings {
                    it.copy(echEnabled = true, echBase64Override = res.echConfigBase64)
                }
                aetherRuntime.value = aetherRuntime.value.copy(
                    isFetchingEch = false,
                    keyActionMessage = "Smart ECH ready via ${res.sourceLabel}",
                )
            } else {
                aetherRuntime.value = aetherRuntime.value.copy(
                    isFetchingEch = false,
                    keyActionMessage = "Could not fetch external ECH; Aether will use --ech auto.",
                )
            }
        }
    }

    fun dismissBanner() {
        aetherRuntime.value = aetherRuntime.value.copy(keyActionMessage = null)
    }

    fun clearLogs() = logRingBuffer.clear()
    fun exportLogs(): String = logRingBuffer.exportPlainText()
}
