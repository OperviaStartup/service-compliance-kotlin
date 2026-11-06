package com.opervia.example.serviceCompliance.features.iam.data

import com.opervia.example.serviceCompliance.core.session.SessionStore
import com.opervia.example.serviceCompliance.core.session.UserSession
import com.opervia.example.serviceCompliance.features.iam.data.remote.AuthApi
import com.opervia.example.serviceCompliance.features.iam.data.remote.LoginRequestDto
import com.opervia.example.serviceCompliance.features.iam.data.remote.CurrentUserResponse
import com.opervia.example.serviceCompliance.features.iam.data.remote.LoginResponseDto
import com.opervia.example.serviceCompliance.features.iam.data.remote.RefreshRequestDto
import com.opervia.example.serviceCompliance.features.iam.domain.AuthRepository
import retrofit2.HttpException

class AuthRepositoryImpl(
    private val api: AuthApi,
    private val sessionStore: SessionStore
) : AuthRepository {

    override suspend fun login(identifier: String, password: String): UserSession {
        val response = api.login(LoginRequestDto(identifier.trim(), password))
        val provisional = response.toSession()
        sessionStore.save(provisional)
        val current = try {
            api.me()
        } catch (error: Exception) {
            sessionStore.clear()
            throw error
        }
            return provisional.copy(
                userId = current.id,
                fullName = current.fullName,
                email = current.email,
                documentNumber = current.documentNumber,
                role = current.role
            ).also(sessionStore::save)
    }

    override suspend fun validateSession(): UserSession? {
        val saved = sessionStore.read() ?: return null
        return try {
            applyCurrentUser(saved, api.me())
        } catch (error: HttpException) {
            if (error.code() != 401) throw error

            try {
                val refreshed = api.refresh(RefreshRequestDto(saved.refreshToken))
                val refreshedSession = refreshed.toSession()
                sessionStore.save(refreshedSession)
                applyCurrentUser(refreshedSession, api.me())
            } catch (refreshError: HttpException) {
                if (refreshError.code() == 401) {
                    sessionStore.clear()
                    null
                } else {
                    throw refreshError
                }
            }
        }
    }

    override suspend fun logout() {
        val saved = sessionStore.read()
        try {
            if (saved != null) api.logout(RefreshRequestDto(saved.refreshToken))
        } finally {
            sessionStore.clear()
        }
    }

    private fun applyCurrentUser(
        session: UserSession,
        current: CurrentUserResponse
    ): UserSession {
        return session.copy(
            userId = current.id,
            fullName = current.fullName,
            email = current.email,
            documentNumber = current.documentNumber,
            role = current.role
        ).also(sessionStore::save)
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
}