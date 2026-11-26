package com.opervia.example.serviceCompliance.features.operations.presentation.viewmodel

import com.opervia.example.serviceCompliance.features.operations.domain.model.*
import com.opervia.example.serviceCompliance.features.operations.domain.repository.FieldOperationsStore
import com.opervia.example.serviceCompliance.features.operations.domain.repository.OperationsRepository
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.*
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class FieldExecutionViewModelTest {
    private lateinit var repository: FakeRepository
    private lateinit var store: FakeStore

    @Before
    fun setUp() {
        Dispatchers.setMain(StandardTestDispatcher())
        repository = FakeRepository()
        store = FakeStore()
    }

    @After
    fun tearDown() { Dispatchers.resetMain() }

    private fun model() = FieldViewModel(repository, 120, null, store, 42)

    @Test
    fun openingTheDetailDoesNotStartOrRegisterAnExecution() = runTest {
        val model = model()
        advanceUntilIdle()
        assertTrue(model.state.value.flowConfirmed)
        assertNull(model.state.value.flow)
        assertTrue(repository.startKeys.isEmpty())
        assertTrue(store.records.isEmpty())
    }

    @Test
    fun startWaitsForServerAndPreventsDoubleClick() = runTest {
        val model = model()
        advanceUntilIdle()
        val response = CompletableDeferred<ExecutionFlow>()
        repository.onStart = { response.await() }
        var navigations = 0
        model.start { navigations++ }
        model.start { navigations++ }
        runCurrent()
        assertEquals(0, navigations)
        assertNull(model.state.value.flow)
        assertTrue(model.state.value.busy)
        response.complete(flow())
        advanceUntilIdle()
        assertEquals(1, repository.startKeys.size)
        assertEquals(1, navigations)
        assertEquals(310L, model.state.value.flow?.id)
        assertTrue(store.records.isEmpty())
    }

    @Test
    fun reopeningRecoversSavedStepsWithoutStartingAgain() = runTest {
        repository.current = flow(done = true, version = 3)
        val model = model()
        advanceUntilIdle()
        model.start {}
        advanceUntilIdle()
        assertTrue(model.state.value.flow!!.steps.single().completed)
        assertEquals(3L, model.state.value.flow!!.version)
        assertTrue(repository.startKeys.isEmpty())
        assertTrue(store.records.isEmpty())
    }

    @Test
    fun checkboxChangesOnlyAfterConfirmationAndUsesServerVersion() = runTest {
        repository.current = flow(version = 4)
        val model = model()
        advanceUntilIdle()
        val response = CompletableDeferred<ExecutionFlow>()
        repository.onStep = { _, _ -> response.await() }
        model.updateStep(701, true)
        runCurrent()
        assertFalse(model.state.value.flow!!.steps.single().completed)
        assertFalse(model.state.value.canEditSteps)
        assertEquals(4L, repository.stepVersions.single())
        response.complete(flow(done = true, version = 5))
        advanceUntilIdle()
        assertTrue(model.state.value.flow!!.steps.single().completed)
        assertEquals(5L, model.state.value.flow!!.version)
    }

    @Test
    fun versionConflictRefreshesProgressWithoutPermanentLock() = runTest {
        repository.current = flow(version = 4)
        val model = model()
        advanceUntilIdle()
        repository.onStep = { _, _ ->
            repository.current = flow(version = 6)
            throw OperationsFailure(OperationsFailureKind.CONFLICT, "La ejecución cambió", "VERSION_CONFLICT")
        }
        model.updateStep(701, true)
        advanceUntilIdle()
        assertEquals(6L, model.state.value.flow!!.version)
        assertTrue(model.state.value.canEditSteps)
        assertFalse(model.state.value.submissionUncertain)
        assertEquals("La ejecución cambió", model.state.value.error)
    }

    @Test
    fun lostStepResponseRecoversConfirmedValue() = runTest {
        repository.current = flow()
        val model = model()
        advanceUntilIdle()
        repository.onStep = { _, _ ->
            repository.current = flow(done = true, version = 1)
            throw OperationsFailure(OperationsFailureKind.SERVER, "Respuesta perdida")
        }
        model.updateStep(701, true)
        advanceUntilIdle()
        assertTrue(model.state.value.flow!!.steps.single().completed)
        assertNull(model.state.value.error)
        assertEquals(1, repository.stepVersions.size)
    }

    @Test
    fun failedStartKeepsTheSameKeyAcrossViewModels() = runTest {
        val first = model()
        advanceUntilIdle()
        repository.onStart = { throw OperationsFailure(OperationsFailureKind.SERVER, "Respuesta perdida") }
        first.start {}
        advanceUntilIdle()
        val second = model()
        advanceUntilIdle()
        repository.onStart = { flow() }
        second.start {}
        advanceUntilIdle()
        assertEquals(2, repository.startKeys.size)
        assertEquals(repository.startKeys[0], repository.startKeys[1])
        assertEquals(310L, second.state.value.flow?.id)
    }

    @Test
    fun lostStartResponseRecoversTheExecutionCreatedByServer() = runTest {
        val model = model()
        advanceUntilIdle()
        repository.onStart = {
            repository.current = flow()
            throw OperationsFailure(OperationsFailureKind.SERVER, "Respuesta perdida")
        }
        var opened = false
        model.start { opened = true }
        advanceUntilIdle()
        assertTrue(opened)
        assertEquals(310L, model.state.value.flow?.id)
        assertEquals(1, repository.startKeys.size)
        assertNull(model.state.value.error)
    }

    @Test
    fun missingPhotoPreventsSubmissionWithoutInventingCompletion() = runTest {
        repository.current = flow(done = true, photos = listOf(
            ExecutionPhotoRequirement(801, "Piso limpio", null, 1, null, null)))
        val model = model()
        advanceUntilIdle()
        model.submitFlow("Terminado")
        advanceUntilIdle()
        assertFalse(model.state.value.flow!!.canSubmit)
        assertTrue(repository.submissions.isEmpty())
        assertTrue(store.records.isEmpty())
    }

    @Test
    fun submittedRecordUsesServerResultAndSubmissionTime() = runTest {
        repository.current = flow(done = true)
        val model = model()
        advanceUntilIdle()
        repository.onSubmit = { flow(done = true).copy(state = ExecutionState.SUBMITTED,
            obligationStatus = "SUBMITTED", canEdit = false, result = ExecutionResult.COMPLETED,
            submittedAt = "2026-11-25T19:00:00Z", notes = it.notes) }
        model.submitFlow("  Revisado  ")
        advanceUntilIdle()
        assertEquals("Revisado", repository.submissions.single().notes)
        assertEquals("2026-11-25T19:00:00Z", store.records.single().executedAt)
        assertEquals(ExecutionResult.COMPLETED, store.records.single().result)
        assertNull(store.pending)
    }

    @Test
    fun uncertainSubmissionSurvivesReopeningAndReusesExactRequest() = runTest {
        repository.current = flow(done = true, version = 3)
        val first = model()
        advanceUntilIdle()
        repository.onSubmit = { throw OperationsFailure(OperationsFailureKind.SERVER, "Respuesta perdida") }
        first.submitFlow("Notas originales")
        advanceUntilIdle()
        assertNotNull(store.pending)
        assertTrue(store.records.isEmpty())
        val second = model()
        advanceUntilIdle()
        assertFalse(second.state.value.canEditSteps)
        repository.onSubmit = { flow(done = true).copy(state = ExecutionState.SUBMITTED,
            obligationStatus = "SUBMITTED", canEdit = false, result = ExecutionResult.COMPLETED,
            submittedAt = "2026-11-25T19:00:00Z", notes = it.notes) }
        second.submitFlow("No debe reemplazar las notas")
        advanceUntilIdle()
        assertEquals(repository.submissions[0], repository.submissions[1])
        assertNull(store.pending)
        assertEquals(1, store.records.size)
    }

    @Test
    fun definiteRejectionClearsPendingRequestAndAllowsCorrection() = runTest {
        repository.current = flow(done = true)
        val model = model()
        advanceUntilIdle()
        repository.onSubmit = { throw OperationsFailure(OperationsFailureKind.INVALID_REQUEST, "Evidencia incompleta") }
        model.submitFlow(null)
        advanceUntilIdle()
        assertNull(store.pending)
        assertTrue(model.state.value.canEditSteps)
        assertEquals("Evidencia incompleta", model.state.value.error)
        assertFalse(model.state.value.submissionUncertain)
    }

    @Test
    fun overdueExecutionKeepsProgressReadOnly() = runTest {
        repository.current = flow(done = true).copy(canEdit = false, obligationStatus = "OVERDUE")
        val model = model()
        advanceUntilIdle()
        model.updateStep(701, false)
        model.submitFlow(null)
        advanceUntilIdle()
        assertTrue(model.state.value.flow!!.steps.single().completed)
        assertFalse(model.state.value.canEditSteps)
        assertTrue(repository.stepVersions.isEmpty())
        assertTrue(repository.submissions.isEmpty())
    }

    @Test
    fun revokedAccessRemovesDetailAndProgress() = runTest {
        repository.current = flow()
        val model = model()
        advanceUntilIdle()
        repository.readFailure = OperationsFailure(OperationsFailureKind.UNAUTHENTICATED, "Sesión vencida")
        model.refresh()
        advanceUntilIdle()
        assertNull(model.state.value.detail)
        assertNull(model.state.value.flow)
        assertTrue(model.state.value.sessionExpired)
        repository.readFailure = null
        model.refresh()
        advanceUntilIdle()
        assertFalse(model.state.value.sessionExpired)
        assertTrue(model.state.value.canEditSteps)
    }

    @Test
    fun impedimentKeepsStepsAndPhotosWithoutCompletingOrAddingHistory() = runTest {
        val photo = ExecutionPhotoRequirement(801, "Piso", null, 1, 901, "/api/v1/evidence/901/content")
        repository.current = flow(version = 4, photos = listOf(photo))
        val model = model()
        advanceUntilIdle()
        model.reportException(ExceptionReason.MISSING_SUPPLIES, "  Faltan paños  ", 701, listOf(901, 901))
        advanceUntilIdle()
        val report = repository.reports.single()
        assertEquals(4L, report.expectedVersion)
        assertEquals("Faltan paños", report.description)
        assertEquals(listOf(901L), report.evidenceIds)
        assertEquals(701L, report.stepId)
        assertEquals(ExecutionState.IN_PROGRESS, model.state.value.flow!!.state)
        assertFalse(model.state.value.flow!!.steps.single().completed)
        assertEquals(photo, model.state.value.flow!!.photoRequirements.single())
        assertFalse(model.state.value.flow!!.canSubmit)
        assertNull(model.state.value.execution)
        assertTrue(store.records.isEmpty())
        assertNotNull(model.state.value.reportedException)
        assertNull(store.pendingReport)
        assertTrue(model.state.value.canEditSteps)
    }

    @Test
    fun invalidDetailOrReferencesNeverSendAnImpediment() = runTest {
        repository.current = flow()
        val model = model()
        advanceUntilIdle()
        model.reportException(ExceptionReason.OTHER, " ", null, emptyList())
        advanceUntilIdle()
        model.reportException(ExceptionReason.OTHER, "x".repeat(2001), null, emptyList())
        advanceUntilIdle()
        model.reportException(ExceptionReason.OTHER, "Detalle", 999, emptyList())
        advanceUntilIdle()
        model.reportException(ExceptionReason.OTHER, "Detalle", null, listOf(999))
        advanceUntilIdle()
        assertTrue(repository.reports.isEmpty())
        assertNull(store.pendingReport)
    }

    @Test
    fun serverConfirmedImpedimentIsRecoveredWhenResponseIsLost() = runTest {
        repository.current = flow()
        val model = model()
        advanceUntilIdle()
        repository.onReport = { report ->
            repository.current = flow(version = 1).copy(exceptions = listOf(confirmed(report)))
            throw OperationsFailure(OperationsFailureKind.SERVER, "Respuesta perdida")
        }
        model.reportException(ExceptionReason.AREA_CLOSED, "Puerta cerrada", null, emptyList())
        advanceUntilIdle()
        assertEquals(1, repository.reports.size)
        assertEquals(601L, model.state.value.reportedException?.id)
        assertNull(store.pendingReport)
        assertNull(model.state.value.error)
        assertTrue(store.records.isEmpty())
    }

    @Test
    fun uncertainImpedimentSurvivesReopeningAndReplaysExactRequest() = runTest {
        repository.current = flow(version = 4)
        val first = model()
        advanceUntilIdle()
        repository.onReport = { throw OperationsFailure(OperationsFailureKind.SERVER, "Sin conexión") }
        first.reportException(ExceptionReason.SAFETY_RISK, "Cable expuesto", 701, emptyList())
        advanceUntilIdle()
        assertNotNull(store.pendingReport)
        assertNull(first.state.value.reportedException)
        assertFalse(first.state.value.canEditSteps)
        first.updateStep(701, true)
        first.submitFlow(null)
        advanceUntilIdle()
        assertTrue(repository.stepVersions.isEmpty())
        assertTrue(repository.submissions.isEmpty())
        val second = model()
        advanceUntilIdle()
        repository.onReport = { report -> flow(version = 5).copy(exceptions = listOf(confirmed(report))) }
        second.reportException(ExceptionReason.OTHER, "No cambiar el reporte", null, emptyList())
        advanceUntilIdle()
        assertEquals(repository.reports[0], repository.reports[1])
        assertNull(store.pendingReport)
        assertEquals("Cable expuesto", second.state.value.reportedException?.description)
        assertTrue(second.state.value.canEditSteps)
    }

    @Test
    fun refreshReconcilesAcceptedImpedimentAfterProcessRestart() = runTest {
        repository.current = flow()
        val first = model()
        advanceUntilIdle()
        repository.onReport = { throw OperationsFailure(OperationsFailureKind.SERVER, "Sin respuesta") }
        first.reportException(ExceptionReason.OTHER, "Evento en el área", null, emptyList())
        advanceUntilIdle()
        val saved = requireNotNull(store.pendingReport)
        repository.current = flow(version = 1).copy(exceptions = listOf(confirmed(saved)))
        val reopened = model()
        advanceUntilIdle()
        assertNull(store.pendingReport)
        assertEquals(saved.operationId, reopened.state.value.reportedException?.operationId)
        assertEquals(1, repository.reports.size)
    }

    @Test
    fun versionConflictClearsRejectedReportAndRecoversLatestProgress() = runTest {
        repository.current = flow(version = 3)
        val model = model()
        advanceUntilIdle()
        repository.onReport = {
            repository.current = flow(done = true, version = 4)
            throw OperationsFailure(OperationsFailureKind.CONFLICT, "El avance cambió", "VERSION_CONFLICT")
        }
        model.reportException(ExceptionReason.AREA_CLOSED, "Puerta cerrada", null, emptyList())
        advanceUntilIdle()
        assertNull(store.pendingReport)
        assertEquals(4L, model.state.value.flow?.version)
        assertTrue(model.state.value.flow!!.steps.single().completed)
        assertTrue(model.state.value.canEditSteps)
        assertEquals("El avance cambió", model.state.value.error)
        assertNull(model.state.value.reportedException)
    }

    @Test
    fun missingOperationInSuccessResponseDoesNotInventConfirmation() = runTest {
        repository.current = flow()
        val model = model()
        advanceUntilIdle()
        repository.onReport = { flow() }
        model.reportException(ExceptionReason.OTHER, "Detalle", null, emptyList())
        advanceUntilIdle()
        assertNotNull(store.pendingReport)
        assertNull(model.state.value.reportedException)
        assertNotNull(model.state.value.error)
        assertTrue(store.records.isEmpty())
    }

    @Test
    fun newReportIsRejectedAfterDeadlineButPendingReplayCanBeConfirmed() = runTest {
        repository.current = flow().copy(canEdit = false, obligationStatus = "OVERDUE")
        val model = model()
        advanceUntilIdle()
        model.reportException(ExceptionReason.AREA_CLOSED, "Puerta cerrada", null, emptyList())
        advanceUntilIdle()
        assertTrue(repository.reports.isEmpty())
        val pending = PendingExceptionReport(310, "exception-test-key", "operation-test", ExceptionReason.AREA_CLOSED,
            "Puerta cerrada", null, emptyList(), 0)
        store.pendingReport = pending
        model.refresh()
        advanceUntilIdle()
        repository.onReport = { requireNotNull(repository.current).copy(exceptions = listOf(confirmed(it))) }
        model.reportException(ExceptionReason.OTHER, "Ignorado", null, emptyList())
        advanceUntilIdle()
        assertEquals(pending, repository.reports.single())
        assertEquals("OVERDUE", model.state.value.flow?.obligationStatus)
        assertNotNull(model.state.value.reportedException)
        assertTrue(store.records.isEmpty())
    }

    @Test
    fun finalSubmissionPendingPreventsReportingAnotherImpediment() = runTest {
        repository.current = flow(done = true)
        store.pending = PendingExecutionSubmission(310, "submit-test-key", 0, null)
        val model = model()
        advanceUntilIdle()
        model.reportException(ExceptionReason.OTHER, "Detalle", null, emptyList())
        advanceUntilIdle()
        assertTrue(repository.reports.isEmpty())
        assertNull(store.pendingReport)
    }

    @Test
    fun reportWaitsForServerAndRejectsDoubleClick() = runTest {
        repository.current = flow()
        val model = model()
        advanceUntilIdle()
        val response = CompletableDeferred<ExecutionFlow>()
        repository.onReport = { response.await() }
        model.reportException(ExceptionReason.OTHER, "Detalle", null, emptyList())
        model.reportException(ExceptionReason.OTHER, "Duplicado", null, emptyList())
        runCurrent()
        assertNull(model.state.value.reportedException)
        assertTrue(model.state.value.busy)
        response.complete(flow(version = 1).copy(exceptions = listOf(confirmed(repository.reports.single()))))
        advanceUntilIdle()
        assertEquals(1, repository.reports.size)
        assertNotNull(model.state.value.reportedException)
    }

    private class FakeStore : FieldOperationsStore {
        var pendingReport: PendingExceptionReport? = null
        override suspend fun pendingException(userId: Long, obligationId: Long) = pendingReport
        override suspend fun saveException(userId: Long, obligationId: Long, report: PendingExceptionReport) { pendingReport = report }
        override suspend fun clearException(userId: Long, obligationId: Long) { pendingReport = null }
        val records = mutableListOf<ExecutionRecord>()
        var pending: PendingExecutionSubmission? = null
        private val keys = mutableMapOf<Pair<Long, Long>, String>()
        override suspend fun startKey(userId: Long, obligationId: Long) =
            keys.getOrPut(userId to obligationId) { "test-start-$userId-$obligationId" }
        override suspend fun saveExecution(userId: Long, execution: ExecutionRecord, obligation: AssignedObligation?) {
            records.removeAll { it.id == execution.id }
            records.add(execution)
        }
        override suspend fun pendingSubmission(userId: Long, obligationId: Long) = pending
        override suspend fun saveSubmission(userId: Long, obligationId: Long, submission: PendingExecutionSubmission) {
            pending = submission
        }
        override suspend fun clearSubmission(userId: Long, obligationId: Long) { pending = null }
    }

    private class FakeRepository : OperationsRepository {
        val reports = mutableListOf<PendingExceptionReport>()
        var onReport: suspend (PendingExceptionReport) -> ExecutionFlow = { report ->
            requireNotNull(current).copy(version = report.expectedVersion + 1,
                exceptions = requireNotNull(current).exceptions + confirmed(report))
        }
        override suspend fun uploadStructuredEvidence(draft: StructuredEvidenceDraft): RequiredEvidence = error("No usado")
        override suspend fun reportException(report: PendingExceptionReport): ExecutionFlow {
            reports.add(report)
            return onReport(report).also { current = it }
        }
        override suspend fun uploadPhoto(upload: PhotoUpload): Long = error("No usado")
        override suspend fun photoContent(evidenceId: Long): PrivatePhoto = error("No usado")
        var current: ExecutionFlow? = null
        var readFailure: OperationsFailure? = null
        val startKeys = mutableListOf<String>()
        val stepVersions = mutableListOf<Long>()
        val submissions = mutableListOf<PendingExecutionSubmission>()
        var onStart: suspend () -> ExecutionFlow = { flow() }
        var onStep: suspend (Boolean, Long) -> ExecutionFlow = { completed, version -> flow(completed, version + 1) }
        var onSubmit: suspend (PendingExecutionSubmission) -> ExecutionFlow = { error("Configura la respuesta de envío") }
        override suspend fun obligationDetail(id: Long): ObligationDetail {
            readFailure?.let { throw it }
            return ObligationDetail(AssignedObligation(120, "Recepción", "Limpiar", "Centro", "ASSIGNED", "2026-11-25T23:00:00Z"),
                "2026-11-25T15:00:00Z", "America/Lima", emptyList(), emptyList())
        }
        override suspend fun currentExecution(obligationId: Long) = current
        override suspend fun startExecution(obligationId: Long, idempotencyKey: String): ExecutionFlow {
            startKeys.add(idempotencyKey)
            return onStart().also { current = it }
        }
        override suspend fun updateStep(executionId: Long, stepId: Long, completed: Boolean, expectedVersion: Long): ExecutionFlow {
            stepVersions.add(expectedVersion)
            return onStep(completed, expectedVersion).also { current = it }
        }
        override suspend fun submitExecution(submission: PendingExecutionSubmission): ExecutionFlow {
            submissions.add(submission)
            return onSubmit(submission).also { current = it }
        }
        override suspend fun executionHistory(query: ExecutionHistoryQuery, cursor: String?): ExecutionHistoryPage = error("No usado")
        override suspend fun assignedObligations(): List<AssignedObligation> = error("El flujo no debe recargar el listado")
        override suspend fun registerExecution(obligationId: Long, result: ExecutionResult, notes: String?): ExecutionRecord =
            error("No debe usar el envío anterior")
        override suspend fun execution(id: Long): ExecutionRecord = error("No usado")
        override suspend fun evidence(id: Long): List<EvidenceRecord> = error("No usado")
        override suspend fun addEvidence(executionId: Long, type: EvidenceType, url: String, description: String?): EvidenceRecord = error("No usado")
    }

    companion object {
        private fun confirmed(report: PendingExceptionReport) = ExecutionException(601, report.stepId, report.reason,
            report.description, 42, "2026-11-25T20:00:00Z", report.operationId, report.evidenceIds)

        private fun flow(done: Boolean = false, version: Long = 0,
                         photos: List<ExecutionPhotoRequirement> = emptyList()) = ExecutionFlow(
            id = 310, obligationId = 120, operatorId = 42, state = ExecutionState.IN_PROGRESS,
            obligationStatus = "IN_PROGRESS", result = null, startedAt = "2026-11-25T15:01:00Z",
            submittedAt = null, startsAt = "2026-11-25T15:00:00Z", dueAt = "2026-11-25T23:00:00Z",
            timezone = "America/Lima", version = version, canEdit = true, notes = null,
            steps = listOf(ExecutionStep(701, "Piso", "Limpiar", 1, done)),
            photoRequirements = photos, evidenceRequirements = emptyList())
    }
}
