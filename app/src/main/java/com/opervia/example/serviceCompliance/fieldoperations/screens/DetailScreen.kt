package com.opervia.example.serviceCompliance.fieldoperations.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.opervia.example.serviceCompliance.fieldoperations.components.*
import com.opervia.example.serviceCompliance.fieldoperations.viewmodel.FieldState
import com.opervia.example.serviceCompliance.fieldoperations.theme.*

@Composable
internal fun DetailScreen(state: FieldState, onStart: () -> Unit, onContinue: () -> Unit) {
    val detail = state.detail
    var opening by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(state.execution, opening) {
        if (opening && state.execution != null) {
            opening = false
            onContinue()
        }
    }
    Page(footer = {
        if (detail != null) {
            if (state.execution != null) {
                PrimaryAction(if (state.execution.state == "SUBMITTED") "Ver ejecución enviada" else "Continuar ejecución",
                    onContinue, enabled = !state.busy)
            } else {
                PrimaryAction("Iniciar ejecución", {
                    opening = true
                    onStart()
                },
                    enabled = !state.busy && !state.needsRefresh && !state.sessionExpired &&
                        detail.status == "ASSIGNED" && detail.steps.isNotEmpty())
            }
        }
    }) {
        Text("Detalle de obligación", style = MaterialTheme.typography.headlineMedium)
        Text("Revisa los detalles antes de iniciar la ejecución.", style = MaterialTheme.typography.bodySmall, color = FieldMuted)
        if (detail == null) {
            InfoNote(if (state.busy) "Consultando obligación…" else "No hay información disponible. Usa Actualizar para volver a consultar.")
            return@Page
        }
        val status = state.execution?.obligationStatus ?: detail.status
        FieldCard {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.Top) {
                IconTile(FieldSymbol.Building)
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    StatusPill(statusLabel(status), positive = status == "SUBMITTED" || status == "IN_PROGRESS", warning = status == "OVERDUE")
                    Text(detail.title, style = MaterialTheme.typography.titleLarge)
                    Text(detail.siteName, style = MaterialTheme.typography.bodySmall, color = FieldMuted)
                }
            }
            HorizontalDivider(color = FieldLine)
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                FieldIcon(FieldSymbol.Clock)
                Column {
                    detail.startsAt?.let { Text("Inicio: ${dateLabel(it)}", style = MaterialTheme.typography.bodySmall, color = FieldMuted) }
                    Text("Hasta ${dateLabel(detail.dueAt)}", style = MaterialTheme.typography.labelLarge)
                    Text("Hora de Lima", style = MaterialTheme.typography.bodySmall, color = FieldMuted)
                }
            }
        }
        detail.description?.takeIf { it.isNotBlank() }?.let { description ->
            Section("Instrucciones de la actividad", icon = FieldSymbol.Document) {
                Text(description, Modifier.fillMaxWidth().background(FieldPaleBlue.copy(alpha = .65f), RoundedCornerShape(8.dp)).padding(12.dp),
                    style = MaterialTheme.typography.bodyMedium, color = FieldMuted)
            }
        }
        Section("Pasos a realizar", trailing = "${detail.steps.size} pasos") {
            detail.steps.forEachIndexed { index, step ->
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    StepNumber(index + 1)
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(step.title, fontWeight = FontWeight.SemiBold)
                        step.instructions?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = FieldMuted) }
                        if (index < detail.steps.lastIndex) HorizontalDivider(Modifier.padding(top = 8.dp), color = FieldLine)
                    }
                }
            }
            if (detail.steps.isEmpty()) Text("Esta obligación aún no tiene pasos configurados.", color = FieldMuted)
        }
        Section("Evidencia requerida", icon = FieldSymbol.Photo, trailing = "${detail.photos.size} fotos") {
            detail.photos.forEachIndexed { index, photo ->
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                    FieldIcon(FieldSymbol.Photo)
                    Column(Modifier.weight(1f)) {
                        Text(photo.title, fontWeight = FontWeight.SemiBold)
                        photo.description?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = FieldMuted) }
                    }
                    StatusPill("Obligatoria")
                }
                if (index < detail.photos.lastIndex) HorizontalDivider(color = FieldLine)
            }
            if (detail.photos.isEmpty()) Text("Esta obligación no exige fotografías.", color = FieldTeal)
        }
        InfoNote(if (detail.photos.isEmpty()) "Completa todos los pasos y envía la ejecución para finalizar."
            else "Tu avance se conserva. Cada requisito admite una foto y no se puede reemplazar después de enviarla.")
    }
}
