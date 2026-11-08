package com.opervia.example.serviceCompliance.features.operations.presentation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import com.opervia.example.serviceCompliance.features.operations.presentation.screens.FieldTasksScreen
import com.opervia.example.serviceCompliance.features.operations.presentation.viewmodel.FieldTasksViewModel
import com.opervia.example.serviceCompliance.features.operations.domain.model.ExecutionRecord
import com.opervia.example.serviceCompliance.features.operations.domain.repository.OperationsRepository
import com.opervia.example.serviceCompliance.features.operations.data.LocalFieldOperationsStore

@Composable
fun FieldTasksRoute(
    repository: OperationsRepository,
    localStore: LocalFieldOperationsStore,
    userId: Long,
    fullName: String,
    executionHistory: List<ExecutionRecord>,
    localHistoryWarning: String?,
    onOpenObligation: (Long) -> Unit,
    onLogout: () -> Unit
) {
    val factory = remember(repository, localStore, userId) { FieldTasksViewModelFactory(repository, localStore, userId) }
    val model: FieldTasksViewModel = viewModel(key = "field-tasks", factory = factory)
    FieldTasksScreen(
        model,
        fullName,
        executionHistory,
        localHistoryWarning,
        onOpenObligation,
        onLogout,
        model::synchronize
    )
}

private class FieldTasksViewModelFactory(
    private val repository: OperationsRepository,
    private val localStore: LocalFieldOperationsStore,
    private val userId: Long
) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        require(modelClass == FieldTasksViewModel::class.java) { "Unsupported ViewModel type" }
        @Suppress("UNCHECKED_CAST")
        return FieldTasksViewModel(repository, localStore, userId) as T
    }
}
