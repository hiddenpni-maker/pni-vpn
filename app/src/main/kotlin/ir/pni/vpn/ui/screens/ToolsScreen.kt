package ir.pni.vpn.ui.screens

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CloudUpload
import androidx.compose.material.icons.rounded.Dns
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Router
import androidx.compose.material.icons.rounded.Security
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import ir.pni.vpn.core.CloudflareDeployer
import ir.pni.vpn.data.model.AppSettings
import ir.pni.vpn.parser.ConfigEncryptor
import ir.pni.vpn.ui.theme.PniAmber
import ir.pni.vpn.ui.theme.PniCyan
import ir.pni.vpn.ui.theme.PniEmerald

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ToolsScreen(
    settings: AppSettings,
    spoofRunning: Boolean,
    cfAccounts: List<CloudflareDeployer.CfAccount>,
    cfBusy: Boolean,
    domainLookupOutput: String,
    dnsProbeOutput: String,
    onFetchCfAccounts: (String) -> Unit,
    onDeployCfWorker: (token: String, accountId: String, name: String, uuid: String, pips: String, fb: String) -> Unit,
    onToggleSpoof: (targetAddr: String, fakeSni: String, fragLen: Int) -> Unit,
    onLookupDomain: (String) -> Unit,
    onTestDnsResolver: (resolver: String, domain: String) -> Unit,
    onSaveDnsTunnel: (name: String, transport: String, resolver: String, domain: String, pubkey: String, innerMode: String, innerUser: String, innerPass: String, utls: String) -> Unit,
    onShowMessage: (String) -> Unit
) {
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current

    var dnsName by remember { mutableStateOf("PNI SlowDNS") }
    var dnsTransport by remember { mutableStateOf("udp") }
    var dnsResolver by remember { mutableStateOf("8.8.8.8:53") }
    var dnsDomain by remember { mutableStateOf("") }
    var dnsPubkey by remember { mutableStateOf("") }
    var dnsInnerMode by remember { mutableStateOf("ssh") }
    var dnsInnerUser by remember { mutableStateOf("") }
    var dnsInnerPass by remember { mutableStateOf("") }

    var cfToken by remember(settings.cfApiToken) { mutableStateOf(settings.cfApiToken) }
    var cfAccountId by remember(settings.cfAccountId) { mutableStateOf(settings.cfAccountId) }
    var cfWorkerName by remember(settings.cfWorkerName) { mutableStateOf(settings.cfWorkerName) }
    var cfUuid by remember(settings.cfWorkerUuid) { mutableStateOf(settings.cfWorkerUuid) }
    var cfProxyIps by remember(settings.cfProxyIps) { mutableStateOf(settings.cfProxyIps) }
    var cfFallback by remember(settings.cfFallbackUrl) { mutableStateOf(settings.cfFallbackUrl) }

    var spoofTarget by remember { mutableStateOf("") }
    var spoofFakeSni by remember { mutableStateOf(settings.spoofFakeSni) }
    var spoofFragLen by remember { mutableStateOf(settings.spoofFragLen.toString()) }

    var domainQuery by remember { mutableStateOf("chatgpt.com") }

    var cryptoInput by remember { mutableStateOf("") }
    var cryptoPass by remember { mutableStateOf("") }
    var cryptoOutput by remember { mutableStateOf("") }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // 1. AM Tunnel Style DNS Tunnel (DNSTT / SlowDNS)
        Card(
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            shape = RoundedCornerShape(18.dp)
        ) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Icon(Icons.Rounded.Router, null, tint = PniAmber)
                    Text("اتصال تونل DNS (مشابه AM Tunnel / SlowDNS)", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                }
                Text(
                    text = "انتقال ترافیک از بستر بسته‌های DNS (UDP پورت ۵۳، DoH یا DoT) برای زمان‌هایی که تمام تونل‌های عادی فیلتر شده‌اند.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Text("نوع انتقال DNS:", style = MaterialTheme.typography.labelMedium)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf("udp" to "UDP (SlowDNS 53)", "doh" to "DoH (HTTPS)", "dot" to "DoT (TLS 853)").forEach { (id, label) ->
                        FilterChip(
                            selected = dnsTransport == id,
                            onClick = {
                                dnsTransport = id
                                dnsResolver = when (id) {
                                    "doh" -> "https://1.1.1.1/dns-query"
                                    "dot" -> "1.1.1.1:853"
                                    else -> "8.8.8.8:53"
                                }
                            },
                            label = { Text(label) }
                        )
                    }
                }

                if (dnsTransport == "udp") {
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        listOf("8.8.8.8:53", "1.1.1.1:53", "178.22.122.100:53", "185.51.200.2:53", "9.9.9.9:53").forEach { r ->
                            AssistChip(onClick = { dnsResolver = r }, label = { Text(r) })
                        }
                    }
                }

                OutlinedTextField(dnsName, { dnsName = it }, label = { Text("نام پروفایل") }, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(dnsResolver, { dnsResolver = it }, label = { Text("آدرس Resolver DNS") }, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(dnsDomain, { dnsDomain = it }, label = { Text("دامنه Nameserver (مثلاً ns1.yourdomain.com)") }, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(dnsPubkey, { dnsPubkey = it }, label = { Text("کلید عمومی سرور DNSTT (Hex PubKey)") }, modifier = Modifier.fillMaxWidth())

                Text("تونل داخلی روی بستر DNS:", style = MaterialTheme.typography.labelMedium)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf("ssh" to "SSH روی DNS", "socks" to "SOCKS5 مستقیم", "vless" to "VLESS روی DNS", "trojan" to "Trojan روی DNS").forEach { (id, label) ->
                        FilterChip(selected = dnsInnerMode == id, onClick = { dnsInnerMode = id }, label = { Text(label) })
                    }
                }

                if (dnsInnerMode != "socks") {
                    OutlinedTextField(
                        dnsInnerUser,
                        { dnsInnerUser = it },
                        label = { Text(if (dnsInnerMode == "ssh") "نام کاربری SSH" else "UUID / Password") },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
                if (dnsInnerMode == "ssh") {
                    OutlinedTextField(dnsInnerPass, { dnsInnerPass = it }, label = { Text("رمز عبور SSH") }, modifier = Modifier.fillMaxWidth())
                }

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(
                        onClick = { onTestDnsResolver(dnsResolver, dnsDomain) },
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("تست پورت ۵۳")
                    }
                    Button(
                        onClick = {
                            onSaveDnsTunnel(
                                dnsName,
                                dnsTransport,
                                dnsResolver,
                                dnsDomain,
                                dnsPubkey,
                                dnsInnerMode,
                                dnsInnerUser,
                                dnsInnerPass,
                                "3*Firefox,2*Chrome,1*iOS"
                            )
                        },
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("ساخت پروفایل DNS")
                    }
                }

                if (dnsProbeOutput.isNotBlank()) {
                    Text(dnsProbeOutput, style = MaterialTheme.typography.bodySmall, color = PniCyan)
                }
            }
        }

        // 2. BPB Cloudflare Worker Wizard
        Card(
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            shape = RoundedCornerShape(18.dp)
        ) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Icon(Icons.Rounded.CloudUpload, null, tint = PniEmerald)
                    Text("ویزارد ساخت Worker کلودفلر (BPB)", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                }
                OutlinedButton(
                    onClick = {
                        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(CloudflareDeployer.tokenCreationUrl())))
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("۱. باز کردن صفحه ساخت توکن در Cloudflare")
                }
                OutlinedTextField(cfToken, { cfToken = it }, label = { Text("Cloudflare API Token") }, modifier = Modifier.fillMaxWidth())
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = { onFetchCfAccounts(cfToken) }, enabled = !cfBusy, modifier = Modifier.weight(1f)) {
                        Text("۲. دریافت اکانت")
                    }
                    if (cfAccounts.isNotEmpty()) {
                        Text("اکانت: ${cfAccounts.first().name}", color = PniCyan, modifier = Modifier.weight(1f))
                    }
                }
                OutlinedTextField(cfAccountId, { cfAccountId = it }, label = { Text("Account ID") }, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(cfWorkerName, { cfWorkerName = it }, label = { Text("نام Worker (انگلیسی)") }, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(cfUuid, { cfUuid = it }, label = { Text("UUID (خالی = تولید خودکار)") }, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(cfProxyIps, { cfProxyIps = it }, label = { Text("ProxyIP ها (با کاما جدا کنید)") }, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(cfFallback, { cfFallback = it }, label = { Text("Fallback URL") }, modifier = Modifier.fillMaxWidth())
                Button(
                    onClick = { onDeployCfWorker(cfToken, cfAccountId, cfWorkerName, cfUuid, cfProxyIps, cfFallback) },
                    enabled = !cfBusy,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    if (cfBusy) CircularProgressIndicator(Modifier.size(18.dp))
                    else Text("۳. ساخت و افزودن خودکار به پروفایل‌ها")
                }
            }
        }

        // 3. Soldier Local SNI Spoof Engine
        Card(
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            shape = RoundedCornerShape(18.dp)
        ) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Icon(Icons.Rounded.Security, null, tint = PniCyan)
                    Text("موتور محلی SNI Spoof (127.0.0.1:${settings.localSniSpoofPort})", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                }
                OutlinedTextField(spoofTarget, { spoofTarget = it }, label = { Text("سرور مقصد (IP:Port)") }, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(spoofFakeSni, { spoofFakeSni = it }, label = { Text("Fake SNI (اختیاری)") }, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(spoofFragLen, { spoofFragLen = it }, label = { Text("طول تکه‌های ClientHello (بایت)") }, modifier = Modifier.fillMaxWidth())
                Button(
                    onClick = { onToggleSpoof(spoofTarget, spoofFakeSni, spoofFragLen.toIntOrNull() ?: 40) },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(if (spoofRunning) "توقف موتور SNI Spoof" else "اجرای موتور محلی SNI Spoof")
                }
            }
        }

        // 4. Domain -> IP Lookup
        Card(
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            shape = RoundedCornerShape(18.dp)
        ) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Icon(Icons.Rounded.Dns, null, tint = PniEmerald)
                    Text("IP Lookup با DoH (رفع مسمومیت DNS)", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                }
                OutlinedTextField(domainQuery, { domainQuery = it }, label = { Text("نام دامنه") }, modifier = Modifier.fillMaxWidth())
                Button(onClick = { onLookupDomain(domainQuery) }, modifier = Modifier.fillMaxWidth()) {
                    Text("استعلام IPهای پشت دامنه")
                }
                if (domainLookupOutput.isNotBlank()) {
                    Text(domainLookupOutput, style = MaterialTheme.typography.bodySmall, color = PniCyan)
                }
            }
        }

        // 5. Config Encrypt / Decrypt
        Card(
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            shape = RoundedCornerShape(18.dp)
        ) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Icon(Icons.Rounded.Lock, null, tint = PniEmerald)
                    Text("رمزنگاری / رمزگشایی امن کانفیگ", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                }
                OutlinedTextField(cryptoInput, { cryptoInput = it }, label = { Text("لینک ساده یا pni://enc:...") }, minLines = 2, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(cryptoPass, { cryptoPass = it }, label = { Text("رمز عبور اختیاری") }, modifier = Modifier.fillMaxWidth())
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(
                        onClick = { cryptoOutput = ConfigEncryptor.encrypt(cryptoInput, cryptoPass) },
                        modifier = Modifier.weight(1f)
                    ) { Text("رمزنگاری") }
                    OutlinedButton(
                        onClick = { cryptoOutput = ConfigEncryptor.decrypt(cryptoInput, cryptoPass) ?: "خطا در رمزگشایی" },
                        modifier = Modifier.weight(1f)
                    ) { Text("رمزگشایی") }
                }
                if (cryptoOutput.isNotBlank()) {
                    OutlinedButton(
                        onClick = {
                            clipboard.setText(AnnotatedString(cryptoOutput))
                            onShowMessage("نتیجه کپی شد")
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("کپی: ${cryptoOutput.take(48)}...")
                    }
                }
            }
        }
    }
}
