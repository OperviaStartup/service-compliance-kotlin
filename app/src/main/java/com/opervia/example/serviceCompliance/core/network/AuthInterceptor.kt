package com.opervia.example.serviceCompliance.core.network

import com.opervia.example.serviceCompliance.core.session.SessionStore
import okhttp3.Interceptor
import okhttp3.Response

class AuthInterceptor(private val sessionStore: SessionStore) : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        val path = request.url().encodedPath()
        val isPublicAuthCall = path.endsWith("/auth/login") || path.endsWith("/auth/refresh")
        val token = if (isPublicAuthCall) null else sessionStore.read()?.accessToken
        val authenticated = request.newBuilder().apply {
            if (token != null) header("Authorization", "Bearer $token")
        }.build()
        return chain.proceed(authenticated)
    }
}