package com.opervia.example.serviceCompliance.features.operations.domain.repository

interface QrImageReader {
    suspend fun read(uri: String): String
}
