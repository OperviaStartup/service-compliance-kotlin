package com.opervia.example.serviceCompliance.app

import android.content.Context
import com.opervia.example.serviceCompliance.core.network.ApiClient
import com.opervia.example.serviceCompliance.core.session.SessionStore
import com.opervia.example.serviceCompliance.features.iam.data.AuthRepositoryImpl
import com.opervia.example.serviceCompliance.features.iam.presentation.login.LoginViewModelFactory

class AppContainer(context: Context, baseUrl: String) {
    val sessionStore = SessionStore(context)
    private val apiClient = ApiClient(baseUrl, sessionStore)
    val authRepository = AuthRepositoryImpl(apiClient.authApi, sessionStore)
    val loginViewModelFactory = LoginViewModelFactory(authRepository)
}