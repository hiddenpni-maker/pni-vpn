package ir.pnifox.feature.home

import android.app.Activity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.HelpOutline
import androidx.compose.material.icons.automirrored.rounded.Send
import androidx.compose.material.icons.rounded.BarChart
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Code
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.ExpandLess
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.Key
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.Radar
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import ir.pnifox.core.model.ActiveSectionEngine
import ir.pnifox.core.model.AetherIdentityTarget
import ir.pnifox.core.model.AetherNoizeProfile
import ir.pnifox.core.model.AetherProtocol
import ir.pnifox.core.model.AetherScanMode
import ir.pnifox.core.model.AppLanguage
import ir.pnifox.core.model.ConnectionMode
import ir.pnifox.core.model.IpVersionMode
import ir.pnifox.core.model.MasqueTransport
import ir.pnifox.core.model.OneTapPreset
import ir.pnifox.core.model.PnifoxRegionsCatalog
import ir.pnifox.core.model.PsiphonChainMode
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
import ir.pnifox.core.ui.PnifoxUserGuideSheet
import ir.pnifox.core.ui.R as UiR
import ir.pnifox.core.ui.RegionPickerSheet
import ir.pnifox.core.ui.ResponsiveScreenContainer
import ir.pnifox.core.ui.RulesetsBottomSheet
import ir.pnifox.core.ui.TunnelTelemetryRow
import ir.pnifox.core.ui.responsiveListPadding
import ir.pnifox.core.ui.tr

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun HomeRoute(
    onNavigateServers: () -> Unit,
    onNavigateScanner: () -> Unit,
    onNavigateDns: () -> Unit,
    onNavigateSettings: () -> Unit,
    onNavigateStats: () -> Unit = {},
    onNavigateSection: (ActiveSectionEngine) -> Unit = {},
    onOpenTelegram: () -> Unit = {},
    viewModel: HomeViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val isFa = LocalAppLanguage.current == AppLanguage.PERSIAN
    val clipboard = LocalClipboardManager.current

    var showLogsSheet by remember { mutableStateOf(false) }
    var showRegionPicker by remember { mutableStateOf(false) }
    var showGuideSheet by remember { mutableStateOf(false) }
    var showRulesetsSheet by remember { mutableStateOf(false) }
    var showProtocolGuide by remember { mutableStateOf(false) }
    var expandStep1Api by remember { mutableStateOf(false) }
    var expandStep3Advanced by remember { mutableStateOf(false) }

    val activeRegion = PnifoxRegionsCatalog.findByCode(state.aetherSettings.psiphonExitCountry)
    val isPsiphonExitActive = state.aetherSettings.psiphonEnabled ||
        state.appSettings.activePreset == OneTapPreset.WARP_TO_PSIPHON ||
        state.appSettings.exitNode == ir.pnifox.core.model.ExitNode.PSIPHON

    val vpnPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult(),
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            viewModel.startAetherAfterPermission()
        }
    }

    val onConnectAether = {
        val isAetherActive = (state.tunnelState is TunnelState.Connected && (state.tunnelState as TunnelState.Connected).section == ActiveSectionEngine.AETHER) ||
            (state.tunnelState is TunnelState.Connecting && (state.tunnelState as TunnelState.Connecting).section == ActiveSectionEngine.AETHER)
        if (!isAetherActive && state.appSettings.connectionMode == ConnectionMode.VPN) {
            val perm = viewModel.prepareVpnIntent()
            if (perm != null) {
                val launched = runCatching { vpnPermissionLauncher.launch(perm) }.isSuccess
                if (!launched) {
                    viewModel.toggleAetherConnection()
                }
            } else {
                viewModel.toggleAetherConnection()
            }
        } else {
            viewModel.toggleAetherConnection()
        }
    }

    ResponsiveScreenContainer { metrics ->
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = responsiveListPadding(metrics),
            verticalArrangement = Arrangement.spacedBy(metrics.sectionSpacing),
        ) {
            // Top Bar
            item {
                AetherTopBar(
                    connectionMode = state.appSettings.connectionMode,
                    onSelectMode = viewModel::setConnectionMode,
                    onOpenTelegram = onOpenTelegram,
                    onOpenStats = onNavigateStats,
                    onOpenGuide = { showGuideSheet = true },
                    onOpenLogs = { showLogsSheet = true },
                    onOpenSettings = onNavigateSettings,
                )
            }

            // Dedicated AETHER Connect Hero Card (PattNG Style Engine)
            item {
                val aetherConnected = state.tunnelState is TunnelState.Connected &&
                    (state.tunnelState as TunnelState.Connected).section == ActiveSectionEngine.AETHER
                val aetherConnecting = state.tunnelState is TunnelState.Connecting &&
                    (state.tunnelState as TunnelState.Connecting).section == ActiveSectionEngine.AETHER

                PnifoxCard(contentPadding = metrics.cardPadding) {
                    FoxConnectionMascot(
                        tunnelState = state.tunnelState,
                        section = ActiveSectionEngine.AETHER,
                        onSuggestionClick = { action ->
                            viewModel.applyRecoveryAction(
                                action = action,
                                onNavigateScanner = onNavigateScanner,
                                onNavigateDns = onNavigateDns,
                                onOpenLogs = { showLogsSheet = true },
                            )
                        },
                    )
                    Spacer(Modifier.height(14.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = if (isFa) state.appSettings.activePreset.titleFa else state.appSettings.activePreset.titleEn,
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            Spacer(Modifier.height(2.dp))
                            Text(
                                text = if (state.aetherSettings.scanForEndpoint) {
                                    tr("Endpoint: Auto-Scan on Next Connect (${state.aetherSettings.scanMode.label})", "اندپوینت: اسکن خودکار در اتصال اول")
                                } else {
                                    tr(
                                        "Saved Endpoint: ${state.aetherSettings.endpointIp}:${state.aetherSettings.endpointPort}",
                                        "اندپوینت ذخیره: ${state.aetherSettings.endpointIp}:${state.aetherSettings.endpointPort}",
                                    )
                                },
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            Spacer(Modifier.height(8.dp))
                            FlowRow(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalArrangement = Arrangement.spacedBy(6.dp),
                            ) {
                                // Region with Flag Selector Pill (BPB / Aether Exit Country)
                                Surface(
                                    shape = RoundedCornerShape(999.dp),
                                    color = MaterialTheme.colorScheme.surfaceVariant,
                                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.7f)),
                                    modifier = Modifier.clickable { showRegionPicker = true },
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                    ) {
                                        Text(activeRegion.flag)
                                        Spacer(Modifier.width(5.dp))
                                        Text(
                                            text = if (isFa) activeRegion.nameFa else activeRegion.nameEn,
                                            style = MaterialTheme.typography.labelMedium,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                        )
                                        Icon(Icons.Rounded.KeyboardArrowDown, null, modifier = Modifier.size(16.dp))
                                    }
                                }
                                // Quick Re-scan Trigger
                                Surface(
                                    shape = RoundedCornerShape(999.dp),
                                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.14f),
                                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.4f)),
                                    modifier = Modifier.clickable {
                                        if (state.appSettings.connectionMode == ConnectionMode.VPN && viewModel.prepareVpnIntent() != null) {
                                            viewModel.updateAether { it.copy(scanForEndpoint = true) }
                                            onConnectAether()
                                        } else {
                                            viewModel.rescanAetherEndpointAndConnect()
                                        }
                                    },
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                    ) {
                                        Icon(Icons.Rounded.Refresh, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(14.dp))
                                        Spacer(Modifier.width(4.dp))
                                        Text(
                                            text = tr("Re-scan", "اسکن مجدد"),
                                            style = MaterialTheme.typography.labelMedium,
                                            color = MaterialTheme.colorScheme.primary,
                                            maxLines = 1,
                                        )
                                    }
                                }
                                // Quick Rulesets Pill (Iran Bypass & AdBlock)
                                val rulesBadge = buildString {
                                    append(tr("Rules", "قوانین"))
                                    if (state.appSettings.bypassIranEnabled) append(" • IR")
                                    if (state.appSettings.blockAdsEnabled) append(" • AdBlock")
                                }
                                Surface(
                                    shape = RoundedCornerShape(999.dp),
                                    color = MaterialTheme.colorScheme.surfaceVariant,
                                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.7f)),
                                    modifier = Modifier.clickable { showRulesetsSheet = true },
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                    ) {
                                        Icon(Icons.Rounded.Shield, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(14.dp))
                                        Spacer(Modifier.width(4.dp))
                                        Text(
                                            text = rulesBadge,
                                            style = MaterialTheme.typography.labelMedium,
                                            maxLines = 1,
                                        )
                                    }
                                }
                            }
                        }
                        Spacer(Modifier.width(12.dp))
                        PnifoxConnectButton(
                            isConnected = aetherConnected,
                            isConnecting = aetherConnecting,
                            onClick = { onConnectAether() },
                            size = metrics.heroButtonSize,
                            contentDescription = "Connect AETHER",
                        )
                    }
                    if (!activeRegion.code.equals("AUTO", ignoreCase = true) && !isPsiphonExitActive) {
                        Spacer(Modifier.height(10.dp))
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.7f),
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { viewModel.enablePsiphonForRegion() },
                        ) {
                            Text(
                                text = tr(
                                    "${activeRegion.flag} Exit region '${activeRegion.code}' works with WARP → Psiphon. Tap to switch.",
                                    "${activeRegion.flag} تغییر IP به کشور ${activeRegion.nameFa} با حالت WARP → Psiphon انجام می‌شود (برای فعال‌سازی لمس کنید).",
                                ),
                                style = MaterialTheme.typography.labelMedium,
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                            )
                        }
                    }
                }
            }

            // Equal Telemetry Row
            item {
                val aetherConnected = state.tunnelState is TunnelState.Connected &&
                    (state.tunnelState as TunnelState.Connected).section == ActiveSectionEngine.AETHER
                TunnelTelemetryRow(
                    rxSpeedBps = if (aetherConnected) state.rxSpeedBps else 0L,
                    txSpeedBps = if (aetherConnected) state.txSpeedBps else 0L,
                    sessionRxBytes = if (aetherConnected) state.sessionRxBytes else 0L,
                    sessionTxBytes = if (aetherConnected) state.sessionTxBytes else 0L,
                    pingMs = if (aetherConnected) (state.tunnelState as? TunnelState.Connected)?.pingMs else null,
                    modeLabel = if (isFa) state.appSettings.connectionMode.labelFa else state.appSettings.connectionMode.labelEn,
                    isConnectedForSection = aetherConnected,
                    isTestingPing = state.isTestingPing,
                    onRetestPing = viewModel::testActiveRealPing,
                )
            }

            // Live Inline Scan & Connection Progress Log Panel directly below Speed & Connect
            item {
                LiveActivityLogPanel(
                    tunnelState = state.tunnelState,
                    section = ActiveSectionEngine.AETHER,
                    logs = state.logs,
                    onCopy = { clipboard.setText(AnnotatedString(viewModel.exportLogs())) },
                    onExpandAll = { showLogsSheet = true },
                    cardPadding = metrics.cardPadding,
                )
            }

            // Goal-Based Scenarios + Smart Auto-Connect + Protocol Comparison
            item {
                ScenarioPresetsCard(
                    selectedScenario = state.selectedScenario,
                    isSmartConnecting = state.isSmartConnecting,
                    smartConnectStatus = state.smartConnectStatus,
                    cardPadding = metrics.cardPadding,
                    onApplyScenario = { profile, connect ->
                        viewModel.applyScenario(profile, connect, onNavigateSection)
                    },
                    onSmartAutoConnect = viewModel::smartAutoConnect,
                    onOpenProtocolGuide = { showProtocolGuide = true },
                )
            }

            // ==========================================================
            // STEP 1 (PattNG Style): WARP Key & Identity Builder
            // ==========================================================
            item {
                PnifoxSectionLabel(
                    title = tr("Step 1 · WARP Keys & Identity", "مرحله ۱ · ساخت کلید و هویت WARP"),
                )
            }
            item {
                PnifoxCard(contentPadding = metrics.cardPadding) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        val masqueOk = state.identitiesState.hasMasqueToml || state.aetherSettings.masqueAccountRegistered
                        val wgOk = state.identitiesState.hasWgToml || state.aetherSettings.wgAccountRegistered
                        val goolOk = state.identitiesState.hasGoolToml || state.aetherSettings.goolAccountRegistered
                        PnifoxBadgePill(
                            text = if (masqueOk) "MASQUE ✓" else "MASQUE ○",
                            containerColor = if (masqueOk) Color(0xFF10B981).copy(alpha = 0.16f) else MaterialTheme.colorScheme.surfaceVariant,
                            contentColor = if (masqueOk) Color(0xFF10B981) else MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        PnifoxBadgePill(
                            text = if (wgOk) "WireGuard ✓" else "WireGuard ○",
                            containerColor = if (wgOk) Color(0xFF10B981).copy(alpha = 0.16f) else MaterialTheme.colorScheme.surfaceVariant,
                            contentColor = if (wgOk) Color(0xFF10B981) else MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        PnifoxBadgePill(
                            text = if (goolOk) "Gool ✓" else "Gool ○",
                            containerColor = if (goolOk) Color(0xFF10B981).copy(alpha = 0.16f) else MaterialTheme.colorScheme.surfaceVariant,
                            contentColor = if (goolOk) Color(0xFF10B981) else MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Spacer(Modifier.height(10.dp))
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(AetherIdentityTarget.entries) { target ->
                            FilterChip(
                                selected = state.selectedKeyTarget == target,
                                onClick = { viewModel.selectKeyTarget(target) },
                                label = { Text(target.label, maxLines = 1) },
                            )
                        }
                    }
                    Spacer(Modifier.height(10.dp))
                    Button(
                        onClick = viewModel::generateAetherKeys,
                        enabled = !state.isRegisteringKeys,
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(14.dp),
                    ) {
                        Icon(Icons.Rounded.Key, null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(
                            if (state.isRegisteringKeys) tr("Generating WARP Keys...", "در حال ساخت کلید...")
                            else tr("Generate WARP Keys (${state.selectedKeyTarget.cliValue})", "ساخت کلید WARP (${state.selectedKeyTarget.cliValue})"),
                        )
                    }

                    if (!state.keyActionMessage.isNullOrBlank()) {
                        Spacer(Modifier.height(8.dp))
                        Text(
                            text = state.keyActionMessage.orEmpty(),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.clickable { viewModel.dismissBanner() },
                        )
                    }

                    Spacer(Modifier.height(8.dp))
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { expandStep1Api = !expandStep1Api },
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = tr("WARP API Registration Options", "تنظیمات API ثبت کلید"),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.weight(1f),
                        )
                        Icon(if (expandStep1Api) Icons.Rounded.ExpandLess else Icons.Rounded.ExpandMore, null)
                    }

                    AnimatedVisibility(visible = expandStep1Api) {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 8.dp)) {
                            OutlinedTextField(
                                value = state.aetherSettings.warpApiPrimaryHost,
                                onValueChange = { v -> viewModel.updateAether { it.copy(warpApiPrimaryHost = v) } },
                                label = { Text("API Host (default: api.devices.cloudflare.com)") },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth(),
                            )
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                OutlinedTextField(
                                    value = state.aetherSettings.warpApiCustomAddress,
                                    onValueChange = { v -> viewModel.updateAether { it.copy(warpApiCustomAddress = v) } },
                                    label = { Text("API IP[:Port]") },
                                    singleLine = true,
                                    modifier = Modifier.weight(1f),
                                )
                                OutlinedTextField(
                                    value = state.aetherSettings.warpApiSni,
                                    onValueChange = { v -> viewModel.updateAether { it.copy(warpApiSni = v) } },
                                    label = { Text("API SNI") },
                                    singleLine = true,
                                    modifier = Modifier.weight(1f),
                                )
                            }
                            PnifoxSwitchRow(
                                title = tr("Fragment ClientHello for WARP API", "فرگمنت برای API ثبت کلید"),
                                checked = state.aetherSettings.warpApiUseFragment,
                                onCheckedChange = { c -> viewModel.updateAether { it.copy(warpApiUseFragment = c) } },
                            )
                            PnifoxSwitchRow(
                                title = tr("Use ECH for WARP API calls", "ECH برای API ثبت کلید"),
                                checked = state.aetherSettings.warpApiUseEch,
                                onCheckedChange = { c -> viewModel.updateAether { it.copy(warpApiUseEch = c) } },
                            )
                        }
                    }
                }
            }

            // ==========================================================
            // STEP 2 (PattNG Style): Endpoint Scanner & Selection
            // ==========================================================
            item {
                PnifoxSectionLabel(
                    title = tr("Step 2 · Endpoint Scan & Network", "مرحله ۲ · اسکن اندپوینت و شبکه"),
                    trailing = {
                        TextButton(onClick = onNavigateScanner) {
                            Icon(Icons.Rounded.Radar, null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(4.dp))
                            Text(tr("Pro Scanner", "اسکنر حرفه‌ای"))
                        }
                    },
                )
            }
            item {
                PnifoxCard(contentPadding = metrics.cardPadding) {
                    PnifoxSwitchRow(
                        title = tr("Auto-Scan WARP Endpoint on Connect", "اسکن خودکار Endpoint در اتصال"),
                        subtitle = tr(
                            "Scans once and automatically saves the fastest gateway for next connects",
                            "پس از یک بار اسکن، بهترین اندپوینت برای اتصال‌های بعدی ذخیره می‌شود",
                        ),
                        checked = state.aetherSettings.scanForEndpoint,
                        onCheckedChange = { c -> viewModel.updateAether { it.copy(scanForEndpoint = c) } },
                    )
                    if (state.aetherSettings.scanForEndpoint) {
                        Spacer(Modifier.height(6.dp))
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            AetherScanMode.entries.forEach { sm ->
                                FilterChip(
                                    selected = state.aetherSettings.scanMode == sm,
                                    onClick = { viewModel.updateAether { it.copy(scanMode = sm) } },
                                    label = { Text(sm.label) },
                                )
                            }
                        }
                    } else {
                        Spacer(Modifier.height(8.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedTextField(
                                value = state.aetherSettings.endpointIp,
                                onValueChange = { v -> viewModel.updateAether { it.copy(endpointIp = v) } },
                                label = { Text("Pinned Peer IP") },
                                singleLine = true,
                                modifier = Modifier.weight(2f),
                            )
                            OutlinedTextField(
                                value = state.aetherSettings.endpointPort.toString(),
                                onValueChange = { v -> v.toIntOrNull()?.let { p -> viewModel.updateAether { it.copy(endpointPort = p) } } },
                                label = { Text("Port") },
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                singleLine = true,
                                modifier = Modifier.weight(1f),
                            )
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        IpVersionMode.entries.forEach { ver ->
                            FilterChip(
                                selected = state.aetherSettings.ipVersion == ver,
                                onClick = { viewModel.updateAether { it.copy(ipVersion = ver) } },
                                label = { Text(ver.label) },
                            )
                        }
                    }
                }
            }

            // ==========================================================
            // STEP 3 (PattNG Style): Build AETHER Config & Anti-DPI
            // ==========================================================
            item {
                PnifoxSectionLabel(
                    title = tr("Step 3 · AETHER Config & Presets", "مرحله ۳ · ساخت کانفیگ AETHER"),
                )
            }
            item {
                PnifoxCard(contentPadding = metrics.cardPadding) {
                    Text(
                        text = tr("1-Tap Presets", "پیش‌تنظیم‌های آماده یک‌لمسی"),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                    )
                    Spacer(Modifier.height(8.dp))
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        OneTapPreset.entries.forEach { preset ->
                            val selected = state.appSettings.activePreset == preset
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant,
                                border = BorderStroke(1.dp, if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { viewModel.applyOneTapPreset(preset) },
                            ) {
                                Row(
                                    modifier = Modifier.padding(10.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = if (isFa) preset.titleFa else preset.titleEn,
                                            style = MaterialTheme.typography.titleMedium,
                                            fontWeight = FontWeight.Bold,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                        )
                                        Text(
                                            text = if (isFa) preset.subtitleFa else preset.subtitleEn,
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                        )
                                    }
                                    if (selected) {
                                        Icon(Icons.Rounded.CheckCircle, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
                                    }
                                }
                            }
                        }
                    }

                    Spacer(Modifier.height(12.dp))
                    Text("Protocol & MASQUE Transport", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.height(6.dp))
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        AetherProtocol.entries.forEach { proto ->
                            FilterChip(
                                selected = state.aetherSettings.protocol == proto,
                                onClick = { viewModel.updateAether { it.copy(protocol = proto) } },
                                label = { Text(proto.label) },
                            )
                        }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        MasqueTransport.entries.forEach { tr ->
                            FilterChip(
                                selected = state.aetherSettings.masqueTransport == tr,
                                onClick = { viewModel.updateAether { it.copy(masqueTransport = tr) } },
                                label = { Text(tr.label) },
                            )
                        }
                    }

                    Spacer(Modifier.height(8.dp))
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { expandStep3Advanced = !expandStep3Advanced },
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = tr("Anti-DPI (Fragment, ECH, Noize, Psiphon, Tor) & Command", "تنظیمات ضدتحریم و پیش‌نمایش دستور"),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.weight(1f),
                        )
                        Icon(if (expandStep3Advanced) Icons.Rounded.ExpandLess else Icons.Rounded.ExpandMore, null)
                    }

                    AnimatedVisibility(visible = expandStep3Advanced) {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 10.dp)) {
                            OutlinedTextField(
                                value = state.aetherSettings.sni,
                                onValueChange = { v -> viewModel.updateAether { it.copy(sni = v) } },
                                label = { Text("MASQUE TLS SNI (--masque-sni)") },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth(),
                            )
                            Text("Obfuscation (--noize)", style = MaterialTheme.typography.labelMedium)
                            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                AetherNoizeProfile.entries.forEach { nz ->
                                    FilterChip(
                                        selected = state.aetherSettings.noizeProfile == nz,
                                        onClick = { viewModel.updateAether { it.copy(noizeProfile = nz) } },
                                        label = { Text(nz.label) },
                                    )
                                }
                            }
                            PnifoxSwitchRow(
                                title = "Fragment ClientHello (--fragment)",
                                checked = state.aetherSettings.fragmentEnabled,
                                onCheckedChange = { c -> viewModel.updateAether { it.copy(fragmentEnabled = c) } },
                            )
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                OutlinedTextField(
                                    value = state.aetherSettings.fragmentSizeBytes,
                                    onValueChange = { v -> viewModel.updateAether { it.copy(fragmentSizeBytes = v) } },
                                    label = { Text("Size (e.g. 8-16)") },
                                    singleLine = true,
                                    modifier = Modifier.weight(1f),
                                )
                                OutlinedTextField(
                                    value = state.aetherSettings.fragmentDelayMs,
                                    onValueChange = { v -> viewModel.updateAether { it.copy(fragmentDelayMs = v) } },
                                    label = { Text("Delay ms (2-10)") },
                                    singleLine = true,
                                    modifier = Modifier.weight(1f),
                                )
                            }
                            PnifoxSwitchRow(
                                title = "Encrypted Client Hello (--ech auto)",
                                checked = state.aetherSettings.echEnabled,
                                onCheckedChange = { c -> viewModel.updateAether { it.copy(echEnabled = c) } },
                            )
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                OutlinedTextField(
                                    value = state.aetherSettings.echDns,
                                    onValueChange = { v -> viewModel.updateAether { it.copy(echDns = v) } },
                                    label = { Text("ECH DNS") },
                                    singleLine = true,
                                    modifier = Modifier.weight(1f),
                                )
                                OutlinedTextField(
                                    value = state.aetherSettings.echDomain,
                                    onValueChange = { v -> viewModel.updateAether { it.copy(echDomain = v) } },
                                    label = { Text("ECH Domain") },
                                    singleLine = true,
                                    modifier = Modifier.weight(1f),
                                )
                            }
                            PnifoxSwitchRow(
                                title = tr("3-Stage Smart ECH Bootstrap (DoH/DoT/53)", "دریافت هوشمند ECH (DoH/DoT/پورت۵۳)"),
                                checked = state.aetherSettings.smartEchBootstrap,
                                onCheckedChange = { c -> viewModel.updateAether { it.copy(smartEchBootstrap = c) } },
                            )
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                                OutlinedButton(
                                    onClick = viewModel::fetchEchConfigNow,
                                    enabled = !state.isFetchingEch,
                                    shape = RoundedCornerShape(10.dp),
                                ) {
                                    Text(if (state.isFetchingEch) tr("Fetching...", "دریافت...") else tr("Fetch ECH Now", "دریافت ECH الان"))
                                }
                                if (state.aetherSettings.echBase64Override.isNotBlank()) {
                                    PnifoxBadgePill(
                                        text = tr("ECH Ready ✓", "کلید ECH آماده ✓"),
                                        containerColor = Color(0xFF10B981).copy(alpha = 0.16f),
                                        contentColor = Color(0xFF10B981),
                                    )
                                }
                            }
                            PnifoxSwitchRow(
                                title = "Psiphon Exit Chain (--psiphon)",
                                checked = state.aetherSettings.psiphonEnabled,
                                onCheckedChange = { c -> viewModel.updateAether { it.copy(psiphonEnabled = c) } },
                            )
                            if (state.aetherSettings.psiphonEnabled) {
                                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    PsiphonChainMode.entries.forEach { pm ->
                                        FilterChip(
                                            selected = state.aetherSettings.psiphonMode == pm,
                                            onClick = { viewModel.updateAether { it.copy(psiphonMode = pm) } },
                                            label = { Text(pm.labelEn) },
                                        )
                                    }
                                }
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    OutlinedButton(
                                        onClick = { showRegionPicker = true },
                                        modifier = Modifier.weight(1f),
                                        shape = RoundedCornerShape(12.dp),
                                    ) {
                                        Text("${activeRegion.flag} ${activeRegion.code}", maxLines = 1)
                                    }
                                    OutlinedTextField(
                                        value = state.aetherSettings.psiphonCdnFrontingIps,
                                        onValueChange = { v -> viewModel.updateAether { it.copy(psiphonCdnFrontingIps = v) } },
                                        label = { Text("CDN Fronting IPs") },
                                        singleLine = true,
                                        modifier = Modifier.weight(1.4f),
                                    )
                                }
                            }
                            PnifoxSwitchRow(
                                title = "Tor Exit Inside Tunnel (--tor)",
                                checked = state.aetherSettings.torEnabled,
                                onCheckedChange = { c -> viewModel.updateAether { it.copy(torEnabled = c) } },
                            )
                            Surface(
                                shape = RoundedCornerShape(10.dp),
                                color = MaterialTheme.colorScheme.surfaceVariant,
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                Column(modifier = Modifier.padding(10.dp)) {
                                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                                        Text("Command Preview", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                                        OutlinedButton(onClick = { clipboard.setText(AnnotatedString(state.commandPreview)) }) {
                                            Icon(Icons.Rounded.ContentCopy, null, modifier = Modifier.size(14.dp))
                                            Spacer(Modifier.width(4.dp))
                                            Text("Copy")
                                        }
                                    }
                                    Text(
                                        text = state.commandPreview,
                                        style = MaterialTheme.typography.labelSmall.copy(fontFamily = FontFamily.Monospace),
                                    )
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

    if (showRulesetsSheet) {
        val aetherActive = state.tunnelState is TunnelState.Connected || state.tunnelState is TunnelState.Connecting
        RulesetsBottomSheet(
            appSettings = state.appSettings,
            onUpdateApp = viewModel::updateApp,
            isTunnelConnected = aetherActive,
            onApplyAndReconnect = { showRulesetsSheet = false; viewModel.reconnectAether() },
            onDismiss = { showRulesetsSheet = false },
        )
    }

    if (showRegionPicker) {
        RegionPickerSheet(
            selectedCode = state.aetherSettings.psiphonExitCountry,
            onSelect = { reg -> viewModel.selectRegion(reg.code) },
            onDismiss = { showRegionPicker = false },
        )
    }

    if (showGuideSheet) {
        PnifoxUserGuideSheet(
            onDismiss = { showGuideSheet = false },
        )
    }

    if (showProtocolGuide) {
        ProtocolGuideDialog(
            onDismiss = { showProtocolGuide = false },
        )
    }
}

@Composable
private fun AetherTopBar(
    connectionMode: ConnectionMode,
    onSelectMode: (ConnectionMode) -> Unit,
    onOpenTelegram: () -> Unit,
    onOpenStats: () -> Unit,
    onOpenGuide: () -> Unit,
    onOpenLogs: () -> Unit,
    onOpenSettings: () -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    val isFa = LocalAppLanguage.current == AppLanguage.PERSIAN

    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Image(
            painter = painterResource(UiR.drawable.pnifox_makima_logo),
            contentDescription = "pnifox",
            modifier = Modifier.size(34.dp).clip(CircleShape),
        )
        Spacer(Modifier.width(6.dp))
        Text("pnifox", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        Spacer(Modifier.width(5.dp))
        Box {
            Surface(
                shape = RoundedCornerShape(999.dp),
                color = MaterialTheme.colorScheme.surfaceVariant,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                modifier = Modifier.clickable { expanded = true },
            ) {
                Row(modifier = Modifier.padding(horizontal = 7.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(if (isFa) connectionMode.labelFa else connectionMode.labelEn, style = MaterialTheme.typography.labelSmall, maxLines = 1)
                    Icon(Icons.Rounded.KeyboardArrowDown, null, modifier = Modifier.size(14.dp))
                }
            }
            DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                ConnectionMode.entries.forEach { mode ->
                    DropdownMenuItem(
                        text = { Text("${mode.labelEn} (${mode.labelFa})") },
                        onClick = { onSelectMode(mode); expanded = false },
                    )
                }
            }
        }
        Spacer(Modifier.weight(1f))
        Surface(
            shape = RoundedCornerShape(999.dp),
            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.14f),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.4f)),
            modifier = Modifier.clickable(onClick = onOpenTelegram),
        ) {
            Row(modifier = Modifier.padding(horizontal = 7.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.AutoMirrored.Rounded.Send, contentDescription = "Telegram", tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(13.dp))
                Spacer(Modifier.width(3.dp))
                Text(tr("TG", "تلگرام"), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
            }
        }
        IconButton(onClick = onOpenStats, modifier = Modifier.size(32.dp)) {
            Icon(Icons.Rounded.BarChart, contentDescription = "Stats", modifier = Modifier.size(19.dp))
        }
        IconButton(onClick = onOpenGuide, modifier = Modifier.size(32.dp)) {
            Icon(Icons.AutoMirrored.Rounded.HelpOutline, contentDescription = "Usage Guide", modifier = Modifier.size(19.dp))
        }
        IconButton(onClick = onOpenLogs, modifier = Modifier.size(32.dp)) {
            Icon(Icons.Rounded.Code, contentDescription = "Logs", modifier = Modifier.size(19.dp))
        }
        IconButton(onClick = onOpenSettings, modifier = Modifier.size(32.dp)) {
            Icon(Icons.Rounded.Settings, contentDescription = "Settings", modifier = Modifier.size(19.dp))
        }
    }
}
