package ir.pnifox.core.vpn

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import ir.pnifox.core.model.AetherIdentityTarget
import ir.pnifox.core.model.AetherProtocol
import ir.pnifox.core.model.AetherSettings
import ir.pnifox.core.model.AppSettings
import ir.pnifox.core.model.ConnectionError
import ir.pnifox.core.model.DnsEngineType
import ir.pnifox.core.model.DnsInnerProtocol
import ir.pnifox.core.model.DnsTunnelSettings
import ir.pnifox.core.model.ExitNode
import ir.pnifox.core.model.MasqueTransport
import ir.pnifox.core.model.MhrSettings
import ir.pnifox.core.model.OneTapPreset
import ir.pnifox.core.model.ProxyProfile
import ir.pnifox.core.model.ProxyProtocol
import ir.pnifox.core.network.ShareLinkParser
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.lang.reflect.InvocationTargetException
import java.net.InetSocketAddress
import java.net.Socket
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference
import javax.inject.Inject
import javax.inject.Singleton

data class AetherIdentityFilesState(
    val hasMasqueToml: Boolean,
    val hasWgToml: Boolean,
    val hasGoolToml: Boolean,
)

/**
 * Supervises native daemon subprocesses (`libaether.so`, `libdnstt.so`, `libpnisingbox.so`)
 * and `pni-core.aar` (`Pnixray`), extracts bundled geo/psiphon assets, captures stdout/stderr
 * into [LogRingBuffer], and verifies local tunnel readiness.
 */
@Singleton
class NativeProcessManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val argBuilder: AetherArgBuilder,
    private val mhrEngineManager: MhrEngineManager,
    private val logs: LogRingBuffer,
) {
    private val scope = CoroutineScope(
        SupervisorJob() + Dispatchers.IO + CoroutineExceptionHandler { _, throwable ->
            logs.append("NativeProc", LogLevel.ERROR, throwable.message ?: throwable::class.java.simpleName)
        },
    )
    private val geoLock = Any()
    private var activeProcess: Process? = null
    private var secondaryProcess: Process? = null
    private var outputPumpJob: Job? = null
    private var secondaryOutputPumpJob: Job? = null
    @Volatile private var xrayStarted = false
    @Volatile var activeXrayProfileId: String? = null
        private set

    val aetherWorkDir: File
        get() = File(context.filesDir, "aether").apply { mkdirs() }

    val geoDir: File
        get() = File(context.filesDir, "geo").apply { mkdirs() }

    private val nativeDir: String
        get() = context.applicationInfo.nativeLibraryDir

    fun isXrayActive(): Boolean = xrayStarted

    fun inspectAetherIdentities(): AetherIdentityFilesState {
        val dir = aetherWorkDir
        return AetherIdentityFilesState(
            hasMasqueToml = File(dir, "aether-masque.toml").exists() || File(dir, "aether.toml").exists(),
            hasWgToml = File(dir, "aether-wg.toml").exists(),
            hasGoolToml = File(dir, "aether-masque-gool.toml").exists() || File(dir, "aether-gool.toml").exists(),
        )
    }

    fun ensurePsiphonServersAsset(): String? = runCatching {
        val target = File(aetherWorkDir, "psiphon_servers.dat")
        if (!target.exists() || target.length() < 1024L) {
            context.assets.open("aether/psiphon_servers.dat").use { input ->
                target.outputStream().use { out -> input.copyTo(out) }
            }
        }
        target.absolutePath
    }.getOrNull()

    /**
     * Copies bundled Chocolate4U `geoip.dat` & `geosite.dat` from APK assets into `files/geo/`
     * so Xray routing rules (`geoip:private`, `geoip:ir`, `geosite:category-ir`, `geosite:category-ads-all`) work.
     */
    fun ensureGeoAssets(): Boolean = synchronized(geoLock) {
        val dir = geoDir
        var copiedAny = false
        for (name in listOf("geoip.dat", "geosite.dat")) {
            val dest = File(dir, name)
            if (!dest.exists() || dest.length() < 1024L) {
                val tmp = File(dir, "$name.tmp")
                val ok = runCatching {
                    context.assets.open("geo/$name").use { input ->
                        tmp.outputStream().use { out -> input.copyTo(out) }
                    }
                    if (dest.exists()) dest.delete()
                    check(tmp.renameTo(dest)) { "Rename failed for $name" }
                    copiedAny = true
                }.onFailure { err ->
                    runCatching { tmp.delete() }
                    logs.append("GeoAssets", LogLevel.WARN, "Could not extract geo/$name: ${err.message}")
                }.isSuccess
                if (!ok) return@synchronized false
            }
        }
        if (copiedAny) {
            logs.append("GeoAssets", LogLevel.INFO, "Installed geoip.dat & geosite.dat in ${dir.absolutePath}")
        }
        File(dir, "geoip.dat").length() > 1024L && File(dir, "geosite.dat").length() > 1024L
    }

    suspend fun runAetherRegister(
        target: AetherIdentityTarget,
        aether: AetherSettings,
    ): Result<String> = withContext(Dispatchers.IO) {
        val exe = File(nativeDir, "libaether.so")
        if (!exe.exists()) {
            return@withContext Result.failure(IllegalStateException("libaether.so not found in $nativeDir"))
        }

        fun executeOnce(effectiveAether: AetherSettings): Result<String> {
            val args = argBuilder.buildRegisterArgs(target, effectiveAether, aetherWorkDir)
            val cmd = listOf(exe.absolutePath) + args
            logs.append("AETHER-Reg", LogLevel.INFO, "Executing: libaether.so ${args.joinToString(" ")}")

            return runCatching {
                val pb = ProcessBuilder(cmd)
                    .directory(aetherWorkDir)
                    .redirectErrorStream(true)
                runCatching { pb.redirectInput(ProcessBuilder.Redirect.from(File("/dev/null"))) }
                val proc = pb.start()
                val outLines = mutableListOf<String>()
                proc.inputStream.bufferedReader().useLines { lines ->
                    lines.forEach { line ->
                        outLines += line
                        logs.append("AETHER-Reg", LogLevel.INFO, line)
                    }
                }
                val code = proc.waitFor()
                if (code != 0) {
                    error("Exit code $code: ${outLines.lastOrNull() ?: "Registration failed"}")
                }
                outLines.lastOrNull() ?: "Keys registered cleanly in aether/*.toml"
            }
        }

        val first = executeOnce(aether)
        if (first.isFailure && aether.warpApiUseFragment) {
            val msg = first.exceptionOrNull()?.message.orEmpty()
            if (msg.contains("EOF", ignoreCase = true) || msg.contains("tls", ignoreCase = true)) {
                logs.append("AETHER-Reg", LogLevel.WARN, "Fragmented WARP API TLS failed on this network; retrying cleanly without Fragment...")
                return@withContext executeOnce(aether.copy(warpApiUseFragment = false))
            }
        }
        first
    }

    suspend fun ensureMasqueIdentityIfMissing(aether: AetherSettings): Boolean {
        if (inspectAetherIdentities().hasMasqueToml) return true
        val res = runAetherRegister(AetherIdentityTarget.MASQUE, aether)
        return res.isSuccess
    }

    /**
     * Unified launcher for the DNS section (`AUTO_TUNNEL`, `DNS_CHANGER`, `CUSTOM_DNSTT`),
     * shared identically by VPN mode (`PniVpnService`) and Proxy mode (`VpnController`).
     */
    suspend fun startDnsSectionDaemon(
        dnsSettings: DnsTunnelSettings,
        appSettings: AppSettings,
        aetherSettings: AetherSettings,
        onStageUpdate: (stageEn: String, stageFa: String) -> Unit = { _, _ -> },
        onEndpointDiscovered: (ip: String, port: Int) -> Unit = { _, _ -> },
    ): ConnectionError? = when (dnsSettings.engineType) {
        DnsEngineType.AUTO_TUNNEL -> {
            if (!inspectAetherIdentities().hasMasqueToml) {
                onStageUpdate("Auto-registering WARP tunnel keys...", "در حال ساخت خودکار کلید تونل...")
                logs.append("DNS-Auto", LogLevel.INFO, "Auto-provisioning AETHER MASQUE keys for 1-tap DNS tunnel...")
                ensureMasqueIdentityIfMissing(aetherSettings)
            }
            val cleanDns = argBuilder.extractAetherDnsServer(dnsSettings)
            logs.append("DNS-Auto", LogLevel.INFO, "Starting 1-tap DNS auto-tunnel with resolver $cleanDns")
            val effectiveApp = appSettings.copy(
                localDns = cleanDns,
                activePreset = OneTapPreset.WARP_FAST_MASQUE_H2,
                exitNode = ExitNode.DEFAULT,
            )
            val effectiveAether = aetherSettings.copy(
                protocol = AetherProtocol.MASQUE,
                masqueTransport = MasqueTransport.HTTP2,
                psiphonEnabled = false,
                torEnabled = false,
            )
            startAetherDaemon(effectiveApp, effectiveAether, onStageUpdate, onEndpointDiscovered)
        }
        DnsEngineType.DNS_CHANGER -> {
            onStageUpdate("Starting lightweight DNS changer...", "در حال فعال‌سازی تغییر DNS ضدتحریم...")
            startDnsChangerDaemon(dnsSettings, appSettings, aetherSettings.localSocksPort)
        }
        DnsEngineType.CUSTOM_DNSTT -> {
            onStageUpdate("Starting DNSTT (${dnsSettings.innerProtocol.label})...", "در حال برقراری تونل DNSTT...")
            startDnsttDaemon(dnsSettings, appSettings, aetherSettings.localSocksPort)
        }
    }

    suspend fun startAetherDaemon(
        appSettings: AppSettings,
        aether: AetherSettings,
        onStageUpdate: (stageEn: String, stageFa: String) -> Unit = { _, _ -> },
        onEndpointDiscovered: (ip: String, port: Int) -> Unit = { _, _ -> },
    ): ConnectionError? = withContext(Dispatchers.IO) {
        stopAll()
        val exe = File(nativeDir, "libaether.so")
        if (!exe.exists()) {
            return@withContext ConnectionError.MissingBinary(
                binaryName = "libaether.so",
                expectedAbiPath = "jniLibs/${nativeDir.substringAfterLast('/')}/libaether.so",
                buildHint = "Add libaether.so to jniLibs.",
            )
        }
        val serversDat = ensurePsiphonServersAsset()
        val routesFile = argBuilder.writeRoutesFileIfNeeded(appSettings, aetherWorkDir)
        if (routesFile != null) {
            logs.append(
                "Rulesets",
                LogLevel.INFO,
                "Rules active (BypassIran=${appSettings.bypassIranEnabled}, Private=${appSettings.bypassPrivateLanEnabled}, AdBlock=${appSettings.blockAdsEnabled})",
            )
        }
        val args = argBuilder.buildRunArgs(appSettings, aether, nativeDir, aetherWorkDir, serversDat, routesFile)
        val usePsiphon = aether.psiphonEnabled ||
            appSettings.exitNode == ExitNode.PSIPHON ||
            appSettings.activePreset == OneTapPreset.WARP_TO_PSIPHON
        val useTor = !usePsiphon && (
            aether.torEnabled ||
                appSettings.exitNode == ExitNode.TOR ||
                appSettings.activePreset == OneTapPreset.WARP_TO_TOR
            )

        val readyMarkers = when {
            usePsiphon -> listOf("[+] psiphon is ready")
            useTor -> listOf("[+] tor is ready")
            else -> listOf("[+] socks5 server listening on")
        }
        val timeoutMs = when {
            usePsiphon || useTor -> 120_000L
            aether.scanForEndpoint || aether.endpointIp.isBlank() -> 120_000L
            else -> 45_000L
        }

        launchAndAwaitCoreReady(
            tag = "AETHER",
            command = listOf(exe.absolutePath) + args,
            socksPort = aether.localSocksPort,
            timeoutMs = timeoutMs,
            readyMarkers = readyMarkers,
            verifySocksReply = true,
            onStageUpdate = onStageUpdate,
            onEndpointDiscovered = onEndpointDiscovered,
        )
    }

    suspend fun startDnsChangerDaemon(
        dns: DnsTunnelSettings,
        appSettings: AppSettings,
        localSocksPort: Int = 10820,
    ): ConnectionError? = withContext(Dispatchers.IO) {
        stopAll()
        val hasGeo = ensureGeoAssets()
        val xrayErrFile = File(context.filesDir, "xray-error.log").apply { runCatching { delete() } }
        val cfg = ConfigBuilders.buildDnsChangerXrayConfig(
            dnsSettings = dns,
            appSettings = appSettings,
            socksPort = localSocksPort,
            errorLogPath = xrayErrFile.absolutePath,
            hasGeoAssets = hasGeo,
        )
        startXrayEngine(
            xrayConfig = cfg,
            profileId = null,
            socksPort = localSocksPort,
            xrayErrFile = xrayErrFile,
            logLabel = "DNS-Changer (${dns.mode.label}: ${dns.resolverAddress})",
        )
    }

    suspend fun startDnsttDaemon(
        dns: DnsTunnelSettings,
        appSettings: AppSettings,
        localSocksPort: Int = 10820,
    ): ConnectionError? = withContext(Dispatchers.IO) {
        stopAll()
        val exe = File(nativeDir, "libdnstt.so")
        if (!exe.exists()) {
            return@withContext ConnectionError.MissingBinary(
                binaryName = "libdnstt.so",
                expectedAbiPath = "jniLibs/libdnstt.so",
                buildHint = "Compile Mygod/dnstt into jniLibs.",
            )
        }
        if (dns.nameserver.isBlank() || dns.publicKeyHex.isBlank()) {
            return@withContext ConnectionError.DnsBlocked(
                reasonEn = "Enter both Nameserver domain and Server Public Key (Hex), or switch DNS mode to 'One-Tap Auto Tunnel' above.",
                reasonFa = "در حالت DNSTT اختصاصی، دامنه Nameserver و کلید عمومی سرور را وارد کنید یا از بالا حالت «اتصال خودکار (بدون کلید)» را بزنید.",
            )
        }

        val innerDnsttPort = 10823
        when (dns.innerProtocol) {
            DnsInnerProtocol.SOCKS5 -> {
                val args = argBuilder.buildDnsttArgs(dns, localSocksPort)
                launchAndAwaitCoreReady(
                    tag = "DNSTT",
                    command = listOf(exe.absolutePath) + args,
                    socksPort = localSocksPort,
                    timeoutMs = 18_000L,
                    verifySocksReply = true,
                    socksReplyReadTimeoutMs = 4_500,
                )
            }
            DnsInnerProtocol.SSH -> {
                if (dns.sshUsername.isBlank()) {
                    return@withContext ConnectionError.DnsBlocked(
                        reasonEn = "SSH mode requires your SlowDNS SSH Username & Password (e.g. from OpenTunnel). Enter credentials or switch inner protocol.",
                        reasonFa = "حالت SSH نیازمند نام کاربری و رمز عبور اکانت SSH (مثلاً از OpenTunnel) است. مشخصات SSH را وارد کنید.",
                    )
                }
                val sbBin = File(nativeDir, "libpnisingbox.so")
                if (!sbBin.exists()) {
                    return@withContext ConnectionError.MissingBinary(
                        binaryName = "libpnisingbox.so",
                        expectedAbiPath = "jniLibs/libpnisingbox.so",
                        buildHint = "Build sing-box core.",
                    )
                }
                // Step 1: Launch dnstt-client forwarding DNS tunnel TCP onto innerDnsttPort
                val dnsttArgs = argBuilder.buildDnsttArgs(dns, innerDnsttPort)
                val dnsttErr = launchSecondaryAndAwaitTcpBind("DNSTT", listOf(exe.absolutePath) + dnsttArgs, innerDnsttPort, 8_000L)
                if (dnsttErr != null) {
                    stopAll()
                    return@withContext dnsttErr
                }
                // Step 2: Launch sing-box SSH client over 127.0.0.1:innerDnsttPort exposing SOCKS5 on localSocksPort
                val cfgFile = File(context.filesDir, "singbox-dnstt-ssh.json").apply {
                    writeText(ConfigBuilders.buildSingBoxDnsttSshConfig(dns, appSettings, innerDnsttPort, localSocksPort))
                }
                logs.append("DNSTT-SSH", LogLevel.INFO, "Starting Sing-box SSH client (${dns.sshUsername}@127.0.0.1:$innerDnsttPort -> :$localSocksPort)")
                val sbErr = launchAndAwaitCoreReady(
                    tag = "DNSTT-SSH",
                    command = listOf(sbBin.absolutePath, "--mode", "singbox", "--config", cfgFile.absolutePath),
                    socksPort = localSocksPort,
                    timeoutMs = 18_000L,
                    verifySocksReply = true,
                )
                if (sbErr != null) stopAll()
                sbErr
            }
            DnsInnerProtocol.VLESS -> {
                val parsed = ShareLinkParser.parseBatch(dns.vlessUri).firstOrNull()
                if (parsed == null || parsed.protocol != ProxyProtocol.VLESS) {
                    return@withContext ConnectionError.ProcessExited(
                        exitCode = -1,
                        lastLogLine = "Paste a valid vless:// link for DNSTT + VLESS mode.",
                    )
                }
                val dnsttArgs = argBuilder.buildDnsttArgs(dns, innerDnsttPort)
                val dnsttErr = launchAndAwaitCoreReady(
                    tag = "DNSTT",
                    command = listOf(exe.absolutePath) + dnsttArgs,
                    socksPort = innerDnsttPort,
                    timeoutMs = 8_000L,
                    verifySocksReply = false,
                )
                if (dnsttErr != null) {
                    stopAll()
                    return@withContext dnsttErr
                }
                val hasGeo = ensureGeoAssets()
                val xrayErrFile = File(context.filesDir, "xray-error.log").apply { runCatching { delete() } }
                val cfg = ConfigBuilders.buildDnsttVlessXrayConfig(
                    profile = parsed,
                    innerDnsttPort = innerDnsttPort,
                    appSettings = appSettings,
                    socksPort = localSocksPort,
                    errorLogPath = xrayErrFile.absolutePath,
                    hasGeoAssets = hasGeo,
                )
                val xErr = startXrayEngine(
                    xrayConfig = cfg,
                    profileId = null,
                    socksPort = localSocksPort,
                    xrayErrFile = xrayErrFile,
                    logLabel = "DNSTT-VLESS via 127.0.0.1:$innerDnsttPort -> :$localSocksPort",
                )
                if (xErr != null) stopAll()
                xErr
            }
        }
    }

    suspend fun startCustomServerDaemon(
        profile: ProxyProfile,
        appSettings: AppSettings,
        localSocksPort: Int = 10820,
    ): ConnectionError? = withContext(Dispatchers.IO) {
        stopAll()
        if (profile.spoofLocalhost40443 && !profile.rawUri.trimStart().startsWith("{")) {
            runCatching {
                val cls = Class.forName("ir.pni.core.pnixray.Pnixray")
                val startSpoof = cls.getMethod(
                    "startSniSpoof",
                    String::class.java,
                    String::class.java,
                    String::class.java,
                    String::class.java,
                    Int::class.javaPrimitiveType,
                    Int::class.javaPrimitiveType,
                )
                val fakeSni = profile.spoofFakeSni.ifBlank { profile.sni }.ifBlank { "www.cloudflare.com" }
                startSpoof.invoke(
                    null,
                    "127.0.0.1:40443",
                    "${profile.address}:${profile.port}",
                    fakeSni,
                    "fragment",
                    12,
                    5,
                )
                logs.append("SNI-Spoof", LogLevel.INFO, "Started local SNI Spoof proxy 127.0.0.1:40443 -> ${profile.address}:${profile.port} (SNI=$fakeSni)")
            }
        }

        val isRawJson = profile.rawUri.trimStart().startsWith("{")
        if (!isRawJson && (profile.protocol == ProxyProtocol.HYSTERIA2 || profile.protocol == ProxyProtocol.WIREGUARD)) {
            val sbBin = File(nativeDir, "libpnisingbox.so")
            if (!sbBin.exists()) {
                return@withContext ConnectionError.MissingBinary(
                    binaryName = "libpnisingbox.so",
                    expectedAbiPath = "jniLibs/libpnisingbox.so",
                    buildHint = "Build sing-box core.",
                )
            }
            val cfgFile = File(context.filesDir, "singbox-active.json").apply {
                writeText(ConfigBuilders.buildSingBoxConfig(profile, appSettings, localSocksPort))
            }
            return@withContext launchAndAwaitCoreReady(
                tag = "Sing-box",
                command = listOf(sbBin.absolutePath, "--mode", "singbox", "--config", cfgFile.absolutePath),
                socksPort = localSocksPort,
                timeoutMs = 12_000L,
                verifySocksReply = true,
            )
        }

        val hasGeo = ensureGeoAssets()
        val xrayErrFile = File(context.filesDir, "xray-error.log").apply {
            runCatching { delete() }
        }
        val xrayConfig = ConfigBuilders.buildXrayConfig(
            profile = profile,
            appSettings = appSettings,
            socksPort = localSocksPort,
            errorLogPath = xrayErrFile.absolutePath,
            hasGeoAssets = hasGeo,
        )
        startXrayEngine(
            xrayConfig = xrayConfig,
            profileId = profile.id,
            socksPort = localSocksPort,
            xrayErrFile = xrayErrFile,
            logLabel = "Xray-core started (${profile.protocol.displayName} ${profile.address}:${profile.port}) on 127.0.0.1:$localSocksPort",
        )
    }

    private suspend fun startXrayEngine(
        xrayConfig: String,
        profileId: String?,
        socksPort: Int,
        xrayErrFile: File,
        logLabel: String,
    ): ConnectionError? {
        val aarResult = runCatching {
            runCatching {
                Class.forName("go.Seq").getMethod("setContext", Context::class.java).invoke(null, context)
            }
            val cls = Class.forName("ir.pni.core.pnixray.Pnixray")
            cls.getMethod("initEnv", String::class.java).invoke(null, geoDir.absolutePath)
            cls.getMethod("startXray", String::class.java).invoke(null, xrayConfig)
            xrayStarted = true
            activeXrayProfileId = profileId
            logs.append("Xray", LogLevel.INFO, logLabel)
        }
        if (aarResult.isSuccess) {
            startXrayErrorTailer(xrayErrFile)
            return awaitLocalSocksReady("Xray", null, socksPort, 8_000L, verifySocksReply = true)
        }

        val xrayBin = File(nativeDir, "libxray.so")
        if (xrayBin.exists()) {
            val cfg = File(context.filesDir, "xray-active.json").apply { writeText(xrayConfig) }
            return launchAndAwaitCoreReady("Xray", listOf(xrayBin.absolutePath, "run", "-c", cfg.absolutePath), socksPort, 10_000L, verifySocksReply = true)
        }

        val rawErr = aarResult.exceptionOrNull()
        val causeErr = (rawErr as? InvocationTargetException)?.targetException ?: rawErr
        val errMsg = causeErr?.message ?: causeErr?.javaClass?.simpleName ?: "pni-core.aar / libxray.so unavailable"
        logs.append("Xray", LogLevel.ERROR, errMsg)
        return ConnectionError.ProcessExited(exitCode = -1, lastLogLine = errMsg)
    }

    private fun startXrayErrorTailer(logFile: File) {
        outputPumpJob?.cancel()
        outputPumpJob = scope.launch {
            var readOffset = 0L
            while (isActive && xrayStarted) {
                delay(1200L)
                if (!logFile.exists()) continue
                val len = logFile.length()
                if (len > readOffset) {
                    runCatching {
                        logFile.inputStream().use { ins ->
                            ins.skip(readOffset)
                            val text = ins.bufferedReader().readText()
                            readOffset = len
                            text.lineSequence().filter { it.isNotBlank() }.forEach { line ->
                                val lvl = if (line.contains("error", ignoreCase = true) || line.contains("failed", ignoreCase = true)) {
                                    LogLevel.WARN
                                } else {
                                    LogLevel.INFO
                                }
                                logs.append("Xray", lvl, line)
                            }
                        }
                    }
                }
            }
        }
    }

    private suspend fun launchSecondaryAndAwaitTcpBind(
        tag: String,
        command: List<String>,
        port: Int,
        timeoutMs: Long,
    ): ConnectionError? = withContext(Dispatchers.IO) {
        logs.append(tag, LogLevel.INFO, "Launching: ${command.first().substringAfterLast('/')} ${command.drop(1).joinToString(" ")}")
        val proc = runCatching {
            ProcessBuilder(command).directory(aetherWorkDir).redirectErrorStream(true).apply {
                runCatching { redirectInput(ProcessBuilder.Redirect.from(File("/dev/null"))) }
            }.start()
        }.getOrElse { err ->
            logs.append(tag, LogLevel.ERROR, err.message ?: "ProcessBuilder failed")
            return@withContext ConnectionError.ProcessExited(-1, err.message ?: "Failed to spawn $tag")
        }
        secondaryProcess = proc
        val lastLine = AtomicReference("")
        secondaryOutputPumpJob = pumpProcessOutput(tag, proc, lastLine)
        awaitLocalSocksReady(
            tag = tag,
            proc = proc,
            socksPort = port,
            timeoutMs = timeoutMs,
            verifySocksReply = false,
            lastLineProvider = { lastLine.get() },
        )
    }

    private fun pumpProcessOutput(
        tag: String,
        proc: Process,
        lastLineRef: AtomicReference<String>,
        onLine: (String) -> Unit = {},
    ): Job = scope.launch {
        runCatching {
            proc.inputStream.bufferedReader().useLines { lines ->
                lines.forEach { line ->
                    lastLineRef.set(line)
                    val lvl = if (line.contains("error", ignoreCase = true) || line.contains("fatal", ignoreCase = true)) {
                        LogLevel.ERROR
                    } else if (line.contains("WARN", ignoreCase = false) || line.contains("[-]")) {
                        LogLevel.WARN
                    } else {
                        LogLevel.INFO
                    }
                    logs.append(tag, lvl, line)
                    onLine(line)
                }
            }
        }
    }

    private suspend fun launchAndAwaitCoreReady(
        tag: String,
        command: List<String>,
        socksPort: Int,
        timeoutMs: Long,
        readyMarkers: List<String> = emptyList(),
        verifySocksReply: Boolean = true,
        socksReplyReadTimeoutMs: Int = 1500,
        onStageUpdate: (stageEn: String, stageFa: String) -> Unit = { _, _ -> },
        onEndpointDiscovered: (ip: String, port: Int) -> Unit = { _, _ -> },
    ): ConnectionError? = withContext(Dispatchers.IO) {
        logs.append(tag, LogLevel.INFO, "Launching: ${command.first().substringAfterLast('/')} ${command.drop(1).joinToString(" ")}")
        val proc = runCatching {
            val pb = ProcessBuilder(command)
                .directory(aetherWorkDir)
                .redirectErrorStream(true)
            runCatching { pb.redirectInput(ProcessBuilder.Redirect.from(File("/dev/null"))) }
            pb.start()
        }.getOrElse { err ->
            logs.append(tag, LogLevel.ERROR, err.message ?: "ProcessBuilder failed")
            return@withContext ConnectionError.ProcessExited(-1, err.message ?: "Failed to spawn binary")
        }

        activeProcess = proc
        val lastLineRef = AtomicReference("")
        val markerSeenRef = AtomicBoolean(readyMarkers.isEmpty())
        val endpointRegex = Regex(
            """(?:selected (?:MASQUE gateway|WireGuard endpoint)|best (?:gateway|endpoint)|using cloudflare edge)\s+(\[?[0-9a-fA-F:.]+\]?):(\d+)""",
            RegexOption.IGNORE_CASE,
        )

        outputPumpJob = pumpProcessOutput(tag, proc, lastLineRef) { line ->
            endpointRegex.find(line)?.let { m ->
                val ip = m.groupValues[1].trim('[', ']')
                val port = m.groupValues[2].toIntOrNull()
                if (ip.isNotBlank() && port != null) {
                    onEndpointDiscovered(ip, port)
                }
            }
            if (readyMarkers.any { marker -> line.contains(marker, ignoreCase = true) }) {
                markerSeenRef.set(true)
            }
            when {
                line.contains("hunting for", ignoreCase = true) ->
                    onStageUpdate("Scanning Cloudflare gateways...", "در حال اسکن دروازه‌های کلودفلر...")
                line.contains("selected MASQUE", ignoreCase = true) ||
                    line.contains("selected WireGuard", ignoreCase = true) ||
                    line.contains("using forced peer", ignoreCase = true) ->
                    onStageUpdate("Connecting tunnel handshake...", "در حال برقراری دست‌دهی تونل...")
                line.contains("psiphon is waiting", ignoreCase = true) ->
                    onStageUpdate("WARP connecting, Psiphon standby...", "در حال اتصال WARP برای سایفون...")
                line.contains("starting psiphon through", ignoreCase = true) ->
                    onStageUpdate("WARP ready, starting Psiphon...", "وارپ آماده شد؛ در حال اتصال سایفون...")
                line.contains("psiphon reached a server", ignoreCase = true) ->
                    onStageUpdate("Psiphon server reached...", "سایفون به سرور متصل شد...")
                line.contains("bootstrapping tor", ignoreCase = true) ->
                    onStageUpdate("Bootstrapping Tor exit...", "در حال راه‌اندازی شبکه تور...")
            }
        }

        awaitLocalSocksReady(
            tag = tag,
            proc = proc,
            socksPort = socksPort,
            timeoutMs = timeoutMs,
            waitOnMarkersFirst = readyMarkers.isNotEmpty(),
            verifySocksReply = verifySocksReply,
            socksReplyReadTimeoutMs = socksReplyReadTimeoutMs,
            lastLineProvider = { lastLineRef.get() },
            extraReadyCheck = { markerSeenRef.get() },
        )
    }

    private suspend fun awaitLocalSocksReady(
        tag: String,
        proc: Process?,
        socksPort: Int,
        timeoutMs: Long,
        waitOnMarkersFirst: Boolean = false,
        verifySocksReply: Boolean = true,
        socksReplyReadTimeoutMs: Int = 1500,
        lastLineProvider: () -> String = { "" },
        extraReadyCheck: () -> Boolean = { true },
    ): ConnectionError? {
        val deadline = System.currentTimeMillis() + timeoutMs
        var lastMismatchError: ConnectionError? = null
        try {
            while (System.currentTimeMillis() < deadline && currentCoroutineContext().isActive) {
                if (proc != null && !isProcessAlive(proc)) {
                    val exit = runCatching { proc.exitValue() }.getOrDefault(-1)
                    return ConnectionError.ProcessExited(exit, lastLineProvider().ifBlank { "Exited prematurely" })
                }
                if (waitOnMarkersFirst && !extraReadyCheck()) {
                    delay(250L)
                    continue
                }
                var socksReady = false
                runCatching {
                    Socket().use { s ->
                        s.soTimeout = socksReplyReadTimeoutMs
                        s.connect(InetSocketAddress("127.0.0.1", socksPort), 400)
                        if (!verifySocksReply) {
                            socksReady = true
                        } else {
                            val out = s.getOutputStream()
                            out.write(byteArrayOf(0x05, 0x01, 0x00))
                            out.flush()
                            val rx = ByteArray(4)
                            val read = s.getInputStream().read(rx)
                            if (read >= 2 && rx[0] == 0x05.toByte()) {
                                socksReady = true
                            } else if (read >= 3 && rx[0] == 'S'.code.toByte() && rx[1] == 'S'.code.toByte() && rx[2] == 'H'.code.toByte()) {
                                lastMismatchError = ConnectionError.DnsBlocked(
                                    reasonEn = "Remote SlowDNS server speaks SSH (received SSH banner instead of SOCKS5). Switch protocol below to 'SSH' and enter your SSH username/password.",
                                    reasonFa = "سرور ریموت SlowDNS از نوع SSH است نه SOCKS5! پروتکل داخلی را روی «SSH» بگذارید و نام کاربری و رمز SSH را وارد کنید.",
                                )
                            }
                        }
                    }
                }
                if (lastMismatchError != null) {
                    stopAll()
                    return lastMismatchError
                }
                if (socksReady && extraReadyCheck()) {
                    logs.append(tag, LogLevel.INFO, "$tag tunnel confirmed ready on 127.0.0.1:$socksPort")
                    return null
                }
                delay(250L)
            }
        } catch (ce: CancellationException) {
            stopAll()
            throw ce
        }
        stopAll()
        return ConnectionError.HandshakeTimeout(
            reasonEn = "$tag did not finish tunnel handshake on 127.0.0.1:$socksPort within ${timeoutMs / 1000}s. ${lastLineProvider()}".trim(),
        )
    }

    private fun isProcessAlive(proc: Process): Boolean = runCatching {
        proc.exitValue()
        false
    }.getOrDefault(true)

    suspend fun startMhrDaemon(
        appSettings: AppSettings,
        aetherSettings: AetherSettings,
        mhrSettings: MhrSettings,
    ): ConnectionError? = withContext(Dispatchers.IO) {
        stopAll()
        val err = mhrEngineManager.start(appSettings, aetherSettings, mhrSettings)
        if (err != null) return@withContext err
        val socksPort = aetherSettings.localSocksPort
        repeat(24) {
            val open = runCatching {
                Socket().use { s ->
                    s.connect(InetSocketAddress("127.0.0.1", socksPort), 250)
                    true
                }
            }.getOrDefault(false)
            if (open) {
                logs.append("MHR", LogLevel.INFO, "MHR SOCKS5 confirmed listening on 127.0.0.1:$socksPort")
                return@withContext null
            }
            delay(200L)
        }
        stopAll()
        ConnectionError.HandshakeTimeout(
            reasonEn = "MHR SOCKS5 did not bind 127.0.0.1:$socksPort within 5s. Check Logs for details.",
        )
    }

    fun stopAll() {
        runCatching { mhrEngineManager.stop() }
        outputPumpJob?.cancel()
        outputPumpJob = null
        secondaryOutputPumpJob?.cancel()
        secondaryOutputPumpJob = null
        activeProcess?.let { proc ->
            runCatching { proc.destroy() }
            runCatching { proc.destroyForcibly() }
        }
        activeProcess = null
        secondaryProcess?.let { proc ->
            runCatching { proc.destroy() }
            runCatching { proc.destroyForcibly() }
        }
        secondaryProcess = null
        if (xrayStarted) {
            runCatching {
                val cls = Class.forName("ir.pni.core.pnixray.Pnixray")
                cls.getMethod("stopXray").invoke(null)
                cls.getMethod("stopSniSpoof").invoke(null)
            }
            xrayStarted = false
        }
        activeXrayProfileId = null
    }
}
