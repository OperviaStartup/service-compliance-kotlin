package com.opervia.example.serviceCompliance.features.operations.data

internal data class ExecutionHistoryPageDto(
    val items: List<ExecutionHistoryItemDto>, val nextCursor: String?, val hasMore: Boolean, val asOf: String
)

internal data class ExecutionHistoryItemDto(
    val id: Long, val obligationId: Long, val activity: String, val siteName: String, val timezone: String,
    val state: String, val result: String, val executedAt: String, val submittedAt: String?, val notes: String?,
    val synchronizationState: String, val complianceResult: HistoryComplianceResultDto?
)

internal data class HistoryComplianceResultDto(
    val id: Long, val caseId: Long, val outcome: String, val decisionVersion: Long,
    val decidedAt: String, val explanation: String
)
