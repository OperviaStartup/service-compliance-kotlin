package com.opervia.example.serviceCompliance.features.operations.presentation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import com.opervia.example.serviceCompliance.features.operations.presentation.viewmodel.FieldViewModel
import com.opervia.example.serviceCompliance.features.operations.domain.model.ExecutionRecord
import com.opervia.example.serviceCompliance.features.operations.domain.repository.OperationsRepository
import com.opervia.example.serviceCompliance.features.operations.data.LocalFieldOperationsStore

@Composable
fun FieldOperationsRoute(
    repository: OperationsRepository,
    localStore: LocalFieldOperationsStore,
    userId: Long,
    obligationId: Long,
    onExit: () -> Unit,
    existingExecution: ExecutionRecord? = null,
    onExecutionRegistered: (ExecutionRecord) -> Unit = {}
) {
    require(obligationId > 0) { "The obligation ID must be positive" }
    val factory = remember(repository, localStore, userId, obligationId, existingExecution?.id) {
        FieldViewModelFactory(repository, obligationId, existingExecution, localStore, userId)
    }
    val model: FieldViewModel = viewModel(key = "field-obligation-$obligationId-${existingExecution?.id ?: 0}", factory = factory)
    FieldOperationsFlow(model, onExit, onExecutionRegistered)
}

private class FieldViewModelFactory(
    private val repository: OperationsRepository,
    private val obligationId: Long,
    private val existingExecution: ExecutionRecord?,
    private val localStore: LocalFieldOperationsStore,
    private val userId: Long
) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        require(modelClass == FieldViewModel::class.java) { "Unsupported ViewModel type" }
        @Suppress("UNCHECKED_CAST")
        return FieldViewModel(repository, obligationId, existingExecution, localStore, userId) as T
    }
}
