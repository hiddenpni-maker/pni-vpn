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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.OpenInNew
import androidx.compose.material.icons.automirrored.rounded.Send
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.AdminPanelSettings
import androidx.compose.material.icons.rounded.AutoFixHigh
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.ContentPaste
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.LockOpen
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.rounded.Speed
import androidx.compose.material.icons.rounded.Stop
import androidx.compose.material.icons.rounded.SystemUpdate
import androidx.compose.material.icons.rounded.WifiTethering
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
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import ir.pnifox.core.model.AccentColorPref
import ir.pnifox.core.model.ActiveSectionEngine
import ir.pnifox.core.model.AppLanguage
import ir.pnifox.core.model.DnsEngineType
import ir.pnifox.core.model.DnsInnerProtocol
import ir.pnifox.core.model.DnsTunnelMode
import ir.pnifox.core.model.IranPresetsCatalog
import ir.pnifox.core.model.MhrDefaultSniPool
import ir.pnifox.core.model.MhrMode
import ir.pnifox.core.model.PnifoxUsageGuideCatalog
import ir.pnifox.core.model.ThemeModePref
import ir.pnifox.core.model.TunnelState
import ir.pnifox.core.model.UpdateCheckState
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
import java.awt.Desktop
import java.net.URI

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun MhrDesktopTab(
    state: DesktopUiState,
    holder: DesktopStateHolder,
) {
    val isFa = LocalDesktopLanguage.current == AppLanguage.PERSIAN
    val clipboard = LocalClipboardManager.current
    val mhr = state.persisted.mhrSettings
    val isConnected = state.tunnelState is TunnelState.Connected && state.tunnelState.section == ActiveSectionEngine.MHR
    val isConnecting = state.tunnelState is TunnelState.Connecting && state.tunnelState.section == ActiveSectionEngine.MHR

    var scriptInput by remember { mutableStateOf("") }
    var customSni by remember { mutableStateOf("") }
    var showImportDialog by remember { mutableStateOf(false) }

    LazyColumn(modifier = Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item {
            DesktopCard {
                Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Column(modifier = Modifier.weight(1f)) {
                        DesktopFoxMascot(tunnelState = state.tunnelState, section = ActiveSectionEngine.MHR)
                        Spacer(Modifier.height(8.dp))
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            MhrMode.entries.forEach { mode ->
                                FilterChip(
                                    selected = mhr.mode == mode,
                                    onClick = { holder.updateMhr { it.copy(mode = mode) } },
                                    label = { Text(if (isFa) mode.labelFa else mode.labelEn) },
                                )
                            }
                        }
                        Spacer(Modifier.height(4.dp))
                        Text(if (isFa) mhr.mode.descFa else mhr.mode.descEn, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Spacer(Modifier.width(16.dp))
                    DesktopConnectButton(
                        isConnected = isConnected,
                        isConnecting = isConnecting,
                        onClick = { holder.toggleConnect(ActiveSectionEngine.MHR) },
                    )
                }
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = { showImportDialog = true }, shape = RoundedCornerShape(10.dp)) {
                        Icon(Icons.Rounded.ContentPaste, null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(dtr("Import mhrv-rs://", "ورود کانفیگ"))
                    }
                    val shareCopiedMsg = dtr("Copied mhrv-rs:// share link.", "لینک mhrv-rs:// کپی شد.")
                    OutlinedButton(onClick = {
                        clipboard.setText(AnnotatedString(holder.exportMhrShareLink()))
                        holder.setBanner(shareCopiedMsg)
                    }, shape = RoundedCornerShape(10.dp)) {
                        Icon(Icons.Rounded.Share, null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(dtr("Copy Share Link", "کپی لینک اشتراک"))
                    }
                    Button(onClick = { holder.installMhrCaCert() }, shape = RoundedCornerShape(10.dp)) {
                        Icon(Icons.Rounded.LockOpen, null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(dtr("Install Windows CA Cert", "نصب گواهی CA ویندوز"))
                    }
                }
            }
        }

        if (mhr.mode != MhrMode.DIRECT) {
            item {
                DesktopCard {
                    Text(dtr("Google Apps Script Relay", "تنظیمات رله گوگل اسکریپت"), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(8.dp))
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(
                            value = scriptInput,
                            onValueChange = { scriptInput = it },
                            label = { Text(dtr("Deployment URL(s) or Script ID(s)", "لینک یا ID دیپلویمنت")) },
                            minLines = 2,
                            maxLines = 3,
                            modifier = Modifier.weight(1f),
                        )
                        Button(onClick = { holder.addMhrScriptIds(scriptInput); scriptInput = "" }, shape = RoundedCornerShape(10.dp)) {
                            Icon(Icons.Rounded.Add, null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(4.dp))
                            Text(dtr("Add", "افزودن"))
                        }
                    }
                    if (mhr.scriptIds.isNotEmpty()) {
                        Spacer(Modifier.height(8.dp))
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            mhr.scriptIds.forEachIndexed { i, id ->
                                Surface(shape = RoundedCornerShape(8.dp), color = MaterialTheme.colorScheme.surfaceVariant) {
                                    Row(modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                                        Text("#${i + 1} ${id.take(18)}...", style = MaterialTheme.typography.labelSmall.copy(fontFamily = FontFamily.Monospace))
                                        Spacer(Modifier.width(4.dp))
                                        Icon(Icons.Rounded.Close, null, modifier = Modifier.size(15.dp).clickable {
                                            holder.updateMhr { cur -> cur.copy(scriptIds = cur.scriptIds.filterNot { it == id }) }
                                        })
                                    }
                                }
                            }
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        value = mhr.authKey,
                        onValueChange = { v -> holder.updateMhr { it.copy(authKey = v) } },
                        label = { Text(dtr("auth_key (shared secret inside Code.gs)", "کلید امنیتی (auth_key داخل اسکریپت)")) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        }

        item {
            DesktopCard {
                Text(dtr("Google Fronting IP & SNI Tester", "آی‌پی گوگل و تستر SNI"), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(
                        value = mhr.googleIp,
                        onValueChange = { v -> holder.updateMhr { it.copy(googleIp = v) } },
                        label = { Text("google_ip") },
                        singleLine = true,
                        modifier = Modifier.weight(1f),
                    )
                    OutlinedTextField(
                        value = mhr.frontDomain,
                        onValueChange = { v -> holder.updateMhr { it.copy(frontDomain = v) } },
                        label = { Text("front_domain") },
                        singleLine = true,
                        modifier = Modifier.weight(1f),
                    )
                    OutlinedButton(onClick = { holder.autoDetectGoogleIp() }, enabled = !state.isDetectingGoogleIp, shape = RoundedCornerShape(10.dp)) {
                        Icon(Icons.Rounded.AutoFixHigh, null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(dtr("Auto IP", "تشخیص IP"))
                    }
                    Button(onClick = { holder.testAllSnis() }, enabled = !state.isTestingAllSnis, shape = RoundedCornerShape(10.dp)) {
                        Icon(Icons.Rounded.Speed, null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(if (state.isTestingAllSnis) dtr("Probing...", "تست...") else dtr("Test SNIs", "تست SNIها"))
                    }
                }
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(
                        value = customSni,
                        onValueChange = { customSni = it },
                        label = { Text(dtr("Add Custom SNI", "افزودن SNI دلخواه")) },
                        singleLine = true,
                        modifier = Modifier.weight(1f),
                    )
                    OutlinedButton(onClick = {
                        val s = customSni.trim()
                        if (s.isNotBlank()) holder.updateMhr { it.copy(sniHosts = (it.sniHosts + s).distinct()) }
                        customSni = ""
                    }) { Text(dtr("Add SNI", "افزودن")) }
                    OutlinedButton(onClick = { holder.updateMhr { it.copy(sniHosts = MhrDefaultSniPool.defaults) } }) {
                        Text(dtr("Reset Pool", "بازنشانی"))
                    }
                }
                Spacer(Modifier.height(8.dp))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    mhr.sniHosts.forEach { sni ->
                        val res = state.sniTestResults[sni]
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant,
                            modifier = Modifier.clickable { holder.updateMhr { it.copy(frontDomain = sni) } },
                        ) {
                            Row(modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                                Text(sni, style = MaterialTheme.typography.labelSmall.copy(fontFamily = FontFamily.Monospace))
                                if (res != null) {
                                    Spacer(Modifier.width(6.dp))
                                    DesktopBadgePill(
                                        text = if (res.ok) "${res.latencyMs}ms" else "Fail",
                                        containerColor = if (res.ok) Color(0xFF10B981).copy(alpha = 0.16f) else MaterialTheme.colorScheme.error.copy(alpha = 0.16f),
                                        contentColor = if (res.ok) Color(0xFF10B981) else MaterialTheme.colorScheme.error,
                                    )
                                }
                                IconButton(onClick = { holder.testSingleSni(sni) }, modifier = Modifier.size(24.dp)) {
                                    Icon(Icons.Rounded.Speed, null, modifier = Modifier.size(14.dp))
                                }
                                IconButton(onClick = { holder.updateMhr { cur -> cur.copy(sniHosts = cur.sniHosts.filterNot { it == sni }) } }, modifier = Modifier.size(24.dp)) {
                                    Icon(Icons.Rounded.Close, null, modifier = Modifier.size(14.dp))
                                }
                            }
                        }
                    }
                }
            }
        }

        item {
            DesktopCard {
                Text(dtr("Setup Guide & Code.gs Copy", "آموزش راه‌اندازی MHR + دریافت کد"), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(6.dp))
                Text(
                    text = dtr(
                        "1. If script.google.com is blocked, connect Mode = Direct first.\n2. Visit script.google.com -> New project -> paste Code.gs -> set AUTH_KEY -> Deploy Web app (Access: Anyone).\n3. Paste Deployment ID & AUTH_KEY above -> click 'Install Windows CA Cert' -> Connect!",
                        "۱. اگر سایت script.google.com باز نمی‌شود، اول با حالت Direct وصل شوید.\n۲. در script.google.com پروژه جدید بسازید و کد Code.gs را جایگزین کنید، AUTH_KEY بگذارید و Web app (دسترسی Anyone) را Deploy کنید.\n۳. ID و AUTH_KEY را بالا وارد کنید، دکمه «نصب گواهی CA ویندوز» را بزنید و متصل شوید!",
                    ),
                    style = MaterialTheme.typography.bodySmall,
                )
                Spacer(Modifier.height(8.dp))
                val codeGsCopiedMsg = dtr("Copied Code.gs to clipboard!", "کد Code.gs کپی شد!")
                val codeFullGsCopiedMsg = dtr("Copied CodeFull.gs to clipboard!", "کد CodeFull.gs کپی شد!")
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = {
                        clipboard.setText(AnnotatedString(holder.readMhrScript(fullTunnel = false)))
                        holder.setBanner(codeGsCopiedMsg)
                    }) {
                        Icon(Icons.Rounded.ContentCopy, null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Copy Code.gs")
                    }
                    OutlinedButton(onClick = {
                        clipboard.setText(AnnotatedString(holder.readMhrScript(fullTunnel = true)))
                        holder.setBanner(codeFullGsCopiedMsg)
                    }) {
                        Icon(Icons.Rounded.ContentCopy, null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Copy CodeFull.gs")
                    }
                }
            }
        }
    }

    if (showImportDialog) {
        var raw by remember { mutableStateOf(clipboard.getText()?.text.orEmpty()) }
        AlertDialog(
            onDismissRequest = { showImportDialog = false },
            title = { Text("Import mhrv-rs://") },
            text = { OutlinedTextField(value = raw, onValueChange = { raw = it }, minLines = 3, modifier = Modifier.fillMaxWidth()) },
            confirmButton = { Button(onClick = { holder.importMhrShareLink(raw); showImportDialog = false }) { Text(dtr("Import", "ورود")) } },
            dismissButton = { TextButton(onClick = { showImportDialog = false }) { Text(dtr("Cancel", "انصراف")) } },
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun DnsDesktopTab(
    state: DesktopUiState,
    holder: DesktopStateHolder,
) {
    val isFa = LocalDesktopLanguage.current == AppLanguage.PERSIAN
    val dns = state.persisted.dnsSettings
    val isConnected = state.tunnelState is TunnelState.Connected && state.tunnelState.section == ActiveSectionEngine.DNS
    val isConnecting = state.tunnelState is TunnelState.Connecting && state.tunnelState.section == ActiveSectionEngine.DNS

    LazyColumn(modifier = Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item {
            DesktopCard {
                Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Column(modifier = Modifier.weight(1f)) {
                        DesktopFoxMascot(tunnelState = state.tunnelState, section = ActiveSectionEngine.DNS)
                        Spacer(Modifier.height(8.dp))
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            DnsEngineType.entries.forEach { et ->
                                FilterChip(
                                    selected = dns.engineType == et,
                                    onClick = { holder.updateDns { it.copy(engineType = et) } },
                                    label = { Text(if (isFa) et.labelFa else et.labelEn) },
                                )
                            }
                        }
                    }
                    Spacer(Modifier.width(16.dp))
                    DesktopConnectButton(
                        isConnected = isConnected,
                        isConnecting = isConnecting,
                        onClick = { holder.toggleConnect(ActiveSectionEngine.DNS) },
                    )
                }
            }
        }

        item {
            DesktopCard {
                Text(dtr("DNS Resolver Presets", "پیش‌تنظیم‌های سرور DNS"), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(8.dp))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    IranPresetsCatalog.dnsPresets.forEach { preset ->
                        FilterChip(
                            selected = dns.selectedPresetId == preset.id,
                            onClick = {
                                holder.updateDns {
                                    it.copy(
                                        selectedPresetId = preset.id,
                                        mode = preset.mode,
                                        resolverAddress = preset.address,
                                    )
                                }
                            },
                            label = { Text(if (isFa) preset.titleFa else preset.titleEn) },
                        )
                    }
                }
                Spacer(Modifier.height(8.dp))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    DnsTunnelMode.entries.forEach { m ->
                        FilterChip(
                            selected = dns.mode == m,
                            onClick = { holder.updateDns { it.copy(mode = m) } },
                            label = { Text(m.label) },
                        )
                    }
                }
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = dns.resolverAddress,
                    onValueChange = { v -> holder.updateDns { it.copy(resolverAddress = v) } },
                    label = { Text(dtr("Resolver Address (e.g. 1.1.1.1:53 or DoH URL)", "آدرس Resolver")) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }

        if (dns.engineType == DnsEngineType.CUSTOM_DNSTT) {
            item {
                DesktopCard {
                    Text(dtr("Custom DNSTT / SlowDNS Credentials", "مشخصات سرور DNSTT / SlowDNS"), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(
                            value = dns.nameserver,
                            onValueChange = { v -> holder.updateDns { it.copy(nameserver = v) } },
                            label = { Text("Nameserver Domain") },
                            singleLine = true,
                            modifier = Modifier.weight(1f),
                        )
                        OutlinedTextField(
                            value = dns.publicKeyHex,
                            onValueChange = { v -> holder.updateDns { it.copy(publicKeyHex = v) } },
                            label = { Text("Public Key (Hex)") },
                            singleLine = true,
                            modifier = Modifier.weight(1f),
                        )
                    }
                    Spacer(Modifier.height(8.dp))
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        DnsInnerProtocol.entries.forEach { proto ->
                            FilterChip(
                                selected = dns.innerProtocol == proto,
                                onClick = { holder.updateDns { it.copy(innerProtocol = proto) } },
                                label = { Text(proto.label) },
                            )
                        }
                    }
                    if (dns.innerProtocol == DnsInnerProtocol.SSH) {
                        Spacer(Modifier.height(8.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedTextField(value = dns.sshUsername, onValueChange = { v -> holder.updateDns { it.copy(sshUsername = v) } }, label = { Text("SSH Username") }, singleLine = true, modifier = Modifier.weight(1f))
                            OutlinedTextField(value = dns.sshPassword, onValueChange = { v -> holder.updateDns { it.copy(sshPassword = v) } }, label = { Text("SSH Password") }, singleLine = true, modifier = Modifier.weight(1f))
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        OutlinedButton(onClick = { holder.testPort53AndTxt() }, enabled = !state.isProbingDns) {
                            Text(if (state.isProbingDns) dtr("Probing...", "در حال تست...") else dtr("Test UDP/53 & TXT", "تست پورت ۵۳ و TXT"))
                        }
                        if (state.dnsProbeStatus.isNotBlank()) {
                            Text(state.dnsProbeStatus, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ScannerDesktopTab(
    state: DesktopUiState,
    holder: DesktopStateHolder,
) {
    var provider by remember { mutableStateOf("cloudflare" to "cf_v4.txt") }
    var sni by remember { mutableStateOf("speed.cloudflare.com") }
    var customTargets by remember { mutableStateOf("") }
    var countFloat by remember { mutableStateOf(60f) }

    val providers = listOf(
        "cloudflare" to "cf_v4.txt",
        "warp" to "warp_cidrs.txt",
        "fastly" to "fastly_v4.txt",
        "gcore" to "gcore_v4.txt",
        "akamai" to "akamai_v4.txt",
    )

    LazyColumn(modifier = Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item {
            DesktopCard {
                Text(dtr("Multi-CDN Clean IP Scanner (Windows)", "اسکنر آی‌پی تمیز (کلودفلر / وارپ)"), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(8.dp))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    providers.forEach { pair ->
                        FilterChip(
                            selected = provider.first == pair.first,
                            onClick = { provider = pair },
                            label = { Text(pair.first.uppercase()) },
                        )
                    }
                }
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(value = sni, onValueChange = { sni = it }, label = { Text("TLS SNI") }, singleLine = true, modifier = Modifier.weight(1f))
                    OutlinedTextField(value = customTargets, onValueChange = { customTargets = it }, label = { Text(dtr("Custom IPs / CIDRs (optional)", "رنج IP دلخواه (اختیاری)")) }, singleLine = true, modifier = Modifier.weight(1.4f))
                }
                Spacer(Modifier.height(6.dp))
                Text(dtr("Sample IPs: ${countFloat.toInt()}", "تعداد تست: ${countFloat.toInt()}"), style = MaterialTheme.typography.labelMedium)
                Slider(value = countFloat, onValueChange = { countFloat = it }, valueRange = 15f..250f)
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                    if (!state.isScanning) {
                        Button(onClick = { holder.startScan(provider.first, provider.second, customTargets, sni, countFloat.toInt()) }) {
                            Icon(Icons.Rounded.PlayArrow, null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text(dtr("Start Scan", "شروع اسکن"))
                        }
                    } else {
                        Button(onClick = { holder.stopScan() }) {
                            Icon(Icons.Rounded.Stop, null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text(dtr("Stop", "توقف"))
                        }
                    }
                    if (state.scanProgressText.isNotBlank()) {
                        Text(state.scanProgressText, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                    }
                }
            }
        }

        items(state.scanResults.take(40)) { res ->
            DesktopCard(padding = 10.dp) {
                Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    DesktopBadgePill(
                        text = res.grade,
                        containerColor = if (res.grade in listOf("S+", "A")) Color(0xFF10B981).copy(alpha = 0.16f) else MaterialTheme.colorScheme.surfaceVariant,
                        contentColor = if (res.grade in listOf("S+", "A")) Color(0xFF10B981) else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.width(10.dp))
                    Text("${res.ip}:${res.port}", style = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace), fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                    Text("${res.latencyMs}ms • Loss ${res.lossPct.toInt()}% • Jitter ${res.jitterMs}ms", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.width(10.dp))
                    OutlinedButton(onClick = { holder.applyScannedIp(res.ip, res.port) }, shape = RoundedCornerShape(8.dp)) {
                        Text(dtr("Apply IP", "اعمال IP"))
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SettingsDesktopTab(
    state: DesktopUiState,
    holder: DesktopStateHolder,
) {
    val isFa = LocalDesktopLanguage.current == AppLanguage.PERSIAN
    val app = state.persisted.appSettings

    LazyColumn(modifier = Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        // 1. Windows Connection Mode (System Proxy vs Wintun TUN)
        item {
            DesktopCard {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(dtr("Windows Connection Mode", "نحوه اتصال در ویندوز"), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                    DesktopBadgePill(
                        text = if (state.isAdmin) dtr("Running as Admin ✓", "اجرا با دسترسی ادمین ✓") else dtr("Standard User", "کاربر عادی"),
                        containerColor = if (state.isAdmin) Color(0xFF10B981).copy(alpha = 0.16f) else MaterialTheme.colorScheme.surfaceVariant,
                        contentColor = if (state.isAdmin) Color(0xFF10B981) else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Spacer(Modifier.height(8.dp))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    WindowsTunnelMode.entries.forEach { m ->
                        FilterChip(
                            selected = state.persisted.windowsTunnelMode == m,
                            onClick = { holder.setWindowsTunnelMode(m) },
                            label = { Text(if (isFa) m.labelFa else m.labelEn) },
                        )
                    }
                }
                Spacer(Modifier.height(4.dp))
                Text(
                    text = if (isFa) state.persisted.windowsTunnelMode.descFa else state.persisted.windowsTunnelMode.descEn,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (state.persisted.windowsTunnelMode == WindowsTunnelMode.TUN_WINTUN && !state.isAdmin) {
                    Spacer(Modifier.height(8.dp))
                    Button(onClick = { holder.relaunchAsAdmin() }, shape = RoundedCornerShape(10.dp)) {
                        Icon(Icons.Rounded.AdminPanelSettings, null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(dtr("Restart pnifox as Administrator", "اجرای مجدد به عنوان ادمین"))
                    }
                }
            }
        }

        // 2. Language, Theme & Accent
        item {
            DesktopCard {
                Text(dtr("Language, Theme & Appearance", "زبان و ظاهر برنامه"), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    AppLanguage.entries.forEach { lang ->
                        FilterChip(
                            selected = app.language == lang,
                            onClick = { holder.updateApp { it.copy(language = lang, hasSelectedLanguage = true) } },
                            label = { Text(lang.displayLabel) },
                        )
                    }
                    ThemeModePref.entries.filter { it != ThemeModePref.SYSTEM }.forEach { tm ->
                        FilterChip(
                            selected = app.themeMode == tm,
                            onClick = { holder.updateApp { it.copy(themeMode = tm) } },
                            label = { Text(if (isFa) tm.labelFa else tm.labelEn) },
                        )
                    }
                }
                Spacer(Modifier.height(6.dp))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    AccentColorPref.entries.forEach { ac ->
                        FilterChip(
                            selected = app.accentColor == ac,
                            onClick = { holder.updateApp { it.copy(accentColor = ac) } },
                            label = { Text(if (isFa) ac.labelFa else ac.labelEn) },
                        )
                    }
                }
            }
        }

        // 3. Rulesets
        item {
            DesktopCard {
                Text(dtr("Rulesets · Whitelist Iran & AdBlock", "قوانین مسیریابی · وایت‌لیست ایران و AdBlock"), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                DesktopSwitchRow(dtr("Whitelist Iran Sites (Bypass VPN)", "عبور مستقیم سایت‌های ایرانی و بانکی"), checked = app.bypassIranEnabled, onCheckedChange = { c -> holder.updateApp { it.copy(bypassIranEnabled = c) } })
                DesktopSwitchRow(dtr("Block Ads & Trackers (AdBlock)", "بلاک کردن تبلیغات"), checked = app.blockAdsEnabled, onCheckedChange = { c -> holder.updateApp { it.copy(blockAdsEnabled = c) } })
                DesktopSwitchRow(dtr("Bypass Private LAN / Router", "عبور مستقیم شبکه محلی و مودم"), checked = app.bypassPrivateLanEnabled, onCheckedChange = { c -> holder.updateApp { it.copy(bypassPrivateLanEnabled = c) } })
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(value = app.customDirectRules, onValueChange = { v -> holder.updateApp { it.copy(customDirectRules = v) } }, label = { Text(dtr("Custom Direct Rules", "دامنه/IP مستقیم")) }, minLines = 2, maxLines = 4, modifier = Modifier.weight(1f))
                    OutlinedTextField(value = app.customBlockRules, onValueChange = { v -> holder.updateApp { it.copy(customBlockRules = v) } }, label = { Text(dtr("Custom Block Rules", "دامنه/IP مسدود")) }, minLines = 2, maxLines = 4, modifier = Modifier.weight(1f))
                }
            }
        }

        // 4. Hotspot / LAN Sharing
        item {
            DesktopCard {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Rounded.WifiTethering, null, tint = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.width(8.dp))
                    Text(dtr("Allow LAN / Share Windows VPN to Phone/iPhone", "اشتراک VPN کامپیوتر روی گوشی و آیفون"), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                    IconButton(onClick = { holder.refreshLanIps() }) { Icon(Icons.Rounded.Refresh, null) }
                }
                DesktopSwitchRow(dtr("Allow LAN Connections (0.0.0.0)", "باز کردن پورت روی شبکه محلی (Allow LAN)"), checked = app.allowLan, onCheckedChange = { c -> holder.updateApp { it.copy(allowLan = c) } })
                val ip = state.lanIps.firstOrNull() ?: "192.168.x.x"
                Text(
                    text = dtr(
                        "Detected PC LAN IP: ${state.lanIps.joinToString(", ").ifBlank { "Turn on Wi-Fi / Hotspot" }}\nOn iPhone/Android (same Wi-Fi): Manual HTTP Proxy -> $ip:${app.httpProxyPort} (SOCKS5: ${state.persisted.aetherSettings.localSocksPort})",
                        "IP کامپیوتر در شبکه: ${state.lanIps.joinToString(", ").ifBlank { "به وای‌فای یا هات‌اسپات وصل شوید" }}\nدر گوشی یا آیفون (متصل به همین مودم/هات‌اسپات): HTTP Proxy -> $ip:${app.httpProxyPort}",
                    ),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        // 5. App Updates & Telegram
        item {
            DesktopCard {
                Text(dtr("App Updates & Community", "به‌روزرسانی برنامه و کانال تلگرام"), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                DesktopSwitchRow(dtr("Auto-check updates on launch", "بررسی خودکار آپدیت هنگام باز شدن"), checked = app.autoCheckUpdates, onCheckedChange = { c -> holder.updateApp { it.copy(autoCheckUpdates = c) } })
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(dtr("Installed: v${state.appVersion}", "نسخه نصب‌شده: v${state.appVersion}"), modifier = Modifier.weight(1f))
                    val stMsg = when (val u = state.updateState) {
                        is UpdateCheckState.UpToDate -> dtr("Up to date ✓", "به‌روز است ✓")
                        is UpdateCheckState.UpdateAvailable -> dtr("New release: ${u.info.latestTag}", "نسخه جدید: ${u.info.latestTag}")
                        is UpdateCheckState.Checking -> dtr("Checking...", "در حال بررسی...")
                        is UpdateCheckState.Error -> u.message
                        UpdateCheckState.Idle -> ""
                    }
                    if (stMsg.isNotBlank()) DesktopBadgePill(stMsg)
                    Button(onClick = { holder.checkForUpdates() }, shape = RoundedCornerShape(10.dp)) {
                        Icon(Icons.Rounded.SystemUpdate, null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(dtr("Check Updates", "بررسی آپدیت"))
                    }
                    OutlinedButton(onClick = {
                        runCatching { Desktop.getDesktop().browse(URI(app.telegramUrl.ifBlank { "https://t.me/otaqe_pni" })) }
                    }, shape = RoundedCornerShape(10.dp)) {
                        Icon(Icons.AutoMirrored.Rounded.Send, null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("@otaqe_pni")
                        Spacer(Modifier.width(4.dp))
                        Icon(Icons.AutoMirrored.Rounded.OpenInNew, null, modifier = Modifier.size(14.dp))
                    }
                }
                val avail = state.updateState as? UpdateCheckState.UpdateAvailable
                if (avail != null) {
                    Spacer(Modifier.height(8.dp))
                    OutlinedButton(onClick = { runCatching { Desktop.getDesktop().browse(URI(avail.info.releaseUrl)) } }) {
                        Icon(Icons.Rounded.Download, null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(dtr("Open Release ${avail.info.latestTag}", "باز کردن صفحه دانلود ${avail.info.latestTag}"))
                    }
                }
            }
        }

        // 6. Bundled Core Binaries Status
        item {
            DesktopCard {
                Text(dtr("Windows Native Cores Status", "وضعیت فایل‌های هسته ویندوز"), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(6.dp))
                state.binaries.forEach { bin ->
                    Row(modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(bin.name, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                            Text(bin.role, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        DesktopBadgePill(
                            text = if (bin.available) dtr("Ready", "آماده") else dtr("Missing", "یافت نشد"),
                            containerColor = if (bin.available) Color(0xFF10B981).copy(alpha = 0.16f) else MaterialTheme.colorScheme.error.copy(alpha = 0.16f),
                            contentColor = if (bin.available) Color(0xFF10B981) else MaterialTheme.colorScheme.error,
                        )
                    }
                }
            }
        }

        // 7. Usage Guides
        item {
            DesktopCard {
                Text(dtr("Simple Usage Guides", "آموزش ساده استفاده از بخش‌ها"), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(6.dp))
                PnifoxUsageGuideCatalog.cards.forEach { card ->
                    Surface(shape = RoundedCornerShape(10.dp), color = MaterialTheme.colorScheme.surfaceVariant, modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                        Column(modifier = Modifier.padding(10.dp)) {
                            DesktopBadgePill(if (isFa) card.badgeFa else card.badgeEn)
                            Spacer(Modifier.height(4.dp))
                            Text(if (isFa) card.titleFa else card.titleEn, fontWeight = FontWeight.Bold)
                            Text(if (isFa) card.howToUseFa else card.howToUseEn, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
        }
    }
}
