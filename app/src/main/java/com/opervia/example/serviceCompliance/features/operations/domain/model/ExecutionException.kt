package com.opervia.example.serviceCompliance.features.operations.domain.model

enum class ExceptionReason { AREA_CLOSED, MISSING_SUPPLIES, SAFETY_RISK, OTHER }

data class ExecutionException(
    val id: Long,
    val stepId: Long?,
    val reason: ExceptionReason,
    val description: String,
    val reportedBy: Long,
    val reportedAt: String,
    val operationId: String,
    val evidenceIds: List<Long>
)

/** Se conserva la solicitud exacta hasta confirmar o recibir un rechazo definitivo. */
data class PendingExceptionReport(
    val executionId: Long,
    val idempotencyKey: String,
    val operationId: String,
    val reason: ExceptionReason,
    val description: String,
    val stepId: Long?,
    val evidenceIds: List<Long>,
    val expectedVersion: Long
) {
    fun matches(report: ExecutionException): Boolean = operationId == report.operationId &&
        reason == report.reason && description == report.description && stepId == report.stepId &&
        evidenceIds.sorted() == report.evidenceIds.sorted()
}
