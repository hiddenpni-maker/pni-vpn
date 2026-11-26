package ir.pnifox.desktop.core

import ir.pnifox.core.model.ActiveSectionEngine
import ir.pnifox.core.model.AetherIdentityTarget
import ir.pnifox.core.model.AetherSettings
import ir.pnifox.core.model.AppSettings
import ir.pnifox.core.model.ConnectionError
import ir.pnifox.core.model.CoreEngine
import ir.pnifox.core.model.DnsEngineType
import ir.pnifox.core.model.DnsInnerProtocol
import ir.pnifox.core.model.DnsTunnelSettings
import ir.pnifox.core.model.LogLevel
import ir.pnifox.core.model.MhrDefaultSniPool
import ir.pnifox.core.model.MhrMode
import ir.pnifox.core.model.MhrSettings
import ir.pnifox.core.model.MhrSniTestResult
import ir.pnifox.core.model.ProxyProfile
import ir.pnifox.core.model.ProxyProtocol
import ir.pnifox.core.model.TunnelState
import ir.pnifox.core.vpn.AetherArgBuilder
import ir.pnifox.core.vpn.ConfigBuilders
import ir.pnifox.core.vpn.JvmCdnScanner
import ir.pnifox.core.vpn.JvmScanCandidateResult
import ir.pnifox.core.vpn.JvmScanProgress
import ir.pnifox.core.vpn.LogRingBuffer
import ir.pnifox.core.vpn.MhrConfigBuilder
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
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.ByteArrayOutputStream
import java.io.File
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.Inet4Address
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.Socket
import java.util.concurrent.TimeUnit
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

    private val _binaries = MutableStateFlow<List<WindowsBinaryStatus>>(emptyList())
    val binaries: StateFlow<List<WindowsBinaryStatus>> = _binaries.asStateFlow()

    @Volatile private var connectJob: Job? = null
    @Volatile private var trafficJob: Job? = null
    @Volatile private var coreProcess: Process? = null
    @Volatile private var secondaryProcess: Process? = null
    @Volatile private var tunProcess: Process? = null

    init {
        if (systemProxy.restoreIfStaleOnStartup()) {
            logs.append("SystemProxy", LogLevel.INFO, "Restored Windows system proxy settings from previous session.")
        }
        scope.launch {
            _isAdmin.value = WindowsPrivilegeHelper.isAdministrator()
            _binaries.value = coreLocator.inspectAll()
        }
        Runtime.getRuntime().addShutdownHook(
            Thread {
                disconnectSync()
            },
        )
    }

    fun refreshEnvironment() {
        scope.launch {
            _isAdmin.value = WindowsPrivilegeHelper.isAdministrator()
            _binaries.value = coreLocator.inspectAll()
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
        store.updateAppSettings { it.copy(activeSection = section) }
        val persisted = store.stateFlow.value
        val app = persisted.appSettings
        val aether = persisted.aetherSettings
        val dns = persisted.dnsSettings
        val mhr = persisted.mhrSettings
        val tunMode = persisted.windowsTunnelMode
        val profile = persisted.profiles.firstOrNull { it.id == app.activeProfileId }

        connectJob?.cancel()
        connectJob = scope.launch {
            disconnectSync(updateState = false)
            _sessionRxBytes.value = 0L
            _sessionTxBytes.value = 0L
            _rxSpeedBps.value = 0L
            _txSpeedBps.value = 0L

            if (tunMode == WindowsTunnelMode.TUN_WINTUN && !WindowsPrivilegeHelper.isAdministrator()) {
                _tunnelState.value = TunnelState.Error(
                    section,
                    ConnectionError.PermissionDenied(),
                )
                logs.append("TUN", LogLevel.ERROR, "TUN (Wintun) mode requires Administrator privileges. Click 'Restart as Admin' or switch to System Proxy.")
                return@launch
            }

            if (section == ActiveSectionEngine.SERVER && profile == null) {
                _tunnelState.value = TunnelState.Error(
                    section,
                    ConnectionError.ProcessExited(-1, "Select or import a server profile first."),
                )
                return@launch
            }

            val engine = resolveEngine(section, profile, dns)
            _tunnelState.value = TunnelState.Connecting(
                section = section,
                stageLabelEn = "Starting ${engine.displayEn}...",
                stageLabelFa = "در حال راه‌اندازی ${engine.displayFa}...",
                targetLabel = "127.0.0.1:${aether.localSocksPort}",
            )
            logs.append("Controller", LogLevel.INFO, "Starting Windows tunnel (${tunMode.labelEn}) section=${section.titleEn}")

            val bypassIps = mutableListOf<String>()
            var activeEndpointLabel = ""
            var lastLogLine = ""

            val err: ConnectionError? = when (section) {
                ActiveSectionEngine.AETHER -> {
                    activeEndpointLabel = if (aether.scanForEndpoint || aether.endpointIp.isBlank()) "Auto-Scanned WARP" else "${aether.endpointIp}:${aether.endpointPort}"
                    if (aether.endpointIp.isNotBlank()) bypassIps += aether.endpointIp
                    startAether(app, aether, onStage = { en, fa ->
                        _tunnelState.value = TunnelState.Connecting(section, en, fa, "127.0.0.1:${aether.localSocksPort}")
                    }, onDiscovered = { ip, port ->
                        bypassIps += ip
                        activeEndpointLabel = "$ip:$port"
                        if (aether.scanForEndpoint || aether.endpointIp.isBlank()) {
                            store.updateAetherSettings { cur -> cur.copy(endpointIp = ip, endpointPort = port, scanForEndpoint = false) }
                            logs.append("AETHER", LogLevel.INFO, "Saved endpoint $ip:$port for future fast connects")
                        }
                    }, onLastLine = { lastLogLine = it })
                }
                ActiveSectionEngine.SERVER -> {
                    val p = profile!!
                    bypassIps += p.address
                    activeEndpointLabel = "${p.address}:${p.port}"
                    startCustomServer(p, app, aether.localSocksPort) { lastLogLine = it }
                }
                ActiveSectionEngine.MHR -> {
                    bypassIps += mhr.googleIp
                    activeEndpointLabel = "${mhr.mode.labelEn} • ${mhr.googleIp}"
                    startMhr(app, aether, mhr) { lastLogLine = it }
                }
                ActiveSectionEngine.DNS -> {
                    activeEndpointLabel = when (dns.engineType) {
                        DnsEngineType.AUTO_TUNNEL -> "Auto DNS • ${dns.resolverAddress}"
                        DnsEngineType.DNS_CHANGER -> "DNS Changer • ${dns.resolverAddress}"
                        DnsEngineType.CUSTOM_DNSTT -> "${dns.nameserver} (${dns.innerProtocol.label})"
                    }
                    startDns(dns, app, aether) { lastLogLine = it }
                }
            }

            if (err != null) {
                disconnectSync(updateState = false)
                _tunnelState.value = TunnelState.Error(section, err)
                logs.append("Controller", LogLevel.ERROR, "Core failed: ${lastLogLine.ifBlank { err.toString() }}")
                return@launch
            }

            // Apply Windows System Proxy OR Wintun TUN bridge
            if (tunMode == WindowsTunnelMode.TUN_WINTUN) {
                val sbExe = coreLocator.binaryFile("sing-box.exe")
                val wintun = coreLocator.binaryFile("wintun.dll")
                if (!sbExe.exists() || !wintun.exists()) {
                    disconnectSync(updateState = false)
                    _tunnelState.value = TunnelState.Error(
                        section,
                        ConnectionError.MissingBinary("sing-box.exe / wintun.dll", sbExe.absolutePath, "Run scripts/fetch-windows-cores.ps1"),
                    )
                    return@launch
                }
                val tunCfgFile = File(store.runtimeDir, "win-tun-bridge.json")
                tunCfgFile.writeText(
                    WindowsTunConfigBuilder.buildTunBridgeConfig(
                        appSettings = app,
                        socksPort = aether.localSocksPort,
                        clashApiPort = 19090,
                        bypassTargetIps = bypassIps,
                    ),
                    Charsets.UTF_8,
                )
                val pb = ProcessBuilder(sbExe.absolutePath, "run", "-c", tunCfgFile.absolutePath)
                    .directory(sbExe.parentFile)
                    .redirectErrorStream(true)
                val tProc = pb.start()
                tunProcess = tProc
                pumpProcessOutput("WinTUN", tProc) { lastLogLine = it }
                delay(700L)
                if (!tProc.isAlive) {
                    disconnectSync(updateState = false)
                    _tunnelState.value = TunnelState.Error(
                        section,
                        ConnectionError.ProcessExited(-1, "Windows Wintun failed to start (run as Administrator). $lastLogLine"),
                    )
                    return@launch
                }
                startClashTrafficMonitor(clashPort = 19090)
                logs.append("WinTUN", LogLevel.INFO, "Wintun adapter 'pnifox-tun' active for all Windows apps & games.")
            } else {
                val httpPort = if (section == ActiveSectionEngine.MHR && !app.allowLan) mhr.listenPort else app.httpProxyPort
                systemProxy.apply(httpPort = httpPort, bypassIran = app.bypassIranEnabled).onFailure { ex ->
                    logs.append("SystemProxy", LogLevel.WARN, "Registry proxy apply note: ${ex.message}")
                }.onSuccess {
                    logs.append("SystemProxy", LogLevel.INFO, "Windows System Proxy set to 127.0.0.1:$httpPort (SOCKS5 :${aether.localSocksPort})")
                }
            }

            val connected = TunnelState.Connected(
                section = section,
                engine = engine,
                localSocksPort = aether.localSocksPort,
                activeEndpoint = activeEndpointLabel,
                resolvedIp = bypassIps.firstOrNull().orEmpty(),
                pingMs = profile?.lastPingMs,
                connectedAtEpochMs = System.currentTimeMillis(),
            )
            _tunnelState.value = connected

            delay(900L)
            refreshActiveRealDelay()
        }
    }

    private suspend fun startAether(
        app: AppSettings,
        aether: AetherSettings,
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
        val routesFile = argBuilder.writeRoutesFileIfNeeded(app, dir)
        val args = argBuilder.buildRunArgs(
            appSettings = app,
            aether = aether,
            nativeDir = coreLocator.resolveCoresDirectory().absolutePath,
            workDir = dir,
            psiphonServersDatPath = null,
            routesFile = routesFile,
            exposeLocalHttpProxy = true,
        ).toMutableList()

        // Ensure Windows binaries on PATH for psiphon/tor
        val cmd = listOf(bin.absolutePath) + args
        logs.append("AETHER", LogLevel.INFO, "Launching: ${cmd.joinToString(" ")}")
        onStage("AETHER handshaking (MASQUE / WARP)...", "در حال برقراری اتصال AETHER...")

        val pb = ProcessBuilder(cmd).directory(bin.parentFile).redirectErrorStream(true)
        val curPath = pb.environment()["PATH"].orEmpty()
        pb.environment()["PATH"] = "${bin.parentFile.absolutePath};$curPath"
        val proc = pb.start()
        coreProcess = proc

        val peerPattern = Regex("""(\d{1,3}(?:\.\d{1,3}){3}):(\d+)""")
        pumpProcessOutput("AETHER", proc) { line ->
            onLastLine(line)
            if (line.contains("registering", ignoreCase = true) || line.contains("no warp identity", ignoreCase = true)) {
                onStage("Registering WARP identity...", "در حال ثبت هویت WARP...")
            } else if (line.contains("scanning", ignoreCase = true)) {
                onStage("Scanning clean WARP endpoints...", "در حال اسکن اندپوینت تمیز...")
            }
            if (line.contains("using gateway", ignoreCase = true) ||
                line.contains("selected endpoint", ignoreCase = true) ||
                line.contains("connected", ignoreCase = true)
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
        return awaitSocksReady("AETHER", proc, aether.localSocksPort, timeoutMs = 90_000L)
    }

    private suspend fun startCustomServer(
        profile: ProxyProfile,
        app: AppSettings,
        socksPort: Int,
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

        val isRawJson = profile.rawUri.trimStart().startsWith("{")
        if (!isRawJson && (profile.protocol == ProxyProtocol.HYSTERIA2 || profile.protocol == ProxyProtocol.WIREGUARD)) {
            val sbExe = coreLocator.binaryFile("sing-box.exe")
            if (!sbExe.exists()) return ConnectionError.MissingBinary("sing-box.exe", sbExe.absolutePath, "Run scripts/fetch-windows-cores.ps1")
            val cfg = File(store.runtimeDir, "singbox-server.json")
            cfg.writeText(ConfigBuilders.buildSingBoxConfig(profile, app, socksPort, exposeLocalHttpProxy = true), Charsets.UTF_8)
            val proc = ProcessBuilder(sbExe.absolutePath, "run", "-c", cfg.absolutePath)
                .directory(sbExe.parentFile)
                .redirectErrorStream(true)
                .start()
            coreProcess = proc
            pumpProcessOutput("Sing-box", proc, onLastLine)
            return awaitSocksReady("Sing-box", proc, socksPort, timeoutMs = 15_000L)
        }

        val xrayExe = coreLocator.binaryFile("xray.exe")
        if (!xrayExe.exists()) return ConnectionError.MissingBinary("xray.exe", xrayExe.absolutePath, "Run scripts/fetch-windows-cores.ps1")
        val hasGeo = coreLocator.ensureGeoAssets(store.geoDir)
        val cfg = File(store.runtimeDir, "xray-server.json")
        cfg.writeText(
            ConfigBuilders.buildXrayConfig(
                profile = profile,
                appSettings = app,
                socksPort = socksPort,
                errorLogPath = null,
                hasGeoAssets = hasGeo,
                exposeLocalHttpProxy = true,
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
        return awaitSocksReady("Xray", proc, socksPort, timeoutMs = 12_000L)
    }

    private suspend fun startMhr(
        app: AppSettings,
        aether: AetherSettings,
        mhr: MhrSettings,
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
        val cfgFile = File(store.mhrDir, "config.json")
        cfgFile.writeText(MhrConfigBuilder.buildConfigJson(app, aether, mhr), Charsets.UTF_8)
        val pb = ProcessBuilder(mhrExe.absolutePath, "-c", cfgFile.absolutePath, "--no-cert-check")
            .directory(store.mhrDir)
            .redirectErrorStream(true)
        pb.environment()["RUST_LOG"] = mhr.logLevel.ifBlank { "info" }
        val proc = pb.start()
        coreProcess = proc
        pumpProcessOutput("MHR", proc, onLastLine)
        return awaitSocksReady("MHR", proc, aether.localSocksPort, timeoutMs = 10_000L)
    }

    private suspend fun startDns(
        dns: DnsTunnelSettings,
        app: AppSettings,
        aether: AetherSettings,
        onLastLine: (String) -> Unit,
    ): ConnectionError? {
        return when (dns.engineType) {
            DnsEngineType.AUTO_TUNNEL -> {
                val dnsIp = argBuilder.extractAetherDnsServer(dns)
                val tunedAether = aether.copy(masqueTransport = ir.pnifox.core.model.MasqueTransport.HTTP2)
                val tunedApp = app.copy(localDns = dnsIp, activePreset = ir.pnifox.core.model.OneTapPreset.WARP_FAST_MASQUE_H2)
                startAether(tunedApp, tunedAether, onStage = { _, _ -> }, onDiscovered = { _, _ -> }, onLastLine = onLastLine)
            }
            DnsEngineType.DNS_CHANGER -> {
                val xrayExe = coreLocator.binaryFile("xray.exe")
                if (!xrayExe.exists()) return ConnectionError.MissingBinary("xray.exe", xrayExe.absolutePath, "Run scripts/fetch-windows-cores.ps1")
                val hasGeo = coreLocator.ensureGeoAssets(store.geoDir)
                val cfg = File(store.runtimeDir, "xray-dnschanger.json")
                cfg.writeText(
                    ConfigBuilders.buildDnsChangerXrayConfig(dns, app, aether.localSocksPort, null, hasGeo, exposeLocalHttpProxy = true),
                    Charsets.UTF_8,
                )
                val pb = ProcessBuilder(xrayExe.absolutePath, "run", "-c", cfg.absolutePath)
                    .directory(xrayExe.parentFile)
                    .redirectErrorStream(true)
                pb.environment()["XRAY_LOCATION_ASSET"] = store.geoDir.absolutePath
                val proc = pb.start()
                coreProcess = proc
                pumpProcessOutput("DNS-Xray", proc, onLastLine)
                awaitSocksReady("DNS-Xray", proc, aether.localSocksPort, timeoutMs = 10_000L)
            }
            DnsEngineType.CUSTOM_DNSTT -> {
                val dnsttExe = coreLocator.binaryFile("dnstt-client.exe")
                if (!dnsttExe.exists()) return ConnectionError.MissingBinary("dnstt-client.exe", dnsttExe.absolutePath, "Run scripts/fetch-windows-cores.ps1")
                val directSocks = dns.innerProtocol == DnsInnerProtocol.SOCKS5
                val dnsttListenPort = if (directSocks) aether.localSocksPort else aether.localSocksPort + 3
                val dnsttArgs = argBuilder.buildDnsttArgs(dns, localSocksPort = dnsttListenPort)
                val dnsttProc = ProcessBuilder(listOf(dnsttExe.absolutePath) + dnsttArgs)
                    .directory(dnsttExe.parentFile)
                    .redirectErrorStream(true)
                    .start()
                if (directSocks) {
                    coreProcess = dnsttProc
                    pumpProcessOutput("DNSTT", dnsttProc, onLastLine)
                    awaitSocksReady("DNSTT", dnsttProc, aether.localSocksPort, timeoutMs = 12_000L)
                } else {
                    secondaryProcess = dnsttProc
                    pumpProcessOutput("DNSTT", dnsttProc, onLastLine)
                    val innerErr = awaitSocksReady("DNSTT-Inner", dnsttProc, dnsttListenPort, timeoutMs = 10_000L)
                    if (innerErr != null) return innerErr
                    val sbExe = coreLocator.binaryFile("sing-box.exe")
                    if (!sbExe.exists()) return ConnectionError.MissingBinary("sing-box.exe", sbExe.absolutePath, "Run scripts/fetch-windows-cores.ps1")
                    val cfg = File(store.runtimeDir, "singbox-dnstt-ssh.json")
                    cfg.writeText(
                        ConfigBuilders.buildSingBoxDnsttSshConfig(dns, app, dnsttListenPort, aether.localSocksPort, exposeLocalHttpProxy = true),
                        Charsets.UTF_8,
                    )
                    val sbProc = ProcessBuilder(sbExe.absolutePath, "run", "-c", cfg.absolutePath)
                        .directory(sbExe.parentFile)
                        .redirectErrorStream(true)
                        .start()
                    coreProcess = sbProc
                    pumpProcessOutput("DNSTT-SSH", sbProc, onLastLine)
                    awaitSocksReady("DNSTT-SSH", sbProc, aether.localSocksPort, timeoutMs = 15_000L)
                }
            }
        }
    }

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
            val open = runCatching {
                Socket().use { s -> s.connect(InetSocketAddress("127.0.0.1", socksPort), 250) }
                true
            }.getOrDefault(false)
            if (open) {
                logs.append(tag, LogLevel.INFO, "$tag ready on 127.0.0.1:$socksPort")
                return null
            }
            delay(250L)
        }
        return ConnectionError.HandshakeTimeout("$tag did not open 127.0.0.1:$socksPort within ${timeoutMs / 1000}s")
    }

    private fun pumpProcessOutput(tag: String, proc: Process, onLine: (String) -> Unit) {
        scope.launch {
            runCatching {
                proc.inputStream.bufferedReader().use { br ->
                    var line = br.readLine()
                    while (line != null && isActive) {
                        val trimmed = line.trim()
                        if (trimmed.isNotEmpty()) {
                            onLine(trimmed)
                            val lvl = when {
                                trimmed.contains("ERROR", ignoreCase = true) || trimmed.contains("FATAL", ignoreCase = true) -> LogLevel.ERROR
                                trimmed.contains("WARN", ignoreCase = true) -> LogLevel.WARN
                                else -> LogLevel.INFO
                            }
                            logs.append(tag, lvl, trimmed)
                        }
                        line = br.readLine()
                    }
                }
            }
        }
    }

    private fun startClashTrafficMonitor(clashPort: Int) {
        trafficJob?.cancel()
        trafficJob = scope.launch {
            val req = Request.Builder().url("http://127.0.0.1:$clashPort/traffic").build()
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
                delay(1500L)
            }
        }
    }

    suspend fun refreshActiveRealDelay(): Int? = withContext(Dispatchers.IO) {
        val connected = _tunnelState.value as? TunnelState.Connected
        if (connected == null) {
            null
        } else {
            val persisted = store.stateFlow.value
            val rtt = if (connected.section == ActiveSectionEngine.DNS && persisted.dnsSettings.engineType == DnsEngineType.DNS_CHANGER) {
                RealDelaySocketProber.measureDnsResolverLatencyMs(persisted.dnsSettings)
            } else {
                RealDelaySocketProber.probeHttp204ViaSocks5("127.0.0.1", connected.localSocksPort)
            }
            val cur = _tunnelState.value
            if (cur is TunnelState.Connected) {
                _tunnelState.value = cur.copy(pingMs = rtt)
            }
            if (rtt != null) {
                logs.append("RealDelay", LogLevel.INFO, "Measured Real HTTP 204 delay: ${rtt}ms")
            } else {
                logs.append("RealDelay", LogLevel.WARN, "Real delay probe timed out on :${connected.localSocksPort}")
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
                    if (l.isNotBlank()) logs.append("AETHER-Reg", LogLevel.INFO, l.trim())
                }
                proc.waitFor(45, TimeUnit.SECONDS)
                if (proc.exitValue() != 0) error("Registration exited with code ${proc.exitValue()}")
                store.updateAetherSettings { it.copy(masqueAccountRegistered = true) }
                Unit
            }
        }
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
                "MITM CA Certificate installed in Windows Trust Store ✓"
            }
        }
    }

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
        onProgress: (JvmScanProgress) -> Unit,
        onResult: (JvmScanCandidateResult) -> Unit,
    ): List<JvmScanCandidateResult> {
        val cidrs = if (customTargets.isNotBlank()) {
            customTargets.lines()
        } else {
            coreLocator.readBundledResourceText("scanner/$cidrResource").lines()
        }
        val ips = JvmCdnScanner.expandCidrs(cidrs, count.coerceIn(10, 400))
        val port = if (provider.equals("warp", ignoreCase = true)) 2408 else 443
        return JvmCdnScanner.scanIps(
            ips = ips,
            port = port,
            provider = provider,
            sni = sni,
            attempts = 2,
            timeoutMs = 2500,
            concurrency = 24,
            onProgress = onProgress,
            onResult = onResult,
        )
    }

    fun disconnect() {
        connectJob?.cancel()
        connectJob = null
        disconnectSync(updateState = true)
    }

    fun disconnectSync(updateState: Boolean = true) {
        trafficJob?.cancel()
        trafficJob = null
        killProcessTree(tunProcess)
        tunProcess = null
        if (systemProxy.isApplied) {
            systemProxy.restore()
            logs.append("SystemProxy", LogLevel.INFO, "Restored Windows System Proxy settings.")
        }
        killProcessTree(coreProcess)
        coreProcess = null
        killProcessTree(secondaryProcess)
        secondaryProcess = null
        spoofServer.stop()
        _rxSpeedBps.value = 0L
        _txSpeedBps.value = 0L
        if (updateState) {
            _tunnelState.value = TunnelState.Disconnected
        }
    }

    private fun killProcessTree(proc: Process?) {
        if (proc == null) return
        runCatching {
            proc.descendants().forEach { h -> runCatching { h.destroyForcibly() } }
            proc.destroyForcibly()
        }
    }

    private fun resolveEngine(section: ActiveSectionEngine, profile: ProxyProfile?, dns: DnsTunnelSettings): CoreEngine =
        when (section) {
            ActiveSectionEngine.AETHER -> CoreEngine.AETHER
            ActiveSectionEngine.MHR -> CoreEngine.MHR
            ActiveSectionEngine.DNS -> when (dns.engineType) {
                DnsEngineType.AUTO_TUNNEL -> CoreEngine.AETHER
                DnsEngineType.DNS_CHANGER -> CoreEngine.XRAY
                DnsEngineType.CUSTOM_DNSTT -> CoreEngine.DNSTT
            }
            ActiveSectionEngine.SERVER -> when {
                profile?.rawUri?.trimStart()?.startsWith("{") == true -> CoreEngine.XRAY
                profile?.protocol == ProxyProtocol.HYSTERIA2 || profile?.protocol == ProxyProtocol.WIREGUARD -> CoreEngine.SING_BOX
                else -> CoreEngine.XRAY
            }
        }
}
