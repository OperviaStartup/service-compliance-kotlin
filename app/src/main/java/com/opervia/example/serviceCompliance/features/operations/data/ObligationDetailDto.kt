package com.opervia.example.serviceCompliance.features.operations.data

/** Respuesta existente de GET /api/v1/obligations/{id}. */
internal data class ObligationDetailDto(
    val id: Long,
    val title: String,
    val description: String?,
    val siteName: String,
    val status: String,
    val dueAt: String,
    val startsAt: String?,
    val timezone: String,
    val steps: List<ObligationStepDto>,
    val photoRequirements: List<ObligationPhotoRequirementDto>
)

internal data class ObligationStepDto(
    val id: Long,
    val title: String,
    val instructions: String?,
    val position: Int
)

internal data class ObligationPhotoRequirementDto(
    val id: Long,
    val title: String,
    val description: String?,
    val position: Int
)
