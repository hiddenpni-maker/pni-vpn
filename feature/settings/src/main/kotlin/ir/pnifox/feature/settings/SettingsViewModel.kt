package ir.pnifox.feature.settings

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import ir.pnifox.core.database.PnifoxDataRepository
import ir.pnifox.core.database.PreferencesRepository
import ir.pnifox.core.model.AccentColorPref
import ir.pnifox.core.model.AetherProtocol
import ir.pnifox.core.model.AetherScanMode
import ir.pnifox.core.model.AetherSettings
import ir.pnifox.core.model.AppSettings
import ir.pnifox.core.model.ExitDialMode
import ir.pnifox.core.model.IpVersionMode
import ir.pnifox.core.model.MasqueTransport
import ir.pnifox.core.model.NativeBinaryStatus
import ir.pnifox.core.model.PsiphonChainMode
import ir.pnifox.core.model.ThemeModePref
import ir.pnifox.core.model.TlsFingerprint
import ir.pnifox.core.model.TunnelState
import ir.pnifox.core.model.UpdateCheckState
import ir.pnifox.core.network.AppUpdateChecker
import ir.pnifox.core.network.WarpKeyClient
import ir.pnifox.core.vpn.AetherArgBuilder
import ir.pnifox.core.vpn.LanNetworkHelper
import ir.pnifox.core.vpn.LogEntry
import ir.pnifox.core.vpn.LogLevel
import ir.pnifox.core.vpn.LogRingBuffer
import ir.pnifox.core.vpn.NativeBinaryRegistry
import ir.pnifox.core.vpn.VpnController
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.io.File
import javax.inject.Inject

data class SettingsUiState(
    val tunnelState: TunnelState = TunnelState.Disconnected,
    val appSettings: AppSettings = AppSettings(),
    val aetherSettings: AetherSettings = AetherSettings(),
    val commandPreview: String = "",
    val binaries: List<NativeBinaryStatus> = emptyList(),
    val logs: List<LogEntry> = emptyList(),
    val isFetchingWarpKeys: Boolean = false,
    val statusBanner: String? = null,
    val currentVersionName: String = "2.0.0",
    val updateState: UpdateCheckState = UpdateCheckState.Idle,
    val detectedLanIps: List<String> = emptyList(),
)

@HiltViewModel
class SettingsViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val preferencesRepo: PreferencesRepository,
    private val dataRepo: PnifoxDataRepository,
    private val binaryRegistry: NativeBinaryRegistry,
    private val aetherArgBuilder: AetherArgBuilder,
    private val warpKeyClient: WarpKeyClient,
    private val logRingBuffer: LogRingBuffer,
    private val vpnController: VpnController,
) : ViewModel() {

    private val isFetchingWarpKeys = MutableStateFlow(false)
    private val statusBanner = MutableStateFlow<String?>(null)
    private val binariesSnapshot = MutableStateFlow(binaryRegistry.inspectAll())
    private val updateChecker = AppUpdateChecker()
    private val updateStateFlow = MutableStateFlow<UpdateCheckState>(UpdateCheckState.Idle)
    private val detectedLanIpsFlow = MutableStateFlow(LanNetworkHelper.detectLocalIpv4Addresses())
    private val appVersion: String = runCatching {
        context.packageManager.getPackageInfo(context.packageName, 0).versionName ?: "2.0.0"
    }.getOrDefault("2.0.0")

    private val extraUiFlow = combine(updateStateFlow, detectedLanIpsFlow) { u, ips -> u to ips }

    val uiState: StateFlow<SettingsUiState> = combine(
        vpnController.tunnelState,
        preferencesRepo.appSettingsFlow,
        preferencesRepo.aetherSettingsFlow,
        binariesSnapshot,
        logRingBuffer.logsFlow,
        isFetchingWarpKeys,
        statusBanner,
        extraUiFlow,
    ) { values ->
        val tunnel = values[0] as TunnelState
        val app = values[1] as AppSettings
        val aether = values[2] as AetherSettings
        @Suppress("UNCHECKED_CAST")
        val bins = values[3] as List<NativeBinaryStatus>
        @Suppress("UNCHECKED_CAST")
        val logs = values[4] as List<LogEntry>
        val fetching = values[5] as Boolean
        val banner = values[6] as String?
        @Suppress("UNCHECKED_CAST")
        val extra = values[7] as Pair<UpdateCheckState, List<String>>

        val psiphonDatPath = File(context.filesDir, "aether/psiphon_servers.dat").absolutePath
        SettingsUiState(
            tunnelState = tunnel,
            appSettings = app,
            aetherSettings = aether,
            commandPreview = aetherArgBuilder.formatCommandPreview(app, aether, psiphonDatPath),
            binaries = bins,
            logs = logs,
            isFetchingWarpKeys = fetching,
            statusBanner = banner,
            currentVersionName = appVersion,
            updateState = extra.first,
            detectedLanIps = extra.second,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000L),
        initialValue = SettingsUiState(currentVersionName = appVersion),
    )

    fun setThemeMode(mode: ThemeModePref) = viewModelScope.launch {
        preferencesRepo.updateAppSettings { it.copy(themeMode = mode) }
    }

    fun setAccentColor(accent: AccentColorPref) = viewModelScope.launch {
        preferencesRepo.updateAppSettings { it.copy(accentColor = accent) }
    }

    fun updateApp(transform: (AppSettings) -> AppSettings) = viewModelScope.launch {
        preferencesRepo.updateAppSettings(transform)
    }

    fun updateAether(transform: (AetherSettings) -> AetherSettings) = viewModelScope.launch {
        preferencesRepo.updateAetherSettings(transform)
    }

    fun fetchWarpKeys(target: String) {
        if (isFetchingWarpKeys.value) return
        val aether = uiState.value.aetherSettings
        val tags = when (target) {
            "ALL" -> listOf("MASQUE", "WIREGUARD")
            else -> listOf(target)
        }
        viewModelScope.launch {
            isFetchingWarpKeys.value = true
            statusBanner.value = "Registering fresh WARP keys via ${aether.warpApiPrimaryHost}..."
            var okCount = 0
            var lastErr = ""
            for (tag in tags) {
                warpKeyClient.registerFreshKey(
                    protocolTag = tag,
                    primaryHost = aether.warpApiPrimaryHost,
                    fallbackHost = aether.warpApiFallbackHost,
                ).onSuccess { keyEntity ->
                    dataRepo.saveWarpKey(keyEntity)
                    okCount++
                    logRingBuffer.append("WARP", LogLevel.INFO, "Registered $tag key (device=${keyEntity.deviceId.take(8)})")
                }.onFailure { err ->
                    lastErr = err.message ?: "Network error"
                    logRingBuffer.append("WARP", LogLevel.ERROR, "Failed $tag registration: $lastErr")
                }
            }
            if (okCount > 0) {
                preferencesRepo.updateAetherSettings { it.copy(masqueAccountRegistered = true) }
                statusBanner.value = "$okCount WARP key(s) saved successfully."
            } else {
                statusBanner.value = "WARP key registration failed: $lastErr"
            }
            isFetchingWarpKeys.value = false
        }
    }

    fun clearPsiphonData() {
        viewModelScope.launch {
            val dir = File(context.filesDir, "aether/psiphon-cache")
            val deleted = if (dir.exists()) dir.deleteRecursively() else true
            logRingBuffer.append("Psiphon", LogLevel.INFO, "Cleared Psiphon cache directory ($deleted).")
            statusBanner.value = "Psiphon cached state cleared."
        }
    }

    fun refreshBinaryCheck() {
        binariesSnapshot.value = binaryRegistry.inspectAll()
        statusBanner.value = "Refreshed native core binary status."
    }

    fun reconnectActiveTunnel() = viewModelScope.launch {
        val current = vpnController.tunnelState.value
        val activeSection = when (current) {
            is TunnelState.Connected -> current.section
            is TunnelState.Connecting -> current.section
            else -> uiState.value.appSettings.activeSection
        }
        if (current is TunnelState.Connected || current is TunnelState.Connecting) {
            vpnController.disconnect()
            delay(400L)
        }
        vpnController.startConnection(activeSection)
    }

    fun resetDefaults() = viewModelScope.launch {
        preferencesRepo.resetAllDefaults()
        statusBanner.value = "All settings restored to defaults."
    }

    fun checkForUpdates() {
        if (updateStateFlow.value is UpdateCheckState.Checking) return
        viewModelScope.launch {
            updateStateFlow.value = UpdateCheckState.Checking
            val res = updateChecker.checkLatestRelease(appVersion)
            updateStateFlow.value = res
            when (res) {
                is UpdateCheckState.UpdateAvailable -> {
                    logRingBuffer.append("Updater", LogLevel.INFO, "New version available: ${res.info.latestTag} (current $appVersion)")
                }
                is UpdateCheckState.UpToDate -> {
                    logRingBuffer.append("Updater", LogLevel.INFO, "App is up to date ($appVersion)")
                }
                is UpdateCheckState.Error -> {
                    logRingBuffer.append("Updater", LogLevel.WARN, "Update check failed: ${res.message}")
                }
                else -> Unit
            }
        }
    }

    fun refreshLanIps() {
        detectedLanIpsFlow.value = LanNetworkHelper.detectLocalIpv4Addresses()
    }

    fun exportLogs(): String = logRingBuffer.exportPlainText()
    fun clearLogs() = logRingBuffer.clear()
    fun dismissBanner() {
        statusBanner.value = null
    }
}
