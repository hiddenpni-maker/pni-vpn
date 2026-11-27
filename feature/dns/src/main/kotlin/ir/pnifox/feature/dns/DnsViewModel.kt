package ir.pnifox.feature.dns

import android.content.Intent
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import ir.pnifox.core.database.PreferencesRepository
import ir.pnifox.core.model.ActiveSectionEngine
import ir.pnifox.core.model.AppSettings
import ir.pnifox.core.model.DnsEngineType
import ir.pnifox.core.model.DnsInnerProtocol
import ir.pnifox.core.model.DnsResolverPreset
import ir.pnifox.core.model.DnsTunnelMode
import ir.pnifox.core.model.DnsTunnelSettings
import ir.pnifox.core.model.IranPresetsCatalog
import ir.pnifox.core.model.LogEntry
import ir.pnifox.core.model.LogLevel
import ir.pnifox.core.model.TunnelState
import ir.pnifox.core.vpn.DnsE2EResult
import ir.pnifox.core.vpn.DnsE2EScanner
import ir.pnifox.core.vpn.LogRingBuffer
import ir.pnifox.core.vpn.VpnController
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import javax.inject.Inject

data class DnsUiState(
    val settings: DnsTunnelSettings = DnsTunnelSettings(),
    val appSettings: AppSettings = AppSettings(),
    val presets: List<DnsResolverPreset> = IranPresetsCatalog.dnsPresets,
    val tunnelState: TunnelState = TunnelState.Disconnected,
    val probeStatus: String? = null,
    val isProbing: Boolean = false,
    val isTestingPing: Boolean = false,
    val e2eResults: List<DnsE2EResult> = emptyList(),
    val isScanningE2e: Boolean = false,
    val e2eScanProgress: String = "",
    val rxSpeedBps: Long = 0L,
    val txSpeedBps: Long = 0L,
    val sessionRxBytes: Long = 0L,
    val sessionTxBytes: Long = 0L,
    val logs: List<LogEntry> = emptyList(),
)

private data class DnsRuntimeFlags(
    val probeStatus: String? = null,
    val isProbing: Boolean = false,
    val isTestingPing: Boolean = false,
    val e2eResults: List<DnsE2EResult> = emptyList(),
    val isScanningE2e: Boolean = false,
    val e2eScanProgress: String = "",
)

private data class DnsTrafficTuple(
    val rxSpeedBps: Long,
    val txSpeedBps: Long,
    val sessionRxBytes: Long,
    val sessionTxBytes: Long,
)

@HiltViewModel
class DnsViewModel @Inject constructor(
    private val preferencesRepo: PreferencesRepository,
    private val vpnController: VpnController,
    private val logRingBuffer: LogRingBuffer,
) : ViewModel() {

    private val runtimeFlags = MutableStateFlow(DnsRuntimeFlags())

    private val trafficFlow = combine(
        vpnController.rxSpeedBps,
        vpnController.txSpeedBps,
        vpnController.sessionRxBytes,
        vpnController.sessionTxBytes,
    ) { rx, tx, sRx, sTx ->
        DnsTrafficTuple(rx, tx, sRx, sTx)
    }

    val uiState: StateFlow<DnsUiState> = combine(
        preferencesRepo.dnsSettingsFlow,
        preferencesRepo.appSettingsFlow,
        vpnController.tunnelState,
        runtimeFlags,
        trafficFlow,
        logRingBuffer.logsFlow,
    ) { values ->
        val dns = values[0] as DnsTunnelSettings
        val app = values[1] as AppSettings
        val tunnel = values[2] as TunnelState
        val rt = values[3] as DnsRuntimeFlags
        val traffic = values[4] as DnsTrafficTuple
        @Suppress("UNCHECKED_CAST")
        val logList = values[5] as List<LogEntry>

        DnsUiState(
            settings = dns,
            appSettings = app,
            presets = IranPresetsCatalog.dnsPresets,
            tunnelState = tunnel,
            probeStatus = rt.probeStatus,
            isProbing = rt.isProbing,
            isTestingPing = rt.isTestingPing,
            e2eResults = rt.e2eResults,
            isScanningE2e = rt.isScanningE2e,
            e2eScanProgress = rt.e2eScanProgress,
            rxSpeedBps = traffic.rxSpeedBps,
            txSpeedBps = traffic.txSpeedBps,
            sessionRxBytes = traffic.sessionRxBytes,
            sessionTxBytes = traffic.sessionTxBytes,
            logs = logList,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000L),
        initialValue = DnsUiState(),
    )

    fun prepareVpnIntent(): Intent? = vpnController.prepareVpnPermissionIntent()

    fun toggleDnsConnection() {
        viewModelScope.launch {
            vpnController.connectOrToggle(ActiveSectionEngine.DNS)
        }
    }

    fun startDnsAfterPermission() {
        viewModelScope.launch {
            vpnController.startConnection(ActiveSectionEngine.DNS)
        }
    }

    fun reconnectDns() {
        viewModelScope.launch {
            val st = vpnController.tunnelState.value
            if (st is TunnelState.Connected || st is TunnelState.Connecting) {
                vpnController.disconnect()
                delay(400L)
            }
            vpnController.startConnection(ActiveSectionEngine.DNS)
        }
    }

    fun updateApp(transform: (AppSettings) -> AppSettings) = viewModelScope.launch {
        preferencesRepo.updateAppSettings(transform)
    }

    fun setEngineType(engineType: DnsEngineType) = viewModelScope.launch {
        preferencesRepo.updateDnsSettings { it.copy(engineType = engineType) }
    }

    fun setMode(mode: DnsTunnelMode) = viewModelScope.launch {
        preferencesRepo.updateDnsSettings { it.copy(mode = mode) }
    }

    fun applyPreset(preset: DnsResolverPreset) = viewModelScope.launch {
        preferencesRepo.updateDnsSettings {
            it.copy(selectedPresetId = preset.id, resolverAddress = preset.address, mode = preset.mode)
        }
    }

    fun setTunnelFrequency(freq: Int) = viewModelScope.launch {
        preferencesRepo.updateDnsSettings { it.copy(tunnelFrequency = freq.coerceIn(1, 16)) }
    }

    fun setNameserver(value: String) = viewModelScope.launch {
        preferencesRepo.updateDnsSettings { it.copy(nameserver = value) }
    }

    fun setPublicKey(value: String) = viewModelScope.launch {
        preferencesRepo.updateDnsSettings { it.copy(publicKeyHex = value) }
    }

    fun setResolverAddress(value: String) = viewModelScope.launch {
        preferencesRepo.updateDnsSettings { it.copy(resolverAddress = value) }
    }

    fun setInnerProtocol(proto: DnsInnerProtocol) = viewModelScope.launch {
        preferencesRepo.updateDnsSettings { it.copy(innerProtocol = proto) }
    }

    fun setSshUsername(value: String) = viewModelScope.launch {
        preferencesRepo.updateDnsSettings { it.copy(sshUsername = value) }
    }

    fun setSshPassword(value: String) = viewModelScope.launch {
        preferencesRepo.updateDnsSettings { it.copy(sshPassword = value) }
    }

    fun setVlessUri(value: String) = viewModelScope.launch {
        preferencesRepo.updateDnsSettings { it.copy(vlessUri = value) }
    }

    fun testActiveRealPing() {
        if (runtimeFlags.value.isTestingPing) return
        viewModelScope.launch {
            runtimeFlags.value = runtimeFlags.value.copy(isTestingPing = true)
            vpnController.refreshActiveRealDelay()
            runtimeFlags.value = runtimeFlags.value.copy(isTestingPing = false)
        }
    }

    fun runPort53Probe() {
        if (runtimeFlags.value.isProbing) return
        val rawResolver = uiState.value.settings.resolverAddress.trim()
        val hostIp = rawResolver
            .removePrefix("https://")
            .removePrefix("http://")
            .removePrefix("quic://")
            .removePrefix("tls://")
            .substringBefore('/')
            .substringBefore(':')
            .ifBlank { "1.1.1.1" }

        val nameserver = uiState.value.settings.nameserver.trim()
        viewModelScope.launch {
            runtimeFlags.value = runtimeFlags.value.copy(
                isProbing = true,
                probeStatus = "Testing UDP/53 reachability & TXT query to $hostIp...",
            )
            logRingBuffer.append("DnsProbe", LogLevel.INFO, "Testing UDP port 53 to $hostIp (google.com A + nameserver TXT)...")
            val aResult = withContext(Dispatchers.IO) { sendRealUdpDnsQuery(hostIp, "google.com", qType = 1) }
            val txtResult = if (nameserver.isNotBlank()) {
                withContext(Dispatchers.IO) { sendRealUdpDnsQuery(hostIp, nameserver, qType = 16) }
            } else {
                null
            }
            val status = buildString {
                aResult.fold(
                    onSuccess = { (ms, rcode) ->
                        logRingBuffer.append("DnsProbe", LogLevel.INFO, "UDP/53 open on $hostIp (A RTT ${ms}ms, RCODE=$rcode)")
                        append("UDP/53 OK ($hostIp: ${ms}ms)")
                    },
                    onFailure = { err ->
                        logRingBuffer.append("DnsProbe", LogLevel.WARN, "UDP/53 blocked/timeout on $hostIp: ${err.message}")
                        append("UDP/53 timeout/blocked: ${err.message}")
                    },
                )
                if (txtResult != null) {
                    txtResult.fold(
                        onSuccess = { (ms, rcode) ->
                            val rcodeNote = when (rcode) {
                                0 -> "NOERROR (TXT allowed)"
                                2 -> "SERVFAIL (NS unreachable or TXT dropped)"
                                3 -> "NXDOMAIN (NS domain error)"
                                5 -> "REFUSED"
                                else -> "RCODE=$rcode"
                            }
                            logRingBuffer.append("DnsProbe", if (rcode == 0) LogLevel.INFO else LogLevel.WARN, "TXT probe '$nameserver' via $hostIp: ${ms}ms, $rcodeNote")
                            append(" • TXT: ${ms}ms ($rcodeNote)")
                        },
                        onFailure = { err ->
                            logRingBuffer.append("DnsProbe", LogLevel.WARN, "TXT probe '$nameserver' timed out via $hostIp (ISP may block SlowDNS TXT or nameserver NS is offline): ${err.message}")
                            append(" • TXT timeout/filtered!")
                        },
                    )
                }
            }
            runtimeFlags.value = runtimeFlags.value.copy(isProbing = false, probeStatus = status)
        }
    }

    private fun sendRealUdpDnsQuery(hostIp: String, domain: String, qType: Int): Result<Pair<Int, Int>> = runCatching {
        val baos = ByteArrayOutputStream()
        baos.write(byteArrayOf(0x41, 0x42, 0x01, 0x00, 0x00, 0x01, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00))
        domain.trim().trimEnd('.').split('.').filter { it.isNotBlank() }.forEach { label ->
            val bytes = label.toByteArray(Charsets.US_ASCII)
            baos.write(bytes.size)
            baos.write(bytes)
        }
        baos.write(0x00)
        // QTYPE (1=A, 16=TXT), QCLASS=IN (1)
        baos.write(byteArrayOf(((qType ushr 8) and 0xFF).toByte(), (qType and 0xFF).toByte(), 0x00, 0x01))
        val query = baos.toByteArray()

        val startNs = System.nanoTime()
        val rxBuf = ByteArray(512)
        val packet = DatagramPacket(rxBuf, rxBuf.size)
        DatagramSocket().use { socket ->
            socket.soTimeout = 3000
            val address = InetAddress.getByName(hostIp)
            socket.send(DatagramPacket(query, query.size, address, 53))
            socket.receive(packet)
        }
        val rcode = if (packet.length >= 4) (rxBuf[3].toInt() and 0x0F) else -1
        val ms = ((System.nanoTime() - startNs) / 1_000_000L).toInt().coerceAtLeast(1)
        ms to rcode
    }

    fun runE2eDnsScan(extraCsv: String = "") {
        if (runtimeFlags.value.isScanningE2e) return
        viewModelScope.launch {
            runtimeFlags.value = runtimeFlags.value.copy(
                isScanningE2e = true,
                e2eScanProgress = "Scanning Port-53 resolvers (No ICMP)...",
            )
            val results = DnsE2EScanner.scanResolvers(extraIpsCsv = extraCsv) { done, total ->
                runtimeFlags.value = runtimeFlags.value.copy(e2eScanProgress = "Scanned $done/$total resolvers (UDP/53)...")
            }
            val clean = results.count { it.isReachable && !it.isPoisoned }
            val poisoned = results.count { it.isPoisoned }
            logRingBuffer.append("DNS-E2E", LogLevel.INFO, "Port-53 E2E scan: $clean clean, $poisoned poisoned (10.10.34.x).")
            runtimeFlags.value = runtimeFlags.value.copy(
                isScanningE2e = false,
                e2eResults = results,
                e2eScanProgress = "Done: $clean clean, $poisoned poisoned",
            )
        }
    }

    fun applyScannedDnsIp(ip: String) {
        viewModelScope.launch {
            preferencesRepo.updateDnsSettings {
                it.copy(mode = DnsTunnelMode.UDP, resolverAddress = "$ip:53")
            }
            preferencesRepo.updateGamingSettings {
                it.copy(selectedDnsIp = ip)
            }
            runtimeFlags.value = runtimeFlags.value.copy(probeStatus = "Applied clean DNS $ip:53")
        }
    }

    fun clearLogs() = logRingBuffer.clear()
    fun exportLogs(): String = logRingBuffer.exportPlainText()
}
