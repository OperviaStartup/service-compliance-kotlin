package com.upc.example.serviceComplianceKotlin.fieldoperations.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import com.upc.example.serviceComplianceKotlin.fieldoperations.components.*
import com.upc.example.serviceComplianceKotlin.fieldoperations.theme.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.upc.example.serviceComplianceKotlin.fieldoperations.viewmodel.FieldState
import java.util.UUID

private val reasons = listOf("AREA_CLOSED", "MISSING_SUPPLIES", "SAFETY_RISK", "OTHER")

@Composable
internal fun ExceptionScreen(state: FieldState, onReport: (String, String, String) -> Unit, onDone: () -> Unit) {
    var reason by rememberSaveable { mutableStateOf(reasons.first()) }
    var description by rememberSaveable { mutableStateOf("") }
    val operationId = rememberSaveable(reason, description) { UUID.randomUUID().toString() }
    var sentId by rememberSaveable { mutableStateOf<String?>(null) }
    val recorded = sentId != null && state.execution?.exceptions?.any { it.operationId == sentId } == true
    Page(footer = {
        if (recorded) PrimaryAction("Volver a la ejecución", onDone)
        else PrimaryAction("Registrar impedimento",
            {
                sentId = operationId
                onReport(reason, description, operationId)
            },
            enabled = description.isNotBlank() && state.execution?.canEdit == true && !state.busy && !state.needsRefresh)
    }) {
        Text(if (recorded) "Impedimento registrado" else "Reportar impedimento", style = MaterialTheme.typography.headlineMedium)
        if (recorded) {
            Section("Reporte guardado", icon = FieldSymbol.Check) {
                StatusPill("Registrado", positive = true)
                Text(state.detail?.title.orEmpty(), fontWeight = FontWeight.Bold)
                Text("El avance se conserva. La obligación permanece incompleta hasta que cumplas sus requisitos y envíes la ejecución.", color = FieldMuted)
            }
        } else {
            FieldCard {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    IconTile(FieldSymbol.Document)
                    Column {
                        Text("ACTIVIDAD EN CURSO", style = MaterialTheme.typography.labelSmall, color = FieldMuted)
                        Text(state.detail?.title.orEmpty(), fontWeight = FontWeight.SemiBold)
                    }
                }
            }
            Text("Selecciona el motivo principal", style = MaterialTheme.typography.titleMedium)
            reasons.forEach { value ->
                val selected = reason == value
                Surface(color = if (selected) Color(0xFFDDF5F0) else Color.White,
                    shape = RoundedCornerShape(12.dp), shadowElevation = 1.dp,
                    border = BorderStroke(1.dp, if (selected) FieldTeal.copy(alpha = .25f) else Color.White)) {
                    Row(Modifier.fillMaxWidth().selectable(selected = selected, role = Role.RadioButton, onClick = { reason = value }).padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        IconTile(if (value == "SAFETY_RISK") FieldSymbol.Warning else FieldSymbol.Document,
                            tint = if (selected) FieldTeal else FieldBlue)
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                            Text(reasonLabel(value), fontWeight = FontWeight.Bold)
                            Text(when (value) {
                                "AREA_CLOSED" -> "Sin acceso al área de trabajo."
                                "MISSING_SUPPLIES" -> "Insumos o materiales no disponibles."
                                "SAFETY_RISK" -> "Condición que impide trabajar con seguridad."
                                else -> "Describe el motivo que impide continuar."
                            }, style = MaterialTheme.typography.bodySmall, color = FieldMuted)
                        }
                        RadioButton(selected = selected, onClick = null, colors = RadioButtonDefaults.colors(selectedColor = FieldTeal))
                    }
                }
            }
            OutlinedTextField(value = description, onValueChange = { if (it.length <= 2000) description = it },
                label = { Text("Describe lo ocurrido") }, supportingText = { Text("${description.length}/2000 · Obligatorio") },
                modifier = Modifier.fillMaxWidth(), minLines = 4, enabled = !state.busy, shape = RoundedCornerShape(12.dp),
                colors = OutlinedTextFieldDefaults.colors(unfocusedContainerColor = Color.White, focusedContainerColor = Color.White))
            InfoNote("El reporte conserva tu avance. Los pasos y las fotografías pendientes siguen siendo necesarios para finalizar.", warning = true)
        }
    }
}
