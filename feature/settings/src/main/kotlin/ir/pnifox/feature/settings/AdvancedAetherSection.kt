package ir.pnifox.feature.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import ir.pnifox.core.model.AetherNoizeProfile
import ir.pnifox.core.model.AetherProtocol
import ir.pnifox.core.model.AetherScanMode
import ir.pnifox.core.model.AetherSettings
import ir.pnifox.core.model.ExitDialMode
import ir.pnifox.core.model.IpVersionMode
import ir.pnifox.core.model.MasqueTransport
import ir.pnifox.core.model.PsiphonChainMode
import ir.pnifox.core.ui.PnifoxSwitchRow

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AdvancedAetherSection(
    aether: AetherSettings,
    commandPreview: String,
    isFetchingKeys: Boolean,
    onUpdateAether: ((AetherSettings) -> AetherSettings) -> Unit,
    onFetchWarpKeys: (String) -> Unit,
    onClearPsiphonData: () -> Unit,
    onCopyCommand: (String) -> Unit,
    onOpenLogs: () -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text("WARP Key API Overrides", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(
                onClick = { onFetchWarpKeys("ALL") },
                enabled = !isFetchingKeys,
                modifier = Modifier.weight(1f),
            ) {
                Text(if (isFetchingKeys) "Registering..." else "API Key Fallback")
            }
            OutlinedButton(
                onClick = { onFetchWarpKeys("MASQUE") },
                enabled = !isFetchingKeys,
            ) {
                Text("MASQUE")
            }
        }
        OutlinedTextField(
            value = aether.warpApiPrimaryHost,
            onValueChange = { v -> onUpdateAether { it.copy(warpApiPrimaryHost = v) } },
            label = { Text("Primary Host (Fallback: ${aether.warpApiFallbackHost})") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        PnifoxSwitchRow(
            title = "Fragment ClientHello for API calls",
            checked = aether.warpApiUseFragment,
            onCheckedChange = { c -> onUpdateAether { it.copy(warpApiUseFragment = c) } },
        )
        PnifoxSwitchRow(
            title = "Use ECH for API calls",
            checked = aether.warpApiUseEch,
            onCheckedChange = { c -> onUpdateAether { it.copy(warpApiUseEch = c) } },
        )

        HorizontalDivider()
        Text("Protocol & Obfuscation", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            AetherProtocol.entries.forEach { proto ->
                FilterChip(
                    selected = aether.protocol == proto,
                    onClick = { onUpdateAether { it.copy(protocol = proto) } },
                    label = { Text(proto.label) },
                )
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            MasqueTransport.entries.forEach { tr ->
                FilterChip(
                    selected = aether.masqueTransport == tr,
                    onClick = { onUpdateAether { it.copy(masqueTransport = tr) } },
                    label = { Text(tr.label) },
                )
            }
        }
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            AetherNoizeProfile.entries.forEach { nz ->
                FilterChip(
                    selected = aether.noizeProfile == nz,
                    onClick = { onUpdateAether { it.copy(noizeProfile = nz) } },
                    label = { Text("--noize ${nz.label}") },
                )
            }
        }

        HorizontalDivider()
        Text("Fragment & ECH", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        PnifoxSwitchRow(
            title = "Fragment ClientHello",
            checked = aether.fragmentEnabled,
            onCheckedChange = { c -> onUpdateAether { it.copy(fragmentEnabled = c) } },
        )
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(
                value = aether.fragmentSizeBytes,
                onValueChange = { v -> onUpdateAether { it.copy(fragmentSizeBytes = v) } },
                label = { Text("Size (8-16)") },
                singleLine = true,
                modifier = Modifier.weight(1f),
            )
            OutlinedTextField(
                value = aether.fragmentDelayMs,
                onValueChange = { v -> onUpdateAether { it.copy(fragmentDelayMs = v) } },
                label = { Text("Delay ms (2-10)") },
                singleLine = true,
                modifier = Modifier.weight(1f),
            )
        }
        PnifoxSwitchRow(
            title = "Encrypted Client Hello (--ech auto)",
            subtitle = "Off by default (experimental — leave OFF if handshakes fail).",
            checked = aether.echEnabled,
            onCheckedChange = { c -> onUpdateAether { it.copy(echEnabled = c) } },
        )
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(
                value = aether.echDns,
                onValueChange = { v -> onUpdateAether { it.copy(echDns = v) } },
                label = { Text("ECH DNS") },
                singleLine = true,
                modifier = Modifier.weight(1f),
            )
            OutlinedTextField(
                value = aether.echDomain,
                onValueChange = { v -> onUpdateAether { it.copy(echDomain = v) } },
                label = { Text("ECH domain") },
                singleLine = true,
                modifier = Modifier.weight(1f),
            )
        }

        HorizontalDivider()
        Text("Scan Mode & IP Stack", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        PnifoxSwitchRow(
            title = "Scan for Endpoint",
            checked = aether.scanForEndpoint,
            onCheckedChange = { c -> onUpdateAether { it.copy(scanForEndpoint = c) } },
        )
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            AetherScanMode.entries.forEach { sm ->
                FilterChip(
                    selected = aether.scanMode == sm,
                    onClick = { onUpdateAether { it.copy(scanMode = sm) } },
                    label = { Text(sm.label) },
                )
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            IpVersionMode.entries.forEach { ipVer ->
                FilterChip(
                    selected = aether.ipVersion == ipVer,
                    onClick = { onUpdateAether { it.copy(ipVersion = ipVer) } },
                    label = { Text(ipVer.label) },
                )
            }
        }

        HorizontalDivider()
        Text("Exit-Node, Psiphon & Tor", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            ExitDialMode.entries.forEach { dial ->
                FilterChip(
                    selected = aether.exitDialMode == dial,
                    onClick = { onUpdateAether { it.copy(exitDialMode = dial) } },
                    label = { Text("dialMode: ${dial.label}") },
                )
            }
        }
        OutlinedTextField(
            value = aether.finalMaskRawJson,
            onValueChange = { v -> onUpdateAether { it.copy(finalMaskRawJson = v) } },
            label = { Text("finalMask (Optional Raw JSON)") },
            minLines = 2,
            maxLines = 4,
            modifier = Modifier.fillMaxWidth(),
        )
        PnifoxSwitchRow(
            title = "Enable Psiphon Exit",
            checked = aether.psiphonEnabled,
            onCheckedChange = { c -> onUpdateAether { it.copy(psiphonEnabled = c) } },
        )
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            PsiphonChainMode.entries.forEach { mode ->
                FilterChip(
                    selected = aether.psiphonMode == mode,
                    onClick = { onUpdateAether { it.copy(psiphonMode = mode) } },
                    label = { Text(mode.labelEn) },
                )
            }
        }
        PnifoxSwitchRow(
            title = "Start from bundled psiphon_servers.dat",
            checked = aether.psiphonUseBundledServersDat,
            onCheckedChange = { c -> onUpdateAether { it.copy(psiphonUseBundledServersDat = c) } },
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = onClearPsiphonData) { Text("Clear Psiphon Data") }
            FilterChip(
                selected = aether.torEnabled,
                onClick = { onUpdateAether { it.copy(torEnabled = !it.torEnabled) } },
                label = { Text("Tor Exit") },
            )
        }

        HorizontalDivider()
        Text("Command Preview", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        Surface(shape = RoundedCornerShape(12.dp), color = MaterialTheme.colorScheme.surfaceVariant, modifier = Modifier.fillMaxWidth()) {
            Text(text = commandPreview, style = MaterialTheme.typography.labelSmall.copy(fontFamily = FontFamily.Monospace), modifier = Modifier.padding(10.dp))
        }
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = { onCopyCommand(commandPreview) }, modifier = Modifier.weight(1f)) { Text("Copy") }
            OutlinedButton(onClick = onOpenLogs, modifier = Modifier.weight(1f)) { Text("View Logs") }
        }
    }
}
