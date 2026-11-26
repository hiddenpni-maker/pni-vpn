package ir.pnifox.desktop

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.OpenInNew
import androidx.compose.material.icons.automirrored.rounded.Send
import androidx.compose.material.icons.rounded.Bolt
import androidx.compose.material.icons.rounded.Dns
import androidx.compose.material.icons.rounded.Radar
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material.icons.rounded.Storage
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.res.loadImageBitmap
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Tray
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import ir.pnifox.core.model.ActiveSectionEngine
import ir.pnifox.core.model.AppLanguage
import ir.pnifox.core.model.TunnelState
import ir.pnifox.desktop.core.WindowsSecurityHelper
import ir.pnifox.desktop.model.DesktopTab
import ir.pnifox.desktop.ui.DesktopBadgePill
import ir.pnifox.desktop.ui.DesktopCard
import ir.pnifox.desktop.ui.DesktopLiveLogConsole
import ir.pnifox.desktop.ui.DesktopStateHolder
import ir.pnifox.desktop.ui.DesktopTelemetryBar
import ir.pnifox.desktop.ui.PnifoxDesktopTheme
import ir.pnifox.desktop.ui.dtr
import ir.pnifox.desktop.ui.tabs.AetherDesktopTab
import ir.pnifox.desktop.ui.tabs.DnsDesktopTab
import ir.pnifox.desktop.ui.tabs.MhrDesktopTab
import ir.pnifox.desktop.ui.tabs.ScannerDesktopTab
import ir.pnifox.desktop.ui.tabs.ServersDesktopTab
import ir.pnifox.desktop.ui.tabs.SettingsDesktopTab

fun main() = application {
    val holder = remember { DesktopStateHolder() }
    val instanceLock = remember { WindowsSecurityHelper.acquireSingleInstanceLock(holder.store.dataDir) }
    val state by holder.uiState.collectAsState()
    var windowVisible by remember { mutableStateOf(true) }

    if (instanceLock == null) {
        Window(
            onCloseRequest = { exitApplication() },
            title = "pnifox — Already Running",
            state = rememberWindowState(width = 420.dp, height = 210.dp),
        ) {
            AlertDialog(
                onDismissRequest = { exitApplication() },
                title = { Text("pnifox is already running") },
                text = { Text("Another pnifox window is already open (check the system tray near the clock).") },
                confirmButton = { Button(onClick = { exitApplication() }) { Text("OK") } },
            )
        }
        return@application
    }

    val logoPainter = remember {
        val stream = DesktopStateHolder::class.java.classLoader.getResourceAsStream("pnifox_logo.png")
        stream?.use { BitmapPainter(loadImageBitmap(it)) }
    }

    val isTunnelActive = state.tunnelState is TunnelState.Connected || state.tunnelState is TunnelState.Connecting

    if (logoPainter != null) {
        Tray(
            icon = logoPainter,
            tooltip = "pnifox • ${if (isTunnelActive) "Active" else "Ready"}",
            onAction = { windowVisible = true },
            menu = {
                Item(
                    text = if (windowVisible) "Hide Window" else "Show pnifox",
                    onClick = { windowVisible = !windowVisible },
                )
                Item(
                    text = if (isTunnelActive) "Disconnect" else "Connect",
                    onClick = { holder.toggleConnect(state.persisted.appSettings.activeSection) },
                )
                Separator()
                Item(
                    text = "Exit",
                    onClick = {
                        holder.controller.disconnectSync()
                        exitApplication()
                    },
                )
            },
        )
    }

    Window(
        visible = windowVisible,
        onCloseRequest = {
            if (isTunnelActive) {
                // Keep tunnel alive in system tray when active; otherwise exit cleanly
                windowVisible = false
            } else {
                holder.controller.disconnectSync()
                exitApplication()
            }
        },
        title = "pnifox — Windows VPN & Anti-Sanction Client",
        icon = logoPainter,
        state = rememberWindowState(width = 1120.dp, height = 760.dp),
    ) {
        val app = state.persisted.appSettings
        val clipboard = LocalClipboardManager.current

        PnifoxDesktopTheme(
            language = app.language,
            themeMode = app.themeMode,
            accent = app.accentColor,
        ) {
            Surface(
                modifier = Modifier.fillMaxSize(),
                color = MaterialTheme.colorScheme.background,
            ) {
                Row(modifier = Modifier.fillMaxSize()) {
                    // Left Navigation Rail
                    Surface(
                        modifier = Modifier.width(230.dp).fillMaxHeight(),
                        color = MaterialTheme.colorScheme.surface,
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)),
                    ) {
                        Column(
                            modifier = Modifier.fillMaxSize().padding(14.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(bottom = 8.dp)) {
                                if (logoPainter != null) {
                                    Image(
                                        painter = logoPainter,
                                        contentDescription = "pnifox",
                                        modifier = Modifier.size(38.dp).clip(CircleShape),
                                    )
                                    Spacer(Modifier.width(10.dp))
                                }
                                Column {
                                    Text("pnifox", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                                    Text("v${state.appVersion} Windows", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                                }
                            }

                            val tabs = listOf(
                                DesktopTab.AETHER to Icons.Rounded.Bolt,
                                DesktopTab.SERVERS to Icons.Rounded.Storage,
                                DesktopTab.MHR to Icons.Rounded.Shield,
                                DesktopTab.DNS to Icons.Rounded.Dns,
                                DesktopTab.SCANNER to Icons.Rounded.Radar,
                                DesktopTab.SETTINGS to Icons.Rounded.Settings,
                            )
                            tabs.forEach { (tab, icon) ->
                                val selected = state.selectedTab == tab
                                val isFa = app.language == AppLanguage.PERSIAN
                                Surface(
                                    shape = RoundedCornerShape(12.dp),
                                    color = if (selected) MaterialTheme.colorScheme.primaryContainer else Color.Transparent,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(12.dp))
                                        .clickable { holder.selectTab(tab) },
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                    ) {
                                        Icon(
                                            icon,
                                            contentDescription = null,
                                            tint = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                                            modifier = Modifier.size(20.dp),
                                        )
                                        Spacer(Modifier.width(10.dp))
                                        Text(
                                            text = if (isFa) tab.labelFa else tab.labelEn,
                                            style = MaterialTheme.typography.bodyMedium,
                                            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                                            color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                        )
                                    }
                                }
                            }

                            Spacer(Modifier.weight(1f))

                            OutlinedButton(
                                onClick = { holder.openTelegramChannel() },
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                Icon(Icons.AutoMirrored.Rounded.Send, null, modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.primary)
                                Spacer(Modifier.width(8.dp))
                                Text(
                                    text = dtr("Telegram Channel", "کانال تلگرام"),
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.weight(1f),
                                )
                                Icon(Icons.AutoMirrored.Rounded.OpenInNew, null, modifier = Modifier.size(14.dp))
                            }

                            val statusColor = when (state.tunnelState) {
                                is TunnelState.Connected -> Color(0xFF10B981)
                                is TunnelState.Connecting -> Color(0xFFF97316)
                                is TunnelState.Error -> MaterialTheme.colorScheme.error
                                TunnelState.Disconnected -> Color(0xFF94A3B8)
                            }
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = MaterialTheme.colorScheme.surfaceVariant,
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Box(modifier = Modifier.size(8.dp).clip(CircleShape).background(statusColor))
                                        Spacer(Modifier.width(6.dp))
                                        Text(
                                            text = when (state.tunnelState) {
                                                is TunnelState.Connected -> dtr("Connected", "متصل")
                                                is TunnelState.Connecting -> dtr("Connecting...", "در حال اتصال...")
                                                is TunnelState.Error -> dtr("Error", "خطا")
                                                TunnelState.Disconnected -> dtr("Disconnected", "قطع")
                                            },
                                            style = MaterialTheme.typography.labelMedium,
                                            fontWeight = FontWeight.Bold,
                                        )
                                    }
                                    val modeText = if (app.activeSection == ActiveSectionEngine.MHR && state.persisted.mhrSettings.proxyOnly) {
                                        dtr("MHR Proxy Only", "فقط پروکسی MHR")
                                    } else {
                                        if (app.language == AppLanguage.PERSIAN) state.persisted.windowsTunnelMode.labelFa else state.persisted.windowsTunnelMode.labelEn
                                    }
                                    Text(
                                        text = modeText,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                    val portLabel = if (app.activeSection == ActiveSectionEngine.MHR) {
                                        "HTTP :${state.persisted.mhrSettings.listenPort}  SOCKS5 :${state.persisted.mhrSettings.socks5Port}"
                                    } else {
                                        "SOCKS5 :${state.persisted.aetherSettings.localSocksPort}  HTTP :${app.httpProxyPort}"
                                    }
                                    Text(
                                        text = portLabel,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.primary,
                                    )
                                }
                            }
                        }
                    }

                    // Main Content + Telemetry + Live Log Panel
                    Column(
                        modifier = Modifier.weight(1f).fillMaxHeight().padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        if (!state.statusBanner.isNullOrBlank()) {
                            DesktopCard(
                                containerColor = MaterialTheme.colorScheme.primaryContainer,
                                padding = 10.dp,
                                modifier = Modifier.clickable { holder.dismissBanner() },
                            ) {
                                Text(state.statusBanner.orEmpty(), style = MaterialTheme.typography.bodySmall)
                            }
                        }

                        val connected = state.tunnelState as? TunnelState.Connected
                        DesktopTelemetryBar(
                            rxSpeedBps = state.rxSpeedBps,
                            txSpeedBps = state.txSpeedBps,
                            sessionRxBytes = state.sessionRxBytes,
                            sessionTxBytes = state.sessionTxBytes,
                            pingMs = connected?.pingMs,
                            isConnected = connected != null,
                            isTestingPing = state.isTestingPing,
                            onRetestPing = { holder.testActiveRealPing() },
                        )

                        Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                            when (state.selectedTab) {
                                DesktopTab.AETHER -> AetherDesktopTab(state, holder)
                                DesktopTab.SERVERS -> ServersDesktopTab(state, holder)
                                DesktopTab.MHR -> MhrDesktopTab(state, holder)
                                DesktopTab.DNS -> DnsDesktopTab(state, holder)
                                DesktopTab.SCANNER -> ScannerDesktopTab(state, holder)
                                DesktopTab.SETTINGS -> SettingsDesktopTab(state, holder)
                            }
                        }

                        val logsCopiedMsg = dtr("Logs copied to clipboard.", "لاگ‌ها کپی شدند.")
                        DesktopLiveLogConsole(
                            logs = state.logs,
                            onCopy = {
                                clipboard.setText(AnnotatedString(holder.logs.exportPlainText()))
                                holder.setBanner(logsCopiedMsg)
                            },
                            onClear = { holder.logs.clear() },
                        )
                    }
                }
            }

            // First-launch dialogs: Language -> Telegram Channel -> Update Available
            if (!app.hasSelectedLanguage) {
                AlertDialog(
                    onDismissRequest = {},
                    title = { Text("Choose Language • انتخاب زبان", fontWeight = FontWeight.Bold) },
                    text = {
                        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            Text("Please select your preferred app language:\nلطفاً زبان برنامه را انتخاب کنید:")
                            Button(onClick = { holder.pickFirstLaunchLanguage(AppLanguage.PERSIAN) }, modifier = Modifier.fillMaxWidth()) {
                                Text("🇮🇷  فارسی (Persian)")
                            }
                            OutlinedButton(onClick = { holder.pickFirstLaunchLanguage(AppLanguage.ENGLISH) }, modifier = Modifier.fillMaxWidth()) {
                                Text("🇺🇸  English")
                            }
                        }
                    },
                    confirmButton = {},
                )
            } else if (!app.hasPromptedTelegramJoin) {
                val url = app.telegramUrl.ifBlank { "https://t.me/otaqe_pni" }
                AlertDialog(
                    onDismissRequest = { holder.markTelegramPromptSeen() },
                    title = { Text(dtr("Join pnifox Telegram", "عضویت در کانال تلگرام pnifox"), fontWeight = FontWeight.Bold) },
                    text = {
                        Text(
                            dtr(
                                "Join $url for latest Windows & Android updates, configs, and guides.",
                                "برای دریافت آخرین آپدیت‌های ویندوز و اندروید و کانفیگ‌های جدید در کانال ما عضو شوید:\n$url",
                            ),
                        )
                    },
                    confirmButton = {
                        Button(onClick = {
                            holder.markTelegramPromptSeen()
                            holder.openTelegramChannel()
                        }) { Text(dtr("Join Channel", "عضویت در کانال")) }
                    },
                    dismissButton = {
                        OutlinedButton(onClick = { holder.markTelegramPromptSeen() }) {
                            Text(dtr("OK", "اوکی"))
                        }
                    },
                )
            } else if (state.startupUpdateModal != null) {
                val upd = state.startupUpdateModal!!
                AlertDialog(
                    onDismissRequest = { holder.dismissStartupUpdate(null) },
                    title = { Text(dtr("Update Available (${upd.latestTag})", "نسخه جدید منتشر شد (${upd.latestTag})")) },
                    text = { Text("${upd.releaseName}\n${upd.changelog.take(350)}") },
                    confirmButton = {
                        Button(onClick = {
                            holder.dismissStartupUpdate(null)
                            holder.openExternal(upd.releaseUrl)
                        }) { Text(dtr("Download", "دانلود")) }
                    },
                    dismissButton = {
                        TextButton(onClick = { holder.dismissStartupUpdate(upd.latestTag) }) { Text(dtr("Later", "بعداً")) }
                    },
                )
            }
        }
    }
}
