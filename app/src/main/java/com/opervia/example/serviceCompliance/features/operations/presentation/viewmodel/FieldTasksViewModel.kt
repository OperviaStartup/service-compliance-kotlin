package com.opervia.example.serviceCompliance.features.operations.presentation.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.opervia.example.serviceCompliance.features.operations.data.LocalFieldOperationsStore
import com.opervia.example.serviceCompliance.features.operations.domain.repository.OperationsRepository
import com.opervia.example.serviceCompliance.features.operations.domain.model.AssignedObligation
import com.opervia.example.serviceCompliance.features.operations.domain.model.OperationsFailure
import com.opervia.example.serviceCompliance.features.operations.domain.model.OperationsFailureKind
import com.opervia.example.serviceCompliance.features.operations.domain.model.ExecutionRecord
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.time.Instant

data class FieldTasksState(
    val obligations: List<AssignedObligation> = emptyList(),
    val loading: Boolean = false,
    val loaded: Boolean = false,
    val error: String? = null,
    val sessionExpired: Boolean = false,
    val showingStaleData: Boolean = false,
    val synchronizing: Boolean = false,
    val lastSyncAt: Instant? = null,
    val syncError: String? = null,
    val cacheError: String? = null
)

class FieldTasksViewModel(
    private val repository: OperationsRepository,
    private val localStore: LocalFieldOperationsStore,
    private val userId: Long
) : ViewModel() {
    private val mutable = MutableStateFlow(FieldTasksState(loading = true))
    val state = mutable.asStateFlow()
    private var cacheReady = false

    init {
        viewModelScope.launch {
            try {
                val cachedObligations = localStore.loadObligations(userId)
                mutable.value = mutable.value.copy(
                    obligations = cachedObligations,
                    loaded = cachedObligations.isNotEmpty(),
                    showingStaleData = cachedObligations.isNotEmpty(),
                    loading = false
                )
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                mutable.value = mutable.value.copy(
                    loading = false,
                    cacheError = "No se pudieron recuperar las tareas guardadas en este dispositivo."
                )
            }
            cacheReady = true
            refresh()
        }
    }

    fun refresh() {
        if (!cacheReady || mutable.value.loading || mutable.value.synchronizing) return
        viewModelScope.launch {
            mutable.value = mutable.value.copy(loading = true, error = null)
            try {
                val cachedObligations = cacheObligations(repository.assignedObligations())
                mutable.value = mutable.value.copy(
                    obligations = cachedObligations,
                    loaded = true,
                    error = null,
                    sessionExpired = false,
                    showingStaleData = false
                )
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                val failure = error as? OperationsFailure
                val message = when (failure?.kind) {
                    OperationsFailureKind.UNAUTHENTICATED -> "La sesión venció. Inicia sesión nuevamente."
                    OperationsFailureKind.FORBIDDEN -> "Tu cuenta no tiene acceso a las obligaciones del operario."
                    else -> error.message ?: "No se pudieron consultar tus obligaciones. Comprueba la conexión e inténtalo de nuevo."
                }
                mutable.value = mutable.value.copy(
                    error = message,
                    sessionExpired = failure?.kind == OperationsFailureKind.UNAUTHENTICATED,
                    showingStaleData = mutable.value.loaded &&
                        (failure == null || failure.kind == OperationsFailureKind.SERVER)
                )
            } finally {
                mutable.value = mutable.value.copy(loading = false)
            }
        }
    }

    /** Verifies records already accepted by the API; it never replays a mutation. */
    fun synchronize(records: List<ExecutionRecord>) {
        if (mutable.value.loading || mutable.value.synchronizing) return
        viewModelScope.launch {
            mutable.value = mutable.value.copy(synchronizing = true, syncError = null)
            try {
                val obligations = cacheObligations(repository.assignedObligations())
                records.forEach { record ->
                    repository.execution(record.id)
                    repository.evidence(record.id)
                }
                mutable.value = mutable.value.copy(
                    obligations = obligations,
                    loaded = true,
                    lastSyncAt = Instant.now()
                )
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                mutable.value = mutable.value.copy(syncError = error.message ?: "No se pudo verificar la sincronización.")
            } finally {
                mutable.value = mutable.value.copy(synchronizing = false)
            }
        }
    }

    private suspend fun cacheObligations(obligations: List<AssignedObligation>): List<AssignedObligation> = try {
        localStore.saveObligations(userId, obligations).also {
            mutable.value = mutable.value.copy(cacheError = null)
        }
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (_: Exception) {
        mutable.value = mutable.value.copy(
            cacheError = "Las tareas se cargaron, pero no se pudieron guardar localmente."
        )
        obligations
    }
}
