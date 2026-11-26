package ir.pnifox.core.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.PowerSettingsNew
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Shared Connect Power Button used across AETHER, Custom Servers, and DNS screens:
 * - Off / Disconnected / Error: Red
 * - Connecting: Amber-Orange with rotating glowing sweep ring
 * - Connected: Emerald Green
 */
@Composable
fun PnifoxConnectButton(
    isConnected: Boolean,
    isConnecting: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    size: Dp = 86.dp,
    contentDescription: String = "Toggle Connection",
) {
    val targetColor = when {
        isConnected -> Color(0xFF10B981)
        isConnecting -> Color(0xFFF97316)
        else -> Color(0xFFEF4444)
    }
    val powerColor by animateColorAsState(
        targetValue = targetColor,
        animationSpec = tween(durationMillis = 280),
        label = "connectPowerColor",
    )

    val transition = rememberInfiniteTransition(label = "connectGlowRing")
    val spinAngle by transition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1250, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "ringSpin",
    )
    val glowAlpha by transition.animateFloat(
        initialValue = 0.35f,
        targetValue = 0.9f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 850),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "ringPulse",
    )

    Box(
        modifier = modifier
            .size(size)
            .clip(CircleShape)
            .background(powerColor.copy(alpha = 0.14f))
            .then(
                if (isConnecting) {
                    Modifier
                } else {
                    Modifier.border(2.dp, powerColor.copy(alpha = 0.7f), CircleShape)
                },
            )
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        if (isConnecting) {
            Canvas(modifier = Modifier.size(size)) {
                val outerStroke = 5.dp.toPx()
                val coreStroke = 3.2.dp.toPx()
                // Subtle static track ring
                drawCircle(
                    color = Color(0xFFF97316).copy(alpha = 0.22f),
                    radius = (this.size.minDimension - coreStroke) / 2f,
                    style = Stroke(width = coreStroke),
                )
                rotate(degrees = spinAngle) {
                    val sweep = Brush.sweepGradient(
                        0.0f to Color.Transparent,
                        0.55f to Color(0xFFF97316).copy(alpha = 0.25f),
                        0.85f to Color(0xFFFFB74D).copy(alpha = glowAlpha),
                        1.0f to Color.White.copy(alpha = 0.95f),
                    )
                    // Soft luminous halo arc
                    drawArc(
                        brush = sweep,
                        startAngle = 0f,
                        sweepAngle = 310f,
                        useCenter = false,
                        style = Stroke(width = outerStroke * 1.45f, cap = StrokeCap.Round),
                    )
                    // Sharp glowing spinner arc
                    drawArc(
                        brush = sweep,
                        startAngle = 0f,
                        sweepAngle = 310f,
                        useCenter = false,
                        style = Stroke(width = coreStroke, cap = StrokeCap.Round),
                    )
                }
            }
        }

        Surface(
            modifier = Modifier.size(size - 16.dp),
            shape = CircleShape,
            color = powerColor,
            shadowElevation = if (isConnected || isConnecting) 8.dp else 2.dp,
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = Icons.Rounded.PowerSettingsNew,
                    contentDescription = contentDescription,
                    tint = Color.White,
                    modifier = Modifier.size(if (size >= 84.dp) 32.dp else 26.dp),
                )
            }
        }
    }
}
