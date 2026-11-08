package com.opervia.example.serviceCompliance.features.operations.presentation.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.opervia.example.serviceCompliance.features.operations.presentation.components.FieldCard
import com.opervia.example.serviceCompliance.features.operations.presentation.components.FieldIcon
import com.opervia.example.serviceCompliance.features.operations.presentation.components.FieldSymbol
import com.opervia.example.serviceCompliance.features.operations.presentation.components.InfoNote
import com.opervia.example.serviceCompliance.features.operations.presentation.components.Page
import com.opervia.example.serviceCompliance.features.operations.presentation.components.PrimaryAction
import com.opervia.example.serviceCompliance.features.operations.presentation.components.StatusPill
import com.opervia.example.serviceCompliance.features.operations.domain.model.EvidenceRecord
import com.opervia.example.serviceCompliance.features.operations.domain.model.ExecutionResult
import com.opervia.example.serviceCompliance.features.operations.presentation.theme.FieldBlue
import com.opervia.example.serviceCompliance.features.operations.presentation.theme.FieldMuted
import com.opervia.example.serviceCompliance.features.operations.presentation.viewmodel.FieldState

@Composable
internal fun ExecutionScreen(
    state: FieldState,
    onSubmit: (ExecutionResult, String?) -> Unit,
    onEvidence: () -> Unit,
    onReport: () -> Unit,
    onExit: () -> Unit
) {
    val record = state.execution
    var selected by rememberSaveable { mutableStateOf(ExecutionResult.COMPLETED) }
    var notes by rememberSaveable { mutableStateOf("") }
    var confirm by remember { mutableStateOf(false) }
    if (confirm) AlertDialog(
        onDismissRequest = { confirm = false },
        title = { Text("Enviar ejecución") },
        text = { Text("Se registrará el resultado declarado y quedará disponible para revisión de supervisión.") },
        confirmButton = { TextButton(onClick = { confirm = false; onSubmit(selected, notes.takeIf(String::isNotBlank)) }) { Text("Enviar") } },
        dismissButton = { TextButton(onClick = { confirm = false }) { Text("Seguir revisando") } }
    )

    Page(footer = {
        if (record != null) PrimaryAction("Volver a mis tareas", onExit)
        else {
            OutlinedButton(onClick = onReport, enabled = !state.busy && !state.sessionExpired && !state.submissionUncertain,
                modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(10.dp)) {
                FieldIcon(FieldSymbol.Warning)
                Text("  Registrar impedimento")
            }
            PrimaryAction("Enviar ejecución", { confirm = true }, enabled = !state.busy && !state.sessionExpired && !state.submissionUncertain)
        }
    }) {
        Text(if (record == null) "Actividad en ejecución" else "Resultado enviado", style = MaterialTheme.typography.headlineMedium)
        state.obligation?.let { ObligationSummary(it) }
        if (record == null) {
            Text("¿Qué ocurrió durante la actividad?", style = MaterialTheme.typography.titleMedium)
            ExecutionResult.entries.forEach { result ->
                val checked = selected == result
                Surface(
                    color = if (checked) Color(0xFFEAF4FF) else Color.White,
                    shape = RoundedCornerShape(12.dp),
                    border = BorderStroke(1.dp, if (checked) FieldBlue.copy(alpha = .35f) else Color.White)
                ) {
                    Row(Modifier.fillMaxWidth().selectable(checked, role = Role.RadioButton, onClick = { selected = result }).padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically) {
                        RadioButton(checked, onClick = null, colors = RadioButtonDefaults.colors(selectedColor = FieldBlue))
                        Column(Modifier.weight(1f)) {
                            Text(result.label(), fontWeight = FontWeight.SemiBold)
                            Text(result.description(), style = MaterialTheme.typography.bodySmall, color = FieldMuted)
                        }
                    }
                }
            }
            OutlinedTextField(
                value = notes,
                onValueChange = { if (it.length <= 2000) notes = it },
                label = { Text(if (selected == ExecutionResult.BLOCKED) "Describe el impedimento" else "Notas de la ejecución") },
                supportingText = { Text("${notes.length}/2000") },
                modifier = Modifier.fillMaxWidth(), minLines = 3, enabled = !state.busy,
                shape = RoundedCornerShape(12.dp)
            )
            if (selected == ExecutionResult.BLOCKED) InfoNote("El backend registra un resultado BLOCKED con notas; todavía no conserva un recurso Exception separado.", warning = true)
            else InfoNote("El envío registra lo declarado por el operario. No representa una decisión de cumplimiento.")
        } else {
            FieldCard {
                StatusPill(record.result.label(), positive = record.result == ExecutionResult.COMPLETED, warning = record.result != ExecutionResult.COMPLETED)
                Text("Ejecución #${record.id}", style = MaterialTheme.typography.titleMedium)
                Text("Enviada ${dateLabel(record.executedAt)}", style = MaterialTheme.typography.bodySmall, color = FieldMuted)
                record.notes?.let { Text(it, style = MaterialTheme.typography.bodyMedium) }
            }
            InfoNote("Enviado a supervisión · La evaluación de cumplimiento sigue pendiente.")
            FieldCard {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FieldIcon(FieldSymbol.Photo)
                    Text("Evidencias vinculadas", style = MaterialTheme.typography.titleMedium)
                    Text("${state.evidence.size}", color = FieldMuted)
                }
                if (state.evidence.isEmpty()) Text("La API conserva enlaces y metadatos; la carga de archivos todavía no está disponible.", style = MaterialTheme.typography.bodySmall, color = FieldMuted)
                state.evidence.forEach { EvidenceItem(it) }
                OutlinedButton(onClick = onEvidence, enabled = !state.busy, modifier = Modifier.fillMaxWidth()) { Text("Vincular evidencia por URL") }
            }
        }
        state.error?.let { InfoNote(it, warning = true) }
        state.localPersistenceWarning?.let { InfoNote(it, warning = true) }
    }
}

@Composable
private fun EvidenceItem(item: EvidenceRecord) {
    Column(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(item.type.label(), fontWeight = FontWeight.SemiBold)
        Text(item.description ?: item.url, style = MaterialTheme.typography.bodySmall, color = FieldMuted)
    }
}

private fun ExecutionResult.label() = when (this) {
    ExecutionResult.COMPLETED -> "Completada"
    ExecutionResult.PARTIAL -> "Parcial"
    ExecutionResult.BLOCKED -> "Impedida"
}

private fun ExecutionResult.description() = when (this) {
    ExecutionResult.COMPLETED -> "La actividad asignada se realizó."
    ExecutionResult.PARTIAL -> "Se realizó parte del trabajo; explica lo pendiente."
    ExecutionResult.BLOCKED -> "No fue posible ejecutar la actividad."
}

private fun com.opervia.example.serviceCompliance.features.operations.domain.model.EvidenceType.label() = when (this) {
    com.opervia.example.serviceCompliance.features.operations.domain.model.EvidenceType.PHOTO -> "Fotografía"
    com.opervia.example.serviceCompliance.features.operations.domain.model.EvidenceType.QR_CODE -> "Código QR"
    com.opervia.example.serviceCompliance.features.operations.domain.model.EvidenceType.LOCATION -> "Ubicación"
    com.opervia.example.serviceCompliance.features.operations.domain.model.EvidenceType.SIGNATURE -> "Firma"
}
