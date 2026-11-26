package com.opervia.example.serviceCompliance.features.operations.presentation.screens

import com.opervia.example.serviceCompliance.features.operations.domain.model.ExecutionHistoryItem
import com.opervia.example.serviceCompliance.features.operations.domain.model.ExecutionResult
import com.opervia.example.serviceCompliance.features.operations.domain.model.HistoryReviewStatus

internal fun reviewStatusLabel(status: HistoryReviewStatus): String = when (status) {
    HistoryReviewStatus.PENDING_REVIEW -> "Pendiente de revisión"
    HistoryReviewStatus.COMPLIANT -> "Conforme"
    HistoryReviewStatus.EXCEPTION_ACCEPTED -> "Excepción aceptada"
    HistoryReviewStatus.NON_COMPLIANT -> "Incumplimiento"
}

internal data class HistoryEntry(val record: ExecutionHistoryItem) {
    val declaredResultLabel: String get() = when (record.result) {
        ExecutionResult.COMPLETED -> "Completada"
        ExecutionResult.PARTIAL -> "Parcial"
        ExecutionResult.BLOCKED -> "Bloqueada"
    }
    val reviewLabel: String get() = reviewStatusLabel(record.reviewStatus)
}

internal fun ExecutionHistoryItem.toHistoryEntry() = HistoryEntry(this)
