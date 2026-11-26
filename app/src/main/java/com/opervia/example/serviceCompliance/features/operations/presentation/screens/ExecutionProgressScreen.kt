package com.opervia.example.serviceCompliance.features.operations.presentation.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.opervia.example.serviceCompliance.features.operations.domain.model.EvidenceType
import com.opervia.example.serviceCompliance.features.operations.domain.model.ExecutionState
import com.opervia.example.serviceCompliance.features.operations.domain.model.PhotoKind
import com.opervia.example.serviceCompliance.features.operations.presentation.components.InfoNote
import com.opervia.example.serviceCompliance.features.operations.presentation.components.Page
import com.opervia.example.serviceCompliance.features.operations.presentation.components.PrimaryAction
import com.opervia.example.serviceCompliance.features.operations.presentation.components.Section
import com.opervia.example.serviceCompliance.features.operations.presentation.components.FieldSymbol
import com.opervia.example.serviceCompliance.features.operations.presentation.theme.FieldMuted
import com.opervia.example.serviceCompliance.features.operations.presentation.viewmodel.FieldState

@Composable
internal fun ExecutionProgressScreen(
    state: FieldState,
    onStepChanged: (Long, Boolean) -> Unit,
    onSubmit: (String?) -> Unit,
    onPhoto: (PhotoKind, Long, Long?) -> Unit,
    onReport: () -> Unit,
    onStructuredEvidence: (Long) -> Unit,
    onSignature: (Long, Long?) -> Unit,
    onExit: () -> Unit
) {
    val flow = state.flow
    if (flow == null) {
        Page {
            InfoNote("Actualiza el avance o vuelve al detalle para iniciar la actividad.")
        }
        return
    }
    val submitted = flow.state == ExecutionState.SUBMITTED
    val pending = state.pendingSubmission
    var notes by rememberSaveable(flow.id) { mutableStateOf(flow.notes.orEmpty()) }
    var confirm by rememberSaveable(flow.id) { mutableStateOf(false) }
    if (confirm) {
        AlertDialog(
            onDismissRequest = { confirm = false },
            title = { Text("Enviar ejecución") },
            text = { Text("Se enviarán los pasos y evidencias registrados para revisión de supervisión.") },
            confirmButton = { TextButton(onClick = { confirm = false; onSubmit(notes) }) { Text("Enviar") } },
            dismissButton = { TextButton(onClick = { confirm = false }) { Text("Seguir revisando") } }
        )
    }
    Page(footer = {
        if (!submitted) {
            PrimaryAction(
                text = if (pending != null) "Reintentar envío" else "Enviar ejecución",
                onClick = { if (pending != null) onSubmit(pending.notes) else confirm = true },
                enabled = !state.busy && !state.sessionExpired && state.pendingException == null &&
                    (pending != null || (state.flowConfirmed && flow.canSubmit))
            )
        }
        if (state.pendingException != null || (!submitted && flow.canEdit)) {
            TextButton(onClick = onReport,
                enabled = !state.busy && !state.sessionExpired && state.pendingSubmission == null &&
                    (state.pendingException != null || state.canEditSteps), modifier = Modifier.fillMaxWidth()) {
                Text(if (state.pendingException != null) "Revisar impedimento pendiente" else "Reportar impedimento")
            }
        }
        TextButton(onClick = onExit, enabled = !state.busy, modifier = Modifier.fillMaxWidth()) {
            Text("Volver a mis tareas")
        }
    }) {
        Text(if (submitted) "Resultado enviado" else "Actividad en ejecución",
            style = MaterialTheme.typography.headlineMedium)
        state.obligation?.let { ObligationSummary(it, flow.startsAt) }
        if (submitted) {
            InfoNote("Enviado a supervisión · Pendiente de revisión.")
            flow.submittedAt?.let { Text("Enviada ${dateLabel(it)}", color = FieldMuted) }
            flow.notes?.let { Text("Notas: $it") }
        } else if (pending != null) {
            InfoNote("El envío aún no está confirmado. Puedes actualizar el avance o reintentar el mismo envío.", warning = true)
        } else if (!flow.canEdit) {
            InfoNote(if (flow.obligationStatus == "OVERDUE") "La actividad está vencida. El avance guardado se conserva."
                else "Esta ejecución ya no admite modificaciones.", warning = true)
        } else if (!state.flowConfirmed) {
            InfoNote("Actualiza el avance para confirmar los últimos cambios antes de continuar.", warning = true)
        } else {
            InfoNote("Cada paso se guarda cuando el servidor confirma el cambio.")
        }
        if (state.pendingException != null) {
            InfoNote("Confirma el impedimento pendiente para continuar modificando o enviar la actividad.", warning = true)
        }
        if (flow.exceptions.isNotEmpty()) {
            Section("Impedimentos registrados", icon = FieldSymbol.Warning, trailing = flow.exceptions.size.toString()) {
                flow.exceptions.forEach { report ->
                    Text(reasonLabel(report.reason.name), fontWeight = FontWeight.SemiBold)
                    Text(report.description)
                    report.stepId?.let { id -> flow.steps.firstOrNull { it.id == id }?.let { Text("Paso: ${it.title}", color = FieldMuted) } }
                    Text("${dateLabel(report.reportedAt)} · ${report.evidenceIds.size} evidencias vinculadas",
                        style = MaterialTheme.typography.bodySmall, color = FieldMuted)
                }
                InfoNote("Estos reportes se guardan por separado y no sustituyen los pasos ni las evidencias exigidas.")
            }
        }
        Section("Pasos de la actividad", trailing = "${flow.steps.count { it.completed }}/${flow.steps.size}") {
            flow.steps.forEach { step ->
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Checkbox(checked = step.completed, enabled = state.canEditSteps,
                        onCheckedChange = { onStepChanged(step.id, it) })
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(step.title, fontWeight = FontWeight.SemiBold)
                        step.instructions?.takeIf(String::isNotBlank)?.let {
                            Text(it, style = MaterialTheme.typography.bodySmall, color = FieldMuted)
                        }
                    }
                }
            }
        }
        if (flow.photoRequirements.isNotEmpty()) {
            Section("Fotografías requeridas", icon = FieldSymbol.Photo,
                trailing = "${flow.photoRequirements.count { it.evidenceId != null }}/${flow.photoRequirements.size}") {
                flow.photoRequirements.forEach { photo ->
                    Text(photo.title, fontWeight = FontWeight.SemiBold)
                    photo.description?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = FieldMuted) }
                    Text(if (photo.evidenceId != null) "Adjuntada" else "Pendiente", color = FieldMuted)
                    TextButton(onClick = { onPhoto(PhotoKind.PHOTO_REQUIREMENT, photo.id, photo.evidenceId) },
                        enabled = !state.busy && (photo.evidenceId != null || state.canEditSteps)) {
                        Text(if (photo.evidenceId != null) "Ver fotografía" else "Tomar fotografía")
                    }
                }
            }
        }
        if (flow.evidenceRequirements.isNotEmpty()) {
            Section("Evidencias de la actividad", icon = FieldSymbol.Photo) {
                flow.evidenceRequirements.forEach { requirement ->
                    Text(when (requirement.type) {
                        EvidenceType.PHOTO -> "Fotografía"
                        EvidenceType.QR_CODE -> "Código QR"
                        EvidenceType.LOCATION -> "Ubicación"
                        EvidenceType.SIGNATURE -> "Firma"
                    }, fontWeight = FontWeight.SemiBold)
                    Text("${requirement.evidence.size} registradas · mínimo ${requirement.minimumCount}" +
                        if (requirement.required) " · Obligatoria" else " · Opcional",
                        style = MaterialTheme.typography.bodySmall, color = FieldMuted)
                    if (requirement.type == EvidenceType.QR_CODE || requirement.type == EvidenceType.LOCATION) {
                        TextButton(onClick = { onStructuredEvidence(requirement.id) }, enabled = !state.busy) {
                            Text(if (requirement.type == EvidenceType.QR_CODE) "Abrir requisito QR" else "Abrir requisito de ubicación")
                        }
                    }
                    if (requirement.type == EvidenceType.SIGNATURE) {
                        requirement.evidence.forEachIndexed { index, evidence ->
                            TextButton(onClick = { onSignature(requirement.id, evidence.id) }, enabled = !state.busy) {
                                Text("Ver firma ${index + 1}")
                            }
                        }
                        TextButton(onClick = { onSignature(requirement.id, null) }, enabled = !state.busy) { Text("Abrir requisito de firma") }
                    }
                    if (requirement.type == EvidenceType.PHOTO) {
                        requirement.evidence.forEachIndexed { index, evidence ->
                            TextButton(onClick = { onPhoto(PhotoKind.EVIDENCE_REQUIREMENT, requirement.id, evidence.id) }, enabled = !state.busy) {
                                Text("Ver fotografía ${index + 1}")
                            }
                        }
                        if (requirement.evidence.size < requirement.maximumCount) {
                            TextButton(onClick = { onPhoto(PhotoKind.EVIDENCE_REQUIREMENT, requirement.id, null) }, enabled = state.canEditSteps) {
                                Text("Tomar fotografía")
                            }
                        }
                    }
                }
            }
        }
        if (!submitted) {
            OutlinedTextField(
                value = pending?.notes ?: notes,
                onValueChange = { if (it.length <= 2000) notes = it },
                label = { Text("Notas de la ejecución") }, supportingText = { Text("${(pending?.notes ?: notes).length}/2000") },
                enabled = state.canEditSteps,
                modifier = Modifier.fillMaxWidth(), minLines = 3, shape = RoundedCornerShape(12.dp)
            )
            if (!flow.requirementsComplete) {
                InfoNote("Completa todos los pasos y adjunta las evidencias obligatorias para habilitar el envío.")
            }
        }
        state.localPersistenceWarning?.let { InfoNote(it, warning = true) }
    }
}
