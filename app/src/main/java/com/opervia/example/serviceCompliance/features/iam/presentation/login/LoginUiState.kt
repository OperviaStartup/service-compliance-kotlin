package com.opervia.example.serviceCompliance.features.iam.presentation.login

data class LoginUiState(
    val identifier: String = "",
    val password: String = "",
    val identifierError: String? = null,
    val identifierValid: Boolean = false,
    val passwordValid: Boolean = false,
    val passwordError: String? = null,
    val message: String? = null,
    val messageIsError: Boolean = false,
    val loading: Boolean = false
)