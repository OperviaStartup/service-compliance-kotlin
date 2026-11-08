package com.opervia.example.serviceCompliance.core.session

data class UserSession(
    val accessToken: String,
    val refreshToken: String,
    val userId: Long,
    val fullName: String,
    val email: String,
    val documentNumber: String,
    val role: String
)