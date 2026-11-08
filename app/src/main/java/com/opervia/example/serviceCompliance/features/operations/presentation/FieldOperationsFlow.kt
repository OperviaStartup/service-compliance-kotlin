package com.opervia.example.serviceCompliance.features.operations.presentation

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.compose.rememberNavController
import kotlinx.coroutines.delay
import com.opervia.example.serviceCompliance.features.operations.presentation.navigation.FieldNavigation
import com.opervia.example.serviceCompliance.features.operations.presentation.theme.FieldTheme
import com.opervia.example.serviceCompliance.features.operations.presentation.viewmodel.FieldViewModel
import com.opervia.example.serviceCompliance.features.operations.domain.model.ExecutionRecord
import com.opervia.example.serviceCompliance.features.operations.presentation.components.*

@Composable
fun FieldOperationsFlow(model: FieldViewModel, onExit: () -> Unit, onExecutionRegistered: (ExecutionRecord) -> Unit) {
    val state by model.state.collectAsStateWithLifecycle()
    LaunchedEffect(state.execution?.id) { state.execution?.let(onExecutionRegistered) }
    val nav = rememberNavController()
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    LaunchedEffect(lifecycle) {
        lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            while (true) {
                delay(30_000)
                if (!model.state.value.busy) {
                    model.refresh()
                }
            }
        }
    }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { model.refresh() }
    FieldTheme {
        FieldBackdrop {
            Scaffold(
                modifier = Modifier.widthIn(max = 600.dp).fillMaxSize().align(Alignment.Center),
                containerColor = Color.Transparent
            ) { padding ->
                Column(Modifier.fillMaxSize().padding(padding).padding(horizontal = 16.dp)) {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        IconButton(onClick = { if (!nav.popBackStack()) onExit() }, modifier = Modifier.semantics { contentDescription = "Volver" }) {
                            FieldIcon(FieldSymbol.Back, tint = MaterialTheme.colorScheme.onSurface)
                        }
                        Column(Modifier.weight(1f)) {
                            Text("SERVICE COMPLIANCE", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                            Text("Mi trabajo", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        }
                        IconButton(onClick = model::refresh, enabled = !state.busy, modifier = Modifier.semantics { contentDescription = "Actualizar avance" }) {
                            FieldIcon(FieldSymbol.Refresh)
                        }
                    }
                    if (state.busy) LinearProgressIndicator(Modifier.fillMaxWidth())
                    state.error?.let { error ->
                        Card(colors = CardDefaults.cardColors(containerColor = Color(0xFFFFEDEA))) {
                            Column(Modifier.padding(14.dp)) {
                                Text(error, color = MaterialTheme.colorScheme.error)
                                if (state.sessionExpired) TextButton(onClick = onExit) { Text("Regresar") }
                            }
                        }
                    }
                    FieldNavigation(model, state, nav, onExit, Modifier.weight(1f))
                }
            }
        }
    }
}
