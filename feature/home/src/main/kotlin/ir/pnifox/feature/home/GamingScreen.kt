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
import androidx.compose.material.icons.rounded.Dns
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Speed
import androidx.compose.material.icons.rounded.SportsEsports
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
import ir.pnifox.core.ui.PnifoxCard
import ir.pnifox.core.ui.PnifoxConnectButton
import ir.pnifox.core.ui.PnifoxDropdownField
import ir.pnifox.core.ui.PnifoxInfoPopupButton
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
    val selectedDnsPreset = gameDnsPresets.firstOrNull { it.address.substringBefore(':') == state.gaming.selectedDnsIp.substringBefore(':') }
        ?: gameDnsPresets.firstOrNull()

    val gamingMethodsHelp = remember(isFa) {
        GamingMode.entries.joinToString("\n\n") { gm ->
            "• ${if (isFa) gm.titleFa else gm.titleEn}\n${if (isFa) gm.subtitleFa else gm.subtitleEn}"
        }
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
                        text = tr("Gaming", "گیمینگ"),
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
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
                            Text(tr("TG", "تلگرام"), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold, maxLines = 1)
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
                            if (state.gaming.mode == GamingMode.CUSTOM_SERVER) {
                                Text(
                                    text = state.activeProfile?.let { "${it.name} (${it.address}:${it.port})" }
                                        ?: tr("Pick a server in Servers tab", "یک سرور در تب سرورها انتخاب کنید"),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.primary,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
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

            // Compact Gaming Method & DNS Selectors
            item {
                PnifoxCard(contentPadding = metrics.cardPadding) {
                    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        PnifoxSectionLabel(
                            title = tr("Gaming Method & DNS", "روش اتصال گیم و DNS"),
                            modifier = Modifier.weight(1f),
                        )
                        PnifoxInfoPopupButton(
                            title = tr("Gaming Methods", "توضیحات روش‌های گیمینگ"),
                            body = gamingMethodsHelp,
                        )
                    }
                    Spacer(Modifier.height(8.dp))
                    PnifoxDropdownField(
                        label = tr("Method", "روش"),
                        selectedText = if (isFa) state.gaming.mode.titleFa else state.gaming.mode.titleEn,
                        options = GamingMode.entries,
                        selectedItem = state.gaming.mode,
                        optionLabel = { gm -> if (isFa) gm.titleFa else gm.titleEn },
                        onSelect = { gm ->
                            viewModel.updateGaming { it.copy(mode = gm) }
                            if (gm == GamingMode.CUSTOM_SERVER && state.activeProfile == null) {
                                onNavigateServers()
                            }
                        },
                        icon = Icons.Rounded.SportsEsports,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Spacer(Modifier.height(8.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        PnifoxDropdownField(
                            label = "DNS",
                            selectedText = selectedDnsPreset?.let { preset ->
                                val ip = preset.address.substringBefore(':')
                                val ms = state.dnsPingMap[ip]
                                val pStr = if (ms != null) " (${ms}ms)" else ""
                                "${if (isFa) preset.titleFa else preset.titleEn}$pStr"
                            } ?: state.gaming.selectedDnsIp,
                            options = gameDnsPresets,
                            selectedItem = selectedDnsPreset,
                            optionLabel = { preset ->
                                val ip = preset.address.substringBefore(':')
                                val ms = state.dnsPingMap[ip]
                                val pStr = if (ms != null) " • ${ms}ms" else ""
                                "${if (isFa) preset.titleFa else preset.titleEn}$pStr"
                            },
                            onSelect = { preset ->
                                val ip = preset.address.substringBefore(':')
                                viewModel.updateGaming { it.copy(selectedDnsIp = ip) }
                            },
                            icon = Icons.Rounded.Dns,
                            modifier = Modifier.weight(1f),
                        )
                        OutlinedButton(
                            onClick = viewModel::testGamingDnsPings,
                            enabled = !state.isTestingDnsPing,
                            shape = RoundedCornerShape(10.dp),
                        ) {
                            Icon(Icons.Rounded.Speed, null, modifier = Modifier.size(15.dp))
                            Spacer(Modifier.width(4.dp))
                            Text(if (state.isTestingDnsPing) "..." else tr("Ping", "پینگ"), style = MaterialTheme.typography.labelSmall, maxLines = 1)
                        }
                    }
                }
            }

            // Tuning & Local Desync Fragment slider
            item {
                PnifoxCard(contentPadding = metrics.cardPadding) {
                    PnifoxSectionLabel(title = tr("Gaming Tuning", "تنظیمات گیمینگ"))
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
                        checked = state.gaming.bypassIranGameTraffic,
                        onCheckedChange = { c -> viewModel.updateGaming { it.copy(bypassIranGameTraffic = c) } },
                    )
                    PnifoxSwitchRow(
                        title = tr("QUIC v2 on WARP Gaming", "استفاده از QUIC v2 در تانل گیم"),
                        checked = state.gaming.preferQuicV2H3,
                        onCheckedChange = { c -> viewModel.updateGaming { it.copy(preferQuicV2H3 = c) } },
                    )
                }
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
}
