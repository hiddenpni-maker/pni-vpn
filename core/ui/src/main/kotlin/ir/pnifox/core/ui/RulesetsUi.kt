package ir.pnifox.core.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Block
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.ExpandLess
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.Lan
import androidx.compose.material.icons.rounded.Public
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import ir.pnifox.core.model.AppSettings

/**
 * Reusable Rulesets controls shared between Settings and the quick-access Rulesets bottom sheet:
 * - Whitelist Iran domestic websites & apps (bypass tunnel, direct access)
 * - AdBlock & tracker blocking
 * - Private LAN bypass + custom direct/block entries
 */
@Composable
fun RulesetsEditorContent(
    appSettings: AppSettings,
    onUpdateApp: ((AppSettings) -> AppSettings) -> Unit,
    isTunnelConnected: Boolean = false,
    onApplyAndReconnect: (() -> Unit)? = null,
) {
    var expandCustom by remember { mutableStateOf(appSettings.customDirectRules.isNotBlank() || appSettings.customBlockRules.isNotBlank()) }

    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        PnifoxSwitchRow(
            title = tr(
                "Whitelist Iran Domestic Sites (Bypass VPN)",
                "وایت‌لیست سایت‌ها و اپ‌های ایرانی (عبور مستقیم)",
            ),
            subtitle = tr(
                "Route .ir & Iranian apps (banks, Snapp, Digikala, Aparat) directly without needing anti-sanction",
                "عبور مستقیم سایت‌های .ir، بانک‌ها و اپ‌های ایرانی بدون نیاز به تحریم‌شکن",
            ),
            checked = appSettings.bypassIranEnabled,
            onCheckedChange = { c -> onUpdateApp { it.copy(bypassIranEnabled = c) } },
            icon = Icons.Rounded.Public,
        )
        PnifoxSwitchRow(
            title = tr(
                "Block Ads & Trackers (AdBlock)",
                "بلاک کردن تبلیغات و ردیاب‌ها (AdBlock)",
            ),
            subtitle = tr(
                "Blocks ad networks and tracking scripts cleanly across AETHER, Xray, Sing-box & DNS",
                "مسدودسازی شبکه‌های تبلیغاتی و ردیاب‌ها در همه حالت‌های اتصال",
            ),
            checked = appSettings.blockAdsEnabled,
            onCheckedChange = { c -> onUpdateApp { it.copy(blockAdsEnabled = c) } },
            icon = Icons.Rounded.Block,
        )
        PnifoxSwitchRow(
            title = tr(
                "Bypass Private LAN & Router IPs",
                "عبور مستقیم شبکه محلی (مودم و LAN)",
            ),
            subtitle = tr(
                "Keep 10.x / 192.168.x local devices reachable while connected",
                "دسترسی مستقیم به مودم و آی‌پی‌های شبکه محلی",
            ),
            checked = appSettings.bypassPrivateLanEnabled,
            onCheckedChange = { c -> onUpdateApp { it.copy(bypassPrivateLanEnabled = c) } },
            icon = Icons.Rounded.Lan,
        )

        Spacer(Modifier.height(4.dp))
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { expandCustom = !expandCustom }
                .padding(vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = tr("Custom Direct / Block Domains & IPs", "افزودن دامنه یا IP مستقیم / بلاک دلخواه"),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.weight(1f),
            )
            Icon(if (expandCustom) Icons.Rounded.ExpandLess else Icons.Rounded.ExpandMore, null)
        }

        AnimatedVisibility(visible = expandCustom) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 6.dp)) {
                OutlinedTextField(
                    value = appSettings.customDirectRules,
                    onValueChange = { v -> onUpdateApp { it.copy(customDirectRules = v) } },
                    label = { Text(tr("Custom Whitelist (Direct) — one per line", "لیست سفید مستقیم (هر خط یک دامنه یا IP)")) },
                    placeholder = { Text("example.ir\nbank.ir\n192.168.1.0/24") },
                    minLines = 2,
                    maxLines = 5,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = appSettings.customBlockRules,
                    onValueChange = { v -> onUpdateApp { it.copy(customBlockRules = v) } },
                    label = { Text(tr("Custom Blocklist — one per line", "لیست مسدودسازی دلخواه (هر خط یک دامنه یا keyword:)")) },
                    placeholder = { Text("ads.example.com\nkeyword:tracker") },
                    minLines = 2,
                    maxLines = 5,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }

        if (isTunnelConnected && onApplyAndReconnect != null) {
            Spacer(Modifier.height(8.dp))
            OutlinedButton(
                onClick = onApplyAndReconnect,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
            ) {
                Text(tr("Apply Rules & Reconnect Tunnel", "اعمال قوانین و اتصال مجدد"))
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RulesetsBottomSheet(
    appSettings: AppSettings,
    onUpdateApp: ((AppSettings) -> AppSettings) -> Unit,
    isTunnelConnected: Boolean = false,
    onApplyAndReconnect: (() -> Unit)? = null,
    onDismiss: () -> Unit,
) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 6.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    text = tr("Rulesets · Bypass Iran & AdBlock", "قوانین مسیریابی · وایت‌لیست ایران و بلاک تبلیغات"),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f),
                )
                IconButton(onClick = onDismiss) {
                    Icon(Icons.Rounded.Close, contentDescription = "Close")
                }
            }
            Spacer(Modifier.height(6.dp))
            RulesetsEditorContent(
                appSettings = appSettings,
                onUpdateApp = onUpdateApp,
                isTunnelConnected = isTunnelConnected,
                onApplyAndReconnect = onApplyAndReconnect,
            )
            Spacer(Modifier.height(10.dp))
            Button(
                onClick = onDismiss,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp),
            ) {
                Text(tr("Done", "بستن"))
            }
            Spacer(Modifier.height(16.dp))
        }
    }
}
