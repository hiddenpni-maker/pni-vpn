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
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Key
import androidx.compose.material.icons.rounded.Link
import androidx.compose.material.icons.rounded.PushPin
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Search
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
import ir.pnifox.core.model.AetherScanMode
import ir.pnifox.core.model.AppLanguage
import ir.pnifox.core.model.ExitNode
import ir.pnifox.core.model.OneTapPreset
import ir.pnifox.core.model.PnifoxRegionsCatalog
import ir.pnifox.core.model.ProxyProfile
import ir.pnifox.core.model.PsiphonChainMode
import ir.pnifox.core.model.TunnelState
import ir.pnifox.core.network.ShareLinkParser
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
fun AetherDesktopTab(
    state: DesktopUiState,
    holder: DesktopStateHolder,
) {
    val isFa = LocalDesktopLanguage.current == AppLanguage.PERSIAN
    val app = state.persisted.appSettings
    val aether = state.persisted.aetherSettings
    val isConnected = state.tunnelState is TunnelState.Connected && state.tunnelState.section == ActiveSectionEngine.AETHER
    val isConnecting = state.tunnelState is TunnelState.Connecting && state.tunnelState.section == ActiveSectionEngine.AETHER

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
                            section = ActiveSectionEngine.AETHER,
                            onFallbackSystemProxy = { holder.connectViaSystemProxyFallback(ActiveSectionEngine.AETHER) },
                            onRestartAdmin = { holder.relaunchAsAdmin() },
                            showTunSuggestion = showTunHint,
                            onEnableTunMode = { holder.enableTunAndReconnect(ActiveSectionEngine.AETHER) },
                            onDismissTunSuggestion = { holder.dismissTunSuggestion() },
                        )
                        Spacer(Modifier.height(10.dp))
                        val endpointText = if (aether.scanForEndpoint || aether.endpointIp.isBlank()) {
                            dtr("Will auto-scan best Cloudflare IP on connect & save it", "در اولین اتصال بهترین IP اسکن و برای دفعات بعد ذخیره می‌شود")
                        } else {
                            dtr("Saved Endpoint: ${aether.endpointIp}:${aether.endpointPort} (Instant Connect)", "اندپوینت ذخیره‌شده: ${aether.endpointIp}:${aether.endpointPort} (اتصال سریع)")
                        }
                        Text(endpointText, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                    }
                    Spacer(Modifier.width(16.dp))
                    DesktopConnectButton(
                        isConnected = isConnected,
                        isConnecting = isConnecting,
                        onClick = { holder.toggleConnect(ActiveSectionEngine.AETHER) },
                    )
                }

                Spacer(Modifier.height(12.dp))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(
                        onClick = { holder.registerAetherKeys(AetherIdentityTarget.ALL) },
                        enabled = !state.isRegisteringWarp,
                        shape = RoundedCornerShape(10.dp),
                    ) {
                        if (state.isRegisteringWarp) CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                        else Icon(Icons.Rounded.Key, null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(dtr("Generate WARP Keys", "ساخت کلید WARP"))
                    }
                    val rescanBannerMsg = dtr("Next connect will scan for a fresh endpoint.", "در اتصال بعدی اندپوینت جدید اسکن می‌شود.")
                    OutlinedButton(
                        onClick = {
                            holder.updateAether { it.copy(scanForEndpoint = true) }
                            holder.setBanner(rescanBannerMsg)
                        },
                        shape = RoundedCornerShape(10.dp),
                    ) {
                        Icon(Icons.Rounded.Search, null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(dtr("Re-scan Endpoint", "اسکن مجدد اندپوینت"))
                    }
                    OutlinedButton(
                        onClick = { holder.clearAetherCache() },
                        shape = RoundedCornerShape(10.dp),
                    ) {
                        Icon(Icons.Rounded.DeleteOutline, null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(dtr("Reset Psiphon / Tor Cache", "پاکسازی کش سایفون / تور"))
                    }
                }
            }
        }

        item {
            ScenarioPresetsDesktopCard(state = state, holder = holder)
        }

        item {
            DesktopCard {
                Text(dtr("One-Tap Modes (Choose by Goal)", "حالت‌های سریع اتصال"), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(8.dp))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OneTapPreset.entries.forEach { preset ->
                        val label = if (isFa) preset.titleFa else preset.titleEn
                        FilterChip(
                            selected = app.activePreset == preset,
                            onClick = {
                                val nextExit = when (preset) {
                                    OneTapPreset.WARP_TO_PSIPHON -> ExitNode.PSIPHON
                                    OneTapPreset.WARP_TO_TOR -> ExitNode.TOR
                                    else -> ExitNode.DEFAULT
                                }
                                holder.updateApp { it.copy(activePreset = preset, exitNode = nextExit) }
                                holder.updateAether {
                                    it.copy(
                                        psiphonEnabled = nextExit == ExitNode.PSIPHON,
                                        torEnabled = nextExit == ExitNode.TOR,
                                    )
                                }
                            },
                            label = { Text(label) },
                        )
                    }
                }
            }
        }

        item {
            DesktopCard {
                Text(dtr("Exit Region (For Anti-Sanction / Psiphon)", "ریجن خروجی (برای رفع تحریم / سایفون)"), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(8.dp))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    PnifoxRegionsCatalog.regions.take(18).forEach { reg ->
                        FilterChip(
                            selected = aether.psiphonExitCountry.equals(reg.code, ignoreCase = true),
                            onClick = {
                                holder.updateApp { it.copy(regionCode = reg.code) }
                                holder.updateAether { it.copy(psiphonExitCountry = reg.code) }
                            },
                            label = { Text("${reg.flag} ${if (isFa) reg.nameFa else reg.nameEn}") },
                        )
                    }
                }
                if (aether.psiphonEnabled || app.activePreset == OneTapPreset.WARP_TO_PSIPHON) {
                    Spacer(Modifier.height(10.dp))
                    Text(dtr("Psiphon Transport Mode (Switch to CDN/Meek if Auto stalls)", "حالت اتصال سایفون (اگر Auto وصل نشد، CDN/Meek را امتحان کنید)"), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.height(6.dp))
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        PsiphonChainMode.entries.forEach { pm ->
                            FilterChip(
                                selected = aether.psiphonMode == pm,
                                onClick = { holder.updateAether { it.copy(psiphonMode = pm) } },
                                label = { Text(if (isFa) pm.labelFa else pm.labelEn) },
                            )
                        }
                    }
                }
            }
        }

        item {
            DesktopCard {
                Text(dtr("AETHER Anti-DPI & Endpoint Settings", "تنظیمات ضد فیلتر و اندپوینت"), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(8.dp))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = aether.endpointIp,
                        onValueChange = { v -> holder.updateAether { it.copy(endpointIp = v, scanForEndpoint = v.isBlank()) } },
                        label = { Text(dtr("Saved Endpoint IP (empty = Auto Scan)", "آی‌پی اندپوینت (خالی = اسکن خودکار)")) },
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
                Spacer(Modifier.height(8.dp))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    AetherScanMode.entries.forEach { sm ->
                        FilterChip(
                            selected = aether.scanMode == sm,
                            onClick = { holder.updateAether { it.copy(scanMode = sm) } },
                            label = { Text("Scan: ${sm.cliValue}") },
                        )
                    }
                }
                Spacer(Modifier.height(4.dp))
                DesktopSwitchRow(
                    title = dtr("Fragment TLS ClientHello", "تکه‌تکه کردن TLS (Fragment)"),
                    subtitle = dtr("Keep OFF if logs show 'TLS handshake unexpected EOF'.", "اگر در لاگ خطای unexpected EOF دیدید خاموش بگذارید."),
                    checked = aether.fragmentEnabled,
                    onCheckedChange = { c -> holder.updateAether { it.copy(fragmentEnabled = c) } },
                )
                DesktopSwitchRow(
                    title = dtr("Enable ECH (Encrypted ClientHello)", "فعال‌سازی ECH"),
                    checked = aether.echEnabled,
                    onCheckedChange = { c -> holder.updateAether { it.copy(echEnabled = c) } },
                )
                DesktopSwitchRow(
                    title = dtr("3-Stage Smart ECH Bootstrap (DoH / DoT / Port-53 + Shared CF ECH)", "دریافت هوشمند ECH (DoH/DoT/پورت۵۳ و قرض کلودفلر)"),
                    subtitle = dtr("Automatically fetches unblocked Cloudflare ECHConfig when UDP 53 ECH lookups are censored.", "اگر استعلام ECH مسدود باشد به صورت خودکار از DoH/DoT یا دامنه‌های باز کلودفلر می‌گیرد."),
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
                        Text(dtr("Fetch ECH Config Now", "دریافت ECH الان"))
                    }
                    if (aether.echBase64Override.isNotBlank()) {
                        DesktopBadgePill(
                            text = dtr("ECH Ready ✓", "کلید ECH آماده ✓"),
                            containerColor = Color(0xFF10B981).copy(alpha = 0.16f),
                            contentColor = Color(0xFF10B981),
                        )
                    }
                }
            }
        }

        item {
            ProtocolGuideDesktopCard()
        }
    }
}

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
                                ?: dtr("No server selected — import or pick one below", "سروری انتخاب نشده — از پایین اضافه یا انتخاب کنید"),
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.SemiBold,
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
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = { showImportDialog = true }, shape = RoundedCornerShape(10.dp)) {
                        Icon(Icons.Rounded.Add, null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(dtr("Import URI / JSON", "ورود کانفیگ"))
                    }
                    OutlinedButton(onClick = { showSubDialog = true }, shape = RoundedCornerShape(10.dp)) {
                        Icon(Icons.Rounded.Link, null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(dtr("Add Subscription", "افزودن اشتراک"))
                    }
                    OutlinedButton(onClick = { holder.refreshAllSubscriptions() }, enabled = !state.isSyncingSubs, shape = RoundedCornerShape(10.dp)) {
                        if (state.isSyncingSubs) CircularProgressIndicator(modifier = Modifier.size(15.dp), strokeWidth = 2.dp)
                        else Icon(Icons.Rounded.Refresh, null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(if (state.isSyncingSubs) dtr("Syncing...", "در حال دریافت...") else dtr("Sync Subs", "بروزرسانی ساب"))
                    }
                    OutlinedButton(onClick = { holder.pingAllProfiles() }, enabled = !state.isTestingAllServers, shape = RoundedCornerShape(10.dp)) {
                        Icon(Icons.Rounded.Speed, null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(if (state.isTestingAllServers) dtr("Probing...", "تست...") else dtr("Test All Real Delay", "تست پینگ همه"))
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
                    Text(dtr("No server configs yet. Click 'Import URI / JSON' to paste VLESS, Reality, VMess, Trojan, Hy2, WireGuard, or MASQUE JSON.", "هنوز کانفیگی اضافه نشده است. روی «ورود کانفیگ» کلیک کنید."))
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
                                    "127.0.0.1:40443 (SNI Spoof) <- ${profile.address}:${profile.port} • SNI=${profile.spoofFakeSni.ifBlank { profile.sni }}"
                                } else {
                                    "${profile.address}:${profile.port} • ${profile.transport.uppercase()} • SNI=${profile.sni.ifBlank { "-" }}"
                                },
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                        val pingLabel = when {
                            profile.lastPingMs == null -> if (selected) dtr("Selected", "انتخاب‌شده") else ""
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
                        Spacer(Modifier.width(8.dp))
                        FilterChip(
                            selected = profile.spoofLocalhost40443,
                            onClick = { holder.toggleSpoof(profile) },
                            label = { Text("SNI Spoof 40443", style = MaterialTheme.typography.labelSmall) },
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
            title = { Text(dtr("Import Configs or Subscription (URI / Base64 / YAML / JSON / URL)", "ورود کانفیگ یا لینک اشتراک")) },
            text = {
                OutlinedTextField(
                    value = raw,
                    onValueChange = { raw = it },
                    label = { Text("vless://, vmess://, trojan://, hy2://, https://... sub link, Clash YAML or JSON") },
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
                            OutlinedTextField(value = fakeSni, onValueChange = { fakeSni = it }, label = { Text(dtr("SNI Spoof 40443 Fake SNI", "SNI جعلی برای تونل 40443")) }, singleLine = true, modifier = Modifier.fillMaxWidth())
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
