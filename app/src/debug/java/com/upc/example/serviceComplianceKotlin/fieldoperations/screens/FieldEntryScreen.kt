package com.upc.example.serviceComplianceKotlin.fieldoperations.screens

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.upc.example.serviceComplianceKotlin.fieldoperations.data.FieldRepository
import com.upc.example.serviceComplianceKotlin.fieldoperations.viewmodel.FieldConnection
import java.net.URI

/** Same local test form, hosted by MainActivity through AppNavigation. */
@Composable
internal fun FieldEntryScreen(onOpen: (FieldConnection) -> Unit) {
    val context = LocalContext.current
    var url by remember { mutableStateOf("") }
    var token by remember { mutableStateOf("") }
    var id by remember { mutableStateOf("") }
    var error by remember { mutableStateOf(false) }
    var permissionDenied by remember { mutableStateOf(false) }
    var pendingConnection by remember { mutableStateOf<FieldConnection?>(null) }
    val networkPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        val target = pendingConnection
        pendingConnection = null
        permissionDenied = !granted
        if (granted && target != null) {
            error = runCatching { onOpen(target); token = "" }.isFailure
        }
    }
    MaterialTheme {
        Surface(Modifier.fillMaxSize()) {
            Column(Modifier.safeDrawingPadding().verticalScroll(rememberScrollState()).padding(24.dp),
                verticalArrangement = Arrangement.spacedBy(20.dp)) {
                Text("Flujo del operario", style = MaterialTheme.typography.headlineMedium)
                Text("Entrada de pruebas local. Introduce la URL del backend, una sesión existente y el ID de una obligación asignada. No se guardan las credenciales.")
                OutlinedTextField(url, { url = it }, label = { Text("URL del backend (sin /api/v1)") },
                    placeholder = { Text("http://10.0.2.2:8080") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                OutlinedTextField(id, { id = it }, label = { Text("ID de obligación") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                OutlinedTextField(token, { token = it }, label = { Text("Token de sesión (sin Bearer)") },
                    visualTransformation = PasswordVisualTransformation(), modifier = Modifier.fillMaxWidth(), singleLine = true)
                if (error) Text("Verifica la URL, un ID positivo y el token de sesión.", color = MaterialTheme.colorScheme.error)
                if (permissionDenied) Text("Se necesita permiso de red local para conectar con el backend del equipo. Pulsa Abrir obligación y permite el acceso cuando Android lo solicite.", color = MaterialTheme.colorScheme.error)
                Button(onClick = {
                    permissionDenied = false
                    error = runCatching {
                        val selectedId = id.trim().toLong().also { require(it > 0) }
                        val cleanUrl = url.trim()
                        val cleanToken = token.trim()
                        FieldRepository(cleanUrl, cleanToken)
                        val target = FieldConnection(cleanUrl, cleanToken, selectedId)
                        val isLocal = URI(cleanUrl).host in setOf("10.0.2.2", "127.0.0.1", "localhost")
                        if (isLocal && Build.VERSION.SDK_INT >= 37 &&
                            ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_LOCAL_NETWORK) != PackageManager.PERMISSION_GRANTED) {
                            pendingConnection = target
                            networkPermission.launch(Manifest.permission.ACCESS_LOCAL_NETWORK)
                        } else {
                            onOpen(target)
                            token = ""
                        }
                    }.isFailure
                }, modifier = Modifier.fillMaxWidth()) { Text("Abrir obligación") }
            }
        }
    }
}
