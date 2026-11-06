package com.upc.example.serviceComplianceKotlin.fieldoperations.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

internal val FieldBlue = Color(0xFF0875EE)
internal val FieldNavy = Color(0xFF07133F)
internal val FieldBackground = Color(0xFFF0F7FF)
internal val FieldMuted = Color(0xFF637187)
internal val FieldLine = Color(0xFFE3EDF6)
internal val FieldTeal = Color(0xFF098778)
internal val FieldPaleBlue = Color(0xFFE6F3FF)

private val fieldTypography = Typography(
    headlineMedium = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.Bold, fontSize = 24.sp, lineHeight = 30.sp, letterSpacing = (-0.5).sp),
    titleLarge = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.Bold, fontSize = 20.sp, lineHeight = 26.sp),
    titleMedium = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.Bold, fontSize = 15.sp, lineHeight = 21.sp),
    bodyLarge = TextStyle(fontFamily = FontFamily.SansSerif, fontSize = 15.sp, lineHeight = 23.sp),
    bodyMedium = TextStyle(fontFamily = FontFamily.SansSerif, fontSize = 14.sp, lineHeight = 21.sp),
    bodySmall = TextStyle(fontFamily = FontFamily.SansSerif, fontSize = 12.sp, lineHeight = 18.sp),
    labelLarge = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.Bold, fontSize = 14.sp, lineHeight = 20.sp),
    labelSmall = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.SemiBold, fontSize = 11.sp, lineHeight = 16.sp)
)

@Composable
internal fun FieldTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = lightColorScheme(primary = FieldBlue, onPrimary = Color.White,
        background = FieldBackground, surface = Color.White, onSurface = FieldNavy,
        onSurfaceVariant = FieldMuted, outline = Color(0xFFCBD9E8), outlineVariant = FieldLine,
        secondary = FieldTeal, secondaryContainer = Color(0xFFDDF5F0),
        primaryContainer = FieldPaleBlue, onPrimaryContainer = FieldNavy),
        typography = fieldTypography,
        shapes = Shapes(small = RoundedCornerShape(8.dp), medium = RoundedCornerShape(12.dp), large = RoundedCornerShape(16.dp)),
        content = content)
}
