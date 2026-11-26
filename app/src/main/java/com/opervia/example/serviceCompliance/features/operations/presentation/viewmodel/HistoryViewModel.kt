package com.opervia.example.serviceCompliance.features.operations.presentation.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.opervia.example.serviceCompliance.features.operations.domain.model.*
import com.opervia.example.serviceCompliance.features.operations.domain.repository.OperationsRepository
import java.time.LocalDate
import java.time.ZoneId
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

enum class HistoryPeriod { TODAY, SEVEN_DAYS }

data class HistoryState(
    val period: HistoryPeriod = HistoryPeriod.TODAY,
    val query: String = "",
    val reviewStatus: HistoryReviewStatus? = null,
    val items: List<ExecutionHistoryItem> = emptyList(),
    val loading: Boolean = false,
    val loadingMore: Boolean = false,
    val nextCursor: String? = null,
    val hasMore: Boolean = false,
    val asOf: String? = null,
    val error: String? = null,
    val sessionExpired: Boolean = false,
    val showingStaleData: Boolean = false
)

class HistoryViewModel(
    private val repository: OperationsRepository,
    private val today: () -> LocalDate = { LocalDate.now(ZoneId.of("America/Lima")) }
) : ViewModel() {
    private val mutable = MutableStateFlow(HistoryState())
    val state = mutable.asStateFlow()
    private var request: Job? = null
    private var generation = 0L
    private var confirmedQuery: ExecutionHistoryQuery? = null

    init { refresh() }

    fun refresh() = loadFirst(debounce = false)

    fun setPeriod(period: HistoryPeriod) {
        if (mutable.value.period == period) return
        mutable.value = mutable.value.copy(period = period)
        loadFirst(debounce = false)
    }

    fun setReviewStatus(status: HistoryReviewStatus?) {
        if (mutable.value.reviewStatus == status) return
        mutable.value = mutable.value.copy(reviewStatus = status)
        loadFirst(debounce = false)
    }

    fun setQuery(value: String) {
        if (value.length > 200 || value == mutable.value.query) return
        mutable.value = mutable.value.copy(query = value)
        loadFirst(debounce = true)
    }

    private fun selectedQuery(): ExecutionHistoryQuery {
        val current = mutable.value
        val end = today()
        val start = if (current.period == HistoryPeriod.TODAY) end else end.minusDays(6)
        return ExecutionHistoryQuery(start, end, current.query.trim(), current.reviewStatus)
    }

    private fun loadFirst(debounce: Boolean) {
        request?.cancel()
        val identity = ++generation
        val query = selectedQuery()
        val keepPrevious = confirmedQuery == query
        mutable.value = mutable.value.copy(loading = true, loadingMore = false, error = null,
            showingStaleData = false, items = if (keepPrevious) mutable.value.items else emptyList(),
            nextCursor = if (keepPrevious) mutable.value.nextCursor else null,
            hasMore = keepPrevious && mutable.value.hasMore,
            asOf = if (keepPrevious) mutable.value.asOf else null)
        request = viewModelScope.launch {
            try {
                if (debounce) delay(300)
                val page = repository.executionHistory(query)
                page.validate()
                if (identity != generation) return@launch
                confirmedQuery = query
                mutable.value = mutable.value.copy(items = page.items.distinctBy { it.id },
                    nextCursor = page.nextCursor, hasMore = page.hasMore, asOf = page.asOf,
                    showingStaleData = false, sessionExpired = false)
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (error: Exception) { if (identity == generation) showFailure(error, keepPrevious) }
            finally { if (identity == generation) mutable.value = mutable.value.copy(loading = false) }
        }
    }

    fun loadMore() {
        val current = mutable.value
        if (current.loading || current.loadingMore || !current.hasMore || current.showingStaleData || current.sessionExpired) return
        val cursor = current.nextCursor ?: return
        val query = confirmedQuery ?: return
        if (query != selectedQuery()) return
        val identity = generation
        mutable.value = current.copy(loadingMore = true, error = null)
        request = viewModelScope.launch {
            try {
                val page = repository.executionHistory(query, cursor)
                page.validate()
                if (identity != generation) return@launch
                require(page.asOf == current.asOf) { "El historial cambió de consulta. Actualiza para continuar." }
                require(!page.hasMore || page.nextCursor != cursor) { "El servidor repitió la misma página del historial." }
                mutable.value = mutable.value.copy(items = (current.items + page.items).distinctBy { it.id },
                    nextCursor = page.nextCursor, hasMore = page.hasMore, asOf = page.asOf)
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (error: Exception) { if (identity == generation) showFailure(error, keepPrevious = true) }
            finally { if (identity == generation) mutable.value = mutable.value.copy(loadingMore = false) }
        }
    }

    private fun showFailure(error: Exception, keepPrevious: Boolean) {
        val failure = error as? OperationsFailure
        val denied = failure?.kind in setOf(OperationsFailureKind.UNAUTHENTICATED, OperationsFailureKind.FORBIDDEN)
        val stale = keepPrevious && mutable.value.asOf != null && !denied
        mutable.value = mutable.value.copy(
            error = when (failure?.kind) {
                OperationsFailureKind.UNAUTHENTICATED -> "La sesión venció. Inicia sesión nuevamente para consultar tu historial."
                OperationsFailureKind.FORBIDDEN -> "Tu cuenta no tiene acceso a este historial."
                OperationsFailureKind.NOT_FOUND -> "Este backend todavía no dispone de la consulta de historial."
                else -> error.message ?: "No se pudo consultar el historial. Comprueba tu conexión."
            },
            items = if (stale) mutable.value.items else emptyList(),
            asOf = if (stale) mutable.value.asOf else null,
            nextCursor = if (stale) mutable.value.nextCursor else null,
            hasMore = stale && mutable.value.hasMore,
            sessionExpired = failure?.kind == OperationsFailureKind.UNAUTHENTICATED,
            showingStaleData = stale
        )
    }
}
