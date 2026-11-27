package ir.pnifox.feature.home

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Bolt
import androidx.compose.material.icons.rounded.ExpandLess
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import ir.pnifox.core.model.AppLanguage
import ir.pnifox.core.model.PnifoxScenarioCatalog
import ir.pnifox.core.model.ProtocolExplainerCatalog
import ir.pnifox.core.model.ScenarioGoalId
import ir.pnifox.core.model.ScenarioProfile
import ir.pnifox.core.ui.LocalAppLanguage
import ir.pnifox.core.ui.PnifoxBadgePill
import ir.pnifox.core.ui.PnifoxCard
import ir.pnifox.core.ui.PnifoxSectionLabel
import ir.pnifox.core.ui.tr

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ScenarioPresetsCard(
    selectedScenario: ScenarioGoalId?,
    isSmartConnecting: Boolean,
    smartConnectStatus: String?,
    cardPadding: androidx.compose.ui.unit.Dp,
    onApplyScenario: (ScenarioProfile, Boolean) -> Unit,
    onSmartAutoConnect: () -> Unit,
    onOpenProtocolGuide: () -> Unit,
) {
    val isFa = LocalAppLanguage.current == AppLanguage.PERSIAN
    var expandedList by remember { mutableStateOf(true) }

    PnifoxCard(contentPadding = cardPadding) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                PnifoxSectionLabel(
                    title = tr("Choose Your Goal (Presets)", "حالت‌های آماده بر اساس نیاز شما"),
                )
                Text(
                    text = tr(
                        "Pick what you want to do — pnifox tunes protocol, DNS & bypass automatically.",
                        "هدفتان را انتخاب کنید تا بهترین روش و تنظیمات با توضیح شفاف اعمال شود.",
                    ),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            TextButton(onClick = { expandedList = !expandedList }) {
                Icon(if (expandedList) Icons.Rounded.ExpandLess else Icons.Rounded.ExpandMore, null)
            }
        }

        Spacer(Modifier.height(8.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Button(
                onClick = onSmartAutoConnect,
                enabled = !isSmartConnecting,
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0EA5E9), contentColor = Color.White),
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier.weight(1f),
            ) {
                if (isSmartConnecting) {
                    CircularProgressIndicator(modifier = Modifier.size(15.dp), strokeWidth = 2.dp, color = Color.White)
                } else {
                    Icon(Icons.Rounded.AutoAwesome, null, modifier = Modifier.size(16.dp))
                }
                Spacer(Modifier.width(6.dp))
                Text(
                    text = if (isSmartConnecting) tr("Finding Best...", "در حال تست...") else tr("Smart Auto-Connect", "اتصال هوشمند"),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                )
            }
            OutlinedButton(
                onClick = onOpenProtocolGuide,
                shape = RoundedCornerShape(10.dp),
            ) {
                Icon(Icons.Rounded.Info, null, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(5.dp))
                Text(tr("Protocols", "روش‌های اتصال"), style = MaterialTheme.typography.labelMedium)
            }
        }

        if (!smartConnectStatus.isNullOrBlank()) {
            Spacer(Modifier.height(6.dp))
            Text(smartConnectStatus, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
        }

        AnimatedVisibility(visible = expandedList) {
            Column(
                modifier = Modifier.fillMaxWidth().padding(top = 10.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                PnifoxScenarioCatalog.scenarios.forEach { profile ->
                    val selected = selectedScenario == profile.id
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = if (selected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.65f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                        border = BorderStroke(
                            width = if (selected) 1.5.dp else 1.dp,
                            color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline.copy(alpha = 0.5f),
                        ),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Column(
                            modifier = Modifier.fillMaxWidth().padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(5.dp),
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                Text(
                                    text = if (isFa) profile.id.titleFa else profile.id.titleEn,
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.weight(1f),
                                )
                                PnifoxBadgePill(
                                    text = if (isFa) profile.badgeFa else profile.badgeEn,
                                    containerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.16f),
                                    contentColor = MaterialTheme.colorScheme.primary,
                                )
                            }
                            Text(
                                text = if (isFa) profile.subtitleFa else profile.subtitleEn,
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = FontWeight.SemiBold,
                            )
                            Text(
                                text = if (isFa) profile.explanationFa else profile.explanationEn,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Text(
                                text = if (isFa) "نکته: ${profile.tradeOffFa}" else "Note: ${profile.tradeOffEn}",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.secondary,
                            )
                            FlowRow(
                                modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                Button(
                                    onClick = { onApplyScenario(profile, true) },
                                    shape = RoundedCornerShape(8.dp),
                                ) {
                                    Icon(Icons.Rounded.Bolt, null, modifier = Modifier.size(14.dp))
                                    Spacer(Modifier.width(4.dp))
                                    Text(tr("Apply & Connect", "اعمال و اتصال"), style = MaterialTheme.typography.labelSmall)
                                }
                                OutlinedButton(
                                    onClick = { onApplyScenario(profile, false) },
                                    shape = RoundedCornerShape(8.dp),
                                ) {
                                    Text(tr("Select Preset", "فقط انتخاب"), style = MaterialTheme.typography.labelSmall)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun ProtocolGuideDialog(
    onDismiss: () -> Unit,
) {
    val isFa = LocalAppLanguage.current == AppLanguage.PERSIAN
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                tr("Connection Methods & Protocols", "آشنایی با روش‌های اتصال و پروتکل‌ها"),
                fontWeight = FontWeight.Bold,
            )
        },
        text = {
            LazyColumn(
                modifier = Modifier.fillMaxWidth().heightIn(max = 420.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(ProtocolExplainerCatalog.items, key = { it.id }) { item ->
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                            ) {
                                Text(
                                    text = if (isFa) item.nameFa else item.nameEn,
                                    fontWeight = FontWeight.Bold,
                                    style = MaterialTheme.typography.bodyMedium,
                                    modifier = Modifier.weight(1f),
                                )
                                PnifoxBadgePill(
                                    text = item.badge,
                                    containerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.16f),
                                    contentColor = MaterialTheme.colorScheme.primary,
                                )
                            }
                            Text(
                                text = if (isFa) item.categoryFa else item.categoryEn,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.primary,
                            )
                            Text(if (isFa) item.summaryFa else item.summaryEn, style = MaterialTheme.typography.bodySmall)
                            Text(
                                text = if (isFa) "بهترین کاربرد: ${item.bestForFa}" else "Best for: ${item.bestForEn}",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.secondary,
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(onClick = onDismiss) {
                Text(tr("Close", "بستن"))
            }
        },
    )
}
