package ir.pnifox.desktop.ui

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowDownward
import androidx.compose.material.icons.rounded.ArrowUpward
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.DeleteSweep
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.PowerSettingsNew
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Speed
import androidx.compose.material.icons.rounded.Terminal
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import ir.pnifox.core.model.ConnectionError
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.platform.Font
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import ir.pnifox.core.model.AccentColorPref
import ir.pnifox.core.model.ActiveSectionEngine
import ir.pnifox.core.model.AppLanguage
import ir.pnifox.core.model.LogEntry
import ir.pnifox.core.model.LogLevel
import ir.pnifox.core.model.ThemeModePref
import ir.pnifox.core.model.TrafficFormatters
import ir.pnifox.core.model.TunnelState
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

val LocalDesktopLanguage = staticCompositionLocalOf { AppLanguage.ENGLISH }

@Composable
fun dtr(en: String, fa: String): String =
    if (LocalDesktopLanguage.current == AppLanguage.PERSIAN) fa else en

private val desktopVazirmatnFamily: FontFamily = runCatching {
    FontFamily(
        Font("fonts/vazirmatn_regular.ttf", FontWeight.Normal),
        Font("fonts/vazirmatn_medium.ttf", FontWeight.Medium),
        Font("fonts/vazirmatn_bold.ttf", FontWeight.Bold),
    )
}.getOrDefault(FontFamily.SansSerif)

@Composable
fun PnifoxDesktopTheme(
    language: AppLanguage,
    themeMode: ThemeModePref,
    accent: AccentColorPref,
    content: @Composable () -> Unit,
) {
    val primary = Color(accent.colorHex)
    val dark = themeMode != ThemeModePref.LIGHT
    val colorScheme = if (dark) {
        darkColorScheme(
            primary = primary,
            onPrimary = Color(0xFF1A0E07),
            primaryContainer = primary.copy(alpha = 0.22f),
            onPrimaryContainer = Color(0xFFFFEDE2),
            secondary = Color(0xFF38BDF8),
            background = Color(0xFF0A0D14),
            surface = Color(0xFF121722),
            surfaceVariant = Color(0xFF1A2232),
            onBackground = Color(0xFFF1F5F9),
            onSurface = Color(0xFFF1F5F9),
            onSurfaceVariant = Color(0xFF94A3B8),
            outline = Color(0xFF283246),
            error = Color(0xFFEF4444),
        )
    } else {
        lightColorScheme(
            primary = primary,
            onPrimary = Color.White,
            primaryContainer = primary.copy(alpha = 0.14f),
            onPrimaryContainer = Color(0xFF7C2D12),
            secondary = Color(0xFF0284C7),
            background = Color(0xFFF6F7FB),
            surface = Color.White,
            surfaceVariant = Color(0xFFEEF1F7),
            onBackground = Color(0xFF0F172A),
            onSurface = Color(0xFF0F172A),
            onSurfaceVariant = Color(0xFF475569),
            outline = Color(0xFFCBD5E1),
            error = Color(0xFFDC2626),
        )
    }

    val typography = remember(language) {
        val base = Typography()
        val fam = if (language == AppLanguage.PERSIAN) desktopVazirmatnFamily else FontFamily.SansSerif
        base.copy(
            displaySmall = base.displaySmall.copy(fontFamily = fam),
            headlineSmall = base.headlineSmall.copy(fontFamily = fam),
            titleLarge = base.titleLarge.copy(fontFamily = fam),
            titleMedium = base.titleMedium.copy(fontFamily = fam),
            titleSmall = base.titleSmall.copy(fontFamily = fam),
            bodyLarge = base.bodyLarge.copy(fontFamily = fam),
            bodyMedium = base.bodyMedium.copy(fontFamily = fam),
            bodySmall = base.bodySmall.copy(fontFamily = fam),
            labelLarge = base.labelLarge.copy(fontFamily = fam),
            labelMedium = base.labelMedium.copy(fontFamily = fam),
            labelSmall = base.labelSmall.copy(fontFamily = fam),
        )
    }

    val dir = if (language == AppLanguage.PERSIAN) LayoutDirection.Rtl else LayoutDirection.Ltr
    CompositionLocalProvider(
        LocalDesktopLanguage provides language,
        LocalLayoutDirection provides dir,
    ) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = typography,
            content = content,
        )
    }
}

@Composable
fun DesktopCard(
    modifier: Modifier = Modifier,
    containerColor: Color = MaterialTheme.colorScheme.surface,
    borderColor: Color = MaterialTheme.colorScheme.outline.copy(alpha = 0.7f),
    padding: Dp = 14.dp,
    content: @Composable ColumnScope.() -> Unit,
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        color = containerColor,
        border = BorderStroke(1.dp, borderColor),
    ) {
        Column(modifier = Modifier.padding(padding), content = content)
    }
}

@Composable
fun DesktopBadgePill(
    text: String,
    containerColor: Color = MaterialTheme.colorScheme.primaryContainer,
    contentColor: Color = MaterialTheme.colorScheme.primary,
) {
    Surface(
        shape = RoundedCornerShape(999.dp),
        color = containerColor,
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelSmall,
            color = contentColor,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
        )
    }
}

@Composable
fun DesktopSwitchRow(
    title: String,
    subtitle: String? = null,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onCheckedChange(!checked) }
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
            if (!subtitle.isNullOrBlank()) {
                Text(subtitle, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        Spacer(Modifier.width(12.dp))
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

@Composable
fun DesktopConnectButton(
    isConnected: Boolean,
    isConnecting: Boolean,
    onClick: () -> Unit,
    size: Dp = 76.dp,
) {
    val transition = rememberInfiniteTransition(label = "desktop_connect_btn")
    val spinAngle by transition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(animation = tween(1050, easing = LinearEasing), repeatMode = RepeatMode.Restart),
        label = "spin",
    )

    val coreColor = when {
        isConnected -> Color(0xFF10B981)
        isConnecting -> Color(0xFFF97316)
        else -> Color(0xFFEF4444)
    }

    Box(
        modifier = Modifier.size(size + 14.dp),
        contentAlignment = Alignment.Center,
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val radius = (this.size.minDimension / 2f) - 4.dp.toPx()
            if (isConnecting) {
                rotate(spinAngle) {
                    drawArc(
                        brush = Brush.sweepGradient(
                            listOf(
                                Color(0xFFF97316).copy(alpha = 0.05f),
                                Color(0xFFFACC15),
                                Color(0xFFF97316),
                            ),
                        ),
                        startAngle = 0f,
                        sweepAngle = 290f,
                        useCenter = false,
                        topLeft = Offset(center.x - radius, center.y - radius),
                        size = Size(radius * 2f, radius * 2f),
                        style = Stroke(width = 4.dp.toPx(), cap = StrokeCap.Round),
                    )
                }
            } else {
                drawCircle(
                    color = coreColor.copy(alpha = 0.28f),
                    radius = radius,
                    style = Stroke(width = 2.5.dp.toPx()),
                )
            }
        }
        Surface(
            modifier = Modifier
                .size(size)
                .clip(CircleShape)
                .clickable(onClick = onClick),
            shape = CircleShape,
            color = coreColor,
            shadowElevation = 6.dp,
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = Icons.Rounded.PowerSettingsNew,
                    contentDescription = "Connect / Disconnect",
                    tint = Color.White,
                    modifier = Modifier.size(size * 0.45f),
                )
            }
        }
    }
}

@Composable
fun DesktopFoxMascot(
    tunnelState: TunnelState,
    section: ActiveSectionEngine,
    onFallbackSystemProxy: (() -> Unit)? = null,
    onRestartAdmin: (() -> Unit)? = null,
    showTunSuggestion: Boolean = false,
    onEnableTunMode: (() -> Unit)? = null,
    onDismissTunSuggestion: (() -> Unit)? = null,
) {
    val isConnecting = tunnelState is TunnelState.Connecting && tunnelState.section == section
    val isConnected = tunnelState is TunnelState.Connected && tunnelState.section == section
    val errState = (tunnelState as? TunnelState.Error)?.takeIf { it.section == section }
    val isError = errState != null
    val isPermissionDenied = errState?.error is ConnectionError.PermissionDenied

    val transition = rememberInfiniteTransition(label = "desktop_fox")
    val runPhase by transition.animateFloat(
        initialValue = 0f,
        targetValue = (2f * PI).toFloat(),
        animationSpec = infiniteRepeatable(animation = tween(520, easing = LinearEasing), repeatMode = RepeatMode.Restart),
        label = "run_phase",
    )
    val breathe by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(animation = tween(1400, easing = FastOutSlowInEasing), repeatMode = RepeatMode.Reverse),
        label = "breathe",
    )

    val foxOrange = Color(0xFFF97316)
    val badgeBg = when {
        isConnected -> Color(0xFF10B981).copy(alpha = 0.14f)
        isConnecting -> Color(0xFFF97316).copy(alpha = 0.15f)
        isError -> Color(0xFFEF4444).copy(alpha = 0.14f)
        else -> MaterialTheme.colorScheme.surfaceVariant
    }

    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Surface(
                shape = RoundedCornerShape(14.dp),
                color = badgeBg,
                modifier = Modifier.size(62.dp),
            ) {
                Canvas(modifier = Modifier.fillMaxSize().padding(6.dp)) {
                    val w = this.size.width
                    val h = this.size.height
                    val bob = if (isConnecting) sin(runPhase) * 2.5f else -breathe * 1.6f
                    val legSwing = if (isConnecting) sin(runPhase) * (w * 0.12f) else 0f

                    if (isConnecting) {
                        drawLine(
                            Color(0xFF94A3B8).copy(alpha = 0.45f),
                            Offset(w * 0.05f, h * 0.4f),
                            Offset(w * 0.24f, h * 0.4f),
                            strokeWidth = 2f,
                            cap = StrokeCap.Round,
                        )
                    }

                    // Tail
                    drawLine(
                        color = foxOrange,
                        start = Offset(w * 0.3f, h * 0.68f + bob),
                        end = Offset(w * 0.1f, h * 0.56f + bob),
                        strokeWidth = w * 0.12f,
                        cap = StrokeCap.Round,
                    )
                    // Body
                    drawRoundRect(
                        color = foxOrange,
                        topLeft = Offset(w * 0.28f, h * 0.52f + bob),
                        size = Size(w * 0.42f, h * 0.24f),
                        cornerRadius = CornerRadius(8f, 8f),
                    )
                    // Legs
                    drawLine(Color(0xFF1E293B), Offset(w * 0.36f, h * 0.74f + bob), Offset(w * 0.34f - legSwing, h * 0.90f), strokeWidth = 3f, cap = StrokeCap.Round)
                    drawLine(Color(0xFF1E293B), Offset(w * 0.60f, h * 0.74f + bob), Offset(w * 0.62f + legSwing, h * 0.90f), strokeWidth = 3f, cap = StrokeCap.Round)
                    // Head
                    val hx = w * 0.72f
                    val hy = h * 0.43f + bob
                    drawCircle(color = foxOrange, radius = w * 0.17f, center = Offset(hx, hy))
                    drawFoxEar(Offset(hx - w * 0.10f, hy - w * 0.10f), w * 0.13f, foxOrange)
                    drawFoxEar(Offset(hx + w * 0.04f, hy - w * 0.12f), w * 0.13f, foxOrange)
                    // Eye
                    val eyeColor = if (isError) Color(0xFFEF4444) else Color(0xFF0F172A)
                    drawCircle(color = eyeColor, radius = 2.6f, center = Offset(hx + w * 0.06f, hy - 1.5f))
                    if (isConnected) {
                        drawCircle(color = Color(0xFF10B981), radius = 4f, center = Offset(w * 0.88f, h * 0.2f))
                    }
                }
            }
            Column(modifier = Modifier.weight(1f)) {
                val headline = when {
                    isConnected -> dtr("Connected", "متصل")
                    isConnecting -> dtr("Connecting...", "در حال اتصال...")
                    errState != null -> dtr(errState.error.titleEn, errState.error.titleFa)
                    else -> dtr("Ready", "آماده اتصال")
                }
                val sub = when {
                    isConnecting -> (tunnelState as TunnelState.Connecting).let { dtr(it.stageLabelEn, it.stageLabelFa) }
                    isConnected -> (tunnelState as TunnelState.Connected).activeEndpoint
                    errState != null -> dtr(errState.error.reasonEn, errState.error.reasonFa)
                    else -> dtr("Tap power to start tunnel", "برای شروع دکمه اتصال را بزنید")
                }
                Text(headline, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(
                    text = sub,
                    style = MaterialTheme.typography.bodySmall,
                    color = if (isError) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }

        if (isPermissionDenied) {
            Surface(
                shape = RoundedCornerShape(10.dp),
                color = MaterialTheme.colorScheme.surfaceVariant,
                modifier = Modifier.fillMaxWidth(),
            ) {
                @OptIn(ExperimentalLayoutApi::class)
                FlowRow(
                    modifier = Modifier.fillMaxWidth().padding(10.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    if (onFallbackSystemProxy != null) {
                        Button(
                            onClick = onFallbackSystemProxy,
                            shape = RoundedCornerShape(8.dp),
                        ) {
                            Text(
                                dtr("Connect via System Proxy", "اتصال بدون ادمین (پروکسی)"),
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                    if (onRestartAdmin != null) {
                        OutlinedButton(
                            onClick = onRestartAdmin,
                            shape = RoundedCornerShape(8.dp),
                        ) {
                            Text(
                                dtr("Restart as Admin (TUN)", "اجرا با ادمین (TUN)"),
                                style = MaterialTheme.typography.labelMedium,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                }
            }
        } else if (isConnected && showTunSuggestion) {
            Surface(
                shape = RoundedCornerShape(10.dp),
                color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.55f),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = dtr(
                            "System Proxy active — enable TUN Mode to tunnel games & all apps.",
                            "حالت پروکسی فعال است؛ برای عبور بازی‌ها و کل برنامه‌ها، TUN Mode را روشن کنید.",
                        ),
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.weight(1f),
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    if (onEnableTunMode != null) {
                        Button(onClick = onEnableTunMode, shape = RoundedCornerShape(8.dp)) {
                            Text(dtr("Enable TUN", "روشن کردن TUN"), style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, maxLines = 1)
                        }
                    }
                    if (onDismissTunSuggestion != null) {
                        TextButton(onClick = onDismissTunSuggestion) {
                            Text(dtr("Hide", "بستن"), style = MaterialTheme.typography.labelSmall, maxLines = 1)
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun <T> DesktopDropdownButton(
    label: String,
    selectedText: String,
    options: List<T>,
    optionLabel: (T) -> String,
    onSelect: (T) -> Unit,
    selectedItem: T? = null,
    icon: ImageVector? = null,
    modifier: Modifier = Modifier,
) {
    var expanded by remember { mutableStateOf(false) }
    Box(modifier = modifier) {
        Surface(
            onClick = { expanded = true },
            shape = RoundedCornerShape(10.dp),
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.72f),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.65f)),
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 7.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                if (icon != null) {
                    Icon(icon, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(16.dp))
                }
                Text(
                    text = if (label.isNotBlank()) "$label: $selectedText" else selectedText,
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false),
                )
                Icon(Icons.Rounded.KeyboardArrowDown, null, modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            modifier = Modifier.heightIn(max = 360.dp),
        ) {
            options.forEach { item ->
                val isSelected = selectedItem == item
                DropdownMenuItem(
                    text = {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(
                                text = optionLabel(item),
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            if (isSelected) {
                                Icon(Icons.Rounded.Check, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(15.dp))
                            }
                        }
                    },
                    onClick = {
                        expanded = false
                        onSelect(item)
                    },
                )
            }
        }
    }
}

@Composable
fun DesktopInfoPopupButton(
    title: String,
    body: String,
    buttonLabel: String = dtr("Info", "توضیحات"),
    modifier: Modifier = Modifier,
) {
    var open by remember { mutableStateOf(false) }
    OutlinedButton(
        onClick = { open = true },
        shape = RoundedCornerShape(10.dp),
        modifier = modifier,
    ) {
        Icon(Icons.Rounded.Info, null, modifier = Modifier.size(15.dp))
        Spacer(Modifier.width(5.dp))
        Text(buttonLabel, style = MaterialTheme.typography.labelSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
    if (open) {
        AlertDialog(
            onDismissRequest = { open = false },
            title = { Text(title, fontWeight = FontWeight.Bold) },
            text = {
                Column(modifier = Modifier.fillMaxWidth().heightIn(max = 380.dp).verticalScroll(rememberScrollState())) {
                    Text(body, style = MaterialTheme.typography.bodySmall)
                }
            },
            confirmButton = {
                TextButton(onClick = { open = false }) {
                    Text(dtr("Close", "بستن"))
                }
            },
        )
    }
}

private fun DrawScope.drawFoxEar(base: Offset, s: Float, color: Color) {
    val path = Path().apply {
        moveTo(base.x - s * 0.45f, base.y + s * 0.15f)
        lineTo(base.x, base.y - s)
        lineTo(base.x + s * 0.5f, base.y + s * 0.2f)
        close()
    }
    drawPath(path, color = color)
}

@Composable
fun DesktopTelemetryBar(
    rxSpeedBps: Long,
    txSpeedBps: Long,
    sessionRxBytes: Long,
    sessionTxBytes: Long,
    pingMs: Int?,
    isConnected: Boolean,
    isTestingPing: Boolean,
    onRetestPing: () -> Unit,
    exitIp: String = "",
    exitLocation: String = "",
    networkChangeSummary: String = "",
) {
    Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        TelemetryMiniTile(
            title = dtr("Download", "دانلود"),
            value = TrafficFormatters.formatSpeedBps(rxSpeedBps),
            sub = TrafficFormatters.formatBytes(sessionRxBytes),
            icon = Icons.Rounded.ArrowDownward,
            tint = Color(0xFF10B981),
            modifier = Modifier.weight(1f),
        )
        TelemetryMiniTile(
            title = dtr("Upload", "آپلود"),
            value = TrafficFormatters.formatSpeedBps(txSpeedBps),
            sub = TrafficFormatters.formatBytes(sessionTxBytes),
            icon = Icons.Rounded.ArrowUpward,
            tint = Color(0xFFF97316),
            modifier = Modifier.weight(1f),
        )
        Surface(
            modifier = Modifier
                .weight(1f)
                .clip(RoundedCornerShape(14.dp))
                .clickable(enabled = isConnected && !isTestingPing, onClick = onRetestPing),
            shape = RoundedCornerShape(14.dp),
            color = MaterialTheme.colorScheme.surface,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.7f)),
        ) {
            Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Rounded.Speed, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(dtr("Real Delay (HTTP 204)", "پینگ واقعی (204)"), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    val valStr = when {
                        isTestingPing -> dtr("Testing...", "در حال تست...")
                        isConnected && pingMs != null && pingMs > 0 -> "$pingMs ms"
                        isConnected -> dtr("Tap to test", "کلیک برای تست")
                        else -> "—"
                    }
                    Text(valStr, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                }
                if (isConnected) {
                    Icon(Icons.Rounded.Refresh, null, modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
    if (isConnected && (exitIp.isNotBlank() || networkChangeSummary.isNotBlank())) {
        Surface(
            shape = RoundedCornerShape(10.dp),
            color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 7.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                if (exitIp.isNotBlank()) {
                    Text(
                        text = dtr("Connected IP: ", "آی‌پی اتصال: ") + if (exitLocation.isNotBlank()) "$exitIp ($exitLocation)" else exitIp,
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF10B981),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                if (networkChangeSummary.isNotBlank()) {
                    Text(
                        text = dtr("Changes: ", "تغییرات شبکه: ") + networkChangeSummary,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }
    }
    }
}

@Composable
private fun TelemetryMiniTile(
    title: String,
    value: String,
    sub: String,
    icon: ImageVector,
    tint: Color,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.7f)),
    ) {
        Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, null, tint = tint, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Column {
                Text(title, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text("$value  •  $sub", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

@Composable
fun DesktopLiveLogConsole(
    logs: List<LogEntry>,
    onCopy: () -> Unit,
    onClear: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val recent = remember(logs) { logs.takeLast(80) }
    val listState = rememberLazyListState()
    LaunchedEffect(recent.size) {
        if (recent.isNotEmpty()) {
            runCatching { listState.animateScrollToItem(recent.lastIndex) }
        }
    }

    DesktopCard(modifier = modifier, padding = 10.dp) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            Icon(Icons.Rounded.Terminal, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(6.dp))
            Text(dtr("Live Core Logs", "لاگ زنده هسته و اتصال"), style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
            IconButton(onClick = onCopy, modifier = Modifier.size(28.dp)) {
                Icon(Icons.Rounded.ContentCopy, contentDescription = "Copy Logs", modifier = Modifier.size(15.dp))
            }
            IconButton(onClick = onClear, modifier = Modifier.size(28.dp)) {
                Icon(Icons.Rounded.DeleteSweep, contentDescription = "Clear Logs", modifier = Modifier.size(15.dp))
            }
        }
        Spacer(Modifier.height(6.dp))
        Surface(
            shape = RoundedCornerShape(10.dp),
            color = Color(0xFF0D1117),
            modifier = Modifier
                .fillMaxWidth()
                .height(145.dp),
        ) {
            if (recent.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize().padding(10.dp), contentAlignment = Alignment.CenterStart) {
                    Text(
                        text = dtr("Ready. Connect any engine to inspect live core output...", "آماده است. پس از اتصال، گزارش زنده هسته‌ها اینجا نمایش داده می‌شود..."),
                        style = MaterialTheme.typography.labelSmall.copy(fontFamily = FontFamily.Monospace),
                        color = Color(0xFF8B949E),
                    )
                }
            } else {
                LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize().padding(8.dp),
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    items(recent) { item ->
                        val c = when (item.level) {
                            LogLevel.ERROR -> Color(0xFFF87171)
                            LogLevel.WARN -> Color(0xFFFBBF24)
                            LogLevel.INFO -> Color(0xFFD1D5DB)
                        }
                        Text(
                            text = "[${item.timestamp}] ${item.tag}: ${item.message}",
                            style = MaterialTheme.typography.labelSmall.copy(fontFamily = FontFamily.Monospace),
                            color = c,
                        )
                    }
                }
            }
        }
    }
}
