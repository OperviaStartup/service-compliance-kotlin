package com.opervia.example.serviceCompliance.features.operations.data

import android.content.Context
import android.graphics.BitmapFactory
import android.net.Uri
import com.opervia.example.serviceCompliance.features.operations.domain.repository.QrImageReader
import java.io.IOException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Lee únicamente el contenido elegido por el usuario, fuera del hilo de la interfaz. */
internal class AndroidQrImageReader(context: Context) : QrImageReader {
    private val resolver = context.applicationContext.contentResolver

    override suspend fun read(uri: String): String = withContext(Dispatchers.IO) {
        val selected = Uri.parse(uri)
        if (selected.scheme != "content") throw invalidQrImage("Selecciona la imagen desde el selector de archivos.")
        val bytes = try {
            resolver.openInputStream(selected)?.use(::readQrImageBytes)
                ?: throw invalidQrImage("No se pudo abrir la imagen seleccionada.")
        } catch (_: SecurityException) {
            throw invalidQrImage("No se pudo acceder a la imagen. Vuelve a seleccionarla.")
        } catch (_: IOException) {
            throw invalidQrImage("No se pudo leer la imagen. Vuelve a seleccionarla.")
        }
        decodeQrImage(bytes)
    }
}

internal fun decodeQrImage(bytes: ByteArray): String {
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
    if (bounds.outWidth <= 0 || bounds.outHeight <= 0 || bounds.outWidth.toLong() * bounds.outHeight > 25_000_000) {
        throw invalidQrImage("Selecciona una imagen válida de hasta 25 millones de píxeles.")
    }
    var sample = 1
    while ((bounds.outWidth + sample - 1) / sample > 2048 || (bounds.outHeight + sample - 1) / sample > 2048) sample *= 2
    val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size, BitmapFactory.Options().apply { inSampleSize = sample })
        ?: throw invalidQrImage("La imagen está dañada o tiene un formato no compatible.")
    try {
        val pixels = IntArray(bitmap.width * bitmap.height)
        bitmap.getPixels(pixels, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)
        return decodeQrPixels(bitmap.width, bitmap.height, pixels)
    } finally { bitmap.recycle() }
}
