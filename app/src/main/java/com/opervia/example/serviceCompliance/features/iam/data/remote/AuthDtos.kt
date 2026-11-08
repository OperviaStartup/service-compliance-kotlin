package com.opervia.example.serviceCompliance.features.iam.data.remote

// Request DTOs
data class LoginRequestDto(
    val identifier: String,
    val password: String
)

// Response DTOs
data class LoginResponseDto(
    val token: String,
    val userId: Long,
    val fullName: String,
    val email: String,
    val documentNumber: String,
    val role: String,
    val refreshToken: String
)

// Refresh DTO
data class RefreshRequestDto(
    val refreshToken: String
)

data class CurrentUserResponse(
    val id: Long,
    val fullName: String,
    val email: String,
    val documentNumber: String,
    val role: String
)