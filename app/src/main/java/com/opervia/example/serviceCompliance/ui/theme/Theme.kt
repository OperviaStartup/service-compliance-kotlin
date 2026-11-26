package com.opervia.example.serviceCompliance.ui.theme

import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider

private val OperviaColorScheme = lightColorScheme(
    primary = OperviaAction,
    onPrimary = OperviaSurface,
    primaryContainer = OperviaTint,
    onPrimaryContainer = OperviaInk,
    secondary = OperviaSuccess,
    onSecondary = OperviaSurface,
    secondaryContainer = OperviaSuccessTint,
    onSecondaryContainer = OperviaInk,
    background = OperviaPaper,
    onBackground = OperviaInk,
    surface = OperviaSurface,
    onSurface = OperviaInk,
    surfaceVariant = OperviaTint,
    onSurfaceVariant = OperviaInkSoft,
    surfaceTint = OperviaAction,
    surfaceContainerLow = OperviaSurface,
    surfaceContainer = OperviaPaper,
    surfaceContainerHigh = OperviaPaper,
    surfaceContainerHighest = OperviaTint,
    outline = OperviaInputOutline,
    outlineVariant = OperviaLine,
    error = OperviaDanger,
    errorContainer = OperviaDangerTint,
    onErrorContainer = OperviaDanger
)

@Composable
internal fun ServiceComplianceKotlinTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = OperviaColorScheme,
        typography = Typography
    ) {
        CompositionLocalProvider(LocalContentColor provides OperviaInk, content = content)
    }
}
