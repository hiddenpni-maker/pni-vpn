package ir.pni.vpn.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

val PniBg = Color(0xFF090E17)
val PniSurface = Color(0xFF111827)
val PniSurfaceElevated = Color(0xFF1B2436)
val PniEmerald = Color(0xFF10B981)
val PniEmeraldSoft = Color(0xFF34D399)
val PniCrimson = Color(0xFFEF4444)
val PniAmber = Color(0xFFF59E0B)
val PniCyan = Color(0xFF38BDF8)
val PniTextPrimary = Color(0xFFF8FAFC)
val PniTextMuted = Color(0xFF94A3B8)

private val PniDarkColors = darkColorScheme(
    primary = PniEmerald,
    onPrimary = Color(0xFF042F2E),
    primaryContainer = Color(0xFF064E3B),
    onPrimaryContainer = PniEmeraldSoft,
    secondary = PniCyan,
    onSecondary = Color(0xFF082F49),
    tertiary = PniAmber,
    background = PniBg,
    onBackground = PniTextPrimary,
    surface = PniSurface,
    onSurface = PniTextPrimary,
    surfaceVariant = PniSurfaceElevated,
    onSurfaceVariant = PniTextMuted,
    error = PniCrimson,
    onError = Color.White
)

@Composable
fun PniTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = PniDarkColors,
        content = content
    )
}
