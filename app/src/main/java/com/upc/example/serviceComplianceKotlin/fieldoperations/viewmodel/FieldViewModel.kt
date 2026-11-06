package com.upc.example.serviceComplianceKotlin.fieldoperations.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.upc.example.serviceComplianceKotlin.fieldoperations.data.FieldApiException
import com.upc.example.serviceComplianceKotlin.fieldoperations.data.FieldRepository
import com.upc.example.serviceComplianceKotlin.fieldoperations.model.Execution
import com.upc.example.serviceComplianceKotlin.fieldoperations.model.ObligationDetail
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.File

data class FieldState(
    val detail: ObligationDetail? = null,
    val execution: Execution? = null,
    val busy: Boolean = false,
    val error: String? = null,
    val sessionExpired: Boolean = false,
    val needsRefresh: Boolean = false,
    val preview: EvidencePreview? = null
)

data class EvidencePreview(val id: Long, val bytes: ByteArray)

class FieldViewModel(
    private val repository: FieldRepository,
    private val obligationId: Long
) : ViewModel() {
    private val mutable = MutableStateFlow(FieldState())
    val state = mutable.asStateFlow()

    init {
        refresh()
    }

    fun refresh() {
        runOperation {
            val detail = repository.detail(obligationId)
            val execution = repository.current(obligationId)
            mutable.value = FieldState(
                detail = detail,
                execution = execution,
                busy = true,
                preview = mutable.value.preview
            )
        }
    }

    fun start() {
        runOperation(mutation = true) {
            val execution = repository.start(obligationId)
            mutable.value = mutable.value.copy(execution = execution)
        }
    }

    fun check(stepId: Long, completed: Boolean) {
        updateExecution { execution ->
            repository.step(execution, stepId, completed)
        }
    }

    fun upload(id: Long, file: File) {
        updateExecution { execution ->
            repository.upload(execution, id, file)
        }
    }

    fun report(reason: String, description: String, operationId: String) {
        val trimmedDescription = description.trim()
        updateExecution { execution ->
            repository.report(execution, reason, trimmedDescription, operationId)
        }
    }

    fun submit() {
        updateExecution { execution ->
            repository.submit(execution)
        }
    }

    fun loadPhoto(id: Long) {
        runOperation {
            val bytes = repository.photo(id)
            val preview = EvidencePreview(id, bytes)
            mutable.value = mutable.value.copy(preview = preview)
        }
    }


    private fun updateExecution(operation: suspend (Execution) -> Execution) {
        val currentState = mutable.value
        val execution = currentState.execution
        if (execution == null) {
            return
        }
        if (!execution.canEdit || currentState.needsRefresh || currentState.sessionExpired) {
            return
        }

        runOperation(mutation = true) {
            val updatedExecution = operation(execution)
            mutable.value = mutable.value.copy(execution = updatedExecution)
        }
    }


    private fun runOperation(mutation: Boolean = false, operation: suspend () -> Unit) {
        if (mutable.value.busy) {
            return
        }

        viewModelScope.launch {
            mutable.value = mutable.value.copy(busy = true, error = null)
            try {
                operation()
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                handleError(error, mutation)
            } finally {
                mutable.value = mutable.value.copy(busy = false)
            }
        }
    }

    private fun handleError(error: Exception, mutation: Boolean) {
        val apiError = error as? FieldApiException
        val status = apiError?.status
        val uncertain = mutation && (apiError == null || apiError.status >= 500 || apiError.status == 409)
        val unavailable = status == 403 || status == 404
        val needsRefresh = uncertain || !mutation || unavailable

        val message = when {
            status == 401 -> "La sesión venció. Regresa e inicia sesión nuevamente."
            status == 403 -> "No tienes permiso para acceder a esta obligación."
            status == 404 -> "La obligación o el recurso ya no está disponible."
            uncertain -> "Consulta el avance actualizado antes de continuar. La operación pudo haberse recibido."
            apiError != null -> apiError.message
            else -> "No se pudo conectar con el servidor. Comprueba la conexión e inténtalo nuevamente."
        }

        mutable.value = mutable.value.copy(
            error = message,
            sessionExpired = status == 401,
            needsRefresh = needsRefresh
        )
    }
}
