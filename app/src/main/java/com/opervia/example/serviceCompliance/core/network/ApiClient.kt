package com.opervia.example.serviceCompliance.core.network

import com.google.gson.Gson
import com.opervia.example.serviceCompliance.core.session.SessionStore
import com.opervia.example.serviceCompliance.features.iam.data.remote.AuthApi
import com.opervia.example.serviceCompliance.features.operations.data.OperationsApi
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.net.URI
import java.util.concurrent.TimeUnit

class ApiClient(baseUrl: String, sessionStore: SessionStore) {
    init {
        val uri = URI(baseUrl)
        val isLocalHost = uri.host in setOf("10.0.2.2", "127.0.0.1", "localhost")
        require(uri.host != null && uri.userInfo == null && uri.query == null && uri.fragment == null) {
            "The API base URL must contain a valid host without credentials or URL parameters."
        }
        require(uri.scheme == "https" || (uri.scheme == "http" && isLocalHost)) {
            "HTTPS is required except for local development hosts."
        }
        require(baseUrl.endsWith('/')) { "The Retrofit base URL must end with '/'." }
    }

    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .addInterceptor(AuthInterceptor(sessionStore))
        .build()

    private val retrofit = Retrofit.Builder()
        .baseUrl(baseUrl)
        .client(client)
        .addConverterFactory(GsonConverterFactory.create(Gson()))
        .build()

    val authApi: AuthApi = retrofit.create(AuthApi::class.java)
    internal val operationsApi: OperationsApi = retrofit.create(OperationsApi::class.java)
}
