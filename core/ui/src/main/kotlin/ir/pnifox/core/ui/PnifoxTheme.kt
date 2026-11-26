package ir.pnifox.core.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.sp
import ir.pnifox.core.model.AccentColorPref
import ir.pnifox.core.model.AppLanguage
import ir.pnifox.core.model.ThemeModePref

val LocalAppLanguage = compositionLocalOf { AppLanguage.ENGLISH }

@Composable
fun tr(en: String, fa: String): String =
    if (LocalAppLanguage.current == AppLanguage.PERSIAN) fa else en

val VazirmatnFontFamily = FontFamily(
    Font(R.font.vazirmatn_regular, FontWeight.Normal),
    Font(R.font.vazirmatn_medium, FontWeight.Medium),
    Font(R.font.vazirmatn_bold, FontWeight.Bold),
)

private fun buildTypography(language: AppLanguage): Typography {
    val family = if (language == AppLanguage.PERSIAN) VazirmatnFontFamily else FontFamily.SansSerif
    return Typography(
        headlineSmall = TextStyle(fontFamily = family, fontWeight = FontWeight.Bold, fontSize = 22.sp, lineHeight = 29.sp),
        titleLarge = TextStyle(fontFamily = family, fontWeight = FontWeight.Bold, fontSize = 19.sp, lineHeight = 25.sp),
        titleMedium = TextStyle(fontFamily = family, fontWeight = FontWeight.SemiBold, fontSize = 15.sp, lineHeight = 21.sp),
        bodyLarge = TextStyle(fontFamily = family, fontWeight = FontWeight.Normal, fontSize = 14.sp, lineHeight = 20.sp),
        bodyMedium = TextStyle(fontFamily = family, fontWeight = FontWeight.Normal, fontSize = 13.sp, lineHeight = 18.sp),
        labelMedium = TextStyle(fontFamily = family, fontWeight = FontWeight.Medium, fontSize = 12.sp, lineHeight = 16.sp),
        labelSmall = TextStyle(fontFamily = family, fontWeight = FontWeight.Medium, fontSize = 11.sp, lineHeight = 14.sp),
    )
}

@Composable
fun PnifoxTheme(
    language: AppLanguage = AppLanguage.ENGLISH,
    themeMode: ThemeModePref = ThemeModePref.DARK,
    accentColor: AccentColorPref = AccentColorPref.FOX_ORANGE,
    content: @Composable () -> Unit,
) {
    val isDark = when (themeMode) {
        ThemeModePref.SYSTEM -> isSystemInDarkTheme()
        ThemeModePref.LIGHT -> false
        ThemeModePref.DARK -> true
    }

    val primary = Color(accentColor.colorHex)
    val colorScheme = if (isDark) {
        darkColorScheme(
            primary = primary,
            onPrimary = Color.White,
            primaryContainer = primary.copy(alpha = 0.20f),
            onPrimaryContainer = Color(0xFFFFEDE5),
            secondary = Color(0xFF10B981),
            onSecondary = Color(0xFF042F1E),
            background = Color(0xFF0B0F17),
            onBackground = Color(0xFFF1F5F9),
            surface = Color(0xFF131924),
            onSurface = Color(0xFFE2E8F0),
            surfaceVariant = Color(0xFF1B2333),
            onSurfaceVariant = Color(0xFF94A3B8),
            outline = Color(0xFF283347),
            error = Color(0xFFEF4444),
            onError = Color.White,
        )
    } else {
        lightColorScheme(
            primary = primary,
            onPrimary = Color.White,
            primaryContainer = primary.copy(alpha = 0.14f),
            onPrimaryContainer = Color(0xFF1E293B),
            secondary = Color(0xFF059669),
            onSecondary = Color.White,
            background = Color(0xFFF6F8FB),
            onBackground = Color(0xFF0F172A),
            surface = Color.White,
            onSurface = Color(0xFF0F172A),
            surfaceVariant = Color(0xFFEFF3F8),
            onSurfaceVariant = Color(0xFF475569),
            outline = Color(0xFFDCE3ED),
            error = Color(0xFFDC2626),
            onError = Color.White,
        )
    }

    val layoutDir = if (language == AppLanguage.PERSIAN) LayoutDirection.Rtl else LayoutDirection.Ltr
    CompositionLocalProvider(
        LocalLayoutDirection provides layoutDir,
        LocalAppLanguage provides language,
    ) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = buildTypography(language),
            content = content,
        )
    }
}
