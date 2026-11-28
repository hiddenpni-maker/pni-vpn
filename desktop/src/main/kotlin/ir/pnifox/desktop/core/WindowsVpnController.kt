package ir.pnifox.desktop.core

import ir.pnifox.core.model.ActiveSectionEngine
import ir.pnifox.core.model.AetherIdentityTarget
import ir.pnifox.core.model.AetherProtocol
import ir.pnifox.core.model.AetherSettings
import ir.pnifox.core.model.AppSettings
import ir.pnifox.core.model.ConnectionError
import ir.pnifox.core.model.CoreEngine
import ir.pnifox.core.model.DnsEngineType
import ir.pnifox.core.model.DnsInnerProtocol
import ir.pnifox.core.model.DnsTunnelMode
import ir.pnifox.core.model.DnsTunnelSettings
import ir.pnifox.core.model.ExitNode
import ir.pnifox.core.model.GamingMode
import ir.pnifox.core.model.GamingSettings
import ir.pnifox.core.model.LogLevel
import ir.pnifox.core.model.MasqueTransport
import ir.pnifox.core.model.MhrDefaultSniPool
import ir.pnifox.core.model.MhrMode
import ir.pnifox.core.model.MhrSettings
import ir.pnifox.core.model.MhrSniTestResult
import ir.pnifox.core.model.OneTapPreset
import ir.pnifox.core.model.ProxyProfile
import ir.pnifox.core.model.ProxyProtocol
import ir.pnifox.core.model.PnifoxScenarioCatalog
import ir.pnifox.core.model.ScanStyle
import ir.pnifox.core.model.ScenarioGoalId
import ir.pnifox.core.model.TunnelState
import ir.pnifox.core.vpn.AetherArgBuilder
import ir.pnifox.core.vpn.ConfigBuilders
import ir.pnifox.core.vpn.DnsE2EResult
import ir.pnifox.core.vpn.DnsE2EScanner
import ir.pnifox.core.vpn.EchBootstrapResolver
import ir.pnifox.core.vpn.JvmCdnScanner
import ir.pnifox.core.vpn.JvmScanCandidateResult
import ir.pnifox.core.vpn.JvmScanProgress
import ir.pnifox.core.vpn.LocalDpiDesyncServer
import ir.pnifox.core.vpn.LogRingBuffer
import ir.pnifox.core.vpn.MhrConfigBuilder
import ir.pnifox.core.vpn.ConnectionStatusNarrator
import ir.pnifox.core.vpn.RealDelaySocketProber
import ir.pnifox.core.vpn.SniSpoofServer
import ir.pnifox.desktop.model.WindowsBinaryStatus
import ir.pnifox.desktop.model.WindowsTunnelMode
import ir.pnifox.desktop.storage.WindowsStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.InputStream
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.Inet4Address
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.NetworkInterface
import java.net.Socket
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import javax.net.ssl.SNIHostName
import javax.net.ssl.SSLSocket
import javax.net.ssl.SSLSocketFactory

class WindowsVpnController(
    private val store: WindowsStore,
    val logs: LogRingBuffer,
    val coreLocator: WindowsCoreLocator = WindowsCoreLocator(),
    val systemProxy: WindowsSystemProxy = WindowsSystemProxy(store.dataDir),
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val connectMutex = Mutex()
    private val argBuilder = AetherArgBuilder()
    private val spoofServer = SniSpoofServer()
    private val httpClient = OkHttpClient.Builder().connectTimeout(5, TimeUnit.SECONDS).build()
    private val json = Json { ignoreUnknownKeys = true }

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

    private val _isAdmin = MutableStateFlow(false)
    val isAdmin: StateFlow<Boolean> = _isAdmin.asStateFlow()

    private val _isMhrCaInstalled = MutableStateFlow(false)
    val isMhrCaInstalled: StateFlow<Boolean> = _isMhrCaInstalled.asStateFlow()

    private val _binaries = MutableStateFlow<List<WindowsBinaryStatus>>(emptyList())
    val binaries: StateFlow<List<WindowsBinaryStatus>> = _binaries.asStateFlow()

    @Volatile private var connectJob: Job? = null
    @Volatile private var trafficJob: Job? = null
    @Volatile private var watchdogJob: Job? = null
    @Volatile private var userRequestedDisconnect = false
    @Volatile private var coreProcess: Process? = null
    @Volatile private var secondaryProcess: Process? = null
    @Volatile private var gatewayProcess: Process? = null
    @Volatile private var localDesyncServer: LocalDpiDesyncServer? = null
    @Volatile private var activeHttpGatewayPort: Int? = null
    @Volatile private var activeSocksGatewayPort: Int? = null

    init {
        WindowsSecurityHelper.hardenDirectoryAcl(store.dataDir)
        WindowsSecurityHelper.cleanupTempConfigs(store.runtimeDir, store.mhrDir)
        if (systemProxy.restoreIfStaleOnStartup()) {
            logs.append("SystemProxy", LogLevel.INFO, "Restored Windows system proxy settings from previous session.")
        }
        scope.launch {
            _isAdmin.value = WindowsPrivilegeHelper.isAdministrator()
            _binaries.value = coreLocator.inspectAll()
            _isMhrCaInstalled.value = queryMhrCaInstalled()
        }
        Runtime.getRuntime().addShutdownHook(
            Thread {
                disconnectSync(updateState = false)
                store.flushState()
            },
        )
    }

    fun activeProxyPorts(): Pair<Int?, Int?> {
        val state = _tunnelState.value as? TunnelState.Connected ?: return null to null
        return activeHttpGatewayPort to (activeSocksGatewayPort ?: state.localSocksPort)
    }

    fun refreshEnvironment() {
        scope.launch {
            _isAdmin.value = WindowsPrivilegeHelper.isAdministrator()
            _binaries.value = coreLocator.inspectAll()
            _isMhrCaInstalled.value = queryMhrCaInstalled()
        }
    }

    suspend fun connectOrToggle(section: ActiveSectionEngine? = null) {
        when (_tunnelState.value) {
            is TunnelState.Connected, is TunnelState.Connecting -> disconnect()
            is TunnelState.Disconnected, is TunnelState.Error -> {
                val target = section ?: store.stateFlow.value.appSettings.activeSection
                startConnection(target)
            }
        }
    }

    suspend fun reconnectActive() {
        val current = _tunnelState.value
        val activeSection = when (current) {
            is TunnelState.Connected -> current.section
            is TunnelState.Connecting -> current.section
            else -> store.stateFlow.value.appSettings.activeSection
        }
        if (current is TunnelState.Connected || current is TunnelState.Connecting) {
            disconnect()
            delay(350L)
        }
        startConnection(activeSection)
    }

    fun startConnection(section: ActiveSectionEngine) {
        if (_tunnelState.value is TunnelState.Connecting) return
        userRequestedDisconnect = false
        watchdogJob?.cancel()
        connectJob?.cancel()
        connectJob = scope.launch {
            executeConnection(section)
        }
    }

    private suspend fun executeConnection(section: ActiveSectionEngine, fastScan: Boolean = false) {
        store.updateAppSettings { it.copy(activeSection = section) }
        val persisted = store.stateFlow.value
        val app = persisted.appSettings
        val aether = if (fastScan && (persisted.aetherSettings.scanForEndpoint || persisted.aetherSettings.endpointIp.isBlank())) {
            persisted.aetherSettings.copy(scanMode = ir.pnifox.core.model.AetherScanMode.TURBO)
        } else {
            persisted.aetherSettings
        }
        val dns = persisted.dnsSettings
        val mhr = persisted.mhrSettings
        val tunMode = persisted.windowsTunnelMode
        val profile = persisted.profiles.firstOrNull { it.id == app.activeProfileId }

        connectMutex.withLock {
            disconnectSync(updateState = false)
                _sessionRxBytes.value = 0L
                _sessionTxBytes.value = 0L
                _rxSpeedBps.value = 0L
                _txSpeedBps.value = 0L

                val isMhrProxyOnly = section == ActiveSectionEngine.MHR && mhr.proxyOnly
                if (!isMhrProxyOnly && tunMode == WindowsTunnelMode.TUN_WINTUN && !WindowsPrivilegeHelper.isAdministrator()) {
                    _tunnelState.value = TunnelState.Error(
                        section,
                        ConnectionError.PermissionDenied(
                            titleEn = "TUN Mode Requires Administrator",
                            titleFa = "حالت TUN نیاز به دسترسی ادمین دارد",
                            reasonEn = "Wintun virtual adapter needs Administrator privileges on Windows. Click 'Connect via System Proxy (No Admin)' for instant browsing, or 'Restart as Admin' to enable TUN Mode for games and all apps.",
                            reasonFa = "حالت TUN برای ساخت کارت شبکه مجازی به دسترسی ادمین نیاز دارد. می‌توانید همین الان با System Proxy (بدون ادمین) متصل شوید یا «اجرا با دسترسی ادمین» را بزنید.",
                        ),
                    )
                    logs.append("TUN", LogLevel.ERROR, "TUN (Wintun) requires Administrator. Use 'Connect via System Proxy' or 'Restart as Admin'.")
                    return@withLock
                }

                if (section == ActiveSectionEngine.SERVER && profile == null) {
                    _tunnelState.value = TunnelState.Error(
                        section,
                        ConnectionError.ProcessExited(-1, "Select or import a server profile first."),
                    )
                    return@withLock
                }

                if (section == ActiveSectionEngine.GAMING &&
                    persisted.gamingSettings.mode == GamingMode.CUSTOM_SERVER &&
                    profile == null
                ) {
                    _tunnelState.value = TunnelState.Error(
                        section,
                        ConnectionError.ProcessExited(-1, "Select or import a game server profile in Servers first."),
                    )
                    return@withLock
                }

                val gatewaySocksPort = resolveGatewaySocksPort(aether.localSocksPort, mhr)
                val gatewayHttpPort = resolveGatewayHttpPort(app.httpProxyPort, gatewaySocksPort, mhr)
                val internalSocksPort = if (section == ActiveSectionEngine.MHR) {
                    val safeMhrHttp = mhr.listenPort.coerceIn(1024, 65535)
                    val safeMhrSocks = mhr.socks5Port.coerceIn(1024, 65535)
                    if (safeMhrSocks == safeMhrHttp) safeMhrHttp + 1 else safeMhrSocks
                } else {
                    pickInternalCoreSocksPort(gatewaySocksPort, gatewayHttpPort)
                }

                val engine = resolveEngine(section, profile, dns, persisted.gamingSettings)
                val connectModeLabel = if (isMhrProxyOnly) "Proxy Only" else tunMode.labelEn
                _tunnelState.value = TunnelState.Connecting(
                    section = section,
                    stageLabelEn = "Starting ${engine.displayEn}...",
                    stageLabelFa = "در حال راه‌اندازی ${engine.displayFa}...",
                    targetLabel = "127.0.0.1:$gatewaySocksPort",
                )
                logs.append("Controller", LogLevel.INFO, "Starting Windows tunnel ($connectModeLabel) section=${section.titleEn}")

                val bypassIps = mutableListOf<String>()
                var activeEndpointLabel = ""
                var lastLogLine = ""

                val err: ConnectionError? = when (section) {
                    ActiveSectionEngine.AETHER -> {
                        activeEndpointLabel = if (aether.scanForEndpoint || aether.endpointIp.isBlank()) "Auto-Scanned WARP" else "${aether.endpointIp}:${aether.endpointPort}"
                        if (aether.endpointIp.isNotBlank()) bypassIps += aether.endpointIp
                        startAether(
                            app = app,
                            aether = aether,
                            coreSocksPort = internalSocksPort,
                            onStage = { en, fa ->
                                _tunnelState.value = TunnelState.Connecting(section, en, fa, "127.0.0.1:$gatewaySocksPort")
                            },
                            onDiscovered = { ip, port ->
                                bypassIps += ip
                                activeEndpointLabel = "$ip:$port"
                                store.update { cur ->
                                    cur.copy(
                                        aetherSettings = cur.aetherSettings.copy(
                                            endpointIp = ip,
                                            endpointPort = port,
                                            lastScannedEndpointIp = ip,
                                            lastScannedEndpointPort = port,
                                            scanForEndpoint = false,
                                        ),
                                    )
                                }
                                logs.append("AETHER", LogLevel.INFO, "Cached fastest endpoint $ip:$port for fast next connect")
                            },
                            onLastLine = { lastLogLine = it },
                        )
                    }
                    ActiveSectionEngine.GAMING -> {
                        val gaming = persisted.gamingSettings
                        when (gaming.mode) {
                            GamingMode.DNS_ANTI_SANCTION -> {
                                val dnsIp = gaming.selectedDnsIp.substringBefore(':').ifBlank { "10.202.10.10" }
                                bypassIps += dnsIp
                                activeEndpointLabel = "Game DNS • ${gaming.selectedDnsIp}"
                                startGamingDns(gaming, app, internalSocksPort) { lastLogLine = it }
                            }
                            GamingMode.LOCAL_DPI_DESYNC -> {
                                val cleanDns = gaming.selectedDnsIp.substringBefore(':').ifBlank { "1.1.1.1" }
                                bypassIps += listOf(cleanDns, "1.1.1.1", "8.8.8.8")
                                activeEndpointLabel = "Local DPI Desync (No Server)"
                                startLocalDesync(gaming, internalSocksPort)
                            }
                            GamingMode.AETHER_UDP_QUIC -> {
                                activeEndpointLabel = "Gaming QUIC v2 • WARP"
                                val tuned = aether.copy(
                                    protocol = AetherProtocol.MASQUE,
                                    masqueTransport = if (gaming.preferQuicV2H3) MasqueTransport.HTTP3 else MasqueTransport.HTTP2,
                                    quicV2Enabled = gaming.preferQuicV2H3,
                                    psiphonEnabled = false,
                                    torEnabled = false,
                                )
                                val tunedApp = app.copy(
                                    activePreset = OneTapPreset.WARP_FAST_MASQUE_H2,
                                    exitNode = ExitNode.DEFAULT,
                                    mtu = gaming.udpMtu,
                                    bypassIranEnabled = gaming.bypassIranGameTraffic,
                                )
                                startAether(
                                    app = tunedApp,
                                    aether = tuned,
                                    coreSocksPort = internalSocksPort,
                                    onStage = { en, fa ->
                                        _tunnelState.value = TunnelState.Connecting(section, en, fa, "127.0.0.1:$gatewaySocksPort")
                                    },
                                    onDiscovered = { ip, port ->
                                        bypassIps += ip
                                        activeEndpointLabel = "Gaming QUIC • $ip:$port"
                                    },
                                    onLastLine = { lastLogLine = it },
                                )
                            }
                            GamingMode.CUSTOM_SERVER -> {
                                val p = profile!!
                                bypassIps += p.address
                                activeEndpointLabel = "Game Server • ${p.name}"
                                startCustomServer(p, app, internalSocksPort) { lastLogLine = it }
                            }
                        }
                    }
                    ActiveSectionEngine.SERVER -> {
                        val p = profile!!
                        bypassIps += p.address
                        activeEndpointLabel = "${p.address}:${p.port}"
                        startCustomServer(p, app, internalSocksPort) { lastLogLine = it }
                    }
                    ActiveSectionEngine.MHR -> {
                        bypassIps += mhr.googleIp
                        val httpPort = mhr.listenPort.coerceIn(1024, 65535)
                        activeEndpointLabel = "${mhr.mode.labelEn} • ${mhr.googleIp} (HTTP:$httpPort / SOCKS:$internalSocksPort)"
                        startMhr(app, aether, mhr, internalSocksPort) { lastLogLine = it }
                    }
                    ActiveSectionEngine.DNS -> {
                        val cleanDnsHost = dns.resolverAddress.removePrefix("https://").removePrefix("quic://").removePrefix("tls://").substringBefore('/').substringBefore(':')
                        if (cleanDnsHost.count { it == '.' } == 3) bypassIps += cleanDnsHost
                        activeEndpointLabel = when (dns.engineType) {
                            DnsEngineType.AUTO_TUNNEL -> "Auto DNS • ${dns.resolverAddress}"
                            DnsEngineType.DNS_CHANGER -> "DNS Changer • ${dns.resolverAddress}"
                            DnsEngineType.CUSTOM_DNSTT -> "${dns.nameserver} (${dns.innerProtocol.label})"
                        }
                        startDns(dns, app, aether, internalSocksPort) { lastLogLine = it }
                    }
                }

                val isDnsChangerOnly = (section == ActiveSectionEngine.GAMING && persisted.gamingSettings.mode == GamingMode.DNS_ANTI_SANCTION) ||
                    (section == ActiveSectionEngine.DNS && dns.engineType == DnsEngineType.DNS_CHANGER)
                val overrideResolver = when {
                    section == ActiveSectionEngine.GAMING && persisted.gamingSettings.mode == GamingMode.DNS_ANTI_SANCTION ->
                        persisted.gamingSettings.selectedDnsIp.ifBlank { "10.202.10.10" }
                    section == ActiveSectionEngine.DNS && dns.engineType == DnsEngineType.DNS_CHANGER ->
                        dns.resolverAddress.ifBlank { "1.1.1.1" }
                    else -> null
                }

                if (err != null) {
                    disconnectSync(updateState = false)
                    _tunnelState.value = TunnelState.Error(section, err)
                    logs.append("Controller", LogLevel.ERROR, "Core failed: ${lastLogLine.ifBlank { err.toString() }}")
                    if (!fastScan && !userRequestedDisconnect && app.autoReconnectOnNetworkChange && isRetryableConnectionError(err)) {
                        scheduleAutoRecovery(section, lastLogLine.ifBlank { err.toString() })
                    }
                    return@withLock
                }

                val clashSecret = WindowsSecurityHelper.generateSessionToken()
                val clashPort = 19090

                if (isMhrProxyOnly) {
                    // Proxy-only mode: expose MHR's own HTTP + SOCKS5 listeners directly without changing Windows proxy
                    activeHttpGatewayPort = mhr.listenPort.coerceIn(1024, 65535)
                    activeSocksGatewayPort = internalSocksPort
                    logs.append("MHR", LogLevel.INFO, "Proxy Only active -> HTTP :${activeHttpGatewayPort} | SOCKS5 :$internalSocksPort")
                } else {
                    // Unified sing-box gateway for BOTH System Proxy and Wintun TUN modes:
                    // ensures HTTP/SOCKS5 parity (fixes Psiphon/DNSTT HTTP routing) AND live speed telemetry + rulesets!
                    val sbExe = coreLocator.binaryFile("sing-box.exe")
                    val wintun = coreLocator.binaryFile("wintun.dll")
                    if (!sbExe.exists() || (tunMode == WindowsTunnelMode.TUN_WINTUN && !wintun.exists())) {
                        disconnectSync(updateState = false)
                        _tunnelState.value = TunnelState.Error(
                            section,
                            ConnectionError.MissingBinary("sing-box.exe / wintun.dll", sbExe.absolutePath, "Run scripts/fetch-windows-cores.ps1"),
                        )
                        return@withLock
                    }

                    val gatewayFile = File(
                        store.runtimeDir,
                        if (tunMode == WindowsTunnelMode.TUN_WINTUN) "win-tun-bridge.json" else "win-gateway.json",
                    )
                    val gatewayJson = if (tunMode == WindowsTunnelMode.TUN_WINTUN) {
                        WindowsTunConfigBuilder.buildTunBridgeConfig(
                            appSettings = app,
                            socksPort = internalSocksPort,
                            clashApiPort = clashPort,
                            clashSecret = clashSecret,
                            bypassTargetIps = bypassIps,
                            dnsChangerOnly = isDnsChangerOnly,
                            overrideResolver = overrideResolver,
                        )
                    } else {
                        WindowsTunConfigBuilder.buildSystemGatewayConfig(
                            appSettings = app,
                            socksListenPort = gatewaySocksPort,
                            httpListenPort = gatewayHttpPort,
                            upstreamSocksPort = internalSocksPort,
                            clashApiPort = clashPort,
                            clashSecret = clashSecret,
                            bypassTargetIps = bypassIps,
                            dnsChangerOnly = isDnsChangerOnly,
                            overrideResolver = overrideResolver,
                        )
                    }
                    gatewayFile.writeText(gatewayJson, Charsets.UTF_8)

                    val gwProc = ProcessBuilder(sbExe.absolutePath, "run", "-c", gatewayFile.absolutePath)
                        .directory(sbExe.parentFile)
                        .redirectErrorStream(true)
                        .start()
                    gatewayProcess = gwProc
                    val gwTag = if (tunMode == WindowsTunnelMode.TUN_WINTUN) "WinTUN" else "WinGateway"
                    pumpProcessOutput(gwTag, gwProc) { lastLogLine = it }

                    if (tunMode == WindowsTunnelMode.TUN_WINTUN) {
                        delay(700L)
                        if (!gwProc.isAlive) {
                            disconnectSync(updateState = false)
                            _tunnelState.value = TunnelState.Error(
                                section,
                                ConnectionError.ProcessExited(-1, "Windows Wintun failed to start (run as Administrator). $lastLogLine"),
                            )
                            return@withLock
                        }
                        logs.append("WinTUN", LogLevel.INFO, "Wintun adapter 'pnifox-tun' active for all Windows apps & games.")
                    } else {
                        val gwErr = awaitSocksReady("WinGateway", gwProc, gatewaySocksPort, timeoutMs = 8_000L)
                        if (gwErr != null) {
                            disconnectSync(updateState = false)
                            _tunnelState.value = TunnelState.Error(section, gwErr)
                            return@withLock
                        }
                        activeHttpGatewayPort = gatewayHttpPort
                        activeSocksGatewayPort = gatewaySocksPort
                        systemProxy.apply(httpPort = gatewayHttpPort, bypassIran = app.bypassIranEnabled).onFailure { ex ->
                            logs.append("SystemProxy", LogLevel.WARN, "Registry proxy apply note: ${ex.message}")
                        }.onSuccess {
                            logs.append("SystemProxy", LogLevel.INFO, "Windows System Proxy set to 127.0.0.1:$gatewayHttpPort (SOCKS5 :$gatewaySocksPort)")
                        }
                    }

                    startClashTrafficMonitor(clashPort = clashPort, clashSecret = clashSecret)
                }

                val exposedSocksPort = if (isMhrProxyOnly) internalSocksPort else gatewaySocksPort
                val activeDnsLabel = if (section == ActiveSectionEngine.GAMING) persisted.gamingSettings.selectedDnsIp else app.localDns.ifBlank { "1.1.1.1" }
                val modeStr = if (persisted.windowsTunnelMode == WindowsTunnelMode.TUN_WINTUN) "Wintun VPN" else "System Proxy"
                val netSummary = "$modeStr • DNS $activeDnsLabel" + if (app.bypassIranEnabled) " • Bypass IR" else ""
                val connected = TunnelState.Connected(
                    section = section,
                    engine = engine,
                    localSocksPort = exposedSocksPort,
                    activeEndpoint = activeEndpointLabel,
                    resolvedIp = bypassIps.firstOrNull().orEmpty(),
                    pingMs = profile?.lastPingMs,
                    connectedAtEpochMs = System.currentTimeMillis(),
                    activeDnsSummary = activeDnsLabel,
                    networkChangeSummary = netSummary,
                )
                _tunnelState.value = connected

                if (!fastScan) {
                    startConnectionWatchdog(section)
                }

                delay(800L)
                refreshActiveRealDelay()
                val exitPair = withContext(Dispatchers.IO) {
                    RealDelaySocketProber.fetchExitIpViaSocks5("127.0.0.1", exposedSocksPort)
                }
                if (exitPair != null) {
                    val cur = _tunnelState.value
                    if (cur is TunnelState.Connected) {
                        _tunnelState.value = cur.copy(exitIp = exitPair.first, exitLocation = exitPair.second)
                        logs.append("ExitIP", LogLevel.INFO, "Connected Exit IP: ${exitPair.first} (${exitPair.second})")
                    }
                }
            }
        }

    private suspend fun startAether(
        app: AppSettings,
        aether: AetherSettings,
        coreSocksPort: Int,
        onStage: (String, String) -> Unit,
        onDiscovered: (String, Int) -> Unit,
        onLastLine: (String) -> Unit,
    ): ConnectionError? {
        val bin = coreLocator.binaryFile("aether.exe")
        if (!bin.exists()) {
            return ConnectionError.MissingBinary("aether.exe", bin.absolutePath, "Run scripts/fetch-windows-cores.ps1")
        }
        val dir = store.aetherDir
        File(dir, "aether.toml").apply { if (!exists()) createNewFile() }

        // Isolate internal listener to strictly localhost on coreSocksPort; sing-box gateway handles public/LAN/HTTP/stats
        val innerAether = aether.copy(
            localSocksPort = coreSocksPort,
            innerAetherBindPort = if (aether.innerAetherBindPort == coreSocksPort) coreSocksPort - 1 else aether.innerAetherBindPort,
        )
        val innerApp = app.copy(allowLan = false)

        val usesPsiphon = argBuilder.isPsiphonActive(innerApp, innerAether)
        val psiphonStandalone = usesPsiphon && (
            innerAether.psiphonMode.isStandalone || innerApp.activePreset == OneTapPreset.PSIPHON_AKAMAI_CDN
        )
        val usesTor = argBuilder.isTorActive(innerApp, innerAether)

        val routesFile = argBuilder.writeRoutesFileIfNeeded(innerApp, dir)
        val effectiveAether = if (innerAether.echEnabled && innerAether.smartEchBootstrap && innerAether.echBase64Override.isBlank()) {
            onStage("Bootstrapping ECH config (DoH / DoT / Port-53)...", "در حال دریافت هوشمند ECH از شبکه...")
            val bootstrapped = withContext(Dispatchers.IO) {
                EchBootstrapResolver.resolveEchConfig(
                    targetDomain = innerAether.echDomain.ifBlank { "cloudflare-ech.com" },
                    customDohOrDns = innerAether.echDns,
                    onLog = { msg -> logs.append("ECH-Bootstrap", LogLevel.INFO, msg) },
                )
            }
            if (bootstrapped != null) {
                innerAether.copy(echBase64Override = bootstrapped.echConfigBase64)
            } else {
                innerAether
            }
        } else {
            innerAether
        }
        val args = argBuilder.buildRunArgs(
            appSettings = innerApp,
            aether = effectiveAether,
            nativeDir = coreLocator.resolveCoresDirectory().absolutePath,
            workDir = dir,
            psiphonServersDatPath = null,
            routesFile = routesFile,
            exposeLocalHttpProxy = false,
        )

        val cmd = listOf(bin.absolutePath) + args
        logs.append("AETHER", LogLevel.INFO, WindowsSecurityHelper.sanitizeLogLine("Launching: ${cmd.joinToString(" ")}"))
        onStage(
            if (psiphonStandalone) "Dialing Akamai / Psiphon CDN..." else "AETHER handshaking (MASQUE / WARP)...",
            if (psiphonStandalone) "در حال اتصال از طریق Akamai CDN..." else "در حال برقراری اتصال AETHER...",
        )

        val pb = ProcessBuilder(cmd).directory(bin.parentFile).redirectErrorStream(true)
        val curPath = pb.environment()["PATH"].orEmpty()
        pb.environment()["PATH"] = "${bin.parentFile.absolutePath};$curPath"
        val proc = pb.start()
        coreProcess = proc

        val secondaryTunnelReady = AtomicBoolean(!(usesPsiphon || usesTor))
        val detectedExitLocation = AtomicBoolean(false)

        val peerPattern = Regex("""(\d{1,3}(?:\.\d{1,3}){3}):(\d+)""")
        pumpProcessOutput("AETHER", proc) { line ->
            onLastLine(line)
            if (!psiphonStandalone && (line.contains("registering", ignoreCase = true) || line.contains("no warp identity", ignoreCase = true))) {
                onStage("Registering WARP identity...", "در حال ثبت هویت WARP...")
            } else if (!psiphonStandalone && line.contains("scanning", ignoreCase = true)) {
                onStage("Scanning clean WARP endpoints...", "در حال اسکن اندپوینت تمیز...")
            } else if (usesPsiphon && line.contains("psiphon", ignoreCase = true) && !secondaryTunnelReady.get()) {
                onStage(
                    if (psiphonStandalone) "Dialing Akamai / Psiphon CDN..." else "Dialing Psiphon inside WARP tunnel...",
                    if (psiphonStandalone) "در حال برقراری اتصال Akamai / سایفون..." else "در حال اتصال سایفون از داخل تونل WARP...",
                )
            } else if (usesTor && line.contains("tor", ignoreCase = true) && !secondaryTunnelReady.get()) {
                onStage("Bootstrapping Tor inside WARP...", "در حال راه‌اندازی Tor از داخل WARP...")
            }

            if (usesPsiphon && (line.contains("psiphon through the tunnel", ignoreCase = true) ||
                    line.contains("tunnel exit:", ignoreCase = true))
            ) {
                secondaryTunnelReady.set(true)
                detectedExitLocation.set(true)
            }
            if (usesTor && (line.contains("Bootstrapped 100%", ignoreCase = true) ||
                    line.contains("tor through the tunnel", ignoreCase = true))
            ) {
                secondaryTunnelReady.set(true)
                detectedExitLocation.set(true)
            }

            ConnectionStatusNarrator.fromLogLine(line)?.let { funStage ->
                onStage(funStage.en, funStage.fa)
            }
            if (line.contains("tryDatastoreOpenDB", ignoreCase = true)) {
                runCatching { File(store.aetherDir, "psiphon-cache").deleteRecursively() }
            }
            if (line.contains("using gateway", ignoreCase = true) ||
                line.contains("selected endpoint", ignoreCase = true) ||
                line.contains("selected masque", ignoreCase = true) ||
                line.contains("best gateway", ignoreCase = true)
            ) {
                peerPattern.find(line)?.let { m ->
                    val ip = m.groupValues[1]
                    val port = m.groupValues[2].toIntOrNull() ?: 443
                    if (!ip.startsWith("127.") && !ip.startsWith("0.")) {
                        onDiscovered(ip, port)
                    }
                }
            }
        }

        val bindErr = awaitSocksReady("AETHER", proc, coreSocksPort, timeoutMs = 90_000L)
        if (bindErr != null) return bindErr

        if ((usesPsiphon || usesTor) && !secondaryTunnelReady.get()) {
            val subName = if (usesPsiphon) "Psiphon" else "Tor"
            onStage("Waiting for $subName exit tunnel...", "در انتظار آماده شدن خروجی $subName...")
            val subDeadline = System.currentTimeMillis() + 45_000L
            while (System.currentTimeMillis() < subDeadline && proc.isAlive) {
                if (secondaryTunnelReady.get() || detectedExitLocation.get()) break
                delay(400L)
            }
            if (!proc.isAlive) {
                return ConnectionError.ProcessExited(proc.exitValue(), "$subName exited unexpectedly before establishing tunnel.")
            }
            if (!secondaryTunnelReady.get()) {
                val probeOk = withContext(Dispatchers.IO) {
                    RealDelaySocketProber.probeHttp204ViaSocks5("127.0.0.1", coreSocksPort, timeoutMs = 6000) != null
                }
                if (!probeOk) {
                    return ConnectionError.HandshakeTimeout(
                        if (usesPsiphon) {
                            "Psiphon did not finish connecting inside WARP within 45s. Try Psiphon Mode = 'CDN (Meek)' or Country = 'AUTO'."
                        } else {
                            "Tor did not bootstrap within 45s. Enable bridges or reset cache."
                        },
                    )
                }
            }
        }
        return null
    }

    private suspend fun startCustomServer(
        profile: ProxyProfile,
        app: AppSettings,
        coreSocksPort: Int,
        onLastLine: (String) -> Unit,
    ): ConnectionError? {
        if (profile.spoofLocalhost40443 && !profile.rawUri.trimStart().startsWith("{")) {
            val fakeSni = profile.spoofFakeSni.ifBlank { profile.sni }.ifBlank { "www.cloudflare.com" }
            spoofServer.start(
                listenHost = "127.0.0.1",
                listenPort = 40443,
                targetHost = profile.address,
                targetPort = profile.port,
                fakeSni = fakeSni,
                method = "fragment+sni",
                fragLen = 12,
                fragDelayMs = 5L,
            )
            logs.append("SNI-Spoof", LogLevel.INFO, "Started local SNI Spoof 127.0.0.1:40443 -> ${profile.address}:${profile.port} (SNI=$fakeSni)")
        }

        val innerApp = app.copy(allowLan = false)
        val isRawJson = profile.rawUri.trimStart().startsWith("{")
        val useSingBox = !isRawJson && (
            profile.protocol == ProxyProtocol.HYSTERIA2 ||
                profile.protocol == ProxyProtocol.WIREGUARD ||
                profile.protocol == ProxyProtocol.TUIC ||
                profile.protocol == ProxyProtocol.ANYTLS
            )
        if (useSingBox) {
            val sbExe = coreLocator.binaryFile("sing-box.exe")
            if (!sbExe.exists()) return ConnectionError.MissingBinary("sing-box.exe", sbExe.absolutePath, "Run scripts/fetch-windows-cores.ps1")
            val cfg = File(store.runtimeDir, "singbox-server.json")
            cfg.writeText(ConfigBuilders.buildSingBoxConfig(profile, innerApp, coreSocksPort, exposeLocalHttpProxy = false), Charsets.UTF_8)
            val proc = ProcessBuilder(sbExe.absolutePath, "run", "-c", cfg.absolutePath)
                .directory(sbExe.parentFile)
                .redirectErrorStream(true)
                .start()
            coreProcess = proc
            pumpProcessOutput("Sing-box", proc, onLastLine)
            return awaitSocksReady("Sing-box", proc, coreSocksPort, timeoutMs = 15_000L)
        }

        val xrayExe = coreLocator.binaryFile("xray.exe")
        if (!xrayExe.exists()) return ConnectionError.MissingBinary("xray.exe", xrayExe.absolutePath, "Run scripts/fetch-windows-cores.ps1")
        val hasGeo = coreLocator.ensureGeoAssets(store.geoDir)
        val cfg = File(store.runtimeDir, "xray-server.json")
        cfg.writeText(
            ConfigBuilders.buildXrayConfig(
                profile = profile,
                appSettings = innerApp,
                socksPort = coreSocksPort,
                errorLogPath = null,
                hasGeoAssets = hasGeo,
                exposeLocalHttpProxy = false,
            ),
            Charsets.UTF_8,
        )
        val pb = ProcessBuilder(xrayExe.absolutePath, "run", "-c", cfg.absolutePath)
            .directory(xrayExe.parentFile)
            .redirectErrorStream(true)
        pb.environment()["XRAY_LOCATION_ASSET"] = store.geoDir.absolutePath
        val proc = pb.start()
        coreProcess = proc
        pumpProcessOutput("Xray", proc, onLastLine)
        return awaitSocksReady("Xray", proc, coreSocksPort, timeoutMs = 12_000L)
    }

    private suspend fun startMhr(
        app: AppSettings,
        aether: AetherSettings,
        mhr: MhrSettings,
        mhrSocksPort: Int,
        onLastLine: (String) -> Unit,
    ): ConnectionError? {
        val mhrExe = coreLocator.binaryFile("mhrv-rs.exe")
        if (!mhrExe.exists()) return ConnectionError.MissingBinary("mhrv-rs.exe", mhrExe.absolutePath, "Run scripts/fetch-windows-cores.ps1")
        val cleanIds = mhr.scriptIds.map { MhrDefaultSniPool.extractDeploymentId(it) }.filter { it.isNotBlank() }
        if ((mhr.mode == MhrMode.APPS_SCRIPT || mhr.mode == MhrMode.FULL) && cleanIds.isEmpty()) {
            return ConnectionError.ProcessExited(
                -1,
                "Add at least one Apps Script Deployment ID or switch MHR Mode to 'Direct (SNI Front)'.",
            )
        }
        val mhrHttpPort = mhr.listenPort.coerceIn(1024, 65535)
        val listenHost = if (mhr.shareOnLan || app.allowLan) "0.0.0.0" else "127.0.0.1"
        val cfgFile = File(store.mhrDir, "config.json")
        cfgFile.writeText(
            MhrConfigBuilder.buildConfigJson(
                appSettings = app,
                aetherSettings = aether,
                mhr = mhr,
                overrideSocks5Port = mhrSocksPort,
                overrideListenPort = mhrHttpPort,
                overrideListenHost = listenHost,
            ),
            Charsets.UTF_8,
        )
        val pb = ProcessBuilder(mhrExe.absolutePath, "-c", cfgFile.absolutePath, "--no-cert-check")
            .directory(store.mhrDir)
            .redirectErrorStream(true)
        pb.environment()["RUST_LOG"] = mhr.logLevel.ifBlank { "info" }
        val proc = pb.start()
        coreProcess = proc
        pumpProcessOutput("MHR", proc, onLastLine)
        return awaitSocksReady("MHR", proc, mhrSocksPort, timeoutMs = 12_000L)
    }

    private suspend fun startDns(
        dns: DnsTunnelSettings,
        app: AppSettings,
        aether: AetherSettings,
        coreSocksPort: Int,
        onLastLine: (String) -> Unit,
    ): ConnectionError? {
        val innerApp = app.copy(allowLan = false)
        return when (dns.engineType) {
            DnsEngineType.AUTO_TUNNEL -> {
                val dnsIp = argBuilder.extractAetherDnsServer(dns)
                val tunedAether = aether.copy(masqueTransport = MasqueTransport.HTTP2)
                val tunedApp = innerApp.copy(localDns = dnsIp, activePreset = OneTapPreset.WARP_FAST_MASQUE_H2)
                startAether(tunedApp, tunedAether, coreSocksPort, onStage = { _, _ -> }, onDiscovered = { _, _ -> }, onLastLine = onLastLine)
            }
            DnsEngineType.DNS_CHANGER -> {
                val xrayExe = coreLocator.binaryFile("xray.exe")
                if (!xrayExe.exists()) return ConnectionError.MissingBinary("xray.exe", xrayExe.absolutePath, "Run scripts/fetch-windows-cores.ps1")
                val hasGeo = coreLocator.ensureGeoAssets(store.geoDir)
                val cfg = File(store.runtimeDir, "xray-dnschanger.json")
                cfg.writeText(
                    ConfigBuilders.buildDnsChangerXrayConfig(dns, innerApp, coreSocksPort, null, hasGeo, exposeLocalHttpProxy = false),
                    Charsets.UTF_8,
                )
                val pb = ProcessBuilder(xrayExe.absolutePath, "run", "-c", cfg.absolutePath)
                    .directory(xrayExe.parentFile)
                    .redirectErrorStream(true)
                pb.environment()["XRAY_LOCATION_ASSET"] = store.geoDir.absolutePath
                val proc = pb.start()
                coreProcess = proc
                pumpProcessOutput("DNS-Xray", proc, onLastLine)
                awaitSocksReady("DNS-Xray", proc, coreSocksPort, timeoutMs = 10_000L)
            }
            DnsEngineType.CUSTOM_DNSTT -> {
                val dnsttExe = coreLocator.binaryFile("dnstt-client.exe")
                if (!dnsttExe.exists()) return ConnectionError.MissingBinary("dnstt-client.exe", dnsttExe.absolutePath, "Run scripts/fetch-windows-cores.ps1")
                val directSocks = dns.innerProtocol == DnsInnerProtocol.SOCKS5
                val dnsttListenPort = if (directSocks) coreSocksPort else coreSocksPort - 1
                val dnsttArgs = argBuilder.buildDnsttArgs(dns, localSocksPort = dnsttListenPort)
                val dnsttProc = ProcessBuilder(listOf(dnsttExe.absolutePath) + dnsttArgs)
                    .directory(dnsttExe.parentFile)
                    .redirectErrorStream(true)
                    .start()
                if (directSocks) {
                    coreProcess = dnsttProc
                    pumpProcessOutput("DNSTT", dnsttProc, onLastLine)
                    awaitSocksReady("DNSTT", dnsttProc, coreSocksPort, timeoutMs = 12_000L)
                } else {
                    secondaryProcess = dnsttProc
                    pumpProcessOutput("DNSTT", dnsttProc, onLastLine)
                    val innerErr = awaitSocksReady("DNSTT-Inner", dnsttProc, dnsttListenPort, timeoutMs = 10_000L)
                    if (innerErr != null) return innerErr
                    val sbExe = coreLocator.binaryFile("sing-box.exe")
                    if (!sbExe.exists()) return ConnectionError.MissingBinary("sing-box.exe", sbExe.absolutePath, "Run scripts/fetch-windows-cores.ps1")
                    val cfg = File(store.runtimeDir, "singbox-dnstt-ssh.json")
                    cfg.writeText(
                        ConfigBuilders.buildSingBoxDnsttSshConfig(dns, innerApp, dnsttListenPort, coreSocksPort, exposeLocalHttpProxy = false),
                        Charsets.UTF_8,
                    )
                    val sbProc = ProcessBuilder(sbExe.absolutePath, "run", "-c", cfg.absolutePath)
                        .directory(sbExe.parentFile)
                        .redirectErrorStream(true)
                        .start()
                    coreProcess = sbProc
                    pumpProcessOutput("DNSTT-SSH", sbProc, onLastLine)
                    awaitSocksReady("DNSTT-SSH", sbProc, coreSocksPort, timeoutMs = 15_000L)
                }
            }
        }
    }

    /**
     * Verifies a real SOCKS5 NO-AUTH greeting (`0x05 0x01 0x00` -> `0x05 0x00`) rather than a raw
     * empty TCP connect+close, preventing spurious `socksPeekByte() failed: EOF` errors in Psiphon.
     */
    private suspend fun awaitSocksReady(
        tag: String,
        proc: Process,
        socksPort: Int,
        timeoutMs: Long,
    ): ConnectionError? {
        val deadline = System.currentTimeMillis() + timeoutMs
        while (System.currentTimeMillis() < deadline) {
            if (!proc.isAlive) {
                return ConnectionError.ProcessExited(proc.exitValue(), "$tag process exited before listening on :$socksPort")
            }
            val open = probeSocks5Greeting("127.0.0.1", socksPort)
            if (open) {
                logs.append(tag, LogLevel.INFO, "$tag SOCKS5 ready on 127.0.0.1:$socksPort")
                return null
            }
            delay(350L)
        }
        return ConnectionError.HandshakeTimeout("$tag did not open SOCKS5 127.0.0.1:$socksPort within ${timeoutMs / 1000}s")
    }

    private fun probeSocks5Greeting(host: String, port: Int): Boolean = runCatching {
        Socket().use { s ->
            s.soTimeout = 400
            s.connect(InetSocketAddress(host, port), 350)
            val out = s.getOutputStream()
            out.write(byteArrayOf(0x05, 0x01, 0x00))
            out.flush()
            val ins: InputStream = s.getInputStream()
            val b0 = ins.read()
            val b1 = ins.read()
            b0 == 0x05 && b1 == 0x00
        }
    }.getOrDefault(false)

    private fun pumpProcessOutput(tag: String, proc: Process, onLine: (String) -> Unit) {
        scope.launch {
            runCatching {
                proc.inputStream.bufferedReader().use { br ->
                    var line = br.readLine()
                    while (line != null && isActive) {
                        val safe = WindowsSecurityHelper.sanitizeLogLine(line.trim())
                        if (safe.isNotEmpty()) {
                            onLine(safe)
                            val lvl = when {
                                safe.contains("ERROR", ignoreCase = true) || safe.contains("FATAL", ignoreCase = true) -> LogLevel.ERROR
                                safe.contains("WARN", ignoreCase = true) -> LogLevel.WARN
                                else -> LogLevel.INFO
                            }
                            logs.append(tag, lvl, safe)
                        }
                        line = br.readLine()
                    }
                }
            }
        }
    }

    private fun startClashTrafficMonitor(clashPort: Int, clashSecret: String) {
        trafficJob?.cancel()
        trafficJob = scope.launch {
            val req = Request.Builder()
                .url("http://127.0.0.1:$clashPort/traffic")
                .header("Authorization", "Bearer $clashSecret")
                .build()
            while (isActive) {
                runCatching {
                    httpClient.newCall(req).execute().use { resp ->
                        val source = resp.body?.source() ?: return@use
                        while (isActive && !source.exhausted()) {
                            val line = source.readUtf8Line() ?: break
                            val obj = json.parseToJsonElement(line) as? JsonObject ?: continue
                            val up = obj["up"]?.jsonPrimitive?.longOrNull ?: 0L
                            val down = obj["down"]?.jsonPrimitive?.longOrNull ?: 0L
                            _txSpeedBps.value = up
                            _rxSpeedBps.value = down
                            _sessionTxBytes.value += up
                            _sessionRxBytes.value += down
                            store.recordTrafficDelta(rxDelta = down, txDelta = up)
                        }
                    }
                }
                delay(1200L)
            }
        }
    }

    suspend fun refreshActiveRealDelay(): Int? = withContext(Dispatchers.IO) {
        val connected = _tunnelState.value as? TunnelState.Connected
        if (connected == null) {
            null
        } else {
            val persisted = store.stateFlow.value
            val isDnsProbe = (connected.section == ActiveSectionEngine.DNS && persisted.dnsSettings.engineType == DnsEngineType.DNS_CHANGER) ||
                (connected.section == ActiveSectionEngine.GAMING && persisted.gamingSettings.mode == GamingMode.DNS_ANTI_SANCTION)
            val rtt = when {
                connected.section == ActiveSectionEngine.DNS && persisted.dnsSettings.engineType == DnsEngineType.DNS_CHANGER ->
                    RealDelaySocketProber.measureDnsResolverLatencyMs(persisted.dnsSettings)
                connected.section == ActiveSectionEngine.GAMING && persisted.gamingSettings.mode == GamingMode.DNS_ANTI_SANCTION ->
                    RealDelaySocketProber.measureDnsResolverLatencyMs(
                        persisted.dnsSettings.copy(mode = DnsTunnelMode.UDP, resolverAddress = persisted.gamingSettings.selectedDnsIp),
                    )
                else -> RealDelaySocketProber.probeHttp204ViaSocks5("127.0.0.1", connected.localSocksPort)
            }
            val cur = _tunnelState.value
            if (cur is TunnelState.Connected) {
                _tunnelState.value = cur.copy(pingMs = rtt)
            }
            if (rtt != null) {
                logs.append("RealDelay", LogLevel.INFO, "Measured ${if (isDnsProbe) "DNS" else "HTTP 204"} delay: ${rtt}ms")
            } else {
                logs.append(
                    "RealDelay",
                    LogLevel.WARN,
                    if (isDnsProbe) "DNS latency probe timed out" else "Real HTTP 204 probe timed out on :${connected.localSocksPort}",
                )
            }
            rtt
        }
    }

    suspend fun registerAetherKeys(target: AetherIdentityTarget): Result<Unit> = withContext(Dispatchers.IO) {
        val bin = coreLocator.binaryFile("aether.exe")
        if (!bin.exists()) {
            Result.failure(IllegalStateException("aether.exe missing"))
        } else {
            val aether = store.stateFlow.value.aetherSettings
            val args = argBuilder.buildRegisterArgs(target, aether, store.aetherDir)
            runCatching {
                val proc = ProcessBuilder(listOf(bin.absolutePath) + args)
                    .directory(bin.parentFile)
                    .redirectErrorStream(true)
                    .start()
                proc.inputStream.bufferedReader().forEachLine { l ->
                    if (l.isNotBlank()) logs.append("AETHER-Reg", LogLevel.INFO, WindowsSecurityHelper.sanitizeLogLine(l.trim()))
                }
                proc.waitFor(45, TimeUnit.SECONDS)
                if (proc.exitValue() != 0) error("Registration exited with code ${proc.exitValue()}")
                store.updateAetherSettings { it.copy(masqueAccountRegistered = true) }
                Unit
            }
        }
    }

    fun clearAetherCache(): Result<Unit> = runCatching {
        File(store.aetherDir, "psiphon-cache").deleteRecursively()
        File(store.aetherDir, "tor-cache").deleteRecursively()
        logs.append("AETHER", LogLevel.INFO, "Cleared Psiphon & Tor cache directories.")
    }

    suspend fun installMhrCaCert(): Result<String> = withContext(Dispatchers.IO) {
        val mhrExe = coreLocator.binaryFile("mhrv-rs.exe")
        if (!mhrExe.exists()) {
            Result.failure(IllegalStateException("mhrv-rs.exe missing"))
        } else {
            runCatching {
                val proc = ProcessBuilder(mhrExe.absolutePath, "--install-cert")
                    .directory(store.mhrDir)
                    .redirectErrorStream(true)
                    .start()
                val output = proc.inputStream.bufferedReader().readText()
                proc.waitFor(30, TimeUnit.SECONDS)
                logs.append("MHR-CA", LogLevel.INFO, output.ifBlank { "CA cert installer finished (code ${proc.exitValue()})" })
                if (proc.exitValue() != 0) error("CA install exited with code ${proc.exitValue()}")
                _isMhrCaInstalled.value = queryMhrCaInstalled()
                "MITM CA Certificate installed in Windows Trust Store ✓"
            }
        }
    }

    suspend fun removeMhrCaCert(): Result<String> = withContext(Dispatchers.IO) {
        val mhrExe = coreLocator.binaryFile("mhrv-rs.exe")
        runCatching {
            if (mhrExe.exists()) {
                val proc = ProcessBuilder(mhrExe.absolutePath, "--remove-cert")
                    .directory(store.mhrDir)
                    .redirectErrorStream(true)
                    .start()
                val output = proc.inputStream.bufferedReader().readText()
                proc.waitFor(30, TimeUnit.SECONDS)
                if (output.isNotBlank()) logs.append("MHR-CA", LogLevel.INFO, output.trim())
            }
            // Extra PowerShell sweep on CurrentUser\Root & LocalMachine\Root in case CA name remained in store
            val psRemove = """
                Get-ChildItem -Path Cert:\CurrentUser\Root -ErrorAction SilentlyContinue |
                  Where-Object { ${'$'}_.Subject -match 'MasterHttpRelay|mhrv' -or ${'$'}_.FriendlyName -match 'MasterHttpRelay|mhrv' } |
                  ForEach-Object { Remove-Item -Path ${'$'}_.PSPath -Force -ErrorAction SilentlyContinue }
            """.trimIndent()
            ProcessBuilder("powershell.exe", "-NoProfile", "-WindowStyle", "Hidden", "-Command", psRemove)
                .redirectErrorStream(true)
                .start()
                .waitFor(8, TimeUnit.SECONDS)

            // Clean any leftover CA folders
            File(store.mhrDir, "ca").deleteRecursively()
            System.getenv("APPDATA")?.takeIf { it.isNotBlank() }?.let { appData ->
                File(File(appData, "mhrv-rs"), "ca").deleteRecursively()
            }
            val stillPresent = queryMhrCaInstalled()
            _isMhrCaInstalled.value = stillPresent
            if (stillPresent) {
                error("Certificate still present in Machine Store — retry after Restart as Administrator.")
            }
            logs.append("MHR-CA", LogLevel.INFO, "Removed MHR MITM CA certificate and local ca/ keypair cleanly.")
            "MITM CA Certificate removed cleanly from Windows Trust Store ✓"
        }
    }

    private fun queryMhrCaInstalled(): Boolean = runCatching {
        val psCheck = """
            ${'$'}found = Get-ChildItem -Path Cert:\CurrentUser\Root, Cert:\LocalMachine\Root -ErrorAction SilentlyContinue |
              Where-Object { ${'$'}_.Subject -match 'MasterHttpRelay|mhrv' -or ${'$'}_.FriendlyName -match 'MasterHttpRelay|mhrv' } |
              Select-Object -First 1
            if (${'$'}found) { Write-Output 'INSTALLED' }
        """.trimIndent()
        val proc = ProcessBuilder("powershell.exe", "-NoProfile", "-WindowStyle", "Hidden", "-Command", psCheck)
            .redirectErrorStream(true)
            .start()
        val out = proc.inputStream.bufferedReader(Charsets.UTF_8).readText()
        proc.waitFor(5, TimeUnit.SECONDS)
        out.contains("INSTALLED")
    }.getOrDefault(false)

    suspend fun probeSni(googleIp: String, sni: String): MhrSniTestResult = withContext(Dispatchers.IO) {
        val tStart = System.nanoTime()
        runCatching {
            Socket().use { raw ->
                raw.connect(InetSocketAddress(googleIp.trim(), 443), 3500)
                val factory = SSLSocketFactory.getDefault() as SSLSocketFactory
                val ssl = factory.createSocket(raw, googleIp.trim(), 443, true) as SSLSocket
                ssl.use { tls ->
                    tls.soTimeout = 3500
                    val params = tls.sslParameters
                    params.serverNames = listOf(SNIHostName(sni.trim()))
                    tls.sslParameters = params
                    tls.startHandshake()
                }
            }
            val ms = ((System.nanoTime() - tStart) / 1_000_000L).coerceAtLeast(1L)
            MhrSniTestResult(host = sni.trim(), ok = true, latencyMs = ms, status = "${ms}ms")
        }.getOrElse { ex ->
            MhrSniTestResult(host = sni.trim(), ok = false, error = ex.message ?: "TLS failed")
        }
    }

    suspend fun autoDetectGoogleIp(frontDomain: String = "www.google.com"): String? = withContext(Dispatchers.IO) {
        runCatching {
            InetAddress.getAllByName(frontDomain.trim().ifBlank { "www.google.com" })
                .filterIsInstance<Inet4Address>()
                .firstOrNull()
                ?.hostAddress
        }.getOrNull()
    }

    suspend fun testUdpPort53AndTxt(resolver: String, nameserver: String): String = withContext(Dispatchers.IO) {
        val hostIp = resolver.removePrefix("https://").removePrefix("http://").removePrefix("quic://").removePrefix("tls://")
            .substringBefore('/').substringBefore(':').ifBlank { "1.1.1.1" }
        val aRes = sendUdpDnsQuery(hostIp, "google.com", 1)
        val txtRes = if (nameserver.isNotBlank()) sendUdpDnsQuery(hostIp, nameserver.trim(), 16) else null
        buildString {
            aRes.fold(
                onSuccess = { (ms, _) -> append("UDP/53 OK ($hostIp: ${ms}ms)") },
                onFailure = { append("UDP/53 blocked/timeout (${it.message})") },
            )
            txtRes?.fold(
                onSuccess = { (ms, rcode) -> append(" • TXT: ${ms}ms (RCODE=$rcode)") },
                onFailure = { append(" • TXT timeout/filtered") },
            )
        }
    }

    private fun sendUdpDnsQuery(hostIp: String, domain: String, qType: Int): Result<Pair<Int, Int>> = runCatching {
        val baos = ByteArrayOutputStream()
        baos.write(byteArrayOf(0x41, 0x42, 0x01, 0x00, 0x00, 0x01, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00))
        domain.trim().trimEnd('.').split('.').filter { it.isNotBlank() }.forEach { label ->
            val b = label.toByteArray(Charsets.US_ASCII)
            baos.write(b.size)
            baos.write(b)
        }
        baos.write(0x00)
        baos.write(byteArrayOf(((qType ushr 8) and 0xFF).toByte(), (qType and 0xFF).toByte(), 0x00, 0x01))
        val query = baos.toByteArray()
        val rxBuf = ByteArray(512)
        val packet = DatagramPacket(rxBuf, rxBuf.size)
        val t0 = System.nanoTime()
        DatagramSocket().use { s ->
            s.soTimeout = 3000
            s.send(DatagramPacket(query, query.size, InetAddress.getByName(hostIp), 53))
            s.receive(packet)
        }
        val rcode = if (packet.length >= 4) (rxBuf[3].toInt() and 0x0F) else -1
        ((System.nanoTime() - t0) / 1_000_000L).toInt().coerceAtLeast(1) to rcode
    }

    suspend fun runCdnScan(
        provider: String,
        cidrResource: String,
        customTargets: String,
        sni: String,
        count: Int,
        style: ScanStyle = ScanStyle.TURBO_FAST,
        onProgress: (JvmScanProgress) -> Unit,
        onResult: (JvmScanCandidateResult) -> Unit,
    ): List<JvmScanCandidateResult> {
        val cidrs = if (customTargets.isNotBlank()) {
            customTargets.lines()
        } else {
            coreLocator.readBundledResourceText("scanner/$cidrResource").lines()
        }
        val ips = JvmCdnScanner.expandCidrs(cidrs, count.coerceIn(10, 400))
        val ports = when {
            provider.equals("warp", ignoreCase = true) && style == ScanStyle.DEEP_MULTIPORT -> listOf(443, 8443, 4443, 2408)
            provider.equals("warp", ignoreCase = true) -> listOf(2408)
            style == ScanStyle.DEEP_MULTIPORT -> style.ports
            else -> listOf(443)
        }
        return JvmCdnScanner.scanCandidates(
            ips = ips,
            ports = ports,
            provider = provider,
            sni = sni,
            style = style,
            testTls = true,
            measureColoAndSpeed = style != ScanStyle.TURBO_FAST,
            onProgress = onProgress,
            onResult = onResult,
        )
    }

    fun disconnect() {
        userRequestedDisconnect = true
        watchdogJob?.cancel()
        watchdogJob = null
        connectJob?.cancel()
        connectJob = null
        disconnectSync(updateState = true)
    }

    private fun isRetryableConnectionError(err: ConnectionError): Boolean =
        err !is ConnectionError.MissingBinary && err !is ConnectionError.PermissionDenied

    private fun activeNetworkSignature(): String = runCatching {
        NetworkInterface.getNetworkInterfaces()
            .asSequence()
            .filter { iface ->
                iface.isUp && !iface.isLoopback && !iface.isVirtual &&
                    !iface.name.contains("wintun", ignoreCase = true) &&
                    !iface.displayName.contains("wintun", ignoreCase = true) &&
                    !iface.displayName.contains("pnifox", ignoreCase = true)
            }
            .flatMap { iface ->
                iface.inetAddresses.asSequence()
                    .filterIsInstance<Inet4Address>()
                    .map { "${iface.name}:${it.hostAddress}" }
                    .filterNot { it.contains("172.19.") || it.contains("169.254.") }
            }
            .sorted()
            .joinToString("|")
    }.getOrDefault("")

    private fun startConnectionWatchdog(section: ActiveSectionEngine) {
        watchdogJob?.cancel()
        watchdogJob = scope.launch {
            var lastSig = activeNetworkSignature()
            var networkWasOffline = lastSig.isEmpty()
            var consecutiveProbesFailed = 0
            var probeTickCounter = 0

            while (isActive && !userRequestedDisconnect) {
                delay(4000L)
                if (userRequestedDisconnect) break
                val settings = store.stateFlow.value.appSettings
                if (!settings.autoReconnectOnNetworkChange) continue

                val state = _tunnelState.value
                if (state is TunnelState.Connecting) continue
                if (state is TunnelState.Disconnected) break

                val curSig = activeNetworkSignature()
                if (curSig.isEmpty()) {
                    if (!networkWasOffline) {
                        networkWasOffline = true
                        logs.append("Watchdog", LogLevel.WARN, "Internet/network adapter went offline — waiting for network recovery to refresh tunnel...")
                        _tunnelState.value = TunnelState.Connecting(
                            section = section,
                            stageLabelEn = "Network offline — waiting to auto-reconnect...",
                            stageLabelFa = "اینترنت قطع شد — در انتظار بازگشت برای اتصال مجدد خودکار...",
                            targetLabel = "",
                        )
                    }
                    continue
                }

                if (networkWasOffline || (lastSig.isNotEmpty() && curSig != lastSig)) {
                    val reason = if (networkWasOffline) "Internet restored ($curSig)" else "Network switched ($lastSig -> $curSig)"
                    logs.append("Watchdog", LogLevel.INFO, "$reason — refreshing tunnel automatically...")
                    if (section == ActiveSectionEngine.AETHER && store.stateFlow.value.aetherSettings.endpointIp.isNotBlank()) {
                        store.updateAetherSettings { it.copy(scanForEndpoint = true, endpointIp = "") }
                    }
                    connectJob?.cancel()
                    connectJob = scope.launch { executeConnection(section) }
                    return@launch
                }
                lastSig = curSig

                val expectedGateway = (section != ActiveSectionEngine.MHR || !store.stateFlow.value.mhrSettings.proxyOnly)
                val coreDead = coreProcess?.let { !it.isAlive } ?: false
                val gwDead = expectedGateway && (gatewayProcess?.let { !it.isAlive } ?: false)
                if (coreDead || gwDead) {
                    logs.append("Watchdog", LogLevel.WARN, "Core/Gateway process exited unexpectedly — auto-reconnecting...")
                    if (section == ActiveSectionEngine.AETHER) {
                        store.updateAetherSettings { it.copy(scanForEndpoint = true, endpointIp = "") }
                    }
                    connectJob?.cancel()
                    connectJob = scope.launch { executeConnection(section) }
                    return@launch
                }

                // Periodic light check (~24s) to recover seamlessly from silent ISP drop while adapter stays up
                probeTickCounter++
                if (probeTickCounter >= 6 && state is TunnelState.Connected) {
                    probeTickCounter = 0
                    val ms = refreshActiveRealDelay()
                    if (ms == null) {
                        consecutiveProbesFailed++
                        if (consecutiveProbesFailed >= 2) {
                            logs.append("Watchdog", LogLevel.WARN, "Tunnel stalled across consecutive checks while internet is UP — refreshing connection...")
                            if (section == ActiveSectionEngine.AETHER) {
                                store.updateAetherSettings { it.copy(scanForEndpoint = true, endpointIp = "") }
                            }
                            connectJob?.cancel()
                            connectJob = scope.launch { executeConnection(section) }
                            return@launch
                        }
                    } else {
                        consecutiveProbesFailed = 0
                    }
                }
            }
        }
    }

    private fun scheduleAutoRecovery(section: ActiveSectionEngine, reason: String) {
        watchdogJob?.cancel()
        watchdogJob = scope.launch {
            var attempt = 0
            while (isActive && !userRequestedDisconnect && store.stateFlow.value.appSettings.autoReconnectOnNetworkChange) {
                attempt++
                val sig = activeNetworkSignature()
                if (sig.isEmpty()) {
                    _tunnelState.value = TunnelState.Connecting(
                        section = section,
                        stageLabelEn = "Waiting for internet to return before reconnecting...",
                        stageLabelFa = "در انتظار برقراری اینترنت برای اتصال مجدد خودکار...",
                        targetLabel = "",
                    )
                    delay(3000L)
                    continue
                }
                val waitMs = when (attempt) {
                    1 -> 3000L
                    2 -> 7000L
                    3 -> 15000L
                    else -> 25000L
                }
                logs.append("Watchdog", LogLevel.INFO, "Auto-reconnect attempt #$attempt in ${waitMs / 1000}s (last reason: ${reason.take(80)})")
                delay(waitMs)
                if (userRequestedDisconnect) return@launch
                if (section == ActiveSectionEngine.AETHER) {
                    store.updateAetherSettings { it.copy(scanForEndpoint = true, endpointIp = "") }
                }
                executeConnection(section)
                if (_tunnelState.value is TunnelState.Connected) return@launch
                if (attempt >= 5) {
                    logs.append("Watchdog", LogLevel.WARN, "Paused auto-retries after 5 attempts; will auto-retry when network changes.")
                    var baseline = activeNetworkSignature()
                    while (isActive && !userRequestedDisconnect && store.stateFlow.value.appSettings.autoReconnectOnNetworkChange) {
                        delay(4000L)
                        val nowSig = activeNetworkSignature()
                        if (nowSig.isNotEmpty() && nowSig != baseline) {
                            logs.append("Watchdog", LogLevel.INFO, "Network changed ($nowSig) — resuming auto-reconnect...")
                            connectJob?.cancel()
                            connectJob = scope.launch { executeConnection(section) }
                            return@launch
                        }
                        if (nowSig.isEmpty()) baseline = ""
                    }
                    return@launch
                }
            }
        }
    }

    fun disconnectSync(updateState: Boolean = true) {
        trafficJob?.cancel()
        trafficJob = null
        activeHttpGatewayPort = null
        activeSocksGatewayPort = null
        killProcessTree(gatewayProcess)
        gatewayProcess = null
        if (systemProxy.isApplied) {
            systemProxy.restore()
            logs.append("SystemProxy", LogLevel.INFO, "Restored Windows System Proxy settings.")
        }
        killProcessTree(coreProcess)
        coreProcess = null
        killProcessTree(secondaryProcess)
        secondaryProcess = null
        localDesyncServer?.stop()
        localDesyncServer = null
        spoofServer.stop()
        WindowsSecurityHelper.cleanupTempConfigs(store.runtimeDir, store.mhrDir)
        store.flushState()
        _rxSpeedBps.value = 0L
        _txSpeedBps.value = 0L
        if (updateState) {
            _tunnelState.value = TunnelState.Disconnected
        }
    }

    private fun resolveGatewaySocksPort(configured: Int, mhr: MhrSettings): Int {
        var port = configured.coerceIn(1024, 65535)
        while (port == 19090 || port == mhr.listenPort || port == mhr.socks5Port) {
            port++
        }
        return port
    }

    private fun resolveGatewayHttpPort(configuredHttp: Int, socksPort: Int, mhr: MhrSettings): Int {
        var port = configuredHttp.coerceIn(1024, 65535)
        while (port == socksPort || port == 19090 || port == mhr.listenPort || port == mhr.socks5Port) {
            port++
        }
        return port
    }

    private fun pickInternalCoreSocksPort(gatewaySocksPort: Int, gatewayHttpPort: Int): Int {
        var candidate = 10818
        while (candidate == gatewaySocksPort || candidate == gatewayHttpPort || candidate == 19090 || candidate == 40443) {
            candidate++
        }
        return candidate
    }

    private fun killProcessTree(proc: Process?) {
        if (proc == null) return
        runCatching {
            proc.descendants().forEach { h -> runCatching { h.destroyForcibly() } }
            proc.destroyForcibly()
        }
    }

    private fun resolveEngine(
        section: ActiveSectionEngine,
        profile: ProxyProfile?,
        dns: DnsTunnelSettings,
        gaming: GamingSettings = GamingSettings(),
    ): CoreEngine =
        when (section) {
            ActiveSectionEngine.AETHER -> CoreEngine.AETHER
            ActiveSectionEngine.GAMING -> when (gaming.mode) {
                GamingMode.DNS_ANTI_SANCTION -> CoreEngine.XRAY
                GamingMode.LOCAL_DPI_DESYNC -> CoreEngine.LOCAL_DESYNC
                GamingMode.AETHER_UDP_QUIC -> CoreEngine.AETHER
                GamingMode.CUSTOM_SERVER -> resolveEngine(ActiveSectionEngine.SERVER, profile, dns, gaming)
            }
            ActiveSectionEngine.MHR -> CoreEngine.MHR
            ActiveSectionEngine.DNS -> when (dns.engineType) {
                DnsEngineType.AUTO_TUNNEL -> CoreEngine.AETHER
                DnsEngineType.DNS_CHANGER -> CoreEngine.XRAY
                DnsEngineType.CUSTOM_DNSTT -> CoreEngine.DNSTT
            }
            ActiveSectionEngine.SERVER -> when {
                profile?.rawUri?.trimStart()?.startsWith("{") == true -> CoreEngine.XRAY
                profile?.protocol == ProxyProtocol.HYSTERIA2 ||
                    profile?.protocol == ProxyProtocol.WIREGUARD ||
                    profile?.protocol == ProxyProtocol.TUIC ||
                    profile?.protocol == ProxyProtocol.ANYTLS -> CoreEngine.SING_BOX
                else -> CoreEngine.XRAY
            }
        }

    private suspend fun startGamingDns(
        gaming: GamingSettings,
        app: AppSettings,
        coreSocksPort: Int,
        onLastLine: (String) -> Unit,
    ): ConnectionError? {
        val xrayExe = coreLocator.binaryFile("xray.exe")
        if (!xrayExe.exists()) return ConnectionError.MissingBinary("xray.exe", xrayExe.absolutePath, "Run scripts/fetch-windows-cores.ps1")
        val hasGeo = coreLocator.ensureGeoAssets(store.geoDir)
        val innerApp = app.copy(
            allowLan = false,
            bypassIranEnabled = gaming.bypassIranGameTraffic,
        )
        val dnsStub = DnsTunnelSettings().copy(
            engineType = DnsEngineType.DNS_CHANGER,
            mode = DnsTunnelMode.UDP,
            resolverAddress = gaming.selectedDnsIp.ifBlank { "10.202.10.10" },
        )
        val cfg = File(store.runtimeDir, "xray-gaming-dns.json")
        cfg.writeText(
            ConfigBuilders.buildDnsChangerXrayConfig(dnsStub, innerApp, coreSocksPort, null, hasGeo, exposeLocalHttpProxy = false),
            Charsets.UTF_8,
        )
        val pb = ProcessBuilder(xrayExe.absolutePath, "run", "-c", cfg.absolutePath)
            .directory(xrayExe.parentFile)
            .redirectErrorStream(true)
        pb.environment()["XRAY_LOCATION_ASSET"] = store.geoDir.absolutePath
        val proc = pb.start()
        coreProcess = proc
        pumpProcessOutput("Gaming-DNS", proc, onLastLine)
        return awaitSocksReady("Gaming-DNS", proc, coreSocksPort, timeoutMs = 10_000L)
    }

    private fun startLocalDesync(
        gaming: GamingSettings,
        coreSocksPort: Int,
    ): ConnectionError? {
        val srv = LocalDpiDesyncServer(
            listenPort = coreSocksPort,
            bindHost = "127.0.0.1",
            fragmentChunkSize = gaming.desyncChunkLen,
            fragmentDelayMs = 2L,
            preferCleanDns = gaming.selectedDnsIp.substringBefore(':').ifBlank { "1.1.1.1" },
            onLog = { msg -> logs.append("DPI-Desync", LogLevel.INFO, msg) },
        )
        srv.start()
        localDesyncServer = srv
        return if (probeSocks5Greeting("127.0.0.1", coreSocksPort)) {
            null
        } else {
            ConnectionError.HandshakeTimeout("Local DPI Desync failed to bind :$coreSocksPort")
        }
    }

    suspend fun scanDnsE2E(
        extraCsv: String = "",
        onProgress: ((Int, Int) -> Unit)? = null,
    ): List<DnsE2EResult> = withContext(Dispatchers.IO) {
        val results = DnsE2EScanner.scanResolvers(
            extraIpsCsv = extraCsv,
            onProgress = onProgress,
        )
        val cleanCount = results.count { it.isReachable && !it.isPoisoned }
        val poisonedCount = results.count { it.isPoisoned }
        logs.append("DNS-E2E", LogLevel.INFO, "Zero-ICMP Port-53 scan complete: $cleanCount clean, $poisonedCount poisoned (10.10.34.x).")
        results
    }
}
