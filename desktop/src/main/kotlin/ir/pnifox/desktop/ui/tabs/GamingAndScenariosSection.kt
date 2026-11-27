package ir.pnifox.desktop.ui.tabs

import androidx.compose.foundation.BorderStroke
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
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Bolt
import androidx.compose.material.icons.rounded.ExpandLess
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Security
import androidx.compose.material.icons.rounded.Speed
import androidx.compose.material.icons.rounded.SportsEsports
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import ir.pnifox.core.model.ActiveSectionEngine
import ir.pnifox.core.model.AppLanguage
import ir.pnifox.core.model.GamingMode
import ir.pnifox.core.model.IranPresetsCatalog
import ir.pnifox.core.model.PnifoxScenarioCatalog
import ir.pnifox.core.model.ProtocolExplainerCatalog
import ir.pnifox.core.model.ScenarioGoalId
import ir.pnifox.core.model.TunnelState
import ir.pnifox.desktop.model.WindowsTunnelMode
import ir.pnifox.desktop.ui.DesktopBadgePill
import ir.pnifox.desktop.ui.DesktopCard
import ir.pnifox.desktop.ui.DesktopConnectButton
import ir.pnifox.desktop.ui.DesktopFoxMascot
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

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        // 1. Hero + Connect + Gaming Mode Status
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
                        Spacer(Modifier.height(10.dp))
                        val modeDesc = if (isFa) gaming.mode.subtitleFa else gaming.mode.subtitleEn
                        Text(modeDesc, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Spacer(Modifier.width(16.dp))
                    DesktopConnectButton(
                        isConnected = isConnected,
                        isConnecting = isConnecting,
                        onClick = { holder.toggleConnect(ActiveSectionEngine.GAMING) },
                    )
                }

                // Helpful Windows PC Game hint: .exe games need TUN Mode (or Local Desync), while browsers/launchers can start with System Proxy
                if (tunMode == WindowsTunnelMode.SYSTEM_PROXY) {
                    Spacer(Modifier.height(10.dp))
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            Icon(Icons.Rounded.SportsEsports, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
                            Text(
                                text = dtr(
                                    "Windows Gaming Tip: System Proxy works instantly without Admin for browsers/Steam login; turn on TUN Mode to tunnel in-game UDP/TCP (.exe) packets too.",
                                    "نکته مهم بازی روی ویندوز: System Proxy بدون ادمین فورا کار می‌کند، اما برای عبور پکت‌های خودِ بازی (.exe) مثل وارزون و ولورانت، پیشنهاد می‌شود TUN Mode را روشن کنید.",
                                ),
                                style = MaterialTheme.typography.bodySmall,
                                modifier = Modifier.weight(1f),
                            )
                            OutlinedButton(
                                onClick = { holder.enableTunAndReconnect(ActiveSectionEngine.GAMING) },
                                shape = RoundedCornerShape(8.dp),
                            ) {
                                Text(dtr("Enable TUN Mode", "روشن کردن TUN Mode"), style = MaterialTheme.typography.labelSmall)
                            }
                        }
                    }
                }
            }
        }

        // 2. Choose Gaming Engine / Method with Plain Explanations
        item {
            DesktopCard {
                Text(dtr("Choose Gaming Connection Method", "انتخاب روش اتصال گیمینگ"), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(8.dp))
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    GamingMode.entries.forEach { gm ->
                        val selected = gaming.mode == gm
                        Surface(
                            onClick = { holder.updateGaming { it.copy(mode = gm) } },
                            shape = RoundedCornerShape(12.dp),
                            color = if (selected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.7f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                            border = BorderStroke(
                                width = if (selected) 1.5.dp else 1.dp,
                                color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant,
                            ),
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(if (isFa) gm.titleFa else gm.titleEn, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodyMedium)
                                    Spacer(Modifier.height(2.dp))
                                    Text(if (isFa) gm.subtitleFa else gm.subtitleEn, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                                if (selected) {
                                    DesktopBadgePill(
                                        text = dtr("Selected", "انتخاب‌شده"),
                                        containerColor = MaterialTheme.colorScheme.primary,
                                        contentColor = MaterialTheme.colorScheme.onPrimary,
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        // 3. Anti-Sanction Gaming DNS Presets + Zero-ICMP Live Ping
        item {
            val gamePresets = remember {
                IranPresetsCatalog.dnsPresets.filter { it.badge == "Game" || it.id in setOf("cf_udp_1", "cf_pdoq", "quad9_unfiltered") }
            }
            DesktopCard {
                Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        dtr("Anti-Sanction Gaming DNS & Edge Ping (Zero-ICMP)", "DNS ضدتحریم گیم و تست پینگ واقعی (بدون ICMP)"),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.weight(1f),
                    )
                    OutlinedButton(
                        onClick = { holder.testGamingPings() },
                        enabled = !state.isTestingGamingPings,
                        shape = RoundedCornerShape(10.dp),
                    ) {
                        if (state.isTestingGamingPings) CircularProgressIndicator(modifier = Modifier.size(14.dp), strokeWidth = 2.dp)
                        else Icon(Icons.Rounded.Speed, null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(dtr("Ping Game DNS", "تست پینگ DNS گیم"))
                    }
                }
                Spacer(Modifier.height(8.dp))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    gamePresets.forEach { preset ->
                        val ip = preset.address.substringBefore(':')
                        val ping = state.gamingPingResults[ip]
                        val pingLabel = if (ping != null) " • ${ping}ms" else ""
                        FilterChip(
                            selected = gaming.selectedDnsIp.substringBefore(':') == ip,
                            onClick = {
                                holder.updateGaming {
                                    it.copy(selectedDnsIp = ip)
                                }
                            },
                            label = { Text("${if (isFa) preset.titleFa else preset.titleEn}$pingLabel") },
                        )
                    }
                }
            }
        }

        // 4. Local DPI Desync & Low-Latency Tweaks
        item {
            DesktopCard {
                Text(dtr("Gaming & Local DPI Desync Tuning", "تنظیمات ضدDPI محلی و کاهش پینگ"), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(8.dp))
                if (gaming.mode == GamingMode.LOCAL_DPI_DESYNC) {
                    Text(
                        dtr("TLS ClientHello Fragment Chunk: ${gaming.desyncChunkLen} bytes (split before SNI + anti-poison DoH)", "اندازه تکه‌سازی TLS ClientHello: ${gaming.desyncChunkLen} بایت (شکستن SNI + DoH ضدجعل)"),
                        style = MaterialTheme.typography.bodySmall,
                    )
                    Slider(
                        value = gaming.desyncChunkLen.toFloat(),
                        onValueChange = { v -> holder.updateGaming { it.copy(desyncChunkLen = v.toInt().coerceIn(2, 16)) } },
                        valueRange = 2f..16f,
                        steps = 13,
                    )
                    Spacer(Modifier.height(4.dp))
                }
                DesktopSwitchRow(
                    title = dtr("Direct Bypass for Domestic Iranian Game Servers", "عبور مستقیم سرورهای گیم داخلی ایران"),
                    subtitle = dtr("Keep Iran lobbies at native ISP ping while tunneling foreign routes.", "برای لابی‌های داخلی یا دانلودهای ایران مستقیما متصل شود."),
                    checked = gaming.bypassIranGameTraffic,
                    onCheckedChange = { c -> holder.updateGaming { it.copy(bypassIranGameTraffic = c) } },
                )
                DesktopSwitchRow(
                    title = dtr("Enable QUIC v2 & 0-RTT (AETHER UDP)", "فعال‌سازی QUIC v2 در تونل سریع گیمینگ"),
                    subtitle = dtr("Reduces handshake jitter for HTTP/3 MASQUE gaming routes.", "کاهش نوسان و پینگ در تانل UDP/MASQUE."),
                    checked = gaming.preferQuicV2H3,
                    onCheckedChange = { c -> holder.updateGaming { it.copy(preferQuicV2H3 = c) } },
                )
                DesktopSwitchRow(
                    title = dtr("Block UDP/443 QUIC for DPI Stability", "بستن QUIC پورت ۴۴۳ در ضدDPI محلی"),
                    subtitle = dtr("Forces browsers to TLS/TCP where local SNI fragmentation catches DPI reliably.", "در حالت ضدDPI محلی مرورگرها روی TCP قرار می‌گیرند تا تکه‌سازی دقیق‌تر کار کند."),
                    checked = gaming.blockQuicUdp443,
                    onCheckedChange = { c -> holder.updateGaming { it.copy(blockQuicUdp443 = c) } },
                )
            }
        }

        // 5. Scenario Presets + Protocol Guide right inside Gaming/Presets tab too
        item {
            ScenarioPresetsDesktopCard(state = state, holder = holder)
        }
        item {
            ProtocolGuideDesktopCard()
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
    var expandedGuide by remember { mutableStateOf(true) }

    DesktopCard {
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    dtr("Goal-Based Presets (Select Your Use Case)", "حالت‌های آماده بر اساس نیاز شما (توضیح کامل)"),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    dtr("Pick what you want to do — pnifox tunes protocol, ECH, DNS & routing automatically.", "هدفتان را انتخاب کنید تا بهترین پروتکل، DNS و تنظیمات خودکار اعمال شود."),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Button(
                onClick = { holder.smartAutoConnect(state.persisted.selectedScenarioId) },
                enabled = !state.isSmartConnecting,
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0EA5E9), contentColor = Color.White),
                shape = RoundedCornerShape(10.dp),
            ) {
                if (state.isSmartConnecting) CircularProgressIndicator(modifier = Modifier.size(14.dp), strokeWidth = 2.dp, color = Color.White)
                else Icon(Icons.Rounded.AutoAwesome, null, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(6.dp))
                Text(
                    if (state.isSmartConnecting) dtr("Testing Best Route...", "تست هوشمند...")
                    else dtr("Smart Auto-Connect", "اتصال هوشمند خودکار"),
                    fontWeight = FontWeight.Bold,
                )
            }
            TextButton(onClick = { expandedGuide = !expandedGuide }) {
                Icon(if (expandedGuide) Icons.Rounded.ExpandLess else Icons.Rounded.ExpandMore, null)
            }
        }

        if (state.smartConnectStatus.isNotBlank()) {
            Spacer(Modifier.height(6.dp))
            Text(state.smartConnectStatus, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
        }

        if (expandedGuide) {
            Spacer(Modifier.height(10.dp))
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                PnifoxScenarioCatalog.scenarios.forEach { profile ->
                    val selected = state.persisted.selectedScenarioId == profile.id
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = if (selected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.6f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
                        border = BorderStroke(
                            width = if (selected) 1.5.dp else 1.dp,
                            color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant,
                        ),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Text(
                                        text = if (isFa) profile.id.titleFa else profile.id.titleEn,
                                        style = MaterialTheme.typography.titleSmall,
                                        fontWeight = FontWeight.Bold,
                                    )
                                    DesktopBadgePill(
                                        text = if (isFa) profile.badgeFa else profile.badgeEn,
                                        containerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.16f),
                                        contentColor = MaterialTheme.colorScheme.primary,
                                    )
                                }
                                Text(
                                    text = if (isFa) profile.subtitleFa else profile.subtitleEn,
                                    style = MaterialTheme.typography.bodySmall,
                                    fontWeight = FontWeight.SemiBold,
                                )
                                Text(
                                    text = if (isFa) profile.explanationFa else profile.explanationEn,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                                Text(
                                    text = if (isFa) "نکته: ${profile.tradeOffFa}" else "Note: ${profile.tradeOffEn}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.secondary,
                                )
                            }
                            Column(verticalArrangement = Arrangement.spacedBy(6.dp), horizontalAlignment = Alignment.End) {
                                Button(
                                    onClick = { holder.applyScenario(profile, autoConnect = true) },
                                    shape = RoundedCornerShape(8.dp),
                                ) {
                                    Icon(Icons.Rounded.Bolt, null, modifier = Modifier.size(15.dp))
                                    Spacer(Modifier.width(4.dp))
                                    Text(dtr("Apply & Connect", "اعمال و اتصال"), style = MaterialTheme.typography.labelSmall)
                                }
                                OutlinedButton(
                                    onClick = { holder.applyScenario(profile, autoConnect = false) },
                                    shape = RoundedCornerShape(8.dp),
                                ) {
                                    Text(dtr("Select Only", "فقط انتخاب"), style = MaterialTheme.typography.labelSmall)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun ProtocolGuideDesktopCard() {
    val isFa = LocalDesktopLanguage.current == AppLanguage.PERSIAN
    var expanded by remember { mutableStateOf(false) }

    DesktopCard {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(Icons.Rounded.Info, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
                Text(
                    dtr("Connection Methods & Protocols Explained", "توضیح روش‌های اتصال و پروتکل‌ها"),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                )
            }
            TextButton(onClick = { expanded = !expanded }) {
                Text(if (expanded) dtr("Hide", "بستن") else dtr("Compare Methods", "مقایسه روش‌ها"))
            }
        }

        if (expanded) {
            Spacer(Modifier.height(8.dp))
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                ProtocolExplainerCatalog.items.forEach { item ->
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Text(if (isFa) item.nameFa else item.nameEn, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodyMedium)
                                DesktopBadgePill(item.badge, MaterialTheme.colorScheme.primary.copy(alpha = 0.14f), MaterialTheme.colorScheme.primary)
                            }
                            Text(if (isFa) item.categoryFa else item.categoryEn, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                            Text(if (isFa) item.summaryFa else item.summaryEn, style = MaterialTheme.typography.bodySmall)
                            Text(
                                text = if (isFa) "بهترین کاربرد: ${item.bestForFa}" else "Best for: ${item.bestForEn}",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.secondary,
                            )
                        }
                    }
                }
            }
        }
    }
}
