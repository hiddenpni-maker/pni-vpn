package ir.pnifox.core.vpn

import android.content.Context
import android.os.Build
import dagger.hilt.android.qualifiers.ApplicationContext
import ir.pnifox.core.model.ActiveSectionEngine
import ir.pnifox.core.model.AetherSettings
import ir.pnifox.core.model.AppSettings
import ir.pnifox.core.model.ConnectionError
import ir.pnifox.core.model.ConnectionMode
import ir.pnifox.core.model.CoreEngine
import ir.pnifox.core.model.DnsEngineType
import ir.pnifox.core.model.DnsInnerProtocol
import ir.pnifox.core.model.DnsTunnelSettings
import ir.pnifox.core.model.ExitNode
import ir.pnifox.core.model.GamingMode
import ir.pnifox.core.model.GamingSettings
import ir.pnifox.core.model.NativeBinaryId
import ir.pnifox.core.model.NativeBinaryStatus
import ir.pnifox.core.model.OneTapPreset
import ir.pnifox.core.model.ProxyProfile
import ir.pnifox.core.model.ProxyProtocol
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class NativeBinaryRegistry @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private val nativeDir: File
        get() = File(context.applicationInfo.nativeLibraryDir)

    fun primaryAbi(): String = Build.SUPPORTED_ABIS.firstOrNull() ?: "arm64-v8a"

    fun isXrayAarAvailable(): Boolean = runCatching {
        Class.forName("ir.pni.core.pnixray.Pnixray")
        true
    }.getOrDefault(false)

    fun inspectAll(): List<NativeBinaryStatus> {
        val dir = nativeDir
        return NativeBinaryId.entries.map { bin ->
            if (bin == NativeBinaryId.XRAY && isXrayAarAvailable()) {
                NativeBinaryStatus(
                    binary = bin,
                    isAvailable = true,
                    resolvedPath = "classpath:libs/pni-core.aar (Pnixray)",
                    sizeBytes = 1L,
                )
            } else {
                val f = File(dir, bin.fileName)
                val valid = f.exists() && f.isFile && f.length() > 1024L
                NativeBinaryStatus(
                    binary = bin,
                    isAvailable = valid,
                    resolvedPath = f.absolutePath,
                    sizeBytes = if (f.exists()) f.length() else 0L,
                )
            }
        }
    }

    fun resolveEngineForSection(
        section: ActiveSectionEngine,
        appSettings: AppSettings,
        aetherSettings: AetherSettings,
        activeProfile: ProxyProfile?,
        dnsSettings: DnsTunnelSettings = DnsTunnelSettings(),
        gamingSettings: GamingSettings = GamingSettings(),
    ): CoreEngine = when (section) {
        ActiveSectionEngine.AETHER -> CoreEngine.AETHER
        ActiveSectionEngine.GAMING -> when (gamingSettings.mode) {
            GamingMode.DNS_ANTI_SANCTION -> CoreEngine.XRAY
            GamingMode.LOCAL_DPI_DESYNC -> CoreEngine.LOCAL_DESYNC
            GamingMode.AETHER_UDP_QUIC -> CoreEngine.AETHER
            GamingMode.CUSTOM_SERVER -> resolveEngineForSection(ActiveSectionEngine.SERVER, appSettings, aetherSettings, activeProfile, dnsSettings, gamingSettings)
        }
        ActiveSectionEngine.DNS -> when (dnsSettings.engineType) {
            DnsEngineType.AUTO_TUNNEL -> CoreEngine.AETHER
            DnsEngineType.DNS_CHANGER -> CoreEngine.XRAY
            DnsEngineType.CUSTOM_DNSTT -> CoreEngine.DNSTT
        }
        ActiveSectionEngine.SERVER -> when {
            activeProfile?.rawUri?.trimStart()?.startsWith("{") == true -> CoreEngine.XRAY
            activeProfile?.protocol == ProxyProtocol.HYSTERIA2 ||
                activeProfile?.protocol == ProxyProtocol.WIREGUARD ||
                activeProfile?.protocol == ProxyProtocol.TUIC ||
                activeProfile?.protocol == ProxyProtocol.ANYTLS -> CoreEngine.SING_BOX
            else -> CoreEngine.XRAY
        }
        ActiveSectionEngine.MHR -> CoreEngine.MHR
    }

    fun verifyRequiredBinaries(
        section: ActiveSectionEngine,
        appSettings: AppSettings,
        aetherSettings: AetherSettings,
        activeProfile: ProxyProfile?,
        dnsSettings: DnsTunnelSettings = DnsTunnelSettings(),
        gamingSettings: GamingSettings = GamingSettings(),
    ): ConnectionError.MissingBinary? {
        val statuses = inspectAll().associateBy { it.binary }
        val abi = primaryAbi()

        val required = buildList {
            if (appSettings.connectionMode == ConnectionMode.VPN) {
                add(NativeBinaryId.HEV_SOCKS5_TUNNEL)
            }
            when (section) {
                ActiveSectionEngine.AETHER -> {
                    add(NativeBinaryId.AETHER)
                    val needPsiphon = aetherSettings.psiphonEnabled ||
                        appSettings.exitNode == ExitNode.PSIPHON ||
                        appSettings.activePreset == OneTapPreset.WARP_TO_PSIPHON ||
                        appSettings.activePreset == OneTapPreset.PSIPHON_AKAMAI_CDN
                    val needTor = !needPsiphon && (
                        aetherSettings.torEnabled ||
                            appSettings.exitNode == ExitNode.TOR ||
                            appSettings.activePreset == OneTapPreset.WARP_TO_TOR
                        )
                    if (needPsiphon) add(NativeBinaryId.PSIPHON)
                    if (!needPsiphon && needTor) {
                        add(NativeBinaryId.TOR)
                        add(NativeBinaryId.LYREBIRD)
                    }
                }
                ActiveSectionEngine.GAMING -> when (gamingSettings.mode) {
                    GamingMode.DNS_ANTI_SANCTION -> add(NativeBinaryId.XRAY)
                    GamingMode.LOCAL_DPI_DESYNC -> Unit // Runs built-in Kotlin LocalDpiDesyncServer (0 binary deps!)
                    GamingMode.AETHER_UDP_QUIC -> add(NativeBinaryId.AETHER)
                    GamingMode.CUSTOM_SERVER -> {
                        val p = activeProfile
                        if (p != null) {
                            when {
                                p.rawUri.trimStart().startsWith("{") -> add(NativeBinaryId.XRAY)
                                p.protocol == ProxyProtocol.HYSTERIA2 ||
                                    p.protocol == ProxyProtocol.WIREGUARD ||
                                    p.protocol == ProxyProtocol.TUIC ||
                                    p.protocol == ProxyProtocol.ANYTLS -> add(NativeBinaryId.SING_BOX)
                                else -> add(NativeBinaryId.XRAY)
                            }
                        }
                    }
                }
                ActiveSectionEngine.SERVER -> when {
                    activeProfile?.rawUri?.trimStart()?.startsWith("{") == true -> add(NativeBinaryId.XRAY)
                    activeProfile?.protocol == ProxyProtocol.HYSTERIA2 ||
                        activeProfile?.protocol == ProxyProtocol.WIREGUARD ||
                        activeProfile?.protocol == ProxyProtocol.TUIC ||
                        activeProfile?.protocol == ProxyProtocol.ANYTLS -> add(NativeBinaryId.SING_BOX)
                    else -> add(NativeBinaryId.XRAY)
                }
                ActiveSectionEngine.DNS -> when (dnsSettings.engineType) {
                    DnsEngineType.AUTO_TUNNEL -> add(NativeBinaryId.AETHER)
                    DnsEngineType.DNS_CHANGER -> add(NativeBinaryId.XRAY)
                    DnsEngineType.CUSTOM_DNSTT -> {
                        add(NativeBinaryId.DNSTT)
                        when (dnsSettings.innerProtocol) {
                            DnsInnerProtocol.SSH -> add(NativeBinaryId.SING_BOX)
                            DnsInnerProtocol.VLESS -> add(NativeBinaryId.XRAY)
                            DnsInnerProtocol.SOCKS5 -> Unit
                        }
                    }
                }
                ActiveSectionEngine.MHR -> add(NativeBinaryId.MHR)
            }
        }

        for (bin in required) {
            val st = statuses[bin]
            if (st == null || !st.isAvailable) {
                return ConnectionError.MissingBinary(
                    binaryName = bin.fileName,
                    expectedAbiPath = "app/src/main/jniLibs/$abi/${bin.fileName}",
                    buildHint = "Run scripts/build-cores.sh & scripts/build-binaries.sh or download GitHub Release APKs with compiled native cores.",
                )
            }
        }
        return null
    }
}
