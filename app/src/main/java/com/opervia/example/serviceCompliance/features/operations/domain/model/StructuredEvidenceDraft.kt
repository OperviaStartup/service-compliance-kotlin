package com.opervia.example.serviceCompliance.features.operations.domain.model

/** Captura real pendiente de confirmación; nunca equivale por sí sola a evidencia registrada. */
data class StructuredEvidenceDraft(
    val executionId: Long,
    val requirementId: Long,
    val type: EvidenceType,
    val idempotencyKey: String,
    val capturedAt: String,
    val textValue: String? = null,
    val location: PhotoLocation? = null,
    val expectedVersion: Long? = null,
    val uploadPending: Boolean = false
)
