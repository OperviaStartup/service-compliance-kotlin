package com.opervia.example.serviceCompliance.features.operations.presentation

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.journeyapps.barcodescanner.ScanContract
import com.journeyapps.barcodescanner.ScanOptions
import com.opervia.example.serviceCompliance.features.operations.data.AndroidQrImageReader
import com.opervia.example.serviceCompliance.features.operations.data.AndroidPhotoLocation
import com.opervia.example.serviceCompliance.features.operations.data.LocalFieldOperationsStore
import com.opervia.example.serviceCompliance.features.operations.domain.repository.OperationsRepository
import com.opervia.example.serviceCompliance.features.operations.presentation.screens.StructuredEvidenceScreen
import com.opervia.example.serviceCompliance.features.operations.presentation.viewmodel.StructuredEvidenceViewModel

@Composable
internal fun StructuredEvidenceRoute(repository: OperationsRepository, store: LocalFieldOperationsStore,
    userId: Long, obligationId: Long, requirementId: Long, onDone: () -> Unit) {
    val context = LocalContext.current
    val factory = remember(repository, store, userId, obligationId, requirementId) {
        object : ViewModelProvider.Factory {
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                require(modelClass == StructuredEvidenceViewModel::class.java)
                @Suppress("UNCHECKED_CAST")
                return StructuredEvidenceViewModel(repository, store, AndroidPhotoLocation(context),
                    userId, obligationId, requirementId, qrImages = AndroidQrImageReader(context)) as T
            }
        }
    }
    val model: StructuredEvidenceViewModel = viewModel(key = "structured-$userId-$obligationId-$requirementId", factory = factory)
    val state by model.state.collectAsStateWithLifecycle()
    var scanning by rememberSaveable { mutableStateOf(false) }
    val scanner = rememberLauncherForActivityResult(ScanContract()) { result ->
        scanning = false
        result.contents?.let { model.captureQr(it) }
    }
    val imagePicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        uri?.let { model.captureQrImage(it.toString()) }
    }
    val permissions = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { result ->
        if (result.values.any { it }) model.captureLocation()
        else model.showError("Concede el permiso de ubicación para este requisito.")
    }
    StructuredEvidenceScreen(state, onScan = {
        if (!scanning) {
            scanning = true
            try {
                scanner.launch(ScanOptions().setDesiredBarcodeFormats(ScanOptions.QR_CODE)
                    .setPrompt("Escanea el código QR de la actividad").setBeepEnabled(false).setOrientationLocked(false))
            } catch (_: Exception) {
                scanning = false
                model.showError("No se pudo abrir el lector. Revisa la cámara y sus permisos.")
            }
        }
    }, onPickImage = {
        if (!scanning && state.canCapture) {
            try { imagePicker.launch("image/*") }
            catch (_: Exception) { model.showError("No se pudo abrir el selector de imágenes.") }
        }
    }, onLocation = {
        val granted = listOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION)
            .any { ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED }
        if (granted) model.captureLocation()
        else permissions.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION))
    }, onUpload = model::upload, onDiscard = model::discard, onRefresh = model::refresh, onDone = onDone)
}
