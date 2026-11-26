package com.opervia.example.serviceCompliance.features.operations.presentation.viewmodel

import com.opervia.example.serviceCompliance.features.operations.domain.model.*
import com.opervia.example.serviceCompliance.features.operations.domain.repository.OperationsRepository
import java.time.LocalDate
import kotlinx.coroutines.*
import kotlinx.coroutines.test.*
import org.junit.*
import org.junit.Assert.*

@OptIn(ExperimentalCoroutinesApi::class)
class HistoryViewModelTest {
    private lateinit var repository: FakeRepository
    @Before fun setUp() { Dispatchers.setMain(StandardTestDispatcher()); repository = FakeRepository() }
    @After fun tearDown() { Dispatchers.resetMain() }
    private fun model() = HistoryViewModel(repository) { LocalDate.of(2026, 11, 25) }

    @Test fun initialLoadUsesTheAuthenticatedHistoryAndLimaPeriod() = runTest {
        val model = model(); advanceUntilIdle()
        assertEquals(LocalDate.of(2026, 11, 25), repository.requests.single().first.fromDate)
        assertEquals(LocalDate.of(2026, 11, 25), repository.requests.single().first.toDate)
        assertEquals(listOf(1L), model.state.value.items.map { it.id })
        assertFalse(model.state.value.loading)
    }

    @Test fun sevenDaysAndReviewFilterAreSentToServerAndClearOldRows() = runTest {
        val model = model(); advanceUntilIdle()
        model.setPeriod(HistoryPeriod.SEVEN_DAYS)
        assertTrue(model.state.value.items.isEmpty())
        advanceUntilIdle()
        assertEquals(LocalDate.of(2026, 11, 19), repository.requests.last().first.fromDate)
        model.setReviewStatus(HistoryReviewStatus.COMPLIANT)
        assertTrue(model.state.value.items.isEmpty())
        advanceUntilIdle()
        assertEquals(HistoryReviewStatus.COMPLIANT, repository.requests.last().first.reviewStatus)
    }

    @Test fun searchIsDebouncedAndOnlyTheLatestTextIsRequested() = runTest {
        val model = model(); advanceUntilIdle()
        model.setQuery("Rec"); advanceTimeBy(100)
        model.setQuery("Recepción"); advanceTimeBy(299); runCurrent()
        assertEquals(1, repository.requests.size)
        advanceUntilIdle()
        assertEquals("Recepción", repository.requests.last().first.query)
        assertEquals(2, repository.requests.size)
    }

    @Test fun lateResponseFromCancelledSearchNeverReplacesCurrentFilterResults() = runTest {
        val model = model(); advanceUntilIdle()
        val oldResponse = CompletableDeferred<ExecutionHistoryPage>()
        repository.onQuery = { query, _ ->
            if (query.query == "old") withContext(NonCancellable) { oldResponse.await() } else page(2)
        }
        model.setQuery("old"); advanceTimeBy(300); runCurrent()
        model.setQuery("new"); advanceTimeBy(300); runCurrent()
        assertEquals(listOf(2L), model.state.value.items.map { it.id })
        oldResponse.complete(page(99)); advanceUntilIdle()
        assertEquals(listOf(2L), model.state.value.items.map { it.id })
        assertFalse(model.state.value.loading)
    }

    @Test fun loadMoreReusesTheCursorPreventsDoubleClickAndDeduplicatesIds() = runTest {
        repository.onQuery = { _, _ -> page(1).copy(hasMore = true, nextCursor = "next-page") }
        val model = model(); advanceUntilIdle()
        val response = CompletableDeferred<ExecutionHistoryPage>()
        repository.onQuery = { _, cursor -> assertEquals("next-page", cursor); response.await() }
        model.loadMore(); model.loadMore(); runCurrent()
        assertEquals(2, repository.requests.size)
        assertTrue(model.state.value.loadingMore)
        response.complete(page(1, 2)); advanceUntilIdle()
        assertEquals(listOf(1L, 2L), model.state.value.items.map { it.id })
        assertFalse(model.state.value.hasMore)
    }

    @Test fun failedRefreshKeepsOnlyPreviouslyConfirmedRowsWithStaleWarning() = runTest {
        val model = model(); advanceUntilIdle()
        repository.onQuery = { _, _ -> throw OperationsFailure(OperationsFailureKind.SERVER, "Sin conexión") }
        model.refresh(); advanceUntilIdle()
        assertEquals(listOf(1L), model.state.value.items.map { it.id })
        assertTrue(model.state.value.showingStaleData)
        repository.onQuery = { _, _ -> page(2) }
        model.refresh(); advanceUntilIdle()
        assertEquals(listOf(2L), model.state.value.items.map { it.id })
        assertFalse(model.state.value.showingStaleData)
    }

    @Test fun failedNewFilterDoesNotShowRowsFromThePreviousFilter() = runTest {
        val model = model(); advanceUntilIdle()
        repository.onQuery = { _, _ -> throw OperationsFailure(OperationsFailureKind.SERVER, "Sin conexión") }
        model.setReviewStatus(HistoryReviewStatus.COMPLIANT); advanceUntilIdle()
        assertTrue(model.state.value.items.isEmpty())
        assertFalse(model.state.value.showingStaleData)
        assertNull(model.state.value.asOf)
    }

    @Test fun revokedSessionOrPermissionsClearTheVisibleHistory() = runTest {
        val model = model(); advanceUntilIdle()
        repository.onQuery = { _, _ -> throw OperationsFailure(OperationsFailureKind.UNAUTHENTICATED, "Sesión vencida") }
        model.refresh(); advanceUntilIdle()
        assertTrue(model.state.value.items.isEmpty())
        assertTrue(model.state.value.sessionExpired)
        assertFalse(model.state.value.showingStaleData)
    }

    @Test fun pageFromAnotherCutoffCannotBeAppended() = runTest {
        repository.onQuery = { _, _ -> page(1).copy(hasMore = true, nextCursor = "next-page") }
        val model = model(); advanceUntilIdle()
        repository.onQuery = { _, _ -> page(2).copy(asOf = "2026-11-25T21:00:00Z") }
        model.loadMore(); advanceUntilIdle()
        assertEquals(listOf(1L), model.state.value.items.map { it.id })
        assertTrue(model.state.value.showingStaleData)
        assertNotNull(model.state.value.error)
    }

    @Test fun changingFiltersCancelsPaginationWithoutMixingResults() = runTest {
        repository.onQuery = { _, _ -> page(1).copy(hasMore = true, nextCursor = "next-page") }
        val model = model(); advanceUntilIdle()
        val oldPage = CompletableDeferred<ExecutionHistoryPage>()
        repository.onQuery = { query, cursor ->
            if (cursor != null) withContext(NonCancellable) { oldPage.await() }
            else if (query.reviewStatus == HistoryReviewStatus.NON_COMPLIANT) page(3) else page(1)
        }
        model.loadMore(); runCurrent()
        model.setReviewStatus(HistoryReviewStatus.NON_COMPLIANT); runCurrent()
        oldPage.complete(page(2)); advanceUntilIdle()
        assertEquals(listOf(3L), model.state.value.items.map { it.id })
        assertFalse(model.state.value.loadingMore)
    }

    private class FakeRepository : OperationsRepository {
        val requests = mutableListOf<Pair<ExecutionHistoryQuery, String?>>()
        var onQuery: suspend (ExecutionHistoryQuery, String?) -> ExecutionHistoryPage = { _, _ -> page(1) }
        override suspend fun executionHistory(query: ExecutionHistoryQuery, cursor: String?): ExecutionHistoryPage {
            requests.add(query to cursor); return onQuery(query, cursor)
        }
        override suspend fun assignedObligations(): List<AssignedObligation> = error("Historial no consulta el listado")
        override suspend fun obligationDetail(id: Long): ObligationDetail = error("No usado")
        override suspend fun currentExecution(obligationId: Long): ExecutionFlow? = error("No usado")
        override suspend fun startExecution(obligationId: Long, idempotencyKey: String): ExecutionFlow = error("No usado")
        override suspend fun updateStep(executionId: Long, stepId: Long, completed: Boolean, expectedVersion: Long): ExecutionFlow = error("No usado")
        override suspend fun submitExecution(submission: PendingExecutionSubmission): ExecutionFlow = error("No usado")
        override suspend fun reportException(report: PendingExceptionReport): ExecutionFlow = error("No usado")
        override suspend fun uploadPhoto(upload: PhotoUpload): Long = error("No usado")
        override suspend fun uploadStructuredEvidence(draft: StructuredEvidenceDraft): RequiredEvidence = error("No usado")
        override suspend fun photoContent(evidenceId: Long): PrivatePhoto = error("No usado")
        override suspend fun registerExecution(obligationId: Long, result: ExecutionResult, notes: String?): ExecutionRecord = error("No usado")
        override suspend fun execution(id: Long): ExecutionRecord = error("No usado")
        override suspend fun evidence(id: Long): List<EvidenceRecord> = error("No usado")
        override suspend fun addEvidence(executionId: Long, type: EvidenceType, url: String, description: String?): EvidenceRecord = error("No usado")
    }
    companion object {
        private fun page(vararg ids: Long) = ExecutionHistoryPage(ids.map { id ->
            ExecutionHistoryItem(id, id, "Actividad $id", "Centro", "America/Lima", ExecutionState.SUBMITTED,
                ExecutionResult.COMPLETED, "2026-11-25T15:00:00Z", "2026-11-25T17:00:00Z", null,
                HistorySynchronizationState.SYNCED, null)
        }, null, false, "2026-11-25T20:00:00Z")
    }
}
