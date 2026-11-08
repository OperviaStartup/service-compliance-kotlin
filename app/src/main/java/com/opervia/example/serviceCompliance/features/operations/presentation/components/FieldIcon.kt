package com.opervia.example.serviceCompliance.features.operations.presentation.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import com.opervia.example.serviceCompliance.R
import com.opervia.example.serviceCompliance.features.operations.presentation.theme.FieldBlue

internal enum class FieldSymbol { Steps, Photo, Clock, Building, Info, Warning, Check, Document, Back, Refresh }

@Composable
internal fun FieldIcon(symbol: FieldSymbol, modifier: Modifier = Modifier, tint: Color = FieldBlue) {
    val image = when (symbol) {
        FieldSymbol.Steps -> R.drawable.field_ic_steps
        FieldSymbol.Photo -> R.drawable.field_ic_photo
        FieldSymbol.Clock -> R.drawable.field_ic_clock
        FieldSymbol.Building -> R.drawable.field_ic_building
        FieldSymbol.Info -> R.drawable.field_ic_info
        FieldSymbol.Warning -> R.drawable.field_ic_warning
        FieldSymbol.Check -> R.drawable.field_ic_check
        FieldSymbol.Document -> R.drawable.field_ic_document
        FieldSymbol.Back -> R.drawable.field_ic_back
        FieldSymbol.Refresh -> R.drawable.field_ic_refresh
    }
    Image(
        painter = painterResource(image),
        contentDescription = null,
        modifier = modifier.size(22.dp),
        colorFilter = ColorFilter.tint(tint)
    )
}
