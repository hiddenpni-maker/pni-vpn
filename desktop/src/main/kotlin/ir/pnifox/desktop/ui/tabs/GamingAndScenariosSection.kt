package ir.pnifox.desktop.ui.tabs

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Bolt
import androidx.compose.material.icons.rounded.Dns
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Speed
import androidx.compose.material.icons.rounded.SportsEsports
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import ir.pnifox.core.model.ActiveSectionEngine
import ir.pnifox.core.model.AppLanguage
import ir.pnifox.core.model.GamingMode
import ir.pnifox.core.model.IranPresetsCatalog
import ir.pnifox.core.model.PnifoxScenarioCatalog
import ir.pnifox.core.model.ProtocolExplainerCatalog
import ir.pnifox.core.model.TunnelState
import ir.pnifox.desktop.model.WindowsTunnelMode
import ir.pnifox.desktop.ui.DesktopBadgePill
import ir.pnifox.desktop.ui.DesktopCard
import ir.pnifox.desktop.ui.DesktopConnectButton
import ir.pnifox.desktop.ui.DesktopDropdownButton
import ir.pnifox.desktop.ui.DesktopFoxMascot
import ir.pnifox.desktop.ui.DesktopInfoPopupButton
import ir.pnifox.desktop.ui.DesktopStateHolder
import ir.pnifox.desktop.ui.DesktopSwitchRow
import ir.pnifox.desktop.ui.DesktopUiState
import ir.pnifox.desktop.ui.LocalDesktopLanguage
import ir.pnifox.desktop.ui.dtr

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun GamingDesktopTab(
    state: DesktopUiState,
    holder: DesktopStateHolder,
) {
    val isFa = LocalDesktopLanguage.current == AppLanguage.PERSIAN
    val gaming = state.persisted.gamingSettings
    val tunMode = state.persisted.windowsTunnelMode
    val isConnected = state.tunnelState is TunnelState.Connected && state.tunnelState.section == ActiveSectionEngine.GAMING
    val isConnecting = state.tunnelState is TunnelState.Connecting && state.tunnelState.section == ActiveSectionEngine.GAMING
    val showTunSuggestion = tunMode == WindowsTunnelMode.SYSTEM_PROXY && !state.persisted.tunPromptDismissed

    val gamePresets = remember {
        IranPresetsCatalog.dnsPresets.filter { it.badge == "Game" || it.id in setOf("cf_udp_1", "cf_pdoq", "quad9_unfiltered") }
    }
    val selectedDnsPreset = gamePresets.firstOrNull { it.address.substringBefore(':') == gaming.selectedDnsIp.substringBefore(':') }
        ?: gamePresets.firstOrNull()

    val gamingInfoBody = remember(isFa) {
        GamingMode.entries.joinToString("\n\n") { gm ->
            val title = if (isFa) gm.titleFa else gm.titleEn
            val sub = if (isFa) gm.subtitleFa else gm.subtitleEn
            "• $title\n$sub"
        }
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        // 1. Hero + Connect
        item {
            DesktopCard {
                Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Column(modifier = Modifier.weight(1f)) {
                        DesktopFoxMascot(
                            tunnelState = state.tunnelState,
                            section = ActiveSectionEngine.GAMING,
                            onFallbackSystemProxy = { holder.connectViaSystemProxyFallback(ActiveSectionEngine.GAMING) },
                            onRestartAdmin = { holder.relaunchAsAdmin() },
                            showTunSuggestion = showTunSuggestion,
                            onEnableTunMode = { holder.enableTunAndReconnect(ActiveSectionEngine.GAMING) },
                            onDismissTunSuggestion = { holder.dismissTunSuggestion() },
                        )
                        Spacer(Modifier.height(6.dp))
                        Text(
                            text = if (isFa) gaming.mode.titleFa else gaming.mode.titleEn,
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    Spacer(Modifier.width(16.dp))
                    DesktopConnectButton(
                        isConnected = isConnected,
                        isConnecting = isConnecting,
                        onClick = { holder.toggleConnect(ActiveSectionEngine.GAMING) },
                    )
                }

                if (tunMode == WindowsTunnelMode.SYSTEM_PROXY) {
                    Spacer(Modifier.height(8.dp))
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        FlowRow(
                            modifier = Modifier.fillMaxWidth().padding(10.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            Text(
                                text = dtr(
                                    "Online games (.exe) require TUN Mode to capture direct UDP/TCP traffic.",
                                    "برای عبور ترافیک مستقیم بازی‌ها (.exe)، روشن کردن TUN Mode پیشنهاد می‌شود.",
                                ),
                                style = MaterialTheme.typography.bodySmall,
                                modifier = Modifier.weight(1f, fill = false),
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                            )
                            OutlinedButton(
                                onClick = { holder.enableTunAndReconnect(ActiveSectionEngine.GAMING) },
                                shape = RoundedCornerShape(8.dp),
                            ) {
                                Text(dtr("Enable TUN Mode", "روشن کردن TUN Mode"), style = MaterialTheme.typography.labelSmall, maxLines = 1)
                            }
                        }
                    }
                }
            }
        }

        // 2. Compact Dropdowns for Gaming Method & Anti-Sanction DNS
        item {
            DesktopCard {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text(
                        dtr("Gaming Engine & DNS", "موتور گیم و DNS ضدتحریم"),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                    )
                    DesktopInfoPopupButton(
                        title = dtr("Gaming Connection Methods", "توضیحات روش‌های اتصال گیمینگ"),
                        body = gamingInfoBody,
                    )
                }
                Spacer(Modifier.height(10.dp))
                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    DesktopDropdownButton(
                        label = dtr("Method", "روش"),
                        selectedText = if (isFa) gaming.mode.titleFa else gaming.mode.titleEn,
                        options = GamingMode.entries,
                        selectedItem = gaming.mode,
                        optionLabel = { gm -> if (isFa) gm.titleFa else gm.titleEn },
                        onSelect = { gm -> holder.updateGaming { it.copy(mode = gm) } },
                        icon = Icons.Rounded.SportsEsports,
                    )
                    DesktopDropdownButton(
                        label = dtr("Game DNS", "DNS گیم"),
                        selectedText = selectedDnsPreset?.let { preset ->
                            val ip = preset.address.substringBefore(':')
                            val ping = state.gamingPingResults[ip]
                            val pStr = if (ping != null) " (${ping}ms)" else ""
                            "${if (isFa) preset.titleFa else preset.titleEn}$pStr"
                        } ?: gaming.selectedDnsIp,
                        options = gamePresets,
                        selectedItem = selectedDnsPreset,
                        optionLabel = { preset ->
                            val ip = preset.address.substringBefore(':')
                            val ping = state.gamingPingResults[ip]
                            val pStr = if (ping != null) " • ${ping}ms" else ""
                            "${if (isFa) preset.titleFa else preset.titleEn}$pStr"
                        },
                        onSelect = { preset ->
                            val ip = preset.address.substringBefore(':')
                            holder.updateGaming { it.copy(selectedDnsIp = ip) }
                        },
                        icon = Icons.Rounded.Dns,
                    )
                    OutlinedButton(
                        onClick = { holder.testGamingPings() },
                        enabled = !state.isTestingGamingPings,
                        shape = RoundedCornerShape(10.dp),
                    ) {
                        if (state.isTestingGamingPings) CircularProgressIndicator(modifier = Modifier.size(14.dp), strokeWidth = 2.dp)
                        else Icon(Icons.Rounded.Speed, null, modifier = Modifier.size(15.dp))
                        Spacer(Modifier.width(5.dp))
                        Text(dtr("Ping DNS", "تست پینگ"), style = MaterialTheme.typography.labelSmall, maxLines = 1)
                    }
                }
            }
        }

        // 3. Compact Tuning
        item {
            DesktopCard {
                Text(
                    dtr("Gaming Tuning", "تنظیمات گیمینگ"),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                )
                Spacer(Modifier.height(6.dp))
                if (gaming.mode == GamingMode.LOCAL_DPI_DESYNC) {
                    Text(
                        dtr("TLS Fragment Chunk: ${gaming.desyncChunkLen} bytes", "اندازه تکه‌سازی TLS: ${gaming.desyncChunkLen} بایت"),
                        style = MaterialTheme.typography.bodySmall,
                    )
                    Slider(
                        value = gaming.desyncChunkLen.toFloat(),
                        onValueChange = { v -> holder.updateGaming { it.copy(desyncChunkLen = v.toInt().coerceIn(2, 16)) } },
                        valueRange = 2f..16f,
                        steps = 13,
                    )
                }
                DesktopSwitchRow(
                    title = dtr("Bypass Iranian Game Servers", "عبور مستقیم سرورهای گیم داخلی"),
                    checked = gaming.bypassIranGameTraffic,
                    onCheckedChange = { c -> holder.updateGaming { it.copy(bypassIranGameTraffic = c) } },
                )
                DesktopSwitchRow(
                    title = dtr("QUIC v2 & 0-RTT (AETHER UDP)", "فعال‌سازی QUIC v2 در تانل UDP"),
                    checked = gaming.preferQuicV2H3,
                    onCheckedChange = { c -> holder.updateGaming { it.copy(preferQuicV2H3 = c) } },
                )
                DesktopSwitchRow(
                    title = dtr("Block UDP/443 QUIC in Local Desync", "بستن QUIC پورت ۴۴۳ در ضدDPI محلی"),
                    checked = gaming.blockQuicUdp443,
                    onCheckedChange = { c -> holder.updateGaming { it.copy(blockQuicUdp443 = c) } },
                )
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ScenarioPresetsDesktopCard(
    state: DesktopUiState,
    holder: DesktopStateHolder,
) {
    val isFa = LocalDesktopLanguage.current == AppLanguage.PERSIAN
    var showGuideDialog by remember { mutableStateOf(false) }
    val selectedProfile = PnifoxScenarioCatalog.scenarios.firstOrNull { it.id == state.persisted.selectedScenarioId }
        ?: PnifoxScenarioCatalog.scenarios.first()

    DesktopCard(padding = 12.dp) {
        FlowRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            DesktopDropdownButton(
                label = dtr("Use Case", "کاربری آماده"),
                selectedText = if (isFa) selectedProfile.id.titleFa else selectedProfile.id.titleEn,
                options = PnifoxScenarioCatalog.scenarios,
                selectedItem = selectedProfile,
                optionLabel = { p -> if (isFa) p.id.titleFa else p.id.titleEn },
                onSelect = { p -> holder.applyScenario(p, autoConnect = false) },
                icon = Icons.Rounded.Tune,
            )
            Button(
                onClick = { holder.applyScenario(selectedProfile, autoConnect = true) },
                shape = RoundedCornerShape(10.dp),
            ) {
                Icon(Icons.Rounded.Bolt, null, modifier = Modifier.size(15.dp))
                Spacer(Modifier.width(4.dp))
                Text(dtr("Connect", "اتصال سریع"), style = MaterialTheme.typography.labelSmall, maxLines = 1)
            }
            OutlinedButton(
                onClick = { showGuideDialog = true },
                shape = RoundedCornerShape(10.dp),
            ) {
                Icon(Icons.Rounded.Info, null, modifier = Modifier.size(15.dp))
                Spacer(Modifier.width(4.dp))
                Text(dtr("Guide", "توضیحات"), style = MaterialTheme.typography.labelSmall, maxLines = 1)
            }
        }
    }

    if (showGuideDialog) {
        AlertDialog(
            onDismissRequest = { showGuideDialog = false },
            title = {
                Text(
                    dtr("Use Cases & Protocols Guide", "توضیحات روش‌های اتصال و کاربری‌ها"),
                    fontWeight = FontWeight.Bold,
                )
            },
            text = {
                Column(
                    modifier = Modifier.fillMaxWidth().heightIn(max = 420.dp).verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text(
                        dtr("Scenarios", "حالت‌های کاربری"),
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Bold,
                    )
                    PnifoxScenarioCatalog.scenarios.forEach { profile ->
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f),
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Text(
                                        text = if (isFa) profile.id.titleFa else profile.id.titleEn,
                                        fontWeight = FontWeight.Bold,
                                        style = MaterialTheme.typography.bodyMedium,
                                        modifier = Modifier.weight(1f),
                                    )
                                    DesktopBadgePill(if (isFa) profile.badgeFa else profile.badgeEn)
                                }
                                Text(if (isFa) profile.explanationFa else profile.explanationEn, style = MaterialTheme.typography.bodySmall)
                                Text(
                                    text = if (isFa) profile.tradeOffFa else profile.tradeOffEn,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.secondary,
                                )
                            }
                        }
                    }

                    Spacer(Modifier.height(4.dp))
                    Text(
                        dtr("Connection Protocols", "مقایسه پروتکل‌ها"),
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Bold,
                    )
                    ProtocolExplainerCatalog.items.forEach { item ->
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Text(if (isFa) item.nameFa else item.nameEn, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                                    DesktopBadgePill(item.badge)
                                }
                                Text(if (isFa) item.summaryFa else item.summaryEn, style = MaterialTheme.typography.bodySmall)
                                Text(
                                    text = if (isFa) "کاربرد: ${item.bestForFa}" else "Best for: ${item.bestForEn}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.secondary,
                                )
                            }
                        }
                    }
                }
            },
            confirmButton = {
                Button(onClick = { showGuideDialog = false }) {
                    Text(dtr("Close", "بستن"))
                }
            },
        )
    }
}
