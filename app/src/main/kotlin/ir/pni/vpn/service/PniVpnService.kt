package ir.pni.vpn.service

import android.app.Notification
import android.app.PendingIntent
import android.content.Intent
import android.net.VpnService
import android.os.ParcelFileDescriptor
import androidx.core.app.NotificationCompat
import ir.pni.vpn.MainActivity
import ir.pni.vpn.PniApplication
import ir.pni.vpn.R
import ir.pni.vpn.core.CoreBridge
import ir.pni.vpn.core.SingBoxConfigBuilder
import ir.pni.vpn.core.XrayConfigBuilder
import ir.pni.vpn.data.model.AppSettings
import ir.pni.vpn.data.model.ConnectionState
import ir.pni.vpn.data.model.EngineKind
import ir.pni.vpn.data.model.ProxyProfile
import ir.pni.vpn.data.repository.PniRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * Primary Android VPN Service for PNI:
 * Orchestrates Xray-core (gomobile AAR), Sing-box / Lantern / Outline, Aether, Psiphon, or Tor,
 * optional local SNI Spoof proxy, Android TUN interface, and `libhev-socks5-tunnel.so`.
 */
class PniVpnService : VpnService() {

    companion object {
        const val ACTION_START = "ir.pni.vpn.VPN_START"
        const val ACTION_START_LAST = "ir.pni.vpn.VPN_START_LAST"
        const val ACTION_STOP = "ir.pni.vpn.VPN_STOP"
        const val EXTRA_PROFILE_ID = "profile_id"
        private const val NOTIF_ID = 1001

        private val _state = MutableStateFlow(ConnectionState.DISCONNECTED)
        val state: StateFlow<ConnectionState> = _state.asStateFlow()

        private val _statusMessage = MutableStateFlow("آماده اتصال")
        val statusMessage: StateFlow<String> = _statusMessage.asStateFlow()

        private val _txBytesPerSec = MutableStateFlow(0L)
        val txBytesPerSec: StateFlow<Long> = _txBytesPerSec.asStateFlow()

        private val _rxBytesPerSec = MutableStateFlow(0L)
        val rxBytesPerSec: StateFlow<Long> = _rxBytesPerSec.asStateFlow()
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var tunFd: ParcelFileDescriptor? = null
    private var tProxy: TProxyService? = null
    private var statsJob: Job? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START, ACTION_START_LAST -> {
                val requestedId = intent.getStringExtra(EXTRA_PROFILE_ID)
                scope.launch { startTunnel(requestedId) }
            }
            ACTION_STOP -> {
                scope.launch { stopTunnel() }
            }
        }
        return START_STICKY
    }

    override fun onRevoke() {
        scope.launch { stopTunnel() }
        super.onRevoke()
    }

    override fun onDestroy() {
        scope.launch { stopTunnel() }
        scope.cancel()
        super.onDestroy()
    }

    private suspend fun startTunnel(requestedProfileId: String?) {
        if (_state.value == ConnectionState.CONNECTING || _state.value == ConnectionState.CONNECTED) {
            stopTunnelInternal()
        }

        _state.value = ConnectionState.CONNECTING
        _statusMessage.value = "در حال آماده‌سازی هسته و تونل..."
        startForeground(NOTIF_ID, buildNotification("در حال اتصال..."))

        val repo = PniRepository(applicationContext)
        val settings = repo.settingsFlow.first()
        val profiles = repo.profilesFlow.first()

        val targetId = requestedProfileId ?: settings.activeProfileId
        val profile = profiles.firstOrNull { it.id == targetId } ?: profiles.firstOrNull()
        if (profile == null) {
            failStart("هیچ پروفایلی برای اتصال انتخاب نشده است")
            return
        }

        val chainNext = profile.chainNextProfileId?.let { id -> profiles.firstOrNull { it.id == id } }

        // Optional local SNI Spoof proxy (`127.0.0.1:40443`)
        if (profile.spoofEnabled) {
            val targetAddr = profile.spoofTargetAddr.ifBlank { "${profile.server}:${profile.port}" }
            val listenAddr = "127.0.0.1:${settings.localSniSpoofPort}"
            if (targetAddr != listenAddr) {
                CoreBridge.startSniSpoof(
                    listenAddr = listenAddr,
                    targetAddr = targetAddr,
                    fakeSni = profile.spoofFakeSni.ifBlank { settings.spoofFakeSni },
                    method = "fragment+sni",
                    fragLen = settings.spoofFragLen,
                    fragDelayMs = 2
                )
            }
        }

        val engineResult: Result<Int> = when (profile.protocol.engine) {
            EngineKind.XRAY -> {
                val cfg = XrayConfigBuilder.build(profile, settings, chainNext)
                val syntaxErr = CoreBridge.checkXrayConfig(cfg)
                if (syntaxErr != null) {
                    Result.failure(IllegalArgumentException("خطای پیکربندی Xray: $syntaxErr"))
                } else {
                    CoreBridge.startXray(cfg).map { XrayConfigBuilder.LOCAL_SOCKS_PORT }
                }
            }
            EngineKind.SINGBOX, EngineKind.AETHER, EngineKind.DNSTT, EngineKind.PSIPHON, EngineKind.TOR -> {
                val cfg = if (profile.protocol.engine == EngineKind.SINGBOX) {
                    SingBoxConfigBuilder.build(profile, settings)
                } else {
                    ""
                }
                ExitProcessService.startEngine(applicationContext, profile, settings, cfg)
            }
        }

        if (engineResult.isFailure) {
            failStart(engineResult.exceptionOrNull()?.message ?: "خطا در اجرای هسته")
            return
        }

        val socksPort = engineResult.getOrDefault(XrayConfigBuilder.LOCAL_SOCKS_PORT)
        val pfd = openTunInterface(settings)
        if (pfd == null) {
            failStart("ایجاد رابط TUN اندروید ممکن نشد (مجوز VPN بررسی شود)")
            return
        }
        tunFd = pfd

        val tp = TProxyService(applicationContext)
        tProxy = tp
        val tunStart = tp.start(pfd, settings.mtu, socksPort, settings.ipv6Enabled)
        if (tunStart.isFailure) {
            failStart(tunStart.exceptionOrNull()?.message ?: "خطا در اجرای hev-socks5-tunnel")
            return
        }

        _state.value = ConnectionState.CONNECTED
        _statusMessage.value = "متصل — ${profile.name}"
        startForeground(NOTIF_ID, buildNotification("متصل به ${profile.name}"))
        startStatsPolling(profile)
    }

    private fun openTunInterface(settings: AppSettings): ParcelFileDescriptor? = runCatching {
        val builder = Builder()
            .setSession("PNI VPN")
            .setMtu(settings.mtu)
            .addAddress("10.233.233.1", 30)
            .addRoute("0.0.0.0", 0)
            .addDnsServer("1.1.1.1")
            .addDnsServer("8.8.8.8")

        if (settings.ipv6Enabled) {
            builder.addAddress("fd00:233::1", 126)
            builder.addRoute("::", 0)
        }

        // Critical: exclude PNI itself so Xray/Sing-box/Aether/Psiphon/Tor bypass the VPN loop
        runCatching { builder.addDisallowedApplication(packageName) }

        if (settings.splitTunnelEnabled) {
            settings.excludedPackages.forEach { pkg ->
                runCatching { builder.addDisallowedApplication(pkg) }
            }
        }
        builder.establish()
    }.getOrNull()

    private fun startStatsPolling(profile: ProxyProfile) {
        statsJob?.cancel()
        statsJob = scope.launch {
            var prevTx = 0L
            var prevRx = 0L
            while (isActive && _state.value == ConnectionState.CONNECTED) {
                val stats = tProxy?.getStats()
                if (stats != null && stats.size >= 4) {
                    val tx = stats[1]
                    val rx = stats[3]
                    _txBytesPerSec.value = (tx - prevTx).coerceAtLeast(0L)
                    _rxBytesPerSec.value = (rx - prevRx).coerceAtLeast(0L)
                    prevTx = tx
                    prevRx = rx
                } else if (profile.protocol.engine == EngineKind.XRAY) {
                    val tx = CoreBridge.queryStats("proxy", "uplink")
                    val rx = CoreBridge.queryStats("proxy", "downlink")
                    _txBytesPerSec.value = (tx - prevTx).coerceAtLeast(0L)
                    _rxBytesPerSec.value = (rx - prevRx).coerceAtLeast(0L)
                    prevTx = tx
                    prevRx = rx
                }
                delay(1000)
            }
        }
    }

    private fun failStart(reason: String) {
        stopTunnelInternal()
        _state.value = ConnectionState.ERROR
        _statusMessage.value = reason
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun stopTunnel() {
        _state.value = ConnectionState.DISCONNECTING
        stopTunnelInternal()
        _state.value = ConnectionState.DISCONNECTED
        _statusMessage.value = "اتصال قطع شد"
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun stopTunnelInternal() {
        statsJob?.cancel()
        statsJob = null
        _txBytesPerSec.value = 0L
        _rxBytesPerSec.value = 0L

        tProxy?.stop()
        tProxy = null

        runCatching { tunFd?.close() }
        tunFd = null

        CoreBridge.stopXray()
        CoreBridge.stopSniSpoof()
        ExitProcessService.stop()
    }

    private fun buildNotification(text: String): Notification {
        val openPi = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE
        )
        val stopPi = PendingIntent.getService(
            this,
            1,
            Intent(this, PniVpnService::class.java).apply { action = ACTION_STOP },
            PendingIntent.FLAG_IMMUTABLE
        )
        return NotificationCompat.Builder(this, PniApplication.CHANNEL_VPN)
            .setSmallIcon(R.drawable.ic_pni_tile)
            .setContentTitle(getString(R.string.app_name))
            .setContentText(text)
            .setContentIntent(openPi)
            .addAction(0, getString(R.string.action_disconnect), stopPi)
            .setOngoing(true)
            .build()
    }
}
