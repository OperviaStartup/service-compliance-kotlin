package com.upc.example.serviceComplianceKotlin.fieldoperations

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import com.upc.example.serviceComplianceKotlin.fieldoperations.data.FieldRepository
import com.upc.example.serviceComplianceKotlin.fieldoperations.viewmodel.FieldViewModel

@Composable
fun FieldOperationsRoute(
    baseUrl: String,
    bearerToken: String,
    obligationId: Long,
    onExit: () -> Unit
) {
    require(obligationId > 0) { "The obligation ID must be positive" }
    val factory = remember(baseUrl, bearerToken, obligationId) {
        FieldViewModelFactory(FieldRepository(baseUrl, bearerToken), obligationId)
    }
    val model: FieldViewModel = viewModel(key = "field-obligation-$obligationId", factory = factory)
    FieldOperationsFlow(model, onExit)
}

private class FieldViewModelFactory(
    private val repository: FieldRepository,
    private val obligationId: Long
) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        require(modelClass == FieldViewModel::class.java) { "Unsupported ViewModel type" }
        @Suppress("UNCHECKED_CAST")
        return FieldViewModel(repository, obligationId) as T
    }
}
