package com.upc.example.serviceComplianceKotlin.fieldoperations.model

data class WorkStep(
    val id: Long,
    val title: String,
    val instructions: String?,
    val completed: Boolean
)
data class PhotoRequirement(
    val id: Long,
    val title: String,
    val description: String?,
    val evidenceId: Long?
)
data class WorkException(
    val reason: String,
    val description: String,
    val operationId: String
)
data class ObligationDetail(
    val id: Long,
    val title: String,
    val description: String?,
    val siteName: String,
    val status: String,
    val startsAt: String?,
    val dueAt: String,
    val steps: List<WorkStep>,
    val photos: List<PhotoRequirement>
)
data class Execution(
    val id: Long,
    val state: String,
    val obligationStatus: String,
    val version: Long,
    val canEdit: Boolean,
    val steps: List<WorkStep>,
    val photos: List<PhotoRequirement>,
    val exceptions: List<WorkException>
) {
    val readyToSubmit: Boolean
        get() {
            if (!canEdit || steps.isEmpty()) {
                return false
            }
            for (step in steps) {
                if (!step.completed) {
                    return false
                }
            }
            for (photo in photos) {
                if (photo.evidenceId == null) {
                    return false
                }
            }
            return true
        }
}
