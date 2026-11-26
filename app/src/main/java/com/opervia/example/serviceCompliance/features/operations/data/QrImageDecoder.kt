package com.opervia.example.serviceCompliance.features.operations.data

import com.google.zxing.*
import com.google.zxing.common.HybridBinarizer
import com.opervia.example.serviceCompliance.features.operations.domain.model.MAX_PHOTO_BYTES
import com.opervia.example.serviceCompliance.features.operations.domain.model.OperationsFailure
import com.opervia.example.serviceCompliance.features.operations.domain.model.OperationsFailureKind
import java.io.ByteArrayOutputStream
import java.io.InputStream

internal fun readQrImageBytes(input: InputStream): ByteArray {
    val output = ByteArrayOutputStream()
    val buffer = ByteArray(8192)
    var count = input.read(buffer)
    while (count != -1) {
        if (output.size() + count > MAX_PHOTO_BYTES) throw invalidQrImage("Selecciona una imagen de hasta 5 MB.")
        output.write(buffer, 0, count)
        count = input.read(buffer)
    }
    if (output.size() == 0) throw invalidQrImage("La imagen seleccionada está vacía.")
    return output.toByteArray()
}

internal fun decodeQrPixels(width: Int, height: Int, pixels: IntArray): String {
    require(width > 0 && height > 0 && width <= 2048 && height <= 2048 && width.toLong() * height == pixels.size.toLong())
    val visiblePixels = if (pixels.any { it ushr 24 != 255 }) IntArray(pixels.size) { onWhiteBackground(pixels[it]) } else pixels
    val source = RGBLuminanceSource(width, height, visiblePixels)
    val hints = mapOf(DecodeHintType.POSSIBLE_FORMATS to listOf(BarcodeFormat.QR_CODE), DecodeHintType.TRY_HARDER to true)
    val reader = MultiFormatReader()
    // El mismo lector se usa también para un QR claro sobre fondo oscuro.
    for (candidate in listOf(source, source.invert())) {
        try {
            return reader.decode(BinaryBitmap(HybridBinarizer(candidate)), hints).text
        } catch (_: ReaderException) {
            // Probar la otra polaridad; no se acepta una imagen sin un QR decodificado.
        } finally { reader.reset() }
    }
    throw invalidQrImage("No se encontró un QR legible en la imagen. Selecciona otra imagen o usa la cámara.")
}

internal fun invalidQrImage(message: String) = OperationsFailure(OperationsFailureKind.INVALID_REQUEST, message)

private fun onWhiteBackground(color: Int): Int {
    val alpha = color ushr 24
    val red = ((color ushr 16 and 255) * alpha + 255 * (255 - alpha)) / 255
    val green = ((color ushr 8 and 255) * alpha + 255 * (255 - alpha)) / 255
    val blue = ((color and 255) * alpha + 255 * (255 - alpha)) / 255
    return (255 shl 24) or (red shl 16) or (green shl 8) or blue
}
