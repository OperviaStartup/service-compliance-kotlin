package com.opervia.example.serviceCompliance.features.operations.presentation.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.material3.Shapes
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.unit.dp
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import com.opervia.example.serviceCompliance.ui.theme.OperviaAction
import com.opervia.example.serviceCompliance.ui.theme.OperviaInk
import com.opervia.example.serviceCompliance.ui.theme.OperviaLine
import com.opervia.example.serviceCompliance.ui.theme.OperviaMuted
import com.opervia.example.serviceCompliance.ui.theme.OperviaPaper
import com.opervia.example.serviceCompliance.ui.theme.OperviaSuccess
import com.opervia.example.serviceCompliance.ui.theme.OperviaSuccessTint
import com.opervia.example.serviceCompliance.ui.theme.OperviaTint
import com.opervia.example.serviceCompliance.ui.theme.Typography

internal val FieldBlue = OperviaAction
internal val FieldNavy = OperviaInk
internal val FieldBackground = OperviaPaper
internal val FieldMuted = OperviaMuted
internal val FieldLine = OperviaLine
internal val FieldTeal = OperviaSuccess
internal val FieldPaleBlue = OperviaTint

@Composable
internal fun FieldTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = lightColorScheme(primary = FieldBlue, onPrimary = Color.White,
        background = FieldBackground, onBackground = FieldNavy, surface = Color.White, onSurface = FieldNavy,
        onSurfaceVariant = FieldMuted, outline = Color(0xFFCBD9E8), outlineVariant = FieldLine,
        secondary = FieldTeal, secondaryContainer = OperviaSuccessTint,
        primaryContainer = FieldPaleBlue, onPrimaryContainer = FieldNavy),
        typography = Typography,
        shapes = Shapes(small = RoundedCornerShape(8.dp), medium = RoundedCornerShape(12.dp), large = RoundedCornerShape(16.dp)),
        content = content)
}
