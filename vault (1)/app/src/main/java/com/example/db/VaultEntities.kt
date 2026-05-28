package com.example.db

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "vault_files")
data class VaultFile(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val filename: String,
    val relativePath: String,
    val sizeBytes: Long,
    val mimeType: String,
    val encryptedKeyHash: String,
    val isEncrypted: Boolean = true,
    val timestamp: Long = System.currentTimeMillis()
)

@Entity(tableName = "sync_queue")
data class SyncQueueItem(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val fileId: Long,
    val status: String, // PENDING, SYNCING, COMPLETED, FAILED
    val retryCount: Int = 0,
    val lastError: String? = null,
    val timestamp: Long = System.currentTimeMillis(),
    val latitude: Double? = null,
    val longitude: Double? = null,
    val address: String? = null,
    val isEmergency: Boolean = false
)

@Entity(tableName = "device_registration")
data class DeviceRegistration(
    @PrimaryKey val deviceId: String,
    val deviceModel: String,
    val osVersion: String,
    val appVersion: String,
    val displayName: String,
    val consentTimestamp: Long,
    val isRegisteredOnBackend: Boolean = false
)

@Entity(tableName = "emergency_events")
data class EmergencyEvent(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val eventType: String, // e.g., FAILED_AUTH
    val timestamp: Long = System.currentTimeMillis(),
    val details: String,
    val payloadFilePath: String? = null,
    val syncedWithBackend: Boolean = false
)
