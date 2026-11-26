package com.opervia.example.serviceCompliance.features.operations.presentation.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.opervia.example.serviceCompliance.features.operations.data.photoChecksum
import com.opervia.example.serviceCompliance.features.operations.domain.model.*
import com.opervia.example.serviceCompliance.features.operations.domain.repository.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID

data class PhotoState(
    val flow: ExecutionFlow? = null,
    val flowConfirmed: Boolean = false,
    val title: String = "Fotografía de la actividad",
    val description: String? = null,
    val requiresLocation: Boolean = false,
    val submissionPending: Boolean = false,
    val draft: PhotoDraft? = null,
    val evidenceId: Long? = null,
    val preview: ByteArray? = null,
    val busy: Boolean = false,
    val sessionExpired: Boolean = false,
    val accessDenied: Boolean = false,
    val error: String? = null
) {
    val canCapture: Boolean get() = flowConfirmed && flow?.canEdit == true &&
        evidenceId == null && !busy && !sessionExpired && !submissionPending && draft?.uploadPending != true
    val canUpload: Boolean get() = !busy && !sessionExpired && !accessDenied && evidenceId == null &&
        (draft?.uploadPending == true || (canCapture && draft?.checksum != null &&
            (!requiresLocation || draft.location != null)))
}

class PhotoViewModel(
    private val repository: OperationsRepository,
    private val store: PhotoDraftStore,
    private val files: PhotoFiles,
    private val location: PhotoLocationProvider,
    private val userId: Long,
    private val obligationId: Long,
    private val kind: PhotoKind,
    private val requirementId: Long,
    private val requestedEvidenceId: Long? = null,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
    private val evidenceType: EvidenceType = EvidenceType.PHOTO
) : ViewModel() {
    private val mutable = MutableStateFlow(PhotoState())
    val state = mutable.asStateFlow()
    private val operations = Mutex()
    private var acceptedEvidenceId: Long? = null

    init {
        require(evidenceType in setOf(EvidenceType.PHOTO, EvidenceType.SIGNATURE))
        require(evidenceType != EvidenceType.SIGNATURE || kind == PhotoKind.EVIDENCE_REQUIREMENT)
        refresh()
    }

    fun refresh() = perform {
        mutable.value = mutable.value.copy(flowConfirmed = false)
        val flow = currentFlow()
        val draft = store.photoDraft(userId, flow.id, kind, requirementId)
        mutable.value = mutable.value.copy(draft = draft)
        if (draft?.uploadPending == true) {
            val confirmed = findUploadedDraft(flow, draft)
            if (confirmed != null) {
                accept(confirmed, draft)
                return@perform
            }
            if (mutable.value.evidenceId != null) {
                val rejected = draft.copy(uploadPending = false, expectedVersion = null)
                store.savePhotoDraft(userId, rejected)
                mutable.value = mutable.value.copy(draft = rejected,
                    error = "El requisito ya tiene una fotografía registrada. Puedes consultar esa foto y descartar el borrador local.")
            }
        }
        val evidenceId = mutable.value.evidenceId
        if (evidenceId != null) loadRegistered(evidenceId)
        else if (draft != null) {
            val file = withContext(ioDispatcher) { files.file(userId, draft) }
            if (file.isFile && file.length() > 0) inspectDraft(draft)
        }
    }

    private suspend fun currentFlow(): ExecutionFlow {
        val flow = repository.currentExecution(obligationId)
            ?: throw unavailable("La actividad todavía no tiene una ejecución iniciada.")
        if (flow.operatorId != userId || flow.obligationId != obligationId) {
            throw OperationsFailure(OperationsFailureKind.FORBIDDEN, "La fotografía no corresponde a tu asignación.")
        }
        val pending = store.hasPendingSubmission(userId, obligationId)
        if (kind == PhotoKind.PHOTO_REQUIREMENT) {
            val target = flow.photoRequirements.firstOrNull { it.id == requirementId }
                ?: throw unavailable("El requisito fotográfico ya no está disponible.")
            if (requestedEvidenceId != null && target.evidenceId != requestedEvidenceId) throw unavailable("La fotografía no pertenece a este requisito.")
            mutable.value = mutable.value.copy(flow = flow, flowConfirmed = true, sessionExpired = false, accessDenied = false,
                title = target.title, description = target.description, evidenceId = target.evidenceId,
                preview = if (mutable.value.evidenceId == target.evidenceId) mutable.value.preview else null,
                requiresLocation = false, submissionPending = pending)
        } else {
            val target = flow.evidenceRequirements.firstOrNull { it.id == requirementId && it.type == evidenceType }
                ?: throw unavailable("El requisito no admite fotografías.")
            val requested = requestedEvidenceId ?: acceptedEvidenceId
            if (requested != null && target.evidence.none { it.id == requested }) throw unavailable("La fotografía no pertenece a este requisito.")
            val selected = requested ?: if (target.evidence.size >= target.maximumCount) target.evidence.firstOrNull()?.id else null
            mutable.value = mutable.value.copy(flow = flow, flowConfirmed = true, sessionExpired = false, accessDenied = false,
                title = if (evidenceType == EvidenceType.SIGNATURE) "Firma de la actividad" else "Fotografía de la actividad", evidenceId = selected,
                preview = if (mutable.value.evidenceId == selected) mutable.value.preview else null,
                requiresLocation = target.constraints["requireGeolocation"].toBoolean(), submissionPending = pending)
        }
        return flow
    }

    fun prepareCapture(onReady: (File) -> Unit) {
        if (!mutable.value.canCapture) return
        perform {
            val flow = currentFlow()
            if (!flow.canEdit || mutable.value.evidenceId != null || mutable.value.submissionPending) throw unavailable("La actividad no admite otra fotografía.")
            val previous = mutable.value.draft
            if (previous?.uploadPending == true) throw unavailable("Primero confirma o reintenta la subida pendiente.")
            val fileName = withContext(ioDispatcher) { files.create(userId, flow.id) }
            val draft = PhotoDraft(flow.id, requirementId, kind, fileName, "photo-${UUID.randomUUID()}")
            store.savePhotoDraft(userId, draft)
            mutable.value = mutable.value.copy(draft = draft, preview = null)
            if (previous != null) withContext(ioDispatcher) { files.discard(userId, previous) }
            onReady(withContext(ioDispatcher) { files.file(userId, draft) })
        }
    }

    fun captureReturned(success: Boolean, failureMessage: String? = null) = perform(queue = true) {
        val flow = mutable.value.flow ?: currentFlow()
        val draft = store.photoDraft(userId, flow.id, kind, requirementId) ?: return@perform
        if (draft.uploadPending) return@perform
        if (success) inspectDraft(draft)
        else {
            store.removePhotoDraft(userId, draft)
            withContext(ioDispatcher) { files.discard(userId, draft) }
            mutable.value = mutable.value.copy(draft = null, preview = null)
            if (failureMessage != null) mutable.value = mutable.value.copy(error = failureMessage)
        }
    }

    private suspend fun inspectDraft(draft: PhotoDraft) {
        val photo = withContext(ioDispatcher) { files.inspect(userId, draft) }
        if (draft.checksum != null && draft.checksum != photo.checksum) throw unavailable("El archivo de la foto cambió. No se puede reintentar con otros bytes.")
        validateImage(photo)
        val inspected = draft.copy(contentType = photo.contentType, checksum = photo.checksum,
            capturedAt = draft.capturedAt ?: photo.capturedAt)
        store.savePhotoDraft(userId, inspected)
        mutable.value = mutable.value.copy(draft = inspected, preview = photo.bytes)
    }

    fun saveSignature(bytes: ByteArray) {
        if (!mutable.value.canCapture || evidenceType != EvidenceType.SIGNATURE) return
        perform {
            val flow = currentFlow()
            if (!flow.canEdit || mutable.value.evidenceId != null || mutable.value.submissionPending) {
                throw unavailable("La actividad no admite otra firma.")
            }
            val previous = mutable.value.draft
            if (previous?.uploadPending == true) throw unavailable("Primero confirma o reintenta la firma pendiente.")
            if (bytes.isEmpty() || bytes.size > MAX_SIGNATURE_BYTES) throw unavailable("La firma debe ser PNG de hasta 1 MB.")
            val name = withContext(ioDispatcher) { files.create(userId, flow.id) }
            val draft = PhotoDraft(flow.id, requirementId, kind, name, "signature-${UUID.randomUUID()}")
            store.savePhotoDraft(userId, draft)
            mutable.value = mutable.value.copy(draft = draft, preview = null)
            withContext(ioDispatcher) { files.file(userId, draft).writeBytes(bytes) }
            inspectDraft(draft)
            if (previous != null) withContext(ioDispatcher) { files.discard(userId, previous) }
        }
    }

    private fun validateImage(image: CapturedPhoto) {
        if (evidenceType == EvidenceType.SIGNATURE && (image.contentType != "image/png" || image.bytes.size > MAX_SIGNATURE_BYTES)) {
            throw unavailable("La firma debe ser PNG de hasta 1 MB.")
        }
    }

    fun attachLocation() {
        if (!mutable.value.canCapture || mutable.value.draft?.checksum == null) return
        perform {
            val draft = mutable.value.draft ?: return@perform
            val point = location.current()
            require(point.latitude in -90.0..90.0 && point.longitude in -180.0..180.0 && point.accuracyMeters > 0)
            val updated = draft.copy(location = point)
            store.savePhotoDraft(userId, updated)
            mutable.value = mutable.value.copy(draft = updated)
        }
    }

    fun upload() {
        if (!mutable.value.canUpload) return
        perform {
            var draft = mutable.value.draft ?: return@perform
            val flow = currentFlow()
            val already = findUploadedDraft(flow, draft)
            if (already != null) {
                accept(already, draft)
                return@perform
            }
            mutable.value.evidenceId?.let {
                loadRegistered(it)
                throw unavailable("Este requisito ya tiene una fotografía registrada; no puede reemplazarse.")
            }
            val photo = withContext(ioDispatcher) { files.inspect(userId, draft) }
            validateImage(photo)
            if (photo.checksum != draft.checksum) throw unavailable("El archivo cambió; no se enviarán otros bytes con la misma solicitud.")
            if (!draft.uploadPending) {
                if (!flow.canEdit || mutable.value.submissionPending) throw unavailable("La actividad ya no admite fotografías.")
                if (mutable.value.requiresLocation && draft.location == null) throw unavailable("Este requisito exige registrar la ubicación de la foto.")
                draft = draft.copy(expectedVersion = flow.version, uploadPending = true)
                store.savePhotoDraft(userId, draft)
                mutable.value = mutable.value.copy(draft = draft)
            }
            val evidenceId = try {
                repository.uploadPhoto(PhotoUpload(draft, photo.bytes, evidenceType))
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                val recovered = try {
                    val latest = currentFlow()
                    findUploadedDraft(latest, draft)
                } catch (cancelled: CancellationException) { throw cancelled }
                catch (_: Exception) { null }
                if (recovered != null) {
                    accept(recovered, draft)
                    return@perform
                }
                val failure = error as? OperationsFailure
                if (failure != null && failure.kind !in setOf(OperationsFailureKind.SERVER, OperationsFailureKind.UNKNOWN)) {
                    val rejected = draft.copy(uploadPending = false, expectedVersion = null)
                    store.savePhotoDraft(userId, rejected)
                    mutable.value = mutable.value.copy(draft = rejected)
                    throw error
                }
                throw OperationsFailure(OperationsFailureKind.SERVER,
                    "No se confirmó la subida. Conservamos la foto: actualiza o reintenta sin volver a tomarla.")
            }
            accept(evidenceId, draft)
        }
    }

    private suspend fun findUploadedDraft(flow: ExecutionFlow, draft: PhotoDraft): Long? {
        val checksum = draft.checksum ?: return null
        if (kind == PhotoKind.EVIDENCE_REQUIREMENT) {
            return flow.evidenceRequirements.firstOrNull { it.id == requirementId }?.evidence
                ?.firstOrNull { evidence -> evidence.checksum == checksum && evidence.capturedAt != null &&
                    runCatching { java.time.Instant.parse(evidence.capturedAt) == java.time.Instant.parse(draft.capturedAt) }.getOrDefault(false) }?.id
        }
        val id = flow.photoRequirements.firstOrNull { it.id == requirementId }?.evidenceId ?: return null
        val registered = repository.photoContent(id)
        return if (withContext(ioDispatcher) { photoChecksum(registered.bytes) } == checksum) id else null
    }

    private suspend fun accept(evidenceId: Long, draft: PhotoDraft) {
        acceptedEvidenceId = evidenceId
        mutable.value = mutable.value.copy(evidenceId = evidenceId)
        store.removePhotoDraft(userId, draft)
        withContext(ioDispatcher) { files.discard(userId, draft) }
        mutable.value = mutable.value.copy(draft = null)
        loadRegistered(evidenceId)
    }

    private suspend fun loadRegistered(evidenceId: Long) {
        val photo = repository.photoContent(evidenceId)
        mutable.value = mutable.value.copy(evidenceId = evidenceId, preview = photo.bytes)
    }

    fun discard() {
        val draft = mutable.value.draft ?: return
        if (draft.uploadPending || mutable.value.busy) return
        perform {
            store.removePhotoDraft(userId, draft)
            withContext(ioDispatcher) { files.discard(userId, draft) }
            mutable.value = mutable.value.copy(draft = null,
                preview = if (mutable.value.evidenceId != null) mutable.value.preview else null)
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
                    val denied = failure?.kind in setOf(OperationsFailureKind.FORBIDDEN, OperationsFailureKind.UNAUTHENTICATED)
                    mutable.value = mutable.value.copy(error = failure?.message ?: "No se pudo preparar o consultar la foto. Inténtalo nuevamente.",
                        sessionExpired = failure?.kind == OperationsFailureKind.UNAUTHENTICATED,
                        accessDenied = denied,
                        flowConfirmed = if (denied) false else mutable.value.flowConfirmed,
                        preview = if (denied) null else mutable.value.preview)
                } finally { mutable.value = mutable.value.copy(busy = false) }
            }
        }
    }

    private fun unavailable(message: String) = OperationsFailure(OperationsFailureKind.INVALID_REQUEST, message)
}
