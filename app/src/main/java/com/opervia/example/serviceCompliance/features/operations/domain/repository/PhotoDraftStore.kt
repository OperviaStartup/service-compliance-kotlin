package com.opervia.example.serviceCompliance.features.operations.domain.repository

import com.opervia.example.serviceCompliance.features.operations.domain.model.PhotoDraft
import com.opervia.example.serviceCompliance.features.operations.domain.model.PhotoKind

interface PhotoDraftStore {
    suspend fun hasPendingSubmission(userId: Long, obligationId: Long): Boolean
    suspend fun photoDraft(userId: Long, executionId: Long, kind: PhotoKind, requirementId: Long): PhotoDraft?
    suspend fun savePhotoDraft(userId: Long, draft: PhotoDraft)
    suspend fun removePhotoDraft(userId: Long, draft: PhotoDraft)
}
