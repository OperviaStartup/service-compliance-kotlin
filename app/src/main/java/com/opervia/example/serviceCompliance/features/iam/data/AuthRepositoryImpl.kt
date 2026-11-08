package com.opervia.example.serviceCompliance.features.iam.data

import com.opervia.example.serviceCompliance.core.session.SessionStore
import com.opervia.example.serviceCompliance.core.session.UserSession
import com.opervia.example.serviceCompliance.features.iam.data.remote.AuthApi
import com.opervia.example.serviceCompliance.features.iam.data.remote.LoginRequestDto
import com.opervia.example.serviceCompliance.features.iam.data.remote.CurrentUserResponse
import com.opervia.example.serviceCompliance.features.iam.data.remote.LoginResponseDto
import com.opervia.example.serviceCompliance.features.iam.data.remote.RefreshRequestDto
import com.opervia.example.serviceCompliance.features.iam.domain.AuthRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import retrofit2.HttpException

class AuthRepositoryImpl(
    private val api: AuthApi,
    private val sessionStore: SessionStore
) : AuthRepository {

    override suspend fun login(identifier: String, password: String): UserSession {
        val response = api.login(LoginRequestDto(identifier.trim(), password))
        val provisional = response.toSession()
        saveSession(provisional)
        val current = try {
            api.me()
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: HttpException) {
            if (error.code() == 401 || error.code() == 403) clearSession()
            throw error
        }
        val authenticated = provisional.copy(
                userId = current.id,
                fullName = current.fullName,
                email = current.email,
                documentNumber = current.documentNumber,
                role = current.role
        )
        saveSession(authenticated)
        return authenticated
    }

    override suspend fun validateSession(): UserSession? {
        val saved = readSession() ?: return null
        return try {
            applyCurrentUser(saved, api.me())
        } catch (error: HttpException) {
            if (error.code() != 401) throw error

            try {
                val refreshed = api.refresh(RefreshRequestDto(saved.refreshToken))
                val refreshedSession = refreshed.toSession()
                saveSession(refreshedSession)
                applyCurrentUser(refreshedSession, api.me())
            } catch (refreshError: HttpException) {
                if (refreshError.code() == 401) {
                    clearSession()
                    null
                } else {
                    throw refreshError
                }
            }
        }
    }

    override suspend fun logout() {
        val saved = readSession()
        try {
            if (saved != null) api.logout(RefreshRequestDto(saved.refreshToken))
        } finally {
            clearSession()
        }
    }

    private suspend fun applyCurrentUser(
        session: UserSession,
        current: CurrentUserResponse
    ): UserSession {
        val updated = session.copy(
            userId = current.id,
            fullName = current.fullName,
            email = current.email,
            documentNumber = current.documentNumber,
            role = current.role
        )
        saveSession(updated)
        return updated
    }

    private fun LoginResponseDto.toSession() = UserSession(
        accessToken = token,
        refreshToken = refreshToken,
        userId = userId,
        fullName = fullName,
        email = email,
        documentNumber = documentNumber,
        role = role
    )

    private suspend fun saveSession(session: UserSession) {
        withContext(Dispatchers.IO) { sessionStore.save(session) }
    }

    private suspend fun readSession(): UserSession? = withContext(Dispatchers.IO) { sessionStore.read() }

    private suspend fun clearSession() {
        withContext(Dispatchers.IO) { sessionStore.clear() }
    }
}
