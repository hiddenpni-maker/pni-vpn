package ir.pnifox.feature.settings

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.OpenInNew
import androidx.compose.material.icons.automirrored.rounded.Send
import androidx.compose.material.icons.rounded.BatteryChargingFull
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.ExpandLess
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.RestartAlt
import androidx.compose.material.icons.rounded.SystemUpdate
import androidx.compose.material.icons.rounded.WifiTethering
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import ir.pnifox.core.model.AccentColorPref
import ir.pnifox.core.model.AppLanguage
import ir.pnifox.core.model.IranPresetsCatalog
import ir.pnifox.core.model.PnifoxUsageGuideCatalog
import ir.pnifox.core.model.ThemeModePref
import ir.pnifox.core.model.TunnelState
import ir.pnifox.core.model.UpdateCheckState
import androidx.compose.ui.text.font.FontFamily
import ir.pnifox.core.ui.CoreLogsModalSheet
import ir.pnifox.core.ui.LocalAppLanguage
import ir.pnifox.core.ui.PnifoxBadgePill
import ir.pnifox.core.ui.PnifoxCard
import ir.pnifox.core.ui.PnifoxSectionLabel
import ir.pnifox.core.ui.PnifoxSwitchRow
import ir.pnifox.core.ui.R as UiR
import ir.pnifox.core.ui.ResponsiveScreenContainer
import ir.pnifox.core.ui.RulesetsEditorContent
import ir.pnifox.core.ui.responsiveListPadding
import ir.pnifox.core.ui.tr

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsRoute(
    onBack: () -> Unit,
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val isFa = LocalAppLanguage.current == AppLanguage.PERSIAN
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current

    var expandAdvancedAether by remember { mutableStateOf(false) }
    var expandBinaries by remember { mutableStateOf(true) }
    var expandGuide by remember { mutableStateOf(false) }
    var showLogsSheet by remember { mutableStateOf(false) }

    ResponsiveScreenContainer { metrics ->
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = responsiveListPadding(metrics),
            verticalArrangement = Arrangement.spacedBy(metrics.sectionSpacing),
        ) {
            item {
                Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back") }
                    Text(tr("Settings & Guides", "تنظیمات و راهنما"), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                    IconButton(onClick = viewModel::resetDefaults) { Icon(Icons.Rounded.RestartAlt, contentDescription = "Reset") }
                }
            }

            if (!state.statusBanner.isNullOrBlank()) {
                item {
                    PnifoxCard(
                        containerColor = MaterialTheme.colorScheme.primaryContainer,
                        contentPadding = 12.dp,
                        modifier = Modifier.clickable { viewModel.dismissBanner() },
                    ) {
                        Text(state.statusBanner.orEmpty(), style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }

            // 1. Appearance & Language (English default + optional Persian switch)
            item { PnifoxSectionLabel(title = tr("Appearance & Language", "ظاهر و زبان برنامه")) }
            item {
                PnifoxCard(contentPadding = metrics.cardPadding) {
                    Text(tr("Language", "زبان"), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.height(6.dp))
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        AppLanguage.entries.forEach { lang ->
                            FilterChip(
                                selected = state.appSettings.language == lang,
                                onClick = { viewModel.updateApp { it.copy(language = lang) } },
                                label = { Text(lang.displayLabel, maxLines = 1) },
                                modifier = Modifier.weight(1f),
                            )
                        }
                    }
                    Spacer(Modifier.height(10.dp))
                    Text(tr("Theme Mode", "پوسته برنامه"), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.height(6.dp))
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        ThemeModePref.entries.forEach { mode ->
                            FilterChip(
                                selected = state.appSettings.themeMode == mode,
                                onClick = { viewModel.setThemeMode(mode) },
                                label = { Text(if (isFa) mode.labelFa else mode.labelEn, maxLines = 1) },
                                modifier = Modifier.weight(1f),
                            )
                        }
                    }
                    Spacer(Modifier.height(12.dp))
                    Text(tr("Accent Color", "رنگ اصلی"), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        AccentColorPref.entries.forEach { accent ->
                            val selected = state.appSettings.accentColor == accent
                            Box(
                                modifier = Modifier
                                    .size(34.dp)
                                    .clip(CircleShape)
                                    .background(Color(accent.colorHex))
                                    .then(if (selected) Modifier.border(2.5.dp, MaterialTheme.colorScheme.onSurface, CircleShape) else Modifier)
                                    .clickable { viewModel.setAccentColor(accent) },
                                contentAlignment = Alignment.Center,
                            ) {
                                if (selected) Icon(Icons.Rounded.Check, null, tint = Color.White, modifier = Modifier.size(18.dp))
                            }
                        }
                    }
                }
            }

            // 2. Global & Routing Card
            item { PnifoxSectionLabel(title = tr("Global & Routing", "عمومی و مسیریابی")) }
            item {
                PnifoxCard(contentPadding = metrics.cardPadding) {
                    PnifoxSwitchRow(
                        title = tr("Auto-Reconnect on Internet Drop / Change", "اتصال مجدد خودکار هنگام قطع و وصل اینترنت"),
                        subtitle = tr("Automatically refreshes tunnel when Wi-Fi or mobile data reconnects.", "هنگام قطع و وصل شدن اینترنت بدون نیاز به خاموش و روشن دستی، اتصال را رفرش می‌کند."),
                        checked = state.appSettings.autoReconnectOnNetworkChange,
                        onCheckedChange = { c -> viewModel.updateApp { it.copy(autoReconnectOnNetworkChange = c) } },
                    )
                    PnifoxSwitchRow(title = "Chain Proxies", checked = state.appSettings.chainProxies, onCheckedChange = { c -> viewModel.updateApp { it.copy(chainProxies = c) } })
                    PnifoxSwitchRow(title = "Fake DNS", checked = state.appSettings.fakeDnsEnabled, onCheckedChange = { c -> viewModel.updateApp { it.copy(fakeDnsEnabled = c) } })
                    PnifoxSwitchRow(title = "IPv6 Routing", checked = state.appSettings.ipv6Enabled, onCheckedChange = { c -> viewModel.updateApp { it.copy(ipv6Enabled = c) } })
                    PnifoxSwitchRow(title = "Allow LAN", checked = state.appSettings.allowLan, onCheckedChange = { c -> viewModel.updateApp { it.copy(allowLan = c) } })
                    Spacer(Modifier.height(8.dp))
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(value = state.appSettings.localDns, onValueChange = { v -> viewModel.updateApp { it.copy(localDns = v) } }, label = { Text("Local DNS") }, singleLine = true, modifier = Modifier.weight(1f))
                        OutlinedTextField(value = state.appSettings.antiSanctionDns, onValueChange = { v -> viewModel.updateApp { it.copy(antiSanctionDns = v) } }, label = { Text("Anti-Sanction DNS") }, singleLine = true, modifier = Modifier.weight(1f))
                    }
                    Spacer(Modifier.height(8.dp))
                    Text("SOCKS5: 127.0.0.1:${state.aetherSettings.localSocksPort} • HTTP LAN: ${state.appSettings.httpProxyPort} • MTU: ${state.appSettings.mtu}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }

            // 2a. Hotspot / LAN VPN Sharing Card (Share VPN to Laptop & iOS)
            item { PnifoxSectionLabel(title = tr("Hotspot & LAN VPN Sharing", "اشتراک‌گذاری VPN روی لپ‌تاپ و آیفون")) }
            item {
                PnifoxCard(contentPadding = metrics.cardPadding) {
                    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Rounded.WifiTethering, null, tint = MaterialTheme.colorScheme.primary)
                        Spacer(Modifier.width(8.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = tr("Share Phone VPN over Hotspot / Wi-Fi", "اشتراک‌گذاری اینترنت بدون تحریم به دستگاه‌های دیگر"),
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                            )
                            val ipText = if (state.detectedLanIps.isEmpty()) {
                                tr("Turn on Hotspot/Wi-Fi and tap Refresh (usually 192.168.43.1)", "هات‌اسپات را روشن کنید و رفرش بزنید (معمولاً 192.168.43.1)")
                            } else {
                                tr("Detected Phone IP: ${state.detectedLanIps.joinToString(", ")}", "آی‌پی شناسایی‌شده گوشی: ${state.detectedLanIps.joinToString(", ")}")
                            }
                            Text(ipText, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                        }
                        IconButton(onClick = viewModel::refreshLanIps) {
                            Icon(Icons.Rounded.Refresh, contentDescription = "Refresh LAN IPs")
                        }
                    }
                    Spacer(Modifier.height(6.dp))
                    val hostExample = state.detectedLanIps.firstOrNull() ?: "192.168.43.1"
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(
                                text = tr(
                                    "1. Enable 'Allow LAN' above and connect VPN.\n2. iOS (iPhone/iPad): Wi-Fi -> (i) next to Hotspot -> Configure Proxy -> Manual -> Server: $hostExample, Port: ${state.appSettings.httpProxyPort}.\n3. Windows / macOS Laptop: Set HTTP Proxy to $hostExample:${state.appSettings.httpProxyPort} (or SOCKS5 to $hostExample:${state.aetherSettings.localSocksPort}).",
                                    "۱. گزینه Allow LAN بالا را روشن و وی‌پی‌ان را وصل کنید.\n۲. آیفون/آیپد (iOS): وارد Wi-Fi شوید -> آیکون (i) کنار هات‌اسپات -> Configure Proxy -> حالت Manual -> در Server مقدار $hostExample و در Port عدد ${state.appSettings.httpProxyPort} را بزنید.\n۳. لپ‌تاپ (ویندوز/مک): در تنظیمات Proxy، مقدار HTTP را روی $hostExample:${state.appSettings.httpProxyPort} (یا SOCKS5 روی $hostExample:${state.aetherSettings.localSocksPort}) تنظیم کنید.\n(نکته: موتور AETHER فقط SOCKS5 روی پورت ${state.aetherSettings.localSocksPort} و بخش سرورها/MHR هر دو پورت را پشتیبانی می‌کنند.)",
                                ),
                                style = MaterialTheme.typography.labelSmall,
                            )
                        }
                    }
                }
            }

            // 2b. Rulesets (Bypass Iran Websites & AdBlock)
            item { PnifoxSectionLabel(title = tr("Rulesets · Bypass Iran & AdBlock", "قوانین مسیریابی · وایت‌لیست ایران و حذف تبلیغات")) }
            item {
                PnifoxCard(contentPadding = metrics.cardPadding) {
                    val isTunnelUp = state.tunnelState is TunnelState.Connected || state.tunnelState is TunnelState.Connecting
                    RulesetsEditorContent(
                        appSettings = state.appSettings,
                        onUpdateApp = viewModel::updateApp,
                        isTunnelConnected = isTunnelUp,
                        onApplyAndReconnect = viewModel::reconnectActiveTunnel,
                    )
                }
            }

            // 3. Native Binaries Inspection Card (Live Verification of jniLibs + pni-core.aar)
            item {
                PnifoxCard(contentPadding = metrics.cardPadding) {
                    Row(modifier = Modifier.fillMaxWidth().clickable { expandBinaries = !expandBinaries }, verticalAlignment = Alignment.CenterVertically) {
                        Column(modifier = Modifier.weight(1f)) {
                            val readyCount = state.binaries.count { it.isAvailable }
                            Text(tr("Native Core Binaries (jniLibs)", "وضعیت هسته‌های بومی"), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                            Text("$readyCount / ${state.binaries.size} native cores installed & verified", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        IconButton(onClick = viewModel::refreshBinaryCheck) { Icon(Icons.Rounded.Refresh, contentDescription = "Refresh") }
                        Icon(if (expandBinaries) Icons.Rounded.ExpandLess else Icons.Rounded.ExpandMore, null)
                    }
                    AnimatedVisibility(visible = expandBinaries) {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 10.dp)) {
                            state.binaries.forEach { st ->
                                Surface(shape = RoundedCornerShape(10.dp), color = MaterialTheme.colorScheme.surfaceVariant, modifier = Modifier.fillMaxWidth()) {
                                    Row(modifier = Modifier.padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(st.binary.displayTitle, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                                            Text(st.binary.requiredFor, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                        }
                                        PnifoxBadgePill(
                                            text = if (st.isAvailable) tr("Ready", "آماده") else tr("Missing", "نصب‌نشده"),
                                            containerColor = if (st.isAvailable) Color(0xFF10B981).copy(alpha = 0.16f) else MaterialTheme.colorScheme.error.copy(alpha = 0.16f),
                                            contentColor = if (st.isAvailable) Color(0xFF10B981) else MaterialTheme.colorScheme.error,
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // 4. Collapsible Advanced AETHER Mirror
            item {
                PnifoxCard(contentPadding = metrics.cardPadding) {
                    Row(modifier = Modifier.fillMaxWidth().clickable { expandAdvancedAether = !expandAdvancedAether }, verticalAlignment = Alignment.CenterVertically) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(tr("Advanced AETHER Parameters", "تنظیمات پیشرفته AETHER"), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                            Text("Clear Psiphon cache, Exit dialMode, finalMask & CLI preview", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                        Icon(if (expandAdvancedAether) Icons.Rounded.ExpandLess else Icons.Rounded.ExpandMore, null)
                    }
                    AnimatedVisibility(visible = expandAdvancedAether) {
                        Column {
                            Spacer(Modifier.height(12.dp))
                            AdvancedAetherSection(
                                aether = state.aetherSettings,
                                commandPreview = state.commandPreview,
                                isFetchingKeys = state.isFetchingWarpKeys,
                                onUpdateAether = viewModel::updateAether,
                                onFetchWarpKeys = viewModel::fetchWarpKeys,
                                onClearPsiphonData = viewModel::clearPsiphonData,
                                onCopyCommand = { cmd -> clipboard.setText(AnnotatedString(cmd)) },
                                onOpenLogs = { showLogsSheet = true },
                            )
                        }
                    }
                }
            }

            // 5. Battery & Doze Card
            item {
                PnifoxCard(contentPadding = metrics.cardPadding) {
                    PnifoxSwitchRow(
                        title = tr("Pause stats telemetry when screen is off", "توقف آمار هنگام خاموش بودن صفحه"),
                        subtitle = tr("Reduces battery wakeups and respects Android Doze Mode", "کاهش مصرف باتری و سازگار با Doze Mode"),
                        checked = state.appSettings.pauseTelemetryOnScreenOff,
                        onCheckedChange = { c -> viewModel.updateApp { it.copy(pauseTelemetryOnScreenOff = c) } },
                        icon = Icons.Rounded.BatteryChargingFull,
                    )
                    OutlinedButton(
                        onClick = {
                            val intent = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS) else Intent(Settings.ACTION_SETTINGS)
                            runCatching { context.startActivity(intent) }
                        },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                    ) {
                        Text(tr("Open Android Battery Optimization", "تنظیمات صرفه‌جویی باتری اندروید"))
                    }
                }
            }

            // 6. Simple Usage & Operator Guides Card
            item {
                PnifoxCard(contentPadding = metrics.cardPadding) {
                    Row(modifier = Modifier.fillMaxWidth().clickable { expandGuide = !expandGuide }, verticalAlignment = Alignment.CenterVertically) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = tr("User Guide: Modes, Gaming & Anti-Sanction", "راهنمای ساده استفاده (گیم، رفع تحریم و اپراتورها)"),
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                            )
                            Text(
                                text = tr("When to use Fast WARP, Psiphon, Servers, DNS & Scanner", "توضیح ساده کاربرد هر حالت و نکات مهم"),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Icon(if (expandGuide) Icons.Rounded.ExpandLess else Icons.Rounded.ExpandMore, null)
                    }
                    AnimatedVisibility(visible = expandGuide) {
                        Column(verticalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.padding(top = 12.dp)) {
                            PnifoxUsageGuideCatalog.cards.forEach { guide ->
                                Surface(
                                    shape = RoundedCornerShape(12.dp),
                                    color = MaterialTheme.colorScheme.surfaceVariant,
                                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                                    modifier = Modifier.fillMaxWidth(),
                                ) {
                                    Column(modifier = Modifier.padding(12.dp)) {
                                        PnifoxBadgePill(text = if (isFa) guide.badgeFa else guide.badgeEn)
                                        Spacer(Modifier.height(6.dp))
                                        Text(if (isFa) guide.titleFa else guide.titleEn, fontWeight = FontWeight.Bold)
                                        Spacer(Modifier.height(2.dp))
                                        Text(if (isFa) guide.whenToUseFa else guide.whenToUseEn, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.primary)
                                        Spacer(Modifier.height(4.dp))
                                        Text(if (isFa) guide.howToUseFa else guide.howToUseEn, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                }
                            }
                            IranPresetsCatalog.carrierGuides.forEach { guide ->
                                Surface(shape = RoundedCornerShape(12.dp), color = MaterialTheme.colorScheme.surfaceVariant, border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline), modifier = Modifier.fillMaxWidth()) {
                                    Column(modifier = Modifier.padding(12.dp)) {
                                        PnifoxBadgePill(text = guide.carrierTag)
                                        Spacer(Modifier.height(6.dp))
                                        Text(if (isFa) guide.titleFa else guide.titleEn, fontWeight = FontWeight.Bold)
                                        Text(if (isFa) guide.summaryFa else guide.summaryEn, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                        Spacer(Modifier.height(6.dp))
                                        val steps = if (isFa) guide.stepsFa else guide.stepsEn
                                        steps.forEachIndexed { idx, step ->
                                            Text("${idx + 1}. $step", style = MaterialTheme.typography.labelMedium, modifier = Modifier.padding(vertical = 2.dp))
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // 6b. App Update Checker Card
            item { PnifoxSectionLabel(title = tr("App Updates", "به‌روزرسانی برنامه")) }
            item {
                PnifoxCard(contentPadding = metrics.cardPadding) {
                    PnifoxSwitchRow(
                        title = tr("Auto-check updates on launch", "بررسی خودکار آپدیت هنگام ورود"),
                        checked = state.appSettings.autoCheckUpdates,
                        onCheckedChange = { c -> viewModel.updateApp { it.copy(autoCheckUpdates = c) } },
                    )
                    Spacer(Modifier.height(6.dp))
                    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Icon(Icons.Rounded.SystemUpdate, null, tint = MaterialTheme.colorScheme.primary)
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = tr("Installed: v${state.currentVersionName}", "نسخه نصب‌شده: v${state.currentVersionName}"),
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Bold,
                            )
                            val stText = when (val u = state.updateState) {
                                UpdateCheckState.Idle -> tr("Tap Check Now to query GitHub Releases", "برای بررسی نسخه جدید دکمه زیر را بزنید")
                                UpdateCheckState.Checking -> tr("Checking GitHub releases...", "در حال بررسی گیت‌هاب...")
                                is UpdateCheckState.UpToDate -> tr("You are running the latest version ✓", "برنامه به‌روز است ✓")
                                is UpdateCheckState.UpdateAvailable -> tr("New version available: ${u.info.latestTag}", "نسخه جدید موجود است: ${u.info.latestTag}")
                                is UpdateCheckState.Error -> tr("Check failed: ${u.message}", "خطا در بررسی: ${u.message}")
                            }
                            Text(stText, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Button(
                            onClick = viewModel::checkForUpdates,
                            enabled = state.updateState !is UpdateCheckState.Checking,
                            shape = RoundedCornerShape(10.dp),
                        ) {
                            if (state.updateState is UpdateCheckState.Checking) {
                                CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                            } else {
                                Text(tr("Check Now", "بررسی آپدیت"), maxLines = 1)
                            }
                        }
                    }
                    val avail = state.updateState as? UpdateCheckState.UpdateAvailable
                    if (avail != null) {
                        Spacer(Modifier.height(8.dp))
                        if (avail.info.changelog.isNotBlank()) {
                            Text(avail.info.changelog.take(300), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Spacer(Modifier.height(8.dp))
                        }
                        OutlinedButton(
                            onClick = {
                                val target = avail.info.apkDownloadUrl ?: avail.info.releaseUrl
                                runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(target))) }
                            },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp),
                        ) {
                            Icon(Icons.Rounded.Download, null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text(tr("Download APK / Open Release (${avail.info.latestTag})", "دانلود نسخه جدید (${avail.info.latestTag})"))
                        }
                    }
                }
            }

            // 7. About pnifox + Clickable Telegram Channel
            item {
                PnifoxCard(contentPadding = metrics.cardPadding) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Image(
                            painter = painterResource(UiR.drawable.pnifox_makima_logo),
                            contentDescription = "pnifox",
                            modifier = Modifier.size(52.dp).clip(CircleShape),
                        )
                        Spacer(Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text("pnifox", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                            Text("v${state.currentVersionName} • AETHER + Xray + Sing-box + DNSTT + MHR", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                    Spacer(Modifier.height(12.dp))
                    Button(
                        onClick = { runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(state.appSettings.telegramUrl))) } },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(14.dp),
                    ) {
                        Icon(Icons.AutoMirrored.Rounded.Send, null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("Telegram Channel: https://t.me/otaqe_pni", maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Spacer(Modifier.width(6.dp))
                        Icon(Icons.AutoMirrored.Rounded.OpenInNew, null, modifier = Modifier.size(16.dp))
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
}
