package com.opervia.example.serviceCompliance.features.operations.presentation

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.opervia.example.serviceCompliance.features.operations.data.*
import com.opervia.example.serviceCompliance.features.operations.domain.model.EvidenceType
import com.opervia.example.serviceCompliance.features.operations.domain.model.PhotoKind
import com.opervia.example.serviceCompliance.features.operations.domain.repository.OperationsRepository
import com.opervia.example.serviceCompliance.features.operations.presentation.screens.SignatureEvidenceScreen
import com.opervia.example.serviceCompliance.features.operations.presentation.viewmodel.PhotoViewModel
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
internal fun SignatureEvidenceRoute(repository: OperationsRepository, store: LocalFieldOperationsStore,
    userId: Long, obligationId: Long, requirementId: Long, evidenceId: Long?, onDone: () -> Unit) {
    val context = LocalContext.current
    val files = remember(context.applicationContext) { LocalPhotoFiles(File(context.filesDir, "field-photos")) }
    val factory = remember(repository, store, userId, obligationId, requirementId, evidenceId) {
        object : ViewModelProvider.Factory {
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                require(modelClass == PhotoViewModel::class.java)
                @Suppress("UNCHECKED_CAST")
                return PhotoViewModel(repository, store, files, AndroidPhotoLocation(context), userId,
                    obligationId, PhotoKind.EVIDENCE_REQUIREMENT, requirementId, evidenceId,
                    evidenceType = EvidenceType.SIGNATURE) as T
            }
        }
    }
    val model: PhotoViewModel = viewModel(key = "signature-$userId-$obligationId-$requirementId-$evidenceId", factory = factory)
    val state by model.state.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    var preparing by remember { mutableStateOf(false) }
    val permissions = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { result ->
        if (result.values.any { it }) model.attachLocation()
        else model.showError("Concede el permiso de ubicación para este requisito.")
    }
    SignatureEvidenceScreen(state, onSave = { strokes ->
        if (!preparing) {
            preparing = true
            scope.launch {
                try {
                    val bytes = withContext(Dispatchers.Default) { signaturePng(strokes) }
                    model.saveSignature(bytes)
                } catch (cancelled: kotlinx.coroutines.CancellationException) { throw cancelled }
                catch (_: Exception) { model.showError("No se pudo preparar la firma. Limpia el panel y vuelve a dibujarla.") }
                finally { preparing = false }
            }
        }
    }, onLocation = {
        val granted = listOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION)
            .any { ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED }
        if (granted) model.attachLocation()
        else permissions.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION))
    }, onUpload = model::upload, onDiscard = model::discard, onRefresh = model::refresh, onDone = onDone)
}
