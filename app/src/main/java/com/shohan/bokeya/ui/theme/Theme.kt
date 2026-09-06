package com.shohan.bokeya.ui.theme

import android.app.Activity
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import com.shohan.bokeya.domain.model.ThemeMode

private val LightColors = lightColorScheme(
    primary = Emerald600,
    onPrimary = Color.White,
    primaryContainer = Emerald100,
    onPrimaryContainer = Emerald900,

    secondary = Navy700,
    onSecondary = Color.White,
    secondaryContainer = Slate200,
    onSecondaryContainer = Navy800,

    tertiary = InfoBlue,
    onTertiary = Color.White,
    tertiaryContainer = InfoBlueLight,
    onTertiaryContainer = Color(0xFF1E3A8A),

    error = DangerRose,
    onError = Color.White,
    errorContainer = DangerRoseLight,
    onErrorContainer = Color(0xFF881337),

    background = Color(0xFFF7F8FA),
    onBackground = Slate900,
    surface = Color.White,
    onSurface = Slate900,
    surfaceVariant = Slate100,
    onSurfaceVariant = Slate600,
    surfaceTint = Emerald600,

    outline = Slate300,
    outlineVariant = Slate200,
    scrim = Color(0x99000000),

    inverseSurface = Navy800,
    inverseOnSurface = Slate100,
    inversePrimary = Emerald300,

    surfaceContainerLowest = Color.White,
    surfaceContainerLow = Color(0xFFFCFDFE),
    surfaceContainer = Color(0xFFF4F6F8),
    surfaceContainerHigh = Color(0xFFEEF1F5),
    surfaceContainerHighest = Color(0xFFE8ECF1),
)

/**
 * Dark theme is a designed surface ramp, not an inverted light theme: each
 * elevation step gets its own navy tone so cards separate from the background
 * without borders, and the emerald accent is lightened to keep 4.5:1 contrast
 * against those dark surfaces.
 */
private val DarkColors = darkColorScheme(
    primary = Emerald400,
    onPrimary = Color(0xFF00281B),
    primaryContainer = Emerald800,
    onPrimaryContainer = Emerald100,

    secondary = Slate300,
    onSecondary = Navy900,
    secondaryContainer = Navy600,
    onSecondaryContainer = Slate100,

    tertiary = InfoBlueDark,
    onTertiary = Color(0xFF04275C),
    tertiaryContainer = Color(0xFF1E3A8A),
    onTertiaryContainer = InfoBlueLight,

    error = DangerRoseDark,
    onError = Color(0xFF4C0519),
    errorContainer = Color(0xFF881337),
    onErrorContainer = DangerRoseLight,

    background = Navy900,
    onBackground = Slate100,
    surface = Navy900,
    onSurface = Slate100,
    surfaceVariant = Navy700,
    onSurfaceVariant = Slate400,
    surfaceTint = Emerald400,

    outline = Color(0xFF3A4A61),
    outlineVariant = Color(0xFF243449),
    scrim = Color(0xCC000000),

    inverseSurface = Slate100,
    inverseOnSurface = Navy800,
    inversePrimary = Emerald700,

    surfaceContainerLowest = Color(0xFF070C16),
    surfaceContainerLow = Color(0xFF0D1524),
    surfaceContainer = Color(0xFF121C2E),
    surfaceContainerHigh = Color(0xFF182338),
    surfaceContainerHighest = Color(0xFF1F2C43),
)

/** Generously rounded, consistent with the soft premium card language. */
val BokeyaShapes = Shapes(
    extraSmall = androidx.compose.foundation.shape.RoundedCornerShape(8.dp),
    small = androidx.compose.foundation.shape.RoundedCornerShape(12.dp),
    medium = androidx.compose.foundation.shape.RoundedCornerShape(16.dp),
    large = androidx.compose.foundation.shape.RoundedCornerShape(22.dp),
    extraLarge = androidx.compose.foundation.shape.RoundedCornerShape(28.dp),
)

/** Extra semantic colours Material's [androidx.compose.material3.ColorScheme] has no slot for. */
data class BokeyaColors(
    val isDark: Boolean,
    val success: Color,
    val onSuccess: Color,
    val successContainer: Color,
    val onSuccessContainer: Color,
    val warning: Color,
    val onWarning: Color,
    val warningContainer: Color,
    val onWarningContainer: Color,
    val shop: Color,
    val loan: Color,
    val emi: Color,
    val person: Color,
    val income: Color,
    val expense: Color,
    /** Translucent white/black used for glass-like layers over gradients. */
    val glass: Color,
    val heroGradient: List<Color>,
)

private val LightExtended = BokeyaColors(
    isDark = false,
    success = SuccessGreen,
    onSuccess = Color.White,
    successContainer = SuccessGreenLight,
    onSuccessContainer = Emerald900,
    warning = WarnAmber,
    onWarning = Color.White,
    warningContainer = WarnAmberLight,
    onWarningContainer = Color(0xFF78350F),
    shop = ShopViolet,
    loan = LoanBlue,
    emi = EmiTeal,
    person = PersonAmber,
    income = SuccessGreen,
    expense = DangerRose,
    glass = Color(0x14FFFFFF),
    heroGradient = listOf(Navy800, Navy700, Color(0xFF17324A)),
)

private val DarkExtended = BokeyaColors(
    isDark = true,
    success = SuccessGreenDark,
    onSuccess = Color(0xFF00281B),
    successContainer = Emerald800,
    onSuccessContainer = Emerald100,
    warning = WarnAmberDark,
    onWarning = Color(0xFF451A03),
    warningContainer = Color(0xFF78350F),
    onWarningContainer = WarnAmberLight,
    shop = ShopVioletDark,
    loan = LoanBlueDark,
    emi = EmiTealDark,
    person = PersonAmberDark,
    income = SuccessGreenDark,
    expense = DangerRoseDark,
    glass = Color(0x0DFFFFFF),
    heroGradient = listOf(Color(0xFF16273D), Color(0xFF101D2F), Color(0xFF0B1220)),
)

val LocalBokeyaColors = staticCompositionLocalOf { LightExtended }

/** Convenience accessor: `BokeyaTheme.colors.success`. */
object BokeyaTheme {
    val colors: BokeyaColors
        @Composable get() = LocalBokeyaColors.current
}

@Composable
fun BokeyaTheme(
    themeMode: ThemeMode = ThemeMode.SYSTEM,
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit,
) {
    val darkTheme = when (themeMode) {
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
    }

    val context = LocalContext.current
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S ->
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        darkTheme -> DarkColors
        else -> LightColors
    }

    val extended = if (darkTheme) DarkExtended else LightExtended

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as? Activity)?.window ?: return@SideEffect
            // Edge-to-edge: the app draws its own background behind the system bars.
            WindowCompat.getInsetsController(window, view).apply {
                isAppearanceLightStatusBars = !darkTheme
                isAppearanceLightNavigationBars = !darkTheme
            }
        }
    }

    CompositionLocalProvider(LocalBokeyaColors provides extended) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = BokeyaTypography,
            shapes = BokeyaShapes,
            content = content,
        )
    }
}
