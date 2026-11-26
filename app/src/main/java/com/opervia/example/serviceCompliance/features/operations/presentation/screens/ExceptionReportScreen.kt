package com.opervia.example.serviceCompliance.features.operations.presentation.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.opervia.example.serviceCompliance.features.operations.domain.model.ExceptionReason
import com.opervia.example.serviceCompliance.features.operations.domain.model.ExecutionState
import com.opervia.example.serviceCompliance.features.operations.domain.model.EvidenceType
import com.opervia.example.serviceCompliance.features.operations.presentation.components.*
import com.opervia.example.serviceCompliance.features.operations.presentation.theme.FieldMuted
import com.opervia.example.serviceCompliance.features.operations.presentation.viewmodel.FieldState

@Composable
internal fun ExceptionReportScreen(
    state: FieldState,
    onReport: (ExceptionReason, String, Long?, List<Long>) -> Unit,
    onDone: () -> Unit
) {
    val flow = state.flow
    if (flow == null || state.detail == null) {
        Page { InfoNote("Actualiza para recuperar la actividad antes de reportar un impedimento.") }
        return
    }
    var reasonName by rememberSaveable(flow.id) { mutableStateOf(ExceptionReason.AREA_CLOSED.name) }
    var description by rememberSaveable(flow.id) { mutableStateOf("") }
    var stepId by rememberSaveable(flow.id) { mutableLongStateOf(-1L) }
    var selectedEvidence by rememberSaveable(flow.id) { mutableStateOf(emptyList<Long>()) }
    var showSteps by remember { mutableStateOf(false) }
    val pending = state.pendingException
    val recorded = state.reportedException
    LaunchedEffect(pending?.operationId) {
        pending?.let {
            reasonName = it.reason.name
            description = it.description
            stepId = it.stepId ?: -1L
            selectedEvidence = it.evidenceIds
        }
    }
    val reason = pending?.reason ?: ExceptionReason.valueOf(reasonName)
    val text = pending?.description ?: description
    val selectedStep = pending?.let { it.stepId ?: -1L } ?: stepId
    val evidenceIds = pending?.evidenceIds ?: selectedEvidence
    val editable = state.canEditSteps && recorded == null
    val canSend = !state.busy && !state.sessionExpired && recorded == null &&
        (pending != null || (editable && text.isNotBlank() && text.length <= 2000))
    Page(footer = {
        if (recorded == null) {
            PrimaryAction(if (pending != null) "Reintentar reporte" else "Enviar para revisión", {
                onReport(reason, text, selectedStep.takeIf { it > 0 }, evidenceIds)
            }, enabled = canSend)
        }
        TextButton(onClick = onDone, enabled = !state.busy, modifier = Modifier.fillMaxWidth()) {
            Text("Volver a la actividad")
        }
    }) {
        Text(if (recorded != null) "Impedimento registrado" else "Reportar impedimento",
            style = MaterialTheme.typography.headlineMedium)
        ObligationSummary(state.detail.obligation, flow.startsAt)
        if (recorded != null) {
            Section("Registro confirmado", icon = FieldSymbol.Check) {
                StatusPill("Impedimento registrado", warning = true)
                Text(reasonLabel(recorded.reason.name), fontWeight = FontWeight.SemiBold)
                Text(recorded.description)
                recorded.stepId?.let { id ->
                    flow.steps.firstOrNull { it.id == id }?.let { Text("Paso: ${it.title}") }
                }
                Text("Registrado ${dateLabel(recorded.reportedAt)}", color = FieldMuted)
                Text("Evidencias vinculadas: ${recorded.evidenceIds.size}", color = FieldMuted)
            }
            InfoNote(if (flow.state == ExecutionState.IN_PROGRESS)
                "La actividad sigue en curso e incompleta. Se conservan el avance y los requisitos pendientes; este reporte no envía la ejecución."
                else "El reporte está guardado por separado. No modifica el resultado ni la revisión de la ejecución.")
        } else {
            if (pending != null) {
                InfoNote("Este reporte aún no está confirmado. Actualiza el avance o reintenta los mismos datos para evitar duplicados.", warning = true)
            } else if (!editable) {
                InfoNote("Solo puedes registrar un impedimento mientras la actividad esté en curso y dentro del plazo.", warning = true)
            }
            Text("Selecciona el motivo principal", style = MaterialTheme.typography.titleMedium)
            ExceptionReason.entries.forEach { value ->
                val selected = reason == value
                Surface(color = if (selected) Color(0xFFEAF4FF) else Color.White, shape = RoundedCornerShape(12.dp)) {
                    Row(Modifier.fillMaxWidth().selectable(selected, enabled = editable, role = Role.RadioButton,
                        onClick = { reasonName = value.name }).padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        FieldIcon(if (value == ExceptionReason.SAFETY_RISK) FieldSymbol.Warning else FieldSymbol.Document)
                        Column(Modifier.weight(1f).padding(horizontal = 10.dp)) {
                            Text(reasonLabel(value.name), fontWeight = FontWeight.SemiBold)
                            Text(when (value) {
                                ExceptionReason.AREA_CLOSED -> "Sin acceso físico o llave requerida."
                                ExceptionReason.MISSING_SUPPLIES -> "Faltan materiales para continuar."
                                ExceptionReason.SAFETY_RISK -> "Una condición impide trabajar de forma segura."
                                ExceptionReason.OTHER -> "Describe otra situación que afecta la actividad."
                            }, style = MaterialTheme.typography.bodySmall, color = FieldMuted)
                        }
                        RadioButton(selected, onClick = null, enabled = editable)
                    }
                }
            }
            OutlinedTextField(value = text, onValueChange = { if (it.length <= 2000) description = it },
                enabled = editable, label = { Text("Detalle del impedimento") }, minLines = 3,
                supportingText = { Text("${text.length}/2000 · Obligatorio") }, modifier = Modifier.fillMaxWidth())
            Section("Paso afectado", icon = FieldSymbol.Steps) {
                Box {
                    OutlinedButton(onClick = { showSteps = true }, enabled = editable, modifier = Modifier.fillMaxWidth()) {
                        Text(flow.steps.firstOrNull { it.id == selectedStep }?.title ?: "Toda la actividad")
                    }
                    DropdownMenu(expanded = showSteps, onDismissRequest = { showSteps = false }) {
                        DropdownMenuItem(text = { Text("Toda la actividad") }, onClick = { stepId = -1; showSteps = false })
                        flow.steps.forEach { step ->
                            DropdownMenuItem(text = { Text(step.title) }, onClick = { stepId = step.id; showSteps = false })
                        }
                    }
                }
            }
            val attachments = flow.photoRequirements.mapNotNull { photo ->
                photo.evidenceId?.let { it to photo.title }
            } + flow.evidenceRequirements.flatMap { requirement ->
                requirement.evidence.mapIndexed { index, evidence ->
                    evidence.id to "${when (requirement.type) {
                        EvidenceType.PHOTO -> "Fotografía"
                        EvidenceType.QR_CODE -> "Código QR"
                        EvidenceType.LOCATION -> "Ubicación"
                        EvidenceType.SIGNATURE -> "Firma"
                    }} · requisito ${requirement.position} · ${index + 1}"
                }
            }
            Section("Evidencias del reporte", icon = FieldSymbol.Photo, trailing = "Opcional") {
                Text("Puedes vincular evidencias que ya estén registradas en esta ejecución.",
                    style = MaterialTheme.typography.bodySmall, color = FieldMuted)
                attachments.distinctBy { it.first }.forEach { (id, title) ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(checked = id in evidenceIds, enabled = editable, onCheckedChange = { checked ->
                            selectedEvidence = if (checked) selectedEvidence + id else selectedEvidence - id
                        })
                        Text(title, modifier = Modifier.weight(1f))
                    }
                }
                if (attachments.isEmpty()) Text("Todavía no hay evidencias registradas.", color = FieldMuted)
            }
            InfoNote("Registrar el impedimento conserva la actividad incompleta. Los pasos y las fotos exigidas siguen siendo obligatorios para enviarla.")
        }
    }
}
