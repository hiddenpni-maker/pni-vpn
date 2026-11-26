package ir.pnifox.feature.stats

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import ir.pnifox.core.database.PnifoxDataRepository
import ir.pnifox.core.model.TrafficSummary
import ir.pnifox.core.model.TrafficTimeframe
import ir.pnifox.core.vpn.VpnController
import ir.pnifox.core.vpn.VpnStateHolder
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class StatsUiState(
    val timeframe: TrafficTimeframe = TrafficTimeframe.SESSION,
    val summary: TrafficSummary = TrafficSummary(),
)

@HiltViewModel
class StatsViewModel @Inject constructor(
    private val dataRepo: PnifoxDataRepository,
    private val vpnController: VpnController,
    private val stateHolder: VpnStateHolder,
) : ViewModel() {

    private val timeframe = MutableStateFlow(TrafficTimeframe.SESSION)

    val uiState: StateFlow<StatsUiState> = combine(
        timeframe,
        dataRepo.observeTrafficBuckets30Days(),
        vpnController.rxSpeedBps,
        vpnController.txSpeedBps,
        vpnController.sessionRxBytes,
        vpnController.sessionTxBytes,
    ) { values ->
        val tf = values[0] as TrafficTimeframe
        val base = values[1] as TrafficSummary
        val rxSpeed = values[2] as Long
        val txSpeed = values[3] as Long
        val sRx = values[4] as Long
        val sTx = values[5] as Long
        StatsUiState(
            timeframe = tf,
            summary = base.copy(
                rxSpeedBps = rxSpeed,
                txSpeedBps = txSpeed,
                sessionRxBytes = sRx,
                sessionTxBytes = sTx,
            ),
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000L),
        initialValue = StatsUiState(),
    )

    fun selectTimeframe(tf: TrafficTimeframe) {
        timeframe.value = tf
    }

    fun resetAllStats() {
        viewModelScope.launch {
            stateHolder.resetSessionCounters()
            dataRepo.resetTrafficStats()
        }
    }
}
