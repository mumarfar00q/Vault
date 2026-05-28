package com.example.repository

import android.content.Context
import android.os.Build
import android.provider.Settings
import android.util.Log
import androidx.work.*
import com.example.db.*
import com.example.network.DeviceRegisterRequest
import com.example.network.VaultApiService
import com.example.network.VaultNetworkClient
import com.example.sync.SyncWorker
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.security.MessageDigest
import java.util.UUID
import java.util.concurrent.TimeUnit

class VaultRepository(private val context: Context) {
    private val TAG = "VaultRepository"
    private val database = VaultDatabase.getDatabase(context)
    private val dao = database.vaultDao()

    val allVaultFiles: Flow<List<VaultFile>> = dao.getAllVaultFiles()
    val activeQueue: Flow<List<SyncQueueItem>> = dao.getActiveQueueFlow()
    val deviceRegistration: Flow<DeviceRegistration?> = dao.getDeviceRegistrationFlow()
    val emergencyEvents: Flow<List<EmergencyEvent>> = dao.getAllEmergencyEventsFlow()

    // Base URL delegation to network client
    var apiBaseUrl: String
        get() = VaultNetworkClient.baseUrl
        set(value) {
            VaultNetworkClient.baseUrl = value
        }

    suspend fun getOrCreateDeviceRegistration(): DeviceRegistration {
        return withContext(Dispatchers.IO) {
            val existing = dao.getDeviceRegistration()
            if (existing != null) {
                return@withContext existing
            }

            // Create new registration state
            val dynamicId = try {
                Settings.Secure.getString(context.contentResolver, Settings.Secure.ANDROID_ID)
                    ?: UUID.randomUUID().toString()
            } catch (e: Exception) {
                UUID.randomUUID().toString()
            }

            val model = "${Build.MANUFACTURER} ${Build.MODEL}"
            val osVersion = "Android ${Build.VERSION.RELEASE}"
            val appVersion = try {
                val pInfo = context.packageManager.getPackageInfo(context.packageName, 0)
                pInfo.versionName ?: "1.0.0"
            } catch (e: Exception) {
                "1.0.0"
            }

            val displayName = "My Vault Phone"
            val reg = DeviceRegistration(
                deviceId = dynamicId,
                deviceModel = model,
                osVersion = osVersion,
                appVersion = appVersion,
                displayName = displayName,
                consentTimestamp = System.currentTimeMillis(),
                isRegisteredOnBackend = false
            )

            dao.insertDeviceRegistration(reg)
            reg
        }
    }

    suspend fun registerDeviceOnBackend(): Boolean {
        return withContext(Dispatchers.IO) {
            try {
                val reg = getOrCreateDeviceRegistration()
                if (reg.isRegisteredOnBackend) return@withContext true

                val api = VaultNetworkClient.getApiService(context)
                val response = api.registerDevice(
                    DeviceRegisterRequest(
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
                    Log.d(TAG, "Device registration validated successfully on backend.")
                    true
                } else {
                    Log.e(TAG, "Device registration failed: ${response.code()} ${response.message()}")
                    false
                }
            } catch (e: Exception) {
                Log.e(TAG, "Network exception during device registration", e)
                false
            }
        }
    }

    // Encrypted file import simulation
    suspend fun importFileToVault(filename: String, fileContent: String, mimeType: String): Long {
        return withContext(Dispatchers.IO) {
            // Simulate rigorous client-side encryption (AES/hash)
            val baseBytes = fileContent.toByteArray(Charsets.UTF_8)
            val encryptedBytes = xorEncryptDecrypt(baseBytes) // Simulated Secure Encryption
            val md = MessageDigest.getInstance("SHA-256")
            val hashBytes = md.digest(encryptedBytes)
            val hashString = hashBytes.joinToString("") { "%02x".format(it) }

            // Store inside app private space
            val destinationFile = File(context.filesDir, "enc_$filename")
            FileOutputStream(destinationFile).use { fos ->
                fos.write(encryptedBytes)
            }

            // Store in Room
            val vaultFile = VaultFile(
                filename = filename,
                relativePath = destinationFile.absolutePath,
                sizeBytes = destinationFile.length(),
                mimeType = mimeType,
                encryptedKeyHash = hashString,
                isEncrypted = true
            )
            val fileId = dao.insertVaultFile(vaultFile)

            // Add straight to local sync queue
            val queueItem = SyncQueueItem(
                fileId = fileId,
                status = "PENDING",
                isEmergency = false
            )
            dao.insertQueueItem(queueItem)

            // Auto-trigger sync when added
            triggerImmediateSync()

            fileId
        }
    }

    // Capture an Emergency failed authentication security-event
    suspend fun logEmergencyEvent(details: String) {
        withContext(Dispatchers.IO) {
            val eventId = System.currentTimeMillis()
            val filename = "security_event_$eventId.log"
            val content = "EMERGENCY SECURITY EVENT\nTimestamp: $eventId\nReason: $details\nDevice Model: ${Build.MODEL}"

            // Encrypt and save
            val fileId = importFileToVault(filename, content, "text/plain")

            // Raise priority in the sync queue as emergency
            val activeQueueList = dao.getActiveQueueList()
            val addedItem = activeQueueList.find { it.fileId == fileId }
            if (addedItem != null) {
                val updatedEmergencyItem = addedItem.copy(isEmergency = true)
                dao.insertQueueItem(updatedEmergencyItem)
            }

            // Record event history
            dao.insertEmergencyEvent(
                EmergencyEvent(
                    eventType = "FAILED_AUTH",
                    details = details,
                    payloadFilePath = File(context.filesDir, "enc_$filename").absolutePath,
                    syncedWithBackend = false
                )
            )

            // Trigger sync immediately to backup the capture
            triggerImmediateSync()
        }
    }

    // Trigger local immediate sync process
    fun triggerImmediateSync() {
        val syncRequest = OneTimeWorkRequestBuilder<SyncWorker>()
            .setExpedited(OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST)
            .build()
        WorkManager.getInstance(context).enqueueUniqueWork(
            "one_time_vault_sync",
            ExistingWorkPolicy.REPLACE,
            syncRequest
        )
    }

    // Schedule background periodic synchronization (every 15 minutes)
    fun schedulePeriodicSync() {
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()

        val periodicRequest = PeriodicWorkRequestBuilder<SyncWorker>(15, TimeUnit.MINUTES)
            .setConstraints(constraints)
            .build()

        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            "periodic_vault_sync",
            ExistingPeriodicWorkPolicy.KEEP,
            periodicRequest
        )
        Log.d(TAG, "Periodic Background WorkManager sync initiated (every 15 mins).")
    }

    // Simple symmetric transformation representing robust military encryption bytes
    private fun xorEncryptDecrypt(input: ByteArray): ByteArray {
        val key = "V_A_U_L_T_S_Y_N_C_S_E_C_U_R_E"
        val out = ByteArray(input.size)
        for (i in input.indices) {
            out[i] = (input[i].toInt() xor key[i % key.length].code).toByte()
        }
        return out
    }
}
