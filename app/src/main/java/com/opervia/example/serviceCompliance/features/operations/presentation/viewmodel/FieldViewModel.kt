package com.opervia.example.serviceCompliance.features.operations.presentation.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.opervia.example.serviceCompliance.features.operations.domain.repository.FieldOperationsStore
import com.opervia.example.serviceCompliance.features.operations.domain.repository.OperationsRepository
import com.opervia.example.serviceCompliance.features.operations.domain.model.AssignedObligation
import com.opervia.example.serviceCompliance.features.operations.domain.model.EvidenceRecord
import com.opervia.example.serviceCompliance.features.operations.domain.model.EvidenceType
import com.opervia.example.serviceCompliance.features.operations.domain.model.ExecutionRecord
import com.opervia.example.serviceCompliance.features.operations.domain.model.ExecutionResult
import com.opervia.example.serviceCompliance.features.operations.domain.model.OperationsFailure
import com.opervia.example.serviceCompliance.features.operations.domain.model.OperationsFailureKind
import com.opervia.example.serviceCompliance.features.operations.domain.model.ObligationDetail
import com.opervia.example.serviceCompliance.features.operations.domain.model.ExecutionFlow
import com.opervia.example.serviceCompliance.features.operations.domain.model.ExecutionState
import com.opervia.example.serviceCompliance.features.operations.domain.model.PendingExecutionSubmission
import com.opervia.example.serviceCompliance.features.operations.domain.model.ExceptionReason
import com.opervia.example.serviceCompliance.features.operations.domain.model.ExecutionException
import com.opervia.example.serviceCompliance.features.operations.domain.model.PendingExceptionReport
import java.util.UUID
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class FieldState(
    val detail: ObligationDetail? = null,
    val flow: ExecutionFlow? = null,
    val flowConfirmed: Boolean = false,
    val pendingSubmission: PendingExecutionSubmission? = null,
    val pendingException: PendingExceptionReport? = null,
    val reportedException: ExecutionException? = null,
    val execution: ExecutionRecord? = null,
    val evidence: List<EvidenceRecord> = emptyList(),
    val busy: Boolean = false,
    val error: String? = null,
    val sessionExpired: Boolean = false,
    val submissionUncertain: Boolean = false,
    val localPersistenceWarning: String? = null
) {
    val obligation: AssignedObligation? get() = detail?.obligation
    val canEditSteps: Boolean get() = flowConfirmed && flow?.canEdit == true && flow.state == ExecutionState.IN_PROGRESS &&
        !busy && !sessionExpired && pendingSubmission == null && pendingException == null
}

class FieldViewModel(
    private val repository: OperationsRepository,
    private val obligationId: Long,
    initialExecution: ExecutionRecord?,
    private val localStore: FieldOperationsStore,
    private val userId: Long
) : ViewModel() {
    private val mutable = MutableStateFlow(FieldState(execution = initialExecution))
    val state = mutable.asStateFlow()

    init { refresh() }

    fun refreshAfterEvidence() {
        viewModelScope.launch {
            state.first { !it.busy }
            refresh()
        }
    }

    fun refresh() = runOperation {
        mutable.value = mutable.value.copy(flowConfirmed = false)
        try {
            val detail = repository.obligationDetail(obligationId)
            mutable.value = mutable.value.copy(detail = detail, sessionExpired = false)
            if (detail.startsAt != null) {
                val pending = localStore.pendingSubmission(userId, obligationId)
                mutable.value = mutable.value.copy(pendingSubmission = pending,
                    pendingException = localStore.pendingException(userId, obligationId))
                val current = repository.currentExecution(obligationId)
                if (current != null) applyFlow(current)
                else mutable.value = mutable.value.copy(flow = null, execution = null, flowConfirmed = true)
            } else {
                mutable.value = mutable.value.copy(flowConfirmed = true)
            }
        } catch (failure: OperationsFailure) {
            mutable.value = mutable.value.copy(flowConfirmed = false)
            if (failure.kind in setOf(
                    OperationsFailureKind.UNAUTHENTICATED,
                    OperationsFailureKind.FORBIDDEN,
                    OperationsFailureKind.NOT_FOUND
                )) {
                clearUnavailableDetail()
            }
            throw failure
        }
    }

    fun start(onStarted: () -> Unit) = runOperation {
        val detail = mutable.value.detail ?: return@runOperation
        if (detail.startsAt == null) {
            onStarted()
            return@runOperation
        }
        val existing = repository.currentExecution(obligationId)
        if (existing != null) {
            applyFlow(existing)
            onStarted()
            return@runOperation
        }
        val key = localStore.startKey(userId, obligationId)
        val started = try {
            repository.startExecution(obligationId, key)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Exception) {
            // Una respuesta perdida no significa que el servidor no haya iniciado.
            recoverCurrent() ?: throw error
        }
        applyFlow(started)
        onStarted()
    }

    fun updateStep(stepId: Long, completed: Boolean) {
        if (!mutable.value.canEditSteps) return
        val flow = mutable.value.flow ?: return
        runOperation {
            val updated = try {
                repository.updateStep(flow.id, stepId, completed, flow.version)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                val recovered = recoverCurrent()
                if (recovered?.steps?.firstOrNull { it.id == stepId }?.completed == completed) recovered
                else throw error
            }
            applyFlow(updated)
        }
    }

    fun submitFlow(notes: String?) = runOperation {
        val flow = mutable.value.flow ?: return@runOperation
        if (mutable.value.pendingException != null) {
            throw OperationsFailure(OperationsFailureKind.INVALID_REQUEST,
                "Confirma el impedimento pendiente antes de enviar la ejecución.")
        }
        val saved = mutable.value.pendingSubmission
        if (saved == null && (!mutable.value.flowConfirmed || !flow.canSubmit)) {
            throw OperationsFailure(OperationsFailureKind.INVALID_REQUEST,
                "Completa todos los pasos y las evidencias obligatorias antes de enviar.")
        }
        val submission = saved ?: PendingExecutionSubmission(
            executionId = flow.id,
            idempotencyKey = "submit-${UUID.randomUUID()}",
            expectedVersion = flow.version,
            notes = notes?.trim()?.takeIf(String::isNotEmpty)
        ).also {
            localStore.saveSubmission(userId, obligationId, it)
            mutable.value = mutable.value.copy(pendingSubmission = it)
        }
        try {
            applyFlow(repository.submitExecution(submission))
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Exception) {
            val recovered = recoverCurrent()
            if (recovered?.state == ExecutionState.SUBMITTED) return@runOperation
            val failure = error as? OperationsFailure
            if (failure != null && failure.kind !in setOf(OperationsFailureKind.SERVER, OperationsFailureKind.UNKNOWN)) {
                localStore.clearSubmission(userId, obligationId)
                mutable.value = mutable.value.copy(pendingSubmission = null)
                throw error
            }
            throw OperationsFailure(OperationsFailureKind.SERVER,
                "No se pudo confirmar el envío. Actualiza el avance o reintenta el mismo envío.")
        }
    }

    fun openException() {
        mutable.value = mutable.value.copy(reportedException = null)
    }

    fun reportException(reason: ExceptionReason, description: String, stepId: Long?, evidenceIds: List<Long>) = runOperation {
        if (mutable.value.pendingSubmission != null || mutable.value.sessionExpired) {
            throw OperationsFailure(OperationsFailureKind.INVALID_REQUEST,
                "Confirma el envío pendiente antes de registrar un impedimento.")
        }
        var pending = mutable.value.pendingException
        if (pending == null) {
            // La versión, los pasos y las evidencias siempre se toman del servidor.
            val flow = repository.currentExecution(obligationId)
                ?: throw OperationsFailure(OperationsFailureKind.NOT_FOUND, "Primero inicia la actividad.")
            applyFlow(flow)
            val text = description.trim()
            val availableEvidence = flow.photoRequirements.mapNotNull { it.evidenceId } +
                flow.evidenceRequirements.flatMap { it.evidence }.map { it.id }
            if (!flow.canEdit || flow.state != ExecutionState.IN_PROGRESS || text.isBlank() || text.length > 2000 ||
                (stepId != null && flow.steps.none { it.id == stepId }) || evidenceIds.any { it !in availableEvidence }) {
                throw OperationsFailure(OperationsFailureKind.INVALID_REQUEST,
                    "Revisa el detalle, el paso y las evidencias. La actividad debe seguir en curso.")
            }
            pending = PendingExceptionReport(flow.id, "exception-${UUID.randomUUID()}", UUID.randomUUID().toString(),
                reason, text, stepId, evidenceIds.distinct().sorted(), flow.version)
            localStore.saveException(userId, obligationId, pending)
            mutable.value = mutable.value.copy(pendingException = pending, reportedException = null)
        }
        val report = pending
        if (report.executionId != mutable.value.flow?.id) {
            throw OperationsFailure(OperationsFailureKind.CONFLICT,
                "Actualiza el avance antes de confirmar el impedimento pendiente.")
        }
        try {
            applyFlow(repository.reportException(report))
            if (mutable.value.pendingException != null) {
                throw OperationsFailure(OperationsFailureKind.SERVER, "El servidor no confirmó el impedimento.")
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Exception) {
            recoverCurrent()
            if (mutable.value.reportedException?.let(report::matches) == true) return@runOperation
            val failure = error as? OperationsFailure
            if (failure != null && failure.kind !in setOf(OperationsFailureKind.SERVER, OperationsFailureKind.UNKNOWN)) {
                localStore.clearException(userId, obligationId)
                mutable.value = mutable.value.copy(pendingException = null)
                throw error
            }
            throw OperationsFailure(OperationsFailureKind.SERVER,
                "No se pudo confirmar el impedimento. Actualiza o reintenta el mismo reporte.")
        }
    }

    private suspend fun recoverCurrent(): ExecutionFlow? = try {
        repository.currentExecution(obligationId).also { flow ->
            if (flow != null) applyFlow(flow)
            else mutable.value = mutable.value.copy(flow = null, execution = null, flowConfirmed = true)
        }
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (error: Exception) {
        mutable.value = mutable.value.copy(flowConfirmed = false)
        if ((error as? OperationsFailure)?.kind in setOf(
                OperationsFailureKind.UNAUTHENTICATED, OperationsFailureKind.FORBIDDEN
            )) throw error
        null
    }

    private suspend fun applyFlow(flow: ExecutionFlow) {
        if (flow.obligationId != obligationId || flow.operatorId != userId) {
            throw OperationsFailure(OperationsFailureKind.FORBIDDEN, "La ejecución no corresponde a tu asignación.")
        }
        val previous = mutable.value
        val detail = previous.detail?.let {
            it.copy(obligation = it.obligation.copy(status = flow.obligationStatus, dueAt = flow.dueAt))
        }
        val record = flow.confirmedRecord()
        mutable.value = previous.copy(detail = detail, flow = flow, flowConfirmed = true,
            execution = record, submissionUncertain = false, sessionExpired = false)
        val pendingReport = previous.pendingException
        val confirmedReport = pendingReport?.let { pending -> flow.exceptions.firstOrNull(pending::matches) }
        if (confirmedReport != null) {
            localStore.clearException(userId, obligationId)
            mutable.value = mutable.value.copy(pendingException = null, reportedException = confirmedReport, error = null)
        }
        if (record != null) {
            if (previous.execution != record || previous.localPersistenceWarning != null) persistRecord(record)
            localStore.clearSubmission(userId, obligationId)
            mutable.value = mutable.value.copy(pendingSubmission = null)
        }
    }

    private fun clearUnavailableDetail() {
        mutable.value = mutable.value.copy(detail = null, flow = null, flowConfirmed = false,
            execution = null, evidence = emptyList(), pendingException = null, reportedException = null)
    }

    fun submit(result: ExecutionResult, notes: String?) = runOperation(mutation = true) {
        val detail = mutable.value.detail ?: return@runOperation
        if (detail.startsAt != null) {
            throw OperationsFailure(OperationsFailureKind.INVALID_REQUEST,
                "Esta actividad requiere iniciar y completar su ejecución.")
        }
        val record = repository.registerExecution(obligationId, result, notes)
        mutable.value = mutable.value.copy(execution = record, submissionUncertain = false)
        persistRecord(record)
    }

    private suspend fun persistRecord(record: ExecutionRecord) {
        try {
            withContext(NonCancellable) {
                localStore.saveExecution(userId, record, mutable.value.obligation)
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
        mutable.value = mutable.value.copy(busy = true, error = null)
        viewModelScope.launch {
            try {
                operation()
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                val failure = error as? OperationsFailure
                val uncertain = mutation && (failure == null || failure.kind in setOf(OperationsFailureKind.UNKNOWN, OperationsFailureKind.SERVER))
                if (failure?.kind in setOf(OperationsFailureKind.UNAUTHENTICATED, OperationsFailureKind.FORBIDDEN)) {
                    clearUnavailableDetail()
                }
                val message = when (failure?.kind) {
                    OperationsFailureKind.INVALID_REQUEST -> failure?.message ?: "Revisa la información enviada."
                    OperationsFailureKind.UNAUTHENTICATED -> "La sesión venció. Inicia sesión nuevamente."
                    OperationsFailureKind.FORBIDDEN -> "No tienes permiso para acceder a esta obligación."
                    OperationsFailureKind.NOT_FOUND -> "La obligación o el recurso ya no está disponible."
                    OperationsFailureKind.CONFLICT -> failure.message ?: "El avance cambió. Actualiza para volver a intentarlo."
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
