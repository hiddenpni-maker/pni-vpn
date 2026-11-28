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

            // Telemetry + Connected IP & Active Network Changes
            item {
                val gConnected = state.tunnelState as? TunnelState.Connected
                TunnelTelemetryRow(
                    rxSpeedBps = if (gamingConnected) state.rxSpeedBps else 0L,
                    txSpeedBps = if (gamingConnected) state.txSpeedBps else 0L,
                    sessionRxBytes = if (gamingConnected) state.sessionRxBytes else 0L,
                    sessionTxBytes = if (gamingConnected) state.sessionTxBytes else 0L,
                    pingMs = if (gamingConnected) gConnected?.pingMs else null,
                    modeLabel = if (isFa) state.appSettings.connectionMode.labelFa else state.appSettings.connectionMode.labelEn,
                    isConnectedForSection = gamingConnected,
                    isTestingPing = state.isTestingActivePing,
                    onRetestPing = viewModel::testActivePing,
                    exitIp = if (gamingConnected) gConnected?.exitIp.orEmpty() else "",
                    exitLocation = if (gamingConnected) gConnected?.exitLocation.orEmpty() else "",
                    networkChangeSummary = if (gamingConnected) gConnected?.networkChangeSummary.orEmpty() else "",
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
                        title = tr("DNS Spy & Hijack Guard", "محافظ ضدجعل DNS حین بازی"),
                        checked = state.gaming.enableDnsSpyOptimizer,
                        onCheckedChange = { c -> viewModel.updateGaming { it.copy(enableDnsSpyOptimizer = c) } },
                    )
                    PnifoxSwitchRow(
                        title = tr("Private DNS (No IP Registration)", "استفاده از DNS پرایوت بدون ثبت IP"),
                        checked = state.gaming.usePrivateDnsNoReg,
                        onCheckedChange = { c -> viewModel.updateGaming { it.copy(usePrivateDnsNoReg = c) } },
                    )
                }
            }

            // WhiteGame-inspired DNS Spy Optimizer & Game Publisher Live Ping Catalog
            item {
                val ctx = androidx.compose.ui.platform.LocalContext.current
                PnifoxCard(contentPadding = metrics.cardPadding) {
                    PnifoxSectionLabel(
                        title = tr("DNS Spy & Game Optimizer", "بهینه‌ساز DNS Spy و پینگ بازی‌ها"),
                        trailing = {
                            PnifoxInfoPopupButton(
                                title = tr("How to Use Game Optimizer", "راهنمای استفاده از بهینه‌ساز بازی"),
                                body = tr(
                                    "1. Tap 'DNS Spy' to detect hijacked ISP DNS (10.10.34.x) and rank clean gaming DNS without IP registration.\n2. Tap 'Use' on the fastest clean DNS (Radar, Electro, 403, Shecan).\n3. Tap 'Test Games' to measure live TLS delay for COD Mobile, Steam, Valorant & PUBG.\n4. Tap 'Ignore Battery Limits' so Android never kills your game tunnel.",
                                    "۱. دکمه «اسکن DNS Spy» را بزنید تا DNSهای مسموم/جعل‌شده (10.10.34.x) شناسایی و سریع‌ترین DNS سالم بدون نیاز به ثبت IP پیدا شود.\n۲. روی سریع‌ترین DNS سالم (رادار، الکترو، ۴۰۳، شکن) دکمه «اعمال» را بزنید.\n۳. برای تست پینگ زنده بازی‌ها (کالاف، استیم، ولورانت، پابجی) دکمه «تست بازی‌ها» را بزنید.\n۴. برای جلوگیری از قطع شدن در پس‌زمینه، «نادیده گرفتن باتری» را فعال کنید.\n۵. برای بازی با پنل‌های Cloud و BPB، کانفیگ را در تب سرورها وارد و حالت «سرور گیمینگ اختصاصی» را انتخاب کنید.",
                                ),
                            )
                        },
                    )
                    Spacer(Modifier.height(8.dp))
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(
                            onClick = viewModel::runDnsSpyOptimizer,
                            enabled = !state.isRunningDnsSpy,
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.weight(1f),
                        ) {
                            Text(
                                text = if (state.isRunningDnsSpy) tr("Scanning...", "در حال اسکن...") else tr("DNS Spy", "اسکن DNS Spy"),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                style = MaterialTheme.typography.labelSmall,
                            )
                        }
                        OutlinedButton(
                            onClick = viewModel::runGameCatalogPing,
                            enabled = !state.isProbingGames,
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.weight(1f),
                        ) {
                            Text(
                                text = if (state.isProbingGames) tr("Testing...", "در حال تست...") else tr("Test Games", "تست بازی‌ها"),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                style = MaterialTheme.typography.labelSmall,
                            )
                        }
                    }
                    Spacer(Modifier.height(6.dp))
                    OutlinedButton(
                        onClick = {
                            runCatching {
                                val intent = android.content.Intent(android.provider.Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                                    data = android.net.Uri.parse("package:${ctx.packageName}")
                                }
                                ctx.startActivity(intent)
                            }
                        },
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(
                            text = tr("Ignore Battery Restrictions (Android Booster)", "نادیده گرفتن محدودیت باتری (پایداری حین بازی)"),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            style = MaterialTheme.typography.labelSmall,
                        )
                    }
                    if (state.dnsSpyResults.isNotEmpty()) {
                        Spacer(Modifier.height(8.dp))
                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            state.dnsSpyResults.take(6).forEach { r ->
                                Surface(
                                    shape = RoundedCornerShape(10.dp),
                                    color = MaterialTheme.colorScheme.surfaceVariant,
                                    modifier = Modifier.fillMaxWidth(),
                                ) {
                                    Row(modifier = Modifier.padding(horizontal = 10.dp, vertical = 7.dp), verticalAlignment = Alignment.CenterVertically) {
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                text = "${if (isFa) r.resolver.nameFa else r.resolver.nameEn} (${r.resolver.ip})",
                                                style = MaterialTheme.typography.labelMedium,
                                                fontWeight = FontWeight.Bold,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis,
                                            )
                                            Text(
                                                text = if (isFa) r.statusNoteFa else r.statusNoteEn,
                                                style = MaterialTheme.typography.labelSmall,
                                                color = if (r.hijacked || r.latencyMs == null) MaterialTheme.colorScheme.error else androidx.compose.ui.graphics.Color(0xFF10B981),
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis,
                                            )
                                        }
                                        if (!r.hijacked && r.latencyMs != null) {
                                            OutlinedButton(
                                                onClick = { viewModel.updateGaming { it.copy(selectedDnsIp = r.resolver.ip) } },
                                                shape = RoundedCornerShape(8.dp),
                                            ) {
                                                Text(tr("Use", "اعمال"), style = MaterialTheme.typography.labelSmall, maxLines = 1)
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                    if (state.gameCatalogResults.isNotEmpty()) {
                        Spacer(Modifier.height(8.dp))
                        Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
                            state.gameCatalogResults.forEach { g ->
                                Surface(
                                    shape = RoundedCornerShape(10.dp),
                                    color = MaterialTheme.colorScheme.surfaceVariant,
                                    modifier = Modifier.fillMaxWidth(),
                                ) {
                                    Row(modifier = Modifier.padding(horizontal = 10.dp, vertical = 7.dp), verticalAlignment = Alignment.CenterVertically) {
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(g.game.title, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                            Text("${g.game.genre} • ${g.game.region}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
                                        }
                                        Text(
                                            text = g.latencyMs?.let { "${it}ms • Loss ${g.lossPct}%" } ?: tr("Unreachable", "غیرقابل دسترس"),
                                            style = MaterialTheme.typography.labelMedium,
                                            fontWeight = FontWeight.Bold,
                                            color = if (g.isGoodForGaming) androidx.compose.ui.graphics.Color(0xFF10B981) else MaterialTheme.colorScheme.error,
                                            maxLines = 1,
                                        )
                                    }
                                }
                            }
                        }
                    }
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

