package com.opervia.example.serviceCompliance.app

import android.content.Context
import com.opervia.example.serviceCompliance.core.network.ApiClient
import com.opervia.example.serviceCompliance.core.session.SessionStore
import com.opervia.example.serviceCompliance.features.iam.data.AuthRepositoryImpl
import com.opervia.example.serviceCompliance.features.iam.presentation.login.LoginViewModelFactory
import com.opervia.example.serviceCompliance.features.operations.data.FieldRepository
import com.opervia.example.serviceCompliance.features.operations.data.LocalFieldOperationsStore
import com.opervia.example.serviceCompliance.features.operations.domain.repository.OperationsRepository

class AppContainer(context: Context, baseUrl: String) {
    val sessionStore = SessionStore(context)
    val localFieldOperationsStore = LocalFieldOperationsStore(context)
    private val apiClient = ApiClient(baseUrl, sessionStore)
    val authRepository = AuthRepositoryImpl(apiClient.authApi, sessionStore)
    val operationsRepository: OperationsRepository = FieldRepository(apiClient.operationsApi)
    val loginViewModelFactory = LoginViewModelFactory(authRepository)
}
