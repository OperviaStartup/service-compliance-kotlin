package com.opervia.example.serviceCompliance.features.operations.domain.model

enum class ExecutionState { LEGACY, IN_PROGRESS, SUBMITTED }

/** Avance confirmado por el servidor, separado del registro final de Historial. */
data class ExecutionFlow(
    val id: Long,
    val obligationId: Long,
    val operatorId: Long,
    val state: ExecutionState,
    val obligationStatus: String,
    val result: ExecutionResult?,
    val startedAt: String?,
    val submittedAt: String?,
    val startsAt: String?,
    val dueAt: String,
    val timezone: String,
    val version: Long,
    val canEdit: Boolean,
    val notes: String?,
    val steps: List<ExecutionStep>,
    val photoRequirements: List<ExecutionPhotoRequirement>,
    val evidenceRequirements: List<ExecutionEvidenceRequirement>,
    val exceptions: List<ExecutionException> = emptyList()
) {
    val requirementsComplete: Boolean get() =
        steps.all { it.completed } && photoRequirements.all { it.evidenceId != null } &&
            evidenceRequirements.all { !it.required || it.evidence.size >= it.minimumCount }

    val canSubmit: Boolean get() = state == ExecutionState.IN_PROGRESS && canEdit && requirementsComplete

    fun confirmedRecord(): ExecutionRecord? {
        if (state != ExecutionState.SUBMITTED) return null
        val confirmedResult = result ?: return null
        val confirmedAt = submittedAt ?: return null
        return ExecutionRecord(id, obligationId, operatorId, confirmedResult, notes, confirmedAt)
    }
}

data class ExecutionStep(
    val id: Long, val title: String, val instructions: String?, val position: Int, val completed: Boolean
)

data class ExecutionPhotoRequirement(
    val id: Long, val title: String, val description: String?, val position: Int,
    val evidenceId: Long?, val contentPath: String?
)

data class ExecutionEvidenceRequirement(
    val id: Long, val type: EvidenceType, val required: Boolean,
    val minimumCount: Int, val maximumCount: Int, val position: Int,
    val constraints: Map<String, String>, val evidence: List<RequiredEvidence>
)

data class RequiredEvidence(
    val id: Long, val type: EvidenceType, val contentPath: String?,
    val textValue: String?, val capturedAt: String?, val checksum: String? = null,
    val latitude: Double? = null, val longitude: Double? = null, val accuracyMeters: Double? = null
)

/** Solicitud de envío persistida antes de contactar la API; se reutiliza sin cambiarla. */
data class PendingExecutionSubmission(
    val executionId: Long, val idempotencyKey: String, val expectedVersion: Long, val notes: String?
)
