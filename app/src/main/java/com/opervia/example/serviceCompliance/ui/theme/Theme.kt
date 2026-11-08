package com.opervia.example.serviceCompliance.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable

private val LightColorScheme = lightColorScheme(
    primary = OperviaAction,
    onPrimary = OperviaSurface,
    secondary = OperviaInkSoft,
    background = OperviaPaper,
    onBackground = OperviaInk,
    surface = OperviaSurface,
    onSurface = OperviaInk,
    outline = OperviaLine,
    error = OperviaDanger
)

@Composable
fun ServiceComplianceKotlinTheme(
    darkTheme: Boolean = false,
    dynamicColor: Boolean = true,
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = LightColorScheme,
        typography = Typography,
        content = content
    )
}