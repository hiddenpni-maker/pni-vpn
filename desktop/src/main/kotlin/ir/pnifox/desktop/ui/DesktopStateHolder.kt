package ir.pnifox.desktop.ui

import ir.pnifox.core.model.ActiveSectionEngine
import ir.pnifox.core.model.AetherIdentityTarget
import ir.pnifox.core.model.AetherSettings
import ir.pnifox.core.model.AppLanguage
import ir.pnifox.core.model.AppSettings
import ir.pnifox.core.model.AppUpdateInfo
import ir.pnifox.core.model.DnsTunnelSettings
import ir.pnifox.core.model.LogEntry
import ir.pnifox.core.model.LogLevel
import ir.pnifox.core.model.MhrDefaultSniPool
import ir.pnifox.core.model.MhrMode
import ir.pnifox.core.model.MhrSettings
import ir.pnifox.core.model.MhrSniTestResult
import ir.pnifox.core.model.ProxyProfile
import ir.pnifox.core.model.ProxyProtocol
import ir.pnifox.core.model.SubscriptionGroup
import ir.pnifox.core.model.TunnelState
import ir.pnifox.core.model.UpdateCheckState
import ir.pnifox.core.network.AppUpdateChecker
import ir.pnifox.core.network.MhrShareCodec
import ir.pnifox.core.network.ShareLinkParser
import ir.pnifox.core.vpn.JvmScanCandidateResult
import ir.pnifox.core.vpn.JvmScanProgress
import ir.pnifox.core.vpn.LanNetworkHelper
import ir.pnifox.core.vpn.LogRingBuffer
import ir.pnifox.core.vpn.RealDelaySocketProber
import ir.pnifox.desktop.core.WindowsPrivilegeHelper
import ir.pnifox.desktop.core.WindowsSecurityHelper
import ir.pnifox.desktop.core.WindowsSubscriptionFetcher
import ir.pnifox.desktop.core.WindowsVpnController
import ir.pnifox.desktop.model.DesktopPersistedState
import ir.pnifox.desktop.model.DesktopTab
import ir.pnifox.desktop.model.WindowsBinaryStatus
import ir.pnifox.desktop.model.WindowsTunnelMode
import ir.pnifox.desktop.storage.WindowsStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import java.io.File
import java.util.UUID
import java.util.concurrent.TimeUnit

data class DesktopUiState(
    val selectedTab: DesktopTab = DesktopTab.AETHER,
    val persisted: DesktopPersistedState = DesktopPersistedState(),
    val tunnelState: TunnelState = TunnelState.Disconnected,
    val rxSpeedBps: Long = 0L,
    val txSpeedBps: Long = 0L,
    val sessionRxBytes: Long = 0L,
    val sessionTxBytes: Long = 0L,
    val isAdmin: Boolean = false,
    val isMhrCaInstalled: Boolean = false,
    val isCaActionRunning: Boolean = false,
    val binaries: List<WindowsBinaryStatus> = emptyList(),
    val logs: List<LogEntry> = emptyList(),
    val statusBanner: String? = null,
    val isRegisteringWarp: Boolean = false,
    val isSyncingSubs: Boolean = false,
    val isTestingPing: Boolean = false,
    val isTestingAllServers: Boolean = false,
    val isScanning: Boolean = false,
    val scanProgressText: String = "",
    val scanResults: List<JvmScanCandidateResult> = emptyList(),
    val isProbingDns: Boolean = false,
    val dnsProbeStatus: String = "",
    val sniTestResults: Map<String, MhrSniTestResult> = emptyMap(),
    val testingSniHost: String? = null,
    val isTestingAllSnis: Boolean = false,
    val isDetectingGoogleIp: Boolean = false,
    val lanIps: List<String> = emptyList(),
    val updateState: UpdateCheckState = UpdateCheckState.Idle,
    val startupUpdateModal: AppUpdateInfo? = null,
    val appVersion: String = "2.1.0",
)

private data class RuntimeUiFlags(
    val selectedTab: DesktopTab = DesktopTab.AETHER,
    val statusBanner: String? = null,
    val isRegisteringWarp: Boolean = false,
    val isSyncingSubs: Boolean = false,
    val isCaActionRunning: Boolean = false,
    val isTestingPing: Boolean = false,
    val isTestingAllServers: Boolean = false,
    val isScanning: Boolean = false,
    val scanProgressText: String = "",
    val scanResults: List<JvmScanCandidateResult> = emptyList(),
    val isProbingDns: Boolean = false,
    val dnsProbeStatus: String = "",
    val sniTestResults: Map<String, MhrSniTestResult> = emptyMap(),
    val testingSniHost: String? = null,
    val isTestingAllSnis: Boolean = false,
    val isDetectingGoogleIp: Boolean = false,
    val lanIps: List<String> = emptyList(),
    val updateState: UpdateCheckState = UpdateCheckState.Idle,
    val startupUpdateModal: AppUpdateInfo? = null,
)

class DesktopStateHolder {
    val store = WindowsStore()
    val logs = LogRingBuffer()
    val controller = WindowsVpnController(store = store, logs = logs)

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val httpClient = OkHttpClient.Builder().connectTimeout(12, TimeUnit.SECONDS).readTimeout(15, TimeUnit.SECONDS).build()
    private val subscriptionFetcher = WindowsSubscriptionFetcher(httpClient, logs)
    private val updateChecker = AppUpdateChecker()

    private val flags = MutableStateFlow(RuntimeUiFlags(lanIps = LanNetworkHelper.detectLocalIpv4Addresses()))
    private var scanJob: Job? = null

    private val trafficFlow = combine(
        controller.rxSpeedBps,
        controller.txSpeedBps,
        controller.sessionRxBytes,
        controller.sessionTxBytes,
    ) { rx, tx, sRx, sTx -> listOf(rx, tx, sRx, sTx) }

    private val envFlow = combine(
        controller.isAdmin,
        controller.binaries,
        controller.isMhrCaInstalled,
    ) { admin, bins, caInstalled -> Triple(admin, bins, caInstalled) }

    val uiState: StateFlow<DesktopUiState> = combine(
        flags,
        store.stateFlow,
        controller.tunnelState,
        trafficFlow,
        envFlow,
        logs.logsFlow,
    ) { values ->
        val f = values[0] as RuntimeUiFlags
        val persisted = values[1] as DesktopPersistedState
        val tunnel = values[2] as TunnelState
        @Suppress("UNCHECKED_CAST")
        val traffic = values[3] as List<Long>
        @Suppress("UNCHECKED_CAST")
        val env = values[4] as Triple<Boolean, List<WindowsBinaryStatus>, Boolean>
        @Suppress("UNCHECKED_CAST")
        val logList = values[5] as List<LogEntry>

        DesktopUiState(
            selectedTab = f.selectedTab,
            persisted = persisted,
            tunnelState = tunnel,
            rxSpeedBps = traffic[0],
            txSpeedBps = traffic[1],
            sessionRxBytes = traffic[2],
            sessionTxBytes = traffic[3],
            isAdmin = env.first,
            binaries = env.second,
            isMhrCaInstalled = env.third,
            isCaActionRunning = f.isCaActionRunning,
            logs = logList,
            statusBanner = f.statusBanner,
            isRegisteringWarp = f.isRegisteringWarp,
            isSyncingSubs = f.isSyncingSubs,
            isTestingPing = f.isTestingPing,
            isTestingAllServers = f.isTestingAllServers,
            isScanning = f.isScanning,
            scanProgressText = f.scanProgressText,
            scanResults = f.scanResults,
            isProbingDns = f.isProbingDns,
            dnsProbeStatus = f.dnsProbeStatus,
            sniTestResults = f.sniTestResults,
            testingSniHost = f.testingSniHost,
            isTestingAllSnis = f.isTestingAllSnis,
            isDetectingGoogleIp = f.isDetectingGoogleIp,
            lanIps = f.lanIps,
            updateState = f.updateState,
            startupUpdateModal = f.startupUpdateModal,
        )
    }.stateIn(scope, SharingStarted.Eagerly, DesktopUiState())

    init {
        if (store.stateFlow.value.appSettings.autoCheckUpdates) {
            scope.launch {
                val res = updateChecker.checkLatestRelease("2.1.0")
                flags.value = flags.value.copy(updateState = res)
                if (res is UpdateCheckState.UpdateAvailable &&
                    res.info.latestTag != store.stateFlow.value.appSettings.lastDismissedUpdateTag
                ) {
                    flags.value = flags.value.copy(startupUpdateModal = res.info)
                }
            }
        }
    }

    fun selectTab(tab: DesktopTab) {
        flags.value = flags.value.copy(selectedTab = tab)
    }

    fun dismissBanner() {
        flags.value = flags.value.copy(statusBanner = null)
    }

    fun setBanner(msg: String) {
        flags.value = flags.value.copy(statusBanner = msg)
    }

    fun updateApp(transform: (AppSettings) -> AppSettings) = store.updateAppSettings(transform)
    fun updateAether(transform: (AetherSettings) -> AetherSettings) = store.updateAetherSettings(transform)
    fun updateDns(transform: (DnsTunnelSettings) -> DnsTunnelSettings) = store.updateDnsSettings(transform)
    fun updateMhr(transform: (MhrSettings) -> MhrSettings) = store.updateMhrSettings(transform)
    fun setWindowsTunnelMode(mode: WindowsTunnelMode) = store.setWindowsTunnelMode(mode)

    fun toggleConnect(section: ActiveSectionEngine) {
        scope.launch { controller.connectOrToggle(section) }
    }

    fun reconnectActive() {
        scope.launch { controller.reconnectActive() }
    }

    fun testActiveRealPing() {
        if (flags.value.isTestingPing) return
        scope.launch {
            flags.value = flags.value.copy(isTestingPing = true)
            controller.refreshActiveRealDelay()
            flags.value = flags.value.copy(isTestingPing = false)
        }
    }

    fun registerAetherKeys(target: AetherIdentityTarget = AetherIdentityTarget.ALL) {
        if (flags.value.isRegisteringWarp) return
        scope.launch {
            flags.value = flags.value.copy(isRegisteringWarp = true, statusBanner = "Registering WARP identity via aether.exe...")
            controller.registerAetherKeys(target)
                .onSuccess {
                    flags.value = flags.value.copy(isRegisteringWarp = false, statusBanner = "WARP account registered successfully ✓")
                }
                .onFailure { err ->
                    flags.value = flags.value.copy(isRegisteringWarp = false, statusBanner = "WARP registration failed: ${err.message}")
                }
        }
    }

    fun clearAetherCache() {
        controller.clearAetherCache()
            .onSuccess { setBanner("Cleared AETHER Psiphon & Tor caches.") }
            .onFailure { setBanner("Cache cleanup error: ${it.message}") }
    }

    fun importServerConfigs(rawText: String) {
        val trimmed = rawText.trim()
        if (trimmed.isEmpty()) {
            setBanner("Paste config URI, Base64, JSON, Clash YAML, or https:// subscription link.")
            return
        }
        val parsed = ShareLinkParser.parseBatch(trimmed)
        if (parsed.isNotEmpty()) {
            store.saveProfiles(parsed)
            if (store.stateFlow.value.appSettings.activeProfileId == null) {
                store.updateAppSettings { it.copy(activeProfileId = parsed.first().id) }
            }
            setBanner("Imported ${parsed.size} server profile(s).")
            return
        }

        // If user pasted subscription URL(s) in the Import box, automatically subscribe & sync!
        val urls = trimmed.lineSequence()
            .map { it.trim() }
            .filter { it.startsWith("http://", ignoreCase = true) || it.startsWith("https://", ignoreCase = true) }
            .toList()
        if (urls.isNotEmpty()) {
            urls.forEachIndexed { idx, u ->
                addSubscription("Sub ${store.stateFlow.value.subscriptions.size + idx + 1}", u)
            }
            return
        }

        setBanner("No valid URI, Base64, Clash YAML, or Xray/Sing-box/MASQUE JSON found.")
    }

    fun addSubscription(name: String, url: String) {
        val clean = url.trim()
        if (!clean.startsWith("http://", ignoreCase = true) && !clean.startsWith("https://", ignoreCase = true)) {
            setBanner("Subscription URL must start with http:// or https://")
            return
        }
        scope.launch {
            val existing = store.stateFlow.value.subscriptions.firstOrNull { it.url.equals(clean, ignoreCase = true) }
            val sub = existing ?: SubscriptionGroup(
                id = UUID.randomUUID().toString(),
                name = name.trim().ifBlank { "Subscription ${store.stateFlow.value.subscriptions.size + 1}" },
                url = clean,
                lastUpdatedEpochMs = System.currentTimeMillis(),
            )
            store.saveSubscription(sub)
            flags.value = flags.value.copy(isSyncingSubs = true, statusBanner = "Fetching '${sub.name}'...")
            syncSubscription(sub)
            flags.value = flags.value.copy(isSyncingSubs = false)
        }
    }

    fun syncSingleSubscription(sub: SubscriptionGroup) {
        if (flags.value.isSyncingSubs) return
        scope.launch {
            flags.value = flags.value.copy(isSyncingSubs = true, statusBanner = "Syncing '${sub.name}'...")
            syncSubscription(sub)
            flags.value = flags.value.copy(isSyncingSubs = false)
        }
    }

    fun deleteSubscription(subId: String) {
        store.deleteSubscription(subId)
        setBanner("Subscription removed.")
    }

    fun refreshAllSubscriptions() {
        val subs = store.stateFlow.value.subscriptions
        if (subs.isEmpty()) {
            setBanner("No subscriptions added yet — click 'Add Subscription' first.")
            return
        }
        if (flags.value.isSyncingSubs) return
        scope.launch {
            flags.value = flags.value.copy(isSyncingSubs = true, statusBanner = "Syncing ${subs.size} subscription(s)...")
            subs.forEach { syncSubscription(it) }
            flags.value = flags.value.copy(isSyncingSubs = false)
        }
    }

    private suspend fun syncSubscription(sub: SubscriptionGroup) {
        val (httpPort, socksPort) = controller.activeProxyPorts()
        subscriptionFetcher.fetch(
            url = sub.url,
            groupId = sub.id,
            activeHttpPort = httpPort,
            activeSocksPort = socksPort,
        )
            .onSuccess { outcome ->
                val list = outcome.profiles
                if (list.isNotEmpty()) {
                    store.deleteGroupProfiles(sub.id)
                    store.saveProfiles(list)
                    store.saveSubscription(sub.copy(profileCount = list.size, lastUpdatedEpochMs = System.currentTimeMillis()))
                    if (store.stateFlow.value.appSettings.activeProfileId == null) {
                        store.updateAppSettings { it.copy(activeProfileId = list.first().id) }
                    }
                    setBanner("Synced '${sub.name}': ${list.size} server(s) via ${outcome.transportUsed} ✓")
                } else {
                    setBanner("'${sub.name}' returned 0 valid servers.")
                }
            }
            .onFailure { err ->
                logs.append("Sub", LogLevel.ERROR, "Subscription '${sub.name}' failed: ${err.message}")
                val hint = if (httpPort == null && socksPort == null) {
                    " (Tip: Connect AETHER/MHR first or retry)"
                } else {
                    ""
                }
                setBanner("Subscription '${sub.name}' failed: ${err.message}$hint")
            }
    }

    fun selectProfile(p: ProxyProfile) = store.updateAppSettings { it.copy(activeProfileId = p.id) }
    fun togglePin(p: ProxyProfile) = store.saveProfiles(listOf(p.copy(isPinned = !p.isPinned)))
    fun toggleSpoof(p: ProxyProfile) = store.saveProfiles(listOf(ShareLinkParser.toggleSoldierSniSpoof(p)))
    fun deleteProfile(id: String) = store.deleteProfile(id)

    fun saveQuickEdit(
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
        val edited = ShareLinkParser.applyQuickEdit(
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
        store.saveProfiles(listOf(edited))
        setBanner("Saved changes to '${edited.name}'.")
    }

    fun saveRawJsonEdit(existing: ProxyProfile, rawJsonText: String): Result<Unit> {
        val res = ShareLinkParser.applyRawJsonEdit(existing, rawJsonText)
        return res.map { updated ->
            store.saveProfiles(listOf(updated))
            setBanner("Updated raw JSON config '${updated.name}'.")
        }
    }

    fun pingProfile(profile: ProxyProfile) {
        scope.launch {
            val ms = measureSingleProfileHttpDelay(profile)
            store.saveProfiles(listOf(profile.copy(lastPingMs = ms ?: -1)))
            setBanner(if (ms != null) "${profile.name}: ${ms}ms (HTTP 204)" else "${profile.name}: Timeout")
        }
    }

    fun pingAllProfiles() {
        if (flags.value.isTestingAllServers) return
        scope.launch {
            flags.value = flags.value.copy(isTestingAllServers = true)
            val list = store.stateFlow.value.profiles
            var ok = 0
            for (p in list) {
                val ms = measureSingleProfileHttpDelay(p)
                if (ms != null) ok++
                store.saveProfiles(listOf(p.copy(lastPingMs = ms ?: -1)))
            }
            flags.value = flags.value.copy(isTestingAllServers = false, statusBanner = "Real Delay probe finished: $ok/${list.size} reachable.")
        }
    }

    private suspend fun measureSingleProfileHttpDelay(profile: ProxyProfile): Int? {
        val freePort = runCatching { java.net.ServerSocket(0).use { it.localPort } }.getOrDefault(10845)
        val app = store.stateFlow.value.appSettings.copy(allowLan = false)
        val tmpCfg = File(store.runtimeDir, "ping-${System.nanoTime()}.json")
        val isRawJson = profile.rawUri.trimStart().startsWith("{")
        val isSb = !isRawJson && (profile.protocol == ProxyProtocol.HYSTERIA2 || profile.protocol == ProxyProtocol.WIREGUARD)
        val bin = controller.coreLocator.binaryFile(if (isSb) "sing-box.exe" else "xray.exe")
        if (!bin.exists()) return null
        val jsonCfg = if (isSb) {
            ir.pnifox.core.vpn.ConfigBuilders.buildSingBoxConfig(profile, app, socksPort = freePort)
        } else {
            val hasGeo = controller.coreLocator.ensureGeoAssets(store.geoDir)
            ir.pnifox.core.vpn.ConfigBuilders.buildXrayConfig(profile, app, socksPort = freePort, hasGeoAssets = hasGeo)
        }
        tmpCfg.writeText(jsonCfg, Charsets.UTF_8)
        var proc: Process? = null
        return try {
            val pb = ProcessBuilder(bin.absolutePath, "run", "-c", tmpCfg.absolutePath)
                .directory(bin.parentFile)
                .redirectErrorStream(true)
            pb.environment()["XRAY_LOCATION_ASSET"] = store.geoDir.absolutePath
            proc = pb.start()
            val deadline = System.currentTimeMillis() + 3000L
            var ready = false
            while (System.currentTimeMillis() < deadline && proc.isAlive) {
                val up = runCatching {
                    java.net.Socket().use { s ->
                        s.soTimeout = 300
                        s.connect(java.net.InetSocketAddress("127.0.0.1", freePort), 250)
                        s.getOutputStream().write(byteArrayOf(0x05, 0x01, 0x00))
                        s.getOutputStream().flush()
                        val b0 = s.getInputStream().read()
                        b0 == 0x05
                    }
                }.getOrDefault(false)
                if (up) {
                    ready = true
                    break
                }
                kotlinx.coroutines.delay(120L)
            }
            if (!ready) return null
            RealDelaySocketProber.probeHttp204ViaSocks5("127.0.0.1", freePort, timeoutMs = 4500)
        } finally {
            runCatching { proc?.destroyForcibly() }
            WindowsSecurityHelper.secureWipeAndDelete(tmpCfg)
        }
    }

    // MHR helpers
    fun addMhrScriptIds(rawInput: String) {
        val tokens = rawInput.split('\n', '\r', ' ', '\t', ',', ';')
            .map { MhrDefaultSniPool.extractDeploymentId(it) }
            .filter { it.isNotBlank() }
        if (tokens.isEmpty()) return
        store.updateMhrSettings { cur -> cur.copy(scriptIds = (cur.scriptIds + tokens).distinct()) }
    }

    fun autoDetectGoogleIp() {
        if (flags.value.isDetectingGoogleIp) return
        val domain = store.stateFlow.value.mhrSettings.frontDomain
        scope.launch {
            flags.value = flags.value.copy(isDetectingGoogleIp = true)
            val ip = controller.autoDetectGoogleIp(domain)
            if (ip != null) {
                store.updateMhrSettings { it.copy(googleIp = ip) }
                flags.value = flags.value.copy(isDetectingGoogleIp = false, statusBanner = "Detected google_ip = $ip")
            } else {
                flags.value = flags.value.copy(isDetectingGoogleIp = false, statusBanner = "Auto-detect google_ip failed.")
            }
        }
    }

    fun testSingleSni(host: String) {
        if (flags.value.testingSniHost != null) return
        val ip = store.stateFlow.value.mhrSettings.googleIp
        scope.launch {
            flags.value = flags.value.copy(testingSniHost = host)
            val res = controller.probeSni(ip, host)
            flags.value = flags.value.copy(
                testingSniHost = null,
                sniTestResults = flags.value.sniTestResults + (host to res),
            )
        }
    }

    fun testAllSnis() {
        if (flags.value.isTestingAllSnis) return
        val mhr = store.stateFlow.value.mhrSettings
        scope.launch {
            flags.value = flags.value.copy(isTestingAllSnis = true)
            for (sni in mhr.sniHosts) {
                flags.value = flags.value.copy(testingSniHost = sni)
                val res = controller.probeSni(mhr.googleIp, sni)
                flags.value = flags.value.copy(sniTestResults = flags.value.sniTestResults + (sni to res))
            }
            flags.value = flags.value.copy(isTestingAllSnis = false, testingSniHost = null)
        }
    }

    fun installMhrCaCert() {
        if (flags.value.isCaActionRunning) return
        scope.launch {
            flags.value = flags.value.copy(isCaActionRunning = true)
            controller.installMhrCaCert()
                .onSuccess { msg -> flags.value = flags.value.copy(isCaActionRunning = false, statusBanner = msg) }
                .onFailure { err -> flags.value = flags.value.copy(isCaActionRunning = false, statusBanner = "CA install failed: ${err.message}") }
        }
    }

    fun removeMhrCaCert() {
        if (flags.value.isCaActionRunning) return
        scope.launch {
            flags.value = flags.value.copy(isCaActionRunning = true)
            controller.removeMhrCaCert()
                .onSuccess { msg -> flags.value = flags.value.copy(isCaActionRunning = false, statusBanner = msg) }
                .onFailure { err -> flags.value = flags.value.copy(isCaActionRunning = false, statusBanner = "CA removal note: ${err.message}") }
        }
    }

    fun readMhrScript(fullTunnel: Boolean): String =
        controller.coreLocator.readBundledResourceText(if (fullTunnel) "mhr/CodeFull.gs" else "mhr/Code.gs")

    fun importMhrShareLink(raw: String) {
        MhrShareCodec.decode(raw, store.stateFlow.value.mhrSettings)
            .onSuccess { s ->
                store.updateMhrSettings { s }
                setBanner("Imported mhrv-rs:// config (${s.scriptIds.size} IDs)")
            }
            .onFailure { setBanner(it.message ?: "Invalid mhrv-rs:// link") }
    }

    fun exportMhrShareLink(): String = MhrShareCodec.encode(store.stateFlow.value.mhrSettings)

    // DNS Probe
    fun testPort53AndTxt() {
        if (flags.value.isProbingDns) return
        val dns = store.stateFlow.value.dnsSettings
        scope.launch {
            flags.value = flags.value.copy(isProbingDns = true, dnsProbeStatus = "Probing UDP/53 & TXT...")
            val msg = controller.testUdpPort53AndTxt(dns.resolverAddress, dns.nameserver)
            logs.append("DnsProbe", LogLevel.INFO, msg)
            flags.value = flags.value.copy(isProbingDns = false, dnsProbeStatus = msg)
        }
    }

    // CDN Scanner
    fun startScan(provider: String, cidrFile: String, customTargets: String, sni: String, sampleCount: Int) {
        if (flags.value.isScanning) return
        scanJob = scope.launch {
            flags.value = flags.value.copy(isScanning = true, scanResults = emptyList(), scanProgressText = "Scanning $provider...")
            val results = controller.runCdnScan(
                provider = provider,
                cidrResource = cidrFile,
                customTargets = customTargets,
                sni = sni,
                count = sampleCount,
                onProgress = { p ->
                    flags.value = flags.value.copy(scanProgressText = "Tested ${p.tested}/${p.total} • Healthy: ${p.healthy}")
                },
                onResult = { res ->
                    val merged = (flags.value.scanResults + res).sortedByDescending { it.score }
                    flags.value = flags.value.copy(scanResults = merged)
                },
            )
            flags.value = flags.value.copy(
                isScanning = false,
                scanResults = results,
                scanProgressText = "Finished: ${results.size} clean IPs found.",
            )
        }
    }

    fun stopScan() {
        scanJob?.cancel()
        scanJob = null
        flags.value = flags.value.copy(isScanning = false)
    }

    fun applyScannedIp(ip: String, port: Int) {
        store.updateAetherSettings {
            it.copy(endpointIp = ip, endpointPort = port, scanForEndpoint = false)
        }
        setBanner("Applied clean endpoint $ip:$port to AETHER!")
    }

    fun refreshLanIps() {
        flags.value = flags.value.copy(lanIps = LanNetworkHelper.detectLocalIpv4Addresses())
    }

    fun checkForUpdates() {
        if (flags.value.updateState is UpdateCheckState.Checking) return
        scope.launch {
            flags.value = flags.value.copy(updateState = UpdateCheckState.Checking)
            val res = updateChecker.checkLatestRelease("2.1.0")
            flags.value = flags.value.copy(updateState = res)
        }
    }

    fun dismissStartupUpdate(tag: String?) {
        flags.value = flags.value.copy(startupUpdateModal = null)
        if (!tag.isNullOrBlank()) {
            store.updateAppSettings { it.copy(lastDismissedUpdateTag = tag) }
        }
    }

    fun openTelegramChannel() {
        val url = store.stateFlow.value.appSettings.telegramUrl.ifBlank { "https://t.me/otaqe_pni" }
        WindowsSecurityHelper.openExternalUrl(url).onFailure { err ->
            setBanner("Open Telegram failed: ${err.message} ($url)")
        }
    }

    fun openExternal(url: String) {
        WindowsSecurityHelper.openExternalUrl(url).onFailure { err ->
            setBanner("Could not open link: ${err.message}")
        }
    }

    fun relaunchAsAdmin() {
        WindowsPrivilegeHelper.relaunchAsAdministrator()
            .onSuccess {
                controller.disconnectSync()
                System.exit(0)
            }
            .onFailure { setBanner("Could not trigger UAC elevation: ${it.message}") }
    }

    fun pickFirstLaunchLanguage(lang: AppLanguage) {
        store.updateAppSettings { it.copy(language = lang, hasSelectedLanguage = true) }
    }

    fun markTelegramPromptSeen() {
        store.updateAppSettings { it.copy(hasPromptedTelegramJoin = true) }
    }
}
