package com.example.data.remote

import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Path

data class HealthResponseDto(
    val status: String,
    val version: String,
    val mode: String = "control_plane_only"
)

data class RemoteModelManifestDto(
    val pack_id: String,
    val language_code: String,
    val task_type: String,
    val version: String,
    val download_url: String,
    val sha256_checksum: String,
    val file_size_bytes: Long,
    val required_runtime: String,
    val min_memory_mb: Int,
    val license_name: String,
    val attribution: String,
    val signature_hex: String
)

data class DeviceRegistrationRequestDto(
    val device_fingerprint: String,
    val organization_code: String,
    val app_version: String
)

data class LicenseActionRequestDto(
    val device_id: String,
    val license_key: String
)

data class LicenseResponseDto(
    val active: Boolean,
    val tier: String,
    val expires_at: String,
    val message: String
)

data class ConsentedFeedbackDto(
    val source_lang: String,
    val target_lang: String,
    val original_stt_text: String,
    val corrected_stt_text: String,
    val model_translation: String,
    val corrected_translation: String?,
    val user_consented: Boolean
)

/**
 * Retrofit interface for the optional Whisly Control Plane Backend (FastAPI).
 *
 * PRIVACY RULE: Offline translation NEVER calls this API. This API is strictly for
 * model catalogue updates, enterprise license management, and explicitly consented corrections.
 */
interface WhislyBackendApi {

    @GET("health")
    suspend fun getHealth(): HealthResponseDto

    @GET("model-packs")
    suspend fun getModelPacks(): List<RemoteModelManifestDto>

    @GET("model-packs/{pack_id}/manifest")
    suspend fun getPackManifest(@Path("pack_id") packId: String): RemoteModelManifestDto

    @POST("auth/register-device")
    suspend fun registerDevice(@Body request: DeviceRegistrationRequestDto): Map<String, String>

    @POST("auth/activate-license")
    suspend fun activateLicense(@Body request: LicenseActionRequestDto): LicenseResponseDto

    @POST("auth/renew-license")
    suspend fun renewLicense(@Body request: LicenseActionRequestDto): LicenseResponseDto

    @POST("feedback")
    suspend fun submitConsentedFeedback(@Body feedback: ConsentedFeedbackDto): Map<String, String>
}
