package com.opervia.example.serviceCompliance.features.operations.presentation.viewmodel

import com.opervia.example.serviceCompliance.features.operations.PhotoTestData
import com.opervia.example.serviceCompliance.features.operations.data.LocalPhotoFiles
import com.opervia.example.serviceCompliance.features.operations.data.photoChecksum
import com.opervia.example.serviceCompliance.features.operations.domain.model.*
import com.opervia.example.serviceCompliance.features.operations.domain.repository.*
import kotlinx.coroutines.*
import kotlinx.coroutines.test.*
import org.junit.*
import org.junit.Assert.*
import org.junit.rules.TemporaryFolder
import java.io.File

@OptIn(ExperimentalCoroutinesApi::class)
class PhotoViewModelTest {
    @get:Rule val temporary = TemporaryFolder()
    private lateinit var dispatcher: TestDispatcher
    private lateinit var repository: FakeRepository
    private lateinit var store: FakeStore
    private lateinit var files: LocalPhotoFiles
    private val location = object : PhotoLocationProvider {
        override suspend fun current() = PhotoLocation(-12.1, -77.03, 8.0)
    }

    @Before
    fun setUp() {
        dispatcher = StandardTestDispatcher()
        Dispatchers.setMain(dispatcher)
        repository = FakeRepository()
        store = FakeStore()
        files = LocalPhotoFiles(temporary.root) { 1 to 1 }
    }

    @After
    fun tearDown() { Dispatchers.resetMain() }

    private fun model(kind: PhotoKind = PhotoKind.PHOTO_REQUIREMENT, type: EvidenceType = EvidenceType.PHOTO) = PhotoViewModel(
        repository, store, files, location, 42, 120, kind,
        if (kind == PhotoKind.PHOTO_REQUIREMENT) 801 else 802, ioDispatcher = dispatcher, evidenceType = type)

    private suspend fun TestScope.capture(model: PhotoViewModel): File {
        var captureFile: File? = null
        model.prepareCapture { captureFile = it }
        advanceUntilIdle()
        val file = requireNotNull(captureFile)
        file.writeBytes(PhotoTestData.png)
        model.captureReturned(true)
        advanceUntilIdle()
        return file
    }

    @Test
    fun captureIsReviewedLocallyBeforeAnyUpload() = runTest {
        val model = model()
        advanceUntilIdle()
        capture(model)
        assertNotNull(model.state.value.preview)
        assertNotNull(store.draft?.capturedAt)
        assertTrue(model.state.value.canUpload)
        assertNull(model.state.value.evidenceId)
        assertTrue(repository.uploads.isEmpty())
    }

    @Test
    fun cancelledCaptureRemovesOnlyTheUnsentDraft() = runTest {
        val model = model()
        advanceUntilIdle()
        var file: File? = null
        model.prepareCapture { file = it }
        advanceUntilIdle()
        model.captureReturned(false)
        advanceUntilIdle()
        assertFalse(file!!.exists())
        assertNull(store.draft)
        assertNull(model.state.value.preview)
        assertTrue(repository.uploads.isEmpty())
    }

    @Test
    fun acceptedPhotoBecomesReadOnlyAndIsLoadedFromPrivateContent() = runTest {
        val model = model()
        advanceUntilIdle()
        val file = capture(model)
        model.upload()
        advanceUntilIdle()
        assertEquals(901L, model.state.value.evidenceId)
        assertFalse(model.state.value.canCapture)
        assertNull(store.draft)
        assertFalse(file.exists())
        assertTrue(repository.contentRequests.contains(901))
        assertArrayEquals(PhotoTestData.png, model.state.value.preview)
        model.prepareCapture { fail("Una foto confirmada no se reemplaza") }
        advanceUntilIdle()
        assertEquals(1, repository.uploads.size)
    }

    @Test
    fun uncertainUploadRetainsFileAndExactRequestAcrossReopening() = runTest {
        val first = model()
        advanceUntilIdle()
        val file = capture(first)
        repository.onUpload = { throw OperationsFailure(OperationsFailureKind.SERVER, "Respuesta perdida") }
        first.upload()
        advanceUntilIdle()
        assertTrue(store.draft!!.uploadPending)
        assertTrue(file.exists())
        assertFalse(first.state.value.canCapture)
        val second = model()
        advanceUntilIdle()
        repository.onUpload = { repository.recordPhoto(it) }
        second.upload()
        advanceUntilIdle()
        assertEquals(2, repository.uploads.size)
        assertEquals(repository.uploads[0].draft, repository.uploads[1].draft)
        assertArrayEquals(repository.uploads[0].bytes, repository.uploads[1].bytes)
        assertEquals(901L, second.state.value.evidenceId)
        assertNull(store.draft)
    }

    @Test
    fun responseLostAfterCommitIsReconciledWithPrivatePhotoChecksum() = runTest {
        val model = model()
        advanceUntilIdle()
        capture(model)
        repository.onUpload = {
            repository.recordPhoto(it)
            throw OperationsFailure(OperationsFailureKind.SERVER, "Respuesta perdida")
        }
        model.upload()
        advanceUntilIdle()
        assertEquals(901L, model.state.value.evidenceId)
        assertEquals(1, repository.uploads.size)
        assertNull(store.draft)
        assertNull(model.state.value.error)
    }

    @Test
    fun definitiveVersionConflictKeepsPhotoAndAllowsFreshVersionRetry() = runTest {
        val model = model()
        advanceUntilIdle()
        capture(model)
        repository.onUpload = {
            repository.flow = repository.flow.copy(version = 9)
            throw OperationsFailure(OperationsFailureKind.CONFLICT, "La ejecución cambió", "VERSION_CONFLICT")
        }
        model.upload()
        advanceUntilIdle()
        assertFalse(store.draft!!.uploadPending)
        assertTrue(model.state.value.canUpload)
        repository.onUpload = { repository.recordPhoto(it) }
        model.upload()
        advanceUntilIdle()
        assertEquals(3L, repository.uploads[0].draft.expectedVersion)
        assertEquals(9L, repository.uploads[1].draft.expectedVersion)
        assertEquals(repository.uploads[0].draft.idempotencyKey, repository.uploads[1].draft.idempotencyKey)
        assertArrayEquals(repository.uploads[0].bytes, repository.uploads[1].bytes)
    }

    @Test
    fun anotherRegisteredPhotoCannotBeOverwrittenByLocalDraft() = runTest {
        val model = model()
        advanceUntilIdle()
        capture(model)
        repository.recordPhoto(PhotoUpload(store.draft!!, PhotoTestData.png + byteArrayOf(1)))
        model.upload()
        advanceUntilIdle()
        assertTrue(repository.uploads.isEmpty())
        assertFalse(model.state.value.canCapture)
        assertNotNull(model.state.value.error)
        assertArrayEquals(repository.content, model.state.value.preview)
    }

    @Test
    fun previewFailureAfterAcceptedUploadDoesNotClaimUploadWasUnconfirmed() = runTest {
        val model = model()
        advanceUntilIdle()
        val file = capture(model)
        repository.onUpload = {
            val id = repository.recordPhoto(it)
            repository.contentFailure = OperationsFailure(OperationsFailureKind.SERVER, "Vista previa no disponible")
            id
        }
        model.upload()
        advanceUntilIdle()
        assertEquals(901L, model.state.value.evidenceId)
        assertNull(store.draft)
        assertFalse(file.exists())
        assertEquals("Vista previa no disponible", model.state.value.error)
        assertEquals(1, repository.uploads.size)
    }

    @Test
    fun cameraResultArrivingDuringInitializationIsNotDropped() = runTest {
        val draft = PhotoDraft(310, 801, PhotoKind.PHOTO_REQUIREMENT, files.create(42, 310), "camera-restored")
        files.file(42, draft).writeBytes(PhotoTestData.png)
        store.draft = draft
        val model = model()
        model.captureReturned(true)
        advanceUntilIdle()
        assertNotNull(model.state.value.draft?.checksum)
        assertNotNull(model.state.value.preview)
        assertFalse(model.state.value.busy)
        assertTrue(repository.uploads.isEmpty())
    }

    @Test
    fun typedPhotoGetsRealRequiredLocationBeforeUpload() = runTest {
        repository.flow = flow().copy(photoRequirements = emptyList(), evidenceRequirements = listOf(
            ExecutionEvidenceRequirement(802, EvidenceType.PHOTO, true, 1, 1, 1,
                mapOf("requireGeolocation" to "true"), emptyList())))
        val model = model(PhotoKind.EVIDENCE_REQUIREMENT)
        advanceUntilIdle()
        capture(model)
        assertFalse(model.state.value.canUpload)
        model.attachLocation()
        advanceUntilIdle()
        assertTrue(model.state.value.canUpload)
        model.upload()
        advanceUntilIdle()
        assertEquals(PhotoLocation(-12.1, -77.03, 8.0), repository.uploads.single().draft.location)
        assertEquals(901L, model.state.value.evidenceId)
    }

    @Test
    fun revokedAccessHidesPreviewAndStopsUploads() = runTest {
        val model = model()
        advanceUntilIdle()
        capture(model)
        repository.failure = OperationsFailure(OperationsFailureKind.FORBIDDEN, "Sin acceso")
        model.refresh()
        advanceUntilIdle()
        assertNull(model.state.value.preview)
        assertFalse(model.state.value.canCapture)
        assertFalse(model.state.value.canUpload)
    }

    @Test
    fun pendingExecutionSubmissionPreventsNewCapture() = runTest {
        store.submissionPending = true
        val model = model()
        advanceUntilIdle()
        assertFalse(model.state.value.canCapture)
        model.prepareCapture { fail("No debe tomar fotos durante un envío pendiente") }
        advanceUntilIdle()
        assertNull(store.draft)
    }

    @Test
    fun signatureIsReviewedAsPngAndUploadedWithItsOwnType() = runTest {
        repository.flow = flow().copy(photoRequirements = emptyList(), evidenceRequirements = listOf(
            ExecutionEvidenceRequirement(802, EvidenceType.SIGNATURE, true, 1, 1, 1, emptyMap(), emptyList())))
        val model = model(PhotoKind.EVIDENCE_REQUIREMENT, EvidenceType.SIGNATURE)
        advanceUntilIdle()
        model.saveSignature(PhotoTestData.png)
        advanceUntilIdle()
        assertTrue(model.state.value.canUpload)
        assertNull(model.state.value.evidenceId)
        assertTrue(repository.uploads.isEmpty())
        model.upload(); advanceUntilIdle()
        assertEquals(EvidenceType.SIGNATURE, repository.uploads.single().type)
        assertEquals("image/png", repository.uploads.single().draft.contentType)
        assertEquals(901L, model.state.value.evidenceId)
        assertFalse(model.state.value.canCapture)
    }

    @Test
    fun signatureRejectsOversizedDataBeforePreparingAnyFile() = runTest {
        repository.flow = flow().copy(photoRequirements = emptyList(), evidenceRequirements = listOf(
            ExecutionEvidenceRequirement(802, EvidenceType.SIGNATURE, true, 1, 1, 1, emptyMap(), emptyList())))
        val model = model(PhotoKind.EVIDENCE_REQUIREMENT, EvidenceType.SIGNATURE)
        advanceUntilIdle()
        model.saveSignature(ByteArray(MAX_SIGNATURE_BYTES + 1))
        advanceUntilIdle()
        assertNull(store.draft)
        assertTrue(repository.uploads.isEmpty())
        assertNotNull(model.state.value.error)
    }

    @Test
    fun uncertainSignatureKeepsTheSamePrivatePngUntilConfirmed() = runTest {
        repository.flow = flow().copy(photoRequirements = emptyList(), evidenceRequirements = listOf(
            ExecutionEvidenceRequirement(802, EvidenceType.SIGNATURE, true, 1, 1, 1, emptyMap(), emptyList())))
        val first = model(PhotoKind.EVIDENCE_REQUIREMENT, EvidenceType.SIGNATURE)
        advanceUntilIdle()
        first.saveSignature(PhotoTestData.png); advanceUntilIdle()
        repository.onUpload = { throw OperationsFailure(OperationsFailureKind.SERVER, "Sin conexión") }
        first.upload(); advanceUntilIdle()
        val pending = requireNotNull(store.draft)
        assertTrue(pending.uploadPending)
        first.saveSignature(PhotoTestData.png); advanceUntilIdle()
        assertEquals(pending, store.draft)
        val second = model(PhotoKind.EVIDENCE_REQUIREMENT, EvidenceType.SIGNATURE)
        advanceUntilIdle()
        repository.onUpload = { repository.recordPhoto(it) }
        second.upload(); advanceUntilIdle()
        assertEquals(repository.uploads[0].draft, repository.uploads[1].draft)
        assertArrayEquals(repository.uploads[0].bytes, repository.uploads[1].bytes)
        assertNull(store.draft)
        assertEquals(901L, second.state.value.evidenceId)
    }

    private class FakeStore : PhotoDraftStore {
        var draft: PhotoDraft? = null
        var submissionPending = false
        override suspend fun hasPendingSubmission(userId: Long, obligationId: Long) = submissionPending
        override suspend fun photoDraft(userId: Long, executionId: Long, kind: PhotoKind, requirementId: Long) = draft
        override suspend fun savePhotoDraft(userId: Long, draft: PhotoDraft) { this.draft = draft }
        override suspend fun removePhotoDraft(userId: Long, draft: PhotoDraft) { this.draft = null }
    }

    private class FakeRepository : OperationsRepository {
        var flow = flow()
        var failure: OperationsFailure? = null
        var contentFailure: OperationsFailure? = null
        var content = PhotoTestData.png
        val uploads = mutableListOf<PhotoUpload>()
        val contentRequests = mutableListOf<Long>()
        var onUpload: suspend (PhotoUpload) -> Long = { recordPhoto(it) }
        override suspend fun currentExecution(obligationId: Long): ExecutionFlow {
            failure?.let { throw it }
            return flow
        }
        override suspend fun uploadStructuredEvidence(draft: StructuredEvidenceDraft): RequiredEvidence = error("No usado")
        override suspend fun reportException(report: PendingExceptionReport): ExecutionFlow = error("No usado")
        override suspend fun uploadPhoto(upload: PhotoUpload): Long {
            uploads.add(upload)
            return onUpload(upload)
        }
        fun recordPhoto(upload: PhotoUpload): Long {
            content = upload.bytes
            flow = if (upload.draft.kind == PhotoKind.PHOTO_REQUIREMENT) flow.copy(
                photoRequirements = flow.photoRequirements.map { it.copy(evidenceId = 901, contentPath = "/api/v1/evidence/901/content") })
            else flow.copy(evidenceRequirements = flow.evidenceRequirements.map { requirement ->
                requirement.copy(evidence = listOf(RequiredEvidence(901, upload.type, "/api/v1/evidence/901/content",
                    null, upload.draft.capturedAt, photoChecksum(upload.bytes))))
            })
            return 901
        }
        override suspend fun photoContent(evidenceId: Long): PrivatePhoto {
            failure?.let { throw it }
            contentFailure?.let { throw it }
            contentRequests.add(evidenceId)
            return PrivatePhoto(content, "image/png")
        }
        override suspend fun obligationDetail(id: Long): ObligationDetail = error("No usado")
        override suspend fun executionHistory(query: ExecutionHistoryQuery, cursor: String?): ExecutionHistoryPage = error("No usado")
        override suspend fun assignedObligations(): List<AssignedObligation> = error("No usado")
        override suspend fun startExecution(obligationId: Long, idempotencyKey: String): ExecutionFlow = error("No usado")
        override suspend fun updateStep(executionId: Long, stepId: Long, completed: Boolean, expectedVersion: Long): ExecutionFlow = error("No usado")
        override suspend fun submitExecution(submission: PendingExecutionSubmission): ExecutionFlow = error("No usado")
        override suspend fun registerExecution(obligationId: Long, result: ExecutionResult, notes: String?): ExecutionRecord = error("No usado")
        override suspend fun execution(id: Long): ExecutionRecord = error("No usado")
        override suspend fun evidence(id: Long): List<EvidenceRecord> = error("No usado")
        override suspend fun addEvidence(executionId: Long, type: EvidenceType, url: String, description: String?): EvidenceRecord = error("No usado")
    }

    companion object {
        private fun flow() = ExecutionFlow(310, 120, 42, ExecutionState.IN_PROGRESS, "IN_PROGRESS", null,
            "2026-11-25T20:00:00Z", null, "2026-11-25T19:00:00Z", "2026-11-26T03:00:00Z", "America/Lima", 3,
            true, null, listOf(ExecutionStep(701, "Piso", null, 1, true)),
            listOf(ExecutionPhotoRequirement(801, "Piso limpio", null, 1, null, null)), emptyList())
    }
}
