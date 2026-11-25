package ir.pni.vpn.ui.screens

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
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
import androidx.compose.material.icons.rounded.Campaign
import androidx.compose.material.icons.rounded.MenuBook
import androidx.compose.material.icons.rounded.NetworkCheck
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import ir.pni.vpn.data.model.DiagnosticReport
import ir.pni.vpn.data.model.GuideSection
import ir.pni.vpn.ui.theme.PniAmber
import ir.pni.vpn.ui.theme.PniCyan
import ir.pni.vpn.ui.theme.PniEmerald
import ir.pni.vpn.ui.theme.PniSurfaceElevated

@Composable
fun GuideScreen(
    guides: List<GuideSection>,
    diagnostic: DiagnosticReport?,
    diagnosticLoading: Boolean,
    onRunDiagnostic: () -> Unit
) {
    val context = LocalContext.current
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Network Doctor Card
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                shape = RoundedCornerShape(18.dp)
            ) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Icon(Icons.Rounded.NetworkCheck, null, tint = PniEmerald)
                        Text("دکتر شبکه PNI (عیب‌یاب خودکار فیلترینگ)", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    }
                    Text(
                        text = "بررسی وضعیت مسمومیت DNS، هندشیک کلودفلر، شبکه Akamai و پورت‌های UDP روی اینترنت فعلی شما.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Button(
                        onClick = onRunDiagnostic,
                        enabled = !diagnosticLoading,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        if (diagnosticLoading) {
                            CircularProgressIndicator(Modifier.size(18.dp))
                            Spacer(Modifier.width(8.dp))
                            Text("در حال بررسی خط اینترنت...")
                        } else {
                            Text("شروع عیب‌یابی شبکه فعلی")
                        }
                    }

                    diagnostic?.let { rep ->
                        Card(
                            colors = CardDefaults.cardColors(containerColor = PniSurfaceElevated),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                Text(rep.networkLabelFa, style = MaterialTheme.typography.labelLarge, color = PniCyan)
                                Text(rep.recommendationFa, style = MaterialTheme.typography.bodySmall)
                                Text(
                                    text = "پروتکل‌های پیشنهادی: ${rep.suggestedProtocols.joinToString(" • ") { it.labelFa }}",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = PniEmerald
                                )
                            }
                        }
                    }
                }
            }
        }

        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                shape = RoundedCornerShape(18.dp)
            ) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Icon(Icons.Rounded.Campaign, null, tint = PniCyan)
                        Text("پشتیبانی و آپدیت در تلگرام (@otaqe_pni)", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                    }
                    Text(
                        text = "برای دریافت آخرین نسخه‌ها، آیپی‌های تمیز روزانه، پل‌های Tor و آموزش‌های ویدیویی به کانال رسمی ما بپیوندید:",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    OutlinedButton(
                        onClick = {
                            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://t.me/otaqe_pni")))
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("باز کردن کانال https://t.me/otaqe_pni")
                    }
                }
            }
        }

        // Persian User Guides
        items(guides, key = { it.id }) { g ->
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Icon(Icons.Rounded.MenuBook, null, tint = PniCyan, modifier = Modifier.size(20.dp))
                        Text(g.categoryFa, style = MaterialTheme.typography.labelMedium, color = PniCyan)
                    }
                    Text(g.titleFa, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                    Text(g.summaryFa, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    g.stepsFa.forEach { step ->
                        Text(step, style = MaterialTheme.typography.bodySmall)
                    }
                    if (g.tipsFa.isNotEmpty()) {
                        Card(
                            colors = CardDefaults.cardColors(containerColor = PniSurfaceElevated),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Column(Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                g.tipsFa.forEach { tip ->
                                    Text("نکته: $tip", style = MaterialTheme.typography.labelSmall, color = PniAmber)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
