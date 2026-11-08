package com.opervia.example.serviceCompliance.features.iam.domain

import com.opervia.example.serviceCompliance.core.session.UserSession

interface AuthRepository {
    suspend fun login(identifier: String, password: String): UserSession
    suspend fun validateSession(): UserSession?
    suspend fun logout()
}