package com.opervia.example.serviceCompliance.features.operations.presentation.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.opervia.example.serviceCompliance.features.operations.data.LocalFieldOperationsStore
import com.opervia.example.serviceCompliance.features.operations.domain.repository.OperationsRepository
import com.opervia.example.serviceCompliance.features.operations.domain.model.AssignedObligation
import com.opervia.example.serviceCompliance.features.operations.domain.model.EvidenceRecord
import com.opervia.example.serviceCompliance.features.operations.domain.model.EvidenceType
import com.opervia.example.serviceCompliance.features.operations.domain.model.ExecutionRecord
import com.opervia.example.serviceCompliance.features.operations.domain.model.ExecutionResult
import com.opervia.example.serviceCompliance.features.operations.domain.model.OperationsFailure
import com.opervia.example.serviceCompliance.features.operations.domain.model.OperationsFailureKind
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class FieldState(
    val obligation: AssignedObligation? = null,
    val execution: ExecutionRecord? = null,
    val evidence: List<EvidenceRecord> = emptyList(),
    val busy: Boolean = false,
    val error: String? = null,
    val sessionExpired: Boolean = false,
    val submissionUncertain: Boolean = false,
    val localPersistenceWarning: String? = null
)

class FieldViewModel(
    private val repository: OperationsRepository,
    private val obligationId: Long,
    initialExecution: ExecutionRecord?,
    private val localStore: LocalFieldOperationsStore,
    private val userId: Long
) : ViewModel() {
    private val mutable = MutableStateFlow(FieldState(execution = initialExecution))
    val state = mutable.asStateFlow()

    init { refresh() }

    fun refresh() = runOperation {
        val obligation = repository.assignedObligations().firstOrNull { it.id == obligationId }
            ?: throw OperationsFailure(OperationsFailureKind.NOT_FOUND, "La obligación ya no está asignada a tu usuario.")
        mutable.value = mutable.value.copy(obligation = obligation)
    }

    fun submit(result: ExecutionResult, notes: String?) = runOperation(mutation = true) {
        val obligation = mutable.value.obligation
        val record = repository.registerExecution(obligationId, result, notes)
        mutable.value = mutable.value.copy(execution = record, submissionUncertain = false)
        try {
            withContext(NonCancellable) {
                localStore.saveExecution(userId, record, obligation)
            }
            mutable.value = mutable.value.copy(localPersistenceWarning = null)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            mutable.value = mutable.value.copy(
                localPersistenceWarning = "La ejecución se confirmó en el servidor, pero no se pudo guardar su copia local."
            )
        }
    }

    fun refreshEvidence() {
        val execution = mutable.value.execution ?: return
        runOperation { mutable.value = mutable.value.copy(evidence = repository.evidence(execution.id)) }
    }

    fun addEvidence(type: EvidenceType, url: String, description: String?) = runOperation(mutation = true) {
        val execution = mutable.value.execution ?: return@runOperation
        val item = repository.addEvidence(execution.id, type, url, description)
        mutable.value = mutable.value.copy(evidence = mutable.value.evidence + item)
    }

    private fun runOperation(mutation: Boolean = false, operation: suspend () -> Unit) {
        if (mutable.value.busy || (mutation && mutable.value.submissionUncertain)) return
        viewModelScope.launch {
            mutable.value = mutable.value.copy(busy = true, error = null)
            try {
                operation()
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                val failure = error as? OperationsFailure
                val uncertain = mutation && (failure == null || failure.kind in setOf(OperationsFailureKind.CONFLICT, OperationsFailureKind.SERVER))
                val message = when (failure?.kind) {
                    OperationsFailureKind.INVALID_REQUEST -> failure?.message ?: "Revisa la información enviada."
                    OperationsFailureKind.UNAUTHENTICATED -> "La sesión venció. Inicia sesión nuevamente."
                    OperationsFailureKind.FORBIDDEN -> "No tienes permiso para acceder a esta obligación."
                    OperationsFailureKind.NOT_FOUND -> "La obligación o el recurso ya no está disponible."
                    else -> if (uncertain) "No se pudo confirmar la respuesta del servidor. Evita reenviar hasta verificar el registro con supervisión." else
                        error.message ?: "No se pudo conectar con el servidor. Inténtalo nuevamente."
                }
                mutable.value = mutable.value.copy(
                    error = message,
                    sessionExpired = failure?.kind == OperationsFailureKind.UNAUTHENTICATED,
                    submissionUncertain = mutable.value.submissionUncertain || uncertain
                )
            } finally {
                mutable.value = mutable.value.copy(busy = false)
            }
        }
    }
}
