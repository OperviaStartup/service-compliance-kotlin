package com.opervia.example.serviceCompliance.features.operations.domain.repository

import com.opervia.example.serviceCompliance.features.operations.domain.model.ExecutionHistoryQuery
import com.opervia.example.serviceCompliance.features.operations.domain.model.ExecutionHistoryPage
import com.opervia.example.serviceCompliance.features.operations.domain.model.RequiredEvidence
import com.opervia.example.serviceCompliance.features.operations.domain.model.StructuredEvidenceDraft
import com.opervia.example.serviceCompliance.features.operations.domain.model.PendingExceptionReport
import com.opervia.example.serviceCompliance.features.operations.domain.model.AssignedObligation
import com.opervia.example.serviceCompliance.features.operations.domain.model.EvidenceRecord
import com.opervia.example.serviceCompliance.features.operations.domain.model.EvidenceType
import com.opervia.example.serviceCompliance.features.operations.domain.model.ExecutionRecord
import com.opervia.example.serviceCompliance.features.operations.domain.model.ExecutionResult
import com.opervia.example.serviceCompliance.features.operations.domain.model.ObligationDetail
import com.opervia.example.serviceCompliance.features.operations.domain.model.ExecutionFlow
import com.opervia.example.serviceCompliance.features.operations.domain.model.PendingExecutionSubmission
import com.opervia.example.serviceCompliance.features.operations.domain.model.PhotoUpload
import com.opervia.example.serviceCompliance.features.operations.domain.model.PrivatePhoto

interface OperationsRepository {
    suspend fun executionHistory(query: ExecutionHistoryQuery, cursor: String? = null): ExecutionHistoryPage
    suspend fun assignedObligations(): List<AssignedObligation>
    suspend fun obligationDetail(id: Long): ObligationDetail
    suspend fun currentExecution(obligationId: Long): ExecutionFlow?
    suspend fun startExecution(obligationId: Long, idempotencyKey: String): ExecutionFlow
    suspend fun updateStep(executionId: Long, stepId: Long, completed: Boolean, expectedVersion: Long): ExecutionFlow
    suspend fun submitExecution(submission: PendingExecutionSubmission): ExecutionFlow
    suspend fun reportException(report: PendingExceptionReport): ExecutionFlow
    suspend fun uploadPhoto(upload: PhotoUpload): Long
    suspend fun uploadStructuredEvidence(draft: StructuredEvidenceDraft): RequiredEvidence
    suspend fun photoContent(evidenceId: Long): PrivatePhoto
    suspend fun registerExecution(obligationId: Long, result: ExecutionResult, notes: String?): ExecutionRecord
    suspend fun execution(id: Long): ExecutionRecord
    suspend fun evidence(id: Long): List<EvidenceRecord>
    suspend fun addEvidence(executionId: Long, type: EvidenceType, url: String, description: String?): EvidenceRecord
}
