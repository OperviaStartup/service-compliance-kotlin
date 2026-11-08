package com.opervia.example.serviceCompliance.fieldoperations.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.background
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import com.opervia.example.serviceCompliance.fieldoperations.components.*
import com.opervia.example.serviceCompliance.fieldoperations.theme.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.opervia.example.serviceCompliance.fieldoperations.viewmodel.FieldState
import android.graphics.BitmapFactory
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.FileProvider
import java.io.File
import com.opervia.example.serviceCompliance.fieldoperations.model.PhotoRequirement

@Composable
internal fun PhotoScreen(requirement: PhotoRequirement, state: FieldState, onUpload: (File) -> Unit,
    onLoad: (Long) -> Unit, onDone: () -> Unit) {
    val context = LocalContext.current
    var path by rememberSaveable(requirement.id) { mutableStateOf<String?>(null) }
    var captured by rememberSaveable(requirement.id) { mutableStateOf(false) }
    var error by rememberSaveable { mutableStateOf<String?>(null) }
    val camera = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { success ->
        captured = success
        if (!success) {
            val previousPath = path
            if (previousPath != null) {
                File(previousPath).delete()
            }
            path = null
        }
    }

    fun capture() {
        try {
            val previousPath = path
            if (previousPath != null) {
                File(previousPath).delete()
            }

            val directory = File(context.cacheDir, "field-photos")
            directory.mkdirs()
            val newFile = File.createTempFile("capture-", ".jpg", directory)
            path = newFile.absolutePath
            captured = false
            error = null

            val authority = "${context.packageName}.fieldphotos"
            val uri = FileProvider.getUriForFile(context, authority, newFile)
            camera.launch(uri)
        } catch (_: Exception) {
            error = "No se pudo abrir la cámara. Comprueba que el dispositivo tenga una aplicación de cámara."
        }
    }

    val capturedPath = path
    val file = if (capturedPath == null) null else File(capturedPath)
    LaunchedEffect(requirement.evidenceId) {
        val evidenceId = requirement.evidenceId
        if (evidenceId != null) {
            onLoad(evidenceId)
        }
    }

    val preview = state.preview
    val downloaded = if (preview != null && preview.id == requirement.evidenceId) preview.bytes else null
    val storedBitmap = remember(downloaded) {
        if (downloaded == null) {
            null
        } else {
            val bounds = BitmapFactory.Options()
            bounds.inJustDecodeBounds = true
            BitmapFactory.decodeByteArray(downloaded, 0, downloaded.size, bounds)

            val options = BitmapFactory.Options()
            options.inSampleSize = previewSampleSize(bounds.outWidth, bounds.outHeight)
            BitmapFactory.decodeByteArray(downloaded, 0, downloaded.size, options)
        }
    }

    val bitmap = remember(path, captured) {
        if (!captured || file == null) {
            null
        } else {
            val bounds = BitmapFactory.Options()
            bounds.inJustDecodeBounds = true
            BitmapFactory.decodeFile(file.absolutePath, bounds)

            val options = BitmapFactory.Options()
            options.inSampleSize = previewSampleSize(bounds.outWidth, bounds.outHeight)
            BitmapFactory.decodeFile(file.absolutePath, options)
        }
    }
    Page {
        Text("Revisión de fotografía", style = MaterialTheme.typography.headlineMedium)
        Section(requirement.title, icon = FieldSymbol.Photo) {
            StatusPill(if (requirement.evidenceId != null) "Fotografía registrada" else "Evidencia requerida", positive = requirement.evidenceId != null)
            Text("Una fotografía por requisito", style = MaterialTheme.typography.bodySmall, color = FieldMuted)
        }
        requirement.description?.let { Text(it, style = MaterialTheme.typography.bodyMedium, color = FieldMuted) }
        if (requirement.evidenceId != null) {
            LaunchedEffect(requirement.evidenceId) { file?.delete() }
            if (storedBitmap != null) {
                Image(
                    bitmap = storedBitmap.asImageBitmap(),
                    contentDescription = "Fotografía registrada",
                    modifier = Modifier.fillMaxWidth().height(360.dp)
                        .clip(RoundedCornerShape(16.dp)).background(FieldNavy),
                    contentScale = ContentScale.Fit
                )
            }
            if (storedBitmap == null) OutlinedButton(onClick = { requirement.evidenceId.let(onLoad) },
                enabled = !state.busy && !state.sessionExpired) { Text("Consultar fotografía") }
            InfoNote("El servidor confirmó la evidencia. No se puede reemplazar.")
            PrimaryAction("Continuar", onDone)
        } else {
            InfoNote("Revisa la fotografía antes de enviarla. Después de registrarla no podrás reemplazarla.")
            if (!captured) FieldCard {
                Column(Modifier.fillMaxWidth().padding(vertical = 36.dp), horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    IconTile(FieldSymbol.Photo)
                    Text("Captura la evidencia", style = MaterialTheme.typography.titleMedium)
                    Text("Busca buena iluminación y muestra el área indicada.", style = MaterialTheme.typography.bodySmall, color = FieldMuted)
                }
            }
            if (bitmap != null) {
                Image(
                    bitmap = bitmap.asImageBitmap(),
                    contentDescription = "Vista previa de la fotografía",
                    modifier = Modifier.fillMaxWidth().height(360.dp)
                        .clip(RoundedCornerShape(16.dp)).background(FieldNavy),
                    contentScale = ContentScale.Fit
                )
            }
            error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            if (captured && bitmap == null) Text("No se pudo leer la fotografía. Vuelve a tomarla.")
            if (file != null && file.length() > 5L * 1024 * 1024) Text("La fotografía supera 5 MiB. Vuelve a tomarla con menor resolución.")
            val editable = state.execution?.canEdit == true && !state.busy && !state.needsRefresh
            OutlinedButton(onClick = ::capture, enabled = editable, modifier = Modifier.fillMaxWidth().heightIn(min = 50.dp)) { Text(if (captured) "Volver a tomar" else "Abrir cámara") }
            PrimaryAction(
                text = "Usar y enviar fotografía",
                onClick = {
                    if (file != null) {
                        onUpload(file)
                    }
                },
                enabled = editable && captured && bitmap != null &&
                    file != null && file.length() in 1..(5L * 1024 * 1024)
            )
        }
    }
}


private fun previewSampleSize(width: Int, height: Int): Int {
    var sampleSize = 1
    while (width / sampleSize > 1200 || height / sampleSize > 1200) {
        sampleSize *= 2
    }
    return sampleSize
}
