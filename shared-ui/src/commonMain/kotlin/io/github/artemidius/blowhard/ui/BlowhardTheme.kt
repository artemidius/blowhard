package io.github.artemidius.blowhard.ui

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

internal val BlowhardColors = darkColorScheme(
    primary = Color(0xFFFF620A),
    onPrimary = Color(0xFF160A02),
    primaryContainer = Color(0xFF382418),
    onPrimaryContainer = Color(0xFFFFCFB2),
    secondary = Color(0xFFFFA46B),
    onSecondary = Color(0xFF20140D),
    secondaryContainer = Color(0xFF30251E),
    onSecondaryContainer = Color(0xFFFFCDAE),
    background = Color(0xFF0C0E12),
    onBackground = Color(0xFFF5F3EF),
    surface = Color(0xFF090B0F),
    onSurface = Color(0xFFF5F3EF),
    surfaceVariant = Color(0xFF20242C),
    onSurfaceVariant = Color(0xFFB6B3AF),
    surfaceContainer = Color(0xFF151920),
    surfaceContainerHigh = Color(0xFF1A1F27),
    surfaceContainerHighest = Color(0xFF202630),
    surfaceContainerLow = Color(0xFF11151B),
    surfaceContainerLowest = Color(0xFF080A0D),
    outline = Color(0xFF85817C),
    outlineVariant = Color(0xFF2A303A),
    tertiary = Color(0xFFFFA46B),
    error = Color(0xFFFFB4AB),
    errorContainer = Color(0xFF492422),
    onErrorContainer = Color(0xFFFFDAD6),
)

internal val BlowhardShapes = Shapes(
    small = RoundedCornerShape(8.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(24.dp),
)

internal val BlowhardTypography = Typography(
    headlineLarge = TextStyle(fontSize = 42.sp, lineHeight = 50.sp, letterSpacing = (-1.5).sp),
    headlineSmall = TextStyle(fontSize = 28.sp, lineHeight = 36.sp, letterSpacing = (-0.7).sp),
    titleLarge = TextStyle(fontSize = 21.sp, lineHeight = 28.sp, fontWeight = FontWeight.Medium),
    bodyLarge = TextStyle(fontSize = 16.sp, lineHeight = 26.sp),
    bodyMedium = TextStyle(fontSize = 14.sp, lineHeight = 22.sp),
    labelSmall = TextStyle(fontSize = 11.sp, lineHeight = 16.sp, letterSpacing = 0.8.sp),
)
