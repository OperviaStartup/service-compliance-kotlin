package com.opervia.example.serviceCompliance.features.operations.domain.model

/** Detalle consultado por ID; los requisitos todavía no representan evidencias adjuntadas. */
data class ObligationDetail(
    val obligation: AssignedObligation,
    val startsAt: String?,
    val timezone: String,
    val steps: List<ObligationStep>,
    val photoRequirements: List<ObligationPhotoRequirement>
)

data class ObligationStep(
    val id: Long,
    val title: String,
    val instructions: String?,
    val position: Int
)

data class ObligationPhotoRequirement(
    val id: Long,
    val title: String,
    val description: String?,
    val position: Int
)
