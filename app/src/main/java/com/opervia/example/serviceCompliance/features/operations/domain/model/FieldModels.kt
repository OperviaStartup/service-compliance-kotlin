package com.opervia.example.serviceCompliance.features.operations.domain.model

/** Read model returned by GET /api/v1/obligations. */
data class AssignedObligation(
    val id: Long,
    val title: String,
    val description: String,
    val siteName: String,
    val status: String,
    val dueAt: String
)

enum class ExecutionResult { COMPLETED, PARTIAL, BLOCKED }

/** Domain-facing representation of the execution record persisted by the API. */
data class ExecutionRecord(
    val id: Long,
    val obligationId: Long,
    val operatorId: Long,
    val result: ExecutionResult,
    val notes: String?,
    val executedAt: String
)

enum class EvidenceType { PHOTO, QR_CODE, LOCATION, SIGNATURE }

enum class OperationsFailureKind { INVALID_REQUEST, UNAUTHENTICATED, FORBIDDEN, NOT_FOUND, CONFLICT, SERVER, UNKNOWN }

class OperationsFailure(val kind: OperationsFailureKind, message: String) : Exception(message)

data class EvidenceRecord(
    val id: Long,
    val executionId: Long,
    val type: EvidenceType,
    val url: String,
    val description: String?,
    val createdAt: String
)
