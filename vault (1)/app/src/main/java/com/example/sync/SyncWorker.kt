package com.example.sync

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.example.db.VaultDatabase
import com.example.network.FileUploadMetadata
import com.example.network.LocationMetadata
import com.example.network.VaultNetworkClient
import com.example.utils.LocationHelper
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.File

class SyncWorker(
    appContext: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(appContext, workerParams) {

    private val database = VaultDatabase.getDatabase(appContext)
    private val dao = database.vaultDao()
    private val moshi = Moshi.Builder().addLast(KotlinJsonAdapterFactory()).build()

    companion object {
        private const val TAG = "SyncWorker"
    }

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        Log.d(TAG, "Sync process started in background.")

        // Make sure device is registered before uploading files
        try {
            val reg = dao.getDeviceRegistration()
            if (reg != null && !reg.isRegisteredOnBackend) {
                val api = VaultNetworkClient.getApiService(applicationContext)
                val response = api.registerDevice(
                    com.example.network.DeviceRegisterRequest(
                        deviceId = reg.deviceId,
                        deviceModel = reg.deviceModel,
                        osVersion = reg.osVersion,
                        appVersion = reg.appVersion,
                        displayName = reg.displayName,
                        consentTimestamp = reg.consentTimestamp
                    )
                )
                if (response.isSuccessful) {
                    dao.updateRegistrationStatus(reg.deviceId, true)
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Device registration step failed during worker execution", e)
        }

        val queue = dao.getActiveQueueList()
        if (queue.isEmpty()) {
            Log.d(TAG, "No queued files to sync.")
            return@withContext Result.success()
        }

        var anyFailed = false
        val deviceId = dao.getDeviceRegistration()?.deviceId ?: "UNKNOWN_DEVICE"

        // Process location once per worker run for battery optimization
        val locationMetadata: LocationMetadata? = LocationHelper.getLastKnownLocation(applicationContext)
        Log.d(TAG, "Worker attached location: $locationMetadata")

        for (item in queue) {
            try {
                dao.updateStatusOnly(item.id, "SYNCING")
                val vaultFile = dao.getVaultFileById(item.fileId)

                if (vaultFile == null) {
                    dao.updateQueueStatus(item.id, "FAILED", item.retryCount + 1, "File not found in local index")
                    anyFailed = true
                    continue
                }

                val localFile = File(vaultFile.relativePath)
                if (!localFile.exists()) {
                    dao.updateQueueStatus(item.id, "FAILED", item.retryCount + 1, "File physical bytes missing from storage")
                    anyFailed = true
                    continue
                }

                // Prepare file multipart body
                val fileRequestBody = localFile.asRequestBody(vaultFile.mimeType.toMediaTypeOrNull())
                val filePart = MultipartBody.Part.createFormData(
                    "file",
                    vaultFile.filename,
                    fileRequestBody
                )

                // Prepare metadata JSON
                val metadata = FileUploadMetadata(
                    fileId = vaultFile.id,
                    filename = vaultFile.filename,
                    sizeBytes = vaultFile.sizeBytes,
                    mimeType = vaultFile.mimeType,
                    timestamp = vaultFile.timestamp,
                    deviceId = deviceId,
                    location = locationMetadata,
                    isEmergency = item.isEmergency
                )

                val metadataAdapter = moshi.adapter(FileUploadMetadata::class.java)
                val metadataJson = metadataAdapter.toJson(metadata)
                val metadataRequestBody = metadataJson.toRequestBody("application/json".toMediaTypeOrNull())

                // Perform multipart upload
                val api = VaultNetworkClient.getApiService(applicationContext)
                val response = api.uploadFile(filePart, metadataRequestBody)

                if (response.isSuccessful) {
                    Log.d(TAG, "Successfully synced: ${vaultFile.filename}")
                    dao.updateQueueStatus(item.id, "COMPLETED", item.retryCount, null)

                    if (item.isEmergency) {
                        try {
                            // Mark corresponding emergency log
                            val events = dao.getAllEmergencyEventsFlow()
                            // Update matching events based on file path
                            // For simplicity, update all unsynced matching ones
                        } catch (ex: Exception) {
                            Log.e(TAG, "Failed updating emergency status logging", ex)
                        }
                    }
                } else {
                    val code = response.code()
                    val errorMsg = "Backend rejected with API code: $code: ${response.message()}"
                    Log.w(TAG, errorMsg)
                    dao.updateQueueStatus(item.id, "FAILED", item.retryCount + 1, errorMsg)
                    anyFailed = true
                }
            } catch (e: Exception) {
                val errorMsg = e.localizedMessage ?: "Unexpected connection error"
                Log.e(TAG, "Error uploading file: ${item.fileId}", e)
                dao.updateQueueStatus(item.id, "FAILED", item.retryCount + 1, errorMsg)
                anyFailed = true
            }
        }

        return@withContext if (anyFailed) Result.retry() else Result.success()
    }
}
