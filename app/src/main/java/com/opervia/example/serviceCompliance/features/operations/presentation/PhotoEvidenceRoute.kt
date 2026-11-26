package com.opervia.example.serviceCompliance.features.operations.presentation

import android.Manifest
import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.opervia.example.serviceCompliance.features.operations.data.*
import com.opervia.example.serviceCompliance.features.operations.domain.model.PhotoKind
import com.opervia.example.serviceCompliance.features.operations.domain.repository.OperationsRepository
import com.opervia.example.serviceCompliance.features.operations.presentation.screens.PhotoEvidenceScreen
import com.opervia.example.serviceCompliance.features.operations.presentation.viewmodel.PhotoViewModel
import java.io.File

@Composable
internal fun PhotoEvidenceRoute(
    repository: OperationsRepository,
    localStore: LocalFieldOperationsStore,
    userId: Long,
    obligationId: Long,
    kind: PhotoKind,
    requirementId: Long,
    evidenceId: Long?,
    onDone: () -> Unit
) {
    val context = LocalContext.current
    val files = remember(context.applicationContext) { LocalPhotoFiles(File(context.filesDir, "field-photos")) }
    val factory = remember(repository, localStore, userId, obligationId, kind, requirementId, evidenceId) {
        object : ViewModelProvider.Factory {
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                require(modelClass == PhotoViewModel::class.java)
                @Suppress("UNCHECKED_CAST")
                return PhotoViewModel(repository, localStore, files, AndroidPhotoLocation(context),
                    userId, obligationId, kind, requirementId, evidenceId) as T
            }
        }
    }
    val model: PhotoViewModel = viewModel(key = "photo-$userId-$obligationId-$kind-$requirementId-$evidenceId", factory = factory)
    val state by model.state.collectAsStateWithLifecycle()
    val authority = "${context.packageName}.fieldphotos"
    val contract = remember {
        object : ActivityResultContracts.TakePicture() {
            override fun createIntent(context: Context, input: Uri): Intent = super.createIntent(context, input).apply {
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
                clipData = ClipData.newRawUri("Foto de actividad", input)
            }
        }
    }
    val camera = rememberLauncherForActivityResult(contract) { success ->
        model.state.value.draft?.let { draft ->
            runCatching {
                val uri = FileProvider.getUriForFile(context, authority, files.file(userId, draft))
                context.revokeUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
            }
        }
        model.captureReturned(success)
    }
    val permissions = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { result ->
        if (result.values.any { it }) model.attachLocation()
        else model.showError("Este requisito exige ubicación. Revisa el permiso de ubicación en los ajustes de la app.")
    }
    val takePhoto = {
        model.prepareCapture { file ->
            try { camera.launch(FileProvider.getUriForFile(context, authority, file)) }
            catch (_: Exception) { model.captureReturned(false, "No se pudo abrir la cámara del dispositivo.") }
        }
    }
    val cameraPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) takePhoto() else model.showError("Concede el permiso de cámara para tomar una fotografía.")
    }
    PhotoEvidenceScreen(state,
        onCapture = {
            if (ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) takePhoto()
            else cameraPermission.launch(Manifest.permission.CAMERA)
        },
        onLocation = {
            val granted = listOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION)
                .any { ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED }
            if (granted) model.attachLocation()
            else permissions.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION))
        },
        onUpload = model::upload, onDiscard = model::discard, onRefresh = model::refresh, onDone = onDone)
}
