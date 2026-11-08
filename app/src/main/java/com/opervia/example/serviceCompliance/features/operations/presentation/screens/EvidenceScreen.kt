package com.opervia.example.serviceCompliance.features.operations.presentation.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.opervia.example.serviceCompliance.features.operations.presentation.components.FieldCard
import com.opervia.example.serviceCompliance.features.operations.presentation.components.InfoNote
import com.opervia.example.serviceCompliance.features.operations.presentation.components.Page
import com.opervia.example.serviceCompliance.features.operations.presentation.components.PrimaryAction
import com.opervia.example.serviceCompliance.features.operations.domain.model.EvidenceType
import com.opervia.example.serviceCompliance.features.operations.presentation.viewmodel.FieldState
import java.net.URI

@Composable
internal fun EvidenceScreen(
    state: FieldState,
    onAdd: (EvidenceType, String, String?) -> Unit,
    onDone: () -> Unit
) {
    var selectedType by rememberSaveable { mutableStateOf(EvidenceType.PHOTO) }
    var url by rememberSaveable { mutableStateOf("") }
    var description by rememberSaveable { mutableStateOf("") }
    var expanded by remember { mutableStateOf(false) }
    val lastAdded = state.evidence.lastOrNull()?.url == url && url.isNotBlank()

    Page(footer = {
        if (lastAdded) PrimaryAction("Volver a la ejecución", onDone)
        else PrimaryAction("Vincular evidencia", {
            onAdd(selectedType, url, description.takeIf(String::isNotBlank))
        }, enabled = validEvidenceUrl(url) && !state.busy && !state.sessionExpired)
    }) {
        Text("Vincular evidencia", style = MaterialTheme.typography.headlineMedium)
        state.obligation?.let { ObligationSummary(it) }
        FieldCard {
            Text("Tipo de evidencia", style = MaterialTheme.typography.titleMedium)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton(onClick = { expanded = true }) { Text(selectedType.label()) }
                DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                    EvidenceType.entries.forEach { type -> DropdownMenuItem(
                        text = { Text(type.label()) },
                        onClick = { selectedType = type; expanded = false }
                    ) }
                }
            }
            OutlinedTextField(value = url, onValueChange = { if (it.length <= 1000) url = it },
                label = { Text("URL accesible de la evidencia") }, modifier = Modifier.fillMaxWidth(),
                singleLine = true, shape = RoundedCornerShape(10.dp))
            OutlinedTextField(value = description, onValueChange = { if (it.length <= 1000) description = it },
                label = { Text("Descripción (opcional)") }, modifier = Modifier.fillMaxWidth(), minLines = 2,
                supportingText = { Text("${description.length}/1000") })
        }
        InfoNote("La API guarda la URL y sus metadatos. Este backend todavía no permite cargar una fotografía desde la cámara.", warning = true)
        if (lastAdded) InfoNote("La evidencia quedó vinculada a la ejecución.")
        state.error?.let { InfoNote(it, warning = true) }
    }
}

private fun validEvidenceUrl(value: String): Boolean = runCatching {
    val uri = URI(value.trim())
    uri.scheme in setOf("https", "http") && uri.host != null
}.getOrDefault(false)

private fun EvidenceType.label() = when (this) {
    EvidenceType.PHOTO -> "Fotografía"
    EvidenceType.QR_CODE -> "Código QR"
    EvidenceType.LOCATION -> "Ubicación"
    EvidenceType.SIGNATURE -> "Firma"
}
