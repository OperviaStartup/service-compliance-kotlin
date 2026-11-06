package com.opervia.example.serviceCompliance.core.network

import com.google.gson.Gson
import com.opervia.example.serviceCompliance.core.session.SessionStore
import com.opervia.example.serviceCompliance.features.iam.data.remote.AuthApi
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

class ApiClient(baseUrl: String, sessionStore: SessionStore) {
    private val client = OkHttpClient.Builder()
        .addInterceptor(AuthInterceptor(sessionStore))
        .build()

    val authApi: AuthApi = Retrofit.Builder()
        .baseUrl(baseUrl)
        .client(client)
        .addConverterFactory(GsonConverterFactory.create(Gson()))
        .build()
        .create(AuthApi::class.java)
}