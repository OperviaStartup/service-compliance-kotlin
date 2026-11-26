package com.opervia.example.serviceCompliance.features.operations.data

internal data class ExecutionFlowDto(
    val id: Long, val obligationId: Long, val operatorId: Long,
    val state: String, val obligationStatus: String, val result: String?,
    val startedAt: String?, val submittedAt: String?, val startsAt: String?,
    val dueAt: String, val timezone: String, val version: Long,
    val canEdit: Boolean, val notes: String?,
    val steps: List<ExecutionStepDto>,
    val photoRequirements: List<ExecutionPhotoDto>,
    val evidenceRequirements: List<ExecutionEvidenceRequirementDto>,
    val exceptions: List<ExecutionExceptionDto>
)

internal data class ExecutionStepDto(
    val id: Long, val title: String, val instructions: String?, val position: Int, val completed: Boolean
)

internal data class ExecutionPhotoDto(
    val id: Long, val title: String, val description: String?, val position: Int,
    val evidenceId: Long?, val contentPath: String?
)

internal data class ExecutionEvidenceRequirementDto(
    val id: Long, val type: String, val required: Boolean,
    val minimumCount: Int, val maximumCount: Int, val position: Int,
    val constraints: Map<String, String>, val evidence: List<RequiredEvidenceDto>
)

internal data class RequiredEvidenceDto(
    val id: Long, val type: String, val contentPath: String?, val textValue: String?, val capturedAt: String?, val checksum: String?,
    val latitude: Double?, val longitude: Double?, val accuracyMeters: Double?
)

internal data class UpdateExecutionStepRequest(val completed: Boolean, val expectedVersion: Long)
internal data class SubmitExecutionRequest(val expectedVersion: Long, val notes: String?)

internal data class ExecutionExceptionDto(
    val id: Long, val stepId: Long?, val reason: String, val description: String,
    val reportedBy: Long, val reportedAt: String, val operationId: String, val evidenceIds: List<Long>
)

internal data class ReportExceptionRequest(
    val reason: String, val description: String, val stepId: Long?, val operationId: String,
    val evidenceIds: List<Long>, val expectedVersion: Long
)

internal data class StructuredEvidenceRequest(
    val textValue: String?, val latitude: Double?, val longitude: Double?, val accuracyMeters: Double?,
    val capturedAt: String, val expectedVersion: Long
)
