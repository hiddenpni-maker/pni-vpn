package ir.pni.vpn.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Stop
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import ir.pni.vpn.data.model.CdnProvider
import ir.pni.vpn.data.model.ScanOptions
import ir.pni.vpn.data.model.ScanResultItem
import ir.pni.vpn.ui.state.ScannerUiState
import ir.pni.vpn.ui.theme.PniCrimson
import ir.pni.vpn.ui.theme.PniCyan
import ir.pni.vpn.ui.theme.PniEmerald

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ScannerScreen(
    state: ScannerUiState,
    onSelectProvider: (CdnProvider) -> Unit,
    onUpdateOptions: ((ScanOptions) -> ScanOptions) -> Unit,
    onStartScan: () -> Unit,
    onStopScan: () -> Unit,
    onApplyIp: (ScanResultItem) -> Unit,
    onExport: (String) -> Unit,
    onShowMessage: (String) -> Unit
) {
    val clipboard = LocalClipboardManager.current
    val opts = state.options

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                shape = RoundedCornerShape(18.dp)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text(
                        text = "اسکنر چندشبکه‌ای PNI (الهام از SenPai + BPB)",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )

                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        CdnProvider.entries.forEach { cdn ->
                            FilterChip(
                                selected = opts.provider == cdn,
                                onClick = { onSelectProvider(cdn) },
                                label = { Text(cdn.titleFa) }
                            )
                        }
                    }

                    OutlinedTextField(
                        value = opts.sni,
                        onValueChange = { v -> onUpdateOptions { it.copy(sni = v) } },
                        label = { Text("SNI / دامنه تست (مثلاً speed.cloudflare.com یا دامنه Akamai)") },
                        modifier = Modifier.fillMaxWidth()
                    )

                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(
                            value = opts.portsCsv,
                            onValueChange = { v -> onUpdateOptions { it.copy(portsCsv = v) } },
                            label = { Text("پورت‌ها") },
                            modifier = Modifier.weight(1f)
                        )
                        OutlinedTextField(
                            value = opts.count.toString(),
                            onValueChange = { v -> onUpdateOptions { it.copy(count = v.toIntOrNull() ?: 200) } },
                            label = { Text("تعداد IP") },
                            modifier = Modifier.weight(1f)
                        )
                    }

                    if (opts.provider == CdnProvider.CUSTOM) {
                        OutlinedTextField(
                            value = opts.targetsText,
                            onValueChange = { v -> onUpdateOptions { it.copy(targetsText = v) } },
                            label = { Text("IP، CIDR، رنج (1.2.3.1-1.2.3.50) یا دامنه دلخواه") },
                            minLines = 3,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }

                    OutlinedTextField(
                        value = opts.shareLinkForPhase2,
                        onValueChange = { v -> onUpdateOptions { it.copy(shareLinkForPhase2 = v) } },
                        label = { Text("لینک VLESS/Trojan/VMess برای تست سرعت واقعی مرحله ۲ (اختیاری)") },
                        modifier = Modifier.fillMaxWidth()
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("حالت ملایم Gentle (ضد قطع اینترنت در اپراتورهای ایران)")
                        Switch(
                            checked = opts.gentle,
                            onCheckedChange = { c -> onUpdateOptions { it.copy(gentle = c) } }
                        )
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("اسکن با Anti-DPI (تکه‌تکه کردن ClientHello)")
                        Switch(
                            checked = opts.antiDpi,
                            onCheckedChange = { c -> onUpdateOptions { it.copy(antiDpi = c) } }
                        )
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("اسکن همسایه (Neighbor Scan در همان /24)")
                        Switch(
                            checked = opts.neighborScan,
                            onCheckedChange = { c -> onUpdateOptions { it.copy(neighborScan = c) } }
                        )
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        if (!state.running) {
                            Button(onClick = onStartScan, modifier = Modifier.weight(1f)) {
                                Icon(Icons.Rounded.PlayArrow, null, Modifier.size(18.dp))
                                Spacer(Modifier.width(6.dp))
                                Text("شروع اسکن")
                            }
                        } else {
                            Button(
                                onClick = onStopScan,
                                colors = ButtonDefaults.buttonColors(containerColor = PniCrimson),
                                modifier = Modifier.weight(1f)
                            ) {
                                Icon(Icons.Rounded.Stop, null, Modifier.size(18.dp))
                                Spacer(Modifier.width(6.dp))
                                Text("توقف اسکن")
                            }
                        }
                    }

                    if (state.running || state.tested > 0) {
                        if (state.running) {
                            LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                        }
                        Text(
                            text = "مرحله ${if (state.phase == 2) "۲ (تست سرعت Xray)" else "۱ (کشف آیپی)"} • تست‌شده: ${state.tested} | سالم: ${state.healthy} | ناموفق: ${state.failed}",
                            style = MaterialTheme.typography.bodySmall,
                            color = PniCyan
                        )
                    }
                    if (state.infoLog.isNotBlank()) {
                        Text(
                            text = state.infoLog,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }

        if (state.results.any { it.healthy }) {
            item {
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text("خروجی نتایج سالم", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            listOf(
                                "endpoints" to "لیست IP:Port",
                                "links" to "لینک‌ها",
                                "base64" to "ساب Base64",
                                "singbox" to "Sing-box",
                                "clash" to "Clash YAML"
                            ).forEach { (fmt, label) ->
                                OutlinedButton(onClick = { onExport(fmt) }) { Text(label) }
                            }
                        }
                        if (state.exportedText.isNotBlank()) {
                            OutlinedButton(
                                onClick = {
                                    clipboard.setText(AnnotatedString(state.exportedText))
                                    onShowMessage("خروجی در کلیپ‌بورد کپی شد")
                                },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Icon(Icons.Rounded.ContentCopy, null, Modifier.size(16.dp))
                                Spacer(Modifier.width(6.dp))
                                Text("کپی خروجی آماده (${state.exportedText.lines().size} خط)")
                            }
                        }
                    }
                }
            }
        }

        items(state.results.filter { it.healthy }, key = { "${it.ip}:${it.port}" }) { item ->
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(
                            text = "${item.ip}:${item.port}",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = PniEmerald
                        )
                        val meta = buildString {
                            append("${item.latencyMs}ms • Loss ${item.lossPct.toInt()}%")
                            if (item.colo.isNotBlank()) append(" • Colo: ${item.colo}")
                            if (item.cdnHeader.isNotBlank()) append(" • ${item.cdnHeader}")
                            if (item.phase2Tested) {
                                append(if (item.phase2Ok) " • سرعت: ${String.format("%.1f", item.downloadMbps)} Mbps" else " • Xray خطا")
                            }
                        }
                        Text(meta, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        OutlinedButton(onClick = { onApplyIp(item) }) {
                            Icon(Icons.Rounded.CheckCircle, null, Modifier.size(16.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("اعمال")
                        }
                    }
                }
            }
        }
    }
}
