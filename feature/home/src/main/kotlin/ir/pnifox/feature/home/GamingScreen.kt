package ir.pnifox.feature.home

import android.app.Activity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Send
import androidx.compose.material.icons.rounded.Code
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Speed
import androidx.compose.material.icons.rounded.SportsEsports
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import ir.pnifox.core.model.ActiveSectionEngine
import ir.pnifox.core.model.AppLanguage
import ir.pnifox.core.model.ConnectionMode
import ir.pnifox.core.model.GamingMode
import ir.pnifox.core.model.IranPresetsCatalog
import ir.pnifox.core.model.TunnelState
import ir.pnifox.core.ui.CoreLogsModalSheet
import ir.pnifox.core.ui.FoxConnectionMascot
import ir.pnifox.core.ui.LiveActivityLogPanel
import ir.pnifox.core.ui.LocalAppLanguage
import ir.pnifox.core.ui.PnifoxBadgePill
import ir.pnifox.core.ui.PnifoxCard
import ir.pnifox.core.ui.PnifoxConnectButton
import ir.pnifox.core.ui.PnifoxSectionLabel
import ir.pnifox.core.ui.PnifoxSwitchRow
import ir.pnifox.core.ui.ResponsiveScreenContainer
import ir.pnifox.core.ui.TunnelTelemetryRow
import ir.pnifox.core.ui.responsiveListPadding
import ir.pnifox.core.ui.tr

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun GamingRoute(
    onNavigateSection: (ActiveSectionEngine) -> Unit,
    onNavigateServers: () -> Unit,
    onNavigateSettings: () -> Unit,
    onOpenTelegram: () -> Unit,
    viewModel: GamingViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val isFa = LocalAppLanguage.current == AppLanguage.PERSIAN
    val clipboard = LocalClipboardManager.current
    var showLogsSheet by remember { mutableStateOf(false) }
    var showProtocolGuide by remember { mutableStateOf(false) }

    val vpnLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { res ->
        if (res.resultCode == Activity.RESULT_OK) {
            viewModel.startGamingAfterPermission()
        }
    }

    val onConnectGaming = {
        val active = (state.tunnelState is TunnelState.Connected && (state.tunnelState as TunnelState.Connected).section == ActiveSectionEngine.GAMING) ||
            (state.tunnelState is TunnelState.Connecting && (state.tunnelState as TunnelState.Connecting).section == ActiveSectionEngine.GAMING)
        if (!active && state.appSettings.connectionMode == ConnectionMode.VPN) {
            val perm = viewModel.prepareVpnIntent()
            if (perm != null) {
                val launched = runCatching { vpnLauncher.launch(perm) }.isSuccess
                if (!launched) viewModel.toggleGamingConnection()
            } else {
                viewModel.toggleGamingConnection()
            }
        } else {
            viewModel.toggleGamingConnection()
        }
    }

    val gamingConnected = state.tunnelState is TunnelState.Connected &&
        (state.tunnelState as TunnelState.Connected).section == ActiveSectionEngine.GAMING
    val gamingConnecting = state.tunnelState is TunnelState.Connecting &&
        (state.tunnelState as TunnelState.Connecting).section == ActiveSectionEngine.GAMING

    val gameDnsPresets = remember {
        IranPresetsCatalog.dnsPresets.filter { it.badge == "Game" || it.id in setOf("cf_udp_1", "cf_pdoq", "quad9_unfiltered") }
    }

    ResponsiveScreenContainer { metrics ->
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = responsiveListPadding(metrics),
            verticalArrangement = Arrangement.spacedBy(metrics.sectionSpacing),
        ) {
            // Top bar with corner Telegram button
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(Icons.Rounded.SportsEsports, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(24.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = tr("Gaming & Low Ping", "گیم و پینگ پایین"),
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.weight(1f),
                    )
                    Surface(
                        shape = RoundedCornerShape(999.dp),
                        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.14f),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.4f)),
                        modifier = Modifier.clickable(onClick = onOpenTelegram),
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(Icons.AutoMirrored.Rounded.Send, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(14.dp))
                            Spacer(Modifier.width(4.dp))
                            Text(tr("Telegram", "تلگرام"), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                        }
                    }
                    IconButton(onClick = { showLogsSheet = true }, modifier = Modifier.size(36.dp)) {
                        Icon(Icons.Rounded.Code, contentDescription = "Logs")
                    }
                    IconButton(onClick = onNavigateSettings, modifier = Modifier.size(36.dp)) {
                        Icon(Icons.Rounded.Settings, contentDescription = "Settings")
                    }
                }
            }

            // Connect Hero
            item {
                PnifoxCard(contentPadding = metrics.cardPadding) {
                    FoxConnectionMascot(
                        tunnelState = state.tunnelState,
                        section = ActiveSectionEngine.GAMING,
                        onSuggestionClick = { showLogsSheet = true },
                    )
                    Spacer(Modifier.height(12.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = if (isFa) state.gaming.mode.titleFa else state.gaming.mode.titleEn,
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            Text(
                                text = if (state.gaming.mode == GamingMode.CUSTOM_SERVER) {
                                    state.activeProfile?.let { "${it.name} (${it.address}:${it.port})" }
                                        ?: tr("Pick a low-ping server in Servers tab", "یک سرور از تب سرورها انتخاب کنید")
                                } else {
                                    if (isFa) state.gaming.mode.subtitleFa else state.gaming.mode.subtitleEn
                                },
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                        Spacer(Modifier.width(12.dp))
                        PnifoxConnectButton(
                            isConnected = gamingConnected,
                            isConnecting = gamingConnecting,
                            onClick = onConnectGaming,
                        )
                    }
                }
            }

            // Telemetry
            item {
                TunnelTelemetryRow(
                    rxSpeedBps = if (gamingConnected) state.rxSpeedBps else 0L,
                    txSpeedBps = if (gamingConnected) state.txSpeedBps else 0L,
                    sessionRxBytes = if (gamingConnected) state.sessionRxBytes else 0L,
                    sessionTxBytes = if (gamingConnected) state.sessionTxBytes else 0L,
                    pingMs = if (gamingConnected) (state.tunnelState as? TunnelState.Connected)?.pingMs else null,
                    modeLabel = if (isFa) state.appSettings.connectionMode.labelFa else state.appSettings.connectionMode.labelEn,
                    isConnectedForSection = gamingConnected,
                    isTestingPing = state.isTestingActivePing,
                    onRetestPing = viewModel::testActivePing,
                )
            }

            // Live Activity Logs
            item {
                LiveActivityLogPanel(
                    tunnelState = state.tunnelState,
                    section = ActiveSectionEngine.GAMING,
                    logs = state.logs,
                    onCopy = { clipboard.setText(AnnotatedString(viewModel.exportLogs())) },
                    onExpandAll = { showLogsSheet = true },
                    cardPadding = metrics.cardPadding,
                )
            }

            // Gaming Modes List
            item {
                PnifoxCard(contentPadding = metrics.cardPadding) {
                    PnifoxSectionLabel(title = tr("Select Gaming Method", "انتخاب روش اتصال گیم"))
                    Spacer(Modifier.height(8.dp))
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        GamingMode.entries.forEach { gm ->
                            val selected = state.gaming.mode == gm
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = if (selected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.7f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                                border = BorderStroke(if (selected) 1.5.dp else 1.dp, if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)),
                                modifier = Modifier.fillMaxWidth().clickable {
                                    viewModel.updateGaming { it.copy(mode = gm) }
                                    if (gm == GamingMode.CUSTOM_SERVER && state.activeProfile == null) {
                                        onNavigateServers()
                                    }
                                },
                            ) {
                                Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                        Text(
                                            text = if (isFa) gm.titleFa else gm.titleEn,
                                            fontWeight = FontWeight.Bold,
                                            style = MaterialTheme.typography.bodyMedium,
                                            modifier = Modifier.weight(1f),
                                        )
                                        if (selected) {
                                            PnifoxBadgePill(
                                                text = tr("Active", "فعال"),
                                                containerColor = MaterialTheme.colorScheme.primary,
                                                contentColor = MaterialTheme.colorScheme.onPrimary,
                                            )
                                        }
                                    }
                                    Text(if (isFa) gm.subtitleFa else gm.subtitleEn, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                        }
                    }
                }
            }

            // Anti-Sanction Gaming DNS + Zero-ICMP Ping
            item {
                PnifoxCard(contentPadding = metrics.cardPadding) {
                    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        PnifoxSectionLabel(
                            title = tr("Gaming Anti-Sanction DNS", "DNS ضدتحریم گیمینگ"),
                            modifier = Modifier.weight(1f),
                        )
                        OutlinedButton(
                            onClick = viewModel::testGamingDnsPings,
                            enabled = !state.isTestingDnsPing,
                            shape = RoundedCornerShape(10.dp),
                        ) {
                            Icon(Icons.Rounded.Speed, null, modifier = Modifier.size(15.dp))
                            Spacer(Modifier.width(4.dp))
                            Text(if (state.isTestingDnsPing) "..." else tr("Ping DNS", "تست پینگ"), style = MaterialTheme.typography.labelSmall)
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        gameDnsPresets.forEach { preset ->
                            val ip = preset.address.substringBefore(':')
                            val ms = state.dnsPingMap[ip]
                            val suffix = if (ms != null) " • ${ms}ms" else ""
                            FilterChip(
                                selected = state.gaming.selectedDnsIp.substringBefore(':') == ip,
                                onClick = { viewModel.updateGaming { it.copy(selectedDnsIp = ip) } },
                                label = { Text("${if (isFa) preset.titleFa else preset.titleEn}$suffix", maxLines = 1) },
                            )
                        }
                    }
                }
            }

            // Tuning & Local Desync Fragment slider
            item {
                PnifoxCard(contentPadding = metrics.cardPadding) {
                    PnifoxSectionLabel(title = tr("Gaming & Anti-DPI Tuning", "تنظیمات گیم و ضدDPI محلی"))
                    Spacer(Modifier.height(6.dp))
                    if (state.gaming.mode == GamingMode.LOCAL_DPI_DESYNC) {
                        Text(
                            text = tr("TLS Fragment Chunk Size: ${state.gaming.desyncChunkLen} bytes", "اندازه تکه‌سازی TLS: ${state.gaming.desyncChunkLen} بایت"),
                            style = MaterialTheme.typography.bodySmall,
                        )
                        Slider(
                            value = state.gaming.desyncChunkLen.toFloat(),
                            onValueChange = { v -> viewModel.updateGaming { it.copy(desyncChunkLen = v.toInt().coerceIn(2, 16)) } },
                            valueRange = 2f..16f,
                            steps = 13,
                        )
                    }
                    PnifoxSwitchRow(
                        title = tr("Bypass Iranian Game Servers", "عبور مستقیم سرورهای گیم داخلی"),
                        subtitle = tr("Keeps domestic downloads/lobbies directly on your ISP.", "ترافیک ایران مستقیما از اینترنت خودتان رد شود."),
                        checked = state.gaming.bypassIranGameTraffic,
                        onCheckedChange = { c -> viewModel.updateGaming { it.copy(bypassIranGameTraffic = c) } },
                    )
                    PnifoxSwitchRow(
                        title = tr("Enable QUIC v2 on WARP Gaming", "استفاده از QUIC v2 در تانل گیم"),
                        subtitle = tr("Reduces UDP jitter & packet-loss impact.", "کاهش نوسان پینگ روی شبکه موبایل و وای‌فای."),
                        checked = state.gaming.preferQuicV2H3,
                        onCheckedChange = { c -> viewModel.updateGaming { it.copy(preferQuicV2H3 = c) } },
                    )
                }
            }

            // Goal-Based Scenarios inside Gaming too
            item {
                ScenarioPresetsCard(
                    selectedScenario = state.selectedScenario,
                    isSmartConnecting = state.isSmartConnecting,
                    smartConnectStatus = state.smartConnectStatus,
                    cardPadding = metrics.cardPadding,
                    onApplyScenario = { prof, connect ->
                        viewModel.applyScenario(prof, connect, onNavigateSection)
                    },
                    onSmartAutoConnect = viewModel::smartAutoConnect,
                    onOpenProtocolGuide = { showProtocolGuide = true },
                )
            }
        }
    }

    if (showLogsSheet) {
        CoreLogsModalSheet(
            logs = state.logs,
            onCopy = { clipboard.setText(AnnotatedString(viewModel.exportLogs())) },
            onClear = viewModel::clearLogs,
            onDismiss = { showLogsSheet = false },
        )
    }

    if (showProtocolGuide) {
        ProtocolGuideDialog(onDismiss = { showProtocolGuide = false })
    }
}
