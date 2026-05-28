package com.example.network

import okhttp3.MultipartBody
import okhttp3.RequestBody
import okhttp3.ResponseBody
import retrofit2.Response
import retrofit2.http.*

interface VaultApiService {

    @POST("api/device/register")
    suspend fun registerDevice(
        @Body request: DeviceRegisterRequest
    ): Response<ResponseBody>

    @Multipart
    @POST("api/files/upload")
    suspend fun uploadFile(
        @Part file: MultipartBody.Part,
        @Part("metadata") metadata: RequestBody
    ): Response<ResponseBody>
}

data class DeviceRegisterRequest(
    val deviceId: String,
    val deviceModel: String,
    val osVersion: String,
    val appVersion: String,
    val displayName: String,
    val consentTimestamp: Long
)

data class FileUploadMetadata(
    val fileId: Long,
    val filename: String,
    val sizeBytes: Long,
    val mimeType: String,
    val timestamp: Long,
    val deviceId: String,
    val location: LocationMetadata?,
    val isEmergency: Boolean
)

data class LocationMetadata(
    val latitude: Double,
    val longitude: Double,
    val address: String?
)
