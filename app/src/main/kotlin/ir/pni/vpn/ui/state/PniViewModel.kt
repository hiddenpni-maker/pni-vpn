package ir.pni.vpn.ui.state

import android.app.Application
import android.content.Intent
import android.os.Build
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import ir.pni.core.pnixray.ScanCallback
import ir.pni.vpn.core.CloudflareDeployer
import ir.pni.vpn.core.CoreBridge
import ir.pni.vpn.data.model.AppSettings
import ir.pni.vpn.data.model.CdnProvider
import ir.pni.vpn.data.model.ConnectionState
import ir.pni.vpn.data.model.DiagnosticReport
import ir.pni.vpn.data.model.GuideSection
import ir.pni.vpn.data.model.NetworkType
import ir.pni.vpn.data.model.ProxyProfile
import ir.pni.vpn.data.model.ScanOptions
import ir.pni.vpn.data.model.ScanResultItem
import ir.pni.vpn.data.preset.IranNetworkPresets
import ir.pni.vpn.data.repository.PniRepository
import ir.pni.vpn.parser.ConfigEncryptor
import ir.pni.vpn.parser.ShareLinkParser
import ir.pni.vpn.parser.SpoofConverter
import ir.pni.vpn.service.NetworkDoctor
import ir.pni.vpn.service.PniVpnService
import ir.pni.vpn.service.SniSpoofService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject

data class ScannerUiState(
    val options: ScanOptions = ScanOptions(),
    val running: Boolean = false,
    val phase: Int = 0,
    val tested: Long = 0,
    val healthy: Long = 0,
    val failed: Long = 0,
    val inFlight: Long = 0,
    val results: List<ScanResultItem> = emptyList(),
    val infoLog: String = "",
    val exportedText: String = ""
)

class PniViewModel(application: Application) : AndroidViewModel(application) {

    private val repo = PniRepository(application)
    private val json = Json { ignoreUnknownKeys = true }

    val profiles: StateFlow<List<ProxyProfile>> = repo.profilesFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val settings: StateFlow<AppSettings> = repo.settingsFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), AppSettings())

    val connectionState: StateFlow<ConnectionState> = PniVpnService.state
    val statusMessage: StateFlow<String> = PniVpnService.statusMessage
    val txBps: StateFlow<Long> = PniVpnService.txBytesPerSec
    val rxBps: StateFlow<Long> = PniVpnService.rxBytesPerSec
    val spoofRunning: StateFlow<Boolean> = SniSpoofService.running

    private val _scanner = MutableStateFlow(ScannerUiState())
    val scanner: StateFlow<ScannerUiState> = _scanner.asStateFlow()

    private val _guides = MutableStateFlow<List<GuideSection>>(emptyList())
    val guides: StateFlow<List<GuideSection>> = _guides.asStateFlow()

    private val _diagnostic = MutableStateFlow<DiagnosticReport?>(null)
    val diagnostic: StateFlow<DiagnosticReport?> = _diagnostic.asStateFlow()

    private val _diagnosticLoading = MutableStateFlow(false)
    val diagnosticLoading: StateFlow<Boolean> = _diagnosticLoading.asStateFlow()

    private val _toastMessage = MutableStateFlow<String?>(null)
    val toastMessage: StateFlow<String?> = _toastMessage.asStateFlow()

    private val _cfAccounts = MutableStateFlow<List<CloudflareDeployer.CfAccount>>(emptyList())
    val cfAccounts: StateFlow<List<CloudflareDeployer.CfAccount>> = _cfAccounts.asStateFlow()

    private val _cfBusy = MutableStateFlow(false)
    val cfBusy: StateFlow<Boolean> = _cfBusy.asStateFlow()

    private val _domainLookupOutput = MutableStateFlow("")
    val domainLookupOutput: StateFlow<String> = _domainLookupOutput.asStateFlow()

    private val _dnsProbeOutput = MutableStateFlow("")
    val dnsProbeOutput: StateFlow<String> = _dnsProbeOutput.asStateFlow()

    private val _delayMs = MutableStateFlow<Long?>(null)
    val delayMs: StateFlow<Long?> = _delayMs.asStateFlow()

    init {
        viewModelScope.launch(Dispatchers.IO) {
            _guides.value = repo.loadGuides()
            val cur = repo.settingsFlow.first()
            val preset = IranNetworkPresets.get(cur.activeNetworkType)
            _scanner.value = _scanner.value.copy(
                options = _scanner.value.options.copy(
                    provider = preset.preferredCdn,
                    sni = preset.preferredCdn.defaultSni,
                    gentle = preset.scannerGentle,
                    antiDpi = preset.scannerAntiDpi
                )
            )
        }
    }

    fun consumeToast() {
        _toastMessage.value = null
    }

    fun showToast(msg: String) {
        _toastMessage.value = msg
    }

    fun selectNetworkType(type: NetworkType) {
        val preset = IranNetworkPresets.get(type)
        viewModelScope.launch {
            repo.updateSettings {
                it.copy(
                    activeNetworkType = type,
                    mtu = preset.mtu,
                    dnsMode = preset.dnsPrimary,
                    fallbackDns = preset.dnsSecondary,
                    ipv6Enabled = preset.preferIpv6
                )
            }
            _scanner.value = _scanner.value.copy(
                options = _scanner.value.options.copy(
                    provider = preset.preferredCdn,
                    sni = preset.preferredCdn.defaultSni,
                    gentle = preset.scannerGentle,
                    antiDpi = preset.scannerAntiDpi
                )
            )
            showToast("تنظیمات بهینه برای ${type.titleFa} اعمال شد")
        }
    }

    fun selectProfile(profileId: String) {
        viewModelScope.launch {
            repo.updateSettings { it.copy(activeProfileId = profileId) }
        }
    }

    fun connectOrDisconnect(selectedProfileId: String? = null) {
        val ctx = getApplication<Application>()
        val cur = connectionState.value
        if (cur == ConnectionState.CONNECTED || cur == ConnectionState.CONNECTING) {
            ctx.startService(Intent(ctx, PniVpnService::class.java).apply {
                action = PniVpnService.ACTION_STOP
            })
            return
        }
        val intent = Intent(ctx, PniVpnService::class.java).apply {
            action = PniVpnService.ACTION_START
            putExtra(PniVpnService.EXTRA_PROFILE_ID, selectedProfileId ?: settings.value.activeProfileId)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            ctx.startForegroundService(intent)
        } else {
            ctx.startService(intent)
        }
    }

    fun measureRealDelay() {
        viewModelScope.launch {
            val ms = CoreBridge.measureDelay()
            _delayMs.value = ms
            showToast(if (ms >= 0) "تأخیر واقعی تونل: ${ms}ms" else "تونل پاسخ نداد یا متصل نیست")
        }
    }

    fun importLinks(raw: String) {
        viewModelScope.launch {
            val parsed = ShareLinkParser.parseMany(raw)
            if (parsed.isEmpty()) {
                showToast("هیچ لینک یا کانفیگ معتبری شناسایی نشد")
                return@launch
            }
            val current = profiles.value
            val updated = parsed + current
            repo.saveProfiles(updated)
            repo.updateSettings { it.copy(activeProfileId = parsed.first().id) }
            showToast("${parsed.size} پروفایل با موفقیت اضافه شد")
        }
    }

    fun upsertProfile(profile: ProxyProfile) {
        viewModelScope.launch {
            val current = profiles.value.toMutableList()
            val idx = current.indexOfFirst { it.id == profile.id }
            if (idx >= 0) current[idx] = profile else current.add(0, profile)
            repo.saveProfiles(current)
            showToast("پروفایل ذخیره شد")
        }
    }

    fun deleteProfile(id: String) {
        viewModelScope.launch {
            val updated = profiles.value.filterNot { it.id == id }
            repo.saveProfiles(updated)
        }
    }

    fun applyIspFragmentToProfile(profile: ProxyProfile) {
        val preset = IranNetworkPresets.get(settings.value.activeNetworkType)
        upsertProfile(
            profile.copy(
                fragmentEnabled = preset.fragmentEnabled,
                fragmentPackets = preset.fragmentPackets,
                fragmentLength = preset.fragmentLength,
                fragmentInterval = preset.fragmentInterval,
                noiseEnabled = preset.noiseEnabled
            )
        )
    }

    fun convertProfileToSpoof(profile: ProxyProfile) {
        val s = settings.value
        val converted = SpoofConverter.convertToLocalSpoof(profile, s.localSniSpoofPort, s.spoofFakeSni)
        if (converted == null) {
            showToast("فقط VLESS / Trojan / VMess قابل تبدیل به SNI Spoof هستند")
            return
        }
        viewModelScope.launch {
            val updated = listOf(converted.profile) + profiles.value
            repo.saveProfiles(updated)
            repo.updateSettings { it.copy(activeProfileId = converted.profile.id) }
            showToast("پروفایل SNI Spoof ساخته و فعال شد")
        }
    }

    fun encryptProfile(profile: ProxyProfile, passphrase: String): String {
        val uri = ShareLinkParser.toShareLink(profile)
        return ConfigEncryptor.encrypt(uri, passphrase)
    }

    fun toggleSniSpoofService(targetAddr: String, fakeSni: String, fragLen: Int) {
        val ctx = getApplication<Application>()
        if (spoofRunning.value) {
            ctx.startService(Intent(ctx, SniSpoofService::class.java).apply {
                action = SniSpoofService.ACTION_STOP
            })
            return
        }
        if (targetAddr.isBlank()) {
            showToast("آدرس سرور هدف (IP:Port) را وارد کنید")
            return
        }
        val intent = Intent(ctx, SniSpoofService::class.java).apply {
            action = SniSpoofService.ACTION_START
            putExtra(SniSpoofService.EXTRA_LISTEN, "127.0.0.1:${settings.value.localSniSpoofPort}")
            putExtra(SniSpoofService.EXTRA_TARGET, targetAddr.trim())
            putExtra(SniSpoofService.EXTRA_FAKE_SNI, fakeSni.trim())
            putExtra(SniSpoofService.EXTRA_METHOD, "fragment+sni")
            putExtra(SniSpoofService.EXTRA_FRAG_LEN, fragLen.coerceIn(5, 256))
            putExtra(SniSpoofService.EXTRA_FRAG_DELAY, 2)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            ctx.startForegroundService(intent)
        } else {
            ctx.startService(intent)
        }
    }

    fun updateScanOptions(transform: (ScanOptions) -> ScanOptions) {
        _scanner.value = _scanner.value.copy(options = transform(_scanner.value.options))
    }

    fun selectScanProvider(provider: CdnProvider) {
        _scanner.value = _scanner.value.copy(
            options = _scanner.value.options.copy(
                provider = provider,
                sni = provider.defaultSni
            )
        )
    }

    fun startScan() {
        if (_scanner.value.running) return
        val opts = _scanner.value.options
        viewModelScope.launch(Dispatchers.IO) {
            val cidrs = if (opts.provider.assetFile.isNotBlank() && opts.targetsText.isBlank()) {
                repo.loadCdnCidrs(opts.provider.assetFile)
            } else {
                emptyList()
            }
            val ports = opts.portsCsv.split(",", " ").mapNotNull { it.trim().toIntOrNull() }
                .ifEmpty { listOf(443) }
            val lengths = opts.antiDpiLengths.split("/", ",", " ").mapNotNull { it.trim().toIntOrNull() }
                .filter { it > 0 }.ifEmpty { listOf(1, 104, 1) }

            val configJson = buildJsonObject {
                put("provider", opts.provider.id)
                putJsonArray("cidrs") { cidrs.forEach { add(kotlinx.serialization.json.JsonPrimitive(it)) } }
                put("targetsText", opts.targetsText)
                put("count", opts.count)
                putJsonArray("ports") { ports.forEach { add(kotlinx.serialization.json.JsonPrimitive(it)) } }
                put("workers", opts.workers)
                put("timeoutMs", opts.timeoutMs)
                put("attempts", opts.attempts)
                put("sni", opts.sni)
                put("requireWs", opts.requireWs)
                put("wsPath", opts.wsPath)
                put("neighborScan", opts.neighborScan)
                put("gentle", opts.gentle)
                put("skipPhase1", opts.skipPhase1)
                put("shareLink", opts.shareLinkForPhase2)
                put("phase2TopN", opts.phase2TopN)
                put("dohResolver", "https://dns.google/resolve")
                putJsonObject("antiDpi") {
                    put("enabled", opts.antiDpi)
                    putJsonArray("lengths") { lengths.forEach { add(kotlinx.serialization.json.JsonPrimitive(it)) } }
                    put("delayMs", opts.antiDpiDelayMs)
                    put("maxSplit", 11)
                    put("unsafeFp", true)
                }
            }.toString()

            _scanner.value = _scanner.value.copy(
                running = true,
                phase = 1,
                tested = 0,
                healthy = 0,
                failed = 0,
                results = emptyList(),
                infoLog = "شروع اسکن ${opts.provider.titleFa}..."
            )

            val res = CoreBridge.startScan(configJson, object : ScanCallback {
                override fun onProgress(tested: Long, healthy: Long, failed: Long, inFlight: Long, phase: Int) {
                    _scanner.value = _scanner.value.copy(
                        tested = tested,
                        healthy = healthy,
                        failed = failed,
                        inFlight = inFlight,
                        phase = phase
                    )
                }

                override fun onResult(resultJSON: String) {
                    val item = runCatching { json.decodeFromString<ScanResultItem>(resultJSON) }.getOrNull() ?: return
                    if (!item.healthy && _scanner.value.results.size > 150) return
                    val cur = _scanner.value.results.toMutableList()
                    val existingIdx = cur.indexOfFirst { it.ip == item.ip && it.port == item.port }
                    if (existingIdx >= 0) cur[existingIdx] = item else cur.add(item)
                    cur.sortWith(
                        compareByDescending<ScanResultItem> { it.healthy }
                            .thenByDescending { it.phase2Ok }
                            .thenByDescending { it.score }
                    )
                    _scanner.value = _scanner.value.copy(results = cur)
                }

                override fun onInfo(message: String) {
                    _scanner.value = _scanner.value.copy(infoLog = message)
                }

                override fun onFinished(summaryJSON: String) {
                    _scanner.value = _scanner.value.copy(
                        running = false,
                        phase = 0,
                        infoLog = "اسکن تکمیل شد (${_scanner.value.healthy} آیپی سالم)"
                    )
                }

                override fun onError(err: String) {
                    _scanner.value = _scanner.value.copy(
                        running = false,
                        phase = 0,
                        infoLog = "خطا: $err"
                    )
                }
            })

            if (res.isFailure) {
                _scanner.value = _scanner.value.copy(
                    running = false,
                    infoLog = res.exceptionOrNull()?.message ?: "اسکنر بومی pni-core.aar یافت نشد"
                )
            }
        }
    }

    fun stopScan() {
        CoreBridge.stopScan()
        _scanner.value = _scanner.value.copy(running = false, infoLog = "اسکن متوقف شد")
    }

    fun applyScannedIpToActiveProfile(item: ScanResultItem) {
        val activeId = settings.value.activeProfileId
        val active = profiles.value.firstOrNull { it.id == activeId } ?: profiles.value.firstOrNull()
        if (active == null) {
            showToast("ابتدا یک پروفایل در لیست ایجاد کنید")
            return
        }
        val updated = active.copy(
            server = item.ip,
            port = item.port,
            sni = active.sni.ifBlank { active.host.ifBlank { active.server } },
            host = active.host.ifBlank { active.sni.ifBlank { active.server } },
            latencyMs = item.latencyMs
        )
        upsertProfile(updated)
        showToast("آیپی ${item.ip}:${item.port} روی «${active.name}» اعمال شد")
    }

    fun exportScanResults(format: String) {
        val resultsJson = json.encodeToString(_scanner.value.results)
        val baseLink = _scanner.value.options.shareLinkForPhase2.ifBlank {
            profiles.value.firstOrNull { it.id == settings.value.activeProfileId }?.let {
                ShareLinkParser.toShareLink(it)
            }.orEmpty()
        }
        val exported = CoreBridge.exportScanResults(resultsJson, format, baseLink)
        _scanner.value = _scanner.value.copy(exportedText = exported)
    }

    fun lookupDomain(domain: String) {
        viewModelScope.launch {
            _domainLookupOutput.value = CoreBridge.lookupDomainIps(domain.trim())
        }
    }

    fun saveDnsTunnelProfile(
        name: String,
        transport: String,
        resolver: String,
        domain: String,
        pubkey: String,
        innerMode: String,
        innerUser: String,
        innerPass: String,
        utls: String
    ) {
        if (domain.isBlank() || pubkey.isBlank()) {
            showToast("دامنه Nameserver و کلید عمومی (PubKey) الزامی هستند")
            return
        }
        val cleanDomain = domain.trim()
        val profile = ProxyProfile(
            id = java.util.UUID.randomUUID().toString(),
            name = name.ifBlank { "DNS-$cleanDomain" },
            protocol = ir.pni.vpn.data.model.ProtocolType.DNS_TUNNEL,
            server = cleanDomain,
            port = 53,
            dnsTransport = transport,
            dnsResolver = resolver.trim(),
            dnsDomain = cleanDomain,
            dnsPubkey = pubkey.trim(),
            dnsInnerMode = innerMode,
            dnsInnerUser = innerUser.trim(),
            dnsInnerPass = innerPass,
            dnsUtls = utls.trim(),
            group = "DNS Tunnel (AM Mode)"
        )
        val withLink = profile.copy(rawShareLink = ShareLinkParser.toShareLink(profile))
        viewModelScope.launch {
            val updated = listOf(withLink) + profiles.value
            repo.saveProfiles(updated)
            repo.updateSettings { it.copy(activeProfileId = withLink.id) }
            showToast("پروفایل تونل DNS ذخیره و فعال شد")
        }
    }

    fun testDnsResolver(resolverInput: String, domainInput: String) {
        viewModelScope.launch(Dispatchers.IO) {
            _dnsProbeOutput.value = "در حال تست پورت 53 / TXT روی $resolverInput ..."
            val hostPort = resolverInput.trim().removePrefix("https://").substringBefore("/")
            val host = hostPort.substringBefore(":")
            val port = hostPort.substringAfter(":", "53").toIntOrNull() ?: 53
            val targetDomain = domainInput.trim().ifBlank { "google.com" }

            val resultMsg = runCatching {
                val txPacket = buildDnsTxtQueryPacket(targetDomain)
                val start = System.currentTimeMillis()
                java.net.DatagramSocket().use { sock ->
                    sock.soTimeout = 3500
                    val addr = java.net.InetAddress.getByName(host)
                    sock.send(java.net.DatagramPacket(txPacket, txPacket.size, addr, port))
                    val buf = ByteArray(512)
                    val rx = java.net.DatagramPacket(buf, buf.size)
                    sock.receive(rx)
                    val ms = System.currentTimeMillis() - start
                    val rcode = buf[3].toInt() and 0x0F
                    "پاسخ DNS دریافت شد (${ms}ms • RCODE=$rcode • اندازه ${rx.length} بایت) — ریسالور فعال است"
                }
            }.getOrElse { err ->
                "عدم پاسخ روی UDP $host:$port (${err.message}) — روی برخی خطوط فقط DNS داخلی اپراتور یا DoH باز است"
            }
            _dnsProbeOutput.value = resultMsg
        }
    }

    private fun buildDnsTxtQueryPacket(domain: String): ByteArray {
        val out = java.io.ByteArrayOutputStream()
        // Header: ID=0x504E, Flags=0x0100 (RD=1), QDCOUNT=1
        out.write(byteArrayOf(0x50, 0x4E, 0x01, 0x00, 0x00, 0x01, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00))
        for (label in domain.trim('.').split('.')) {
            val b = label.toByteArray(Charsets.UTF_8)
            out.write(b.size.coerceAtMost(63))
            out.write(b, 0, b.size.coerceAtMost(63))
        }
        out.write(0) // root label
        out.write(byteArrayOf(0x00, 0x10, 0x00, 0x01)) // QTYPE=TXT(16), QCLASS=IN(1)
        return out.toByteArray()
    }

    fun fetchCfAccounts(token: String) {
        viewModelScope.launch {
            _cfBusy.value = true
            val res = CloudflareDeployer.listAccounts(token)
            _cfBusy.value = false
            res.onSuccess { list ->
                _cfAccounts.value = list
                if (list.isNotEmpty()) {
                    repo.updateSettings { it.copy(cfApiToken = token, cfAccountId = list.first().id) }
                    showToast("${list.size} اکانت کلودفلر یافت شد")
                } else {
                    showToast("اکانتی در این توکن یافت نشد")
                }
            }.onFailure {
                showToast(it.message ?: "خطا در ارتباط با Cloudflare")
            }
        }
    }

    fun deployCfWorker(
        token: String,
        accountId: String,
        workerName: String,
        uuid: String,
        proxyIpsCsv: String,
        fallbackUrl: String
    ) {
        viewModelScope.launch {
            _cfBusy.value = true
            val pips = proxyIpsCsv.split(",", "\n").map { it.trim() }.filter { it.isNotBlank() }
            val res = CloudflareDeployer.deployWorker(
                context = getApplication(),
                rawToken = token,
                accountId = accountId,
                workerName = workerName,
                uuidInput = uuid,
                proxyIps = pips,
                fallbackUrl = fallbackUrl
            )
            _cfBusy.value = false
            res.onSuccess { deployed ->
                repo.updateSettings {
                    it.copy(
                        cfApiToken = token,
                        cfAccountId = accountId,
                        cfWorkerName = workerName,
                        cfWorkerUuid = deployed.uuid,
                        cfProxyIps = proxyIpsCsv,
                        cfFallbackUrl = fallbackUrl
                    )
                }
                importLinks(deployed.shareLink)
                showToast("Worker با موفقیت نصب شد: ${deployed.workerUrl}")
            }.onFailure {
                showToast(it.message ?: "خطا در نصب Worker")
            }
        }
    }

    fun runNetworkDoctor() {
        viewModelScope.launch {
            _diagnosticLoading.value = true
            _diagnostic.value = NetworkDoctor.diagnose(getApplication(), settings.value.activeNetworkType)
            _diagnosticLoading.value = false
        }
    }

    fun updateSettings(transform: (AppSettings) -> AppSettings) {
        viewModelScope.launch { repo.updateSettings(transform) }
    }
}
