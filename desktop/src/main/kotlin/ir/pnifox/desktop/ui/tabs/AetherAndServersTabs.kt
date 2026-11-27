package ir.pnifox.desktop.ui.tabs

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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Clear
import androidx.compose.material.icons.rounded.Cloud
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Hub
import androidx.compose.material.icons.rounded.Key
import androidx.compose.material.icons.rounded.Link
import androidx.compose.material.icons.rounded.Public
import androidx.compose.material.icons.rounded.PushPin
import androidx.compose.material.icons.rounded.Radar
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Router
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material.icons.rounded.Speed
import androidx.compose.material.icons.rounded.SwapHoriz
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import ir.pnifox.core.model.ActiveSectionEngine
import ir.pnifox.core.model.AetherIdentityTarget
import ir.pnifox.core.model.AetherNoizeProfile
import ir.pnifox.core.model.AetherProtocol
import ir.pnifox.core.model.AetherScanMode
import ir.pnifox.core.model.AppLanguage
import ir.pnifox.core.model.ExitNode
import ir.pnifox.core.model.IpVersionMode
import ir.pnifox.core.model.MasqueTransport
import ir.pnifox.core.model.OneTapPreset
import ir.pnifox.core.model.PnifoxRegionsCatalog
import ir.pnifox.core.model.ProxyProfile
import ir.pnifox.core.model.PsiphonCdnCatalog
import ir.pnifox.core.model.PsiphonChainMode
import ir.pnifox.core.model.TunnelState
import ir.pnifox.core.network.ShareLinkParser
import ir.pnifox.desktop.model.DesktopTab
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

private enum class DesktopExitChainOption(
    val labelEn: String,
    val labelFa: String,
) {
    DIRECT_WARP("WARP Direct (Fast)", "مستقیم WARP (سریع)"),
    CHAIN_PSIPHON("+ Psiphon Exit", "+ زنجیره سایفون"),
    REVERSE_PSIPHON("Psiphon -> WARP", "سایفون به WARP"),
    CHAIN_TOR("+ Tor Exit", "+ زنجیره Tor"),
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AetherDesktopTab(
    state: DesktopUiState,
    holder: DesktopStateHolder,
) {
    val isFa = LocalDesktopLanguage.current == AppLanguage.PERSIAN
    val app = state.persisted.appSettings
    val aether = state.persisted.aetherSettings
    val isConnected = state.tunnelState is TunnelState.Connected && state.tunnelState.section == ActiveSectionEngine.AETHER
    val isConnecting = state.tunnelState is TunnelState.Connecting && state.tunnelState.section == ActiveSectionEngine.AETHER

    val isAkamaiStandalone = aether.psiphonEnabled &&
        (aether.psiphonMode == PsiphonChainMode.STANDALONE_CDN || app.activePreset == OneTapPreset.PSIPHON_AKAMAI_CDN)

    val activeChain = when {
        aether.torEnabled || app.exitNode == ExitNode.TOR -> DesktopExitChainOption.CHAIN_TOR
        aether.psiphonEnabled && aether.psiphonMode == PsiphonChainMode.PSIPHON_TO_WARP -> DesktopExitChainOption.REVERSE_PSIPHON
        aether.psiphonEnabled -> DesktopExitChainOption.CHAIN_PSIPHON
        else -> DesktopExitChainOption.DIRECT_WARP
    }
    val activeRegion = PnifoxRegionsCatalog.findByCode(aether.psiphonExitCountry)
    val hasAppliedIp = aether.endpointIp.isNotBlank() || aether.psiphonCdnFrontingIps.isNotBlank()

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        // 1. Hero Card
        item {
            val showTunHint = state.persisted.windowsTunnelMode == WindowsTunnelMode.SYSTEM_PROXY && !state.persisted.tunPromptDismissed
            DesktopCard {
                Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Column(modifier = Modifier.weight(1f)) {
                        DesktopFoxMascot(
                            tunnelState = state.tunnelState,
                            section = ActiveSectionEngine.AETHER,
                            onFallbackSystemProxy = { holder.connectViaSystemProxyFallback(ActiveSectionEngine.AETHER) },
                            onRestartAdmin = { holder.relaunchAsAdmin() },
                            showTunSuggestion = showTunHint,
                            onEnableTunMode = { holder.enableTunAndReconnect(ActiveSectionEngine.AETHER) },
                            onDismissTunSuggestion = { holder.dismissTunSuggestion() },
                        )
                        Spacer(Modifier.height(8.dp))
                        val routeSummary = if (isAkamaiStandalone) {
                            val customCnt = aether.psiphonCdnFrontingIps.split(',', ';', '\n', ' ').count { it.isNotBlank() }
                            if (customCnt > 0) dtr("Akamai CDN • $customCnt custom edge IP(s)", "اکامای CDN • $customCnt آی‌پی دستی/اسکن‌شده")
                            else dtr("Akamai CDN • Auto edge selection", "اکامای CDN • انتخاب خودکار سرور")
                        } else if (aether.scanForEndpoint || aether.endpointIp.isBlank()) {
                            dtr("${aether.protocol.label} (${aether.masqueTransport.label}) • Auto-Scan", "${aether.protocol.label} • اسکن خودکار اندپوینت")
                        } else {
                            dtr("Applied IP: ${aether.endpointIp}:${aether.endpointPort}", "آی‌پی اعمال‌شده: ${aether.endpointIp}:${aether.endpointPort}")
                        }
                        Text(
                            text = routeSummary,
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.primary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    Spacer(Modifier.width(16.dp))
                    DesktopConnectButton(
                        isConnected = isConnected,
                        isConnecting = isConnecting,
                        onClick = { holder.toggleConnect(ActiveSectionEngine.AETHER) },
                    )
                }

                Spacer(Modifier.height(10.dp))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (!isAkamaiStandalone) {
                        Button(
                            onClick = { holder.registerAetherKeys(AetherIdentityTarget.ALL) },
                            enabled = !state.isRegisteringWarp,
                            shape = RoundedCornerShape(10.dp),
                        ) {
                            if (state.isRegisteringWarp) CircularProgressIndicator(modifier = Modifier.size(15.dp), strokeWidth = 2.dp)
                            else Icon(Icons.Rounded.Key, null, modifier = Modifier.size(15.dp))
                            Spacer(Modifier.width(5.dp))
                            Text(dtr("Generate WARP Keys", "ساخت کلید WARP"), maxLines = 1)
                        }
                        val rescanBannerMsg = dtr("Scanning WARP subnets...", "در حال اسکن رنج‌های WARP...")
                        OutlinedButton(
                            onClick = {
                                holder.updateAether { it.copy(endpointIp = "", scanForEndpoint = true, scanMode = AetherScanMode.TURBO) }
                                holder.setBanner(rescanBannerMsg)
                                holder.toggleConnect(ActiveSectionEngine.AETHER)
                            },
                            shape = RoundedCornerShape(10.dp),
                        ) {
                            Icon(Icons.Rounded.Search, null, modifier = Modifier.size(15.dp))
                            Spacer(Modifier.width(5.dp))
                            Text(dtr("Re-scan IP", "اسکن مجدد"), maxLines = 1)
                        }
                    }
                    if (hasAppliedIp) {
                        OutlinedButton(
                            onClick = { holder.clearAllAppliedIps() },
                            shape = RoundedCornerShape(10.dp),
                        ) {
                            Icon(Icons.Rounded.Clear, null, modifier = Modifier.size(15.dp))
                            Spacer(Modifier.width(5.dp))
                            Text(dtr("Clear Applied IPs", "پاک کردن IPها"), maxLines = 1)
                        }
                    }
                    OutlinedButton(
                        onClick = { holder.clearAetherCache() },
                        shape = RoundedCornerShape(10.dp),
                    ) {
                        Icon(Icons.Rounded.DeleteOutline, null, modifier = Modifier.size(15.dp))
                        Spacer(Modifier.width(5.dp))
                        Text(dtr("Clear Cache", "پاکسازی کش"), maxLines = 1)
                    }
                }
            }
        }

        // 2. Compact Use Case Selector + Info Popup
        item {
            ScenarioPresetsDesktopCard(state = state, holder = holder)
        }

        // 3. Cleanly Separated Connection Engine Card (WARP/AETHER vs Akamai Standalone)
        item {
            DesktopCard {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text(
                        dtr("Connection Core", "نوع هسته و تنظیمات"),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                    )
                    DesktopInfoPopupButton(
                        title = dtr("AETHER & Akamai Guide", "توضیحات پروتکل‌ها و Akamai"),
                        body = dtr(
                            "• AETHER (WARP): Pick Protocol (MASQUE/WireGuard) + Transport (HTTP/2 or HTTP/3) independently, then optionally chain Psiphon & exit country on top.\n• Akamai / Psiphon CDN: Runs standalone without WARP keys or country lock so reachable Akamai CDN nodes connect cleanly.",
                            "• حالت AETHER (WARP): پروتکل (MASQUE یا WireGuard) و نوع انتقال (HTTP/2 یا HTTP/3) را جداگانه انتخاب کنید و در صورت نیاز سایفون و کشور خروجی را روی آن فعال کنید.\n• حالت Akamai CDN (مستقل): بدون نیاز به WARP و بدون قفل کشور، مستقیم از طریق CDNهای اکامای متصل می‌شود.",
                        ),
                    )
                }
                Spacer(Modifier.height(8.dp))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    FilterChip(
                        selected = !isAkamaiStandalone,
                        onClick = {
                            holder.updateApp { it.copy(activePreset = OneTapPreset.WARP_FAST_MASQUE_H2, exitNode = ExitNode.DEFAULT) }
                            holder.updateAether {
                                it.copy(
                                    psiphonEnabled = false,
                                    psiphonMode = PsiphonChainMode.WARP_TO_PSIPHON,
                                    torEnabled = false,
                                )
                            }
                        },
                        label = { Text(dtr("AETHER (WARP / MASQUE)", "AETHER (وارپ و زنجیره)"), maxLines = 1) },
                    )
                    FilterChip(
                        selected = isAkamaiStandalone,
                        onClick = {
                            holder.updateApp { it.copy(activePreset = OneTapPreset.PSIPHON_AKAMAI_CDN, exitNode = ExitNode.PSIPHON) }
                            holder.updateAether {
                                it.copy(
                                    psiphonEnabled = true,
                                    psiphonMode = PsiphonChainMode.STANDALONE_CDN,
                                    psiphonCdnSets = it.psiphonCdnSets.ifBlank { "psiphon-akamai" },
                                    torEnabled = false,
                                )
                            }
                        },
                        label = { Text(dtr("Akamai / Psiphon CDN", "اکامای / سایفون CDN"), maxLines = 1) },
                    )
                }

                Spacer(Modifier.height(10.dp))
                if (isAkamaiStandalone) {
                    // Clean Akamai CDN Fronting section — NO WARP/Country confusion
                    val setOptions = listOf("" to dtr("All Sets (Auto)", "همه پکیج‌ها (خودکار)")) + PsiphonCdnCatalog.availableCdnSets
                    val activeSet = setOptions.firstOrNull { it.first.equals(aether.psiphonCdnSets.trim(), ignoreCase = true) }
                        ?: setOptions.first()
                    FlowRow(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        DesktopDropdownButton(
                            label = "CDN Set",
                            selectedText = activeSet.second,
                            options = setOptions,
                            selectedItem = activeSet,
                            optionLabel = { it.second },
                            onSelect = { pair -> holder.updateAether { it.copy(psiphonCdnSets = pair.first) } },
                            icon = Icons.Rounded.Cloud,
                        )
                        OutlinedButton(
                            onClick = { holder.testAndSortPsiphonCdnEdges() },
                            enabled = !state.isScanning && aether.psiphonCdnFrontingIps.isNotBlank(),
                            shape = RoundedCornerShape(10.dp),
                        ) {
                            Text(if (state.isScanning) dtr("Testing...", "تست...") else dtr("Test Edge IPs", "تست IPها"), style = MaterialTheme.typography.labelSmall, maxLines = 1)
                        }
                        OutlinedButton(
                            onClick = { holder.selectTab(DesktopTab.SCANNER) },
                            shape = RoundedCornerShape(10.dp),
                        ) {
                            Text(dtr("Open Scanner", "اسکنر Akamai"), style = MaterialTheme.typography.labelSmall, maxLines = 1)
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(
                            value = aether.psiphonCdnFrontingIps,
                            onValueChange = { v -> holder.updateAether { it.copy(psiphonCdnFrontingIps = v) } },
                            label = { Text(dtr("CDN Edge IPs / CIDRs", "آی‌پی / رنج‌های Akamai و CDN")) },
                            trailingIcon = if (aether.psiphonCdnFrontingIps.isNotBlank()) {
                                {
                                    IconButton(onClick = { holder.clearAppliedCdnIps() }) {
                                        Icon(Icons.Rounded.Clear, contentDescription = "Clear CDN IPs", modifier = Modifier.size(16.dp))
                                    }
                                }
                            } else null,
                            minLines = 2,
                            maxLines = 3,
                            modifier = Modifier.weight(1.2f),
                        )
                        OutlinedTextField(
                            value = aether.psiphonCdnSniHostnames,
                            onValueChange = { v -> holder.updateAether { it.copy(psiphonCdnSniHostnames = v) } },
                            label = { Text(dtr("CDN SNI Hostnames (optional)", "SNIهای فرانتینگ (اختیاری)")) },
                            minLines = 2,
                            maxLines = 3,
                            modifier = Modifier.weight(1f),
                        )
                    }
                } else {
                    // Clean WARP/AETHER Dropdowns: Protocol + Transport + Exit Chain + Exit Country (when Psiphon active)
                    FlowRow(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        DesktopDropdownButton(
                            label = dtr("Protocol", "پروتکل"),
                            selectedText = aether.protocol.label,
                            options = AetherProtocol.entries,
                            selectedItem = aether.protocol,
                            optionLabel = { it.label },
                            onSelect = { proto ->
                                val nextPreset = when (proto) {
                                    AetherProtocol.WIREGUARD_OVER_MASQUE -> OneTapPreset.WG_OVER_MASQUE
                                    AetherProtocol.MASQUE_IN_MASQUE -> OneTapPreset.MASQUE_IN_MASQUE
                                    else -> OneTapPreset.WARP_FAST_MASQUE_H2
                                }
                                holder.updateApp { it.copy(activePreset = if (aether.psiphonEnabled) OneTapPreset.WARP_TO_PSIPHON else nextPreset) }
                                holder.updateAether { it.copy(protocol = proto) }
                            },
                            icon = Icons.Rounded.Router,
                        )
                        if (aether.protocol == AetherProtocol.MASQUE ||
                            aether.protocol == AetherProtocol.MASQUE_IN_MASQUE ||
                            aether.protocol == AetherProtocol.WIREGUARD_OVER_MASQUE
                        ) {
                            DesktopDropdownButton(
                                label = dtr("Transport", "انتقال"),
                                selectedText = aether.masqueTransport.label,
                                options = MasqueTransport.entries,
                                selectedItem = aether.masqueTransport,
                                optionLabel = { it.label },
                                onSelect = { tr -> holder.updateAether { it.copy(masqueTransport = tr) } },
                                icon = Icons.Rounded.SwapHoriz,
                            )
                        }
                        DesktopDropdownButton(
                            label = dtr("Chain", "زنجیره"),
                            selectedText = if (isFa) activeChain.labelFa else activeChain.labelEn,
                            options = DesktopExitChainOption.entries,
                            selectedItem = activeChain,
                            optionLabel = { ch -> if (isFa) ch.labelFa else ch.labelEn },
                            onSelect = { opt ->
                                when (opt) {
                                    DesktopExitChainOption.DIRECT_WARP -> {
                                        holder.updateApp { it.copy(exitNode = ExitNode.DEFAULT, activePreset = OneTapPreset.WARP_FAST_MASQUE_H2) }
                                        holder.updateAether { it.copy(psiphonEnabled = false, torEnabled = false) }
                                    }
                                    DesktopExitChainOption.CHAIN_PSIPHON -> {
                                        holder.updateApp { it.copy(exitNode = ExitNode.PSIPHON, activePreset = OneTapPreset.WARP_TO_PSIPHON) }
                                        holder.updateAether { it.copy(psiphonEnabled = true, psiphonMode = PsiphonChainMode.WARP_TO_PSIPHON, torEnabled = false) }
                                    }
                                    DesktopExitChainOption.REVERSE_PSIPHON -> {
                                        holder.updateApp { it.copy(exitNode = ExitNode.PSIPHON, activePreset = OneTapPreset.WARP_TO_PSIPHON) }
                                        holder.updateAether { it.copy(psiphonEnabled = true, psiphonMode = PsiphonChainMode.PSIPHON_TO_WARP, torEnabled = false) }
                                    }
                                    DesktopExitChainOption.CHAIN_TOR -> {
                                        holder.updateApp { it.copy(exitNode = ExitNode.TOR, activePreset = OneTapPreset.WARP_TO_TOR) }
                                        holder.updateAether { it.copy(psiphonEnabled = false, torEnabled = true) }
                                    }
                                }
                            },
                            icon = Icons.Rounded.Hub,
                        )
                        if (activeChain == DesktopExitChainOption.CHAIN_PSIPHON || activeChain == DesktopExitChainOption.REVERSE_PSIPHON) {
                            DesktopDropdownButton(
                                label = dtr("Country", "کشور"),
                                selectedText = "${activeRegion.flag} ${if (isFa) activeRegion.nameFa else activeRegion.nameEn}",
                                options = PnifoxRegionsCatalog.regions.take(18),
                                selectedItem = activeRegion,
                                optionLabel = { r -> "${r.flag} ${if (isFa) r.nameFa else r.nameEn}" },
                                onSelect = { reg ->
                                    holder.updateApp { it.copy(regionCode = reg.code) }
                                    holder.updateAether { it.copy(psiphonExitCountry = reg.code) }
                                },
                                icon = Icons.Rounded.Public,
                            )
                        }
                    }
                }
            }
        }

        // 4. Compact Endpoint & Anti-DPI Card (only when WARP/AETHER is active)
        if (!isAkamaiStandalone) {
            item {
                DesktopCard {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Text(
                            dtr("Endpoint & Anti-DPI", "اندپوینت و ضد فیلتر"),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.weight(1f),
                        )
                        DesktopInfoPopupButton(
                            title = dtr("Anti-DPI & ECH Help", "توضیحات تنظیمات ضد فیلتر"),
                            body = dtr(
                                "• Auto-Scan probes diverse WARP subnets instead of locking onto one IP.\n• Fragment splits TLS ClientHello (turn OFF if unexpected EOF appears).\n• ECH is OFF by default (only enable if your network supports Cloudflare ECH).",
                                "• اسکن خودکار هر بار رنج‌های متنوع WARP را تست می‌کند.\n• Fragment بسته‌های TLS را خرد می‌کند (در صورت خطای EOF خاموش کنید).\n• ECH پیش‌فرض خاموش است (فقط در صورت نیاز روشن کنید).",
                            ),
                        )
                    }
                    Spacer(Modifier.height(8.dp))
                    FlowRow(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        DesktopDropdownButton(
                            label = dtr("Scan", "سبک اسکن"),
                            selectedText = aether.scanMode.label,
                            options = AetherScanMode.entries,
                            selectedItem = aether.scanMode,
                            optionLabel = { it.label },
                            onSelect = { sm -> holder.updateAether { it.copy(scanMode = sm) } },
                            icon = Icons.Rounded.Radar,
                        )
                        DesktopDropdownButton(
                            label = "IP",
                            selectedText = aether.ipVersion.label,
                            options = IpVersionMode.entries,
                            selectedItem = aether.ipVersion,
                            optionLabel = { it.label },
                            onSelect = { iv -> holder.updateAether { it.copy(ipVersion = iv) } },
                        )
                        DesktopDropdownButton(
                            label = "Noize",
                            selectedText = aether.noizeProfile.label,
                            options = AetherNoizeProfile.entries,
                            selectedItem = aether.noizeProfile,
                            optionLabel = { it.label },
                            onSelect = { nz -> holder.updateAether { it.copy(noizeProfile = nz) } },
                            icon = Icons.Rounded.Shield,
                        )
                    }
                    Spacer(Modifier.height(8.dp))
                    DesktopSwitchRow(
                        title = dtr("Auto-Scan Diverse Endpoint", "اسکن خودکار و متنوع اندپوینت"),
                        checked = aether.scanForEndpoint,
                        onCheckedChange = { c -> holder.updateAether { it.copy(scanForEndpoint = c) } },
                    )
                    if (!aether.scanForEndpoint || aether.endpointIp.isNotBlank()) {
                        Spacer(Modifier.height(6.dp))
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedTextField(
                                value = aether.endpointIp,
                                onValueChange = { v -> holder.updateAether { it.copy(endpointIp = v, scanForEndpoint = v.isBlank()) } },
                                label = { Text(dtr("Saved Endpoint IP", "آی‌پی اندپوینت")) },
                                trailingIcon = if (aether.endpointIp.isNotBlank()) {
                                    {
                                        IconButton(onClick = { holder.clearAppliedEndpointIp() }) {
                                            Icon(Icons.Rounded.Clear, contentDescription = "Clear IP", modifier = Modifier.size(16.dp))
                                        }
                                    }
                                } else null,
                                singleLine = true,
                                modifier = Modifier.weight(1.5f),
                            )
                            OutlinedTextField(
                                value = aether.endpointPort.toString(),
                                onValueChange = { v -> v.toIntOrNull()?.let { p -> holder.updateAether { it.copy(endpointPort = p) } } },
                                label = { Text(dtr("Port", "پورت")) },
                                singleLine = true,
                                modifier = Modifier.weight(0.7f),
                            )
                        }
                    }
                    DesktopSwitchRow(
                        title = dtr("Fragment TLS ClientHello", "تکه‌تکه کردن TLS (Fragment)"),
                        checked = aether.fragmentEnabled,
                        onCheckedChange = { c -> holder.updateAether { it.copy(fragmentEnabled = c) } },
                    )
                    DesktopSwitchRow(
                        title = dtr("Enable ECH (Default Off)", "فعال‌سازی ECH (پیش‌فرض خاموش)"),
                        checked = aether.echEnabled,
                        onCheckedChange = { c -> holder.updateAether { it.copy(echEnabled = c) } },
                    )
                    if (aether.echEnabled) {
                        DesktopSwitchRow(
                            title = dtr("Smart ECH Bootstrap (DoH/DoT/53)", "دریافت هوشمند ECH"),
                            checked = aether.smartEchBootstrap,
                            onCheckedChange = { c -> holder.updateAether { it.copy(smartEchBootstrap = c) } },
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            OutlinedButton(
                                onClick = { holder.fetchEchConfigNow() },
                                enabled = !state.isFetchingEch,
                                shape = RoundedCornerShape(10.dp),
                            ) {
                                if (state.isFetchingEch) CircularProgressIndicator(modifier = Modifier.size(14.dp), strokeWidth = 2.dp)
                                Spacer(Modifier.width(6.dp))
                                Text(dtr("Fetch ECH Now", "دریافت ECH"), maxLines = 1)
                            }
                            if (aether.echBase64Override.isNotBlank()) {
                                DesktopBadgePill(
                                    text = dtr("ECH Ready ✓", "ECH آماده ✓"),
                                    containerColor = Color(0xFF10B981).copy(alpha = 0.16f),
                                    contentColor = Color(0xFF10B981),
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ServersDesktopTab(
    state: DesktopUiState,
    holder: DesktopStateHolder,
) {
    val clipboard = LocalClipboardManager.current
    val persisted = state.persisted
    val app = persisted.appSettings
    val activeProfile = persisted.profiles.firstOrNull { it.id == app.activeProfileId }
    val isConnected = state.tunnelState is TunnelState.Connected && state.tunnelState.section == ActiveSectionEngine.SERVER
    val isConnecting = state.tunnelState is TunnelState.Connecting && state.tunnelState.section == ActiveSectionEngine.SERVER

    var showImportDialog by remember { mutableStateOf(false) }
    var showSubDialog by remember { mutableStateOf(false) }
    var editingProfile by remember { mutableStateOf<ProxyProfile?>(null) }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            val showTunHint = state.persisted.windowsTunnelMode == WindowsTunnelMode.SYSTEM_PROXY && !state.persisted.tunPromptDismissed
            DesktopCard {
                Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Column(modifier = Modifier.weight(1f)) {
                        DesktopFoxMascot(
                            tunnelState = state.tunnelState,
                            section = ActiveSectionEngine.SERVER,
                            onFallbackSystemProxy = { holder.connectViaSystemProxyFallback(ActiveSectionEngine.SERVER) },
                            onRestartAdmin = { holder.relaunchAsAdmin() },
                            showTunSuggestion = showTunHint,
                            onEnableTunMode = { holder.enableTunAndReconnect(ActiveSectionEngine.SERVER) },
                            onDismissTunSuggestion = { holder.dismissTunSuggestion() },
                        )
                        Spacer(Modifier.height(8.dp))
                        Text(
                            text = activeProfile?.let { "${it.name} (${it.address}:${it.port})" }
                                ?: dtr("No server selected", "سروری انتخاب نشده است"),
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    Spacer(Modifier.width(16.dp))
                    DesktopConnectButton(
                        isConnected = isConnected,
                        isConnecting = isConnecting,
                        onClick = { holder.toggleConnect(ActiveSectionEngine.SERVER) },
                    )
                }
                Spacer(Modifier.height(10.dp))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = { showImportDialog = true }, shape = RoundedCornerShape(10.dp)) {
                        Icon(Icons.Rounded.Add, null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(5.dp))
                        Text(dtr("Import Config", "ورود کانفیگ"), maxLines = 1)
                    }
                    OutlinedButton(onClick = { showSubDialog = true }, shape = RoundedCornerShape(10.dp)) {
                        Icon(Icons.Rounded.Link, null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(5.dp))
                        Text(dtr("Add Sub", "افزودن ساب"), maxLines = 1)
                    }
                    OutlinedButton(onClick = { holder.refreshAllSubscriptions() }, enabled = !state.isSyncingSubs, shape = RoundedCornerShape(10.dp)) {
                        if (state.isSyncingSubs) CircularProgressIndicator(modifier = Modifier.size(15.dp), strokeWidth = 2.dp)
                        else Icon(Icons.Rounded.Refresh, null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(5.dp))
                        Text(if (state.isSyncingSubs) dtr("Syncing...", "دریافت...") else dtr("Sync Subs", "بروزرسانی ساب"), maxLines = 1)
                    }
                    OutlinedButton(onClick = { holder.pingAllProfiles() }, enabled = !state.isTestingAllServers, shape = RoundedCornerShape(10.dp)) {
                        Icon(Icons.Rounded.Speed, null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(5.dp))
                        Text(if (state.isTestingAllServers) dtr("Probing...", "تست...") else dtr("Test All", "تست پینگ"), maxLines = 1)
                    }
                }
            }
        }

        if (persisted.subscriptions.isNotEmpty()) {
            item {
                DesktopCard(padding = 10.dp) {
                    Text(dtr("Subscriptions (${persisted.subscriptions.size})", "لینک‌های اشتراک (${persisted.subscriptions.size})"), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(6.dp))
                    persisted.subscriptions.forEach { sub ->
                        Row(modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp), verticalAlignment = Alignment.CenterVertically) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(sub.name, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Text("${sub.profileCount} servers • ${sub.url}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }
                            IconButton(onClick = { holder.syncSingleSubscription(sub) }, enabled = !state.isSyncingSubs, modifier = Modifier.size(30.dp)) {
                                Icon(Icons.Rounded.Refresh, contentDescription = "Sync Subscription", modifier = Modifier.size(16.dp))
                            }
                            IconButton(onClick = { holder.deleteSubscription(sub.id) }, modifier = Modifier.size(30.dp)) {
                                Icon(Icons.Rounded.DeleteOutline, contentDescription = "Delete Subscription", modifier = Modifier.size(16.dp))
                            }
                        }
                    }
                }
            }
        }

        if (persisted.profiles.isEmpty()) {
            item {
                DesktopCard {
                    Text(dtr("No server configs yet. Click 'Import Config' to add VLESS, Reality, VMess, Trojan, Hy2, WireGuard, or JSON.", "هنوز کانفیگی اضافه نشده است. روی «ورود کانفیگ» کلیک کنید."))
                }
            }
        } else {
            items(persisted.profiles, key = { it.id }) { profile ->
                val selected = profile.id == app.activeProfileId
                DesktopCard(
                    containerColor = if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface,
                    borderColor = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline.copy(alpha = 0.7f),
                    modifier = Modifier.clickable { holder.selectProfile(profile) },
                ) {
                    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        DesktopBadgePill(profile.protocol.displayName)
                        Spacer(Modifier.width(8.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(profile.name, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(
                                text = if (profile.spoofLocalhost40443) {
                                    "127.0.0.1:40443 <- ${profile.address}:${profile.port}"
                                } else {
                                    "${profile.address}:${profile.port} • ${profile.transport.uppercase()}"
                                },
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                        val pingLabel = when {
                            profile.lastPingMs == null -> if (selected) dtr("Active", "فعال") else ""
                            profile.lastPingMs!! < 0 -> dtr("Timeout", "تایم‌اوت")
                            else -> "${profile.lastPingMs} ms"
                        }
                        if (pingLabel.isNotBlank()) {
                            DesktopBadgePill(
                                text = pingLabel,
                                containerColor = if ((profile.lastPingMs ?: -1) > 0) Color(0xFF10B981).copy(alpha = 0.16f) else MaterialTheme.colorScheme.surfaceVariant,
                                contentColor = if ((profile.lastPingMs ?: -1) > 0) Color(0xFF10B981) else MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Spacer(Modifier.width(6.dp))
                        FilterChip(
                            selected = profile.spoofLocalhost40443,
                            onClick = { holder.toggleSpoof(profile) },
                            label = { Text("Spoof 40443", style = MaterialTheme.typography.labelSmall, maxLines = 1) },
                        )
                        IconButton(onClick = { holder.pingProfile(profile) }) { Icon(Icons.Rounded.Speed, "Real Ping") }
                        IconButton(onClick = { editingProfile = profile }) { Icon(Icons.Rounded.Edit, "Edit") }
                        IconButton(onClick = { holder.togglePin(profile) }) {
                            Icon(Icons.Rounded.PushPin, "Pin", tint = if (profile.isPinned) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        IconButton(onClick = { holder.deleteProfile(profile.id) }) { Icon(Icons.Rounded.DeleteOutline, "Delete") }
                    }
                }
            }
        }
    }

    if (showImportDialog) {
        var raw by remember { mutableStateOf(clipboard.getText()?.text.orEmpty()) }
        AlertDialog(
            onDismissRequest = { showImportDialog = false },
            title = { Text(dtr("Import Configs or Subscription", "ورود کانفیگ یا لینک اشتراک")) },
            text = {
                OutlinedTextField(
                    value = raw,
                    onValueChange = { raw = it },
                    label = { Text("vless://, vmess://, trojan://, hy2://, https://..., JSON") },
                    minLines = 5,
                    maxLines = 10,
                    modifier = Modifier.fillMaxWidth(),
                )
            },
            confirmButton = {
                Button(onClick = { holder.importServerConfigs(raw); showImportDialog = false }) { Text(dtr("Import", "ثبت")) }
            },
            dismissButton = { TextButton(onClick = { showImportDialog = false }) { Text(dtr("Cancel", "انصراف")) } },
        )
    }

    if (showSubDialog) {
        var name by remember { mutableStateOf("") }
        var url by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showSubDialog = false },
            title = { Text(dtr("Add Subscription URL", "افزودن لینک اشتراک")) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text(dtr("Name", "نام")) }, singleLine = true, modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(value = url, onValueChange = { url = it }, label = { Text("https://...") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                }
            },
            confirmButton = {
                Button(onClick = { holder.addSubscription(name, url); showSubDialog = false }) { Text(dtr("Save & Sync", "ذخیره")) }
            },
            dismissButton = { TextButton(onClick = { showSubDialog = false }) { Text(dtr("Cancel", "انصراف")) } },
        )
    }

    editingProfile?.let { target ->
        val isJson = remember(target.id, target.rawUri) { target.rawUri.trimStart().startsWith("{") }
        var useRaw by remember { mutableStateOf(false) }
        var name by remember(target.id) { mutableStateOf(target.name) }
        var address by remember(target.id) { mutableStateOf(target.address) }
        var portText by remember(target.id) { mutableStateOf(target.port.toString()) }
        var secret by remember(target.id) { mutableStateOf(target.secret) }
        var sni by remember(target.id) { mutableStateOf(target.sni) }
        var fakeSni by remember(target.id) { mutableStateOf(target.spoofFakeSni) }
        var transport by remember(target.id) { mutableStateOf(target.transport) }
        var path by remember(target.id) { mutableStateOf(target.pathOrServiceName) }
        var fp by remember(target.id) { mutableStateOf(target.tlsFingerprint) }
        var pubKey by remember(target.id) { mutableStateOf(target.realityPublicKey) }
        var shortId by remember(target.id) { mutableStateOf(target.realityShortId) }
        var fragment by remember(target.id) { mutableStateOf(target.fragmentEnabled) }
        var spoof by remember(target.id) { mutableStateOf(target.spoofLocalhost40443) }
        var rawJson by remember(target.id) { mutableStateOf(if (isJson) ShareLinkParser.formatPrettyJson(target.rawUri) else "") }
        var jsonErr by remember { mutableStateOf<String?>(null) }

        AlertDialog(
            onDismissRequest = { editingProfile = null },
            title = { Text(dtr("Edit Server & SNI Config", "ویرایش کانفیگ و SNI"), fontWeight = FontWeight.Bold) },
            text = {
                Column(modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (isJson) {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            FilterChip(selected = !useRaw, onClick = { useRaw = false }, label = { Text(dtr("Quick Fields", "ویرایش سریع")) })
                            FilterChip(selected = useRaw, onClick = { useRaw = true }, label = { Text("Raw JSON") })
                        }
                    }
                    if (isJson && useRaw) {
                        OutlinedTextField(
                            value = rawJson,
                            onValueChange = { rawJson = it; jsonErr = null },
                            label = { Text("Xray / MASQUE JSON") },
                            textStyle = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                            minLines = 10,
                            maxLines = 16,
                            modifier = Modifier.fillMaxWidth(),
                        )
                        jsonErr?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.labelSmall) }
                    } else {
                        OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text(dtr("Name", "نام")) }, singleLine = true, modifier = Modifier.fillMaxWidth())
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedTextField(value = address, onValueChange = { address = it }, label = { Text(dtr("Address", "آدرس")) }, singleLine = true, modifier = Modifier.weight(1.6f))
                            OutlinedTextField(value = portText, onValueChange = { portText = it.filter(Char::isDigit) }, label = { Text(dtr("Port", "پورت")) }, singleLine = true, modifier = Modifier.weight(0.9f))
                        }
                        OutlinedTextField(value = sni, onValueChange = { sni = it }, label = { Text("SNI") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                        if (spoof || !isJson) {
                            OutlinedTextField(value = fakeSni, onValueChange = { fakeSni = it }, label = { Text(dtr("SNI Spoof 40443 Fake SNI", "SNI جعلی برای 40443")) }, singleLine = true, modifier = Modifier.fillMaxWidth())
                        }
                        OutlinedTextField(value = secret, onValueChange = { secret = it }, label = { Text(dtr("UUID / Password", "کلید / رمز")) }, singleLine = true, modifier = Modifier.fillMaxWidth())
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedTextField(value = transport, onValueChange = { transport = it }, label = { Text(dtr("Transport", "انتقال")) }, singleLine = true, modifier = Modifier.weight(1f))
                            OutlinedTextField(value = fp, onValueChange = { fp = it }, label = { Text("Fingerprint") }, singleLine = true, modifier = Modifier.weight(1f))
                        }
                        OutlinedTextField(value = path, onValueChange = { path = it }, label = { Text(dtr("Path / ServiceName", "مسیر")) }, singleLine = true, modifier = Modifier.fillMaxWidth())
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedTextField(value = pubKey, onValueChange = { pubKey = it }, label = { Text("Reality PubKey") }, singleLine = true, modifier = Modifier.weight(1.4f))
                            OutlinedTextField(value = shortId, onValueChange = { shortId = it }, label = { Text("ShortId") }, singleLine = true, modifier = Modifier.weight(0.8f))
                        }
                        DesktopSwitchRow(dtr("TLS Fragment", "قطعه‌قطعه کردن TLS"), checked = fragment, onCheckedChange = { fragment = it })
                        if (!isJson) {
                            DesktopSwitchRow(dtr("Enable SNI Spoof 40443", "فعال‌سازی SNI Spoof 40443"), checked = spoof, onCheckedChange = { spoof = it })
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (isJson && useRaw) {
                            holder.saveRawJsonEdit(target, rawJson)
                                .onSuccess { editingProfile = null }
                                .onFailure { jsonErr = it.message }
                        } else {
                            holder.saveQuickEdit(
                                existing = target,
                                name = name,
                                address = address,
                                port = portText.toIntOrNull()?.coerceIn(1, 65535) ?: target.port,
                                secret = secret,
                                sni = sni,
                                transport = transport,
                                pathOrServiceName = path,
                                tlsFingerprint = fp,
                                realityPublicKey = pubKey,
                                realityShortId = shortId,
                                fragmentEnabled = fragment,
                                spoofLocalhost40443 = spoof,
                                spoofFakeSni = fakeSni,
                            )
                            editingProfile = null
                        }
                    },
                ) { Text(dtr("Save", "ذخیره")) }
            },
            dismissButton = { TextButton(onClick = { editingProfile = null }) { Text(dtr("Cancel", "انصراف")) } },
        )
    }
}
