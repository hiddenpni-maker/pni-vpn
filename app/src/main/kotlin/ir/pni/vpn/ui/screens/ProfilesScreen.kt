package ir.pni.vpn.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoFixHigh
import androidx.compose.material.icons.rounded.ContentPaste
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.QrCodeScanner
import androidx.compose.material.icons.rounded.Security
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import ir.pni.vpn.data.model.ProxyProfile
import ir.pni.vpn.ui.theme.PniCyan
import ir.pni.vpn.ui.theme.PniEmerald

@Composable
fun ProfilesScreen(
    profiles: List<ProxyProfile>,
    activeProfileId: String?,
    onSelect: (String) -> Unit,
    onImportRaw: (String) -> Unit,
    onScanQr: () -> Unit,
    onSaveProfile: (ProxyProfile) -> Unit,
    onDeleteProfile: (String) -> Unit,
    onApplyIspTuning: (ProxyProfile) -> Unit,
    onConvertSpoof: (ProxyProfile) -> Unit,
    onEncryptProfile: (ProxyProfile, String) -> String,
    onShowMessage: (String) -> Unit
) {
    val clipboard = LocalClipboardManager.current
    var showImportDialog by remember { mutableStateOf(false) }
    var importText by remember { mutableStateOf("") }
    var editingProfile by remember { mutableStateOf<ProxyProfile?>(null) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Button(
                onClick = {
                    val clip = clipboard.getText()?.text.orEmpty()
                    if (clip.isNotBlank()) onImportRaw(clip) else showImportDialog = true
                },
                modifier = Modifier.weight(1f)
            ) {
                Icon(Icons.Rounded.ContentPaste, null, Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text("ورود از کلیپ‌بورد")
            }
            OutlinedButton(
                onClick = onScanQr,
                modifier = Modifier.weight(1f)
            ) {
                Icon(Icons.Rounded.QrCodeScanner, null, Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text("اسکن QR")
            }
        }

        OutlinedButton(
            onClick = { showImportDialog = true },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("وارد کردن دستی لینک (VLESS / VMess / Trojan / Hy2 / TUIC / SSH / Aether / pni://enc)")
        }

        LazyColumn(
            verticalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier.fillMaxSize()
        ) {
            items(profiles, key = { it.id }) { profile ->
                val isSelected = profile.id == activeProfileId
                Card(
                    onClick = { onSelect(profile.id) },
                    colors = CardDefaults.cardColors(
                        containerColor = if (isSelected) PniEmerald.copy(alpha = 0.14f)
                        else MaterialTheme.colorScheme.surface
                    ),
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(Modifier.weight(1f)) {
                                Text(
                                    text = profile.name,
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = "${profile.protocol.labelFa} (${profile.protocol.engine.name}) • ${profile.server}:${profile.port}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Row {
                                IconButton(onClick = { editingProfile = profile }) {
                                    Icon(Icons.Rounded.Edit, "Edit")
                                }
                                IconButton(onClick = { onDeleteProfile(profile.id) }) {
                                    Icon(Icons.Rounded.Delete, "Delete")
                                }
                            }
                        }

                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            AssistChip(
                                onClick = { onApplyIspTuning(profile) },
                                label = { Text("بهینه‌سازی برای اپراتور") },
                                leadingIcon = { Icon(Icons.Rounded.AutoFixHigh, null, Modifier.size(16.dp)) }
                            )
                            AssistChip(
                                onClick = { onConvertSpoof(profile) },
                                label = { Text("تبدیل SNI Spoof") },
                                leadingIcon = { Icon(Icons.Rounded.Security, null, Modifier.size(16.dp)) }
                            )
                            AssistChip(
                                onClick = {
                                    val enc = onEncryptProfile(profile, "")
                                    clipboard.setText(AnnotatedString(enc))
                                    onShowMessage("کانفیگ رمزنگاری و کپی شد")
                                },
                                label = { Text("رمزنگاری") },
                                leadingIcon = { Icon(Icons.Rounded.Lock, null, Modifier.size(16.dp)) }
                            )
                        }
                        if (profile.unsafeTls || profile.cipherSuites.isNotBlank() || profile.fragmentEnabled) {
                            Text(
                                text = "ضد فیلتر: ${if (profile.fragmentEnabled) "Fragment(${profile.fragmentLength}) " else ""}${if (profile.unsafeTls) "PattN-Unsafe " else ""}${if (profile.cipherSuites.isNotBlank()) "Custom-Cipher" else ""}",
                                style = MaterialTheme.typography.labelSmall,
                                color = PniCyan
                            )
                        }
                    }
                }
            }
        }
    }

    if (showImportDialog) {
        AlertDialog(
            onDismissRequest = { showImportDialog = false },
            title = { Text("افزودن کانفیگ یا سابسکریپشن") },
            text = {
                OutlinedTextField(
                    value = importText,
                    onValueChange = { importText = it },
                    label = { Text("لینک‌های vless://, trojan://, hy2://, ... یا Base64") },
                    minLines = 4,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                Button(onClick = {
                    onImportRaw(importText)
                    importText = ""
                    showImportDialog = false
                }) { Text("افزودن") }
            },
            dismissButton = {
                TextButton(onClick = { showImportDialog = false }) { Text("انصراف") }
            }
        )
    }

    editingProfile?.let { current ->
        ProfileEditorDialog(
            profile = current,
            allProfiles = profiles,
            onDismiss = { editingProfile = null },
            onSave = {
                onSaveProfile(it)
                editingProfile = null
            }
        )
    }
}

@Composable
private fun ProfileEditorDialog(
    profile: ProxyProfile,
    allProfiles: List<ProxyProfile>,
    onDismiss: () -> Unit,
    onSave: (ProxyProfile) -> Unit
) {
    var name by remember { mutableStateOf(profile.name) }
    var server by remember { mutableStateOf(profile.server) }
    var port by remember { mutableStateOf(profile.port.toString()) }
    var sni by remember { mutableStateOf(profile.sni) }
    var host by remember { mutableStateOf(profile.host) }
    var path by remember { mutableStateOf(profile.path) }
    var fp by remember { mutableStateOf(profile.fingerprint) }
    var cipherSuites by remember { mutableStateOf(profile.cipherSuites) }
    var unsafeTls by remember { mutableStateOf(profile.unsafeTls) }
    var fragEnabled by remember { mutableStateOf(profile.fragmentEnabled) }
    var fragPackets by remember { mutableStateOf(profile.fragmentPackets) }
    var fragLength by remember { mutableStateOf(profile.fragmentLength) }
    var fragInterval by remember { mutableStateOf(profile.fragmentInterval) }
    var noiseEnabled by remember { mutableStateOf(profile.noiseEnabled) }
    var chainId by remember { mutableStateOf(profile.chainNextProfileId.orEmpty()) }
    val chainCandidates = allProfiles.filter { it.id != profile.id }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("تنظیمات پیشرفته پروفایل (PattN + BPB)") },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedTextField(name, { name = it }, label = { Text("نام پروفایل") }, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(server, { server = it }, label = { Text("آدرس سرور / آیپی تمیز") }, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(port, { port = it }, label = { Text("پورت") }, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(sni, { sni = it }, label = { Text("SNI") }, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(host, { host = it }, label = { Text("Host Header") }, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(path, { path = it }, label = { Text("Path") }, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(fp, { fp = it }, label = { Text("TLS Fingerprint (chrome / unsafe / random)") }, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(cipherSuites, { cipherSuites = it }, label = { Text("PattN CipherSuites (اختیاری)") }, modifier = Modifier.fillMaxWidth())

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(unsafeTls, { unsafeTls = it })
                    Text("حالت Unsafe TLS (PattN)")
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(fragEnabled, { fragEnabled = it })
                    Text("فعال‌سازی TLS Fragment")
                }
                if (fragEnabled) {
                    OutlinedTextField(fragPackets, { fragPackets = it }, label = { Text("Fragment Packets (tlshello / 1-3)") }, modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(fragLength, { fragLength = it }, label = { Text("Fragment Length (e.g. 10-20)") }, modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(fragInterval, { fragInterval = it }, label = { Text("Fragment Interval ms (e.g. 10-20)") }, modifier = Modifier.fillMaxWidth())
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(noiseEnabled, { noiseEnabled = it })
                    Text("فعال‌سازی UDP Noise (برای WireGuard/WARP)")
                }
                if (chainCandidates.isNotEmpty()) {
                    Text("زنجیره پروکسی (Chain Hop اختیاری):", style = MaterialTheme.typography.labelMedium)
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        AssistChip(
                            onClick = { chainId = "" },
                            label = { Text(if (chainId.isBlank()) "✓ بدون زنجیره" else "بدون زنجیره") }
                        )
                        chainCandidates.take(3).forEach { cand ->
                            AssistChip(
                                onClick = { chainId = cand.id },
                                label = { Text(if (chainId == cand.id) "✓ ${cand.name.take(14)}" else cand.name.take(14)) }
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(onClick = {
                onSave(
                    profile.copy(
                        name = name,
                        server = server.trim(),
                        port = port.toIntOrNull() ?: profile.port,
                        sni = sni.trim(),
                        host = host.trim(),
                        path = path.trim(),
                        fingerprint = fp.trim(),
                        cipherSuites = cipherSuites.trim(),
                        unsafeTls = unsafeTls,
                        fragmentEnabled = fragEnabled,
                        fragmentPackets = fragPackets.trim(),
                        fragmentLength = fragLength.trim(),
                        fragmentInterval = fragInterval.trim(),
                        noiseEnabled = noiseEnabled,
                        chainNextProfileId = chainId.ifBlank { null }
                    )
                )
            }) { Text("ذخیره") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("بستن") }
        }
    )
}
