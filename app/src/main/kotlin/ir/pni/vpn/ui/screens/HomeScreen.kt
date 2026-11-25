package ir.pni.vpn.ui.screens

import android.content.Intent
import android.net.Uri
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Bolt
import androidx.compose.material.icons.rounded.Campaign
import androidx.compose.material.icons.rounded.NetworkCheck
import androidx.compose.material.icons.rounded.PowerSettingsNew
import androidx.compose.material.icons.rounded.Speed
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import ir.pni.vpn.R
import ir.pni.vpn.data.model.AppSettings
import ir.pni.vpn.data.model.ConnectionState
import ir.pni.vpn.data.model.NetworkType
import ir.pni.vpn.data.model.ProxyProfile
import ir.pni.vpn.data.preset.IranNetworkPresets
import ir.pni.vpn.ui.theme.PniAmber
import ir.pni.vpn.ui.theme.PniCrimson
import ir.pni.vpn.ui.theme.PniCyan
import ir.pni.vpn.ui.theme.PniEmerald
import ir.pni.vpn.ui.theme.PniSurfaceElevated

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun HomeScreen(
    connectionState: ConnectionState,
    statusMessage: String,
    txBps: Long,
    rxBps: Long,
    delayMs: Long?,
    settings: AppSettings,
    profiles: List<ProxyProfile>,
    onToggleConnect: () -> Unit,
    onSelectNetwork: (NetworkType) -> Unit,
    onSelectProfile: (String) -> Unit,
    onMeasureDelay: () -> Unit,
    onOpenScanner: () -> Unit,
    onOpenDoctor: () -> Unit
) {
    val context = LocalContext.current
    val activeProfile = profiles.firstOrNull { it.id == settings.activeProfileId } ?: profiles.firstOrNull()
    val preset = IranNetworkPresets.get(settings.activeNetworkType)

    val ringColor by animateColorAsState(
        targetValue = when (connectionState) {
            ConnectionState.CONNECTED -> PniEmerald
            ConnectionState.CONNECTING, ConnectionState.DISCONNECTING -> PniAmber
            ConnectionState.ERROR -> PniCrimson
            ConnectionState.DISCONNECTED -> Color(0xFF334155)
        },
        label = "ring"
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // Connect hero card
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            shape = RoundedCornerShape(22.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(132.dp)
                        .clip(CircleShape)
                        .background(ringColor.copy(alpha = 0.14f))
                        .border(3.dp, ringColor, CircleShape)
                        .clickable { onToggleConnect() },
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            imageVector = Icons.Rounded.PowerSettingsNew,
                            contentDescription = "Connect",
                            tint = ringColor,
                            modifier = Modifier.size(46.dp)
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(
                            text = when (connectionState) {
                                ConnectionState.CONNECTED -> "متصل"
                                ConnectionState.CONNECTING -> "در حال اتصال"
                                ConnectionState.DISCONNECTING -> "در حال قطع"
                                ConnectionState.ERROR -> "خطا"
                                ConnectionState.DISCONNECTED -> "اتصال"
                            },
                            color = ringColor,
                            fontWeight = FontWeight.Bold,
                            style = MaterialTheme.typography.titleMedium
                        )
                    }
                }

                Text(
                    text = statusMessage,
                    style = MaterialTheme.typography.bodyMedium,
                    textAlign = TextAlign.Center,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    StatBadge("دانلود", formatRate(rxBps))
                    StatBadge("آپلود", formatRate(txBps))
                    StatBadge("تأخیر", delayMs?.takeIf { it >= 0 }?.let { "${it}ms" } ?: "—")
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedButton(
                        onClick = onMeasureDelay,
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Rounded.Speed, null, Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("تست پینگ واقعی")
                    }
                    OutlinedButton(
                        onClick = onOpenDoctor,
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Rounded.NetworkCheck, null, Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("دکتر شبکه")
                    }
                }
            }
        }

        // Iranian ISP & Modem Selector
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            shape = RoundedCornerShape(18.dp)
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(
                    text = "مدل اینترنت و اپراتور فعلی شما",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    NetworkType.entries.forEach { net ->
                        val selected = net == settings.activeNetworkType
                        FilterChip(
                            selected = selected,
                            onClick = { onSelectNetwork(net) },
                            label = { Text(net.titleFa) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = PniEmerald.copy(alpha = 0.22f),
                                selectedLabelColor = PniEmerald
                            )
                        )
                    }
                }

                Card(
                    colors = CardDefaults.cardColors(containerColor = PniSurfaceElevated),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(
                            text = "توصیه هوشمند PNI برای ${settings.activeNetworkType.titleFa}:",
                            style = MaterialTheme.typography.labelLarge,
                            color = PniEmerald
                        )
                        Text(
                            text = preset.adviceFa,
                            style = MaterialTheme.typography.bodySmall
                        )
                        Text(
                            text = "هشدار: ${preset.warningsFa}",
                            style = MaterialTheme.typography.bodySmall,
                            color = PniAmber
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            AssistChip(
                                onClick = onOpenScanner,
                                label = { Text("اسکن ${preset.preferredCdn.titleFa}") },
                                leadingIcon = { Icon(Icons.Rounded.Bolt, null, Modifier.size(16.dp)) }
                            )
                        }
                    }
                }
            }
        }

        // Active Profile & Quick Switch
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            shape = RoundedCornerShape(18.dp)
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = "پروفایل فعال: ${activeProfile?.name ?: "انتخاب نشده"}",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold
                )
                if (activeProfile != null) {
                    Text(
                        text = "${activeProfile.protocol.labelFa} • ${activeProfile.server}:${activeProfile.port} • Fragment=${if (activeProfile.fragmentEnabled) "روشن" else "خاموش"}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    profiles.take(6).forEach { p ->
                        FilterChip(
                            selected = p.id == activeProfile?.id,
                            onClick = { onSelectProfile(p.id) },
                            label = { Text(p.name.take(26)) }
                        )
                    }
                }
            }
        }

        // Brand & Telegram Channel Card (@otaqe_pni)
        Card(
            onClick = {
                context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://t.me/otaqe_pni")))
            },
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            shape = RoundedCornerShape(18.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(14.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Image(
                    painter = painterResource(id = R.drawable.pni_fox_logo),
                    contentDescription = "PNI Fox Logo",
                    modifier = Modifier
                        .size(48.dp)
                        .clip(RoundedCornerShape(12.dp))
                )
                Column(Modifier.weight(1f)) {
                    Text("کانال تلگرام رسمی PNI", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                    Text("t.me/otaqe_pni — آموزش، آپدیت و کانفیگ‌ها", style = MaterialTheme.typography.bodySmall, color = PniCyan)
                }
                Icon(Icons.Rounded.Campaign, contentDescription = "Telegram", tint = PniCyan)
            }
        }
    }
}

@Composable
private fun StatBadge(label: String, value: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(text = value, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        Text(text = label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

private fun formatRate(bytesPerSec: Long): String = when {
    bytesPerSec >= 1_048_576 -> String.format("%.1f MB/s", bytesPerSec / 1_048_576.0)
    bytesPerSec >= 1024 -> String.format("%.0f KB/s", bytesPerSec / 1024.0)
    else -> "$bytesPerSec B/s"
}
