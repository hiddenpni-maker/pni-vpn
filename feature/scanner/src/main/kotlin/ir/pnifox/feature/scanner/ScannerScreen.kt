package ir.pnifox.feature.scanner

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Sort
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.EditNote
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Stop
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import ir.pnifox.core.model.AppLanguage
import ir.pnifox.core.model.ScanCategoryFilter
import ir.pnifox.core.model.ScanProvider
import ir.pnifox.core.model.ScanSortBy
import ir.pnifox.core.model.ScanSpeedTier
import ir.pnifox.core.model.ScanStyle
import ir.pnifox.core.ui.LocalAppLanguage
import ir.pnifox.core.ui.PnifoxBadgePill
import ir.pnifox.core.ui.PnifoxCard
import ir.pnifox.core.ui.PnifoxSectionLabel
import ir.pnifox.core.ui.PnifoxSwitchRow
import ir.pnifox.core.ui.ResponsiveScreenContainer
import ir.pnifox.core.ui.responsiveListPadding
import ir.pnifox.core.ui.tr

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ScannerRoute(
    viewModel: ScannerViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val isFa = LocalAppLanguage.current == AppLanguage.PERSIAN
    val clipboard = LocalClipboardManager.current
    var showCleanIpDialog by remember { mutableStateOf(false) }
    var sortMenuExpanded by remember { mutableStateOf(false) }

    ResponsiveScreenContainer { metrics ->
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = responsiveListPadding(metrics),
            verticalArrangement = Arrangement.spacedBy(metrics.sectionSpacing),
        ) {
            item {
                Text(
                    text = tr("Pro Multi-Metric IP Scanner", "اسکنر حرفه‌ای چندمعیاره IP"),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                )
            }

            // Provider Pills
            item {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(ScanProvider.entries) { provider ->
                        FilterChip(
                            selected = state.provider == provider,
                            onClick = { viewModel.selectProvider(provider) },
                            label = { Text(if (isFa) provider.labelFa else provider.labelEn, maxLines = 1) },
                        )
                    }
                }
                Spacer(Modifier.height(6.dp))
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(ScanStyle.entries) { style ->
                        FilterChip(
                            selected = state.scanStyle == style,
                            onClick = { viewModel.setScanStyle(style) },
                            label = { Text(if (isFa) style.labelFa else style.labelEn, maxLines = 1) },
                        )
                    }
                }
            }

            // Scanner Config Card
            item {
                PnifoxCard(contentPadding = metrics.cardPadding) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        OutlinedTextField(
                            value = state.port.toString(),
                            onValueChange = viewModel::setPort,
                            label = { Text("Port") },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            modifier = Modifier.weight(1f),
                        )
                        OutlinedTextField(
                            value = state.candidateCount.toString(),
                            onValueChange = { v -> v.toIntOrNull()?.let { viewModel.setCandidateCount(it) } },
                            label = { Text(tr("Samples", "تعداد")) },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            modifier = Modifier.weight(1f),
                        )
                        OutlinedButton(
                            onClick = { showCleanIpDialog = true },
                            shape = RoundedCornerShape(14.dp),
                        ) {
                            Icon(Icons.Rounded.EditNote, null, modifier = Modifier.size(18.dp))
                        }
                    }

                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        value = state.sniHost,
                        onValueChange = viewModel::setSniHost,
                        label = { Text("TLS / SNI Hostname") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Spacer(Modifier.height(6.dp))
                    PnifoxSwitchRow(
                        title = tr("TLS Handshake & Speed Test (KB/s)", "تست TLS و سرعت دانلود"),
                        subtitle = tr("Measures SNI TLS latency, Colo, and HTTP throughput", "سنجش دست‌دهی TLS، دیتاسنتر و سرعت دانلود واقعی"),
                        checked = state.enableTlsAndSpeedTest,
                        onCheckedChange = viewModel::toggleTlsAndSpeed,
                    )
                    PnifoxSwitchRow(
                        title = tr("Gentle ISP Mode (6 workers)", "حالت ملایم ضدبلاک اپراتور"),
                        checked = state.gentleMode,
                        onCheckedChange = viewModel::toggleGentle,
                    )

                    Spacer(Modifier.height(8.dp))
                    Button(
                        onClick = viewModel::startOrStopScan,
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(14.dp),
                    ) {
                        Icon(if (state.isScanning) Icons.Rounded.Stop else Icons.Rounded.PlayArrow, null)
                        Spacer(Modifier.width(6.dp))
                        Text(
                            if (state.isScanning) tr("Stop Scanner", "توقف اسکن")
                            else tr("Start Pro Multi-Metric Scan", "شروع اسکن حرفه‌ای"),
                        )
                    }
                    if (state.isScanning) {
                        Spacer(Modifier.height(8.dp))
                        LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                    }
                    if (state.progressLabel.isNotBlank()) {
                        Spacer(Modifier.height(6.dp))
                        Text(state.progressLabel, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                    }
                    val topHealthy = state.filteredResults.filter { it.packetLossPct <= 35 }.take(25).map { it.ip }
                    if (topHealthy.isNotEmpty()) {
                        Spacer(Modifier.height(8.dp))
                        OutlinedButton(onClick = { viewModel.applyIpsToPsiphonCdn(topHealthy, state.provider) }) {
                            Text(tr("Apply Top ${topHealthy.size} to Akamai / Psiphon CDN", "اعمال ${topHealthy.size} IP به فرانتینگ اکامای"), style = MaterialTheme.typography.labelSmall)
                        }
                    }
                }
            }

            if (!state.statusNote.isNullOrBlank()) {
                item {
                    PnifoxCard(containerColor = MaterialTheme.colorScheme.primaryContainer, contentPadding = 12.dp) {
                        Text(state.statusNote.orEmpty(), style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }

            // Category Filter & Sort Header
            item {
                PnifoxSectionLabel(
                    title = tr("Categorized Results (${state.filteredResults.size}/${state.allResults.size})", "دسته‌بندی نتایج (${state.filteredResults.size})"),
                    trailing = {
                        Row {
                            OutlinedButton(onClick = { sortMenuExpanded = true }) {
                                Icon(Icons.AutoMirrored.Rounded.Sort, null, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(4.dp))
                                Text(if (isFa) state.sortBy.labelFa else state.sortBy.labelEn, maxLines = 1)
                            }
                            DropdownMenu(expanded = sortMenuExpanded, onDismissRequest = { sortMenuExpanded = false }) {
                                ScanSortBy.entries.forEach { s ->
                                    DropdownMenuItem(
                                        text = { Text(if (isFa) s.labelFa else s.labelEn) },
                                        onClick = { viewModel.selectSortBy(s); sortMenuExpanded = false },
                                    )
                                }
                            }
                        }
                    },
                )
            }

            item {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(ScanCategoryFilter.entries) { cat ->
                        FilterChip(
                            selected = state.categoryFilter == cat,
                            onClick = { viewModel.selectCategoryFilter(cat) },
                            label = { Text(if (isFa) cat.labelFa else cat.labelEn, maxLines = 1) },
                        )
                    }
                }
            }

            if (state.filteredResults.isEmpty()) {
                item {
                    PnifoxCard(contentPadding = metrics.cardPadding) {
                        Text(
                            tr("No IPs match this category yet. Start a scan or switch to 'All Ranked'.", "آی‌پی در این دسته نیست؛ اسکن را شروع کنید یا فیلتر را تغییر دهید."),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            } else {
                items(state.filteredResults, key = { it.id }) { row ->
                    val tierColor = when (row.tier) {
                        ScanSpeedTier.S_PLUS -> Color(0xFF10B981)
                        ScanSpeedTier.A -> Color(0xFF38BDF8)
                        ScanSpeedTier.B -> Color(0xFFF59E0B)
                        ScanSpeedTier.C -> Color(0xFF94A3B8)
                    }
                    PnifoxCard(contentPadding = 12.dp) {
                        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            PnifoxBadgePill(
                                text = "${row.tier.badge} · ${row.stabilityScore}%",
                                containerColor = tierColor.copy(alpha = 0.18f),
                                contentColor = tierColor,
                            )
                            Spacer(Modifier.width(8.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text("${row.ip}:${row.port}", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                val details = buildString {
                                    append("Ping ${row.latencyMs}ms (min ${row.minLatencyMs}) · Jitter ${row.jitterMs}ms · Loss ${row.packetLossPct}%")
                                    row.tlsHandshakeMs?.let { append(" · TLS ${it}ms") }
                                    if (row.downloadSpeedKbps > 0) append(" · ↓ ${row.downloadSpeedKbps} KB/s")
                                    if (row.colo.isNotBlank()) append(" · ${row.colo}")
                                }
                                Text(details, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                        Spacer(Modifier.height(8.dp))
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            OutlinedButton(onClick = { viewModel.applyToAether(row) }) {
                                Text(tr("Use in AETHER", "اعمال به AETHER"), style = MaterialTheme.typography.labelSmall)
                            }
                            OutlinedButton(onClick = { viewModel.applyIpsToPsiphonCdn(listOf(row.ip), row.provider) }) {
                                Text(tr("Akamai CDN", "اکامای CDN"), style = MaterialTheme.typography.labelSmall)
                            }
                            OutlinedButton(onClick = { viewModel.applyToActiveServer(row) }) {
                                Text(tr("Use in Server", "اعمال به سرور"), style = MaterialTheme.typography.labelSmall)
                            }
                            TextButton(onClick = { clipboard.setText(AnnotatedString(row.ip)) }) {
                                Icon(Icons.Rounded.ContentCopy, null, modifier = Modifier.size(14.dp))
                                Spacer(Modifier.width(4.dp))
                                Text("Copy", style = MaterialTheme.typography.labelSmall)
                            }
                        }
                    }
                }
            }
        }
    }

    if (showCleanIpDialog) {
        var draft by remember(state.cleanIpsMultiline) { mutableStateOf(state.cleanIpsMultiline) }
        AlertDialog(
            onDismissRequest = { showCleanIpDialog = false },
            title = { Text(tr("Custom IPs / CIDRs", "ویرایش رنج و IP دلخواه")) },
            text = {
                OutlinedTextField(
                    value = draft,
                    onValueChange = { draft = it },
                    label = { Text("One IP or CIDR per line") },
                    minLines = 5,
                    maxLines = 9,
                    modifier = Modifier.fillMaxWidth(),
                )
            },
            confirmButton = {
                Button(onClick = { viewModel.saveCleanIpsMultiline(draft); showCleanIpDialog = false }) { Text("Save") }
            },
            dismissButton = {
                TextButton(onClick = { showCleanIpDialog = false }) { Text("Close") }
            },
        )
    }
}
