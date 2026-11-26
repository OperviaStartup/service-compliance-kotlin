package com.opervia.example.serviceCompliance.features.operations.presentation.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.opervia.example.serviceCompliance.features.operations.data.SignaturePoint

@Composable
internal fun SignaturePad(strokes: List<List<SignaturePoint>>, onChange: (List<List<SignaturePoint>>) -> Unit) {
    var current by remember { mutableStateOf(emptyList<SignaturePoint>()) }
    val latestStrokes by rememberUpdatedState(strokes)
    val latestChange by rememberUpdatedState(onChange)
    Canvas(Modifier.fillMaxWidth().aspectRatio(2f).background(Color.White).border(1.dp, Color(0xFF80BCFF))
        .semantics { contentDescription = "Panel para dibujar la firma" }
        .pointerInput(Unit) {
            fun point(value: Offset) = SignaturePoint((value.x / size.width).coerceIn(0f, 1f),
                (value.y / size.height).coerceIn(0f, 1f))
            detectDragGestures(
                onDragStart = { current = listOf(point(it)) },
                onDragCancel = { current = emptyList() },
                onDragEnd = {
                    if (current.size > 1) latestChange(latestStrokes + listOf(current))
                    current = emptyList()
                },
                onDrag = { change, _ -> change.consume(); current = current + point(change.position) }
            )
        }) {
        (strokes + listOf(current)).forEach { stroke ->
            stroke.zipWithNext().forEach { (a, b) ->
                drawLine(Color.Black, Offset(a.x * size.width, a.y * size.height),
                    Offset(b.x * size.width, b.y * size.height), strokeWidth = 2.dp.toPx())
            }
        }
    }
}
