package ir.pnifox.feature.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Clear
import androidx.compose.material.icons.rounded.Cloud
import androidx.compose.material.icons.rounded.Radar
import androidx.compose.material.icons.rounded.Router
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material.icons.rounded.SwapHoriz
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import ir.pnifox.core.model.PsiphonCdnCatalog
import ir.pnifox.core.model.PsiphonChainMode
import ir.pnifox.core.ui.PnifoxDropdownField
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
                Text(if (isFetchingKeys) "Registering..." else "API Key Fallback", maxLines = 1)
            }
            OutlinedButton(
                onClick = { onFetchWarpKeys("MASQUE") },
                enabled = !isFetchingKeys,
            ) {
                Text("MASQUE", maxLines = 1)
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
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            PnifoxDropdownField(
                label = "Protocol",
                selectedText = aether.protocol.label,
                options = AetherProtocol.entries,
                selectedItem = aether.protocol,
                optionLabel = { it.label },
                onSelect = { proto -> onUpdateAether { it.copy(protocol = proto) } },
                icon = Icons.Rounded.Router,
                modifier = Modifier.weight(1f),
            )
            PnifoxDropdownField(
                label = "Transport",
                selectedText = aether.masqueTransport.label,
                options = MasqueTransport.entries,
                selectedItem = aether.masqueTransport,
                optionLabel = { it.label },
                onSelect = { tr -> onUpdateAether { it.copy(masqueTransport = tr) } },
                icon = Icons.Rounded.SwapHoriz,
                modifier = Modifier.weight(0.9f),
            )
        }
        PnifoxDropdownField(
            label = "Noize",
            selectedText = aether.noizeProfile.label,
            options = AetherNoizeProfile.entries,
            selectedItem = aether.noizeProfile,
            optionLabel = { it.label },
            onSelect = { nz -> onUpdateAether { it.copy(noizeProfile = nz) } },
            icon = Icons.Rounded.Shield,
            modifier = Modifier.fillMaxWidth(),
        )

        HorizontalDivider()
        Text("Fragment & ECH", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        PnifoxSwitchRow(
            title = "Fragment ClientHello",
            checked = aether.fragmentEnabled,
            onCheckedChange = { c -> onUpdateAether { it.copy(fragmentEnabled = c) } },
        )
        if (aether.fragmentEnabled) {
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
        }
        PnifoxSwitchRow(
            title = "ECH (Default Off)",
            checked = aether.echEnabled,
            onCheckedChange = { c -> onUpdateAether { it.copy(echEnabled = c) } },
        )
        if (aether.echEnabled) {
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
        }

        HorizontalDivider()
        Text("Scan Mode & IP Stack", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        PnifoxSwitchRow(
            title = "Scan for Endpoint",
            checked = aether.scanForEndpoint,
            onCheckedChange = { c -> onUpdateAether { it.copy(scanForEndpoint = c) } },
        )
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            PnifoxDropdownField(
                label = "Scan",
                selectedText = aether.scanMode.label,
                options = AetherScanMode.entries,
                selectedItem = aether.scanMode,
                optionLabel = { it.label },
                onSelect = { sm -> onUpdateAether { it.copy(scanMode = sm) } },
                icon = Icons.Rounded.Radar,
                modifier = Modifier.weight(1f),
            )
            PnifoxDropdownField(
                label = "IP",
                selectedText = aether.ipVersion.label,
                options = IpVersionMode.entries,
                selectedItem = aether.ipVersion,
                optionLabel = { it.label },
                onSelect = { iv -> onUpdateAether { it.copy(ipVersion = iv) } },
                modifier = Modifier.weight(0.85f),
            )
        }

        HorizontalDivider()
        Text("Exit-Node, Psiphon & Tor", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        PnifoxDropdownField(
            label = "dialMode",
            selectedText = aether.exitDialMode.label,
            options = ExitDialMode.entries,
            selectedItem = aether.exitDialMode,
            optionLabel = { it.label },
            onSelect = { dial -> onUpdateAether { it.copy(exitDialMode = dial) } },
            modifier = Modifier.fillMaxWidth(),
        )
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
        val setOptions = listOf("" to "All Sets (Auto)") + PsiphonCdnCatalog.availableCdnSets
        val activeSet = setOptions.firstOrNull { it.first.equals(aether.psiphonCdnSets.trim(), ignoreCase = true) } ?: setOptions.first()
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            PnifoxDropdownField(
                label = "Mode",
                selectedText = aether.psiphonMode.labelEn,
                options = PsiphonChainMode.entries,
                selectedItem = aether.psiphonMode,
                optionLabel = { it.labelEn },
                onSelect = { mode -> onUpdateAether { it.copy(psiphonMode = mode) } },
                modifier = Modifier.weight(1f),
            )
            PnifoxDropdownField(
                label = "CDN Set",
                selectedText = activeSet.second,
                options = setOptions,
                selectedItem = activeSet,
                optionLabel = { it.second },
                onSelect = { pair -> onUpdateAether { it.copy(psiphonCdnSets = pair.first) } },
                icon = Icons.Rounded.Cloud,
                modifier = Modifier.weight(1f),
            )
        }
        OutlinedTextField(
            value = aether.psiphonCdnFrontingIps,
            onValueChange = { v -> onUpdateAether { it.copy(psiphonCdnFrontingIps = v) } },
            label = { Text("CDN Edge IPs / CIDRs (Akamai, Cloudflare)") },
            trailingIcon = if (aether.psiphonCdnFrontingIps.isNotBlank()) {
                {
                    IconButton(onClick = { onUpdateAether { it.copy(psiphonCdnFrontingIps = "") } }) {
                        Icon(Icons.Rounded.Clear, contentDescription = "Clear CDN IPs", modifier = Modifier.size(16.dp))
                    }
                }
            } else null,
            minLines = 2,
            maxLines = 4,
            modifier = Modifier.fillMaxWidth(),
        )
        OutlinedTextField(
            value = aether.psiphonCdnSniHostnames,
            onValueChange = { v -> onUpdateAether { it.copy(psiphonCdnSniHostnames = v) } },
            label = { Text("CDN SNI Hostnames (comma/space/newline)") },
            minLines = 2,
            maxLines = 4,
            modifier = Modifier.fillMaxWidth(),
        )
        PnifoxSwitchRow(
            title = "Start from bundled psiphon_servers.dat",
            checked = aether.psiphonUseBundledServersDat,
            onCheckedChange = { c -> onUpdateAether { it.copy(psiphonUseBundledServersDat = c) } },
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = onClearPsiphonData) { Text("Clear Psiphon Data", maxLines = 1) }
            FilterChip(
                selected = aether.torEnabled,
                onClick = { onUpdateAether { it.copy(torEnabled = !it.torEnabled) } },
                label = { Text("Tor Exit", maxLines = 1) },
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
