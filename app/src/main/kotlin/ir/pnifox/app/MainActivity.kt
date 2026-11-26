package ir.pnifox.app

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.unit.sp
import ir.pnifox.core.model.UpdateCheckState
import ir.pnifox.core.network.AppUpdateChecker
import ir.pnifox.feature.mhr.MhrRoute
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import dagger.hilt.android.AndroidEntryPoint
import ir.pnifox.core.database.PnifoxDataRepository
import ir.pnifox.core.database.PreferencesRepository
import ir.pnifox.core.model.AppLanguage
import ir.pnifox.core.model.AppSettings
import ir.pnifox.core.network.ShareLinkParser
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.BarChart
import androidx.compose.material.icons.rounded.Bolt
import androidx.compose.material.icons.rounded.Dns
import androidx.compose.material.icons.rounded.Radar
import androidx.compose.material.icons.rounded.Storage
import ir.pnifox.core.ui.PnifoxTheme
import ir.pnifox.core.ui.R as UiR
import ir.pnifox.core.ui.tr
import ir.pnifox.feature.dns.DnsRoute
import ir.pnifox.feature.home.HomeRoute
import ir.pnifox.feature.scanner.ScannerRoute
import ir.pnifox.feature.servers.ServersRoute
import ir.pnifox.feature.settings.SettingsRoute
import ir.pnifox.feature.stats.StatsRoute
import kotlinx.coroutines.launch
import javax.inject.Inject

private enum class BottomTab(
    val route: String,
    val labelEn: String,
    val labelFa: String,
    val icon: ImageVector,
) {
    AETHER("aether", "AETHER", "وارپ", Icons.Rounded.Bolt),
    SERVERS("servers", "Servers", "سرورها", Icons.Rounded.Storage),
    MHR("mhr", "MHR", "MHR", Icons.Rounded.Shield),
    DNS("dns", "DNS", "DNS", Icons.Rounded.Dns),
    SCANNER("scanner", "Scan", "اسکنر", Icons.Rounded.Radar),
    STATS("stats", "Stats", "آمار", Icons.Rounded.BarChart),
}

private const val ROUTE_SETTINGS = "settings"

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject lateinit var preferencesRepo: PreferencesRepository
    @Inject lateinit var dataRepo: PnifoxDataRepository

    private val notifPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { /* handled gracefully; PniVpnService posts speed updates when granted */ }

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        handleIncomingShareIntent(intent)
        requestNotificationPermissionIfNeeded()

        setContent {
            val loadedSettings by preferencesRepo.appSettingsFlow.collectAsStateWithLifecycle(initialValue = null)
            val appSettings = loadedSettings ?: AppSettings()
            val showLanguageDialog = loadedSettings?.hasSelectedLanguage == false
            val showTelegramJoinDialog = loadedSettings != null &&
                loadedSettings?.hasSelectedLanguage == true &&
                loadedSettings?.hasPromptedTelegramJoin == false
            var availableUpdate by remember { mutableStateOf<ir.pnifox.core.model.AppUpdateInfo?>(null) }

            val installedVersion = remember {
                runCatching { packageManager.getPackageInfo(packageName, 0).versionName ?: "2.0.0" }.getOrDefault("2.0.0")
            }

            LaunchedEffect(loadedSettings?.autoCheckUpdates) {
                if (loadedSettings?.autoCheckUpdates == true) {
                    val state = AppUpdateChecker().checkLatestRelease(installedVersion)
                    if (state is UpdateCheckState.UpdateAvailable &&
                        state.info.latestTag != loadedSettings?.lastDismissedUpdateTag
                    ) {
                        availableUpdate = state.info
                    }
                }
            }

            PnifoxTheme(
                language = appSettings.language,
                themeMode = appSettings.themeMode,
                accentColor = appSettings.accentColor,
            ) {
                PnifoxRootScreen(language = appSettings.language)

                if (showLanguageDialog) {
                    FirstLaunchLanguageDialog(
                        onPickLanguage = { lang ->
                            lifecycleScope.launch {
                                preferencesRepo.updateAppSettings {
                                    it.copy(language = lang, hasSelectedLanguage = true)
                                }
                            }
                        },
                    )
                } else if (showTelegramJoinDialog) {
                    FirstLaunchTelegramDialog(
                        channelUrl = appSettings.telegramUrl,
                        onOpenAndComplete = { openTelegramAndMarkJoined(appSettings.telegramUrl) },
                        onDismissOnly = { markTelegramPromptSeen() },
                    )
                } else if (availableUpdate != null) {
                    val upd = availableUpdate!!
                    AlertDialog(
                        onDismissRequest = { availableUpdate = null },
                        title = {
                            Text(
                                text = tr("Update Available (${upd.latestTag})", "نسخه جدید منتشر شد (${upd.latestTag})"),
                                fontWeight = FontWeight.Bold,
                            )
                        },
                        text = {
                            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                Text(
                                    text = tr(
                                        "Current: $installedVersion -> New: ${upd.versionName}",
                                        "نسخه فعلی: $installedVersion  •  نسخه جدید: ${upd.versionName}",
                                    ),
                                    fontWeight = FontWeight.SemiBold,
                                )
                                if (upd.changelog.isNotBlank()) {
                                    Text(text = upd.changelog.take(400))
                                }
                            }
                        },
                        confirmButton = {
                            Button(
                                onClick = {
                                    val url = upd.apkDownloadUrl ?: upd.releaseUrl
                                    availableUpdate = null
                                    runCatching { startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) }
                                },
                            ) {
                                Text(tr("Download Update", "دانلود آپدیت"))
                            }
                        },
                        dismissButton = {
                            TextButton(
                                onClick = {
                                    val tag = upd.latestTag
                                    availableUpdate = null
                                    lifecycleScope.launch {
                                        preferencesRepo.updateAppSettings { it.copy(lastDismissedUpdateTag = tag) }
                                    }
                                },
                            ) {
                                Text(tr("Later", "بعداً"))
                            }
                        },
                    )
                }
            }
        }
    }

    private fun markTelegramPromptSeen() {
        lifecycleScope.launch {
            preferencesRepo.updateAppSettings { it.copy(hasPromptedTelegramJoin = true) }
        }
    }

    private fun openTelegramAndMarkJoined(channelUrl: String) {
        markTelegramPromptSeen()
        val tgHandle = channelUrl.substringAfterLast('/').trim().removePrefix("@").ifBlank { "otaqe_pni" }
        val tgIntent = Intent(Intent.ACTION_VIEW, Uri.parse("tg://resolve?domain=$tgHandle"))
        val webIntent = Intent(Intent.ACTION_VIEW, Uri.parse(channelUrl.ifBlank { "https://t.me/otaqe_pni" }))
        runCatching { startActivity(tgIntent) }
            .onFailure { runCatching { startActivity(webIntent) } }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleIncomingShareIntent(intent)
    }

    private fun requestNotificationPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                runCatching { notifPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS) }
            }
        }
    }

    private fun handleIncomingShareIntent(intent: Intent?) {
        val rawUri = intent?.dataString ?: return
        val profiles = ShareLinkParser.parseBatch(rawUri)
        if (profiles.isEmpty()) return
        lifecycleScope.launch {
            dataRepo.saveProfiles(profiles)
        }
    }
}

@Composable
private fun PnifoxRootScreen(language: AppLanguage) {
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route
    val showBottomBar = currentRoute != ROUTE_SETTINGS

    val navigateToTab: (String) -> Unit = { route ->
        navController.navigate(route) {
            popUpTo(navController.graph.findStartDestination().id) {
                saveState = true
            }
            launchSingleTop = true
            restoreState = true
        }
    }

    Scaffold(
        modifier = Modifier
            .fillMaxSize()
            .imePadding(),
        contentWindowInsets = WindowInsets.safeDrawing,
        bottomBar = {
            if (showBottomBar) {
                NavigationBar {
                    val hierarchy = backStackEntry?.destination?.hierarchy
                    BottomTab.entries.forEach { tab ->
                        val selected = hierarchy?.any { it.route == tab.route } == true
                        val label = if (language == AppLanguage.PERSIAN) tab.labelFa else tab.labelEn
                        NavigationBarItem(
                            selected = selected,
                            onClick = { navigateToTab(tab.route) },
                            icon = { Icon(tab.icon, contentDescription = label, modifier = Modifier.size(21.dp)) },
                            label = { Text(text = label, fontSize = 10.sp, maxLines = 1, softWrap = false, overflow = TextOverflow.Ellipsis) },
                        )
                    }
                }
            }
        },
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = BottomTab.AETHER.route,
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
        ) {
            composable(BottomTab.AETHER.route) {
                HomeRoute(
                    onNavigateServers = { navigateToTab(BottomTab.SERVERS.route) },
                    onNavigateScanner = { navigateToTab(BottomTab.SCANNER.route) },
                    onNavigateDns = { navigateToTab(BottomTab.DNS.route) },
                    onNavigateSettings = { navController.navigate(ROUTE_SETTINGS) },
                )
            }
            composable(BottomTab.SERVERS.route) {
                ServersRoute(
                    onNavigateScanner = { navigateToTab(BottomTab.SCANNER.route) },
                    onNavigateDns = { navigateToTab(BottomTab.DNS.route) },
                )
            }
            composable(BottomTab.MHR.route) {
                MhrRoute()
            }
            composable(BottomTab.SCANNER.route) {
                ScannerRoute()
            }
            composable(BottomTab.DNS.route) {
                DnsRoute(onNavigateScanner = { navigateToTab(BottomTab.SCANNER.route) })
            }
            composable(BottomTab.STATS.route) {
                StatsRoute()
            }
            composable(ROUTE_SETTINGS) {
                SettingsRoute(onBack = { navController.popBackStack() })
            }
        }
    }
}

@Composable
private fun FirstLaunchTelegramDialog(
    channelUrl: String,
    onOpenAndComplete: () -> Unit,
    onDismissOnly: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismissOnly,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Image(
                    painter = painterResource(UiR.drawable.pnifox_makima_logo),
                    contentDescription = "pnifox",
                    modifier = Modifier.size(36.dp).clip(CircleShape),
                )
                Spacer(Modifier.width(10.dp))
                Text(
                    text = tr("Join pnifox Telegram", "عضویت در کانال تلگرام pnifox"),
                    fontWeight = FontWeight.Bold,
                )
            }
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    text = tr(
                        "Welcome to pnifox! Join our Telegram channel for latest app updates, MASQUE/VLESS configs, and guides:",
                        "به pnifox خوش آمدید! برای دریافت آخرین نسخه‌ها، کانفیگ‌های جدید (MASQUE / VLESS) و آموزش‌ها، در کانال تلگرام ما عضو شوید:",
                    ),
                )
                Text(
                    text = channelUrl.ifBlank { "https://t.me/otaqe_pni" },
                    fontWeight = FontWeight.Bold,
                )
            }
        },
        confirmButton = {
            Button(onClick = onOpenAndComplete) {
                Text(tr("Join Channel", "جوین در کانال"))
            }
        },
        dismissButton = {
            OutlinedButton(onClick = onDismissOnly) {
                Text(tr("OK", "اوکی"))
            }
        },
    )
}

@Composable
private fun FirstLaunchLanguageDialog(
    onPickLanguage: (AppLanguage) -> Unit,
) {
    AlertDialog(
        onDismissRequest = {},
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Image(
                    painter = painterResource(UiR.drawable.pnifox_makima_logo),
                    contentDescription = "pnifox",
                    modifier = Modifier.size(36.dp).clip(CircleShape),
                )
                Spacer(Modifier.width(10.dp))
                Column {
                    Text(
                        text = "Choose Language • انتخاب زبان",
                        fontWeight = FontWeight.Bold,
                    )
                }
            }
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("Please select your preferred app language:\nلطفاً زبان برنامه را انتخاب کنید:")
                Button(
                    onClick = { onPickLanguage(AppLanguage.PERSIAN) },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text("🇮🇷  فارسی (Persian)")
                }
                OutlinedButton(
                    onClick = { onPickLanguage(AppLanguage.ENGLISH) },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text("🇺🇸  English")
                }
            }
        },
        confirmButton = {},
    )
}
