package ir.pni.vpn

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.VpnService
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Build
import androidx.compose.material.icons.rounded.MenuBook
import androidx.compose.material.icons.rounded.PowerSettingsNew
import androidx.compose.material.icons.rounded.Radar
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.VpnKey
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import androidx.core.content.ContextCompat
import com.journeyapps.barcodescanner.ScanContract
import com.journeyapps.barcodescanner.ScanOptions as ZxingScanOptions
import ir.pni.vpn.data.model.ConnectionState
import ir.pni.vpn.ui.screens.GuideScreen
import ir.pni.vpn.ui.screens.HomeScreen
import ir.pni.vpn.ui.screens.ProfilesScreen
import ir.pni.vpn.ui.screens.ScannerScreen
import ir.pni.vpn.ui.screens.SettingsScreen
import ir.pni.vpn.ui.screens.ToolsScreen
import ir.pni.vpn.ui.state.PniViewModel
import ir.pni.vpn.ui.theme.PniTheme

class MainActivity : ComponentActivity() {

    private val vm: PniViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        requestNotificationPermissionIfNeeded()
        handleIncomingUri(intent)

        setContent {
            PniTheme {
                CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
                    PniApp(vm = vm, onRequestVpnPermission = { launchFn ->
                        val prep = VpnService.prepare(this)
                        if (prep == null) {
                            vm.connectOrDisconnect()
                        } else {
                            launchFn(prep)
                        }
                    })
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleIncomingUri(intent)
    }

    private fun handleIncomingUri(intent: Intent?) {
        val raw = intent?.dataString ?: return
        if (raw.contains("://")) {
            vm.importLinks(raw)
        }
    }

    private fun requestNotificationPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 101)
        }
    }
}

private enum class PniTab(val labelFa: String) {
    HOME("اتصال"),
    PROFILES("پروفایل‌ها"),
    SCANNER("اسکنر"),
    TOOLS("ابزارها"),
    GUIDES("راهنما")
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PniApp(
    vm: PniViewModel,
    onRequestVpnPermission: ((Intent) -> Unit) -> Unit
) {
    var activeTab by remember { mutableStateOf(PniTab.HOME) }
    var showSettings by remember { mutableStateOf(false) }

    val profiles by vm.profiles.collectAsState()
    val settings by vm.settings.collectAsState()
    val connState by vm.connectionState.collectAsState()
    val statusMessage by vm.statusMessage.collectAsState()
    val txBps by vm.txBps.collectAsState()
    val rxBps by vm.rxBps.collectAsState()
    val delayMs by vm.delayMs.collectAsState()
    val scannerState by vm.scanner.collectAsState()
    val guides by vm.guides.collectAsState()
    val diagnostic by vm.diagnostic.collectAsState()
    val diagnosticLoading by vm.diagnosticLoading.collectAsState()
    val spoofRunning by vm.spoofRunning.collectAsState()
    val cfAccounts by vm.cfAccounts.collectAsState()
    val cfBusy by vm.cfBusy.collectAsState()
    val domainLookup by vm.domainLookupOutput.collectAsState()
    val dnsProbe by vm.dnsProbeOutput.collectAsState()
    val toastMsg by vm.toastMessage.collectAsState()

    val snackbarHost = remember { SnackbarHostState() }
    LaunchedEffect(toastMsg) {
        toastMsg?.let {
            snackbarHost.showSnackbar(it)
            vm.consumeToast()
        }
    }

    val vpnPermLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { res ->
        if (res.resultCode == ComponentActivity.RESULT_OK) {
            vm.connectOrDisconnect()
        } else {
            vm.showToast("مجوز VPN توسط سیستم تایید نشد")
        }
    }

    val qrLauncher = rememberLauncherForActivityResult(ScanContract()) { result ->
        result.contents?.let { vm.importLinks(it) }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHost) },
        topBar = {
            TopAppBar(
                title = { Text(if (showSettings) "تنظیمات PNI" else "PNI — دسترسی آزاد و هوشمند") },
                navigationIcon = {
                    if (showSettings) {
                        IconButton(onClick = { showSettings = false }) {
                            Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Back")
                        }
                    }
                },
                actions = {
                    if (!showSettings) {
                        IconButton(onClick = { showSettings = true }) {
                            Icon(Icons.Rounded.Settings, "Settings")
                        }
                    }
                }
            )
        },
        bottomBar = {
            if (!showSettings) {
                NavigationBar {
                    PniTab.entries.forEach { tab ->
                        val icon = when (tab) {
                            PniTab.HOME -> Icons.Rounded.PowerSettingsNew
                            PniTab.PROFILES -> Icons.Rounded.VpnKey
                            PniTab.SCANNER -> Icons.Rounded.Radar
                            PniTab.TOOLS -> Icons.Rounded.Build
                            PniTab.GUIDES -> Icons.Rounded.MenuBook
                        }
                        NavigationBarItem(
                            selected = activeTab == tab,
                            onClick = { activeTab = tab },
                            icon = { Icon(icon, tab.labelFa) },
                            label = { Text(tab.labelFa) }
                        )
                    }
                }
            }
        }
    ) { padding ->
        androidx.compose.foundation.layout.Box(Modifier.padding(padding)) {
            if (showSettings) {
                SettingsScreen(
                    settings = settings,
                    onUpdateSettings = vm::updateSettings
                )
            } else {
                when (activeTab) {
                    PniTab.HOME -> HomeScreen(
                        connectionState = connState,
                        statusMessage = statusMessage,
                        txBps = txBps,
                        rxBps = rxBps,
                        delayMs = delayMs,
                        settings = settings,
                        profiles = profiles,
                        onToggleConnect = {
                            if (connState == ConnectionState.CONNECTED || connState == ConnectionState.CONNECTING) {
                                vm.connectOrDisconnect()
                            } else {
                                onRequestVpnPermission { vpnPermLauncher.launch(it) }
                            }
                        },
                        onSelectNetwork = vm::selectNetworkType,
                        onSelectProfile = vm::selectProfile,
                        onMeasureDelay = vm::measureRealDelay,
                        onOpenScanner = { activeTab = PniTab.SCANNER },
                        onOpenDoctor = {
                            activeTab = PniTab.GUIDES
                            vm.runNetworkDoctor()
                        }
                    )
                    PniTab.PROFILES -> ProfilesScreen(
                        profiles = profiles,
                        activeProfileId = settings.activeProfileId,
                        onSelect = vm::selectProfile,
                        onImportRaw = vm::importLinks,
                        onScanQr = {
                            qrLauncher.launch(ZxingScanOptions().apply {
                                setPrompt("اسکن QR کد کانفیگ PNI")
                                setBeepEnabled(false)
                            })
                        },
                        onSaveProfile = vm::upsertProfile,
                        onDeleteProfile = vm::deleteProfile,
                        onApplyIspTuning = vm::applyIspFragmentToProfile,
                        onConvertSpoof = vm::convertProfileToSpoof,
                        onEncryptProfile = vm::encryptProfile,
                        onShowMessage = vm::showToast
                    )
                    PniTab.SCANNER -> ScannerScreen(
                        state = scannerState,
                        onSelectProvider = vm::selectScanProvider,
                        onUpdateOptions = vm::updateScanOptions,
                        onStartScan = vm::startScan,
                        onStopScan = vm::stopScan,
                        onApplyIp = vm::applyScannedIpToActiveProfile,
                        onExport = vm::exportScanResults,
                        onShowMessage = vm::showToast
                    )
                    PniTab.TOOLS -> ToolsScreen(
                        settings = settings,
                        spoofRunning = spoofRunning,
                        cfAccounts = cfAccounts,
                        cfBusy = cfBusy,
                        domainLookupOutput = domainLookup,
                        dnsProbeOutput = dnsProbe,
                        onFetchCfAccounts = vm::fetchCfAccounts,
                        onDeployCfWorker = vm::deployCfWorker,
                        onToggleSpoof = vm::toggleSniSpoofService,
                        onLookupDomain = vm::lookupDomain,
                        onTestDnsResolver = vm::testDnsResolver,
                        onSaveDnsTunnel = vm::saveDnsTunnelProfile,
                        onShowMessage = vm::showToast
                    )
                    PniTab.GUIDES -> GuideScreen(
                        guides = guides,
                        diagnostic = diagnostic,
                        diagnosticLoading = diagnosticLoading,
                        onRunDiagnostic = vm::runNetworkDoctor
                    )
                }
            }
        }
    }
}
