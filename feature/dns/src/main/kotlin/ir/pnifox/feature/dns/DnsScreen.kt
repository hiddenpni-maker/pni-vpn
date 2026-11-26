package ir.pnifox.feature.dns

import android.app.Activity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Dns
import androidx.compose.material.icons.rounded.Remove
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import ir.pnifox.core.model.ActiveSectionEngine
import ir.pnifox.core.model.AppLanguage
import ir.pnifox.core.model.ConnectionMode
import ir.pnifox.core.model.DnsEngineType
import ir.pnifox.core.model.DnsInnerProtocol
import ir.pnifox.core.model.DnsTunnelMode
import ir.pnifox.core.model.TunnelState
import ir.pnifox.core.ui.CoreLogsModalSheet
import ir.pnifox.core.ui.FoxConnectionMascot
import ir.pnifox.core.ui.LiveActivityLogPanel
import ir.pnifox.core.ui.LocalAppLanguage
import ir.pnifox.core.ui.PnifoxBadgePill
import ir.pnifox.core.ui.PnifoxCard
import ir.pnifox.core.ui.PnifoxConnectButton
import ir.pnifox.core.ui.PnifoxSectionLabel
import ir.pnifox.core.ui.ResponsiveScreenContainer
import ir.pnifox.core.ui.RulesetsBottomSheet
import ir.pnifox.core.ui.TunnelTelemetryRow
import ir.pnifox.core.ui.responsiveListPadding
import ir.pnifox.core.ui.tr

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun DnsRoute(
    onNavigateScanner: () -> Unit = {},
    viewModel: DnsViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val isFa = LocalAppLanguage.current == AppLanguage.PERSIAN
    val clipboard = LocalClipboardManager.current
    var showServerPicker by remember { mutableStateOf(false) }
    var showFreqDialog by remember { mutableStateOf(false) }
    var showLogsSheet by remember { mutableStateOf(false) }
    var showRulesetsSheet by remember { mutableStateOf(false) }

    val selectedPreset = state.presets.firstOrNull { it.id == state.settings.selectedPresetId }

    val vpnPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult(),
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            viewModel.startDnsAfterPermission()
        }
    }

    val onConnectDns = {
        val isDnsActive = (state.tunnelState is TunnelState.Connected && (state.tunnelState as TunnelState.Connected).section == ActiveSectionEngine.DNS) ||
            (state.tunnelState is TunnelState.Connecting && (state.tunnelState as TunnelState.Connecting).section == ActiveSectionEngine.DNS)
        if (!isDnsActive && state.appSettings.connectionMode == ConnectionMode.VPN) {
            val perm = viewModel.prepareVpnIntent()
            if (perm != null) {
                val launched = runCatching { vpnPermissionLauncher.launch(perm) }.isSuccess
                if (!launched) viewModel.toggleDnsConnection()
            } else {
                viewModel.toggleDnsConnection()
            }
        } else {
            viewModel.toggleDnsConnection()
        }
    }

    val dnsConnected = state.tunnelState is TunnelState.Connected && (state.tunnelState as TunnelState.Connected).section == ActiveSectionEngine.DNS
    val dnsConnecting = state.tunnelState is TunnelState.Connecting && (state.tunnelState as TunnelState.Connecting).section == ActiveSectionEngine.DNS

    ResponsiveScreenContainer { metrics ->
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = responsiveListPadding(metrics),
            verticalArrangement = Arrangement.spacedBy(metrics.sectionSpacing),
        ) {
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text(
                        text = tr("DNS Connection", "اتصال DNS (سبک AM Tunnel)"),
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.weight(1f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Surface(
                        shape = RoundedCornerShape(999.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.7f)),
                        modifier = Modifier.clickable { showRulesetsSheet = true },
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(Icons.Rounded.Shield, null, modifier = Modifier.size(14.dp), tint = MaterialTheme.colorScheme.primary)
                            Spacer(Modifier.width(4.dp))
                            Text(tr("Rules", "قوانین"), style = MaterialTheme.typography.labelSmall, maxLines = 1)
                        }
                    }
                }
            }

            // Dedicated DNS Connection Hero Card
            item {
                val heroTitle = when (state.settings.engineType) {
                    DnsEngineType.AUTO_TUNNEL -> tr("1-Tap Auto DNS Tunnel", "تونل DNS خودکار (بدون نیاز به کلید)")
                    DnsEngineType.DNS_CHANGER -> tr("Anti-Sanction DNS Changer", "تغییر DNS ضدتحریم (بدون افت سرعت)")
                    DnsEngineType.CUSTOM_DNSTT -> state.settings.nameserver.ifBlank { tr("Custom DNSTT Tunnel", "تونل اختصاصی DNSTT") }
                }
                PnifoxCard(contentPadding = metrics.cardPadding) {
                    FoxConnectionMascot(
                        tunnelState = state.tunnelState,
                        section = ActiveSectionEngine.DNS,
                        onSuggestionClick = { onNavigateScanner() },
                    )
                    Spacer(Modifier.height(12.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = heroTitle,
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            Text(
                                text = "${state.settings.mode.label} • ${state.settings.resolverAddress}",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                        Spacer(Modifier.width(12.dp))
                        PnifoxConnectButton(
                            isConnected = dnsConnected,
                            isConnecting = dnsConnecting,
                            onClick = { onConnectDns() },
                            size = 76.dp,
                            contentDescription = "Connect DNS",
                        )
                    }
                }
            }

            // Live Download / Upload Speed + Real Ping Row
            item {
                TunnelTelemetryRow(
                    rxSpeedBps = if (dnsConnected) state.rxSpeedBps else 0L,
                    txSpeedBps = if (dnsConnected) state.txSpeedBps else 0L,
                    sessionRxBytes = if (dnsConnected) state.sessionRxBytes else 0L,
                    sessionTxBytes = if (dnsConnected) state.sessionTxBytes else 0L,
                    pingMs = if (dnsConnected) (state.tunnelState as? TunnelState.Connected)?.pingMs else null,
                    modeLabel = if (isFa) state.settings.engineType.labelFa else state.settings.engineType.labelEn,
                    isConnectedForSection = dnsConnected,
                    isTestingPing = state.isTestingPing,
                    onRetestPing = viewModel::testActiveRealPing,
                )
            }

            // Live Core Log Viewer for DNS Tab
            item {
                LiveActivityLogPanel(
                    tunnelState = state.tunnelState,
                    section = ActiveSectionEngine.DNS,
                    logs = state.logs,
                    onCopy = { clipboard.setText(AnnotatedString(viewModel.exportLogs())) },
                    onExpandAll = { showLogsSheet = true },
                    cardPadding = metrics.cardPadding,
                )
            }

            // 1-Tap DNS Mode Selector (No Nameserver/Key needed by default!)
            item {
                PnifoxCard(contentPadding = metrics.cardPadding) {
                    PnifoxSectionLabel(
                        title = tr("DNS Connection Mode", "نوع اتصال DNS (پیش‌فرض بدون کلید)"),
                    )
                    Spacer(Modifier.height(8.dp))
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        DnsEngineType.entries.forEach { type ->
                            val selected = state.settings.engineType == type
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant,
                                border = BorderStroke(
                                    1.dp,
                                    if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline.copy(alpha = 0.6f),
                                ),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { viewModel.setEngineType(type) },
                            ) {
                                Row(
                                    modifier = Modifier.padding(12.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = if (isFa) type.labelFa else type.labelEn,
                                            style = MaterialTheme.typography.titleMedium,
                                            fontWeight = FontWeight.Bold,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                        )
                                        Text(
                                            text = if (isFa) type.subtitleFa else type.subtitleEn,
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            maxLines = 2,
                                            overflow = TextOverflow.Ellipsis,
                                        )
                                    }
                                    if (selected) {
                                        Spacer(Modifier.width(8.dp))
                                        Icon(Icons.Rounded.CheckCircle, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
                                    }
                                }
                            }
                        }
                    }

                    if (state.settings.engineType == DnsEngineType.AUTO_TUNNEL) {
                        Spacer(Modifier.height(8.dp))
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = Color(0xFF10B981).copy(alpha = 0.12f),
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text(
                                text = tr(
                                    "1-Tap Ready: No Nameserver or Server Public Key needed. Pick any DNS below and tap Connect.",
                                    "آماده اتصال فوری با یک کلیک! نیازی به Nameserver یا کلید عمومی سرور نیست — فقط سرور DNS را انتخاب کرده و متصل شوید.",
                                ),
                                style = MaterialTheme.typography.labelMedium,
                                color = Color(0xFF10B981),
                                modifier = Modifier.padding(10.dp),
                            )
                        }
                    }
                }
            }

            // Resolver Selector (Auto Select Server)
            item {
                PnifoxCard(
                    contentPadding = metrics.cardPadding,
                    modifier = Modifier.clickable { showServerPicker = true },
                ) {
                    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Rounded.Dns, null, tint = MaterialTheme.colorScheme.primary)
                        Spacer(Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = selectedPreset?.let { if (isFa) it.titleFa else it.titleEn } ?: tr("Custom DNS Resolver", "سرور DNS سفارشی"),
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            Text(
                                text = "${state.settings.resolverAddress} • Frequency: ${state.settings.tunnelFrequency}",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                        Spacer(Modifier.width(8.dp))
                        PnifoxBadgePill(text = state.settings.mode.label)
                    }
                    Spacer(Modifier.height(10.dp))
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(
                            onClick = { showServerPicker = true },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(12.dp),
                        ) {
                            Text(tr("Auto Select Server", "انتخاب سرور DNS"), maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                        OutlinedButton(
                            onClick = { showFreqDialog = true },
                            shape = RoundedCornerShape(12.dp),
                        ) {
                            Text("Freq: ${state.settings.tunnelFrequency}", maxLines = 1)
                        }
                    }
                }
            }

            // UDP | SuperDNS | FastDNS | PDoQ | DoH | DoT
            item {
                PnifoxCard(contentPadding = metrics.cardPadding) {
                    PnifoxSectionLabel(title = tr("DNS Protocol / Mode", "پروتکل DNS"))
                    Spacer(Modifier.height(8.dp))
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        DnsTunnelMode.entries.forEach { mode ->
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = if (state.settings.mode == mode) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant,
                                border = BorderStroke(1.dp, if (state.settings.mode == mode) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline),
                                modifier = Modifier.clickable { viewModel.setMode(mode) },
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)) {
                                    RadioButton(selected = state.settings.mode == mode, onClick = { viewModel.setMode(mode) }, modifier = Modifier.size(18.dp))
                                    Spacer(Modifier.width(6.dp))
                                    Text(mode.label, style = MaterialTheme.typography.labelMedium)
                                }
                            }
                        }
                    }
                    Spacer(Modifier.height(10.dp))
                    OutlinedTextField(
                        value = state.settings.resolverAddress,
                        onValueChange = viewModel::setResolverAddress,
                        label = { Text(tr("Resolver (e.g. 1.1.1.1:53 or DoH URL)", "آدرس DNS یا DoH")) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Spacer(Modifier.height(8.dp))
                    OutlinedButton(
                        onClick = viewModel::runPort53Probe,
                        enabled = !state.isProbing,
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                    ) {
                        Text(if (state.isProbing) tr("Probing Port 53...", "در حال تست پورت ۵۳...") else tr("Test Real UDP Port 53", "تست واقعی پورت ۵۳"))
                    }
                    if (!state.probeStatus.isNullOrBlank()) {
                        Spacer(Modifier.height(8.dp))
                        Text(state.probeStatus.orEmpty(), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.primary)
                    }
                }
            }

            // Custom SlowDNS / DNSTT Configuration (Only expanded when CUSTOM_DNSTT is chosen)
            if (state.settings.engineType == DnsEngineType.CUSTOM_DNSTT) {
                item {
                    PnifoxCard(contentPadding = metrics.cardPadding) {
                        PnifoxSectionLabel(title = tr("Custom SlowDNS / DNSTT Settings", "تنظیمات DNSTT اختصاصی (سرور شخصی)"))
                        Spacer(Modifier.height(6.dp))
                        Text(
                            text = tr(
                                "Note: Nameserver & PubKey are issued by your own VPS or SlowDNS provider (e.g. OpenTunnel). For keyless 1-tap connect, pick 'One-Tap Auto Tunnel' above.",
                                "نکته: دامنه Nameserver و کلید عمومی متعلق به سرور شما (یا اکانت OpenTunnel / SlowDNS) است. برای اتصال بدون کلید، از بالا «اتصال خودکار» را انتخاب کنید.",
                            ),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Spacer(Modifier.height(8.dp))
                        OutlinedTextField(
                            value = state.settings.nameserver,
                            onValueChange = viewModel::setNameserver,
                            label = { Text("Nameserver Domain (e.g. ns1.example.com)") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                        )
                        Spacer(Modifier.height(8.dp))
                        OutlinedTextField(
                            value = state.settings.publicKeyHex,
                            onValueChange = viewModel::setPublicKey,
                            label = { Text("Server Public Key (Hex)") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                        )
                        Spacer(Modifier.height(10.dp))
                        Text(
                            text = tr("Inner Tunnel Protocol (behind DNSTT):", "پروتکل داخلی پشت تونل DNS:"),
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                        )
                        Spacer(Modifier.height(6.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            DnsInnerProtocol.entries.forEach { inner ->
                                FilterChip(
                                    selected = state.settings.innerProtocol == inner,
                                    onClick = { viewModel.setInnerProtocol(inner) },
                                    label = { Text(inner.label) },
                                )
                            }
                        }

                        AnimatedVisibility(visible = state.settings.innerProtocol == DnsInnerProtocol.SSH) {
                            Column(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 8.dp)) {
                                OutlinedTextField(
                                    value = state.settings.sshUsername,
                                    onValueChange = viewModel::setSshUsername,
                                    label = { Text(tr("SSH Username (e.g. from OpenTunnel)", "نام کاربری SSH")) },
                                    singleLine = true,
                                    modifier = Modifier.fillMaxWidth(),
                                )
                                OutlinedTextField(
                                    value = state.settings.sshPassword,
                                    onValueChange = viewModel::setSshPassword,
                                    label = { Text(tr("SSH Password", "رمز عبور SSH")) },
                                    visualTransformation = PasswordVisualTransformation(),
                                    singleLine = true,
                                    modifier = Modifier.fillMaxWidth(),
                                )
                            }
                        }

                        AnimatedVisibility(visible = state.settings.innerProtocol == DnsInnerProtocol.VLESS) {
                            Column(modifier = Modifier.padding(top = 8.dp)) {
                                OutlinedTextField(
                                    value = state.settings.vlessUri,
                                    onValueChange = viewModel::setVlessUri,
                                    label = { Text("VLESS Link (vless://...)") },
                                    singleLine = true,
                                    modifier = Modifier.fillMaxWidth(),
                                )
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

    if (showRulesetsSheet) {
        RulesetsBottomSheet(
            appSettings = state.appSettings,
            onUpdateApp = viewModel::updateApp,
            isTunnelConnected = dnsConnected || dnsConnecting,
            onApplyAndReconnect = { showRulesetsSheet = false; viewModel.reconnectDns() },
            onDismiss = { showRulesetsSheet = false },
        )
    }

    if (showServerPicker) {
        var search by remember { mutableStateOf("") }
        val filtered = remember(search, state.presets) {
            val q = search.trim()
            if (q.isEmpty()) state.presets
            else state.presets.filter {
                it.titleEn.contains(q, ignoreCase = true) || it.titleFa.contains(q, ignoreCase = true) ||
                    it.ispHintEn.contains(q, ignoreCase = true) || it.address.contains(q, ignoreCase = true)
            }
        }
        AlertDialog(
            onDismissRequest = { showServerPicker = false },
            title = { Text(tr("Auto Select DNS Server", "انتخاب سرور و اپراتور DNS")) },
            text = {
                Column(modifier = Modifier.fillMaxWidth()) {
                    OutlinedTextField(
                        value = search,
                        onValueChange = { search = it },
                        leadingIcon = { Icon(Icons.Rounded.Search, null) },
                        label = { Text(tr("Search Network / ISP", "جستجوی شبکه یا سرور")) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Spacer(Modifier.height(10.dp))
                    LazyColumn(modifier = Modifier.fillMaxWidth().height(280.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(filtered, key = { it.id }) { preset ->
                            PnifoxCard(
                                contentPadding = 10.dp,
                                containerColor = if (preset.id == state.settings.selectedPresetId) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant,
                                modifier = Modifier.clickable {
                                    viewModel.applyPreset(preset)
                                    showServerPicker = false
                                    showFreqDialog = true
                                },
                            ) {
                                Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(if (isFa) preset.titleFa else preset.titleEn, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                        Text("${if (isFa) preset.ispHintFa else preset.ispHintEn} • ${preset.address}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    }
                                    PnifoxBadgePill(text = preset.badge)
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { showServerPicker = false }) { Text("Close") } },
        )
    }

    if (showFreqDialog) {
        var freq by remember(state.settings.tunnelFrequency) { mutableIntStateOf(state.settings.tunnelFrequency) }
        AlertDialog(
            onDismissRequest = { showFreqDialog = false },
            title = { Text(tr("Number of Tunnel Frequency", "تعداد فرکانس تونل")) },
            text = {
                Column(modifier = Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(selectedPreset?.let { if (isFa) it.titleFa else it.titleEn } ?: "DNS Tunnel", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(16.dp))
                    Surface(shape = RoundedCornerShape(999.dp), color = MaterialTheme.colorScheme.primaryContainer) {
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp)) {
                            IconButton(onClick = { freq = (freq - 1).coerceAtLeast(1) }) { Icon(Icons.Rounded.Remove, contentDescription = "-") }
                            Text(freq.toString(), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, modifier = Modifier.width(40.dp))
                            IconButton(onClick = { freq = (freq + 1).coerceAtMost(16) }) { Icon(Icons.Rounded.Add, contentDescription = "+") }
                        }
                    }
                }
            },
            confirmButton = { Button(onClick = { viewModel.setTunnelFrequency(freq); showFreqDialog = false }) { Text("OK") } },
            dismissButton = { TextButton(onClick = { showFreqDialog = false }) { Text("Cancel") } },
        )
    }
}
