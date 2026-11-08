package com.opervia.example.serviceCompliance.features.operations.presentation.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.opervia.example.serviceCompliance.features.operations.presentation.components.FieldCard
import com.opervia.example.serviceCompliance.features.operations.presentation.components.FieldIcon
import com.opervia.example.serviceCompliance.features.operations.presentation.components.FieldSymbol
import com.opervia.example.serviceCompliance.features.operations.presentation.components.InfoNote
import com.opervia.example.serviceCompliance.features.operations.presentation.components.Page
import com.opervia.example.serviceCompliance.features.operations.presentation.components.PrimaryAction
import com.opervia.example.serviceCompliance.features.operations.presentation.components.StatusPill
import com.opervia.example.serviceCompliance.features.operations.domain.model.AssignedObligation
import com.opervia.example.serviceCompliance.features.operations.presentation.theme.FieldMuted
import com.opervia.example.serviceCompliance.features.operations.presentation.viewmodel.FieldState

@Composable
internal fun DetailScreen(
    state: FieldState,
    onStart: () -> Unit,
    onContinue: () -> Unit
) {
    val obligation = state.obligation
    Page(footer = {
        if (obligation != null) PrimaryAction(
            when {
                state.execution != null -> "Ver ejecución registrada"
                obligation.status in setOf("SUBMITTED", "COMPLETED") -> "Actividad registrada"
                else -> "Iniciar actividad"
            },
            onClick = if (state.execution == null) onStart else onContinue,
            enabled = !state.busy && !state.sessionExpired &&
                (state.execution != null || obligation.status !in setOf("SUBMITTED", "COMPLETED"))
        )
    }) {
        Text("Detalle de tarea", style = MaterialTheme.typography.headlineMedium)
        Text("Revisa la actividad antes de registrar su resultado.", style = MaterialTheme.typography.bodySmall, color = FieldMuted)
        if (obligation == null) {
            InfoNote(if (state.busy) "Consultando obligaciones…" else "No se pudo cargar esta obligación. Actualiza para volver a consultar.", warning = state.error != null)
            return@Page
        }
        ObligationSummary(obligation)
        FieldCard {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                FieldIcon(FieldSymbol.Document)
                Text("Instrucciones de la actividad", style = MaterialTheme.typography.titleMedium)
            }
            Text(obligation.description, style = MaterialTheme.typography.bodyMedium, color = FieldMuted)
        }
        InfoNote("El registro de ejecución indica lo realizado. La evaluación de cumplimiento corresponde a supervisión.")
        if (state.execution != null) InfoNote("Ya registraste un resultado para esta actividad en esta sesión.")
    }
}

@Composable
internal fun ObligationSummary(obligation: AssignedObligation) {
    FieldCard {
        StatusPill(statusLabel(obligation.status), positive = obligation.status == "IN_PROGRESS" || obligation.status == "SUBMITTED", warning = obligation.status == "OVERDUE")
        Text(obligation.title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FieldIcon(FieldSymbol.Building)
            Text(obligation.siteName, style = MaterialTheme.typography.bodyMedium, color = FieldMuted)
        }
        HorizontalDivider()
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FieldIcon(FieldSymbol.Clock)
            Column {
                Text("Fecha límite", style = MaterialTheme.typography.labelSmall, color = FieldMuted)
                Text(dateLabel(obligation.dueAt), style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}
