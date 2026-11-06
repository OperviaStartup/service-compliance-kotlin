package com.upc.example.serviceComplianceKotlin.fieldoperations.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.upc.example.serviceComplianceKotlin.fieldoperations.components.*
import com.upc.example.serviceComplianceKotlin.fieldoperations.viewmodel.FieldState
import com.upc.example.serviceComplianceKotlin.fieldoperations.theme.*

@Composable
internal fun ExecutionScreen(state: FieldState, onCheck: (Long, Boolean) -> Unit,
    onPhoto: (Long) -> Unit, onReport: () -> Unit, onSubmit: () -> Unit, onExit: () -> Unit) {
    val execution = state.execution ?: return
    val editable = execution.canEdit && !state.busy && !state.needsRefresh && !state.sessionExpired
    var confirm by remember { mutableStateOf(false) }
    if (confirm) {
        AlertDialog(
            onDismissRequest = { confirm = false },
            title = { Text("Enviar ejecución") },
            text = { Text("Se enviarán los pasos y las evidencias registrados. Luego no podrás modificarlos.") },
            confirmButton = {
                TextButton(onClick = {
                    confirm = false
                    onSubmit()
                }) {
                    Text("Enviar")
                }
            },
            dismissButton = {
                TextButton(onClick = { confirm = false }) {
                    Text("Seguir revisando")
                }
            }
        )
    }
    Page(footer = {
        if (execution.state == "SUBMITTED") {
            PrimaryAction("Volver a mi trabajo", onExit)
        } else {
            OutlinedButton(onClick = onReport, enabled = editable, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
                shape = RoundedCornerShape(8.dp), border = BorderStroke(1.dp, FieldBlue.copy(alpha = .2f))) {
                FieldIcon(FieldSymbol.Warning)
                Spacer(Modifier.width(10.dp))
                Text("Reportar impedimento")
            }
            PrimaryAction("Enviar ejecución", { confirm = true }, enabled = editable && execution.readyToSubmit)
            if (!execution.readyToSubmit && execution.canEdit) Text("Completa todos los pasos y las fotos para enviar.",
                style = MaterialTheme.typography.bodySmall, color = FieldMuted)
        }
    }) {
        Text(if (execution.state == "SUBMITTED") "Resultado enviado" else "Actividad en ejecución", style = MaterialTheme.typography.headlineMedium)
        StatusPill(statusLabel(execution.obligationStatus), positive = execution.obligationStatus in listOf("IN_PROGRESS", "SUBMITTED"),
            warning = execution.obligationStatus == "OVERDUE")
        FieldCard {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                IconTile(if (execution.state == "SUBMITTED") FieldSymbol.Check else FieldSymbol.Building)
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(state.detail?.title.orEmpty(), style = MaterialTheme.typography.titleMedium)
                    state.detail?.siteName?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = FieldMuted) }
                }
            }
            state.detail?.dueAt?.let {
                HorizontalDivider(color = FieldLine)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    FieldIcon(FieldSymbol.Clock)
                    Text("Hora límite: ${dateLabel(it)} · Lima", style = MaterialTheme.typography.bodySmall, color = FieldMuted)
                }
            }
        }
        if (execution.state == "SUBMITTED") {
            Surface(color = MaterialTheme.colorScheme.secondaryContainer, shape = RoundedCornerShape(12.dp)) {
                Row(Modifier.padding(16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    FieldIcon(FieldSymbol.Check, tint = FieldTeal)
                    Column {
                        Text("Ejecución enviada", fontWeight = FontWeight.Bold)
                        Text("El servidor confirmó el envío. Tu ejecución está disponible para revisión.", style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        }
        if (execution.obligationStatus == "OVERDUE") InfoNote("Terminó el plazo. Se conserva el avance y no se admiten nuevos cambios.", warning = true)
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text("Pasos a realizar", Modifier.weight(1f), style = MaterialTheme.typography.titleMedium)
            Text("${execution.steps.count { it.completed }} de ${execution.steps.size}", style = MaterialTheme.typography.bodySmall, color = FieldMuted)
        }
        LinearProgressIndicator(progress = { if (execution.steps.isEmpty()) 0f else execution.steps.count { it.completed }.toFloat() / execution.steps.size },
            modifier = Modifier.fillMaxWidth().height(4.dp), color = FieldTeal, trackColor = FieldLine)
        execution.steps.forEach { step ->
            FieldCard {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Checkbox(checked = step.completed, enabled = editable, onCheckedChange = { onCheck(step.id, it) },
                        colors = CheckboxDefaults.colors(checkedColor = FieldTeal))
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(step.title, fontWeight = FontWeight.SemiBold)
                        step.instructions?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = FieldMuted) }
                    }
                }
            }
        }
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text("Evidencia requerida", Modifier.weight(1f), style = MaterialTheme.typography.titleMedium)
            Text("${execution.photos.count { it.evidenceId != null }} de ${execution.photos.size}", style = MaterialTheme.typography.bodySmall, color = FieldMuted)
        }
        execution.photos.forEach { photo ->
            FieldCard {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                    IconTile(if (photo.evidenceId != null) FieldSymbol.Check else FieldSymbol.Photo,
                        tint = if (photo.evidenceId != null) FieldTeal else FieldBlue)
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(photo.title, fontWeight = FontWeight.SemiBold)
                        Text(if (photo.evidenceId != null) "Fotografía registrada" else "Obligatoria para finalizar", style = MaterialTheme.typography.bodySmall,
                            color = if (photo.evidenceId != null) FieldTeal else FieldMuted)
                    }
                }
                photo.description?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = FieldMuted) }
                if (photo.evidenceId != null) {
                    OutlinedButton(onClick = { onPhoto(photo.id) }, enabled = !state.busy, modifier = Modifier.fillMaxWidth()) { Text("Ver fotografía") }
                } else Button(onClick = { onPhoto(photo.id) }, enabled = editable, modifier = Modifier.fillMaxWidth()) { Text("Capturar fotografía") }
            }
        }
        if (execution.photos.isEmpty()) InfoNote("Esta obligación no requiere fotografías.")
        if (execution.exceptions.isNotEmpty()) Section("Impedimentos registrados", icon = FieldSymbol.Warning) {
            for (exception in execution.exceptions) {
                Text(reasonLabel(exception.reason), fontWeight = FontWeight.SemiBold)
                Text(exception.description, style = MaterialTheme.typography.bodySmall, color = FieldMuted)
            }
        }
        if (execution.state != "SUBMITTED") InfoNote("Tu avance se guarda al confirmar cada paso y cada fotografía. El impedimento no completa la obligación.")
    }
}
