package ir.pnifox.app.service

import android.os.Build
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import dagger.hilt.android.AndroidEntryPoint
import ir.pnifox.core.model.TunnelState
import ir.pnifox.core.vpn.VpnController
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class PniQsTileService : TileService() {

    @Inject lateinit var vpnController: VpnController

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var collectorJob: Job? = null

    override fun onStartListening() {
        super.onStartListening()
        collectorJob?.cancel()
        collectorJob = scope.launch {
            vpnController.tunnelState.collect { state ->
                val tile = qsTile ?: return@collect
                when (state) {
                    is TunnelState.Connected -> {
                        tile.state = Tile.STATE_ACTIVE
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) tile.subtitle = "Connected"
                    }
                    is TunnelState.Connecting -> {
                        tile.state = Tile.STATE_ACTIVE
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) tile.subtitle = "Connecting"
                    }
                    else -> {
                        tile.state = Tile.STATE_INACTIVE
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) tile.subtitle = "Off"
                    }
                }
                tile.updateTile()
            }
        }
    }

    override fun onClick() {
        super.onClick()
        scope.launch { vpnController.connectOrToggle() }
    }

    override fun onStopListening() {
        collectorJob?.cancel()
        super.onStopListening()
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }
}
