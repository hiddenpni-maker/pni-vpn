package ir.pnifox.core.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import ir.pnifox.core.model.ActiveSectionEngine
import ir.pnifox.core.model.AppLanguage
import ir.pnifox.core.model.RecoveryAction
import ir.pnifox.core.model.TunnelState
import ir.pnifox.core.vpn.ConnectionStatusNarrator
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * Animated Fox Mascot bound to live tunnel state for a given section (AETHER / Server / DNS):
 * - Running fox when connecting
 * - Happy emerald fox when connected
 * - Sad fox + human-readable reason + 1-tap suggestions when failed
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun FoxConnectionMascot(
    tunnelState: TunnelState,
    section: ActiveSectionEngine,
    onSuggestionClick: (RecoveryAction) -> Unit,
    modifier: Modifier = Modifier,
) {
    val isFa = LocalAppLanguage.current == AppLanguage.PERSIAN

    // Show active connection/error for this section cleanly; if another section owns the tunnel,
    // show a calm note without confusing the user.
    val effectiveState: TunnelState = when (tunnelState) {
        is TunnelState.Connecting -> if (tunnelState.section == section) tunnelState else TunnelState.Disconnected
        is TunnelState.Connected -> if (tunnelState.section == section) tunnelState else TunnelState.Disconnected
        is TunnelState.Error -> if (tunnelState.section == section) tunnelState else TunnelState.Disconnected
        TunnelState.Disconnected -> TunnelState.Disconnected
    }

    val otherSectionActive: ActiveSectionEngine? = when (tunnelState) {
        is TunnelState.Connected -> if (tunnelState.section != section) tunnelState.section else null
        is TunnelState.Connecting -> if (tunnelState.section != section) tunnelState.section else null
        else -> null
    }

    val transition = rememberInfiniteTransition(label = "fox_mascot")
    val runPhase by transition.animateFloat(
        initialValue = 0f,
        targetValue = (2f * PI).toFloat(),
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 520, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "run_phase",
    )
    val breathePulse by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1500, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "breathe",
    )

    Column(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Canvas(modifier = Modifier.size(width = 92.dp, height = 64.dp)) {
                when (effectiveState) {
                    is TunnelState.Connecting -> drawRunningFox(runPhase)
                    is TunnelState.Connected -> drawHappyFox(breathePulse)
                    is TunnelState.Error -> drawSadFox(breathePulse)
                    TunnelState.Disconnected -> drawIdleFox(breathePulse)
                }
            }
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                val (headline, subline, accent) = when (effectiveState) {
                    is TunnelState.Connecting -> Triple(
                        ConnectionStatusNarrator.headlineForState(effectiveState, isFa),
                        tr("Target: ${effectiveState.targetLabel}", "مقصد: ${effectiveState.targetLabel}"),
                        Color(0xFFF97316),
                    )
                    is TunnelState.Connected -> Triple(
                        ConnectionStatusNarrator.headlineForState(effectiveState, isFa),
                        buildString {
                            if (effectiveState.exitIp.isNotBlank()) {
                                append("IP: ${effectiveState.exitIp}")
                                if (effectiveState.exitLocation.isNotBlank()) append(" (${effectiveState.exitLocation})")
                            } else {
                                append("SOCKS5 127.0.0.1:${effectiveState.localSocksPort}")
                            }
                            effectiveState.pingMs?.let { append(" • ${it}ms") }
                        },
                        Color(0xFF10B981),
                    )
                    is TunnelState.Error -> Triple(
                        ConnectionStatusNarrator.headlineForState(effectiveState, isFa),
                        tr("See diagnosis & quick fixes below", "علت خطا و راه‌حل پیشنهادی"),
                        Color(0xFFEF4444),
                    )
                    TunnelState.Disconnected -> {
                        if (otherSectionActive != null) {
                            Triple(
                                tr("Another core is active", "هسته دیگری فعال است"),
                                tr("Active: ${otherSectionActive.titleEn}", "فعال: ${otherSectionActive.titleFa}"),
                                Color(0xFF38BDF8),
                            )
                        } else {
                            Triple(
                                ConnectionStatusNarrator.headlineForState(TunnelState.Disconnected, isFa),
                                tr("Engine: ${section.titleEn}", "بخش: ${section.titleFa}"),
                                MaterialTheme.colorScheme.onSurface,
                            )
                        }
                    }
                }

                Text(
                    text = headline,
                    style = MaterialTheme.typography.titleMedium,
                    color = accent,
                    fontWeight = FontWeight.Bold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(3.dp))
                Text(
                    text = subline,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }

        AnimatedVisibility(visible = effectiveState is TunnelState.Error) {
            if (effectiveState is TunnelState.Error) {
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 12.dp),
                    shape = RoundedCornerShape(14.dp),
                    color = MaterialTheme.colorScheme.error.copy(alpha = 0.12f),
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text(
                            text = if (isFa) effectiveState.error.reasonFa else effectiveState.error.reasonEn,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                        if (effectiveState.error.suggestions.isNotEmpty()) {
                            Spacer(Modifier.height(10.dp))
                            Text(
                                text = tr("Quick Recovery Actions:", "پیشنهاد فوری برای رفع مشکل:"),
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.error,
                                fontWeight = FontWeight.Bold,
                            )
                            Spacer(Modifier.height(6.dp))
                            FlowRow(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalArrangement = Arrangement.spacedBy(6.dp),
                            ) {
                                effectiveState.error.suggestions.forEach { action ->
                                    FilledTonalButton(
                                        onClick = { onSuggestionClick(action) },
                                        shape = RoundedCornerShape(12.dp),
                                    ) {
                                        Text(
                                            text = if (isFa) action.labelFa else action.labelEn,
                                            style = MaterialTheme.typography.labelMedium,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

private val FoxFur = Color(0xFFF97316)
private val FoxDarkFur = Color(0xFFEA580C)
private val FoxCream = Color(0xFFFFF7ED)
private val FoxEarTip = Color(0xFF1E293B)

private fun DrawScope.drawRunningFox(phase: Float) {
    val w = size.width
    val h = size.height
    val bob = sin(phase * 2f) * (h * 0.055f)
    val groundY = h * 0.88f

    val streakColor = Color(0xFF38BDF8).copy(alpha = 0.55f)
    val shift = ((phase / (2f * PI.toFloat())) * 18f)
    drawLine(streakColor, Offset(w * 0.04f - shift * 0.4f, h * 0.34f), Offset(w * 0.20f - shift * 0.4f, h * 0.34f), strokeWidth = 3f, cap = StrokeCap.Round)
    drawLine(streakColor, Offset(w * 0.08f - shift * 0.6f, h * 0.52f), Offset(w * 0.25f - shift * 0.6f, h * 0.52f), strokeWidth = 2.5f, cap = StrokeCap.Round)

    drawOval(color = Color.Black.copy(alpha = 0.22f), topLeft = Offset(w * 0.28f, groundY - 3f), size = Size(w * 0.42f, 6f))

    val tailWag = sin(phase) * 10f
    rotate(degrees = -12f + tailWag, pivot = Offset(w * 0.30f, h * 0.58f + bob)) {
        drawOval(color = FoxFur, topLeft = Offset(w * 0.07f, h * 0.44f + bob), size = Size(w * 0.25f, h * 0.16f))
        drawOval(color = FoxCream, topLeft = Offset(w * 0.06f, h * 0.46f + bob), size = Size(w * 0.09f, h * 0.11f))
    }

    val frontAngle = sin(phase) * 28f
    val backAngle = cos(phase) * 28f
    rotate(backAngle, pivot = Offset(w * 0.36f, h * 0.66f + bob)) {
        drawRoundRect(FoxDarkFur, Offset(w * 0.33f, h * 0.66f + bob), Size(7f, h * 0.20f), CornerRadius(4f, 4f))
    }
    rotate(-backAngle * 0.7f, pivot = Offset(w * 0.42f, h * 0.66f + bob)) {
        drawRoundRect(FoxFur, Offset(w * 0.39f, h * 0.66f + bob), Size(7f, h * 0.20f), CornerRadius(4f, 4f))
    }
    rotate(frontAngle, pivot = Offset(w * 0.58f, h * 0.66f + bob)) {
        drawRoundRect(FoxDarkFur, Offset(w * 0.56f, h * 0.66f + bob), Size(7f, h * 0.20f), CornerRadius(4f, 4f))
    }
    rotate(-frontAngle * 0.7f, pivot = Offset(w * 0.63f, h * 0.66f + bob)) {
        drawRoundRect(FoxFur, Offset(w * 0.61f, h * 0.66f + bob), Size(7f, h * 0.20f), CornerRadius(4f, 4f))
    }

    drawRoundRect(color = FoxFur, topLeft = Offset(w * 0.29f, h * 0.46f + bob), size = Size(w * 0.37f, h * 0.24f), cornerRadius = CornerRadius(24f, 24f))
    drawFoxHead(center = Offset(w * 0.69f, h * 0.41f + bob), radius = h * 0.19f, mood = 1)
}

private fun DrawScope.drawIdleFox(pulse: Float) {
    val cx = size.width * 0.5f
    val cy = size.height * 0.52f
    drawCircle(color = Color(0xFFF97316).copy(alpha = 0.08f + pulse * 0.08f), radius = size.height * 0.44f, center = Offset(cx, cy))
    drawSittingBody(cx, cy)
    drawFoxHead(center = Offset(cx, cy - size.height * 0.12f), radius = size.height * 0.22f, mood = 0)
}

private fun DrawScope.drawHappyFox(pulse: Float) {
    val cx = size.width * 0.5f
    val cy = size.height * 0.52f
    drawCircle(color = Color(0xFF10B981).copy(alpha = 0.15f + pulse * 0.14f), radius = size.height * 0.45f, center = Offset(cx, cy))
    drawCircle(color = Color(0xFF10B981).copy(alpha = 0.5f), radius = size.height * 0.45f, center = Offset(cx, cy), style = Stroke(width = 3f))
    drawSittingBody(cx, cy)
    drawFoxHead(center = Offset(cx, cy - size.height * 0.12f), radius = size.height * 0.22f, mood = 1)
}

private fun DrawScope.drawSadFox(pulse: Float) {
    val cx = size.width * 0.5f
    val cy = size.height * 0.54f
    drawCircle(color = Color(0xFFEF4444).copy(alpha = 0.12f + pulse * 0.10f), radius = size.height * 0.44f, center = Offset(cx, cy))
    drawSittingBody(cx, cy)
    drawFoxHead(center = Offset(cx, cy - size.height * 0.10f), radius = size.height * 0.22f, mood = -1)
    drawOval(color = Color(0xFF60A5FA), topLeft = Offset(cx + size.height * 0.10f, cy - size.height * 0.05f + pulse * 4f), size = Size(5f, 7f))
}

private fun DrawScope.drawSittingBody(cx: Float, cy: Float) {
    val h = size.height
    drawOval(color = FoxDarkFur, topLeft = Offset(cx + h * 0.04f, cy + h * 0.12f), size = Size(h * 0.34f, h * 0.20f))
    drawOval(color = FoxCream, topLeft = Offset(cx + h * 0.26f, cy + h * 0.14f), size = Size(h * 0.12f, h * 0.12f))
    drawRoundRect(color = FoxFur, topLeft = Offset(cx - h * 0.16f, cy + h * 0.02f), size = Size(h * 0.32f, h * 0.32f), cornerRadius = CornerRadius(18f, 18f))
    drawOval(color = FoxCream, topLeft = Offset(cx - h * 0.09f, cy + h * 0.09f), size = Size(h * 0.18f, h * 0.22f))
}

private fun DrawScope.drawFoxHead(center: Offset, radius: Float, mood: Int) {
    val earDrop = if (mood < 0) radius * 0.45f else 0f
    val leftEar = Path().apply {
        moveTo(center.x - radius * 0.85f, center.y - radius * 0.15f + earDrop)
        lineTo(center.x - radius * 0.95f - earDrop * 0.4f, center.y - radius * 1.15f + earDrop)
        lineTo(center.x - radius * 0.18f, center.y - radius * 0.65f + earDrop * 0.3f)
        close()
    }
    drawPath(leftEar, FoxDarkFur)

    val rightEar = Path().apply {
        moveTo(center.x + radius * 0.85f, center.y - radius * 0.15f + earDrop)
        lineTo(center.x + radius * 0.95f + earDrop * 0.4f, center.y - radius * 1.15f + earDrop)
        lineTo(center.x + radius * 0.18f, center.y - radius * 0.65f + earDrop * 0.3f)
        close()
    }
    drawPath(rightEar, FoxDarkFur)

    drawCircle(color = FoxFur, radius = radius, center = center)
    drawOval(color = FoxCream, topLeft = Offset(center.x - radius * 0.85f, center.y + radius * 0.05f), size = Size(radius * 0.72f, radius * 0.68f))
    drawOval(color = FoxCream, topLeft = Offset(center.x + radius * 0.13f, center.y + radius * 0.05f), size = Size(radius * 0.72f, radius * 0.68f))

    val eyeY = center.y - radius * 0.08f
    if (mood < 0) {
        drawLine(FoxEarTip, Offset(center.x - radius * 0.48f, eyeY + 2f), Offset(center.x - radius * 0.22f, eyeY - 2f), strokeWidth = 3f, cap = StrokeCap.Round)
        drawLine(FoxEarTip, Offset(center.x + radius * 0.22f, eyeY - 2f), Offset(center.x + radius * 0.48f, eyeY + 2f), strokeWidth = 3f, cap = StrokeCap.Round)
    } else {
        drawCircle(FoxEarTip, radius = radius * 0.14f, center = Offset(center.x - radius * 0.34f, eyeY))
        drawCircle(FoxEarTip, radius = radius * 0.14f, center = Offset(center.x + radius * 0.34f, eyeY))
    }
    drawCircle(color = FoxEarTip, radius = radius * 0.14f, center = Offset(center.x, center.y + radius * 0.42f))
}
