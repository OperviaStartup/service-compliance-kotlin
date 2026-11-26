package com.opervia.example.serviceCompliance.features.operations.domain.repository

import com.opervia.example.serviceCompliance.features.operations.domain.model.CapturedPhoto
import com.opervia.example.serviceCompliance.features.operations.domain.model.PhotoDraft
import com.opervia.example.serviceCompliance.features.operations.domain.model.PhotoLocation
import java.io.File

interface PhotoFiles {
    fun create(userId: Long, executionId: Long): String
    fun file(userId: Long, draft: PhotoDraft): File
    fun inspect(userId: Long, draft: PhotoDraft): CapturedPhoto
    fun discard(userId: Long, draft: PhotoDraft)
}

interface PhotoLocationProvider {
    suspend fun current(): PhotoLocation
}
