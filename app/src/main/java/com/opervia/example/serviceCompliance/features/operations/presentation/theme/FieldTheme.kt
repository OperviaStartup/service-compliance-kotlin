package com.opervia.example.serviceCompliance.features.operations.presentation.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.unit.dp
import androidx.compose.runtime.Composable
import com.opervia.example.serviceCompliance.ui.theme.OperviaAction
import com.opervia.example.serviceCompliance.ui.theme.OperviaInk
import com.opervia.example.serviceCompliance.ui.theme.OperviaMuted
import com.opervia.example.serviceCompliance.ui.theme.OperviaSuccess
import com.opervia.example.serviceCompliance.ui.theme.OperviaTint

internal val FieldBlue = OperviaAction
internal val FieldNavy = OperviaInk
internal val FieldMuted = OperviaMuted
internal val FieldTeal = OperviaSuccess
internal val FieldPaleBlue = OperviaTint

private val FieldShapes = Shapes(
    small = RoundedCornerShape(8.dp),
    medium = RoundedCornerShape(12.dp),
    large = RoundedCornerShape(16.dp)
)

@Composable
internal fun FieldTheme(content: @Composable () -> Unit) {
    MaterialTheme(shapes = FieldShapes, content = content)
}
