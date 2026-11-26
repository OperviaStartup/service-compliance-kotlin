package com.opervia.example.serviceCompliance.features.operations.presentation.viewmodel

import com.opervia.example.serviceCompliance.features.operations.domain.model.*
import com.opervia.example.serviceCompliance.features.operations.domain.repository.*
import java.time.Instant
import kotlinx.coroutines.*
import kotlinx.coroutines.test.*
import org.junit.*
import org.junit.Assert.*

@OptIn(ExperimentalCoroutinesApi::class)
class StructuredEvidenceViewModelTest {
    private lateinit var repository: FakeRepository
    private lateinit var store: FakeStore
    private var point = PhotoLocation(-12.1, -77.03, 8.0, "2026-11-25T22:00:00Z")
    private var locationFailure: Exception? = null
    private var imageValue = "OPER-IMAGEN-01"
    private var imageFailure: Exception? = null
    private val imageRequests = mutableListOf<String>()

    @Before fun setUp() {
        Dispatchers.setMain(StandardTestDispatcher())
        repository = FakeRepository()
        store = FakeStore()
    }
    @After fun tearDown() { Dispatchers.resetMain() }

    private fun model() = StructuredEvidenceViewModel(repository, store, object : PhotoLocationProvider {
        override suspend fun current(): PhotoLocation { locationFailure?.let { throw it }; return point }
    }, 42, 120, 802, now = { Instant.parse("2026-11-25T22:00:00.123456789Z") }, qrImages = object : QrImageReader {
        override suspend fun read(uri: String): String {
            imageRequests.add(uri)
            imageFailure?.let { throw it }
            return imageValue
        }
    })

    @Test fun scannedQrIsReviewedAndNotSentAutomatically() = runTest {
        val model = model(); advanceUntilIdle()
        model.captureQr("  OPER-123  "); advanceUntilIdle()
        assertEquals("OPER-123", store.draft?.textValue)
        assertEquals("2026-11-25T22:00:00.123Z", store.draft?.capturedAt)
        assertTrue(model.state.value.canUpload)
        assertTrue(repository.uploads.isEmpty())
        assertTrue(model.state.value.requirement!!.evidence.isEmpty())
    }

    @Test fun wrongPrefixOrOversizedCodeCanBeRetriedWithoutInventingEvidence() = runTest {
        val model = model(); advanceUntilIdle()
        model.captureQr("OTHER-123"); advanceUntilIdle()
        assertNull(store.draft)
        assertTrue(model.state.value.canCapture)
        model.captureQr("OPER-" + "a".repeat(512)); advanceUntilIdle()
        assertNull(store.draft)
        model.captureQr("OPER-123"); advanceUntilIdle()
        assertNotNull(store.draft)
        assertTrue(repository.uploads.isEmpty())
    }

    @Test fun lateScannerResultWaitsForInitialLoad() = runTest {
        val model = model()
        model.captureQr("OPER-123")
        advanceUntilIdle()
        assertEquals("OPER-123", store.draft?.textValue)
        assertFalse(model.state.value.busy)
    }

    @Test fun currentLocationUsesProviderCoordinatesPrecisionAndCaptureTime() = runTest {
        repository.flow = flow(EvidenceType.LOCATION)
        val model = model(); advanceUntilIdle()
        model.captureLocation(); advanceUntilIdle()
        assertEquals(point, store.draft?.location)
        assertEquals(point.capturedAt, store.draft?.capturedAt)
        assertTrue(repository.uploads.isEmpty())
        model.upload(); advanceUntilIdle()
        assertEquals(point, repository.uploads.single().location)
        assertEquals(3L, repository.uploads.single().expectedVersion)
        assertEquals(901L, model.state.value.requirement?.evidence?.single()?.id)
        assertNull(store.draft)
    }

    @Test fun deniedLocationOrExcessiveAccuracyDoesNotRegisterCoordinates() = runTest {
        repository.flow = flow(EvidenceType.LOCATION)
        val model = model(); advanceUntilIdle()
        locationFailure = OperationsFailure(OperationsFailureKind.INVALID_REQUEST, "Sin permiso")
        model.captureLocation(); advanceUntilIdle()
        assertNull(store.draft)
        locationFailure = null
        point = point.copy(accuracyMeters = 101.0)
        model.captureLocation(); advanceUntilIdle()
        assertNull(store.draft)
        assertTrue(repository.uploads.isEmpty())
    }

    @Test fun responseLostAfterServerAcceptanceIsReconciled() = runTest {
        val model = model(); advanceUntilIdle()
        model.captureQr("OPER-123"); advanceUntilIdle()
        repository.onUpload = {
            repository.record(it)
            throw OperationsFailure(OperationsFailureKind.SERVER, "Respuesta perdida")
        }
        model.upload(); advanceUntilIdle()
        assertEquals(1, repository.uploads.size)
        assertEquals(1, model.state.value.requirement!!.evidence.size)
        assertNull(store.draft)
        assertNull(model.state.value.error)
    }

    @Test fun uncertainUploadSurvivesReopeningAndReusesExactCapture() = runTest {
        val first = model(); advanceUntilIdle()
        first.captureQr("OPER-123"); advanceUntilIdle()
        repository.onUpload = { throw OperationsFailure(OperationsFailureKind.SERVER, "Sin conexión") }
        first.upload(); advanceUntilIdle()
        assertTrue(store.draft!!.uploadPending)
        assertFalse(first.state.value.canCapture)
        first.captureQr("OPER-999"); advanceUntilIdle()
        assertEquals("OPER-123", store.draft?.textValue)
        val second = model(); advanceUntilIdle()
        repository.onUpload = { repository.record(it) }
        second.upload(); advanceUntilIdle()
        assertEquals(repository.uploads[0], repository.uploads[1])
        assertNull(store.draft)
        assertEquals(1, second.state.value.requirement!!.evidence.size)
    }

    @Test fun staleVersionRefreshesAndKeepsTheCaptureEditable() = runTest {
        val model = model(); advanceUntilIdle()
        model.captureQr("OPER-123"); advanceUntilIdle()
        repository.onUpload = {
            repository.flow = repository.flow.copy(version = 4)
            throw OperationsFailure(OperationsFailureKind.CONFLICT, "Versión cambió")
        }
        model.upload(); advanceUntilIdle()
        assertFalse(store.draft!!.uploadPending)
        assertNull(store.draft!!.expectedVersion)
        assertTrue(model.state.value.canCapture)
        assertEquals(4L, model.state.value.flow?.version)
    }

    @Test fun maximumAndExpiredExecutionPreventNewCaptures() = runTest {
        repository.flow = flow().copy(canEdit = false, obligationStatus = "OVERDUE")
        val model = model(); advanceUntilIdle()
        model.captureQr("OPER-123"); advanceUntilIdle()
        assertNull(store.draft)
        repository.flow = flow()
        val full = repository.flow.evidenceRequirements.single().copy(evidence = listOf(
            RequiredEvidence(901, EvidenceType.QR_CODE, null, "OPER-123", "2026-11-25T21:00:00Z")))
        repository.flow = repository.flow.copy(evidenceRequirements = listOf(full))
        model.refresh(); advanceUntilIdle()
        assertFalse(model.state.value.canCapture)
    }

    @Test fun acceptedPendingCaptureCanBeRecoveredAfterDeadline() = runTest {
        val model = model(); advanceUntilIdle()
        model.captureQr("OPER-123"); advanceUntilIdle()
        repository.onUpload = { throw OperationsFailure(OperationsFailureKind.SERVER, "Sin respuesta") }
        model.upload(); advanceUntilIdle()
        repository.record(requireNotNull(store.draft))
        repository.flow = repository.flow.copy(canEdit = false, obligationStatus = "OVERDUE")
        val second = model(); advanceUntilIdle()
        assertNull(store.draft)
        assertEquals(1, second.state.value.requirement!!.evidence.size)
        assertEquals(1, repository.uploads.size)
    }

    @Test fun otherOperatorsFlowIsNotExposedAndNoUploadOccurs() = runTest {
        repository.flow = flow().copy(operatorId = 99)
        val model = model(); advanceUntilIdle()
        assertNull(model.state.value.flow)
        assertNull(model.state.value.requirement)
        assertFalse(model.state.value.canCapture)
        assertTrue(repository.uploads.isEmpty())
    }

    @Test fun finalSubmissionPendingPreventsNewEvidence() = runTest {
        store.pending = true
        val model = model(); advanceUntilIdle()
        model.captureQr("OPER-123"); advanceUntilIdle()
        assertNull(store.draft)
        assertTrue(repository.uploads.isEmpty())
    }

    @Test fun successfulResponseWithDifferentCaptureIsNotTreatedAsConfirmation() = runTest {
        val model = model(); advanceUntilIdle()
        model.captureQr("OPER-123"); advanceUntilIdle()
        repository.onUpload = { RequiredEvidence(901, EvidenceType.QR_CODE, null, "OPER-OTHER", it.capturedAt) }
        model.upload(); advanceUntilIdle()
        assertTrue(store.draft!!.uploadPending)
        assertTrue(model.state.value.requirement!!.evidence.isEmpty())
        assertNotNull(model.state.value.error)
    }

    @Test fun selectedQrImageIsReviewedBeforeRegisteringTheDecodedCode() = runTest {
        val model = model(); advanceUntilIdle()
        model.captureQrImage("content://picker/qr.png"); advanceUntilIdle()
        assertEquals(listOf("content://picker/qr.png"), imageRequests)
        assertEquals("OPER-IMAGEN-01", store.draft?.textValue)
        assertTrue(repository.uploads.isEmpty())
        model.upload(); advanceUntilIdle()
        assertEquals("OPER-IMAGEN-01", repository.uploads.single().textValue)
        assertEquals(EvidenceType.QR_CODE, repository.uploads.single().type)
        assertEquals(1, model.state.value.requirement!!.evidence.size)
    }

    @Test fun wrongPrefixFromImagePreservesThePreviousCapture() = runTest {
        val model = model(); advanceUntilIdle()
        model.captureQr("OPER-ORIGINAL"); advanceUntilIdle()
        val original = store.draft
        imageValue = "OTHER-INVALID"
        model.captureQrImage("content://picker/wrong.png"); advanceUntilIdle()
        assertEquals(original, store.draft)
        assertTrue(repository.uploads.isEmpty())
        assertNotNull(model.state.value.error)
        assertTrue(model.state.value.canCapture)
    }

    @Test fun unreadableImageDoesNotReplaceExistingCaptureOrCountAsEvidence() = runTest {
        val model = model(); advanceUntilIdle()
        model.captureQr("OPER-ORIGINAL"); advanceUntilIdle()
        val original = store.draft
        imageFailure = OperationsFailure(OperationsFailureKind.INVALID_REQUEST, "No contiene un QR legible")
        model.captureQrImage("content://picker/blank.png"); advanceUntilIdle()
        assertEquals(original, store.draft)
        assertTrue(model.state.value.requirement!!.evidence.isEmpty())
        assertTrue(repository.uploads.isEmpty())
        assertEquals("No contiene un QR legible", model.state.value.error)
    }

    @Test fun pendingUploadCannotBeReplacedByAnImportedImage() = runTest {
        val model = model(); advanceUntilIdle()
        model.captureQrImage("content://picker/qr.png"); advanceUntilIdle()
        repository.onUpload = { throw OperationsFailure(OperationsFailureKind.SERVER, "Sin respuesta") }
        model.upload(); advanceUntilIdle()
        val original = store.draft
        model.captureQrImage("content://picker/another.png"); advanceUntilIdle()
        assertEquals(1, imageRequests.size)
        assertEquals(original, store.draft)
        assertTrue(store.draft!!.uploadPending)
    }

    @Test fun imageResultWaitsForInitialLoadAndUsesTheSameValidation() = runTest {
        val model = model()
        model.captureQrImage("content://picker/qr.png")
        advanceUntilIdle()
        assertEquals("OPER-IMAGEN-01", store.draft?.textValue)
        assertFalse(model.state.value.busy)
        assertTrue(repository.uploads.isEmpty())
    }

    @Test fun anotherOperatorsRequirementNeverReadsTheSelectedImage() = runTest {
        repository.flow = flow().copy(operatorId = 99)
        val model = model(); advanceUntilIdle()
        model.captureQrImage("content://picker/qr.png"); advanceUntilIdle()
        assertTrue(imageRequests.isEmpty())
        assertNull(store.draft)
        assertNull(model.state.value.requirement)
    }

    private class FakeStore : StructuredEvidenceStore {
        var draft: StructuredEvidenceDraft? = null
        var pending = false
        override suspend fun hasPendingSubmission(userId: Long, obligationId: Long) = pending
        override suspend fun structuredDraft(userId: Long, executionId: Long, requirementId: Long) = draft
        override suspend fun saveStructuredDraft(userId: Long, draft: StructuredEvidenceDraft) { this.draft = draft }
        override suspend fun removeStructuredDraft(userId: Long, draft: StructuredEvidenceDraft) { this.draft = null }
    }

    private class FakeRepository : OperationsRepository {
        var flow = flow()
        val uploads = mutableListOf<StructuredEvidenceDraft>()
        var onUpload: suspend (StructuredEvidenceDraft) -> RequiredEvidence = { record(it) }
        fun record(draft: StructuredEvidenceDraft): RequiredEvidence {
            val evidence = RequiredEvidence(901, draft.type, null, draft.textValue, draft.capturedAt,
                latitude = draft.location?.latitude, longitude = draft.location?.longitude, accuracyMeters = draft.location?.accuracyMeters)
            flow = flow.copy(version = flow.version + 1, evidenceRequirements = flow.evidenceRequirements.map {
                it.copy(evidence = it.evidence + evidence)
            })
            return evidence
        }
        override suspend fun currentExecution(obligationId: Long) = flow
        override suspend fun uploadStructuredEvidence(draft: StructuredEvidenceDraft): RequiredEvidence {
            uploads.add(draft); return onUpload(draft)
        }
        override suspend fun executionHistory(query: ExecutionHistoryQuery, cursor: String?): ExecutionHistoryPage = error("No usado")
        override suspend fun assignedObligations(): List<AssignedObligation> = error("No usado")
        override suspend fun obligationDetail(id: Long): ObligationDetail = error("No usado")
        override suspend fun startExecution(obligationId: Long, idempotencyKey: String): ExecutionFlow = error("No usado")
        override suspend fun updateStep(executionId: Long, stepId: Long, completed: Boolean, expectedVersion: Long): ExecutionFlow = error("No usado")
        override suspend fun submitExecution(submission: PendingExecutionSubmission): ExecutionFlow = error("No usado")
        override suspend fun reportException(report: PendingExceptionReport): ExecutionFlow = error("No usado")
        override suspend fun uploadPhoto(upload: PhotoUpload): Long = error("No usado")
        override suspend fun photoContent(evidenceId: Long): PrivatePhoto = error("No usado")
        override suspend fun registerExecution(obligationId: Long, result: ExecutionResult, notes: String?): ExecutionRecord = error("No usado")
        override suspend fun execution(id: Long): ExecutionRecord = error("No usado")
        override suspend fun evidence(id: Long): List<EvidenceRecord> = error("No usado")
        override suspend fun addEvidence(executionId: Long, type: EvidenceType, url: String, description: String?): EvidenceRecord = error("No usado")
    }

    companion object {
        private fun flow(type: EvidenceType = EvidenceType.QR_CODE) = ExecutionFlow(310, 120, 42,
            ExecutionState.IN_PROGRESS, "IN_PROGRESS", null, "2026-11-25T20:00:00Z", null,
            "2026-11-25T19:00:00Z", "2026-11-26T03:00:00Z", "America/Lima", 3, true, null,
            emptyList(), emptyList(), listOf(ExecutionEvidenceRequirement(802, type, true, 1, 1, 1,
                if (type == EvidenceType.QR_CODE) mapOf("expectedPrefix" to "OPER-") else mapOf("maxAccuracyMeters" to "100"), emptyList())))
    }
}
