package com.opervia.example.serviceCompliance.features.operations.domain.model

import java.time.Instant
import java.time.LocalDate

enum class HistoryReviewStatus { PENDING_REVIEW, COMPLIANT, EXCEPTION_ACCEPTED, NON_COMPLIANT }
enum class HistorySynchronizationState { SYNCED }

data class HistoryComplianceResult(
    val id: Long,
    val caseId: Long,
    val outcome: HistoryReviewStatus,
    val decisionVersion: Long,
    val decidedAt: String,
    val explanation: String
)

/** Hechos consultados en el servidor; el resultado operativo nunca sustituye la evaluación. */
data class ExecutionHistoryItem(
    val id: Long,
    val obligationId: Long,
    val activity: String,
    val siteName: String,
    val timezone: String,
    val state: ExecutionState,
    val result: ExecutionResult,
    val executedAt: String,
    val submittedAt: String?,
    val notes: String?,
    val synchronizationState: HistorySynchronizationState,
    val complianceResult: HistoryComplianceResult?
) {
    val recordedAt: String get() = submittedAt ?: executedAt
    val reviewStatus: HistoryReviewStatus get() = complianceResult?.outcome ?: HistoryReviewStatus.PENDING_REVIEW
}

data class ExecutionHistoryQuery(
    val fromDate: LocalDate,
    val toDate: LocalDate,
    val query: String = "",
    val reviewStatus: HistoryReviewStatus? = null
)

data class ExecutionHistoryPage(
    val items: List<ExecutionHistoryItem>,
    val nextCursor: String?,
    val hasMore: Boolean,
    val asOf: String
) {
    fun validate() {
        require(!hasMore || !nextCursor.isNullOrBlank()) { "El servidor no devolvió la siguiente página del historial." }
        Instant.parse(asOf)
    }
}
