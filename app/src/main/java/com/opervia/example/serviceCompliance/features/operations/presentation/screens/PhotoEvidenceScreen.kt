package com.opervia.example.serviceCompliance.features.operations.presentation.screens

import android.graphics.BitmapFactory
import android.graphics.Matrix
import androidx.exifinterface.media.ExifInterface
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.opervia.example.serviceCompliance.features.operations.presentation.components.*
import com.opervia.example.serviceCompliance.features.operations.presentation.theme.FieldMuted
import com.opervia.example.serviceCompliance.features.operations.presentation.viewmodel.PhotoState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
internal fun PhotoEvidenceScreen(
    state: PhotoState,
    onCapture: () -> Unit,
    onLocation: () -> Unit,
    onUpload: () -> Unit,
    onDiscard: () -> Unit,
    onRefresh: () -> Unit,
    onDone: () -> Unit
) {
    val registered = state.evidenceId != null
    Page(footer = {
        if (!registered) {
            if (state.draft?.checksum != null) {
                PrimaryAction(if (state.draft.uploadPending) "Reintentar subida" else "Subir fotografía",
                    onUpload, enabled = state.canUpload)
            }
            if (state.draft?.uploadPending != true) {
                OutlinedButton(onClick = onCapture, enabled = state.canCapture, modifier = Modifier.fillMaxWidth()) {
                    Text(if (state.draft == null) "Tomar fotografía" else "Volver a tomar fotografía")
                }
            }
        }
        if (state.draft != null && !state.draft.uploadPending) {
            TextButton(onClick = onDiscard, enabled = !state.busy) { Text("Descartar borrador local") }
        }
        TextButton(onClick = onDone, enabled = !state.busy, modifier = Modifier.fillMaxWidth()) { Text("Volver a la actividad") }
    }) {
        Text(if (registered) "Fotografía registrada" else "Evidencia fotográfica", style = MaterialTheme.typography.headlineMedium)
        FieldCard {
            Text(state.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            state.description?.let { Text(it, color = FieldMuted) }
        }
        if (state.busy) LinearProgressIndicator(Modifier.fillMaxWidth())
        state.error?.let { InfoNote(it, warning = true) }
        val bytes = state.preview
        if (bytes != null) {
            val preview by produceState<PhotoPreview?>(initialValue = null, key1 = bytes) {
                value = withContext(Dispatchers.IO) { PhotoPreview(decodePhotoPreview(bytes)) }
            }
            FieldCard {
                preview?.bitmap?.let {
                    Image(it.asImageBitmap(), contentDescription = "Vista previa de la fotografía",
                        modifier = Modifier.fillMaxWidth().heightIn(min = 180.dp, max = 360.dp), contentScale = ContentScale.Fit)
                } ?: Text(if (preview == null) "Preparando vista previa…" else "No se pudo mostrar la vista previa. Actualiza la fotografía.", color = FieldMuted)
            }
        }
        if (registered) {
            InfoNote("La fotografía está registrada. Puedes consultarla, pero no reemplazarla.")
        } else if (state.draft?.uploadPending == true) {
            InfoNote("La subida aún no está confirmada. Conservamos el mismo archivo para reintentar sin reemplazarlo.", warning = true)
        } else if (state.draft?.checksum != null) {
            InfoNote("Revisa la foto antes de subirla. Una vez registrada no podrás reemplazarla.")
        } else {
            InfoNote("Toma una fotografía que muestre el resultado solicitado. Se admiten JPEG o PNG de hasta 5 MB.")
        }
        if (!registered && state.requiresLocation) {
            FieldCard {
                Text("Ubicación de la fotografía", fontWeight = FontWeight.SemiBold)
                Text(if (state.draft?.location != null) "Ubicación registrada" else "Este requisito exige la ubicación del dispositivo.", color = FieldMuted)
                OutlinedButton(onClick = onLocation, enabled = state.canCapture && state.draft?.checksum != null) {
                    Text(if (state.draft?.location != null) "Actualizar ubicación" else "Registrar ubicación")
                }
            }
        }
        if (state.submissionPending) InfoNote("Confirma el envío o impedimento pendiente antes de adjuntar nuevas fotografías.", warning = true)
        if (state.flow?.canEdit == false && !registered) InfoNote("La actividad ya no admite fotografías. El avance guardado se conserva.", warning = true)
        TextButton(onClick = onRefresh, enabled = !state.busy) { Text("Actualizar fotografía") }
    }
}

private data class PhotoPreview(val bitmap: android.graphics.Bitmap?)

internal fun decodePhotoPreview(bytes: ByteArray): android.graphics.Bitmap? {
    val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options)
    if (options.outWidth <= 0 || options.outHeight <= 0 || options.outWidth.toLong() * options.outHeight > 25_000_000) return null
    var sample = 1
    while (options.outWidth / sample > 1280 || options.outHeight / sample > 1280) sample *= 2
    val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size, BitmapFactory.Options().apply { inSampleSize = sample }) ?: return null
    val orientation = runCatching {
        ExifInterface(bytes.inputStream()).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)
    }.getOrDefault(ExifInterface.ORIENTATION_NORMAL)
    val matrix = Matrix().apply {
        when (orientation) {
            ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> postScale(-1f, 1f)
            ExifInterface.ORIENTATION_ROTATE_180 -> postRotate(180f)
            ExifInterface.ORIENTATION_FLIP_VERTICAL -> postScale(1f, -1f)
            ExifInterface.ORIENTATION_TRANSPOSE -> { postRotate(90f); postScale(-1f, 1f) }
            ExifInterface.ORIENTATION_ROTATE_90 -> postRotate(90f)
            ExifInterface.ORIENTATION_TRANSVERSE -> { postRotate(-90f); postScale(-1f, 1f) }
            ExifInterface.ORIENTATION_ROTATE_270 -> postRotate(-90f)
        }
    }
    if (matrix.isIdentity) return bitmap
    return android.graphics.Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true).also {
        if (it !== bitmap) bitmap.recycle()
    }
}
