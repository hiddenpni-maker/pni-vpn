package ir.pni.vpn.service

import android.content.Context
import ir.pni.vpn.core.CoreBridge
import ir.pni.vpn.core.SingBoxConfigBuilder
import ir.pni.vpn.core.XrayConfigBuilder
import ir.pni.vpn.data.model.AppSettings
import ir.pni.vpn.data.model.ProtocolType
import ir.pni.vpn.data.model.ProxyProfile
import kotlinx.coroutines.delay
import java.io.File
import java.net.InetSocketAddress
import java.net.Socket

/**
 * Manages standalone native circumvention sidecar executables packaged in `nativeLibraryDir`:
 * - `libpnisingbox.so` (Lantern sing-box-minimal + outline-sdk disorder/split/tlsfrag)
 * - `libaether.so` (PattNG/Soldier Aether WARP & MASQUE core)
 * - `libdnstt.so` (DNSTT / SlowDNS tunnel client like AM Tunnel)
 * - `libpsiphon.so` (Psiphon exit binary)
 * - `libtor.so` + `liblyrebird.so` (Tor + obfs4/snowflake/webtunnel/meek Pluggable Transports)
 */
object ExitProcessService {

    private var activeProcess: Process? = null
    private var secondaryProcess: Process? = null

    fun nativeBinary(context: Context, soName: String): File? {
        val dir = context.applicationInfo.nativeLibraryDir ?: return null
        val file = File(dir, soName)
        return if (file.exists()) file else null
    }

    suspend fun startEngine(
        context: Context,
        profile: ProxyProfile,
        settings: AppSettings,
        configText: String
    ): Result<Int> = runCatching {
        stop()
        val nativeDir = context.applicationInfo.nativeLibraryDir
        val socksPort = XrayConfigBuilder.LOCAL_SOCKS_PORT

        if (profile.protocol == ProtocolType.DNS_TUNNEL) {
            return@runCatching startDnsTunnel(context, profile, settings, socksPort)
        }

        val cmd: List<String>
        val envExtra = mutableMapOf<String, String>()

        when (profile.protocol) {
            ProtocolType.HYSTERIA2, ProtocolType.TUIC, ProtocolType.SSH -> {
                val bin = nativeBinary(context, "libpnisingbox.so")
                    ?: error("هسته libpnisingbox.so هنوز بیلد نشده است (اسکریپت build-cores را اجرا کنید)")
                val cfg = File(context.filesDir, "singbox-config.json").apply { writeText(configText) }
                cmd = listOf(bin.absolutePath, "--mode", "singbox", "--config", cfg.absolutePath)
            }
            ProtocolType.LANTERN_STEALTH -> {
                val bin = nativeBinary(context, "libpnisingbox.so")
                    ?: error("هسته libpnisingbox.so هنوز بیلد نشده است")
                val transport = profile.rawShareLink.removePrefix("lantern://").ifBlank {
                    "split:3|tlsfrag:5"
                }
                cmd = listOf(
                    bin.absolutePath,
                    "--mode", "outline",
                    "--listen", "127.0.0.1:$socksPort",
                    "--transport", transport
                )
            }
            ProtocolType.AETHER_WARP, ProtocolType.AETHER_MASQUE -> {
                val bin = nativeBinary(context, "libaether.so")
                    ?: error("هسته libaether.so هنوز بیلد نشده است")
                val mode = if (profile.protocol == ProtocolType.AETHER_MASQUE) "masque" else "warp"
                val args = mutableListOf(
                    bin.absolutePath,
                    "--bind", "127.0.0.1:$socksPort",
                    "--protocol", mode
                )
                if (profile.server.isNotBlank()) {
                    args += listOf("--endpoint", "${profile.server}:${profile.port}")
                }
                if (profile.sni.isNotBlank()) {
                    args += listOf("--sni", profile.sni)
                }
                cmd = args
            }
            ProtocolType.PSIPHON -> {
                val bin = nativeBinary(context, "libpsiphon.so")
                    ?: error("هسته libpsiphon.so هنوز بیلد نشده است")
                val dataDir = File(context.filesDir, "psiphon_data").apply { mkdirs() }
                cmd = listOf(
                    bin.absolutePath,
                    "-dataRootDirectory", dataDir.absolutePath,
                    "-listen", "127.0.0.1:$socksPort"
                )
            }
            ProtocolType.TOR -> {
                val torBin = nativeBinary(context, "libtor.so")
                    ?: error("هسته libtor.so هنوز بیلد نشده است")
                val ptBin = nativeBinary(context, "liblyrebird.so")
                    ?: error("ترنسپورت liblyrebird.so یافت نشد")
                val torDir = File(context.filesDir, "tor_data").apply { mkdirs() }
                val torrc = buildTorrc(context, torDir, ptBin, socksPort, profile.torBridgeType)
                val torrcFile = File(torDir, "torrc").apply { writeText(torrc) }
                envExtra["LD_LIBRARY_PATH"] = nativeDir
                cmd = listOf(torBin.absolutePath, "-f", torrcFile.absolutePath)
            }
            else -> error("پروتکل ${profile.protocol.labelEn} توسط هسته جانبی پشتیبانی نمی‌شود")
        }

        val pb = ProcessBuilder(cmd).redirectErrorStream(true)
        pb.environment().putAll(envExtra)
        val proc = pb.start()
        activeProcess = proc

        val ready = waitForPort(proc, "127.0.0.1", socksPort, timeoutMs = 15_000)
        if (!ready) {
            val tail = runCatching {
                proc.inputStream.bufferedReader().readLines().takeLast(12).joinToString("\n")
            }.getOrDefault("")
            stop()
            error("پورت محلی هسته باز نشد.\n$tail")
        }
        socksPort
    }

    /**
     * Starts AM Tunnel-style DNS Tunneling (`libdnstt.so`):
     * - If `dnsInnerMode == "socks"`, `dnstt-client` binds directly on `127.0.0.1:socksPort`.
     * - If `dnsInnerMode == "ssh"`, `dnstt-client` binds `127.0.0.1:7000` and `libpnisingbox.so`
     *   runs an SSH outbound through `127.0.0.1:7000` exposing SOCKS5 on `127.0.0.1:socksPort`.
     * - If `dnsInnerMode == "vless"` or `"trojan"`, `dnstt-client` binds `127.0.0.1:7000` and
     *   Xray-core runs through `127.0.0.1:7000` exposing SOCKS5 on `127.0.0.1:socksPort`.
     */
    private suspend fun startDnsTunnel(
        context: Context,
        profile: ProxyProfile,
        settings: AppSettings,
        socksPort: Int
    ): Int {
        val dnsttBin = nativeBinary(context, "libdnstt.so")
            ?: error("هسته تونل DNS (libdnstt.so) هنوز بیلد نشده است")
        val domain = profile.dnsDomain.ifBlank { profile.server }.trim()
        require(domain.isNotBlank()) { "دامنه Nameserver تونل DNS وارد نشده است" }
        require(profile.dnsPubkey.isNotBlank()) { "کلید عمومی سرور DNSTT (PubKey) وارد نشده است" }

        val innerMode = profile.dnsInnerMode.lowercase()
        val dnsttListenPort = if (innerMode == "socks") socksPort else 7000

        val transportFlag = when (profile.dnsTransport.lowercase()) {
            "doh" -> "-doh"
            "dot" -> "-dot"
            else -> "-udp"
        }
        val resolver = profile.dnsResolver.ifBlank {
            when (profile.dnsTransport.lowercase()) {
                "doh" -> "https://1.1.1.1/dns-query"
                "dot" -> "1.1.1.1:853"
                else -> "8.8.8.8:53"
            }
        }

        val cmd = mutableListOf(
            dnsttBin.absolutePath,
            transportFlag, resolver,
            "-pubkey", profile.dnsPubkey.trim()
        )
        if (profile.dnsTransport.lowercase() in setOf("doh", "dot") && profile.dnsUtls.isNotBlank()) {
            cmd += listOf("-utls", profile.dnsUtls.trim())
        }
        cmd += listOf(domain, "127.0.0.1:$dnsttListenPort")

        val proc = ProcessBuilder(cmd).redirectErrorStream(true).start()
        activeProcess = proc

        if (!waitForPort(proc, "127.0.0.1", dnsttListenPort, timeoutMs = 12_000)) {
            val tail = runCatching {
                proc.inputStream.bufferedReader().readLines().takeLast(10).joinToString("\n")
            }.getOrDefault("")
            stop()
            error("پورت محلی DNSTT باز نشد.\n$tail")
        }

        when (innerMode) {
            "socks" -> return socksPort
            "ssh" -> {
                val sbBin = nativeBinary(context, "libpnisingbox.so")
                    ?: error("هسته libpnisingbox.so برای SSH روی DNS یافت نشد")
                val sshProfile = profile.copy(
                    protocol = ProtocolType.SSH,
                    server = "127.0.0.1",
                    port = dnsttListenPort,
                    uuidOrPassword = "${profile.dnsInnerUser}:${profile.dnsInnerPass}"
                )
                val sbCfg = SingBoxConfigBuilder.build(sshProfile, settings)
                val cfgFile = File(context.filesDir, "singbox-dnstt-ssh.json").apply { writeText(sbCfg) }
                val sbProc = ProcessBuilder(
                    listOf(sbBin.absolutePath, "--mode", "singbox", "--config", cfgFile.absolutePath)
                ).redirectErrorStream(true).start()
                secondaryProcess = sbProc

                if (!waitForPort(sbProc, "127.0.0.1", socksPort, timeoutMs = 15_000)) {
                    stop()
                    error("اتصال SSH روی بستر DNS برقرار نشد (نام کاربری/رمز SSH و پورت سرور را بررسی کنید)")
                }
                return socksPort
            }
            "vless", "trojan" -> {
                val innerProto = if (innerMode == "trojan") ProtocolType.TROJAN else ProtocolType.VLESS
                val innerProfile = profile.copy(
                    protocol = innerProto,
                    server = "127.0.0.1",
                    port = dnsttListenPort,
                    uuidOrPassword = profile.dnsInnerUser.ifBlank { profile.uuidOrPassword },
                    network = "tcp",
                    security = "none"
                )
                val xrayCfg = XrayConfigBuilder.build(innerProfile, settings)
                CoreBridge.startXray(xrayCfg).getOrThrow()
                return socksPort
            }
            else -> return socksPort
        }
    }

    fun stop() {
        listOf(secondaryProcess, activeProcess).forEach { proc ->
            if (proc != null) {
                runCatching { proc.destroy() }
                runCatching {
                    if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O && proc.isAlive) {
                        proc.destroyForcibly()
                    }
                }
            }
        }
        secondaryProcess = null
        activeProcess = null
    }

    private fun buildTorrc(
        context: Context,
        torDir: File,
        lyrebird: File,
        socksPort: Int,
        bridgeType: String
    ): String {
        val bridges = loadTorBridges(context, bridgeType)
        return buildString {
            appendLine("DataDirectory ${torDir.absolutePath}")
            appendLine("SocksPort 127.0.0.1:$socksPort")
            appendLine("UseBridges 1")
            appendLine("ClientTransportPlugin obfs4,snowflake,webtunnel,meek_lite exec ${lyrebird.absolutePath}")
            if (bridges.isNotEmpty()) {
                bridges.take(6).forEach { appendLine("Bridge $it") }
            } else {
                appendLine("Bridge snowflake 192.0.2.3:80 2B280B23E1107BB62ABFC40DDCC8824814F80A72 fingerprint=2B280B23E1107BB62ABFC40DDCC8824814F80A72 url=https://1098762253.rsc.cdn77.org/ fronts=www.cdn77.com,www.phpmyadmin.net ice=stun:stun.l.google.com:19302,stun:stun.antisip.com:3478 utls-imitate=hellorandomizedalpn")
            }
        }
    }

    private fun loadTorBridges(context: Context, bridgeType: String): List<String> = runCatching {
        val raw = context.assets.open("tor/bridges.json").bufferedReader().use { it.readText() }
        val lines = Regex("\"((?:webtunnel|obfs4|snowflake|meek_lite) [^\"]+)\"")
            .findAll(raw)
            .map { it.groupValues[1] }
            .toList()
        val filtered = lines.filter { it.startsWith(bridgeType, ignoreCase = true) }
        filtered.ifEmpty { lines }
    }.getOrDefault(emptyList())

    private suspend fun waitForPort(proc: Process, host: String, port: Int, timeoutMs: Long): Boolean {
        val deadline = System.currentTimeMillis() + timeoutMs
        while (System.currentTimeMillis() < deadline) {
            val alive = runCatching {
                proc.exitValue()
                false
            }.getOrDefault(true)
            if (!alive) return false

            val ok = runCatching {
                Socket().use { s ->
                    s.connect(InetSocketAddress(host, port), 350)
                    true
                }
            }.getOrDefault(false)
            if (ok) return true
            delay(250)
        }
        return false
    }
}
