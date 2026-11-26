package com.opervia.example.serviceCompliance.features.operations.presentation.screens

import com.opervia.example.serviceCompliance.features.operations.domain.model.*
import org.junit.Assert.*
import org.junit.Test

class HistoryEntryTest {
    @Test fun completedExecutionWithoutDecisionRemainsPendingReview() {
        val entry = record(ExecutionResult.COMPLETED).toHistoryEntry()
        assertEquals("Pendiente de revisión", entry.reviewLabel)
        assertEquals("Completada", entry.declaredResultLabel)
        assertNotEquals(HistoryReviewStatus.COMPLIANT, entry.record.reviewStatus)
    }
    @Test fun partialAndBlockedDoNotInferAnObservation() {
        for (result in listOf(ExecutionResult.PARTIAL, ExecutionResult.BLOCKED)) {
            val entry = record(result).toHistoryEntry()
            assertEquals("Pendiente de revisión", entry.reviewLabel)
            assertNotEquals(HistoryReviewStatus.NON_COMPLIANT, entry.record.reviewStatus)
        }
    }
    @Test fun compliantIsShownOnlyForAnExplicitBackendDecision() {
        val entry = record(ExecutionResult.COMPLETED, HistoryReviewStatus.COMPLIANT).toHistoryEntry()
        assertEquals("Conforme", entry.reviewLabel)
        assertEquals(HistoryReviewStatus.COMPLIANT, entry.record.reviewStatus)
        assertNotEquals(HistoryReviewStatus.PENDING_REVIEW, entry.record.reviewStatus)
    }
    @Test fun exceptionAcceptedKeepsItsOwnLabelAndFilter() {
        val entry = record(ExecutionResult.COMPLETED, HistoryReviewStatus.EXCEPTION_ACCEPTED).toHistoryEntry()
        assertEquals("Excepción aceptada", entry.reviewLabel)
        assertEquals(HistoryReviewStatus.EXCEPTION_ACCEPTED, entry.record.reviewStatus)
        assertNotEquals(HistoryReviewStatus.COMPLIANT, entry.record.reviewStatus)
    }
    @Test fun nonCompliantIsNotRewrittenByOperationalCompletion() {
        val entry = record(ExecutionResult.COMPLETED, HistoryReviewStatus.NON_COMPLIANT).toHistoryEntry()
        assertEquals("Incumplimiento", entry.reviewLabel)
        assertEquals("Completada", entry.declaredResultLabel)
        assertEquals(HistoryReviewStatus.NON_COMPLIANT, entry.record.reviewStatus)
    }
    @Test fun evaluationNeverChangesTheOperationalResult() {
        for (status in listOf(null) + HistoryReviewStatus.entries.filter { it != HistoryReviewStatus.PENDING_REVIEW }) {
            assertEquals("Completada", record(ExecutionResult.COMPLETED, status).toHistoryEntry().declaredResultLabel)
        }
    }
    private fun record(result: ExecutionResult, status: HistoryReviewStatus? = null) = ExecutionHistoryItem(
        1, 2, "Recepción", "Centro", "America/Lima", ExecutionState.LEGACY, result,
        "2026-11-25T15:00:00Z", null, "Notas", HistorySynchronizationState.SYNCED,
        status?.let { HistoryComplianceResult(3, 4, it, 1, "2026-11-25T16:00:00Z", "Evaluación explícita") })
}
