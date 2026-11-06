package com.example.core.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R

/**
 * Whisly Design System Tokens (Phase 1 — Exact Palette, Typography, Shapes, Spacing).
 *
 * Light Theme:
 * - Background: #FFF9F5
 * - Surface: #FFFFFF
 * - Soft Peach: #FCE5DB
 * - Border: #E8DDD5
 * - Secondary Text: #806D62
 * - Primary Text: #29211E
 *
 * Dark Theme:
 * - Background: #1A1412
 * - Surface: #29211E
 * - Border: #49352B
 * - Secondary Text: #A8968C
 * - Primary Text: #FFF9F5
 *
 * Brand (Both):
 * - Primary Orange: #F45A18 (single primary action per screen)
 * - Bright Orange: #FF6B1A
 * - Gold: #FFAB32
 * - Peach: #EBAF96
 * - Warm Brown: #49352B
 * - Deep Brown: #29211E
 *
 * Header / Top Bar:
 * - Deep Cocoa: #29211E in both themes, with Warm White (#FFF9F5) content.
 */
object WhislyTokens {
    // Brand Colors (Both Themes)
    val PrimaryOrange = Color(0xFFF45A18)
    val BrightOrange = Color(0xFFFF6B1A)
    val GoldenAccent = Color(0xFFFFAB32)
    val PrimaryPeach = Color(0xFFEBAF96)
    val SecondaryPeach = Color(0xFFDFA58D)
    val WarmBrown = Color(0xFF49352B)
    val DeepBrown = Color(0xFF29211E)
    val WarmWhite = Color(0xFFFFF9F5)
    val PureWhite = Color(0xFFFFFFFF)

    // Light Theme Surfaces & Text
    val LightBackground = Color(0xFFFFF9F5)
    val LightSurface = Color(0xFFFFFFFF)
    val SoftPeach = Color(0xFFFCE5DB)
    val LightBorder = Color(0xFFE8DDD5)
    val LightSecondaryText = Color(0xFF806D62)
    val LightPrimaryText = Color(0xFF29211E)

    // Dark Theme Surfaces & Text
    val DarkBackground = Color(0xFF1A1412)
    val DarkSurface = Color(0xFF29211E)
    val DarkBorder = Color(0xFF49352B)
    val DarkSecondaryText = Color(0xFFA8968C)
    val DarkPrimaryText = Color(0xFFFFF9F5)
    val DarkElevatedCard = Color(0xFF332823)

    // Top Bar / Header (Deep Cocoa #29211E in both themes with Warm White #FFF9F5 content)
    val HeaderCocoaBackground = DeepBrown
    val HeaderSecondaryText = Color(0xFFD8C5B8)

    // Backwards-compatible aliases
    val LightPeach = SoftPeach
    val CreamBackground = LightBackground
    val MutedText = LightSecondaryText
    val WarmBackground = LightBackground
    val PrimarySurface = LightSurface
    val SecondarySurface = SoftPeach
    val PrimaryCocoa = LightPrimaryText
    val SecondaryBrown = LightSecondaryText
    val TerracottaAccent = PrimaryOrange
    val SoftAmber = GoldenAccent
    val BorderLight = LightBorder
    val DarkCardSurface = DarkSurface

    // Status & Semantic Tokens
    val Success = Color(0xFF2E6B47)
    val SuccessBg = Color(0xFFE7F3EC)
    val Warning = Color(0xFFC87D20)
    val WarningText = Color(0xFF7A4B0E)
    val WarningBg = Color(0xFFFCF2E3)
    val Error = Color(0xFFB33A3A)
    val ErrorBg = Color(0xFFFBEAEA)

    // Spacing Grid (4 / 8 / 12 / 16 / 24 / 32dp, 20dp screen padding)
    val Space4: Dp = 4.dp
    val Space8: Dp = 8.dp
    val Space12: Dp = 12.dp
    val Space16: Dp = 16.dp
    val Space24: Dp = 24.dp
    val Space32: Dp = 32.dp
    val ScreenHorizontalPadding: Dp = 20.dp

    // Shape Radius Tokens (controls 12dp, buttons/inputs 16dp, cards 20dp, major surfaces 24dp, bottom sheets 28dp)
    val RadiusControl: Dp = 12.dp
    val RadiusButtonInput: Dp = 16.dp
    val RadiusCard: Dp = 20.dp
    val RadiusMajorSurface: Dp = 24.dp
    val RadiusBottomSheet: Dp = 28.dp

    // Canonical Logo Size Tokens
    val LogoSizeSmall: Dp = 24.dp
    val LogoSizeHeader: Dp = 32.dp
    val LogoSizeMedium: Dp = 40.dp
    val LogoSizeHero: Dp = 96.dp

    val PeachCardGradient = Brush.linearGradient(
        colors = listOf(SoftPeach, PrimaryPeach.copy(alpha = 0.35f))
    )

    val CocoaHeroGradient = Brush.linearGradient(
        colors = listOf(WarmBrown, DeepBrown)
    )
}

@Immutable
data class WhislyExtendedColors(
    val background: Color,
    val cardSurface: Color,
    val elevatedSurface: Color,
    val primaryCocoa: Color,
    val warmBrown: Color,
    val deepBrown: Color,
    val mutedText: Color,
    val primaryPeach: Color,
    val secondaryPeach: Color,
    val softPeach: Color,
    val primaryAccent: Color,
    val onPrimaryAccent: Color,
    val border: Color,
    val glassNavSurface: Color,
    val glassNavBorder: Color,
    val isDark: Boolean
)

val LocalWhislyColors = staticCompositionLocalOf {
    WhislyExtendedColors(
        background = WhislyTokens.LightBackground,
        cardSurface = WhislyTokens.LightSurface,
        elevatedSurface = WhislyTokens.SoftPeach,
        primaryCocoa = WhislyTokens.LightPrimaryText,
        warmBrown = WhislyTokens.WarmBrown,
        deepBrown = WhislyTokens.DeepBrown,
        mutedText = WhislyTokens.LightSecondaryText,
        primaryPeach = WhislyTokens.PrimaryPeach,
        secondaryPeach = WhislyTokens.SecondaryPeach,
        softPeach = WhislyTokens.SoftPeach,
        primaryAccent = WhislyTokens.PrimaryOrange,
        onPrimaryAccent = WhislyTokens.WarmWhite,
        border = WhislyTokens.LightBorder,
        glassNavSurface = WhislyTokens.LightSurface.copy(alpha = 0.94f),
        glassNavBorder = WhislyTokens.LightBorder,
        isDark = false
    )
}

private val LightColorScheme = lightColorScheme(
    primary = WhislyTokens.PrimaryOrange,
    onPrimary = WhislyTokens.WarmWhite,
    primaryContainer = WhislyTokens.SoftPeach,
    onPrimaryContainer = WhislyTokens.DeepBrown,
    secondary = WhislyTokens.PrimaryPeach,
    onSecondary = WhislyTokens.DeepBrown,
    secondaryContainer = WhislyTokens.SoftPeach,
    onSecondaryContainer = WhislyTokens.DeepBrown,
    tertiary = WhislyTokens.Success,
    onTertiary = WhislyTokens.PureWhite,
    background = WhislyTokens.LightBackground,
    onBackground = WhislyTokens.LightPrimaryText,
    surface = WhislyTokens.LightSurface,
    onSurface = WhislyTokens.LightPrimaryText,
    surfaceVariant = WhislyTokens.SoftPeach,
    onSurfaceVariant = WhislyTokens.LightSecondaryText,
    outline = WhislyTokens.LightBorder,
    error = WhislyTokens.Error,
    onError = WhislyTokens.PureWhite,
    errorContainer = WhislyTokens.ErrorBg,
    onErrorContainer = WhislyTokens.Error
)

private val DarkColorScheme = darkColorScheme(
    primary = WhislyTokens.PrimaryOrange,
    onPrimary = WhislyTokens.WarmWhite,
    primaryContainer = WhislyTokens.WarmBrown,
    onPrimaryContainer = WhislyTokens.WarmWhite,
    secondary = WhislyTokens.PrimaryPeach,
    onSecondary = WhislyTokens.DeepBrown,
    secondaryContainer = WhislyTokens.DarkSurface,
    onSecondaryContainer = WhislyTokens.WarmWhite,
    tertiary = Color(0xFF63B884),
    onTertiary = WhislyTokens.DeepBrown,
    background = WhislyTokens.DarkBackground,
    onBackground = WhislyTokens.DarkPrimaryText,
    surface = WhislyTokens.DarkSurface,
    onSurface = WhislyTokens.DarkPrimaryText,
    surfaceVariant = WhislyTokens.DarkElevatedCard,
    onSurfaceVariant = WhislyTokens.DarkSecondaryText,
    outline = WhislyTokens.DarkBorder,
    error = Color(0xFFF28B82),
    onError = WhislyTokens.DeepBrown,
    errorContainer = Color(0xFF5C1F1F),
    onErrorContainer = Color(0xFFFAD2CF)
)

/**
 * Inter FontFamily backed by dedicated static TTF weights (400 / 500 / 600 / 700)
 * so Bold (700) and SemiBold (600) render with true typographic weight across API 24–36.
 */
val InterFontFamily = FontFamily(
    Font(R.font.inter, FontWeight.Normal),
    Font(R.font.inter_500, FontWeight.Medium),
    Font(R.font.inter_600, FontWeight.SemiBold),
    Font(R.font.inter_700, FontWeight.Bold)
)

/**
 * Plus Jakarta Sans Display FontFamily backed by dedicated static TTF weights (400 / 700 / 800).
 */
val WhislyDisplayFontFamily = FontFamily(
    Font(R.font.plus_jakarta_sans, FontWeight.Normal),
    Font(R.font.plus_jakarta_sans_700, FontWeight.SemiBold),
    Font(R.font.plus_jakarta_sans_700, FontWeight.Bold),
    Font(R.font.plus_jakarta_sans_800, FontWeight.ExtraBold)
)

/**
 * Whisly Typography Scale (Phase 1):
 * - ScreenTitle: 28sp Bold
 * - SectionHeading: 20sp Bold
 * - CardTitle: 17sp SemiBold/Bold
 * - Body: 15sp Regular (body is never bold)
 * - Label: 13sp Medium
 */
val WhislyTypography = Typography(
    displayLarge = TextStyle(
        fontFamily = WhislyDisplayFontFamily,
        fontWeight = FontWeight.Bold,
        fontSize = 30.sp,
        lineHeight = 38.sp,
        letterSpacing = (-0.3).sp
    ),
    // ScreenTitle (28sp Bold)
    headlineLarge = TextStyle(
        fontFamily = InterFontFamily,
        fontWeight = FontWeight.Bold,
        fontSize = 28.sp,
        lineHeight = 36.sp,
        letterSpacing = (-0.3).sp
    ),
    // ScreenTitle (28sp Bold)
    headlineMedium = TextStyle(
        fontFamily = InterFontFamily,
        fontWeight = FontWeight.Bold,
        fontSize = 28.sp,
        lineHeight = 36.sp,
        letterSpacing = (-0.2).sp
    ),
    // SectionHeading (20sp Bold)
    titleLarge = TextStyle(
        fontFamily = InterFontFamily,
        fontWeight = FontWeight.Bold,
        fontSize = 20.sp,
        lineHeight = 28.sp,
        letterSpacing = (-0.1).sp
    ),
    // CardTitle (17sp Bold)
    titleMedium = TextStyle(
        fontFamily = InterFontFamily,
        fontWeight = FontWeight.Bold,
        fontSize = 17.sp,
        lineHeight = 24.sp
    ),
    // CardTitle (17sp SemiBold)
    titleSmall = TextStyle(
        fontFamily = InterFontFamily,
        fontWeight = FontWeight.SemiBold,
        fontSize = 17.sp,
        lineHeight = 24.sp
    ),
    // Body (15sp Regular — never bold)
    bodyLarge = TextStyle(
        fontFamily = InterFontFamily,
        fontWeight = FontWeight.Normal,
        fontSize = 15.sp,
        lineHeight = 22.sp
    ),
    // Body (15sp Regular — never bold)
    bodyMedium = TextStyle(
        fontFamily = InterFontFamily,
        fontWeight = FontWeight.Normal,
        fontSize = 15.sp,
        lineHeight = 22.sp
    ),
    bodySmall = TextStyle(
        fontFamily = InterFontFamily,
        fontWeight = FontWeight.Normal,
        fontSize = 13.sp,
        lineHeight = 19.sp
    ),
    // Label (13sp Medium)
    labelLarge = TextStyle(
        fontFamily = InterFontFamily,
        fontWeight = FontWeight.Medium,
        fontSize = 13.sp,
        lineHeight = 18.sp,
        letterSpacing = 0.1.sp
    ),
    // Label (13sp Medium)
    labelMedium = TextStyle(
        fontFamily = InterFontFamily,
        fontWeight = FontWeight.Medium,
        fontSize = 13.sp,
        lineHeight = 18.sp,
        letterSpacing = 0.1.sp
    ),
    labelSmall = TextStyle(
        fontFamily = InterFontFamily,
        fontWeight = FontWeight.Medium,
        fontSize = 12.sp,
        lineHeight = 16.sp,
        letterSpacing = 0.15.sp
    )
)

/**
 * Whisly Shape Scale (Phase 1):
 * - extraSmall: 12dp (controls)
 * - small: 16dp (buttons & inputs)
 * - medium: 20dp (cards)
 * - large: 24dp (major surfaces)
 * - extraLarge: 28dp (bottom sheets)
 */
val WhislyShapes = Shapes(
    extraSmall = RoundedCornerShape(WhislyTokens.RadiusControl),
    small = RoundedCornerShape(WhislyTokens.RadiusButtonInput),
    medium = RoundedCornerShape(WhislyTokens.RadiusCard),
    large = RoundedCornerShape(WhislyTokens.RadiusMajorSurface),
    extraLarge = RoundedCornerShape(WhislyTokens.RadiusBottomSheet)
)

@Composable
fun WhislyTheme(
    darkTheme: Boolean = false,
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme
    val extendedColors = if (darkTheme) {
        WhislyExtendedColors(
            background = WhislyTokens.DarkBackground,
            cardSurface = WhislyTokens.DarkSurface,
            elevatedSurface = WhislyTokens.DarkElevatedCard,
            primaryCocoa = WhislyTokens.DarkPrimaryText,
            warmBrown = WhislyTokens.WarmBrown,
            deepBrown = WhislyTokens.DeepBrown,
            mutedText = WhislyTokens.DarkSecondaryText,
            primaryPeach = WhislyTokens.PrimaryPeach,
            secondaryPeach = WhislyTokens.SecondaryPeach,
            softPeach = WhislyTokens.WarmBrown,
            primaryAccent = WhislyTokens.PrimaryOrange,
            onPrimaryAccent = WhislyTokens.WarmWhite,
            border = WhislyTokens.DarkBorder,
            glassNavSurface = WhislyTokens.DarkSurface.copy(alpha = 0.94f),
            glassNavBorder = WhislyTokens.DarkBorder,
            isDark = true
        )
    } else {
        WhislyExtendedColors(
            background = WhislyTokens.LightBackground,
            cardSurface = WhislyTokens.LightSurface,
            elevatedSurface = WhislyTokens.SoftPeach,
            primaryCocoa = WhislyTokens.LightPrimaryText,
            warmBrown = WhislyTokens.WarmBrown,
            deepBrown = WhislyTokens.DeepBrown,
            mutedText = WhislyTokens.LightSecondaryText,
            primaryPeach = WhislyTokens.PrimaryPeach,
            secondaryPeach = WhislyTokens.SecondaryPeach,
            softPeach = WhislyTokens.SoftPeach,
            primaryAccent = WhislyTokens.PrimaryOrange,
            onPrimaryAccent = WhislyTokens.WarmWhite,
            border = WhislyTokens.LightBorder,
            glassNavSurface = WhislyTokens.LightSurface.copy(alpha = 0.94f),
            glassNavBorder = WhislyTokens.LightBorder,
            isDark = false
        )
    }

    CompositionLocalProvider(LocalWhislyColors provides extendedColors) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = WhislyTypography,
            shapes = WhislyShapes,
            content = content
        )
    }
}

object WhislyDesign {
    val colors: WhislyExtendedColors
        @Composable
        get() = LocalWhislyColors.current
}
