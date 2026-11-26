package com.opervia.example.serviceCompliance.features.operations.domain.repository

import com.opervia.example.serviceCompliance.features.operations.domain.model.StructuredEvidenceDraft

interface StructuredEvidenceStore {
    suspend fun hasPendingSubmission(userId: Long, obligationId: Long): Boolean
    suspend fun structuredDraft(userId: Long, executionId: Long, requirementId: Long): StructuredEvidenceDraft?
    suspend fun saveStructuredDraft(userId: Long, draft: StructuredEvidenceDraft)
    suspend fun removeStructuredDraft(userId: Long, draft: StructuredEvidenceDraft)
}
