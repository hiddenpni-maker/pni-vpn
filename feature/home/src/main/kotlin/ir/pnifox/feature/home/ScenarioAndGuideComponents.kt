package ir.pnifox.feature.home

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
import androidx.compose.material.icons.rounded.Bolt
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import ir.pnifox.core.model.AppLanguage
import ir.pnifox.core.model.PnifoxScenarioCatalog
import ir.pnifox.core.model.ProtocolExplainerCatalog
import ir.pnifox.core.model.ScenarioGoalId
import ir.pnifox.core.model.ScenarioProfile
import ir.pnifox.core.ui.LocalAppLanguage
import ir.pnifox.core.ui.PnifoxBadgePill
import ir.pnifox.core.ui.PnifoxCard
import ir.pnifox.core.ui.PnifoxDropdownField
import ir.pnifox.core.ui.tr

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ScenarioPresetsCard(
    selectedScenario: ScenarioGoalId?,
    cardPadding: androidx.compose.ui.unit.Dp,
    onApplyScenario: (ScenarioProfile, Boolean) -> Unit,
    onOpenProtocolGuide: () -> Unit,
) {
    val isFa = LocalAppLanguage.current == AppLanguage.PERSIAN
    val activeProfile = PnifoxScenarioCatalog.scenarios.firstOrNull { it.id == selectedScenario }
        ?: PnifoxScenarioCatalog.scenarios.first()

    PnifoxCard(contentPadding = cardPadding) {
        FlowRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            PnifoxDropdownField(
                label = tr("Preset", "حالت آماده"),
                selectedText = if (isFa) activeProfile.id.titleFa else activeProfile.id.titleEn,
                options = PnifoxScenarioCatalog.scenarios,
                selectedItem = activeProfile,
                optionLabel = { p -> if (isFa) p.id.titleFa else p.id.titleEn },
                onSelect = { p -> onApplyScenario(p, false) },
                icon = Icons.Rounded.Tune,
                modifier = Modifier.fillMaxWidth(),
            )
            Button(
                onClick = { onApplyScenario(activeProfile, true) },
                shape = RoundedCornerShape(10.dp),
            ) {
                Icon(Icons.Rounded.Bolt, null, modifier = Modifier.size(15.dp))
                Spacer(Modifier.width(4.dp))
                Text(tr("Connect", "اتصال سریع"), style = MaterialTheme.typography.labelSmall, maxLines = 1)
            }
            OutlinedButton(
                onClick = onOpenProtocolGuide,
                shape = RoundedCornerShape(10.dp),
            ) {
                Icon(Icons.Rounded.Info, null, modifier = Modifier.size(15.dp))
                Spacer(Modifier.width(4.dp))
                Text(tr("Guide", "توضیحات"), style = MaterialTheme.typography.labelSmall, maxLines = 1)
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
                tr("Modes & Protocols Guide", "توضیحات روش‌های اتصال و کاربری‌ها"),
                fontWeight = FontWeight.Bold,
            )
        },
        text = {
            LazyColumn(
                modifier = Modifier.fillMaxWidth().heightIn(max = 420.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                item {
                    Text(
                        tr("Use-Case Presets", "حالت‌های آماده بر اساس نیاز"),
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Bold,
                    )
                }
                items(PnifoxScenarioCatalog.scenarios, key = { it.id.name }) { profile ->
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                Text(
                                    text = if (isFa) profile.id.titleFa else profile.id.titleEn,
                                    fontWeight = FontWeight.Bold,
                                    style = MaterialTheme.typography.bodyMedium,
                                    modifier = Modifier.weight(1f),
                                )
                                PnifoxBadgePill(
                                    text = if (isFa) profile.badgeFa else profile.badgeEn,
                                    containerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.16f),
                                    contentColor = MaterialTheme.colorScheme.primary,
                                )
                            }
                            Text(if (isFa) profile.explanationFa else profile.explanationEn, style = MaterialTheme.typography.bodySmall)
                            Text(
                                text = if (isFa) profile.tradeOffFa else profile.tradeOffEn,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.secondary,
                            )
                        }
                    }
                }
                item {
                    Spacer(Modifier.height(4.dp))
                    Text(
                        tr("Protocols Explained", "مقایسه روش‌های اتصال"),
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Bold,
                    )
                }
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
                            Text(if (isFa) item.summaryFa else item.summaryEn, style = MaterialTheme.typography.bodySmall)
                            Text(
                                text = if (isFa) "کاربرد: ${item.bestForFa}" else "Best for: ${item.bestForEn}",
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
