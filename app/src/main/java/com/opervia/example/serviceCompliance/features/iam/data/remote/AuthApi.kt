package com.opervia.example.serviceCompliance.features.iam.data.remote

import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST

interface AuthApi {
    @POST("api/v1/auth/login")
    suspend fun login(@Body request: LoginRequestDto): LoginResponseDto

    @POST("api/v1/auth/refresh")
    suspend fun refresh(@Body request: RefreshRequestDto): LoginResponseDto

    @POST("api/v1/auth/logout")
    suspend fun logout(@Body request: RefreshRequestDto)

    @GET("api/v1/auth/me")
    suspend fun me(): CurrentUserResponse
}

