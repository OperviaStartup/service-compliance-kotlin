package com.opervia.example.serviceCompliance.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.opervia.example.serviceCompliance.R

private val plexSans = FontFamily(
    Font(R.font.ibm_plex_sans_regular, FontWeight.Normal),
    Font(R.font.ibm_plex_sans_semibold, FontWeight.SemiBold),
    Font(R.font.ibm_plex_sans_bold, FontWeight.Bold)
)
private val base = Typography()
internal val Typography = Typography(
    displayLarge = base.displayLarge.copy(fontFamily = plexSans),
    displayMedium = base.displayMedium.copy(fontFamily = plexSans),
    displaySmall = base.displaySmall.copy(fontFamily = plexSans),
    headlineLarge = base.headlineLarge.copy(fontFamily = plexSans, fontWeight = FontWeight.Bold),
    headlineMedium = base.headlineMedium.copy(fontFamily = plexSans, fontSize = 24.sp, lineHeight = 30.sp, fontWeight = FontWeight.Bold),
    headlineSmall = base.headlineSmall.copy(fontFamily = plexSans, fontSize = 22.sp, lineHeight = 28.sp, fontWeight = FontWeight.Bold),
    titleLarge = base.titleLarge.copy(fontFamily = plexSans, fontSize = 20.sp, lineHeight = 26.sp, fontWeight = FontWeight.Bold),
    titleMedium = base.titleMedium.copy(fontFamily = plexSans, fontSize = 17.sp, lineHeight = 22.sp, fontWeight = FontWeight.SemiBold),
    titleSmall = base.titleSmall.copy(fontFamily = plexSans, fontWeight = FontWeight.SemiBold),
    bodyLarge = base.bodyLarge.copy(fontFamily = plexSans),
    bodyMedium = base.bodyMedium.copy(fontFamily = plexSans, lineHeight = 21.sp),
    bodySmall = base.bodySmall.copy(fontFamily = plexSans, lineHeight = 17.sp),
    labelLarge = base.labelLarge.copy(fontFamily = plexSans, fontWeight = FontWeight.SemiBold),
    labelMedium = base.labelMedium.copy(fontFamily = plexSans, fontWeight = FontWeight.SemiBold),
    labelSmall = base.labelSmall.copy(fontFamily = plexSans, fontWeight = FontWeight.SemiBold)
)
