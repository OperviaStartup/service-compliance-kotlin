package com.opervia.example.serviceCompliance.features.operations.presentation.screens

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import com.opervia.example.serviceCompliance.features.operations.domain.model.EvidenceType
import com.opervia.example.serviceCompliance.features.operations.domain.model.PhotoLocation
import com.opervia.example.serviceCompliance.features.operations.presentation.components.*
import com.opervia.example.serviceCompliance.features.operations.presentation.theme.FieldMuted
import com.opervia.example.serviceCompliance.features.operations.presentation.viewmodel.StructuredEvidenceState

@Composable
internal fun StructuredEvidenceScreen(
    state: StructuredEvidenceState,
    onScan: () -> Unit,
    onPickImage: () -> Unit,
    onLocation: () -> Unit,
    onUpload: () -> Unit,
    onDiscard: () -> Unit,
    onRefresh: () -> Unit,
    onDone: () -> Unit
) {
    val qr = state.requirement?.type == EvidenceType.QR_CODE
    Page(footer = {
        if (state.draft != null) {
            PrimaryAction(if (state.draft.uploadPending) "Reintentar registro" else "Registrar evidencia", onUpload,
                enabled = state.canUpload)
            if (!state.draft.uploadPending) TextButton(onClick = onDiscard, enabled = !state.busy) { Text("Descartar captura local") }
        }
        TextButton(onClick = onDone, enabled = !state.busy, modifier = Modifier.fillMaxWidth()) { Text("Volver a la actividad") }
    }) {
        Text(if (qr) "Evidencia con código QR" else "Evidencia de ubicación", style = MaterialTheme.typography.headlineMedium)
        state.error?.let { InfoNote(it, warning = true) }
        if (state.busy) LinearProgressIndicator(Modifier.fillMaxWidth())
        state.requirement?.let { requirement ->
            FieldCard {
                Text(if (qr) "Código QR de la actividad" else "Ubicación del dispositivo", fontWeight = FontWeight.Bold)
                Text("${requirement.evidence.size}/${requirement.maximumCount} registradas · mínimo ${requirement.minimumCount}" +
                    if (requirement.required) " · Obligatoria" else " · Opcional", color = FieldMuted)
                if (qr) requirement.constraints["expectedPrefix"]?.let { Text("Prefijo esperado: $it", color = FieldMuted) }
                else requirement.constraints["maxAccuracyMeters"]?.let { Text("Precisión máxima: $it m", color = FieldMuted) }
                requirement.constraints["maxAgeMinutes"]?.let { Text("Antigüedad máxima: $it minutos", color = FieldMuted) }
            }
            if (state.draft?.uploadPending != true) {
                OutlinedButton(onClick = if (qr) onScan else onLocation, enabled = state.canCapture, modifier = Modifier.fillMaxWidth()) {
                    Text(if (qr) "Escanear código QR" else "Obtener ubicación actual")
                }
                if (qr) {
                    OutlinedButton(onClick = onPickImage, enabled = state.canCapture, modifier = Modifier.fillMaxWidth()) {
                        Text("Seleccionar imagen del QR")
                    }
                }
            }
            state.draft?.let { draft ->
                Section("Revisar captura", icon = FieldSymbol.Document) {
                    if (qr) Text(draft.textValue.orEmpty()) else draft.location?.let { LocationValues(it) }
                    Text("Capturada ${dateLabel(draft.capturedAt)}", color = FieldMuted)
                    InfoNote(if (draft.uploadPending) "El registro aún no está confirmado. Actualiza o reintenta esta misma captura."
                        else "Revisa los datos antes de registrarlos. Una evidencia registrada no puede reemplazarse.", warning = draft.uploadPending)
                }
            }
            if (requirement.evidence.isNotEmpty()) {
                Section("Evidencias registradas", icon = FieldSymbol.Check) {
                    requirement.evidence.forEachIndexed { index, evidence ->
                        Text("Registro ${index + 1}", fontWeight = FontWeight.SemiBold)
                        if (qr) Text(evidence.textValue.orEmpty())
                        else if (evidence.latitude != null && evidence.longitude != null && evidence.accuracyMeters != null) {
                            LocationValues(PhotoLocation(evidence.latitude, evidence.longitude, evidence.accuracyMeters))
                        }
                        evidence.capturedAt?.let { Text("Capturada ${dateLabel(it)}", color = FieldMuted) }
                    }
                    InfoNote("Estos datos están registrados en el servidor. Actualiza para consultar el avance más reciente.")
                }
            }
            if (state.submissionPending) InfoNote("Confirma el envío o impedimento pendiente antes de registrar nuevas evidencias.", warning = true)
            if (state.flow?.canEdit == false) InfoNote("La actividad ya no admite nuevas evidencias. Sus registros se conservan.", warning = true)
            if (requirement.evidence.isEmpty() && state.draft == null) {
                InfoNote(if (qr) "Escanea el QR con la cámara o selecciona una imagen que lo contenga (hasta 5 MB). Revisa el código leído antes de registrarlo."
                    else "Se solicitará permiso para obtener la ubicación del dispositivo. No se introducen coordenadas manualmente.")
            }
        }
        TextButton(onClick = onRefresh, enabled = !state.busy) { Text("Actualizar evidencia") }
    }
}

@Composable
private fun LocationValues(point: PhotoLocation) {
    Text("Latitud: ${point.latitude}")
    Text("Longitud: ${point.longitude}")
    Text("Precisión: ${point.accuracyMeters} m", color = FieldMuted)
}
