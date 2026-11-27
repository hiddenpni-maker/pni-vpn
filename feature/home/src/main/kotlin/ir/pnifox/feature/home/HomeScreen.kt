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
import androidx.compose.material.icons.rounded.Clear
import androidx.compose.material.icons.rounded.Cloud
import androidx.compose.material.icons.rounded.Code
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.ExpandLess
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.Hub
import androidx.compose.material.icons.rounded.Key
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.Public
import androidx.compose.material.icons.rounded.Radar
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Router
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material.icons.rounded.SwapHoriz
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
import ir.pnifox.core.model.ExitNode
import ir.pnifox.core.model.IpVersionMode
import ir.pnifox.core.model.MasqueTransport
import ir.pnifox.core.model.OneTapPreset
import ir.pnifox.core.model.PnifoxRegionsCatalog
import ir.pnifox.core.model.PsiphonCdnCatalog
import ir.pnifox.core.model.PsiphonChainMode
import ir.pnifox.core.model.TunnelState
import ir.pnifox.core.ui.CoreLogsModalSheet
import ir.pnifox.core.ui.FoxConnectionMascot
import ir.pnifox.core.ui.LiveActivityLogPanel
import ir.pnifox.core.ui.LocalAppLanguage
import ir.pnifox.core.ui.PnifoxBadgePill
import ir.pnifox.core.ui.PnifoxCard
import ir.pnifox.core.ui.PnifoxConnectButton
import ir.pnifox.core.ui.PnifoxDropdownField
import ir.pnifox.core.ui.PnifoxInfoPopupButton
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

private enum class HomeExitChainOption(
    val labelEn: String,
    val labelFa: String,
) {
    DIRECT_WARP("WARP Direct (Fast)", "مستقیم WARP (سریع)"),
    CHAIN_PSIPHON("+ Psiphon Exit", "+ زنجیره سایفون"),
    REVERSE_PSIPHON("Psiphon -> WARP", "سایفون به WARP"),
    CHAIN_TOR("+ Tor Exit", "+ زنجیره Tor"),
}

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
    val isAkamaiStandalone = state.aetherSettings.psiphonEnabled &&
        (state.aetherSettings.psiphonMode == PsiphonChainMode.STANDALONE_CDN || state.appSettings.activePreset == OneTapPreset.PSIPHON_AKAMAI_CDN)
    val isPsiphonChainActive = !isAkamaiStandalone && (
        state.aetherSettings.psiphonEnabled ||
            state.appSettings.exitNode == ExitNode.PSIPHON
        )
    val hasAppliedIp = state.aetherSettings.endpointIp.isNotBlank() ||
        state.aetherSettings.psiphonCdnFrontingIps.isNotBlank()

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
                                text = if (isAkamaiStandalone) {
                                    tr("Akamai / Psiphon CDN", "اکامای / سایفون CDN")
                                } else {
                                    "${state.aetherSettings.protocol.label} (${state.aetherSettings.masqueTransport.label})"
                                },
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            Spacer(Modifier.height(2.dp))
                            Text(
                                text = when {
                                    isAkamaiStandalone -> {
                                        val cnt = state.aetherSettings.psiphonCdnFrontingIps.split(',', ';', '\n', ' ').count { it.isNotBlank() }
                                        if (cnt > 0) tr("Akamai CDN • $cnt edge IP(s)", "اکامای CDN • $cnt آی‌پی اعمال‌شده")
                                        else tr("Akamai CDN • Auto edge", "اکامای CDN • انتخاب خودکار")
                                    }
                                    state.aetherSettings.scanForEndpoint || state.aetherSettings.endpointIp.isBlank() ->
                                        tr("Endpoint: Auto-Scan (${state.aetherSettings.scanMode.label})", "اندپوینت: اسکن خودکار (${state.aetherSettings.scanMode.label})")
                                    else ->
                                        tr(
                                            "Saved IP: ${state.aetherSettings.endpointIp}:${state.aetherSettings.endpointPort}",
                                            "آی‌پی اعمال‌شده: ${state.aetherSettings.endpointIp}:${state.aetherSettings.endpointPort}",
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
                                if (!isAkamaiStandalone && isPsiphonChainActive) {
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
                                }
                                if (!isAkamaiStandalone) {
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
                                            Text(tr("Re-scan", "اسکن مجدد"), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary, maxLines = 1)
                                        }
                                    }
                                }
                                if (hasAppliedIp) {
                                    Surface(
                                        shape = RoundedCornerShape(999.dp),
                                        color = MaterialTheme.colorScheme.error.copy(alpha = 0.12f),
                                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.35f)),
                                        modifier = Modifier.clickable { viewModel.clearAllAppliedIps() },
                                    ) {
                                        Row(
                                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                        ) {
                                            Icon(Icons.Rounded.Clear, null, tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(14.dp))
                                            Spacer(Modifier.width(4.dp))
                                            Text(tr("Clear IPs", "پاک کردن IP"), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.error, maxLines = 1)
                                        }
                                    }
                                }
                                // Quick Rulesets Pill
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
                                        Text(text = rulesBadge, style = MaterialTheme.typography.labelMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
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

            // Compact Preset & Info Bar
            item {
                ScenarioPresetsCard(
                    selectedScenario = state.selectedScenario,
                    cardPadding = metrics.cardPadding,
                    onApplyScenario = { profile, connect ->
                        viewModel.applyScenario(profile, connect, onNavigateSection)
                    },
                    onOpenProtocolGuide = { showProtocolGuide = true },
                )
            }

            // ==========================================================
            // CORE ENGINE CONFIGURATION (Cleanly Separated: WARP vs Akamai)
            // ==========================================================
            item {
                val activeChain = when {
                    state.aetherSettings.torEnabled || state.appSettings.exitNode == ExitNode.TOR -> HomeExitChainOption.CHAIN_TOR
                    state.aetherSettings.psiphonEnabled && state.aetherSettings.psiphonMode == PsiphonChainMode.PSIPHON_TO_WARP ->
                        HomeExitChainOption.REVERSE_PSIPHON
                    state.aetherSettings.psiphonEnabled -> HomeExitChainOption.CHAIN_PSIPHON
                    else -> HomeExitChainOption.DIRECT_WARP
                }

                PnifoxCard(contentPadding = metrics.cardPadding) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = tr("Connection Mode", "روش اتصال و پروتکل"),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.weight(1f),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        PnifoxInfoPopupButton(
                            title = tr("AETHER vs Akamai", "توضیحات پروتکل‌ها و Akamai"),
                            body = tr(
                                "• AETHER (WARP): Pick Protocol + Transport independently, then optionally chain Psiphon & Country.\n• Akamai / Psiphon CDN: Standalone CDN mode with no WARP keys or country lock.",
                                "• حالت AETHER (WARP): پروتکل و نوع انتقال را جداگانه انتخاب کرده و در صورت نیاز سایفون و کشور خروجی را روی آن فعال کنید.\n• حالت Akamai CDN (مستقل): بدون نیاز به WARP و بدون قفل کشور، مستقیم با CDN اکامای متصل می‌شود.",
                            ),
                        )
                    }
                    Spacer(Modifier.height(8.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        FilterChip(
                            selected = !isAkamaiStandalone,
                            onClick = {
                                viewModel.updateApp { it.copy(activePreset = OneTapPreset.WARP_FAST_MASQUE_H2, exitNode = ExitNode.DEFAULT) }
                                viewModel.updateAether { it.copy(psiphonEnabled = false, psiphonMode = PsiphonChainMode.WARP_TO_PSIPHON, torEnabled = false) }
                            },
                            label = { Text(tr("AETHER (WARP)", "وارپ (AETHER)"), maxLines = 1, overflow = TextOverflow.Ellipsis) },
                            modifier = Modifier.weight(1f),
                        )
                        FilterChip(
                            selected = isAkamaiStandalone,
                            onClick = {
                                viewModel.updateApp { it.copy(activePreset = OneTapPreset.PSIPHON_AKAMAI_CDN, exitNode = ExitNode.PSIPHON) }
                                viewModel.updateAether {
                                    it.copy(
                                        psiphonEnabled = true,
                                        psiphonMode = PsiphonChainMode.STANDALONE_CDN,
                                        psiphonCdnSets = it.psiphonCdnSets.ifBlank { "psiphon-akamai" },
                                        torEnabled = false,
                                    )
                                }
                            },
                            label = { Text(tr("Akamai CDN", "اکامای CDN"), maxLines = 1, overflow = TextOverflow.Ellipsis) },
                            modifier = Modifier.weight(1f),
                        )
                    }

                    Spacer(Modifier.height(10.dp))
                    if (isAkamaiStandalone) {
                        val setOptions = listOf("" to tr("All Sets (Auto)", "همه پکیج‌ها (خودکار)")) + PsiphonCdnCatalog.availableCdnSets
                        val activeSet = setOptions.firstOrNull { it.first.equals(state.aetherSettings.psiphonCdnSets.trim(), ignoreCase = true) }
                            ?: setOptions.first()
                        PnifoxDropdownField(
                            label = "CDN Set",
                            selectedText = activeSet.second,
                            options = setOptions,
                            selectedItem = activeSet,
                            optionLabel = { it.second },
                            onSelect = { pair -> viewModel.updateAether { it.copy(psiphonCdnSets = pair.first) } },
                            icon = Icons.Rounded.Cloud,
                            modifier = Modifier.fillMaxWidth(),
                        )
                        Spacer(Modifier.height(8.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedButton(
                                onClick = viewModel::testAndSortPsiphonCdnEdges,
                                enabled = !state.isFetchingEch && state.aetherSettings.psiphonCdnFrontingIps.isNotBlank(),
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(10.dp),
                            ) {
                                Text(tr("Test Edge IPs", "تست سرعت آی‌پی‌ها"), maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.labelSmall)
                            }
                            OutlinedButton(
                                onClick = onNavigateScanner,
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(10.dp),
                            ) {
                                Text(tr("Scan Akamai", "اسکنر Akamai"), maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.labelSmall)
                            }
                        }
                        Spacer(Modifier.height(8.dp))
                        OutlinedTextField(
                            value = state.aetherSettings.psiphonCdnFrontingIps,
                            onValueChange = { v -> viewModel.updateAether { it.copy(psiphonCdnFrontingIps = v) } },
                            label = { Text(tr("CDN Edge IPs / CIDRs", "آی‌پی / رنج‌های اکامای و CDN")) },
                            trailingIcon = if (state.aetherSettings.psiphonCdnFrontingIps.isNotBlank()) {
                                {
                                    IconButton(onClick = viewModel::clearAppliedCdnIps) {
                                        Icon(Icons.Rounded.Clear, contentDescription = "Clear CDN IPs", modifier = Modifier.size(16.dp))
                                    }
                                }
                            } else null,
                            minLines = 2,
                            maxLines = 3,
                            modifier = Modifier.fillMaxWidth(),
                        )
                        Spacer(Modifier.height(8.dp))
                        OutlinedTextField(
                            value = state.aetherSettings.psiphonCdnSniHostnames,
                            onValueChange = { v -> viewModel.updateAether { it.copy(psiphonCdnSniHostnames = v) } },
                            label = { Text(tr("CDN SNI Hostnames (optional)", "SNI فرانتینگ (اختیاری)")) },
                            minLines = 1,
                            maxLines = 2,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    } else {
                        // Clean independent Protocol + Transport + Optional Exit Chain
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            PnifoxDropdownField(
                                label = tr("Protocol", "پروتکل"),
                                selectedText = state.aetherSettings.protocol.label,
                                options = AetherProtocol.entries,
                                selectedItem = state.aetherSettings.protocol,
                                optionLabel = { it.label },
                                onSelect = { proto -> viewModel.updateAether { it.copy(protocol = proto) } },
                                icon = Icons.Rounded.Router,
                                modifier = Modifier.weight(1f),
                            )
                            if (state.aetherSettings.protocol == AetherProtocol.MASQUE ||
                                state.aetherSettings.protocol == AetherProtocol.MASQUE_IN_MASQUE ||
                                state.aetherSettings.protocol == AetherProtocol.WIREGUARD_OVER_MASQUE
                            ) {
                                PnifoxDropdownField(
                                    label = tr("Transport", "انتقال"),
                                    selectedText = state.aetherSettings.masqueTransport.label,
                                    options = MasqueTransport.entries,
                                    selectedItem = state.aetherSettings.masqueTransport,
                                    optionLabel = { it.label },
                                    onSelect = { tr -> viewModel.updateAether { it.copy(masqueTransport = tr) } },
                                    icon = Icons.Rounded.SwapHoriz,
                                    modifier = Modifier.weight(0.9f),
                                )
                            }
                        }
                        Spacer(Modifier.height(8.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            PnifoxDropdownField(
                                label = tr("Chain", "زنجیره"),
                                selectedText = if (isFa) activeChain.labelFa else activeChain.labelEn,
                                options = HomeExitChainOption.entries,
                                selectedItem = activeChain,
                                optionLabel = { ch -> if (isFa) ch.labelFa else ch.labelEn },
                                onSelect = { opt ->
                                    when (opt) {
                                        HomeExitChainOption.DIRECT_WARP -> {
                                            viewModel.updateApp { it.copy(exitNode = ExitNode.DEFAULT, activePreset = OneTapPreset.WARP_FAST_MASQUE_H2) }
                                            viewModel.updateAether { it.copy(psiphonEnabled = false, torEnabled = false) }
                                        }
                                        HomeExitChainOption.CHAIN_PSIPHON -> {
                                            viewModel.updateApp { it.copy(exitNode = ExitNode.PSIPHON, activePreset = OneTapPreset.WARP_TO_PSIPHON) }
                                            viewModel.updateAether { it.copy(psiphonEnabled = true, psiphonMode = PsiphonChainMode.WARP_TO_PSIPHON, torEnabled = false) }
                                        }
                                        HomeExitChainOption.REVERSE_PSIPHON -> {
                                            viewModel.updateApp { it.copy(exitNode = ExitNode.PSIPHON, activePreset = OneTapPreset.WARP_TO_PSIPHON) }
                                            viewModel.updateAether { it.copy(psiphonEnabled = true, psiphonMode = PsiphonChainMode.PSIPHON_TO_WARP, torEnabled = false) }
                                        }
                                        HomeExitChainOption.CHAIN_TOR -> {
                                            viewModel.updateApp { it.copy(exitNode = ExitNode.TOR, activePreset = OneTapPreset.WARP_TO_TOR) }
                                            viewModel.updateAether { it.copy(psiphonEnabled = false, torEnabled = true) }
                                        }
                                    }
                                },
                                icon = Icons.Rounded.Hub,
                                modifier = Modifier.weight(1f),
                            )
                            if (activeChain == HomeExitChainOption.CHAIN_PSIPHON || activeChain == HomeExitChainOption.REVERSE_PSIPHON) {
                                OutlinedButton(
                                    onClick = { showRegionPicker = true },
                                    shape = RoundedCornerShape(12.dp),
                                ) {
                                    Icon(Icons.Rounded.Public, null, modifier = Modifier.size(16.dp))
                                    Spacer(Modifier.width(4.dp))
                                    Text("${activeRegion.flag} ${activeRegion.code}", maxLines = 1)
                                }
                            }
                        }
                    }

                    if (!state.keyActionMessage.isNullOrBlank()) {
                        Spacer(Modifier.height(8.dp))
                        Text(
                            text = state.keyActionMessage.orEmpty(),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.clickable { viewModel.dismissBanner() },
                        )
                    }
                }
            }

            if (!isAkamaiStandalone) {
                // Step 1: Compact WARP Keys Card
                item {
                    PnifoxCard(contentPadding = metrics.cardPadding) {
                        PnifoxSectionLabel(title = tr("WARP Keys & Identity", "کلید و هویت WARP"))
                        Spacer(Modifier.height(6.dp))
                        val masqueOk = state.identitiesState.hasMasqueToml || state.aetherSettings.masqueAccountRegistered
                        val wgOk = state.identitiesState.hasWgToml || state.aetherSettings.wgAccountRegistered
                        val goolOk = state.identitiesState.hasGoolToml || state.aetherSettings.goolAccountRegistered
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
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
                        Spacer(Modifier.height(8.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            PnifoxDropdownField(
                                label = tr("Key", "نوع کلید"),
                                selectedText = state.selectedKeyTarget.label,
                                options = AetherIdentityTarget.entries,
                                selectedItem = state.selectedKeyTarget,
                                optionLabel = { it.label },
                                onSelect = viewModel::selectKeyTarget,
                                modifier = Modifier.weight(1f),
                            )
                            Button(
                                onClick = viewModel::generateAetherKeys,
                                enabled = !state.isRegisteringKeys,
                                shape = RoundedCornerShape(12.dp),
                            ) {
                                Icon(Icons.Rounded.Key, null, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(4.dp))
                                Text(
                                    if (state.isRegisteringKeys) tr("Working...", "در حال ساخت...")
                                    else tr("Generate", "ساخت کلید"),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    style = MaterialTheme.typography.labelSmall,
                                )
                            }
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
                                    label = { Text("API Host") },
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
                                    title = tr("Fragment ClientHello for API", "فرگمنت برای API ثبت کلید"),
                                    checked = state.aetherSettings.warpApiUseFragment,
                                    onCheckedChange = { c -> viewModel.updateAether { it.copy(warpApiUseFragment = c) } },
                                )
                                PnifoxSwitchRow(
                                    title = tr("ECH for API calls", "ECH برای API ثبت کلید"),
                                    checked = state.aetherSettings.warpApiUseEch,
                                    onCheckedChange = { c -> viewModel.updateAether { it.copy(warpApiUseEch = c) } },
                                )
                            }
                        }
                    }
                }

                // Step 2: Compact Endpoint & Anti-DPI Card
                item {
                    PnifoxCard(contentPadding = metrics.cardPadding) {
                        PnifoxSectionLabel(
                            title = tr("Endpoint & Anti-DPI", "اندپوینت و ضد فیلتر"),
                            trailing = {
                                IconButton(onClick = onNavigateScanner, modifier = Modifier.size(28.dp)) {
                                    Icon(Icons.Rounded.Radar, contentDescription = "Scanner", modifier = Modifier.size(18.dp))
                                }
                            },
                        )
                        Spacer(Modifier.height(6.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            PnifoxDropdownField(
                                label = tr("Scan", "اسکن"),
                                selectedText = state.aetherSettings.scanMode.label,
                                options = AetherScanMode.entries,
                                selectedItem = state.aetherSettings.scanMode,
                                optionLabel = { it.label },
                                onSelect = { sm -> viewModel.updateAether { it.copy(scanMode = sm) } },
                                icon = Icons.Rounded.Radar,
                                modifier = Modifier.weight(1f),
                            )
                            PnifoxDropdownField(
                                label = "IP",
                                selectedText = state.aetherSettings.ipVersion.label,
                                options = IpVersionMode.entries,
                                selectedItem = state.aetherSettings.ipVersion,
                                optionLabel = { it.label },
                                onSelect = { ver -> viewModel.updateAether { it.copy(ipVersion = ver) } },
                                modifier = Modifier.weight(0.85f),
                            )
                        }
                        Spacer(Modifier.height(6.dp))
                        PnifoxSwitchRow(
                            title = tr("Auto-Scan Diverse Endpoint", "اسکن خودکار و متنوع اندپوینت"),
                            checked = state.aetherSettings.scanForEndpoint,
                            onCheckedChange = { c -> viewModel.updateAether { it.copy(scanForEndpoint = c) } },
                        )
                        if (!state.aetherSettings.scanForEndpoint || state.aetherSettings.endpointIp.isNotBlank()) {
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                OutlinedTextField(
                                    value = state.aetherSettings.endpointIp,
                                    onValueChange = { v -> viewModel.updateAether { it.copy(endpointIp = v, scanForEndpoint = v.isBlank()) } },
                                    label = { Text(tr("Saved Endpoint IP", "آی‌پی اندپوینت")) },
                                    trailingIcon = if (state.aetherSettings.endpointIp.isNotBlank()) {
                                        {
                                            IconButton(onClick = viewModel::clearAppliedEndpointIp) {
                                                Icon(Icons.Rounded.Clear, contentDescription = "Clear IP", modifier = Modifier.size(16.dp))
                                            }
                                        }
                                    } else null,
                                    singleLine = true,
                                    modifier = Modifier.weight(2f),
                                )
                                OutlinedTextField(
                                    value = state.aetherSettings.endpointPort.toString(),
                                    onValueChange = { v -> v.toIntOrNull()?.let { p -> viewModel.updateAether { it.copy(endpointPort = p) } } },
                                    label = { Text(tr("Port", "پورت")) },
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                    singleLine = true,
                                    modifier = Modifier.weight(1f),
                                )
                            }
                        }

                        Spacer(Modifier.height(6.dp))
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { expandStep3Advanced = !expandStep3Advanced },
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                text = tr("Advanced (Noize, Fragment, ECH) & Command", "تنظیمات پیشرفته (Fragment، ECH، Noize)"),
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.primary,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f),
                            )
                            Icon(if (expandStep3Advanced) Icons.Rounded.ExpandLess else Icons.Rounded.ExpandMore, null)
                        }

                        AnimatedVisibility(visible = expandStep3Advanced) {
                            Column(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 8.dp)) {
                                PnifoxDropdownField(
                                    label = "Noize",
                                    selectedText = state.aetherSettings.noizeProfile.label,
                                    options = AetherNoizeProfile.entries,
                                    selectedItem = state.aetherSettings.noizeProfile,
                                    optionLabel = { it.label },
                                    onSelect = { nz -> viewModel.updateAether { it.copy(noizeProfile = nz) } },
                                    modifier = Modifier.fillMaxWidth(),
                                )
                                OutlinedTextField(
                                    value = state.aetherSettings.sni,
                                    onValueChange = { v -> viewModel.updateAether { it.copy(sni = v) } },
                                    label = { Text("MASQUE SNI (--masque-sni)") },
                                    singleLine = true,
                                    modifier = Modifier.fillMaxWidth(),
                                )
                                PnifoxSwitchRow(
                                    title = tr("Fragment ClientHello (--fragment)", "تکه‌سازی TLS (--fragment)"),
                                    checked = state.aetherSettings.fragmentEnabled,
                                    onCheckedChange = { c -> viewModel.updateAether { it.copy(fragmentEnabled = c) } },
                                )
                                if (state.aetherSettings.fragmentEnabled) {
                                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                        OutlinedTextField(
                                            value = state.aetherSettings.fragmentSizeBytes,
                                            onValueChange = { v -> viewModel.updateAether { it.copy(fragmentSizeBytes = v) } },
                                            label = { Text("Size (8-16)") },
                                            singleLine = true,
                                            modifier = Modifier.weight(1f),
                                        )
                                        OutlinedTextField(
                                            value = state.aetherSettings.fragmentDelayMs,
                                            onValueChange = { v -> viewModel.updateAether { it.copy(fragmentDelayMs = v) } },
                                            label = { Text("Delay ms") },
                                            singleLine = true,
                                            modifier = Modifier.weight(1f),
                                        )
                                    }
                                }
                                PnifoxSwitchRow(
                                    title = tr("ECH (Default Off)", "فعال‌سازی ECH (پیش‌فرض خاموش)"),
                                    checked = state.aetherSettings.echEnabled,
                                    onCheckedChange = { c -> viewModel.updateAether { it.copy(echEnabled = c) } },
                                )
                                if (state.aetherSettings.echEnabled) {
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
                                        title = tr("Smart ECH Bootstrap", "دریافت هوشمند ECH"),
                                        checked = state.aetherSettings.smartEchBootstrap,
                                        onCheckedChange = { c -> viewModel.updateAether { it.copy(smartEchBootstrap = c) } },
                                    )
                                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                                        OutlinedButton(
                                            onClick = viewModel::fetchEchConfigNow,
                                            enabled = !state.isFetchingEch,
                                            shape = RoundedCornerShape(10.dp),
                                        ) {
                                            Text(if (state.isFetchingEch) tr("Fetching...", "دریافت...") else tr("Fetch ECH", "دریافت ECH"), maxLines = 1)
                                        }
                                        if (state.aetherSettings.echBase64Override.isNotBlank()) {
                                            PnifoxBadgePill(
                                                text = tr("ECH Ready ✓", "کلید آماده ✓"),
                                                containerColor = Color(0xFF10B981).copy(alpha = 0.16f),
                                                contentColor = Color(0xFF10B981),
                                            )
                                        }
                                    }
                                }
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
