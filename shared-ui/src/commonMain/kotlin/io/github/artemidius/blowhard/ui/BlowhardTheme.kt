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
    background = Color(0xFF19191B),
    onBackground = Color(0xFFF5F3EF),
    surface = Color(0xFF121214),
    onSurface = Color(0xFFF5F3EF),
    surfaceVariant = Color(0xFF252527),
    onSurfaceVariant = Color(0xFFB6B3AF),
    surfaceContainer = Color(0xFF202022),
    surfaceContainerHigh = Color(0xFF29292C),
    surfaceContainerHighest = Color(0xFF323235),
    surfaceContainerLow = Color(0xFF171719),
    surfaceContainerLowest = Color(0xFF101012),
    outline = Color(0xFF85817C),
    outlineVariant = Color(0xFF363638),
    error = Color(0xFFFFB4AB),
    errorContainer = Color(0xFF492422),
    onErrorContainer = Color(0xFFFFDAD6),
)

internal val BlowhardShapes = Shapes(
    small = RoundedCornerShape(8.dp),
    medium = RoundedCornerShape(12.dp),
    large = RoundedCornerShape(20.dp),
)

internal val BlowhardTypography = Typography(
    headlineLarge = TextStyle(fontSize = 42.sp, lineHeight = 50.sp, letterSpacing = (-1.5).sp),
    headlineSmall = TextStyle(fontSize = 28.sp, lineHeight = 36.sp, letterSpacing = (-0.7).sp),
    titleLarge = TextStyle(fontSize = 21.sp, lineHeight = 28.sp, fontWeight = FontWeight.Medium),
    bodyLarge = TextStyle(fontSize = 16.sp, lineHeight = 26.sp),
    bodyMedium = TextStyle(fontSize = 14.sp, lineHeight = 22.sp),
    labelSmall = TextStyle(fontSize = 11.sp, lineHeight = 16.sp, letterSpacing = 0.8.sp),
)
