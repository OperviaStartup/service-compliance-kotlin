package com.opervia.example.serviceCompliance.features.operations.domain.model

enum class PhotoKind { PHOTO_REQUIREMENT, EVIDENCE_REQUIREMENT }

/** Archivo privado y solicitud de subida; no se considera evidencia hasta confirmarlo con la API. */
data class PhotoDraft(
    val executionId: Long,
    val requirementId: Long,
    val kind: PhotoKind,
    val fileName: String,
    val idempotencyKey: String,
    val capturedAt: String? = null,
    val contentType: String? = null,
    val checksum: String? = null,
    val location: PhotoLocation? = null,
    val expectedVersion: Long? = null,
    val uploadPending: Boolean = false
)

data class PhotoLocation(val latitude: Double, val longitude: Double, val accuracyMeters: Double, val capturedAt: String? = null)
data class CapturedPhoto(val bytes: ByteArray, val contentType: String, val checksum: String, val capturedAt: String)
data class PrivatePhoto(val bytes: ByteArray, val contentType: String)
data class PhotoUpload(val draft: PhotoDraft, val bytes: ByteArray, val type: EvidenceType = EvidenceType.PHOTO)

const val MAX_SIGNATURE_BYTES = 1024 * 1024

const val MAX_PHOTO_BYTES = 5 * 1024 * 1024
