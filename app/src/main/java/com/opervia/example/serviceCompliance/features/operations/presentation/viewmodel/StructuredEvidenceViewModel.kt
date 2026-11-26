package com.opervia.example.serviceCompliance.features.operations.presentation.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.opervia.example.serviceCompliance.features.operations.domain.model.*
import com.opervia.example.serviceCompliance.features.operations.domain.repository.*
import java.time.Instant
import java.util.UUID
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

data class StructuredEvidenceState(
    val flow: ExecutionFlow? = null,
    val requirement: ExecutionEvidenceRequirement? = null,
    val flowConfirmed: Boolean = false,
    val draft: StructuredEvidenceDraft? = null,
    val submissionPending: Boolean = false,
    val busy: Boolean = false,
    val sessionExpired: Boolean = false,
    val error: String? = null
) {
    val canCapture: Boolean get() = flowConfirmed && flow?.canEdit == true &&
        flow.state == ExecutionState.IN_PROGRESS && !busy && !sessionExpired && !submissionPending &&
        draft?.uploadPending != true && requirement?.let { it.evidence.size < it.maximumCount } == true
    val canUpload: Boolean get() = !busy && !sessionExpired && draft != null &&
        (draft.uploadPending || canCapture)
}

/** QR y ubicación comparten el contrato JSON; las capturas se revisan antes del envío. */
class StructuredEvidenceViewModel(
    private val repository: OperationsRepository,
    private val store: StructuredEvidenceStore,
    private val location: PhotoLocationProvider,
    private val userId: Long,
    private val obligationId: Long,
    private val requirementId: Long,
    private val now: () -> Instant = Instant::now,
    private val qrImages: QrImageReader? = null
) : ViewModel() {
    private val mutable = MutableStateFlow(StructuredEvidenceState())
    val state = mutable.asStateFlow()
    private val operations = Mutex()

    init { refresh() }

    fun refresh() = perform {
        mutable.value = mutable.value.copy(flowConfirmed = false)
        val requirement = loadRequirement()
        val flow = requireNotNull(mutable.value.flow)
        val draft = store.structuredDraft(userId, flow.id, requirementId)
        mutable.value = mutable.value.copy(draft = draft)
        if (draft?.uploadPending == true) {
            requirement.evidence.firstOrNull { matches(draft, it) }?.let { accept(draft, it) }
        }
    }

    // Los resultados externos pueden llegar mientras se recupera la actividad.
    fun captureQr(value: String) = perform(queue = true) {
        val requirement = prepareQrCapture()
        saveQrValue(requirement, value)
    }

    fun captureQrImage(uri: String) = perform(queue = true) {
        val requirement = prepareQrCapture()
        val reader = qrImages ?: throw invalid("No se pudo preparar el lector de imágenes.")
        val value = reader.read(uri)
        saveQrValue(requirement, value)
    }

    private suspend fun prepareQrCapture(): ExecutionEvidenceRequirement {
        val requirement = loadRequirement()
        ensureCaptureAllowed(requirement)
        if (requirement.type != EvidenceType.QR_CODE) throw invalid("El requisito no admite códigos QR.")
        return requirement
    }

    private suspend fun saveQrValue(requirement: ExecutionEvidenceRequirement, value: String) {
        val text = value.trim()
        if (text.isBlank() || text.length > 512) throw invalid("El código QR debe contener entre 1 y 512 caracteres.")
        requirement.constraints["expectedPrefix"]?.let { prefix ->
            if (!text.startsWith(prefix)) throw invalid("El código QR no corresponde al prefijo exigido para esta actividad.")
        }
        saveCapture(StructuredEvidenceDraft(requireNotNull(mutable.value.flow).id, requirementId, EvidenceType.QR_CODE,
            "evidence-${UUID.randomUUID()}", now().truncatedTo(java.time.temporal.ChronoUnit.MILLIS).toString(), textValue = text))
    }

    fun captureLocation() = perform {
        val requirement = loadRequirement()
        ensureCaptureAllowed(requirement)
        if (requirement.type != EvidenceType.LOCATION) throw invalid("El requisito no admite una ubicación independiente.")
        val point = location.current()
        if (!point.latitude.isFinite() || point.latitude !in -90.0..90.0 ||
            !point.longitude.isFinite() || point.longitude !in -180.0..180.0 ||
            !point.accuracyMeters.isFinite() || point.accuracyMeters <= 0) {
            throw invalid("El dispositivo no devolvió una ubicación válida.")
        }
        val maximum = requirement.constraints["maxAccuracyMeters"]?.toDoubleOrNull() ?: 1000.0
        if (point.accuracyMeters > maximum) {
            throw invalid("La precisión obtenida supera el máximo de $maximum m. Vuelve a obtener la ubicación.")
        }
        saveCapture(StructuredEvidenceDraft(requireNotNull(mutable.value.flow).id, requirementId, EvidenceType.LOCATION,
            "evidence-${UUID.randomUUID()}", (point.capturedAt?.let(Instant::parse) ?: now()).truncatedTo(java.time.temporal.ChronoUnit.MILLIS).toString(), location = point))
    }

    private suspend fun ensureCaptureAllowed(requirement: ExecutionEvidenceRequirement) {
        val flow = requireNotNull(mutable.value.flow)
        val existing = store.structuredDraft(userId, flow.id, requirementId)
        mutable.value = mutable.value.copy(draft = existing)
        if (!flow.canEdit || flow.state != ExecutionState.IN_PROGRESS || mutable.value.submissionPending ||
            existing?.uploadPending == true || requirement.evidence.size >= requirement.maximumCount) {
            throw invalid("El requisito no admite otra captura. Confirma el registro pendiente o actualiza la actividad.")
        }
    }

    private suspend fun saveCapture(draft: StructuredEvidenceDraft) {
        store.saveStructuredDraft(userId, draft)
        mutable.value = mutable.value.copy(draft = draft)
    }

    fun upload() {
        if (!mutable.value.canUpload) return
        perform {
            var draft = mutable.value.draft ?: return@perform
            val requirement = loadRequirement()
            if (draft.executionId != mutable.value.flow?.id || draft.type != requirement.type) {
                throw invalid("La captura no corresponde al requisito de esta actividad.")
            }
            if (draft.uploadPending) {
                requirement.evidence.firstOrNull { matches(draft, it) }?.let {
                    accept(draft, it)
                    return@perform
                }
            } else {
                ensureCaptureAllowed(requirement)
                draft = draft.copy(uploadPending = true, expectedVersion = requireNotNull(mutable.value.flow).version)
                saveCapture(draft)
            }
            try {
                val registered = repository.uploadStructuredEvidence(draft)
                if (registered.id <= 0 || !matches(draft, registered)) {
                    throw OperationsFailure(OperationsFailureKind.SERVER, "El servidor no confirmó la captura enviada.")
                }
                accept(draft, registered)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                val recovered = try {
                    loadRequirement().evidence.firstOrNull { matches(draft, it) }
                } catch (cancelled: CancellationException) { throw cancelled }
                catch (failure: Exception) {
                    if ((failure as? OperationsFailure)?.kind in setOf(
                            OperationsFailureKind.FORBIDDEN, OperationsFailureKind.UNAUTHENTICATED)) throw failure
                    null
                }
                if (recovered != null) {
                    accept(draft, recovered)
                    return@perform
                }
                val failure = error as? OperationsFailure
                if (failure != null && failure.kind !in setOf(OperationsFailureKind.SERVER, OperationsFailureKind.UNKNOWN)) {
                    saveCapture(draft.copy(uploadPending = false, expectedVersion = null))
                    throw error
                }
                throw OperationsFailure(OperationsFailureKind.SERVER,
                    "No se confirmó el registro. Conservamos la misma captura: actualiza o reintenta sin cambiar sus datos.")
            }
        }
    }

    private suspend fun accept(draft: StructuredEvidenceDraft, evidence: RequiredEvidence) {
        store.removeStructuredDraft(userId, draft)
        val requirement = requireNotNull(mutable.value.requirement)
        mutable.value = mutable.value.copy(draft = null, flowConfirmed = false, error = null,
            requirement = requirement.copy(evidence = (requirement.evidence + evidence).distinctBy { it.id }))
    }

    private suspend fun loadRequirement(): ExecutionEvidenceRequirement {
        val flow = repository.currentExecution(obligationId)
            ?: throw invalid("Primero inicia la actividad.")
        if (flow.operatorId != userId || flow.obligationId != obligationId) {
            throw OperationsFailure(OperationsFailureKind.FORBIDDEN, "La evidencia no corresponde a tu asignación.")
        }
        val requirement = flow.evidenceRequirements.firstOrNull { it.id == requirementId &&
            it.type in setOf(EvidenceType.QR_CODE, EvidenceType.LOCATION) }
            ?: throw invalid("El requisito de evidencia ya no está disponible.")
        mutable.value = mutable.value.copy(flow = flow, requirement = requirement, flowConfirmed = true,
            submissionPending = store.hasPendingSubmission(userId, obligationId), sessionExpired = false)
        return requirement
    }

    private fun matches(draft: StructuredEvidenceDraft, evidence: RequiredEvidence): Boolean {
        if (draft.type != evidence.type || evidence.capturedAt == null ||
            runCatching { Instant.parse(draft.capturedAt) == Instant.parse(evidence.capturedAt) }.getOrDefault(false).not()) return false
        return when (draft.type) {
            EvidenceType.QR_CODE -> draft.textValue == evidence.textValue
            EvidenceType.LOCATION -> draft.location?.let { point ->
                point.latitude == evidence.latitude && point.longitude == evidence.longitude &&
                    point.accuracyMeters == evidence.accuracyMeters
            } == true
            else -> false
        }
    }

    fun discard() {
        if (mutable.value.busy || mutable.value.draft?.uploadPending == true) return
        perform {
            mutable.value.draft?.let { store.removeStructuredDraft(userId, it) }
            mutable.value = mutable.value.copy(draft = null)
        }
    }

    fun showError(message: String) { mutable.value = mutable.value.copy(error = message) }

    private fun perform(queue: Boolean = false, block: suspend () -> Unit) {
        if (!queue && mutable.value.busy) return
        mutable.value = mutable.value.copy(busy = true)
        viewModelScope.launch {
            operations.withLock {
                mutable.value = mutable.value.copy(busy = true, error = null)
                try { block() }
                catch (cancelled: CancellationException) { throw cancelled }
                catch (error: Exception) {
                    val failure = error as? OperationsFailure
                    val denied = failure?.kind in setOf(OperationsFailureKind.FORBIDDEN, OperationsFailureKind.UNAUTHENTICATED,
                        OperationsFailureKind.NOT_FOUND)
                    mutable.value = mutable.value.copy(error = failure?.message ?: "No se pudo consultar o preparar la evidencia.",
                        sessionExpired = failure?.kind == OperationsFailureKind.UNAUTHENTICATED,
                        flowConfirmed = if (denied || failure == null || failure.kind in setOf(
                            OperationsFailureKind.SERVER, OperationsFailureKind.UNKNOWN)) false else mutable.value.flowConfirmed,
                        flow = if (denied) null else mutable.value.flow,
                        requirement = if (denied) null else mutable.value.requirement,
                        draft = if (denied) null else mutable.value.draft)
                } finally { mutable.value = mutable.value.copy(busy = false) }
            }
        }
    }

    private fun invalid(message: String) = OperationsFailure(OperationsFailureKind.INVALID_REQUEST, message)
}
