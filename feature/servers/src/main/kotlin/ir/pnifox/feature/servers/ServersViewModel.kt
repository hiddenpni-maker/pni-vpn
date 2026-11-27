package ir.pnifox.feature.servers

import android.content.Intent
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import ir.pnifox.core.database.PnifoxDataRepository
import ir.pnifox.core.database.PreferencesRepository
import ir.pnifox.core.model.ActiveSectionEngine
import ir.pnifox.core.model.AppSettings
import ir.pnifox.core.model.ConnectionMode
import ir.pnifox.core.model.LogEntry
import ir.pnifox.core.model.ProxyProfile
import ir.pnifox.core.model.ProxyProtocol
import ir.pnifox.core.model.RecoveryAction
import ir.pnifox.core.model.SubscriptionGroup
import ir.pnifox.core.model.TransportMode
import ir.pnifox.core.model.TunnelState
import ir.pnifox.core.network.ShareLinkParser
import ir.pnifox.core.network.SubscriptionClient
import ir.pnifox.core.vpn.LogRingBuffer
import ir.pnifox.core.vpn.ProfilePingResult
import ir.pnifox.core.vpn.RealDelayProber
import ir.pnifox.core.vpn.VpnController
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.util.UUID
import javax.inject.Inject

enum class ServerCategory(val labelEn: String, val labelFa: String) {
    ALL("All", "همه"),
    VLESS("VLESS / Reality", "VLESS / Reality"),
    VMESS_TROJAN("VMess / Trojan", "VMess / Trojan"),
    HY2_WG("Hy2 / WG / SS / MASQUE", "Hy2 / WG / SS / MASQUE"),
    SUBSCRIPTIONS("Subscriptions", "اشتراک‌ها"),
}

data class ServersUiState(
    val tunnelState: TunnelState = TunnelState.Disconnected,
    val appSettings: AppSettings = AppSettings(),
    val selectedCategory: ServerCategory = ServerCategory.ALL,
    val profiles: List<ProxyProfile> = emptyList(),
    val subscriptions: List<SubscriptionGroup> = emptyList(),
    val activeProfile: ProxyProfile? = null,
    val statusMessage: String? = null,
    val isRefreshingSubs: Boolean = false,
    val isTestingActivePing: Boolean = false,
    val isTestingAllPings: Boolean = false,
    val pingingProfileIds: Set<String> = emptySet(),
    val rxSpeedBps: Long = 0L,
    val txSpeedBps: Long = 0L,
    val sessionRxBytes: Long = 0L,
    val sessionTxBytes: Long = 0L,
    val logs: List<LogEntry> = emptyList(),
)

private data class ServerRuntimeFlags(
    val selectedCategory: ServerCategory = ServerCategory.ALL,
    val statusMessage: String? = null,
    val isRefreshingSubs: Boolean = false,
    val isTestingActivePing: Boolean = false,
    val isTestingAllPings: Boolean = false,
    val pingingProfileIds: Set<String> = emptySet(),
)

private data class ServerTrafficTuple(
    val rxSpeedBps: Long,
    val txSpeedBps: Long,
    val sessionRxBytes: Long,
    val sessionTxBytes: Long,
)

@HiltViewModel
class ServersViewModel @Inject constructor(
    private val dataRepo: PnifoxDataRepository,
    private val preferencesRepo: PreferencesRepository,
    private val subscriptionClient: SubscriptionClient,
    private val vpnController: VpnController,
    private val realDelayProber: RealDelayProber,
    private val logRingBuffer: LogRingBuffer,
) : ViewModel() {

    private val runtimeFlags = MutableStateFlow(ServerRuntimeFlags())

    private val trafficFlow = combine(
        vpnController.rxSpeedBps,
        vpnController.txSpeedBps,
        vpnController.sessionRxBytes,
        vpnController.sessionTxBytes,
    ) { rx, tx, sRx, sTx ->
        ServerTrafficTuple(rx, tx, sRx, sTx)
    }

    val uiState: StateFlow<ServersUiState> = combine(
        vpnController.tunnelState,
        runtimeFlags,
        dataRepo.observeProfiles(),
        dataRepo.observeSubscriptions(),
        preferencesRepo.appSettingsFlow,
        trafficFlow,
        logRingBuffer.logsFlow,
    ) { values ->
        val tunnel = values[0] as TunnelState
        val flags = values[1] as ServerRuntimeFlags
        @Suppress("UNCHECKED_CAST")
        val profiles = values[2] as List<ProxyProfile>
        @Suppress("UNCHECKED_CAST")
        val subs = values[3] as List<SubscriptionGroup>
        val app = values[4] as AppSettings
        val traffic = values[5] as ServerTrafficTuple
        @Suppress("UNCHECKED_CAST")
        val logList = values[6] as List<LogEntry>

        val filtered = when (flags.selectedCategory) {
            ServerCategory.ALL -> profiles
            ServerCategory.VLESS -> profiles.filter { it.protocol == ProxyProtocol.VLESS }
            ServerCategory.VMESS_TROJAN -> profiles.filter {
                it.protocol == ProxyProtocol.VMESS || it.protocol == ProxyProtocol.TROJAN
            }
            ServerCategory.HY2_WG -> profiles.filter {
                it.protocol == ProxyProtocol.HYSTERIA2 ||
                    it.protocol == ProxyProtocol.TUIC ||
                    it.protocol == ProxyProtocol.ANYTLS ||
                    it.protocol == ProxyProtocol.WIREGUARD ||
                    it.protocol == ProxyProtocol.SHADOWSOCKS ||
                    it.protocol == ProxyProtocol.MASQUE
            }
            ServerCategory.SUBSCRIPTIONS -> profiles.filter { it.groupId != "manual" && it.groupId != "default" }
        }
        ServersUiState(
            tunnelState = tunnel,
            appSettings = app,
            selectedCategory = flags.selectedCategory,
            profiles = filtered,
            subscriptions = subs,
            activeProfile = profiles.firstOrNull { it.id == app.activeProfileId },
            statusMessage = flags.statusMessage,
            isRefreshingSubs = flags.isRefreshingSubs,
            isTestingActivePing = flags.isTestingActivePing,
            isTestingAllPings = flags.isTestingAllPings,
            pingingProfileIds = flags.pingingProfileIds,
            rxSpeedBps = traffic.rxSpeedBps,
            txSpeedBps = traffic.txSpeedBps,
            sessionRxBytes = traffic.sessionRxBytes,
            sessionTxBytes = traffic.sessionTxBytes,
            logs = logList,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000L),
        initialValue = ServersUiState(),
    )

    fun prepareVpnIntent(): Intent? = vpnController.prepareVpnPermissionIntent()

    fun toggleServerConnection() {
        viewModelScope.launch {
            vpnController.connectOrToggle(ActiveSectionEngine.SERVER)
        }
    }

    fun startServerAfterPermission() {
        viewModelScope.launch {
            vpnController.startConnection(ActiveSectionEngine.SERVER)
        }
    }

    fun reconnectServer() {
        viewModelScope.launch {
            val st = vpnController.tunnelState.value
            if (st is TunnelState.Connected || st is TunnelState.Connecting) {
                vpnController.disconnect()
                delay(400L)
            }
            vpnController.startConnection(ActiveSectionEngine.SERVER)
        }
    }

    fun updateApp(transform: (AppSettings) -> AppSettings) {
        viewModelScope.launch {
            preferencesRepo.updateAppSettings(transform)
        }
    }

    fun setConnectionMode(mode: ConnectionMode) {
        viewModelScope.launch {
            preferencesRepo.updateAppSettings { it.copy(connectionMode = mode) }
        }
    }

    fun toggleFragmentMode() {
        viewModelScope.launch {
            preferencesRepo.updateAppSettings {
                val next = if (it.transportMode == TransportMode.FRAGMENT) TransportMode.NORMAL else TransportMode.FRAGMENT
                it.copy(transportMode = next)
            }
        }
    }

    fun selectCategory(category: ServerCategory) {
        runtimeFlags.value = runtimeFlags.value.copy(selectedCategory = category)
    }

    fun selectProfile(profile: ProxyProfile) {
        viewModelScope.launch {
            preferencesRepo.updateAppSettings { it.copy(activeProfileId = profile.id) }
            runtimeFlags.value = runtimeFlags.value.copy(
                statusMessage = "Selected '${profile.name}' for Custom Server connection.",
            )
        }
    }

    fun importShareLinks(rawText: String) {
        viewModelScope.launch {
            val parsed = ShareLinkParser.parseBatch(rawText)
            if (parsed.isEmpty()) {
                runtimeFlags.value = runtimeFlags.value.copy(
                    statusMessage = "No valid vless://, vmess://, trojan://, ss://, hy2://, wg:// link or Xray/MASQUE JSON config found.",
                )
                return@launch
            }
            dataRepo.saveProfiles(parsed)
            if (uiState.value.activeProfile == null) {
                preferencesRepo.updateAppSettings { it.copy(activeProfileId = parsed.first().id) }
            }
            runtimeFlags.value = runtimeFlags.value.copy(
                statusMessage = "Imported ${parsed.size} server profile(s).",
            )
        }
    }

    fun addSubscription(name: String, url: String) {
        val cleanUrl = url.trim()
        if (!cleanUrl.startsWith("http")) {
            runtimeFlags.value = runtimeFlags.value.copy(
                statusMessage = "Subscription URL must start with http:// or https://",
            )
            return
        }
        viewModelScope.launch {
            val id = UUID.randomUUID().toString()
            val sub = SubscriptionGroup(
                id = id,
                name = name.ifBlank { "Subscription" },
                url = cleanUrl,
                lastUpdatedEpochMs = System.currentTimeMillis(),
            )
            dataRepo.saveSubscription(sub)
            refreshSingleSubscription(sub)
        }
    }

    fun refreshAllSubscriptions() {
        val subs = uiState.value.subscriptions
        if (subs.isEmpty()) {
            runtimeFlags.value = runtimeFlags.value.copy(statusMessage = "No subscriptions added yet.")
            return
        }
        viewModelScope.launch {
            subs.forEach { refreshSingleSubscription(it) }
        }
    }

    private suspend fun refreshSingleSubscription(sub: SubscriptionGroup) {
        runtimeFlags.value = runtimeFlags.value.copy(isRefreshingSubs = true)
        subscriptionClient.fetchSubscriptionProfiles(sub.url, sub.id)
            .onSuccess { list ->
                if (list.isNotEmpty()) {
                    dataRepo.deleteGroupProfiles(sub.id)
                    dataRepo.saveProfiles(list)
                    dataRepo.saveSubscription(
                        sub.copy(profileCount = list.size, lastUpdatedEpochMs = System.currentTimeMillis()),
                    )
                    if (uiState.value.activeProfile == null) {
                        preferencesRepo.updateAppSettings { it.copy(activeProfileId = list.first().id) }
                    }
                    runtimeFlags.value = runtimeFlags.value.copy(
                        statusMessage = "Synced '${sub.name}': ${list.size} servers.",
                    )
                } else {
                    runtimeFlags.value = runtimeFlags.value.copy(
                        statusMessage = "'${sub.name}' returned 0 valid servers.",
                    )
                }
            }
            .onFailure { err ->
                runtimeFlags.value = runtimeFlags.value.copy(
                    statusMessage = "Subscription sync failed (${sub.name}): ${err.message}",
                )
            }
        runtimeFlags.value = runtimeFlags.value.copy(isRefreshingSubs = false)
    }

    fun testActiveRealPing() {
        if (runtimeFlags.value.isTestingActivePing) return
        viewModelScope.launch {
            runtimeFlags.value = runtimeFlags.value.copy(isTestingActivePing = true)
            val ms = vpnController.refreshActiveRealDelay()
            runtimeFlags.value = runtimeFlags.value.copy(
                isTestingActivePing = false,
                statusMessage = if (ms != null) "Active tunnel Real Delay: ${ms}ms" else "Active tunnel Real Delay timed out.",
            )
        }
    }

    fun pingProfile(profile: ProxyProfile) {
        viewModelScope.launch {
            val res = runSingleRealPing(profile)
            val msg = when (res) {
                is ProfilePingResult.Success -> "${profile.name}: ${res.latencyMs}ms Real Delay (204)"
                is ProfilePingResult.XrayBusyWithOtherProfile ->
                    "Another Xray server is currently connected. Disconnect first (or test the active server)."
                is ProfilePingResult.Failed -> "${profile.name}: Real Delay failed (${res.reason})"
            }
            runtimeFlags.value = runtimeFlags.value.copy(statusMessage = msg)
        }
    }

    fun pingAllProfiles() {
        if (runtimeFlags.value.isTestingAllPings) return
        val targets = uiState.value.profiles
        if (targets.isEmpty()) return
        viewModelScope.launch {
            runtimeFlags.value = runtimeFlags.value.copy(isTestingAllPings = true, statusMessage = "Testing real HTTP delays (${targets.size} servers)...")
            var reachable = 0
            for (profile in targets) {
                val res = runSingleRealPing(profile)
                if (res is ProfilePingResult.Success) {
                    reachable++
                } else if (res is ProfilePingResult.XrayBusyWithOtherProfile) {
                    runtimeFlags.value = runtimeFlags.value.copy(
                        statusMessage = "Disconnect current Xray server before batch testing all Xray profiles.",
                    )
                    runtimeFlags.value = runtimeFlags.value.copy(isTestingAllPings = false)
                    return@launch
                }
            }
            runtimeFlags.value = runtimeFlags.value.copy(
                isTestingAllPings = false,
                statusMessage = "Real Delay test finished: $reachable/${targets.size} servers responsive.",
            )
        }
    }

    private suspend fun runSingleRealPing(profile: ProxyProfile): ProfilePingResult {
        runtimeFlags.value = runtimeFlags.value.copy(pingingProfileIds = runtimeFlags.value.pingingProfileIds + profile.id)
        val res = realDelayProber.measureProfileRealDelayMs(profile, uiState.value.appSettings)
        runtimeFlags.value = runtimeFlags.value.copy(pingingProfileIds = runtimeFlags.value.pingingProfileIds - profile.id)
        when (res) {
            is ProfilePingResult.Success -> dataRepo.saveProfile(profile.copy(lastPingMs = res.latencyMs))
            is ProfilePingResult.Failed -> dataRepo.saveProfile(profile.copy(lastPingMs = -1))
            is ProfilePingResult.XrayBusyWithOtherProfile -> Unit
        }
        return res
    }

    fun toggleSoldierSpoof(profile: ProxyProfile) {
        viewModelScope.launch {
            dataRepo.saveProfile(ShareLinkParser.toggleSoldierSniSpoof(profile))
        }
    }

    fun togglePin(profile: ProxyProfile) {
        viewModelScope.launch {
            dataRepo.saveProfile(profile.copy(isPinned = !profile.isPinned))
        }
    }

    fun saveQuickEditedProfile(
        existing: ProxyProfile,
        name: String,
        address: String,
        port: Int,
        secret: String,
        sni: String,
        transport: String,
        pathOrServiceName: String,
        tlsFingerprint: String,
        realityPublicKey: String,
        realityShortId: String,
        fragmentEnabled: Boolean,
        spoofLocalhost40443: Boolean,
        spoofFakeSni: String,
    ) {
        viewModelScope.launch {
            val updated = ShareLinkParser.applyQuickEdit(
                existing = existing,
                name = name,
                address = address,
                port = port,
                secret = secret,
                sni = sni,
                transport = transport,
                pathOrServiceName = pathOrServiceName,
                tlsFingerprint = tlsFingerprint,
                realityPublicKey = realityPublicKey,
                realityShortId = realityShortId,
                fragmentEnabled = fragmentEnabled,
                spoofLocalhost40443 = spoofLocalhost40443,
                spoofFakeSni = spoofFakeSni,
            )
            dataRepo.saveProfile(updated)
            runtimeFlags.value = runtimeFlags.value.copy(
                statusMessage = "Saved edits to '${updated.name}' (SNI=${updated.sni.ifBlank { "auto" }}).",
            )
        }
    }

    fun saveRawJsonEditedProfile(
        existing: ProxyProfile,
        rawJsonText: String,
        onResult: (Result<Unit>) -> Unit,
    ) {
        viewModelScope.launch {
            val res = ShareLinkParser.applyRawJsonEdit(existing, rawJsonText)
            res.onSuccess { updated ->
                dataRepo.saveProfile(updated)
                runtimeFlags.value = runtimeFlags.value.copy(
                    statusMessage = "Updated raw JSON config '${updated.name}'.",
                )
                onResult(Result.success(Unit))
            }.onFailure { err ->
                onResult(Result.failure(err))
            }
        }
    }

    fun deleteProfile(id: String) {
        viewModelScope.launch { dataRepo.deleteProfile(id) }
    }

    fun deleteSubscription(id: String) {
        viewModelScope.launch { dataRepo.deleteSubscription(id) }
    }

    fun applyRecoveryAction(action: RecoveryAction, onNavigateScanner: () -> Unit, onNavigateDns: () -> Unit) {
        when (action) {
            RecoveryAction.ENABLE_FRAGMENT -> {
                toggleFragmentMode()
                toggleServerConnection()
            }
            RecoveryAction.OPEN_CLOUDFLARE_SCANNER -> onNavigateScanner()
            RecoveryAction.TRY_DNS_TUNNEL -> onNavigateDns()
            else -> Unit
        }
    }

    fun dismissMessage() {
        runtimeFlags.value = runtimeFlags.value.copy(statusMessage = null)
    }

    fun clearLogs() = logRingBuffer.clear()
    fun exportLogs(): String = logRingBuffer.exportPlainText()
}
