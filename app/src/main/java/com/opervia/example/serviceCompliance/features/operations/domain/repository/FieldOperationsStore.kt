package com.opervia.example.serviceCompliance.features.operations.domain.repository

import com.opervia.example.serviceCompliance.features.operations.domain.model.PendingExceptionReport
import com.opervia.example.serviceCompliance.features.operations.domain.model.AssignedObligation
import com.opervia.example.serviceCompliance.features.operations.domain.model.ExecutionRecord
import com.opervia.example.serviceCompliance.features.operations.domain.model.PendingExecutionSubmission

/** Persistencia usada por el flujo; todas las claves están separadas por cuenta y obligación. */
interface FieldOperationsStore {
    suspend fun saveExecution(userId: Long, execution: ExecutionRecord, obligation: AssignedObligation?)
    suspend fun startKey(userId: Long, obligationId: Long): String
    suspend fun pendingSubmission(userId: Long, obligationId: Long): PendingExecutionSubmission?
    suspend fun saveSubmission(userId: Long, obligationId: Long, submission: PendingExecutionSubmission)
    suspend fun clearSubmission(userId: Long, obligationId: Long)
    suspend fun pendingException(userId: Long, obligationId: Long): PendingExceptionReport?
    suspend fun saveException(userId: Long, obligationId: Long, report: PendingExceptionReport)
    suspend fun clearException(userId: Long, obligationId: Long)
}
