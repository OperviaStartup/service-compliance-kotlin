package com.opervia.example.serviceCompliance.features.iam.presentation.login

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.opervia.example.serviceCompliance.core.session.UserSession
import com.opervia.example.serviceCompliance.features.iam.domain.AuthRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import retrofit2.HttpException
import java.io.IOException

class LoginViewModel(private val repository: AuthRepository) : ViewModel() {
    private val _state = MutableStateFlow(LoginUiState())
    val state = _state.asStateFlow()

    fun identifierChanged(value: String) {
        val valid = isValidIdentifier(value.trim())
        _state.value = _state.value.copy(
            identifier = value,
            identifierError = null,
            identifierValid = valid,
            message = null
        )
    }

    fun passwordChanged(value: String) {
        _state.value = _state.value.copy(
            password = value,
            passwordError = null,
            passwordValid = value.isNotBlank(),
            message = null
        )
    }

    fun forgotPasswordClicked() {
        _state.value = _state.value.copy(
            message = "Solicita la recuperación de acceso al administrador de tu empresa.",
            messageIsError = false
        )
    }

    fun login(onSuccess: (UserSession) -> Unit) {
        val current = _state.value
        val identifier = current.identifier.trim()
        val validIdentifier = isValidIdentifier(identifier)
        val validPassword = current.password.isNotBlank()

        if (!validIdentifier || !validPassword) {
            _state.value = current.copy(
                identifierError = if (validIdentifier) null else
                    "Ingresa un correo válido o un DNI de 8 dígitos.",
                passwordError = if (validPassword) null else "Ingresa tu contraseña."
            )
            return
        }

        viewModelScope.launch {
            _state.value = current.copy(loading = true, message = null, messageIsError = false)
            try {
                val session = repository.login(identifier, current.password)
                _state.value = LoginUiState()
                onSuccess(session)
            } catch (error: Throwable) {
                val message = when {
                    error is HttpException && error.code() == 401 ->
                        "No pudimos validar tus credenciales."
                    error is HttpException && error.code() == 403 ->
                        "Tu cuenta no está activa o no tiene acceso a esta aplicación."
                    error is IOException ->
                        "No se pudo conectar. Revisa tu conexión e inténtalo otra vez."
                    else -> "Ocurrió un error al iniciar sesión."
                }
                _state.value = _state.value.copy(
                    loading = false,
                    message = message,
                    messageIsError = true
                )
            }
        }
    }

    private fun isValidIdentifier(value: String): Boolean =
        android.util.Patterns.EMAIL_ADDRESS.matcher(value).matches() ||
                value.matches(Regex("\\d{8}"))
}

class LoginViewModelFactory(
    private val repository: AuthRepository
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        require(modelClass.isAssignableFrom(LoginViewModel::class.java))
        return LoginViewModel(repository) as T
    }
}