package ir.pni.vpn.service

import android.content.Intent
import android.net.VpnService
import android.os.Build
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import ir.pni.vpn.MainActivity
import ir.pni.vpn.data.model.ConnectionState

class PniQsTileService : TileService() {

    override fun onStartListening() {
        super.onStartListening()
        syncTile()
    }

    override fun onClick() {
        super.onClick()
        val state = PniVpnService.state.value
        if (state == ConnectionState.CONNECTED || state == ConnectionState.CONNECTING) {
            val stopIntent = Intent(this, PniVpnService::class.java).apply {
                action = PniVpnService.ACTION_STOP
            }
            startService(stopIntent)
        } else {
            // Prepare check: if VPN permission isn't granted yet, open MainActivity
            if (VpnService.prepare(this) != null) {
                val launch = Intent(this, MainActivity::class.java).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                    val pi = android.app.PendingIntent.getActivity(
                        this, 0, launch, android.app.PendingIntent.FLAG_IMMUTABLE
                    )
                    startActivityAndCollapse(pi)
                } else {
                    @Suppress("DEPRECATION")
                    startActivityAndCollapse(launch)
                }
                return
            }
            val startIntent = Intent(this, PniVpnService::class.java).apply {
                action = PniVpnService.ACTION_START_LAST
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                startForegroundService(startIntent)
            } else {
                startService(startIntent)
            }
        }
        syncTile()
    }

    private fun syncTile() {
        val tile = qsTile ?: return
        when (PniVpnService.state.value) {
            ConnectionState.CONNECTED -> {
                tile.state = Tile.STATE_ACTIVE
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    tile.subtitle = "متصل"
                }
            }
            ConnectionState.CONNECTING -> {
                tile.state = Tile.STATE_ACTIVE
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    tile.subtitle = "در حال اتصال..."
                }
            }
            else -> {
                tile.state = Tile.STATE_INACTIVE
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    tile.subtitle = "قطع"
                }
            }
        }
        tile.updateTile()
    }
}
