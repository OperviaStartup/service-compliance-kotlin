package com.opervia.example.serviceCompliance.features.operations.data

import android.graphics.BitmapFactory
import com.opervia.example.serviceCompliance.features.operations.domain.model.*
import com.opervia.example.serviceCompliance.features.operations.domain.repository.PhotoFiles
import java.io.File
import java.security.MessageDigest
import java.time.Instant
import java.util.UUID

/** Solo expone archivos dentro del directorio privado de la cuenta y la ejecución. */
internal class LocalPhotoFiles(
    private val root: File,
    private val dimensions: (File) -> Pair<Int, Int> = { file ->
        val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.absolutePath, options)
        options.outWidth to options.outHeight
    }
) : PhotoFiles {
    override fun create(userId: Long, executionId: Long): String {
        val directory = directory(userId, executionId)
        check(directory.isDirectory || directory.mkdirs()) { "No se pudo preparar el archivo de la cámara." }
        val name = "${UUID.randomUUID()}.jpg"
        check(File(directory, name).createNewFile()) { "No se pudo crear el archivo de la cámara." }
        return name
    }

    private fun directory(userId: Long, executionId: Long): File {
        require(userId > 0 && executionId > 0)
        val userDirectory = File(root.canonicalFile, "user-$userId")
        require(userDirectory.canonicalFile == userDirectory.absoluteFile)
        val expected = File(userDirectory, "execution-$executionId")
        val directory = expected.canonicalFile
        require(directory == expected.absoluteFile)
        return directory
    }

    override fun file(userId: Long, draft: PhotoDraft): File {
        require(draft.fileName.matches(Regex("[0-9a-fA-F-]{36}\\.jpg"))) { "Referencia de foto inválida." }
        val directory = directory(userId, draft.executionId)
        val file = File(directory, draft.fileName).canonicalFile
        require(file.parentFile == directory) { "Referencia de foto inválida." }
        return file
    }

    override fun inspect(userId: Long, draft: PhotoDraft): CapturedPhoto {
        val file = file(userId, draft)
        if (!file.isFile || file.length() !in 1..MAX_PHOTO_BYTES.toLong()) {
            throw OperationsFailure(OperationsFailureKind.INVALID_REQUEST, "La foto debe contener una imagen de hasta 5 MB.")
        }
        val bytes = file.inputStream().use { input ->
            val output = java.io.ByteArrayOutputStream()
            val buffer = ByteArray(8192)
            var count = input.read(buffer)
            while (count != -1) {
                if (output.size() + count > MAX_PHOTO_BYTES) {
                    throw OperationsFailure(OperationsFailureKind.INVALID_REQUEST, "La foto supera los 5 MB.")
                }
                output.write(buffer, 0, count)
                count = input.read(buffer)
            }
            output.toByteArray()
        }
        val mime = when {
            bytes.size >= 3 && bytes[0] == 0xff.toByte() && bytes[1] == 0xd8.toByte() && bytes[2] == 0xff.toByte() -> "image/jpeg"
            bytes.take(8) == listOf(137, 80, 78, 71, 13, 10, 26, 10).map { it.toByte() } -> "image/png"
            else -> throw OperationsFailure(OperationsFailureKind.INVALID_REQUEST, "La foto debe ser JPEG o PNG.")
        }
        val (width, height) = dimensions(file)
        if (width <= 0 || height <= 0 || width.toLong() * height > 25_000_000) {
            throw OperationsFailure(OperationsFailureKind.INVALID_REQUEST, "La imagen no es válida o tiene dimensiones excesivas.")
        }
        return CapturedPhoto(bytes, mime, photoChecksum(bytes), Instant.ofEpochMilli(file.lastModified()).toString())
    }

    override fun discard(userId: Long, draft: PhotoDraft) {
        val file = file(userId, draft)
        if (file.exists() && !file.delete()) throw java.io.IOException("No se pudo descartar la foto local.")
    }
}

internal fun photoChecksum(bytes: ByteArray): String =
    MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it.toInt() and 0xff) }
