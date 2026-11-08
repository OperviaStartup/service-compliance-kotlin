package com.opervia.example.serviceCompliance.features.operations.presentation.screens

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
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
import com.opervia.example.serviceCompliance.features.operations.domain.model.ExecutionResult
import com.opervia.example.serviceCompliance.features.operations.presentation.theme.FieldBlue
import com.opervia.example.serviceCompliance.features.operations.presentation.theme.FieldMuted
import com.opervia.example.serviceCompliance.features.operations.presentation.viewmodel.FieldState

private val reasons = listOf("ÁREA CERRADA", "FALTAN INSUMOS", "RIESGO DE SEGURIDAD", "OTRO MOTIVO")

@Composable
internal fun ExceptionScreen(state: FieldState, onReport: (ExecutionResult, String?) -> Unit, onDone: () -> Unit) {
    var reason by rememberSaveable { mutableStateOf(reasons.first()) }
    var description by rememberSaveable { mutableStateOf("") }
    val recorded = state.execution?.result == ExecutionResult.BLOCKED
    Page(footer = {
        if (recorded) PrimaryAction("Volver a mis tareas", onDone)
        else PrimaryAction("Enviar resultado impedido", {
            onReport(ExecutionResult.BLOCKED, "$reason: ${description.trim()}")
        }, enabled = description.isNotBlank() && !state.busy && !state.sessionExpired && !state.submissionUncertain)
    }) {
        Text(if (recorded) "Resultado impedido enviado" else "Reportar impedimento", style = MaterialTheme.typography.headlineMedium)
        state.obligation?.let { ObligationSummary(it) }
        if (recorded) {
            FieldCard {
                FieldIcon(FieldSymbol.Check, tint = Color(0xFF098778))
                Text(state.execution?.notes.orEmpty(), style = MaterialTheme.typography.bodyMedium)
            }
            InfoNote("El registro queda pendiente de revisión. No decide automáticamente el cumplimiento.")
        } else {
            Text("Selecciona el motivo principal", style = MaterialTheme.typography.titleMedium)
            reasons.forEach { value ->
                val selected = reason == value
                Surface(color = if (selected) Color(0xFFEAF4FF) else Color.White, shape = RoundedCornerShape(12.dp)) {
                    Row(Modifier.fillMaxWidth().selectable(selected, role = Role.RadioButton, onClick = { reason = value }).padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically) {
                        FieldIcon(if (value == "RIESGO DE SEGURIDAD") FieldSymbol.Warning else FieldSymbol.Document)
                        Column(Modifier.weight(1f).padding(horizontal = 10.dp)) {
                            Text(value.lowercase().replaceFirstChar(Char::uppercase), fontWeight = FontWeight.SemiBold)
                            Text(when (value) {
                                "ÁREA CERRADA" -> "No fue posible acceder al área."
                                "FALTAN INSUMOS" -> "Faltan materiales para continuar."
                                "RIESGO DE SEGURIDAD" -> "Una condición impide trabajar de forma segura."
                                else -> "Describe el motivo que impidió realizar la actividad."
                            }, style = MaterialTheme.typography.bodySmall, color = FieldMuted)
                        }
                        RadioButton(selected, onClick = null, colors = RadioButtonDefaults.colors(selectedColor = FieldBlue))
                    }
                }
            }
            OutlinedTextField(value = description, onValueChange = { if (it.length <= 1800) description = it },
                label = { Text("Añadir detalle") }, modifier = Modifier.fillMaxWidth(), minLines = 3,
                supportingText = { Text("${description.length}/1800 · Obligatorio") })
            InfoNote("La API no tiene un recurso Exception independiente. Se enviará un resultado BLOCKED con este motivo en las notas.", warning = true)
        }
        state.error?.let { InfoNote(it, warning = true) }
        state.localPersistenceWarning?.let { InfoNote(it, warning = true) }
    }
}
