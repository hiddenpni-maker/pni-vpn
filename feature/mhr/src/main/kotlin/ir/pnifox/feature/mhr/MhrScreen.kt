package ir.pnifox.feature.mhr

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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.HelpOutline
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.AutoFixHigh
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.ContentPaste
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.ExpandLess
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.LockOpen
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material.icons.rounded.Speed
import androidx.compose.material.icons.rounded.VerifiedUser
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import ir.pnifox.core.model.ActiveSectionEngine
import ir.pnifox.core.model.AppLanguage
import ir.pnifox.core.model.ConnectionMode
import ir.pnifox.core.model.MhrMode
import ir.pnifox.core.model.TrafficFormatters
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
import ir.pnifox.core.ui.RulesetsBottomSheet
import ir.pnifox.core.ui.TunnelTelemetryRow
import ir.pnifox.core.ui.responsiveListPadding
import ir.pnifox.core.ui.tr

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun MhrRoute(
    viewModel: MhrViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val isFa = LocalAppLanguage.current == AppLanguage.PERSIAN
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current

    var scriptInput by remember { mutableStateOf("") }
    var customSniInput by remember { mutableStateOf("") }
    var expandSniPool by remember { mutableStateOf(false) }
    var expandAdvanced by remember { mutableStateOf(false) }
    var expandGuide by remember { mutableStateOf(state.settings.scriptIds.isEmpty()) }
    var showLogsSheet by remember { mutableStateOf(false) }
    var showRulesetsSheet by remember { mutableStateOf(false) }
    var showImportDialog by remember { mutableStateOf(false) }
    var showCaInstallDialog by remember { mutableStateOf(false) }
    var caSavedPathNotice by remember { mutableStateOf<String?>(null) }

    val vpnPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult(),
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            viewModel.startMhrAfterPermission()
        }
    }

    val mhrConnected = state.tunnelState is TunnelState.Connected &&
        (state.tunnelState as TunnelState.Connected).section == ActiveSectionEngine.MHR
    val mhrConnecting = state.tunnelState is TunnelState.Connecting &&
        (state.tunnelState as TunnelState.Connecting).section == ActiveSectionEngine.MHR

    val onConnectMhr = {
        val active = mhrConnected || mhrConnecting
        if (!active && state.appSettings.connectionMode == ConnectionMode.VPN) {
            val perm = viewModel.prepareVpnIntent()
            if (perm != null) {
                val launched = runCatching { vpnPermissionLauncher.launch(perm) }.isSuccess
                if (!launched) viewModel.toggleMhrConnection()
            } else {
                viewModel.toggleMhrConnection()
            }
        } else {
            viewModel.toggleMhrConnection()
        }
    }

    ResponsiveScreenContainer { metrics ->
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = responsiveListPadding(metrics),
            verticalArrangement = Arrangement.spacedBy(metrics.sectionSpacing),
        ) {
            // 1. Header Bar
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(
                                text = tr("MasterHttpRelay", "رله هوشمند MHR"),
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            PnifoxBadgePill(text = "v${state.mhrVersion}")
                        }
                        Text(
                            text = "mhrv-rs • Google Fronting & Apps Script",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    IconButton(onClick = { showImportDialog = true }) {
                        Icon(Icons.Rounded.ContentPaste, contentDescription = "Import mhrv-rs://")
                    }
                    IconButton(
                        onClick = {
                            clipboard.setText(AnnotatedString(viewModel.exportShareUri()))
                        },
                    ) {
                        Icon(Icons.Rounded.Share, contentDescription = "Share mhrv-rs://")
                    }
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

            if (!state.statusMessage.isNullOrBlank()) {
                item {
                    PnifoxCard(
                        containerColor = MaterialTheme.colorScheme.primaryContainer,
                        contentPadding = 12.dp,
                        modifier = Modifier.clickable { viewModel.dismissBanner() },
                    ) {
                        Text(state.statusMessage.orEmpty(), style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }

            // 2. Hero Connection Card + Mode Switcher
            item {
                PnifoxCard(contentPadding = metrics.cardPadding) {
                    FoxConnectionMascot(
                        tunnelState = state.tunnelState,
                        section = ActiveSectionEngine.MHR,
                        onSuggestionClick = { _ -> viewModel.autoDetectGoogleIp() },
                    )
                    Spacer(Modifier.height(12.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = if (isFa) state.settings.mode.labelFa else state.settings.mode.labelEn,
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            Text(
                                text = "${state.settings.googleIp} • ${state.settings.frontDomain}",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            Text(
                                text = tr(
                                    "Deployments: ${state.settings.scriptIds.size} • SNIs: ${state.settings.sniHosts.size}",
                                    "رله‌های فعال: ${state.settings.scriptIds.size} • pool SNI: ${state.settings.sniHosts.size}",
                                ),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.primary,
                            )
                        }
                        Spacer(Modifier.width(12.dp))
                        PnifoxConnectButton(
                            isConnected = mhrConnected,
                            isConnecting = mhrConnecting,
                            onClick = onConnectMhr,
                            size = 76.dp,
                            contentDescription = "Connect MHR",
                        )
                    }

                    Spacer(Modifier.height(12.dp))
                    Text(
                        text = tr("Relay Operating Mode", "حالت عملکرد رله MHR"),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(6.dp))
                    FlowRow(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        MhrMode.entries.forEach { mode ->
                            FilterChip(
                                selected = state.settings.mode == mode,
                                onClick = { viewModel.setMode(mode) },
                                label = { Text(if (isFa) mode.labelFa else mode.labelEn, maxLines = 1) },
                            )
                        }
                    }
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = if (isFa) state.settings.mode.descFa else state.settings.mode.descEn,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            // 3. Shared Speed + Real Ping Telemetry Row
            item {
                val activePing = (state.tunnelState as? TunnelState.Connected)?.takeIf {
                    it.section == ActiveSectionEngine.MHR
                }?.pingMs
                TunnelTelemetryRow(
                    rxSpeedBps = state.rxSpeedBps,
                    txSpeedBps = state.txSpeedBps,
                    sessionRxBytes = state.sessionRxBytes,
                    sessionTxBytes = state.sessionTxBytes,
                    pingMs = activePing,
                    modeLabel = if (isFa) state.settings.mode.labelFa else state.settings.mode.labelEn,
                    isConnectedForSection = mhrConnected,
                    isTestingPing = state.isTestingPing,
                    onRetestPing = { viewModel.testActiveRealPing() },
                )
            }

            // 3b. Apps Script Quota Counter (visible when running in APPS_SCRIPT / FULL mode)
            if (mhrConnected && state.mhrStats != null) {
                val st = state.mhrStats!!
                val pct = (st.todayCalls.toDouble() / st.freeQuotaPerDay.coerceAtLeast(1L)) * 100.0
                item {
                    PnifoxCard(contentPadding = metrics.cardPadding) {
                        Text(
                            text = tr("Apps Script Relay Usage Today", "مصرف امروز رله گوگل اسکریپت"),
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                        )
                        Spacer(Modifier.height(6.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                        ) {
                            Text(
                                text = tr("Requests:", "درخواست‌ها:"),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Text(
                                text = "${st.todayCalls} / ${st.freeQuotaPerDay} (${String.format("%.1f", pct)}%)",
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = FontWeight.SemiBold,
                            )
                        }
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                        ) {
                            Text(
                                text = tr("Relay Data:", "حجم مصرفی رله:"),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Text(
                                text = TrafficFormatters.formatBytes(st.todayBytes),
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = FontWeight.SemiBold,
                            )
                        }
                    }
                }
            }

            // 4. Apps Script Relay Card
            if (state.settings.mode != MhrMode.DIRECT) {
                item { PnifoxSectionLabel(title = tr("Apps Script Relay", "تنظیمات رله گوگل اسکریپت")) }
                item {
                    PnifoxCard(contentPadding = metrics.cardPadding) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            OutlinedTextField(
                                value = scriptInput,
                                onValueChange = { scriptInput = it },
                                label = { Text(tr("Deployment URL(s) or Script ID(s)", "لینک / ID دیپلویمنت")) },
                                placeholder = { Text("https://script.google.com/macros/s/.../exec") },
                                minLines = 2,
                                maxLines = 4,
                                modifier = Modifier.weight(1f),
                            )
                            Button(
                                onClick = {
                                    viewModel.addScriptIds(scriptInput)
                                    scriptInput = ""
                                },
                                shape = RoundedCornerShape(12.dp),
                            ) {
                                Icon(Icons.Rounded.Add, null, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(4.dp))
                                Text(tr("Add", "افزودن"))
                            }
                        }

                        if (state.settings.scriptIds.isNotEmpty()) {
                            Spacer(Modifier.height(8.dp))
                            FlowRow(
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                verticalArrangement = Arrangement.spacedBy(6.dp),
                            ) {
                                state.settings.scriptIds.forEachIndexed { idx, id ->
                                    Surface(
                                        shape = RoundedCornerShape(10.dp),
                                        color = MaterialTheme.colorScheme.surfaceVariant,
                                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)),
                                    ) {
                                        Row(
                                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                        ) {
                                            Text(
                                                text = "#${idx + 1} ${id.take(16)}...",
                                                style = MaterialTheme.typography.labelSmall.copy(fontFamily = FontFamily.Monospace),
                                            )
                                            Spacer(Modifier.width(4.dp))
                                            Icon(
                                                Icons.Rounded.Close,
                                                contentDescription = "Remove",
                                                modifier = Modifier
                                                    .size(16.dp)
                                                    .clickable { viewModel.removeScriptId(id) },
                                            )
                                        }
                                    }
                                }
                            }
                        } else {
                            Spacer(Modifier.height(6.dp))
                            Text(
                                text = tr(
                                    "Multiple IDs rotate round-robin automatically. Need script.google.com access? Connect with Mode = Direct first!",
                                    "می‌توانید چند ID را هم‌زمان وارد کنید تا ترافیک بین آن‌ها تقسیم شود. برای ساخت اسکریپت، ابتدا با حالت «مستقیم» وصل شوید.",
                                ),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }

                        Spacer(Modifier.height(10.dp))
                        OutlinedTextField(
                            value = state.settings.authKey,
                            onValueChange = viewModel::setAuthKey,
                            label = { Text(tr("auth_key (Shared secret in Code.gs)", "کلید امنیتی (auth_key داخل اسکریپت)")) },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }
            }

            // 5. Network & Edge IP Card + MITM Certificate Button
            item { PnifoxSectionLabel(title = tr("Google Fronting & Certificate", "آی‌پی گوگل و گواهی MITM")) }
            item {
                PnifoxCard(contentPadding = metrics.cardPadding) {
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(
                            value = state.settings.googleIp,
                            onValueChange = viewModel::setGoogleIp,
                            label = { Text("google_ip") },
                            singleLine = true,
                            modifier = Modifier.weight(1f),
                        )
                        OutlinedTextField(
                            value = state.settings.frontDomain,
                            onValueChange = viewModel::setFrontDomain,
                            label = { Text("front_domain") },
                            singleLine = true,
                            modifier = Modifier.weight(1f),
                        )
                    }
                    Spacer(Modifier.height(8.dp))
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(
                            onClick = viewModel::autoDetectGoogleIp,
                            enabled = !state.isDetectingIp,
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(12.dp),
                        ) {
                            if (state.isDetectingIp) {
                                CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                            } else {
                                Icon(Icons.Rounded.AutoFixHigh, null, modifier = Modifier.size(16.dp))
                            }
                            Spacer(Modifier.width(6.dp))
                            Text(tr("Auto-detect IP", "تشخیص خودکار IP"), maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }

                        Button(
                            onClick = { showCaInstallDialog = true },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(12.dp),
                        ) {
                            Icon(
                                if (state.caStatus.isTrustedInstalled) Icons.Rounded.VerifiedUser else Icons.Rounded.LockOpen,
                                null,
                                modifier = Modifier.size(16.dp),
                            )
                            Spacer(Modifier.width(6.dp))
                            Text(
                                if (state.caStatus.isTrustedInstalled) tr("CA Trusted ✓", "گواهی نصب است ✓")
                                else tr("MITM CA Cert", "نصب گواهی CA"),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                }
            }

            // 6. Collapsible SNI Pool + Real Native Probe Tester
            item {
                PnifoxCard(contentPadding = metrics.cardPadding) {
                    Row(
                        modifier = Modifier.fillMaxWidth().clickable { expandSniPool = !expandSniPool },
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = tr("SNI Rotation Pool & Native Tester", "لیست SNI و تست پینگ بومی"),
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                            )
                            Text(
                                text = tr("${state.settings.sniHosts.size} SNIs configured", "${state.settings.sniHosts.size} دامنه SNI در صف چرخش"),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Icon(if (expandSniPool) Icons.Rounded.ExpandLess else Icons.Rounded.ExpandMore, null)
                    }
                    AnimatedVisibility(visible = expandSniPool) {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 10.dp)) {
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                                Button(
                                    onClick = viewModel::testAllSnis,
                                    enabled = !state.isTestingSnis,
                                    modifier = Modifier.weight(1f),
                                    shape = RoundedCornerShape(10.dp),
                                ) {
                                    Icon(Icons.Rounded.Speed, null, modifier = Modifier.size(16.dp))
                                    Spacer(Modifier.width(6.dp))
                                    Text(if (state.isTestingSnis) tr("Testing...", "در حال تست...") else tr("Test All SNIs", "تست همه SNIها"))
                                }
                                OutlinedButton(onClick = viewModel::resetSniPool, shape = RoundedCornerShape(10.dp)) {
                                    Text(tr("Reset Pool", "بازنشانی"))
                                }
                            }
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                OutlinedTextField(
                                    value = customSniInput,
                                    onValueChange = { customSniInput = it },
                                    label = { Text(tr("Add Custom SNI", "افزودن SNI دلخواه")) },
                                    singleLine = true,
                                    modifier = Modifier.weight(1f),
                                )
                                IconButton(
                                    onClick = {
                                        viewModel.addSniHost(customSniInput)
                                        customSniInput = ""
                                    },
                                ) { Icon(Icons.Rounded.Add, null) }
                            }
                            state.settings.sniHosts.forEach { sni ->
                                val res = state.sniResults[sni]
                                val testingThis = state.testingSniHost == sni
                                Surface(
                                    shape = RoundedCornerShape(10.dp),
                                    color = MaterialTheme.colorScheme.surfaceVariant,
                                    modifier = Modifier.fillMaxWidth(),
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                    ) {
                                        Text(
                                            text = sni,
                                            style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                                            modifier = Modifier.weight(1f),
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                        )
                                        if (res != null) {
                                            PnifoxBadgePill(
                                                text = if (res.ok) "${res.latencyMs}ms" else "Fail",
                                                containerColor = if (res.ok) Color(0xFF10B981).copy(alpha = 0.16f) else MaterialTheme.colorScheme.error.copy(alpha = 0.16f),
                                                contentColor = if (res.ok) Color(0xFF10B981) else MaterialTheme.colorScheme.error,
                                            )
                                            Spacer(Modifier.width(4.dp))
                                        }
                                        TextButton(onClick = { viewModel.setFrontDomain(sni) }) {
                                            Text(tr("Use", "انتخاب"), style = MaterialTheme.typography.labelSmall)
                                        }
                                        IconButton(onClick = { viewModel.testSingleSni(sni) }, enabled = state.testingSniHost == null) {
                                            if (testingThis) CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                                            else Icon(Icons.Rounded.Speed, null, modifier = Modifier.size(16.dp))
                                        }
                                        IconButton(onClick = { viewModel.removeSniHost(sni) }) {
                                            Icon(Icons.Rounded.DeleteOutline, null, modifier = Modifier.size(16.dp))
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // 7. Advanced Settings Accordion
            item {
                PnifoxCard(contentPadding = metrics.cardPadding) {
                    Row(
                        modifier = Modifier.fillMaxWidth().clickable { expandAdvanced = !expandAdvanced },
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = tr("Advanced MHR Parameters", "تنظیمات پیشرفته رله"),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.weight(1f),
                        )
                        Icon(if (expandAdvanced) Icons.Rounded.ExpandLess else Icons.Rounded.ExpandMore, null)
                    }
                    AnimatedVisibility(visible = expandAdvanced) {
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp), modifier = Modifier.padding(top = 8.dp)) {
                            Text(
                                text = tr("Parallel Relay Concurrency: ${state.settings.parallelRelay}", "تعداد کانال هم‌زمان رله: ${state.settings.parallelRelay}"),
                                style = MaterialTheme.typography.labelMedium,
                            )
                            Slider(
                                value = state.settings.parallelRelay.toFloat(),
                                onValueChange = { v -> viewModel.updateMhr { it.copy(parallelRelay = v.toInt().coerceIn(1, 8)) } },
                                valueRange = 1f..8f,
                                steps = 6,
                            )
                            PnifoxSwitchRow(title = tr("Verify Upstream SSL", "بررسی گواهی SSL مقصد"), checked = state.settings.verifySsl, onCheckedChange = { c -> viewModel.updateMhr { it.copy(verifySsl = c) } })
                            PnifoxSwitchRow(title = tr("Force HTTP/1.1 (Disable H2)", "اجبار به HTTP/1.1"), checked = state.settings.forceHttp1, onCheckedChange = { c -> viewModel.updateMhr { it.copy(forceHttp1 = c) } })
                            PnifoxSwitchRow(title = tr("Block QUIC (UDP/443)", "مسدودسازی QUIC برای پایداری"), checked = state.settings.blockQuic, onCheckedChange = { c -> viewModel.updateMhr { it.copy(blockQuic = c) } })
                            PnifoxSwitchRow(title = tr("Block STUN / WebRTC Leaks", "مسدودسازی STUN"), checked = state.settings.blockStun, onCheckedChange = { c -> viewModel.updateMhr { it.copy(blockStun = c) } })
                            PnifoxSwitchRow(title = tr("Tunnel DoH Requests", "تونل درخواست‌های DoH"), checked = state.settings.tunnelDoh, onCheckedChange = { c -> viewModel.updateMhr { it.copy(tunnelDoh = c) } })
                            PnifoxSwitchRow(title = tr("Block Direct DoH", "بلاک DoH پیش‌فرض مرورگرها"), checked = state.settings.blockDoh, onCheckedChange = { c -> viewModel.updateMhr { it.copy(blockDoh = c) } })
                            PnifoxSwitchRow(title = tr("Route YouTube via Relay", "عبور یوتیوب از رله اسکریپت"), checked = state.settings.youtubeViaRelay, onCheckedChange = { c -> viewModel.updateMhr { it.copy(youtubeViaRelay = c) } })
                            OutlinedTextField(
                                value = state.settings.upstreamSocks5,
                                onValueChange = { v -> viewModel.updateMhr { it.copy(upstreamSocks5 = v) } },
                                label = { Text("Upstream SOCKS5 (optional)") },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth(),
                            )
                        }
                    }
                }
            }

            // 8. Complete Beginner Guide & Code.gs / CodeFull.gs Copy Box
            item {
                PnifoxCard(contentPadding = metrics.cardPadding) {
                    Row(
                        modifier = Modifier.fillMaxWidth().clickable { expandGuide = !expandGuide },
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(Icons.AutoMirrored.Rounded.HelpOutline, null, tint = MaterialTheme.colorScheme.primary)
                        Spacer(Modifier.width(8.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = tr("Complete MHR Setup Guide & Code.gs", "آموزش قدم‌به‌قدم راه‌اندازی MHR + دریافت کد اسکریپت"),
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                            )
                            Text(
                                text = tr("How to deploy Google Apps Script, get AUTH_KEY, and install CA Cert", "آموزش ساخت اسکریپت گوگل، دریافت کلید و نصب گواهی"),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Icon(if (expandGuide) Icons.Rounded.ExpandLess else Icons.Rounded.ExpandMore, null)
                    }
                    AnimatedVisibility(visible = expandGuide) {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 10.dp)) {
                            val steps = if (isFa) {
                                listOf(
                                    "۱. اگر سایت script.google.com برای شما فیلتر است، ابتدا حالت MHR را روی «مستقیم (بدون رله)» بگذارید، روی Auto-detect IP بزنید و دکمه اتصال را روشن کنید.",
                                    "۲. وارد script.google.com شوید، پروژه جدید (New project) بسازید و کد Code.gs زیر را کپی و جایگزین کنید (برای حالت بدون گواهی از CodeFull.gs استفاده کنید).",
                                    "۳. داخل کد، مقدار AUTH_KEY را یک عبارت دلخواه بگذارید -> دکمه Deploy -> New deployment -> نوع Web app -> گزینه Who has access روی Anyone و Deploy را بزنید.",
                                    "۴. لینک خروجی (/exec) یا ID بین /macros/s/ و /exec را به همراه AUTH_KEY در بخش بالا وارد کنید (+ افزودن).",
                                    "۵. دکمه «نصب گواهی CA» را بزنید -> فایل pnifox-mhr-ca.crt در پوشه Downloads ذخیره می‌شود -> در تنظیمات گوشی عبارت CA certificate را جستجو و نصب کنید.",
                                    "۶. حالا حالت را روی Apps Script (MITM) بگذارید و دکمه اتصال را لمس کنید!",
                                )
                            } else {
                                listOf(
                                    "1. If script.google.com is blocked, connect first using Mode = 'Direct (SNI Front)' as a bootstrap.",
                                    "2. Visit script.google.com -> New project -> paste Code.gs (or CodeFull.gs for Full Tunnel without CA cert).",
                                    "3. Set your secret AUTH_KEY inside the script -> Deploy -> New deployment -> Web app -> Who has access: Anyone.",
                                    "4. Paste the Web App URL / Deployment ID and your AUTH_KEY above.",
                                    "5. Tap 'MITM CA Cert' -> export PEM to Downloads -> search 'CA certificate' in Android Settings and install it.",
                                    "6. Switch Mode back to Apps Script (MITM) and tap Connect!",
                                )
                            }
                            steps.forEach { step ->
                                Text(step, style = MaterialTheme.typography.bodySmall)
                            }
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Button(
                                    onClick = { clipboard.setText(AnnotatedString(viewModel.readCodeGs(fullTunnel = false))) },
                                    modifier = Modifier.weight(1f),
                                    shape = RoundedCornerShape(10.dp),
                                ) {
                                    Icon(Icons.Rounded.ContentCopy, null, modifier = Modifier.size(16.dp))
                                    Spacer(Modifier.width(6.dp))
                                    Text("Copy Code.gs", maxLines = 1)
                                }
                                OutlinedButton(
                                    onClick = { clipboard.setText(AnnotatedString(viewModel.readCodeGs(fullTunnel = true))) },
                                    modifier = Modifier.weight(1f),
                                    shape = RoundedCornerShape(10.dp),
                                ) {
                                    Icon(Icons.Rounded.ContentCopy, null, modifier = Modifier.size(16.dp))
                                    Spacer(Modifier.width(6.dp))
                                    Text("Copy CodeFull.gs", maxLines = 1)
                                }
                            }
                        }
                    }
                }
            }

            // 9. Shared Live Log Panel
            item {
                LiveActivityLogPanel(
                    tunnelState = state.tunnelState,
                    section = ActiveSectionEngine.MHR,
                    logs = state.logs,
                    onCopy = { clipboard.setText(AnnotatedString(viewModel.exportLogs())) },
                    onExpandAll = { showLogsSheet = true },
                    cardPadding = metrics.cardPadding,
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

    if (showRulesetsSheet) {
        RulesetsBottomSheet(
            appSettings = state.appSettings,
            onUpdateApp = viewModel::updateApp,
            isTunnelConnected = mhrConnected || mhrConnecting,
            onApplyAndReconnect = { showRulesetsSheet = false; viewModel.reconnectMhr() },
            onDismiss = { showRulesetsSheet = false },
        )
    }

    if (showImportDialog) {
        var rawShare by remember { mutableStateOf(clipboard.getText()?.text.orEmpty()) }
        AlertDialog(
            onDismissRequest = { showImportDialog = false },
            title = { Text(tr("Import mhrv-rs:// Config", "ورود کانفیگ mhrv-rs://")) },
            text = {
                OutlinedTextField(
                    value = rawShare,
                    onValueChange = { rawShare = it },
                    label = { Text("mhrv-rs://...") },
                    minLines = 3,
                    maxLines = 6,
                    modifier = Modifier.fillMaxWidth(),
                )
            },
            confirmButton = {
                Button(onClick = { viewModel.importShareUri(rawShare); showImportDialog = false }) {
                    Text(tr("Import", "ورود"))
                }
            },
            dismissButton = {
                TextButton(onClick = { showImportDialog = false }) { Text(tr("Cancel", "انصراف")) }
            },
        )
    }

    if (showCaInstallDialog) {
        AlertDialog(
            onDismissRequest = { showCaInstallDialog = false },
            title = { Text(tr("MITM CA Certificate Setup", "نصب گواهی امنیتی MITM CA")) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.verticalScroll(rememberScrollState())) {
                    PnifoxBadgePill(
                        text = if (state.caStatus.isTrustedInstalled) tr("Status: Installed & Trusted ✓", "وضعیت: نصب و تایید شده ✓")
                        else tr("Status: Not Installed Yet", "وضعیت: هنوز نصب نشده"),
                        containerColor = if (state.caStatus.isTrustedInstalled) Color(0xFF10B981).copy(alpha = 0.16f) else MaterialTheme.colorScheme.secondaryContainer,
                        contentColor = if (state.caStatus.isTrustedInstalled) Color(0xFF10B981) else MaterialTheme.colorScheme.onSecondaryContainer,
                    )
                    state.caStatus.subjectCn?.let { cn ->
                        Text("CN: $cn", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                    }
                    state.caStatus.fingerprintHex?.let { fp ->
                        Text(
                            text = "SHA-256: $fp",
                            style = MaterialTheme.typography.labelSmall.copy(fontFamily = FontFamily.Monospace),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    if (!caSavedPathNotice.isNullOrBlank()) {
                        Text(
                            text = tr("Saved to: $caSavedPathNotice", "مسیر فایل ذخیره شده: $caSavedPathNotice"),
                            style = MaterialTheme.typography.labelMedium,
                            color = Color(0xFF10B981),
                        )
                    }
                    Text(
                        text = tr(
                            "Android 11+ requires installing CA certificates from System Settings:\n1) Tap 'Export & Open Settings' below.\n2) In Settings search bar type: CA certificate.\n3) Tap 'CA certificate' -> 'Install anyway' -> pick pnifox-mhr-ca.crt from Downloads.\n4) Return and tap Refresh.",
                            "در اندروید ۱۱ و بالاتر، گواهی باید از تنظیمات گوشی نصب شود:\n۱) دکمه «ذخیره و باز کردن تنظیمات» زیر را بزنید.\n۲) در نوار جستجوی تنظیمات عبارت CA certificate را تایپ کنید.\n۳) گزینه CA certificate را بزنید <- Install anyway <- فایل pnifox-mhr-ca.crt را از پوشه Downloads انتخاب کنید.\n۴) برگردید و بررسی وضعیت را بزنید.",
                        ),
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.exportCaAndGetSettingsIntent { intent, st ->
                            caSavedPathNotice = st.savedLocationLabel
                            runCatching { context.startActivity(intent) }
                        }
                    },
                ) {
                    Icon(Icons.Rounded.Settings, null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(tr("Export & Open Settings", "ذخیره و باز کردن تنظیمات"))
                }
            },
            dismissButton = {
                Row {
                    IconButton(onClick = viewModel::refreshCaStatus) {
                        Icon(Icons.Rounded.Refresh, contentDescription = "Re-check")
                    }
                    TextButton(onClick = { showCaInstallDialog = false }) { Text(tr("Close", "بستن")) }
                }
            },
        )
    }
}
