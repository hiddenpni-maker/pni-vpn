package ir.pnifox.feature.stats

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import ir.pnifox.core.model.AppLanguage
import ir.pnifox.core.model.TrafficChartPoint
import ir.pnifox.core.model.TrafficTimeframe
import ir.pnifox.core.ui.LocalAppLanguage
import ir.pnifox.core.ui.PnifoxCard
import ir.pnifox.core.ui.PnifoxSectionLabel
import ir.pnifox.core.ui.ResponsiveScreenContainer
import ir.pnifox.core.ui.formatBytes
import ir.pnifox.core.ui.formatSpeedBps
import ir.pnifox.core.ui.responsiveListPadding
import ir.pnifox.core.ui.tr

@Composable
fun StatsRoute(
    viewModel: StatsViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val isFa = LocalAppLanguage.current == AppLanguage.PERSIAN
    var confirmReset by remember { mutableStateOf(false) }

    val (selectedRx, selectedTx) = when (state.timeframe) {
        TrafficTimeframe.SESSION -> state.summary.sessionRxBytes to state.summary.sessionTxBytes
        TrafficTimeframe.DAILY -> state.summary.todayRxBytes to state.summary.todayTxBytes
        TrafficTimeframe.WEEKLY -> state.summary.weekRxBytes to state.summary.weekTxBytes
        TrafficTimeframe.MONTHLY -> state.summary.monthRxBytes to state.summary.monthTxBytes
    }

    ResponsiveScreenContainer { metrics ->
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = responsiveListPadding(metrics),
            verticalArrangement = Arrangement.spacedBy(metrics.sectionSpacing),
        ) {
            item {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(
                        text = tr("Traffic & Speed Statistics", "آمار مصرف و سرعت"),
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                    )
                    OutlinedButton(onClick = { confirmReset = true }) { Text(tr("Reset", "بازنشانی")) }
                }
            }

            item {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(TrafficTimeframe.entries) { tf ->
                        FilterChip(
                            selected = state.timeframe == tf,
                            onClick = { viewModel.selectTimeframe(tf) },
                            label = { Text(if (isFa) tf.labelFa else tf.labelEn) },
                        )
                    }
                }
            }

            item {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    val tfLabel = if (isFa) state.timeframe.labelFa else state.timeframe.labelEn
                    PnifoxCard(modifier = Modifier.weight(1f), contentPadding = metrics.cardPadding) {
                        Text(tr("Download ($tfLabel)", "دانلود ($tfLabel)"), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Spacer(Modifier.height(6.dp))
                        Text(formatBytes(selectedRx), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = Color(0xFF10B981))
                        Text("Rate: ${formatSpeedBps(state.summary.rxSpeedBps)}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    PnifoxCard(modifier = Modifier.weight(1f), contentPadding = metrics.cardPadding) {
                        Text(tr("Upload ($tfLabel)", "آپلود ($tfLabel)"), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Spacer(Modifier.height(6.dp))
                        Text(formatBytes(selectedTx), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = Color(0xFFF97316))
                        Text("Rate: ${formatSpeedBps(state.summary.txSpeedBps)}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }

            item {
                PnifoxCard(contentPadding = metrics.cardPadding) {
                    PnifoxSectionLabel(title = tr("7-Day Usage History", "نمودار مصرف ۷ روز گذشته"))
                    Spacer(Modifier.height(12.dp))
                    TrafficSevenDayChart(points = state.summary.chartPoints)
                }
            }
        }
    }

    if (confirmReset) {
        AlertDialog(
            onDismissRequest = { confirmReset = false },
            title = { Text(tr("Reset all traffic statistics?", "پاک‌کردن تمام آمار مصرف؟")) },
            text = { Text(tr("Session, daily, weekly, and monthly counters will be cleared.", "سوابق نشست فعلی، روزانه، هفتگی و ماهانه صفر می‌شوند.")) },
            confirmButton = {
                Button(
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                    onClick = { viewModel.resetAllStats(); confirmReset = false },
                ) { Text(tr("Reset", "بازنشانی")) }
            },
            dismissButton = {
                TextButton(onClick = { confirmReset = false }) { Text(tr("Cancel", "انصراف")) }
            },
        )
    }
}

@Composable
private fun TrafficSevenDayChart(points: List<TrafficChartPoint>) {
    val maxBytes = points.maxOfOrNull { (it.rxBytes + it.txBytes).coerceAtLeast(1L) } ?: 1L
    val rxColor = Color(0xFF10B981)
    val txColor = Color(0xFFF97316)
    val trackColor = MaterialTheme.colorScheme.surfaceVariant

    Column(modifier = Modifier.fillMaxWidth()) {
        Canvas(modifier = Modifier.fillMaxWidth().height(140.dp).padding(horizontal = 4.dp)) {
            if (points.isEmpty()) return@Canvas
            val slotWidth = size.width / points.size
            val barWidth = (slotWidth * 0.44f).coerceAtLeast(10f)

            points.forEachIndexed { idx, pt ->
                val left = idx * slotWidth + (slotWidth - barWidth) / 2f
                drawRoundRect(color = trackColor, topLeft = Offset(left, 0f), size = Size(barWidth, size.height), cornerRadius = CornerRadius(8f, 8f))
                val rxH = (pt.rxBytes.toFloat() / maxBytes.toFloat()).coerceIn(0f, 1f) * size.height
                val txH = (pt.txBytes.toFloat() / maxBytes.toFloat()).coerceIn(0f, 1f) * size.height
                if (rxH > 1f) drawRoundRect(color = rxColor, topLeft = Offset(left, size.height - rxH), size = Size(barWidth, rxH), cornerRadius = CornerRadius(8f, 8f))
                if (txH > 1f) drawRoundRect(color = txColor, topLeft = Offset(left, (size.height - rxH - txH).coerceAtLeast(0f)), size = Size(barWidth, txH.coerceAtMost(size.height)), cornerRadius = CornerRadius(8f, 8f))
            }
        }
        Spacer(Modifier.height(6.dp))
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            points.forEach { pt ->
                Text(text = pt.label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
            }
        }
    }
}
