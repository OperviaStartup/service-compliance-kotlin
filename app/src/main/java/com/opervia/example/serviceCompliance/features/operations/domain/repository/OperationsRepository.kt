package com.opervia.example.serviceCompliance.features.operations.domain.repository

import com.opervia.example.serviceCompliance.features.operations.domain.model.AssignedObligation
import com.opervia.example.serviceCompliance.features.operations.domain.model.EvidenceRecord
import com.opervia.example.serviceCompliance.features.operations.domain.model.EvidenceType
import com.opervia.example.serviceCompliance.features.operations.domain.model.ExecutionRecord
import com.opervia.example.serviceCompliance.features.operations.domain.model.ExecutionResult

interface OperationsRepository {
    suspend fun assignedObligations(): List<AssignedObligation>
    suspend fun registerExecution(obligationId: Long, result: ExecutionResult, notes: String?): ExecutionRecord
    suspend fun execution(id: Long): ExecutionRecord
    suspend fun evidence(id: Long): List<EvidenceRecord>
    suspend fun addEvidence(executionId: Long, type: EvidenceType, url: String, description: String?): EvidenceRecord
}
