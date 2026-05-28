package com.example.db

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface VaultDao {
    // Vault Files
    @Query("SELECT * FROM vault_files ORDER BY timestamp DESC")
    fun getAllVaultFiles(): Flow<List<VaultFile>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertVaultFile(file: VaultFile): Long

    @Query("SELECT * FROM vault_files WHERE id = :fileId LIMIT 1")
    suspend fun getVaultFileById(fileId: Long): VaultFile?

    // Sync Queue
    @Query("SELECT * FROM sync_queue WHERE status != 'COMPLETED' ORDER BY isEmergency DESC, timestamp ASC")
    fun getActiveQueueFlow(): Flow<List<SyncQueueItem>>

    @Query("SELECT * FROM sync_queue WHERE status != 'COMPLETED' ORDER BY isEmergency DESC, timestamp ASC")
    suspend fun getActiveQueueList(): List<SyncQueueItem>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertQueueItem(item: SyncQueueItem): Long

    @Query("UPDATE sync_queue SET status = :status, retryCount = :retryCount, lastError = :lastError WHERE id = :id")
    suspend fun updateQueueStatus(id: Long, status: String, retryCount: Int, lastError: String?)

    @Query("UPDATE sync_queue SET status = :status WHERE id = :id")
    suspend fun updateStatusOnly(id: Long, status: String)

    @Query("DELETE FROM sync_queue WHERE id = :id")
    suspend fun deleteQueueItem(id: Long)

    // Device Registration
    @Query("SELECT * FROM device_registration LIMIT 1")
    fun getDeviceRegistrationFlow(): Flow<DeviceRegistration?>

    @Query("SELECT * FROM device_registration LIMIT 1")
    suspend fun getDeviceRegistration(): DeviceRegistration?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertDeviceRegistration(reg: DeviceRegistration)

    @Query("UPDATE device_registration SET isRegisteredOnBackend = :registered WHERE deviceId = :deviceId")
    suspend fun updateRegistrationStatus(deviceId: String, registered: Boolean)

    // Emergency Events
    @Query("SELECT * FROM emergency_events ORDER BY timestamp DESC")
    fun getAllEmergencyEventsFlow(): Flow<List<EmergencyEvent>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertEmergencyEvent(event: EmergencyEvent): Long

    @Query("UPDATE emergency_events SET syncedWithBackend = :synced WHERE id = :id")
    suspend fun updateEmergencyEventSyncStatus(id: Long, synced: Boolean)
}
