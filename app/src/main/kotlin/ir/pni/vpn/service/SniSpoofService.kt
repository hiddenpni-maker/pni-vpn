package ir.pni.vpn.service

import android.app.Notification
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.os.IBinder
import androidx.core.app.NotificationCompat
import ir.pni.vpn.MainActivity
import ir.pni.vpn.PniApplication
import ir.pni.vpn.R
import ir.pni.vpn.core.CoreBridge
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Foreground service hosting Soldier-style local SNI Spoof Engine (`127.0.0.1:40443`).
 */
class SniSpoofService : Service() {

    companion object {
        const val ACTION_START = "ir.pni.vpn.SPOOF_START"
        const val ACTION_STOP = "ir.pni.vpn.SPOOF_STOP"
        const val EXTRA_LISTEN = "listen"
        const val EXTRA_TARGET = "target"
        const val EXTRA_FAKE_SNI = "fake_sni"
        const val EXTRA_METHOD = "method"
        const val EXTRA_FRAG_LEN = "frag_len"
        const val EXTRA_FRAG_DELAY = "frag_delay"
        private const val NOTIF_ID = 40443

        private val _running = MutableStateFlow(false)
        val running: StateFlow<Boolean> = _running.asStateFlow()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> {
                val listen = intent.getStringExtra(EXTRA_LISTEN) ?: "127.0.0.1:40443"
                val target = intent.getStringExtra(EXTRA_TARGET) ?: ""
                val fakeSni = intent.getStringExtra(EXTRA_FAKE_SNI) ?: ""
                val method = intent.getStringExtra(EXTRA_METHOD) ?: "fragment"
                val fragLen = intent.getIntExtra(EXTRA_FRAG_LEN, 40)
                val fragDelay = intent.getIntExtra(EXTRA_FRAG_DELAY, 2)

                startForeground(NOTIF_ID, buildNotification("$listen → $target"))
                val res = CoreBridge.startSniSpoof(listen, target, fakeSni, method, fragLen, fragDelay)
                _running.value = res.isSuccess
                if (res.isFailure) {
                    stopSelf()
                }
            }
            ACTION_STOP -> {
                CoreBridge.stopSniSpoof()
                _running.value = false
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
            }
        }
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        CoreBridge.stopSniSpoof()
        _running.value = false
        super.onDestroy()
    }

    private fun buildNotification(detail: String): Notification {
        val pi = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE
        )
        return NotificationCompat.Builder(this, PniApplication.CHANNEL_TOOLS)
            .setSmallIcon(R.drawable.ic_pni_tile)
            .setContentTitle(getString(R.string.spoof_service_title))
            .setContentText(detail)
            .setContentIntent(pi)
            .setOngoing(true)
            .build()
    }
}
