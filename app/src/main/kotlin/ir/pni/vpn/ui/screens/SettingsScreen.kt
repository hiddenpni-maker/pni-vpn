package ir.pni.vpn.ui.screens

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Campaign
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import ir.pni.vpn.R
import ir.pni.vpn.core.CoreBridge
import ir.pni.vpn.data.model.AppSettings
import ir.pni.vpn.ui.theme.PniCyan

@Composable
fun SettingsScreen(
    settings: AppSettings,
    onUpdateSettings: ((AppSettings) -> AppSettings) -> Unit
) {
    val context = LocalContext.current
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Card(
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            shape = RoundedCornerShape(18.dp)
        ) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("مسیریابی و DNS", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)

                SettingToggle("عبور مستقیم سایت‌های ایرانی (geosite:ir)", settings.bypassIranDomains) { v ->
                    onUpdateSettings { it.copy(bypassIranDomains = v) }
                }
                SettingToggle("عبور مستقیم IPهای ایران (geoip:ir)", settings.bypassIranIps) { v ->
                    onUpdateSettings { it.copy(bypassIranIps = v) }
                }
                SettingToggle("مسدودسازی تبلیغات و ردیاب‌ها", settings.blockAds) { v ->
                    onUpdateSettings { it.copy(blockAds = v) }
                }
                SettingToggle("فعال‌سازی IPv6 در تونل", settings.ipv6Enabled) { v ->
                    onUpdateSettings { it.copy(ipv6Enabled = v) }
                }
                SettingToggle("Split Tunnel (استثنا کردن اپ‌ها)", settings.splitTunnelEnabled) { v ->
                    onUpdateSettings { it.copy(splitTunnelEnabled = v) }
                }

                OutlinedTextField(
                    value = settings.dnsMode,
                    onValueChange = { v -> onUpdateSettings { it.copy(dnsMode = v) } },
                    label = { Text("DNS اصلی (DoH)") },
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = settings.mtu.toString(),
                    onValueChange = { v -> onUpdateSettings { it.copy(mtu = v.toIntOrNull() ?: 1280) } },
                    label = { Text("MTU تونل (موبایل: 1280 | TD-LTE: 1260)") },
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }

        Card(
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            shape = RoundedCornerShape(18.dp)
        ) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Image(
                        painter = painterResource(id = R.drawable.pni_fox_logo),
                        contentDescription = "PNI Fox Logo",
                        modifier = Modifier
                            .size(56.dp)
                            .clip(RoundedCornerShape(14.dp))
                    )
                    Column {
                        Text("PNI — شبکه هوشمند آزادی", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                        Text("Xray-core (PattN Patch): ${CoreBridge.getXrayVersion()}", style = MaterialTheme.typography.bodySmall, color = PniCyan)
                    }
                }
                Text("هسته‌های جانبی: Sing-box / Outline / Aether / DNSTT (AM Tunnel) / Psiphon / Tor + Lyrebird", style = MaterialTheme.typography.bodySmall)
                Text("اسکنرها: SenPai Anti-DPI + Cloudflare + Akamai + Fastly + Gcore + WARP", style = MaterialTheme.typography.bodySmall)
                Button(
                    onClick = {
                        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://t.me/otaqe_pni")))
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Rounded.Campaign, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("عضویت در کانال تلگرام: @otaqe_pni")
                }
            }
        }
    }
}

@Composable
private fun SettingToggle(label: String, checked: Boolean, onChanged: (Boolean) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
        Switch(checked = checked, onCheckedChange = onChanged)
    }
}
