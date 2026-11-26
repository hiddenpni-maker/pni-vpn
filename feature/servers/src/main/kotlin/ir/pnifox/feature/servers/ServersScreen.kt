package ir.pnifox.feature.servers

import android.app.Activity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Link
import androidx.compose.material.icons.rounded.PushPin
import androidx.compose.material.icons.rounded.QrCodeScanner
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material.icons.rounded.Speed
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import ir.pnifox.core.network.ShareLinkParser
import ir.pnifox.core.ui.PnifoxSwitchRow
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.journeyapps.barcodescanner.ScanContract
import com.journeyapps.barcodescanner.ScanOptions
import ir.pnifox.core.model.ActiveSectionEngine
import ir.pnifox.core.model.AppLanguage
import ir.pnifox.core.model.ConnectionMode
import ir.pnifox.core.model.ProxyProfile
import ir.pnifox.core.model.TransportMode
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

@Composable
fun ServersRoute(
    onNavigateScanner: () -> Unit = {},
    onNavigateDns: () -> Unit = {},
    viewModel: ServersViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val isFa = LocalAppLanguage.current == AppLanguage.PERSIAN
    val clipboard = LocalClipboardManager.current
    var showImportDialog by remember { mutableStateOf(false) }
    var showSubDialog by remember { mutableStateOf(false) }
    var showLogsSheet by remember { mutableStateOf(false) }
    var showRulesetsSheet by remember { mutableStateOf(false) }
    var editingProfile by remember { mutableStateOf<ProxyProfile?>(null) }

    val qrLauncher = rememberLauncherForActivityResult(ScanContract()) { result ->
        result.contents?.let { viewModel.importShareLinks(it) }
    }

    val vpnPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult(),
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            viewModel.startServerAfterPermission()
        }
    }

    val onConnectServer = {
        val isServerActive = (state.tunnelState is TunnelState.Connected && (state.tunnelState as TunnelState.Connected).section == ActiveSectionEngine.SERVER) ||
            (state.tunnelState is TunnelState.Connecting && (state.tunnelState as TunnelState.Connecting).section == ActiveSectionEngine.SERVER)
        if (!isServerActive && state.appSettings.connectionMode == ConnectionMode.VPN) {
            val perm = viewModel.prepareVpnIntent()
            if (perm != null) {
                val launched = runCatching { vpnPermissionLauncher.launch(perm) }.isSuccess
                if (!launched) viewModel.toggleServerConnection()
            } else {
                viewModel.toggleServerConnection()
            }
        } else {
            viewModel.toggleServerConnection()
        }
    }

    val srvConnected = state.tunnelState is TunnelState.Connected && (state.tunnelState as TunnelState.Connected).section == ActiveSectionEngine.SERVER
    val srvConnecting = state.tunnelState is TunnelState.Connecting && (state.tunnelState as TunnelState.Connecting).section == ActiveSectionEngine.SERVER

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
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Text(
                        text = tr("Custom Servers", "اتصال با سرور دلخواه"),
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
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(Icons.Rounded.Shield, null, modifier = Modifier.size(14.dp), tint = MaterialTheme.colorScheme.primary)
                            Spacer(Modifier.width(4.dp))
                            Text(tr("Rules", "قوانین"), style = MaterialTheme.typography.labelSmall, maxLines = 1)
                        }
                    }
                    FilterChip(
                        selected = state.appSettings.transportMode == TransportMode.FRAGMENT,
                        onClick = viewModel::toggleFragmentMode,
                        label = { Text("Fragment", maxLines = 1) },
                    )
                }
            }

            // Dedicated Server Connection Card
            item {
                PnifoxCard(contentPadding = metrics.cardPadding) {
                    FoxConnectionMascot(
                        tunnelState = state.tunnelState,
                        section = ActiveSectionEngine.SERVER,
                        onSuggestionClick = { action ->
                            viewModel.applyRecoveryAction(action, onNavigateScanner, onNavigateDns)
                        },
                    )
                    Spacer(Modifier.height(12.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = state.activeProfile?.name ?: tr("No server selected", "هیچ سروری انتخاب نشده"),
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            Text(
                                text = state.activeProfile?.let { "${it.protocol.displayName} • ${it.address}:${it.port}" }
                                    ?: tr("Import a VLESS / VMess / Trojan / Hy2 / MASQUE config below", "یک لینک یا فایل JSON کانفیگ وارد و انتخاب کنید"),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                        Spacer(Modifier.width(12.dp))
                        PnifoxConnectButton(
                            isConnected = srvConnected,
                            isConnecting = srvConnecting,
                            onClick = { onConnectServer() },
                            size = 76.dp,
                            contentDescription = "Connect Server",
                        )
                    }
                }
            }

            // Live Download / Upload Speed + Real Delay Ping Row
            item {
                val livePing = if (srvConnected) {
                    (state.tunnelState as? TunnelState.Connected)?.pingMs ?: state.activeProfile?.lastPingMs
                } else {
                    state.activeProfile?.lastPingMs
                }
                TunnelTelemetryRow(
                    rxSpeedBps = if (srvConnected) state.rxSpeedBps else 0L,
                    txSpeedBps = if (srvConnected) state.txSpeedBps else 0L,
                    sessionRxBytes = if (srvConnected) state.sessionRxBytes else 0L,
                    sessionTxBytes = if (srvConnected) state.sessionTxBytes else 0L,
                    pingMs = livePing,
                    modeLabel = if (isFa) state.appSettings.connectionMode.labelFa else state.appSettings.connectionMode.labelEn,
                    isConnectedForSection = srvConnected,
                    isTestingPing = state.isTestingActivePing,
                    onRetestPing = viewModel::testActiveRealPing,
                )
            }

            // Live Core Log Viewer for Servers Tab
            item {
                LiveActivityLogPanel(
                    tunnelState = state.tunnelState,
                    section = ActiveSectionEngine.SERVER,
                    logs = state.logs,
                    onCopy = { clipboard.setText(AnnotatedString(viewModel.exportLogs())) },
                    onExpandAll = { showLogsSheet = true },
                    cardPadding = metrics.cardPadding,
                )
            }

            // Action Buttons
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Button(
                        onClick = { showImportDialog = true },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(14.dp),
                    ) {
                        Icon(Icons.Rounded.Add, null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(4.dp))
                        Text(tr("Import", "افزودن"), maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                    val qrPrompt = tr("Scan Config QR", "اسکن QR کانفیگ")
                    OutlinedButton(
                        onClick = {
                            qrLauncher.launch(ScanOptions().apply {
                                setPrompt(qrPrompt)
                                setBeepEnabled(false)
                            })
                        },
                        shape = RoundedCornerShape(14.dp),
                    ) {
                        Icon(Icons.Rounded.QrCodeScanner, null, modifier = Modifier.size(18.dp))
                    }
                    OutlinedButton(
                        onClick = { showSubDialog = true },
                        shape = RoundedCornerShape(14.dp),
                    ) {
                        Icon(Icons.Rounded.Link, null, modifier = Modifier.size(18.dp))
                    }
                    OutlinedButton(
                        onClick = viewModel::refreshAllSubscriptions,
                        enabled = !state.isRefreshingSubs,
                        shape = RoundedCornerShape(14.dp),
                    ) {
                        Icon(Icons.Rounded.Refresh, null, modifier = Modifier.size(18.dp))
                    }
                    OutlinedButton(
                        onClick = viewModel::pingAllProfiles,
                        enabled = !state.isTestingAllPings && state.profiles.isNotEmpty(),
                        shape = RoundedCornerShape(14.dp),
                    ) {
                        if (state.isTestingAllPings) {
                            CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                        } else {
                            Icon(Icons.Rounded.Speed, null, modifier = Modifier.size(18.dp))
                        }
                    }
                }
            }

            if (!state.statusMessage.isNullOrBlank()) {
                item {
                    PnifoxCard(
                        containerColor = MaterialTheme.colorScheme.primaryContainer,
                        contentPadding = 12.dp,
                        modifier = Modifier.clickable { viewModel.dismissMessage() },
                    ) {
                        Text(state.statusMessage.orEmpty(), style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }

            item {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(ServerCategory.entries) { category ->
                        FilterChip(
                            selected = state.selectedCategory == category,
                            onClick = { viewModel.selectCategory(category) },
                            label = { Text(if (isFa) category.labelFa else category.labelEn, maxLines = 1) },
                        )
                    }
                }
            }

            if (state.selectedCategory == ServerCategory.SUBSCRIPTIONS && state.subscriptions.isNotEmpty()) {
                item { PnifoxSectionLabel(title = "Subscriptions (${state.subscriptions.size})") }
                items(state.subscriptions, key = { "sub_${it.id}" }) { sub ->
                    PnifoxCard(contentPadding = metrics.cardPadding) {
                        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text("${sub.name} (${sub.profileCount})", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Text(sub.url, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }
                            IconButton(onClick = { viewModel.deleteSubscription(sub.id) }) {
                                Icon(Icons.Rounded.DeleteOutline, contentDescription = "Delete")
                            }
                        }
                    }
                }
            }

            if (state.profiles.isEmpty()) {
                item {
                    PnifoxCard(contentPadding = metrics.cardPadding) {
                        Text(tr("No server profiles added yet.", "هنوز سروری اضافه نشده است."), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        Spacer(Modifier.height(4.dp))
                        Text(
                            tr("Tap 'Import' or scan QR to add VLESS, Reality, VMess, Trojan, Hy2, WireGuard, or full Xray/MASQUE JSON configs.", "از دکمه افزودن یا اسکن QR برای وارد کردن لینک یا فایل متنی JSON کانفیگ (VLESS / MASQUE / VMess) استفاده کنید."),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            } else {
                items(state.profiles, key = { it.id }) { profile ->
                    val isSelected = profile.id == state.activeProfile?.id
                    val isTestingThis = profile.id in state.pingingProfileIds
                    ServerProfileCard(
                        profile = profile,
                        selected = isSelected,
                        isTestingPing = isTestingThis,
                        cardPadding = metrics.cardPadding,
                        onSelect = { viewModel.selectProfile(profile) },
                        onPing = { viewModel.pingProfile(profile) },
                        onEdit = { editingProfile = profile },
                        onToggleSpoof = { viewModel.toggleSoldierSpoof(profile) },
                        onTogglePin = { viewModel.togglePin(profile) },
                        onDelete = { viewModel.deleteProfile(profile.id) },
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

    if (showRulesetsSheet) {
        RulesetsBottomSheet(
            appSettings = state.appSettings,
            onUpdateApp = viewModel::updateApp,
            isTunnelConnected = srvConnected || srvConnecting,
            onApplyAndReconnect = { showRulesetsSheet = false; viewModel.reconnectServer() },
            onDismiss = { showRulesetsSheet = false },
        )
    }

    if (showImportDialog) {
        var rawInput by remember { mutableStateOf(clipboard.getText()?.text.orEmpty()) }
        AlertDialog(
            onDismissRequest = { showImportDialog = false },
            title = { Text(tr("Import Configs (URI / Base64 / JSON)", "ورود کانفیگ (لینک یا JSON)")) },
            text = {
                OutlinedTextField(
                    value = rawInput,
                    onValueChange = { rawInput = it },
                    label = { Text("vless://  vmess://  trojan://  hy2://  or {\"outbounds\":...}") },
                    minLines = 4,
                    maxLines = 8,
                    modifier = Modifier.fillMaxWidth(),
                )
            },
            confirmButton = {
                Button(onClick = { viewModel.importShareLinks(rawInput); showImportDialog = false }) {
                    Text(tr("Save Servers", "ثبت سرورها"))
                }
            },
            dismissButton = {
                TextButton(onClick = { showImportDialog = false }) { Text(tr("Cancel", "انصراف")) }
            },
        )
    }

    if (showSubDialog) {
        var name by remember { mutableStateOf("") }
        var url by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showSubDialog = false },
            title = { Text(tr("Add Subscription URL", "افزودن لینک اشتراک")) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text(tr("Name", "نام اشتراک")) }, singleLine = true, modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(value = url, onValueChange = { url = it }, label = { Text("https://...") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                }
            },
            confirmButton = {
                Button(onClick = { viewModel.addSubscription(name, url); showSubDialog = false }) {
                    Text(tr("Save & Sync", "ذخیره و همگام‌سازی"))
                }
            },
            dismissButton = {
                TextButton(onClick = { showSubDialog = false }) { Text(tr("Cancel", "انصراف")) }
            },
        )
    }

    editingProfile?.let { targetProfile ->
        EditServerProfileDialog(
            profile = targetProfile,
            onSaveQuick = { name, addr, port, secret, sni, transport, path, fp, pubKey, shortId, frag, spoof, fakeSni ->
                viewModel.saveQuickEditedProfile(
                    existing = targetProfile,
                    name = name,
                    address = addr,
                    port = port,
                    secret = secret,
                    sni = sni,
                    transport = transport,
                    pathOrServiceName = path,
                    tlsFingerprint = fp,
                    realityPublicKey = pubKey,
                    realityShortId = shortId,
                    fragmentEnabled = frag,
                    spoofLocalhost40443 = spoof,
                    spoofFakeSni = fakeSni,
                )
                editingProfile = null
            },
            onSaveRawJson = { jsonText, onResult ->
                viewModel.saveRawJsonEditedProfile(targetProfile, jsonText) { res ->
                    onResult(res)
                    if (res.isSuccess) editingProfile = null
                }
            },
            onDismiss = { editingProfile = null },
        )
    }
}

@Composable
private fun EditServerProfileDialog(
    profile: ProxyProfile,
    onSaveQuick: (
        name: String,
        address: String,
        port: Int,
        secret: String,
        sni: String,
        transport: String,
        pathOrServiceName: String,
        tlsFingerprint: String,
        realityPublicKey: String,
        realityShortId: String,
        fragmentEnabled: Boolean,
        spoofLocalhost40443: Boolean,
        spoofFakeSni: String,
    ) -> Unit,
    onSaveRawJson: (String, (Result<Unit>) -> Unit) -> Unit,
    onDismiss: () -> Unit,
) {
    val isJsonConfig = remember(profile.id, profile.rawUri) { profile.rawUri.trimStart().startsWith("{") }
    var useRawTab by remember { mutableStateOf(false) }

    var name by remember(profile.id) { mutableStateOf(profile.name) }
    var address by remember(profile.id) { mutableStateOf(profile.address) }
    var portText by remember(profile.id) { mutableStateOf(profile.port.toString()) }
    var secret by remember(profile.id) { mutableStateOf(profile.secret) }
    var sni by remember(profile.id) { mutableStateOf(profile.sni) }
    var transport by remember(profile.id) { mutableStateOf(profile.transport) }
    var path by remember(profile.id) { mutableStateOf(profile.pathOrServiceName) }
    var fingerprint by remember(profile.id) { mutableStateOf(profile.tlsFingerprint) }
    var realityPub by remember(profile.id) { mutableStateOf(profile.realityPublicKey) }
    var realitySid by remember(profile.id) { mutableStateOf(profile.realityShortId) }
    var fragmentEnabled by remember(profile.id) { mutableStateOf(profile.fragmentEnabled) }
    var spoofEnabled by remember(profile.id) { mutableStateOf(profile.spoofLocalhost40443) }
    var spoofFakeSni by remember(profile.id) { mutableStateOf(profile.spoofFakeSni) }
    var rawJsonText by remember(profile.id) {
        mutableStateOf(if (isJsonConfig) ShareLinkParser.formatPrettyJson(profile.rawUri) else "")
    }
    var jsonError by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(tr("Edit Server & SNI Config", "ویرایش سرور و تنظیمات SNI"), fontWeight = FontWeight.Bold) },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                if (isJsonConfig) {
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterChip(
                            selected = !useRawTab,
                            onClick = { useRawTab = false },
                            label = { Text(tr("Quick Fields", "ویرایش سریع"), maxLines = 1) },
                            modifier = Modifier.weight(1f),
                        )
                        FilterChip(
                            selected = useRawTab,
                            onClick = { useRawTab = true },
                            label = { Text("Raw JSON", maxLines = 1) },
                            modifier = Modifier.weight(1f),
                        )
                    }
                }

                if (isJsonConfig && useRawTab) {
                    OutlinedTextField(
                        value = rawJsonText,
                        onValueChange = { rawJsonText = it; jsonError = null },
                        label = { Text("Xray / MASQUE JSON") },
                        textStyle = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                        minLines = 10,
                        maxLines = 16,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    if (!jsonError.isNullOrBlank()) {
                        Text(
                            text = jsonError.orEmpty(),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.error,
                        )
                    }
                } else {
                    OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text(tr("Name / Remarks", "نام سرور")) }, singleLine = true, modifier = Modifier.fillMaxWidth())
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(value = address, onValueChange = { address = it }, label = { Text(tr("Address / IP", "آدرس / آی‌پی")) }, singleLine = true, modifier = Modifier.weight(1.7f))
                        OutlinedTextField(value = portText, onValueChange = { portText = it.filter(Char::isDigit) }, label = { Text(tr("Port", "پورت")) }, singleLine = true, modifier = Modifier.weight(0.9f))
                    }
                    OutlinedTextField(value = sni, onValueChange = { sni = it }, label = { Text("SNI / ServerName") }, placeholder = { Text("www.cloudflare.com") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                    if (spoofEnabled || !isJsonConfig) {
                        OutlinedTextField(
                            value = spoofFakeSni,
                            onValueChange = { spoofFakeSni = it },
                            label = { Text(tr("SNI Spoof 40443 Fake SNI (optional)", "SNI جعلی برای تونل 40443 (اختیاری)")) },
                            placeholder = { Text(sni.ifBlank { "www.cloudflare.com" }) },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                    OutlinedTextField(value = secret, onValueChange = { secret = it }, label = { Text(tr("UUID / Password / Key", "کد UUID یا رمز")) }, singleLine = true, modifier = Modifier.fillMaxWidth())
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(value = transport, onValueChange = { transport = it }, label = { Text(tr("Transport", "انتقال")) }, singleLine = true, modifier = Modifier.weight(1f))
                        OutlinedTextField(value = fingerprint, onValueChange = { fingerprint = it }, label = { Text(tr("Fingerprint", "اثرانگشت")) }, singleLine = true, modifier = Modifier.weight(1f))
                    }
                    OutlinedTextField(value = path, onValueChange = { path = it }, label = { Text(tr("Path / ServiceName", "مسیر WS یا gRPC")) }, singleLine = true, modifier = Modifier.fillMaxWidth())
                    if (realityPub.isNotBlank() || transport.equals("tcp", ignoreCase = true) || profile.protocol.name == "VLESS") {
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedTextField(value = realityPub, onValueChange = { realityPub = it }, label = { Text("Reality PubKey") }, singleLine = true, modifier = Modifier.weight(1.4f))
                            OutlinedTextField(value = realitySid, onValueChange = { realitySid = it }, label = { Text("ShortId") }, singleLine = true, modifier = Modifier.weight(0.8f))
                        }
                    }
                    PnifoxSwitchRow(
                        title = tr("TLS Fragment", "قطعه‌قطعه کردن TLS"),
                        checked = fragmentEnabled,
                        onCheckedChange = { fragmentEnabled = it },
                    )
                    if (!isJsonConfig) {
                        PnifoxSwitchRow(
                            title = tr("Enable SNI Spoof (127.0.0.1:40443)", "فعال‌سازی SNI Spoof روی پورت 40443"),
                            checked = spoofEnabled,
                            onCheckedChange = { spoofEnabled = it },
                        )
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (isJsonConfig && useRawTab) {
                        onSaveRawJson(rawJsonText) { res ->
                            res.onFailure { jsonError = it.message ?: "Invalid JSON config" }
                        }
                    } else {
                        val parsedPort = portText.toIntOrNull()?.coerceIn(1, 65535) ?: profile.port
                        onSaveQuick(
                            name,
                            address,
                            parsedPort,
                            secret,
                            sni,
                            transport,
                            path,
                            fingerprint,
                            realityPub,
                            realitySid,
                            fragmentEnabled,
                            spoofEnabled,
                            spoofFakeSni,
                        )
                    }
                },
            ) {
                Text(tr("Save Changes", "ذخیره تغییرات"))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(tr("Cancel", "انصراف")) }
        },
    )
}

@Composable
private fun ServerProfileCard(
    profile: ProxyProfile,
    selected: Boolean,
    isTestingPing: Boolean,
    cardPadding: androidx.compose.ui.unit.Dp,
    onSelect: () -> Unit,
    onPing: () -> Unit,
    onEdit: () -> Unit,
    onToggleSpoof: () -> Unit,
    onTogglePin: () -> Unit,
    onDelete: () -> Unit,
) {
    PnifoxCard(
        containerColor = if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface,
        borderColor = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
        contentPadding = cardPadding,
        modifier = Modifier.clickable(onClick = onSelect),
    ) {
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            PnifoxBadgePill(text = profile.protocol.displayName)
            Spacer(Modifier.width(8.dp))
            Text(
                text = profile.name,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            IconButton(onClick = onPing, enabled = !isTestingPing, modifier = Modifier.size(34.dp)) {
                if (isTestingPing) {
                    CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                } else {
                    Icon(Icons.Rounded.Speed, contentDescription = "Real Delay Test", modifier = Modifier.size(18.dp))
                }
            }
            IconButton(onClick = onEdit, modifier = Modifier.size(34.dp)) {
                Icon(Icons.Rounded.Edit, contentDescription = "Edit Config", modifier = Modifier.size(18.dp))
            }
            IconButton(onClick = onTogglePin, modifier = Modifier.size(34.dp)) {
                Icon(Icons.Rounded.PushPin, contentDescription = "Pin", tint = if (profile.isPinned) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(18.dp))
            }
            IconButton(onClick = onDelete, modifier = Modifier.size(34.dp)) { Icon(Icons.Rounded.DeleteOutline, contentDescription = "Delete", modifier = Modifier.size(18.dp)) }
        }
        Spacer(Modifier.height(4.dp))
        Text(
            text = if (profile.spoofLocalhost40443) {
                "127.0.0.1:40443 (SNI Spoof) ← ${profile.address}:${profile.port}"
            } else {
                "${profile.address}:${profile.port} • ${profile.transport.uppercase()}"
            },
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Spacer(Modifier.height(8.dp))
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            FilterChip(
                selected = profile.spoofLocalhost40443,
                onClick = onToggleSpoof,
                label = { Text("SNI Spoof 40443", style = MaterialTheme.typography.labelSmall) },
            )
            val pingLabel = when {
                isTestingPing -> tr("Testing...", "در حال تست...")
                profile.lastPingMs == null -> if (selected) tr("Selected", "انتخاب‌شده") else tr("Tap to select", "لمس برای انتخاب")
                profile.lastPingMs!! < 0 -> tr("Timeout", "تایم‌اوت")
                else -> "${profile.lastPingMs} ms"
            }
            val pingColor = when {
                profile.lastPingMs != null && profile.lastPingMs!! in 1..250 -> Color(0xFF10B981)
                profile.lastPingMs != null && profile.lastPingMs!! > 250 -> Color(0xFFF59E0B)
                profile.lastPingMs != null && profile.lastPingMs!! < 0 -> MaterialTheme.colorScheme.error
                else -> MaterialTheme.colorScheme.primary
            }
            Text(text = pingLabel, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold, color = pingColor)
        }
    }
}
