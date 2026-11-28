package ir.pnifox.core.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowDownward
import androidx.compose.material.icons.rounded.ArrowUpward
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.OpenInFull
import androidx.compose.material.icons.rounded.Speed
import androidx.compose.material.icons.rounded.Terminal
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import ir.pnifox.core.model.ActiveSectionEngine
import ir.pnifox.core.model.AppLanguage
import ir.pnifox.core.model.LogEntry
import ir.pnifox.core.model.LogLevel
import ir.pnifox.core.model.TunnelState

/**
 * Equal-width live telemetry strip shared across AETHER, Custom Servers, and DNS sections:
 * Download speed/total, Upload speed/total, and clickable Real Delay Ping tile.
 */
@Composable
fun TunnelTelemetryRow(
    rxSpeedBps: Long,
    txSpeedBps: Long,
    sessionRxBytes: Long,
    sessionTxBytes: Long,
    pingMs: Int?,
    modeLabel: String,
    isConnectedForSection: Boolean,
    isTestingPing: Boolean = false,
    onRetestPing: (() -> Unit)? = null,
    exitIp: String = "",
    exitLocation: String = "",
    networkChangeSummary: String = "",
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        TelemetryMetricTile(
            title = tr("Download", "دانلود"),
            value = formatSpeedBps(rxSpeedBps),
            sub = formatBytes(sessionRxBytes),
            icon = Icons.Rounded.ArrowDownward,
            tint = Color(0xFF10B981),
            modifier = Modifier.weight(1f),
        )
        TelemetryMetricTile(
            title = tr("Upload", "آپلود"),
            value = formatSpeedBps(txSpeedBps),
            sub = formatBytes(sessionTxBytes),
            icon = Icons.Rounded.ArrowUpward,
            tint = Color(0xFFF97316),
            modifier = Modifier.weight(1f),
        )
        val pingValue = when {
            isTestingPing -> tr("Testing...", "تست...")
            isConnectedForSection && pingMs != null && pingMs > 0 -> "$pingMs ms"
            isConnectedForSection && pingMs != null && pingMs < 0 -> tr("Timeout", "تایم‌اوت")
            isConnectedForSection -> tr("Test Ping", "تست پینگ")
            pingMs != null && pingMs > 0 -> "$pingMs ms"
            else -> ":10820"
        }
        val pingSub = if (isConnectedForSection) {
            tr("Real Delay · Tap", "پینگ واقعی · لمس")
        } else {
            modeLabel
        }
        TelemetryMetricTile(
            title = tr("Real Ping", "پینگ واقعی"),
            value = pingValue,
            sub = pingSub,
            icon = Icons.Rounded.Speed,
            tint = MaterialTheme.colorScheme.primary,
            onClick = if (isConnectedForSection && !isTestingPing) onRetestPing else null,
            modifier = Modifier.weight(1f),
        )
    }
    if (isConnectedForSection && (exitIp.isNotBlank() || networkChangeSummary.isNotBlank())) {
        PnifoxCard(
            containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f),
            contentPadding = 10.dp,
        ) {
            if (exitIp.isNotBlank()) {
                Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = tr("Connected IP: ", "آی‌پی اتصال: "),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        text = if (exitLocation.isNotBlank()) "$exitIp ($exitLocation)" else exitIp,
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF10B981),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                    )
                }
            }
            if (networkChangeSummary.isNotBlank()) {
                Spacer(Modifier.height(2.dp))
                Text(
                    text = tr("Network changes: $networkChangeSummary", "تغییرات شبکه: $networkChangeSummary"),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
    }
}

@Composable
private fun TelemetryMetricTile(
    title: String,
    value: String,
    sub: String,
    icon: ImageVector,
    tint: Color,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
) {
    PnifoxCard(
        modifier = modifier.let { m -> if (onClick != null) m.clickable(onClick = onClick) else m },
        contentPadding = 12.dp,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, null, tint = tint, modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(4.dp))
            Text(
                text = title,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Spacer(Modifier.height(6.dp))
        Text(
            text = value,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            text = sub,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/**
 * Inline live execution & scan log monitor card shared across AETHER, Custom Servers, and DNS screens.
 */
@Composable
fun LiveActivityLogPanel(
    tunnelState: TunnelState,
    section: ActiveSectionEngine,
    logs: List<LogEntry>,
    onCopy: () -> Unit,
    onExpandAll: () -> Unit,
    cardPadding: Dp,
    modifier: Modifier = Modifier,
) {
    val isFa = LocalAppLanguage.current == AppLanguage.PERSIAN
    val recent = remember(logs) { logs.takeLast(25) }
    val listState = rememberLazyListState()
    LaunchedEffect(recent.size) {
        if (recent.isNotEmpty()) {
            runCatching { listState.animateScrollToItem(recent.lastIndex) }
        }
    }

    val stageLine = when (tunnelState) {
        is TunnelState.Connecting -> if (tunnelState.section == section) {
            if (isFa) tunnelState.stageLabelFa else tunnelState.stageLabelEn
        } else {
            tr("Other core connecting...", "هسته دیگری در حال اتصال...")
        }
        is TunnelState.Connected -> if (tunnelState.section == section) {
            tr("Connected • ${tunnelState.activeEndpoint}", "متصل • ${tunnelState.activeEndpoint}")
        } else {
            tr("Active on ${tunnelState.section.titleEn}", "متصل روی ${tunnelState.section.titleFa}")
        }
        is TunnelState.Error -> if (tunnelState.section == section) {
            tr("Connection stopped", "خطا در اتصال")
        } else {
            tr("Idle — live core log monitor", "آماده — نمایشگر زنده لاگ")
        }
        TunnelState.Disconnected -> tr("Idle — ready to connect", "آماده — گزارش زنده اتصال")
    }
    val dotColor = when (tunnelState) {
        is TunnelState.Connected -> if (tunnelState.section == section) Color(0xFF10B981) else Color(0xFF38BDF8)
        is TunnelState.Connecting -> Color(0xFFF97316)
        is TunnelState.Error -> if (tunnelState.section == section) Color(0xFFEF4444) else Color(0xFF94A3B8)
        TunnelState.Disconnected -> Color(0xFF94A3B8)
    }

    PnifoxCard(
        modifier = modifier,
        containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f),
        contentPadding = cardPadding,
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(9.dp)
                    .clip(CircleShape)
                    .background(dotColor),
            )
            Spacer(Modifier.width(8.dp))
            Icon(Icons.Rounded.Terminal, null, modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.width(6.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = tr("Live Core & Connection Monitor", "نمایش زنده مراحل اتصال و لاگ"),
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = stageLine,
                    style = MaterialTheme.typography.labelSmall,
                    color = dotColor,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            IconButton(onClick = onCopy, modifier = Modifier.size(30.dp)) {
                Icon(Icons.Rounded.ContentCopy, contentDescription = "Copy Logs", modifier = Modifier.size(16.dp))
            }
            IconButton(onClick = onExpandAll, modifier = Modifier.size(30.dp)) {
                Icon(Icons.Rounded.OpenInFull, contentDescription = "Open Full Logs", modifier = Modifier.size(16.dp))
            }
        }
        Spacer(Modifier.height(8.dp))
        Surface(
            shape = RoundedCornerShape(10.dp),
            color = Color(0xFF0D1117),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.45f)),
            modifier = Modifier
                .fillMaxWidth()
                .height(138.dp),
        ) {
            if (recent.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(12.dp),
                    contentAlignment = Alignment.CenterStart,
                ) {
                    Text(
                        text = tr(
                            "Tap Connect to watch live core startup, delay tests, and tunnel diagnostics here...",
                            "پس از زدن اتصال، جزئیات راه‌اندازی هسته، تست پینگ واقعی و وضعیت تونل اینجا نمایش داده می‌شود...",
                        ),
                        style = MaterialTheme.typography.labelSmall.copy(fontFamily = FontFamily.Monospace),
                        color = Color(0xFF8B949E),
                    )
                }
            } else {
                LazyColumn(
                    state = listState,
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(8.dp),
                    verticalArrangement = Arrangement.spacedBy(3.dp),
                ) {
                    items(recent) { entry ->
                        val msg = entry.message
                        val lineColor = when {
                            entry.level == LogLevel.ERROR -> Color(0xFFF87171)
                            entry.level == LogLevel.WARN -> Color(0xFFFBBF24)
                            msg.contains("[+]") || msg.contains("confirmed ready") -> Color(0xFF34D399)
                            msg.contains("candidate ok") || msg.contains("RealDelay") -> Color(0xFF38BDF8)
                            else -> Color(0xFFD1D5DB)
                        }
                        Text(
                            text = "[${entry.timestamp}] ${entry.tag}: $msg",
                            style = MaterialTheme.typography.labelSmall.copy(fontFamily = FontFamily.Monospace),
                            color = lineColor,
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CoreLogsModalSheet(
    logs: List<LogEntry>,
    onCopy: () -> Unit,
    onClear: () -> Unit,
    onDismiss: () -> Unit,
) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    text = tr("Core & Execution Logs", "لاگ کامل هسته و عیب‌یابی"),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                )
                Row {
                    TextButton(onClick = onCopy) { Text(tr("Copy", "کپی")) }
                    TextButton(onClick = onClear) { Text(tr("Clear", "پاک کردن")) }
                }
            }
            Spacer(Modifier.height(8.dp))
            if (logs.isEmpty()) {
                Text(
                    text = tr("No events logged yet.", "هنوز رویدادی ثبت نشده است."),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(vertical = 20.dp),
                )
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(320.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    items(logs) { entry ->
                        val color = when (entry.level) {
                            LogLevel.ERROR -> MaterialTheme.colorScheme.error
                            LogLevel.WARN -> Color(0xFFF59E0B)
                            LogLevel.INFO -> MaterialTheme.colorScheme.onSurfaceVariant
                        }
                        Text(
                            text = "[${entry.timestamp}] ${entry.tag}: ${entry.message}",
                            style = MaterialTheme.typography.labelSmall.copy(fontFamily = FontFamily.Monospace),
                            color = color,
                        )
                    }
                }
            }
            Spacer(Modifier.height(16.dp))
        }
    }
}
