package com.opervia.example.serviceCompliance.features.operations.data

import android.graphics.Bitmap
import androidx.core.graphics.createBitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import java.io.ByteArrayOutputStream

internal data class SignaturePoint(val x: Float, val y: Float) : java.io.Serializable

/** Conserva los trazos introducidos por el usuario; no genera ni interpreta una firma. */
internal fun signaturePng(strokes: List<List<SignaturePoint>>): ByteArray {
    require(strokes.any { stroke -> stroke.size > 1 && stroke.any { it != stroke.first() } }) { "Dibuja la firma antes de continuar." }
    require(strokes.flatten().all { it.x.isFinite() && it.y.isFinite() && it.x in 0f..1f && it.y in 0f..1f })
    val width = 1000
    val height = 500
    val bitmap = createBitmap(width, height)
    try {
        val canvas = Canvas(bitmap)
        canvas.drawColor(Color.WHITE)
        val pen = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.BLACK
            strokeWidth = 4f
            style = Paint.Style.STROKE
            strokeCap = Paint.Cap.ROUND
            strokeJoin = Paint.Join.ROUND
        }
        strokes.filter { it.size > 1 }.forEach { stroke ->
            val path = Path().apply {
                moveTo(stroke.first().x * width, stroke.first().y * height)
                stroke.drop(1).forEach { lineTo(it.x * width, it.y * height) }
            }
            canvas.drawPath(path, pen)
        }
        return ByteArrayOutputStream().use { output ->
            check(bitmap.compress(Bitmap.CompressFormat.PNG, 100, output))
            output.toByteArray()
        }
    } finally { bitmap.recycle() }
}
