package com.upc.example.serviceComplianceKotlin.fieldoperations.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path

@Composable
internal fun FieldBackdrop(content: @Composable BoxScope.() -> Unit) {
    Box(Modifier.fillMaxSize().drawBehind {
        drawRect(Brush.verticalGradient(listOf(Color(0xFFDDF3FF), Color(0xFFF4FAFE), Color(0xFFF5FCFC))))
        val top = Path().apply {
            moveTo(0f, 0f); lineTo(size.width, 0f); lineTo(size.width, size.height*.08f)
            quadraticTo(size.width*.3f, size.height*.2f, 0f, size.height*.045f); close()
        }
        drawPath(top, Brush.horizontalGradient(listOf(Color(0xFF89CEFA), Color(0xFFDEF3FF))))
        val diagonal = Path().apply {
            moveTo(size.width*.35f, 0f); lineTo(size.width, size.height*.3f)
            lineTo(size.width, size.height*.13f); lineTo(size.width*.65f, 0f); close()
        }
        drawPath(diagonal, Color(0xFF9AD7FA).copy(alpha=.28f))
        val lower = Path().apply {
            moveTo(0f,size.height*.42f); lineTo(size.width,size.height*.18f)
            lineTo(size.width,size.height*.3f); lineTo(0f,size.height*.54f); close()
        }
        drawPath(lower, Color.White.copy(alpha=.26f))
    }, content = content)
}
