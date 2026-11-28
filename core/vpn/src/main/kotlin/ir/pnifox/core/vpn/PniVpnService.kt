package ir.pnifox.core.vpn

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ServiceInfo
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.net.VpnService
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.ParcelFileDescriptor
import androidx.core.app.NotificationCompat
import dagger.hilt.android.AndroidEntryPoint
import ir.pnifox.core.database.PnifoxDataRepository
import ir.pnifox.core.database.PreferencesRepository
import ir.pnifox.core.model.ActiveSectionEngine
import ir.pnifox.core.model.AppLanguage
import ir.pnifox.core.model.ConnectionError
import ir.pnifox.core.model.CoreEngine
import ir.pnifox.core.model.DnsEngineType
import ir.pnifox.core.model.DnsInnerProtocol
import ir.pnifox.core.model.DnsTunnelMode
import ir.pnifox.core.model.DnsTunnelSettings
import ir.pnifox.core.model.GamingMode
import ir.pnifox.core.model.PnifoxRegionsCatalog
import ir.pnifox.core.model.TrafficFormatters
import ir.pnifox.core.model.TunnelState
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject

/**
 * Real Android VpnService host for `pnifox`.
 * Starts the selected native daemon (`AETHER`, `Xray`/`Sing-box`, or `DNS`/`DNSTT`), awaits local
 * SOCKS5 `127.0.0.1:10820` readiness, binds TUN fd via `libhev-socks5-tunnel.so`, measures live
 * speed & real end-to-end ping, and keeps the persistent notification updated.
 */
@AndroidEntryPoint
class PniVpnService : VpnService() {

    @Inject lateinit var binaryRegistry: NativeBinaryRegistry
    @Inject lateinit var processManager: NativeProcessManager
    @Inject lateinit var realDelayProber: RealDelayProber
    @Inject lateinit var preferencesRepo: PreferencesRepository
    @Inject lateinit var dataRepo: PnifoxDataRepository
    @Inject lateinit var vpnStateHolder: VpnStateHolder
    @Inject lateinit var logs: LogRingBuffer

    private val mainHandler = Handler(Looper.getMainLooper())
    private val serviceExceptionHandler = CoroutineExceptionHandler { _, throwable ->
        val msg = throwable.message ?: throwable::class.java.simpleName
        if (::logs.isInitialized) {
            logs.append("VpnService", LogLevel.ERROR, "Unhandled VPN coroutine error: $msg")
        }
        if (::vpnStateHolder.isInitialized) {
            val section = vpnStateHolder.tunnelState.value.let {
                when (it) {
                    is TunnelState.Connecting -> it.section
                    is TunnelState.Connected -> it.section
                    is TunnelState.Error -> it.section
                    TunnelState.Disconnected -> ActiveSectionEngine.AETHER
                }
            }
            vpnStateHolder.updateState(TunnelState.Error(section, ConnectionError.ProcessExited(-1, msg)))
        }
        teardownAndStop(keepErrorState = true)
    }
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO + serviceExceptionHandler)
    private var tunInterface: ParcelFileDescriptor? = null
    private var tProxyService: TProxyService? = null
    private var connectJob: Job? = null
    private var statsJob: Job? = null
    private var reconnectJob: Job? = null
    @Volatile private var isScreenInteractive = true
    @Volatile private var userRequestedStop = false
    @Volatile private var autoReconnectEnabled = true
    @Volatile private var activeSectionState = ActiveSectionEngine.AETHER
    @Volatile private var hadPhysicalNetworkLoss = false
    @Volatile private var hasPhysicalInternet = true
    private val physicalNetworks = ConcurrentHashMap.newKeySet<Network>()

    @Volatile private var notifEngineLabel = "pnifox"
    @Volatile private var notifEndpoint = ""
    @Volatile private var notifRegionBadge = ""
    @Volatile private var notifConnectedAtMs = 0L
    @Volatile private var notifIsFa = false
    @Volatile private var notifLatestPingMs: Int? = null

    private val underlyingNetworkCallback = object : ConnectivityManager.NetworkCallback() {
        override fun onAvailable(network: Network) {
            val isNew = physicalNetworks.add(network)
            val prevOnline = hasPhysicalInternet
            hasPhysicalInternet = true
            if (!::vpnStateHolder.isInitialized || userRequestedStop || !autoReconnectEnabled) return
            val state = vpnStateHolder.tunnelState.value
            val isActiveOrWaiting = state is TunnelState.Connected || state is TunnelState.Error
            if ((hadPhysicalNetworkLoss || !prevOnline || (isNew && physicalNetworks.size == 1)) && isActiveOrWaiting) {
                hadPhysicalNetworkLoss = false
                if (::logs.isInitialized) {
                    logs.append("VpnService", LogLevel.INFO, "Underlying internet restored/switched — scheduling automatic tunnel refresh...")
                }
                scheduleAutoReconnect("Underlying network changed/restored", delayMs = 1400L)
            }
        }

        override fun onLost(network: Network) {
            physicalNetworks.remove(network)
            if (physicalNetworks.isEmpty()) {
                hasPhysicalInternet = false
                if (!userRequestedStop && autoReconnectEnabled && ::vpnStateHolder.isInitialized) {
                    hadPhysicalNetworkLoss = true
                    if (::logs.isInitialized) {
                        logs.append("VpnService", LogLevel.WARN, "Internet lost — waiting for network recovery to reconnect automatically.")
                    }
                    updateForegroundNotification(
                        title = getString(R.string.pnifox_notif_title),
                        contentText = if (notifIsFa) "اینترنت قطع شد — در انتظار اتصال مجدد..." else "Internet lost — waiting to auto-reconnect...",
                        asStartForeground = false,
                    )
                }
            }
        }
    }

    private val screenStateReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            when (intent?.action) {
                Intent.ACTION_SCREEN_OFF -> isScreenInteractive = false
                Intent.ACTION_SCREEN_ON -> isScreenInteractive = true
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_SCREEN_OFF)
            addAction(Intent.ACTION_SCREEN_ON)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(screenStateReceiver, filter, RECEIVER_NOT_EXPORTED)
        } else {
            @Suppress("DEPRECATION")
            registerReceiver(screenStateReceiver, filter)
        }
        runCatching {
            val cm = getSystemService(ConnectivityManager::class.java)
            val req = NetworkRequest.Builder()
                .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                .addCapability(NetworkCapabilities.NET_CAPABILITY_NOT_VPN)
                .build()
            cm?.registerNetworkCallback(req, underlyingNetworkCallback)
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            userRequestedStop = true
            reconnectJob?.cancel()
            reconnectJob = null
            logs.append("VpnService", LogLevel.INFO, "Disconnect requested from notification.")
            teardownAndStop()
            return START_NOT_STICKY
        }

        val requestedSection = intent?.getStringExtra(EXTRA_SECTION)
            ?.let { runCatching { ActiveSectionEngine.valueOf(it) }.getOrNull() }
        userRequestedStop = false
        reconnectJob?.cancel()
        reconnectJob = null

        updateForegroundNotification(
            title = getString(R.string.pnifox_notif_title),
            contentText = "Starting native tunnel engine...",
            asStartForeground = true,
        )
        launchTunnelAttempt(requestedSection = requestedSection, isAutoRetry = false)
        return START_STICKY
    }

    private fun launchTunnelAttempt(
        requestedSection: ActiveSectionEngine?,
        isAutoRetry: Boolean,
    ) {
        connectJob?.cancel()
        connectJob = serviceScope.launch {
            if (!isAutoRetry) vpnStateHolder.resetSessionCounters()
            val appSettings = preferencesRepo.appSettingsFlow.first()
            autoReconnectEnabled = appSettings.autoReconnectOnNetworkChange
            val aetherSettings = preferencesRepo.aetherSettingsFlow.first()
            val dnsSettings = preferencesRepo.dnsSettingsFlow.first()
            val mhrSettings = preferencesRepo.mhrSettingsFlow.first()
            val gamingSettings = preferencesRepo.gamingSettingsFlow.first()
            val section = requestedSection ?: appSettings.activeSection
            activeSectionState = section
            val profile = appSettings.activeProfileId?.let { dataRepo.getProfile(it) }
            notifIsFa = appSettings.language == AppLanguage.PERSIAN

            val missing = binaryRegistry.verifyRequiredBinaries(section, appSettings, aetherSettings, profile, dnsSettings, gamingSettings)
            if (missing != null) {
                logs.append("VpnService", LogLevel.ERROR, "Missing binary: ${missing.expectedAbiPath}")
                vpnStateHolder.updateState(TunnelState.Error(section, missing))
                teardownAndStop(keepErrorState = true)
                return@launch
            }

            if ((section == ActiveSectionEngine.SERVER || (section == ActiveSectionEngine.GAMING && gamingSettings.mode == GamingMode.CUSTOM_SERVER)) &&
                profile == null
            ) {
                vpnStateHolder.updateState(
                    TunnelState.Error(
                        section,
                        ConnectionError.ProcessExited(-1, "No server selected. Import and pick a server profile first."),
                    ),
                )
                teardownAndStop(keepErrorState = true)
                return@launch
            }

            // Tear down previous TUN/proxy cleanly on auto-refresh before restarting daemon
            if (isAutoRetry) {
                statsJob?.cancel()
                runCatching { tProxyService?.stop() }
                tProxyService = null
                runCatching { processManager.stopAll() }
                runCatching { tunInterface?.close() }
                tunInterface = null
                delay(300L)
            }

            // 1. Start underlying SOCKS5 core daemon first (127.0.0.1:10820) and wait until ready
            var discoveredEndpointIp: String? = null
            var discoveredEndpointPort: Int? = null
            var discoveredExitIp = ""
            var discoveredExitLoc = ""
            val onStageUpdate: (String, String) -> Unit = { stageEn, stageFa ->
                vpnStateHolder.updateState(
                    TunnelState.Connecting(
                        section = section,
                        stageLabelEn = stageEn,
                        stageLabelFa = stageFa,
                        targetLabel = "127.0.0.1:${aetherSettings.localSocksPort}",
                    ),
                )
                updateForegroundNotification(
                    title = getString(R.string.pnifox_notif_title),
                    contentText = if (notifIsFa) stageFa else stageEn,
                    asStartForeground = false,
                )
            }
            val onEndpointDiscovered: (String, Int) -> Unit = { ip, port ->
                discoveredEndpointIp = ip
                discoveredEndpointPort = port
                serviceScope.launch {
                    preferencesRepo.updateAetherSettings { cur ->
                        cur.copy(
                            endpointIp = ip,
                            endpointPort = port,
                            lastScannedEndpointIp = ip,
                            lastScannedEndpointPort = port,
                            scanForEndpoint = false,
                        )
                    }
                }
                logs.append("AETHER", LogLevel.INFO, "Cached fastest endpoint $ip:$port for fast next connect")
            }

            processManager.activeSocketProtector = { sock -> protect(sock) }
            processManager.activeDatagramProtector = { sock -> protect(sock) }
            val effectiveAether = if (isAutoRetry && section == ActiveSectionEngine.AETHER && aetherSettings.endpointIp.isNotBlank()) {
                aetherSettings.copy(scanForEndpoint = true, endpointIp = "")
            } else {
                aetherSettings
            }
            val coreErr = when (section) {
                ActiveSectionEngine.AETHER -> processManager.startAetherDaemon(
                    appSettings = appSettings,
                    aether = effectiveAether,
                    onStageUpdate = onStageUpdate,
                    onEndpointDiscovered = onEndpointDiscovered,
                    onExitInfoDiscovered = { exIp, exLoc ->
                        discoveredExitIp = exIp
                        discoveredExitLoc = exLoc
                        vpnStateHolder.updateConnectedExitInfo(exIp, exLoc)
                    },
                    forceFreshScan = isAutoRetry,
                )
                ActiveSectionEngine.GAMING -> processManager.startGamingDaemon(
                    gaming = gamingSettings,
                    appSettings = appSettings,
                    aetherSettings = effectiveAether,
                    activeProfile = profile,
                    onStageUpdate = onStageUpdate,
                    onEndpointDiscovered = onEndpointDiscovered,
                )
                ActiveSectionEngine.DNS -> processManager.startDnsSectionDaemon(
                    dnsSettings = dnsSettings,
                    appSettings = appSettings,
                    aetherSettings = effectiveAether,
                    onStageUpdate = onStageUpdate,
                    onEndpointDiscovered = onEndpointDiscovered,
                )
                ActiveSectionEngine.SERVER -> processManager.startCustomServerDaemon(profile!!, appSettings, aetherSettings.localSocksPort)
                ActiveSectionEngine.MHR -> processManager.startMhrDaemon(appSettings, aetherSettings, mhrSettings)
            }
            if (coreErr != null) {
                vpnStateHolder.updateState(TunnelState.Error(section, coreErr))
                if (!userRequestedStop && autoReconnectEnabled && coreErr !is ConnectionError.MissingBinary && coreErr !is ConnectionError.PermissionDenied) {
                    if (hasPhysicalInternet) {
                        scheduleAutoReconnect("Core failed: ${coreErr.titleEn}", delayMs = 6_000L)
                    } else {
                        logs.append("VpnService", LogLevel.WARN, "No active internet during connect — waiting for network recovery to retry automatically.")
                    }
                } else {
                    teardownAndStop(keepErrorState = true)
                }
                return@launch
            }

            // 2. Establish Android TUN and route through libhev-socks5-tunnel.so -> 127.0.0.1:10820
            val isDnsChangerOnly = (section == ActiveSectionEngine.DNS && dnsSettings.engineType == DnsEngineType.DNS_CHANGER) ||
                (section == ActiveSectionEngine.GAMING && gamingSettings.mode == GamingMode.DNS_ANTI_SANCTION)
            val useMapDns = !isDnsChangerOnly && (
                appSettings.fakeDnsEnabled ||
                    section == ActiveSectionEngine.DNS ||
                    section == ActiveSectionEngine.MHR
                )
            val tunDns = when {
                isDnsChangerOnly -> "10.233.233.2"
                useMapDns -> "10.233.233.2"
                else -> appSettings.localDns.ifBlank { "1.1.1.1" }
            }
            val pfd = buildTunInterface(
                mtu = appSettings.mtu,
                dnsServer = tunDns,
                ipv6Enabled = appSettings.ipv6Enabled,
                perAppWhitelist = appSettings.perAppWhitelistMode,
                packages = appSettings.perAppPackages,
                dnsOnlyMode = isDnsChangerOnly,
            )
            if (pfd == null) {
                vpnStateHolder.updateState(TunnelState.Error(section, ConnectionError.PermissionDenied()))
                teardownAndStop(keepErrorState = true)
                return@launch
            }
            tunInterface = pfd

            val enableUdp = !(
                section == ActiveSectionEngine.DNS &&
                    dnsSettings.engineType == DnsEngineType.CUSTOM_DNSTT &&
                    dnsSettings.innerProtocol != DnsInnerProtocol.VLESS
                )
            val proxy = TProxyService(this@PniVpnService)
            tProxyService = proxy
            val started = proxy.start(
                vpnInterface = pfd,
                mtu = appSettings.mtu,
                socksPort = aetherSettings.localSocksPort,
                ipv6Enabled = appSettings.ipv6Enabled,
                useMapDns = useMapDns,
                mapDnsAddress = "10.233.233.2",
                enableUdp = enableUdp,
            )
            started.onFailure { err ->
                logs.append("TProxy", LogLevel.ERROR, err.message ?: "TProxyStartService failed")
                vpnStateHolder.updateState(
                    TunnelState.Error(
                        section,
                        ConnectionError.ProcessExited(-1, err.message ?: "TUN startup failed"),
                    ),
                )
                teardownAndStop(keepErrorState = true)
                return@launch
            }
            logs.append("TProxy", LogLevel.INFO, "hev-socks5-tunnel started (fd=${pfd.fd}, dns=$tunDns, mapdns=$useMapDns, udp=$enableUdp)")

            val engine = binaryRegistry.resolveEngineForSection(section, appSettings, aetherSettings, profile, dnsSettings, gamingSettings)
            val resolvedAetherIp = discoveredEndpointIp ?: aetherSettings.endpointIp
            val resolvedAetherPort = discoveredEndpointPort ?: aetherSettings.endpointPort
            val endpointLabel = when (section) {
                ActiveSectionEngine.AETHER -> if (discoveredEndpointIp != null || !aetherSettings.scanForEndpoint) {
                    "$resolvedAetherIp:$resolvedAetherPort"
                } else {
                    "Auto-Scanned WARP"
                }
                ActiveSectionEngine.GAMING -> when (gamingSettings.mode) {
                    GamingMode.DNS_ANTI_SANCTION -> "Game DNS • ${gamingSettings.selectedDnsIp}"
                    GamingMode.LOCAL_DPI_DESYNC -> "Local DPI Desync (0-Server)"
                    GamingMode.AETHER_UDP_QUIC -> "Game WARP • ${discoveredEndpointIp ?: resolvedAetherIp}"
                    GamingMode.CUSTOM_SERVER -> "Game Server • ${profile?.name ?: "-"}"
                }
                ActiveSectionEngine.SERVER -> "${profile!!.address}:${profile.port}"
                ActiveSectionEngine.DNS -> when (dnsSettings.engineType) {
                    DnsEngineType.AUTO_TUNNEL -> "Auto DNS • ${dnsSettings.resolverAddress}"
                    DnsEngineType.DNS_CHANGER -> "DNS Changer • ${dnsSettings.resolverAddress}"
                    DnsEngineType.CUSTOM_DNSTT -> "${dnsSettings.nameserver} (${dnsSettings.innerProtocol.label})"
                }
                ActiveSectionEngine.MHR -> "${mhrSettings.mode.labelEn} • ${mhrSettings.googleIp}"
            }

            notifEngineLabel = if (notifIsFa) engine.displayFa else engine.displayEn
            notifEndpoint = endpointLabel
            notifConnectedAtMs = System.currentTimeMillis()
            notifLatestPingMs = profile?.lastPingMs
            val region = PnifoxRegionsCatalog.findByCode(aetherSettings.psiphonExitCountry)
            notifRegionBadge = if (section == ActiveSectionEngine.AETHER && !region.code.equals("AUTO", ignoreCase = true)) {
                " • ${region.code} ${region.flag}"
            } else {
                ""
            }

            refreshSpeedNotification(rxBps = 0L, txBps = 0L)
            val activeDnsLabel = when {
                isDnsChangerOnly && section == ActiveSectionEngine.GAMING -> gamingSettings.selectedDnsIp
                isDnsChangerOnly -> dnsSettings.resolverAddress
                useMapDns -> "MapDNS (10.233.233.2) → ${appSettings.localDns.ifBlank { "1.1.1.1" }}"
                else -> tunDns
            }
            val netChanges = buildString {
                append(if (isDnsChangerOnly) "DNS-Only TUN" else "VPN TUN (10.233.233.1)")
                append(" • DNS: $activeDnsLabel")
                if (appSettings.bypassIranEnabled) append(" • Bypass IR")
                if (appSettings.blockAdsEnabled) append(" • AdBlock")
            }
            vpnStateHolder.updateState(
                TunnelState.Connected(
                    section = section,
                    engine = engine,
                    localSocksPort = aetherSettings.localSocksPort,
                    activeEndpoint = endpointLabel,
                    resolvedIp = when (section) {
                        ActiveSectionEngine.SERVER -> profile!!.address
                        ActiveSectionEngine.MHR -> mhrSettings.googleIp
                        else -> resolvedAetherIp
                    },
                    pingMs = profile?.lastPingMs,
                    connectedAtEpochMs = notifConnectedAtMs,
                    exitIp = discoveredExitIp,
                    exitLocation = discoveredExitLoc,
                    activeDnsSummary = activeDnsLabel,
                    networkChangeSummary = netChanges,
                ),
            )
            startStatsLoop(proxy, pauseOnScreenOff = appSettings.pauseTelemetryOnScreenOff)

            // Probe real end-to-end delay shortly after tunnel becomes active
            serviceScope.launch {
                delay(1100L)
                val probeDnsSettings = when {
                    section == ActiveSectionEngine.DNS -> dnsSettings
                    section == ActiveSectionEngine.GAMING && gamingSettings.mode == GamingMode.DNS_ANTI_SANCTION ->
                        DnsTunnelSettings().copy(mode = DnsTunnelMode.UDP, resolverAddress = gamingSettings.selectedDnsIp)
                    else -> null
                }
                val measured = realDelayProber.measureActiveTunnelDelayMs(
                    engine = engine,
                    localSocksPort = aetherSettings.localSocksPort,
                    dnsSettings = probeDnsSettings,
                )
                if (measured != null) {
                    notifLatestPingMs = measured
                    vpnStateHolder.updateConnectedPing(measured)
                    logs.append("RealDelay", LogLevel.INFO, "Measured active tunnel delay: ${measured}ms")
                    if (section == ActiveSectionEngine.SERVER && profile != null) {
                        runCatching { dataRepo.saveProfile(profile.copy(lastPingMs = measured)) }
                    }
                    refreshSpeedNotification(
                        rxBps = vpnStateHolder.rxSpeedBps.value,
                        txBps = vpnStateHolder.txSpeedBps.value,
                    )
                } else {
                    logs.append(
                        "RealDelay",
                        LogLevel.WARN,
                        "Active tunnel real delay probe timed out (no HTTP response from upstream). Verify server/nameserver/credentials and ISP filtering.",
                    )
                }
                if (discoveredExitIp.isBlank() && !isDnsChangerOnly) {
                    RealDelaySocketProber.fetchExitIpViaSocks5("127.0.0.1", aetherSettings.localSocksPort)?.let { (ip, loc) ->
                        discoveredExitIp = ip
                        discoveredExitLoc = loc
                        vpnStateHolder.updateConnectedExitInfo(ip, loc)
                        logs.append("ExitIP", LogLevel.INFO, "Connected Exit IP: $ip ($loc)")
                    }
                }
            }
        }
    }

    private fun scheduleAutoReconnect(reason: String, delayMs: Long = 2000L) {
        if (userRequestedStop || !autoReconnectEnabled) return
        reconnectJob?.cancel()
        reconnectJob = serviceScope.launch {
            delay(delayMs)
            if (userRequestedStop || !autoReconnectEnabled) return@launch
            if (!hasPhysicalInternet) {
                logs.append("Watchdog", LogLevel.INFO, "Auto-reconnect paused until internet returns ($reason).")
                return@launch
            }
            logs.append("Watchdog", LogLevel.INFO, "Refreshing tunnel ($reason)...")
            launchTunnelAttempt(requestedSection = activeSectionState, isAutoRetry = true)
        }
    }

    private fun buildTunInterface(
        mtu: Int,
        dnsServer: String,
        ipv6Enabled: Boolean,
        perAppWhitelist: Boolean,
        packages: Set<String>,
        dnsOnlyMode: Boolean = false,
    ): ParcelFileDescriptor? = runCatching {
        val builder = Builder()
            .setSession("pnifox")
            .setMtu(mtu.coerceIn(1200, 1500))
            .addAddress("10.233.233.1", 30)
            .addDnsServer(dnsServer)

        if (dnsOnlyMode) {
            // Capture only DNS requests directed to virtual TUN DNS server
            builder.addRoute("10.233.233.2", 32)
        } else {
            builder.addRoute("0.0.0.0", 0)
            if (ipv6Enabled) {
                builder.addAddress("fd00:233::1", 126)
                builder.addRoute("::", 0)
            }
        }

        // CRITICAL: Always disallow our own app so native engines never loop back into TUN.
        if (packages.isNotEmpty() && perAppWhitelist) {
            packages.filter { it != packageName }.forEach { pkg ->
                runCatching { builder.addAllowedApplication(pkg) }
            }
        } else {
            runCatching { builder.addDisallowedApplication(packageName) }
            packages.forEach { pkg ->
                runCatching { builder.addDisallowedApplication(pkg) }
            }
        }

        builder.establish()
    }.getOrNull()

    private fun startStatsLoop(proxy: TProxyService, pauseOnScreenOff: Boolean) {
        statsJob?.cancel()
        var lastTx = 0L
        var lastRx = 0L
        val myUid = android.os.Process.myUid()
        var lastUidRx = android.net.TrafficStats.getUidRxBytes(myUid).coerceAtLeast(0L)
        var lastUidTx = android.net.TrafficStats.getUidTxBytes(myUid).coerceAtLeast(0L)
        var ticks = 0
        var zeroRxWarned = false
        statsJob = serviceScope.launch {
            while (isActive && !userRequestedStop) {
                delay(1500L)
                if (!processManager.isPrimaryDaemonAlive() && !userRequestedStop && autoReconnectEnabled) {
                    logs.append("Watchdog", LogLevel.WARN, "Native core process exited unexpectedly — scheduling auto-reconnect...")
                    scheduleAutoReconnect("Native core exited", delayMs = 2500L)
                    break
                }
                if (pauseOnScreenOff && !isScreenInteractive) continue
                val raw = proxy.getStats()
                if (raw != null && raw.size >= 4) {
                    val txTotal = raw[1]
                    val rxTotal = raw[3]
                    ticks++
                    if (!zeroRxWarned && ticks >= 6 && txTotal > 6144L && rxTotal == 0L) {
                        zeroRxWarned = true
                        logs.append(
                            "TProxy",
                            LogLevel.WARN,
                            "Sent ${txTotal}B into TUN with 0B RX — checking upstream health...",
                        )
                        if (!userRequestedStop && autoReconnectEnabled && hasPhysicalInternet) {
                            scheduleAutoReconnect("Extended 0-RX stall while internet is active", delayMs = 1500L)
                            break
                        }
                    }
                    val txDelta = (txTotal - lastTx).coerceAtLeast(0L)
                    val rxDelta = (rxTotal - lastRx).coerceAtLeast(0L)
                    lastTx = txTotal
                    lastRx = rxTotal
                    val curUidRx = android.net.TrafficStats.getUidRxBytes(myUid).coerceAtLeast(0L)
                    val curUidTx = android.net.TrafficStats.getUidTxBytes(myUid).coerceAtLeast(0L)
                    val uidRxDelta = (curUidRx - lastUidRx).coerceAtLeast(0L)
                    val uidTxDelta = (curUidTx - lastUidTx).coerceAtLeast(0L)
                    lastUidRx = curUidRx
                    lastUidTx = curUidTx
                    val effRx = if (rxDelta > 0L) rxDelta else uidRxDelta
                    val effTx = if (txDelta > 0L) txDelta else uidTxDelta
                    val rxBps = (effRx * 1000L) / 1500L
                    val txBps = (effTx * 1000L) / 1500L
                    vpnStateHolder.updateSpeed(rxBps = rxBps, txBps = txBps, rxDelta = effRx, txDelta = effTx)
                    runCatching { dataRepo.recordTrafficDelta(rxDelta = effRx, txDelta = effTx) }
                    refreshSpeedNotification(rxBps = rxBps, txBps = txBps)
                } else {
                    val curUidRx = android.net.TrafficStats.getUidRxBytes(myUid).coerceAtLeast(0L)
                    val curUidTx = android.net.TrafficStats.getUidTxBytes(myUid).coerceAtLeast(0L)
                    val uidRxDelta = (curUidRx - lastUidRx).coerceAtLeast(0L)
                    val uidTxDelta = (curUidTx - lastUidTx).coerceAtLeast(0L)
                    lastUidRx = curUidRx
                    lastUidTx = curUidTx
                    val rxBps = (uidRxDelta * 1000L) / 1500L
                    val txBps = (uidTxDelta * 1000L) / 1500L
                    vpnStateHolder.updateSpeed(rxBps = rxBps, txBps = txBps, rxDelta = uidRxDelta, txDelta = uidTxDelta)
                    refreshSpeedNotification(rxBps = rxBps, txBps = txBps)
                }
            }
        }
    }

    private fun refreshSpeedNotification(rxBps: Long, txBps: Long) {
        val dlStr = TrafficFormatters.formatSpeedBps(rxBps)
        val ulStr = TrafficFormatters.formatSpeedBps(txBps)
        val totalDown = TrafficFormatters.formatBytes(vpnStateHolder.sessionRxBytes.value)
        val totalUp = TrafficFormatters.formatBytes(vpnStateHolder.sessionTxBytes.value)
        val duration = TrafficFormatters.formatElapsed(System.currentTimeMillis() - notifConnectedAtMs)
        val pingSuffix = notifLatestPingMs?.let { " • ${it}ms" }.orEmpty()

        val title = if (notifIsFa) {
            "pnifox • متصل ($notifEngineLabel)$notifRegionBadge"
        } else {
            "pnifox • Connected ($notifEngineLabel)$notifRegionBadge"
        }
        val compactText = "↓ $dlStr  ↑ $ulStr$pingSuffix"
        val bigText = "$compactText\n↓ $totalDown  ↑ $totalUp • $duration • $notifEndpoint"
        updateForegroundNotification(
            title = title,
            contentText = compactText,
            bigText = bigText,
            asStartForeground = false,
        )
    }

    private fun updateForegroundNotification(
        title: String,
        contentText: String,
        bigText: String? = null,
        asStartForeground: Boolean,
    ) {
        val runBlock = {
            runCatching {
                val stopIntent = Intent(this, PniVpnService::class.java).apply { action = ACTION_STOP }
                val stopPending = PendingIntent.getService(
                    this,
                    101,
                    stopIntent,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
                )
                val launchIntent = packageManager.getLaunchIntentForPackage(packageName)?.apply {
                    flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
                }
                val contentPending = launchIntent?.let {
                    PendingIntent.getActivity(
                        this,
                        100,
                        it,
                        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
                    )
                }

                val builder = NotificationCompat.Builder(this, CHANNEL_ID)
                    .setContentTitle(title)
                    .setContentText(contentText)
                    .setSmallIcon(R.drawable.ic_pnifox_notif)
                    .setOngoing(true)
                    .setOnlyAlertOnce(true)
                    .setSilent(true)
                    .setShowWhen(true)
                    .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
                    .addAction(0, getString(R.string.pnifox_action_disconnect), stopPending)

                if (contentPending != null) {
                    builder.setContentIntent(contentPending)
                }
                if (!bigText.isNullOrBlank()) {
                    builder.setStyle(NotificationCompat.BigTextStyle().bigText(bigText))
                }

                val notification: Notification = builder.build()
                if (asStartForeground) {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                        try {
                            startForeground(NOTIF_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
                        } catch (_: Throwable) {
                            startForeground(NOTIF_ID, notification)
                        }
                    } else {
                        startForeground(NOTIF_ID, notification)
                    }
                } else {
                    val nm = getSystemService(NotificationManager::class.java)
                    nm?.notify(NOTIF_ID, notification)
                }
            }.onFailure { err ->
                if (::logs.isInitialized) {
                    logs.append("VpnService", LogLevel.ERROR, "Foreground notification error: ${err.message}")
                }
            }
        }
        if (Looper.myLooper() == Looper.getMainLooper()) {
            runBlock()
        } else {
            mainHandler.post { runBlock() }
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val nm = getSystemService(NotificationManager::class.java) ?: return
            val channel = NotificationChannel(
                CHANNEL_ID,
                getString(R.string.pnifox_notif_channel),
                NotificationManager.IMPORTANCE_LOW,
            ).apply {
                setShowBadge(false)
                enableVibration(false)
                setSound(null, null)
            }
            nm.createNotificationChannel(channel)
        }
    }

    private fun teardownAndStop(keepErrorState: Boolean = false) {
        connectJob?.cancel()
        statsJob?.cancel()
        runCatching { tProxyService?.stop() }
        tProxyService = null
        if (::processManager.isInitialized) {
            runCatching { processManager.stopAll() }
        }
        runCatching { tunInterface?.close() }
        tunInterface = null
        if (!keepErrorState && ::vpnStateHolder.isInitialized) {
            vpnStateHolder.updateState(TunnelState.Disconnected)
        }
        val stopOnMain = {
            runCatching { stopForeground(STOP_FOREGROUND_REMOVE) }
            runCatching { stopSelf() }
        }
        if (Looper.myLooper() == Looper.getMainLooper()) {
            stopOnMain()
        } else {
            mainHandler.post { stopOnMain() }
        }
    }

    override fun onDestroy() {
        runCatching { unregisterReceiver(screenStateReceiver) }
        runCatching {
            val cm = getSystemService(ConnectivityManager::class.java)
            cm?.unregisterNetworkCallback(underlyingNetworkCallback)
        }
        reconnectJob?.cancel()
        statsJob?.cancel()
        tProxyService?.stop()
        processManager.stopAll()
        runCatching { tunInterface?.close() }
        serviceScope.cancel()
        super.onDestroy()
    }

    companion object {
        const val ACTION_START = "ir.pnifox.core.vpn.START"
        const val ACTION_STOP = "ir.pnifox.core.vpn.STOP"
        const val EXTRA_SECTION = "section"
        private const val CHANNEL_ID = "pnifox_vpn_channel"
        private const val NOTIF_ID = 2001
    }
}
