package com.opervia.example.serviceCompliance.features.operations.presentation

import androidx.compose.runtime.*
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.opervia.example.serviceCompliance.features.operations.domain.repository.OperationsRepository
import com.opervia.example.serviceCompliance.features.operations.presentation.screens.HistoryScreen
import com.opervia.example.serviceCompliance.features.operations.presentation.viewmodel.HistoryViewModel

@Composable
internal fun HistoryRoute(repository: OperationsRepository, userId: Long, onSessionExpired: () -> Unit) {
    val factory = remember(repository, userId) {
        object : ViewModelProvider.Factory {
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                require(modelClass == HistoryViewModel::class.java)
                @Suppress("UNCHECKED_CAST")
                return HistoryViewModel(repository) as T
            }
        }
    }
    val model: HistoryViewModel = viewModel(key = "history-$userId", factory = factory)
    val state by model.state.collectAsStateWithLifecycle()
    LaunchedEffect(Unit) { if (!model.state.value.loading) model.refresh() }
    HistoryScreen(state, model::setQuery, model::setPeriod, model::setReviewStatus,
        model::refresh, model::loadMore, onSessionExpired)
}
