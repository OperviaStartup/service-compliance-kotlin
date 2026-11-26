package com.opervia.example.serviceCompliance.features.operations.presentation.screens

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.unit.dp
import com.opervia.example.serviceCompliance.features.operations.data.SignaturePoint
import com.opervia.example.serviceCompliance.features.operations.presentation.components.*
import com.opervia.example.serviceCompliance.features.operations.presentation.viewmodel.PhotoState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
internal fun SignatureEvidenceScreen(state: PhotoState, onSave: (List<List<SignaturePoint>>) -> Unit,
    onLocation: () -> Unit, onUpload: () -> Unit, onDiscard: () -> Unit, onRefresh: () -> Unit, onDone: () -> Unit) {
    var strokes by rememberSaveable { mutableStateOf(emptyList<List<SignaturePoint>>()) }
    val registered = state.evidenceId != null
    Page(footer = {
        if (!registered) {
            if (state.draft?.checksum != null) {
                PrimaryAction(if (state.draft.uploadPending) "Reintentar subida" else "Subir firma", onUpload, enabled = state.canUpload)
                if (!state.draft.uploadPending) TextButton(onClick = { strokes = emptyList(); onDiscard() }, enabled = !state.busy) {
                    Text("Volver a firmar")
                }
            } else {
                PrimaryAction("Revisar firma", { onSave(strokes) }, enabled = state.canCapture && strokes.any { it.size > 1 })
            }
        }
        TextButton(onClick = onDone, enabled = !state.busy, modifier = Modifier.fillMaxWidth()) { Text("Volver a la actividad") }
    }) {
        Text(if (registered) "Firma registrada" else "Evidencia de firma", style = MaterialTheme.typography.headlineMedium)
        state.error?.let { InfoNote(it, warning = true) }
        if (state.busy) LinearProgressIndicator(Modifier.fillMaxWidth())
        if (state.preview != null) {
            val preview by produceState<android.graphics.Bitmap?>(initialValue = null, key1 = state.preview) {
                value = withContext(Dispatchers.IO) { decodePhotoPreview(state.preview) }
            }
            FieldCard {
                preview?.let { Image(it.asImageBitmap(), contentDescription = "Vista previa de la firma",
                    modifier = Modifier.fillMaxWidth().aspectRatio(2f)) } ?: Text("Preparando vista previa…")
            }
        } else if (state.canCapture) {
            FieldCard {
                Text("Dibuja la firma dentro del recuadro.")
                SignaturePad(strokes, onChange = { strokes = it })
                TextButton(onClick = { strokes = emptyList() }) { Text("Limpiar panel") }
            }
        }
        InfoNote(when {
            registered -> "La firma está registrada y puede consultarse, pero no reemplazarse."
            state.draft?.uploadPending == true -> "La subida no está confirmada. Conservamos el mismo archivo para reintentar."
            state.draft?.checksum != null -> "Revisa tu firma antes de subirla. Se enviará como PNG de hasta 1 MB."
            else -> "La firma solo se registra cuando confirmas su subida al servidor."
        }, warning = state.draft?.uploadPending == true)
        if (!registered && state.requiresLocation) {
            FieldCard {
                Text(if (state.draft?.location != null) "Ubicación registrada" else "Este requisito exige la ubicación de la firma.")
                OutlinedButton(onClick = onLocation, enabled = state.canCapture && state.draft?.checksum != null) { Text("Obtener ubicación") }
            }
        }
        if (state.submissionPending) InfoNote("Confirma el envío o impedimento pendiente antes de registrar una firma.", warning = true)
        if (state.flow?.canEdit == false && !registered) InfoNote("La actividad ya no admite nuevas firmas. El avance se conserva.", warning = true)
        TextButton(onClick = onRefresh, enabled = !state.busy) { Text("Actualizar firma") }
    }
}
