package ir.pnifox.core.vpn

import android.content.Context
import android.content.Intent
import android.net.VpnService
import androidx.core.content.ContextCompat
import dagger.hilt.android.qualifiers.ApplicationContext
import ir.pnifox.core.database.PnifoxDataRepository
import ir.pnifox.core.database.PreferencesRepository
import ir.pnifox.core.model.ActiveSectionEngine
import ir.pnifox.core.model.ConnectionError
import ir.pnifox.core.model.ConnectionMode
import ir.pnifox.core.model.DnsEngineType
import ir.pnifox.core.model.NativeBinaryStatus
import ir.pnifox.core.model.TunnelState
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class VpnStateHolder @Inject constructor() {
    private val _tunnelState = MutableStateFlow<TunnelState>(TunnelState.Disconnected)
    val tunnelState: StateFlow<TunnelState> = _tunnelState.asStateFlow()

    private val _rxSpeedBps = MutableStateFlow(0L)
    val rxSpeedBps: StateFlow<Long> = _rxSpeedBps.asStateFlow()

    private val _txSpeedBps = MutableStateFlow(0L)
    val txSpeedBps: StateFlow<Long> = _txSpeedBps.asStateFlow()

    private val _sessionRxBytes = MutableStateFlow(0L)
    val sessionRxBytes: StateFlow<Long> = _sessionRxBytes.asStateFlow()

    private val _sessionTxBytes = MutableStateFlow(0L)
    val sessionTxBytes: StateFlow<Long> = _sessionTxBytes.asStateFlow()

    fun updateState(state: TunnelState) {
        _tunnelState.value = state
        if (state is TunnelState.Disconnected || state is TunnelState.Error) {
            _rxSpeedBps.value = 0L
            _txSpeedBps.value = 0L
        }
    }

    fun updateConnectedPing(pingMs: Int?) {
        val current = _tunnelState.value
        if (current is TunnelState.Connected) {
            _tunnelState.value = current.copy(pingMs = pingMs)
        }
    }

    fun updateSpeed(rxBps: Long, txBps: Long, rxDelta: Long, txDelta: Long) {
        _rxSpeedBps.value = rxBps
        _txSpeedBps.value = txBps
        _sessionRxBytes.value += rxDelta
        _sessionTxBytes.value += txDelta
    }

    fun resetSessionCounters() {
        _sessionRxBytes.value = 0L
        _sessionTxBytes.value = 0L
    }
}

@Singleton
class VpnController @Inject constructor(
    @ApplicationContext private val context: Context,
    private val binaryRegistry: NativeBinaryRegistry,
    private val processManager: NativeProcessManager,
    private val realDelayProber: RealDelayProber,
    private val preferencesRepo: PreferencesRepository,
    private val dataRepo: PnifoxDataRepository,
    private val stateHolder: VpnStateHolder,
    private val logs: LogRingBuffer,
) {
    val tunnelState: StateFlow<TunnelState> = stateHolder.tunnelState
    val rxSpeedBps: StateFlow<Long> = stateHolder.rxSpeedBps
    val txSpeedBps: StateFlow<Long> = stateHolder.txSpeedBps
    val sessionRxBytes: StateFlow<Long> = stateHolder.sessionRxBytes
    val sessionTxBytes: StateFlow<Long> = stateHolder.sessionTxBytes

    private val exceptionHandler = CoroutineExceptionHandler { _, throwable ->
        val msg = throwable.message ?: throwable::class.java.simpleName
        logs.append("Controller", LogLevel.ERROR, "Coroutine failure: $msg")
        stateHolder.updateState(
            TunnelState.Error(
                ActiveSectionEngine.AETHER,
                ConnectionError.ProcessExited(-1, msg),
            ),
        )
    }
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO + exceptionHandler)
    private var proxyConnectJob: Job? = null
    @Volatile private var lastToggleTimestampMs = 0L

    init {
        runCatching {
            val crashFile = File(context.filesDir, "last_crash.txt")
            if (crashFile.exists()) {
                val report = crashFile.readText().trim()
                if (report.isNotEmpty()) {
                    logs.append("CrashReport", LogLevel.ERROR, report)
                }
                crashFile.delete()
            }
        }
    }

    fun inspectBinaries(): List<NativeBinaryStatus> = binaryRegistry.inspectAll()

    fun inspectAetherIdentities(): AetherIdentityFilesState = processManager.inspectAetherIdentities()

    fun prepareVpnPermissionIntent(): Intent? =
        runCatching { VpnService.prepare(context) }.getOrNull()

    suspend fun connectOrToggle(section: ActiveSectionEngine? = null) {
        val now = System.currentTimeMillis()
        if (now - lastToggleTimestampMs < 400L) return
        lastToggleTimestampMs = now

        when (stateHolder.tunnelState.value) {
            is TunnelState.Connected, is TunnelState.Connecting -> {
                disconnect()
            }
            is TunnelState.Disconnected, is TunnelState.Error -> {
                val targetSection = section ?: preferencesRepo.appSettingsFlow.first().activeSection
                startConnection(targetSection)
            }
        }
    }

    suspend fun refreshActiveRealDelay(): Int? {
        val connected = stateHolder.tunnelState.value as? TunnelState.Connected ?: return null
        val dns = preferencesRepo.dnsSettingsFlow.first()
        val gaming = preferencesRepo.gamingSettingsFlow.first()
        val app = preferencesRepo.appSettingsFlow.first()
        val effectiveDns = when {
            connected.section == ActiveSectionEngine.DNS -> dns
            connected.section == ActiveSectionEngine.GAMING && gaming.mode == ir.pnifox.core.model.GamingMode.DNS_ANTI_SANCTION ->
                ir.pnifox.core.model.DnsTunnelSettings().copy(
                    mode = ir.pnifox.core.model.DnsTunnelMode.UDP,
                    resolverAddress = gaming.selectedDnsIp,
                )
            else -> null
        }
        val rtt = realDelayProber.measureActiveTunnelDelayMs(
            engine = connected.engine,
            localSocksPort = connected.localSocksPort,
            dnsSettings = effectiveDns,
        )
        stateHolder.updateConnectedPing(rtt)
        if (connected.section == ActiveSectionEngine.SERVER && rtt != null) {
            app.activeProfileId?.let { id ->
                dataRepo.getProfile(id)?.let { profile ->
                    dataRepo.saveProfile(profile.copy(lastPingMs = rtt))
                }
            }
        }
        if (rtt != null) {
            logs.append("RealDelay", LogLevel.INFO, "Real HTTP/tunnel delay: ${rtt}ms (${connected.engine.displayEn})")
        } else {
            logs.append("RealDelay", LogLevel.WARN, "Real delay probe timed out on :${connected.localSocksPort}")
        }
        return rtt
    }

    suspend fun startConnection(section: ActiveSectionEngine) {
        if (stateHolder.tunnelState.value is TunnelState.Connecting) return
        preferencesRepo.updateAppSettings { it.copy(activeSection = section) }

        val appSettings = preferencesRepo.appSettingsFlow.first()
        val aetherSettings = preferencesRepo.aetherSettingsFlow.first()
        val dnsSettings = preferencesRepo.dnsSettingsFlow.first()
        val mhrSettings = preferencesRepo.mhrSettingsFlow.first()
        val gamingSettings = preferencesRepo.gamingSettingsFlow.first()
        val profile = appSettings.activeProfileId?.let { dataRepo.getProfile(it) }
        val engine = binaryRegistry.resolveEngineForSection(section, appSettings, aetherSettings, profile, dnsSettings, gamingSettings)

        stateHolder.updateState(
            TunnelState.Connecting(
                section = section,
                stageLabelEn = "Starting ${engine.displayEn}...",
                stageLabelFa = "در حال راه‌اندازی ${engine.displayFa}...",
                targetLabel = "127.0.0.1:${aetherSettings.localSocksPort}",
            ),
        )
        logs.append("Controller", LogLevel.INFO, "Checking native cores for ${section.titleEn}...")

        val missing = binaryRegistry.verifyRequiredBinaries(section, appSettings, aetherSettings, profile, dnsSettings, gamingSettings)
        if (missing != null) {
            logs.append("BinaryGuard", LogLevel.ERROR, "Missing binary: ${missing.expectedAbiPath}")
            stateHolder.updateState(TunnelState.Error(section, missing))
            return
        }

        if (appSettings.connectionMode == ConnectionMode.VPN) {
            val permissionIntent = prepareVpnPermissionIntent()
            if (permissionIntent != null) {
                logs.append("Controller", LogLevel.WARN, "Android VPN consent required.")
                stateHolder.updateState(TunnelState.Error(section, ConnectionError.PermissionDenied()))
                return
            }
            val startIntent = Intent(context, PniVpnService::class.java).apply {
                action = PniVpnService.ACTION_START
                putExtra(PniVpnService.EXTRA_SECTION, section.name)
            }
            runCatching {
                ContextCompat.startForegroundService(context, startIntent)
            }.onFailure { err ->
                val msg = err.message ?: err::class.java.simpleName
                logs.append("Controller", LogLevel.ERROR, "Cannot start PniVpnService: $msg")
                stateHolder.updateState(
                    TunnelState.Error(
                        section,
                        ConnectionError.ProcessExited(-1, "VpnService start failed: $msg"),
                    ),
                )
            }
        } else {
            stateHolder.resetSessionCounters()
            proxyConnectJob?.cancel()
            proxyConnectJob = scope.launch {
                var discoveredIp: String? = null
                var discoveredPort: Int? = null
                val onStage: (String, String) -> Unit = { stageEn, stageFa ->
                    stateHolder.updateState(
                        TunnelState.Connecting(
                            section = section,
                            stageLabelEn = stageEn,
                            stageLabelFa = stageFa,
                            targetLabel = "127.0.0.1:${aetherSettings.localSocksPort}",
                        ),
                    )
                }
                val onDiscovered: (String, Int) -> Unit = { ip, port ->
                    discoveredIp = ip
                    discoveredPort = port
                    if (aetherSettings.scanForEndpoint || aetherSettings.endpointIp.isBlank()) {
                        scope.launch {
                            preferencesRepo.updateAetherSettings { cur ->
                                cur.copy(endpointIp = ip, endpointPort = port, scanForEndpoint = false)
                            }
                            logs.append("AETHER", LogLevel.INFO, "Saved discovered endpoint $ip:$port for fast future connects")
                        }
                    }
                }
                val err = when (section) {
                    ActiveSectionEngine.AETHER -> processManager.startAetherDaemon(
                        appSettings = appSettings,
                        aether = aetherSettings,
                        onStageUpdate = onStage,
                        onEndpointDiscovered = onDiscovered,
                    )
                    ActiveSectionEngine.GAMING -> processManager.startGamingDaemon(
                        gaming = gamingSettings,
                        appSettings = appSettings,
                        aetherSettings = aetherSettings,
                        activeProfile = profile,
                        onStageUpdate = onStage,
                        onEndpointDiscovered = onDiscovered,
                    )
                    ActiveSectionEngine.DNS -> processManager.startDnsSectionDaemon(
                        dnsSettings = dnsSettings,
                        appSettings = appSettings,
                        aetherSettings = aetherSettings,
                        onStageUpdate = onStage,
                        onEndpointDiscovered = onDiscovered,
                    )
                    ActiveSectionEngine.SERVER -> {
                        if (profile == null) {
                            ConnectionError.ProcessExited(-1, "Select a server profile first.")
                        } else {
                            processManager.startCustomServerDaemon(profile, appSettings, aetherSettings.localSocksPort)
                        }
                    }
                    ActiveSectionEngine.MHR -> processManager.startMhrDaemon(appSettings, aetherSettings, mhrSettings)
                }
                if (err != null) {
                    stateHolder.updateState(TunnelState.Error(section, err))
                } else {
                    val endpoint = when (section) {
                        ActiveSectionEngine.AETHER -> "${discoveredIp ?: aetherSettings.endpointIp}:${discoveredPort ?: aetherSettings.endpointPort}"
                        ActiveSectionEngine.GAMING -> when (gamingSettings.mode) {
                            ir.pnifox.core.model.GamingMode.DNS_ANTI_SANCTION -> "Game DNS • ${gamingSettings.selectedDnsIp}"
                            ir.pnifox.core.model.GamingMode.LOCAL_DPI_DESYNC -> "Local DPI Desync (0-Server)"
                            ir.pnifox.core.model.GamingMode.AETHER_UDP_QUIC -> "Game WARP • ${discoveredIp ?: aetherSettings.endpointIp}"
                            ir.pnifox.core.model.GamingMode.CUSTOM_SERVER -> "Game Server • ${profile?.name ?: "-"}"
                        }
                        ActiveSectionEngine.SERVER -> "${profile!!.address}:${profile.port}"
                        ActiveSectionEngine.DNS -> when (dnsSettings.engineType) {
                            DnsEngineType.AUTO_TUNNEL -> "Auto DNS • ${dnsSettings.resolverAddress}"
                            DnsEngineType.DNS_CHANGER -> "DNS Changer • ${dnsSettings.resolverAddress}"
                            DnsEngineType.CUSTOM_DNSTT -> "${dnsSettings.nameserver} (${dnsSettings.innerProtocol.label})"
                        }
                        ActiveSectionEngine.MHR -> "${mhrSettings.mode.labelEn} • ${mhrSettings.googleIp}"
                    }
                    stateHolder.updateState(
                        TunnelState.Connected(
                            section = section,
                            engine = engine,
                            localSocksPort = aetherSettings.localSocksPort,
                            activeEndpoint = endpoint,
                            resolvedIp = endpoint,
                            pingMs = profile?.lastPingMs,
                            connectedAtEpochMs = System.currentTimeMillis(),
                        ),
                    )
                    delay(900L)
                    refreshActiveRealDelay()
                }
            }
        }
    }

    fun disconnect() {
        logs.append("Controller", LogLevel.INFO, "Disconnecting tunnel.")
        proxyConnectJob?.cancel()
        processManager.stopAll()
        val stopIntent = Intent(context, PniVpnService::class.java).apply {
            action = PniVpnService.ACTION_STOP
        }
        runCatching { context.startService(stopIntent) }
        stateHolder.updateState(TunnelState.Disconnected)
    }
}
