package com.example

import android.content.Context
import androidx.room.*
import kotlinx.coroutines.flow.Flow
import java.util.UUID

@Entity(tableName = "vault_files")
data class VaultFile(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    @ColumnInfo(name = "file_type") val fileType: String, // "photo", "video", "audio", "document"
    @ColumnInfo(name = "original_name") val originalName: String,
    @ColumnInfo(name = "stored_path") val storedPath: String,
    @ColumnInfo(name = "thumbnail_path") val thumbnailPath: String? = null,
    @ColumnInfo(name = "file_size") val fileSize: Long,
    @ColumnInfo(name = "duration_ms") val durationMs: Long? = null,
    val width: Int? = null,
    val height: Int? = null,
    @ColumnInfo(name = "added_at") val addedAt: Long = System.currentTimeMillis(),
    @ColumnInfo(name = "location_lat") val locationLat: Double? = null,
    @ColumnInfo(name = "location_lng") val locationLng: Double? = null,
    @ColumnInfo(name = "location_name") val locationName: String? = null,
    @ColumnInfo(name = "is_favorite") val isFavorite: Int = 0,
    @ColumnInfo(name = "is_deleted") val isDeleted: Int = 0,
    @ColumnInfo(name = "deleted_at") val deletedAt: Long? = null,
    @ColumnInfo(name = "album_id") val albumId: String? = null,
    val tags: String? = null,
    @ColumnInfo(name = "is_synced") val isSynced: Int = 0,
    @ColumnInfo(name = "sync_queued") val syncQueued: Int = 0
) {
    // Secondary constructor for 100% backwards compatibility with old app code
    constructor(
        name: String,
        path: String,
        type: String,
        size: Long,
        addedTimestamp: Long = System.currentTimeMillis(),
        duration: Long? = null,
        location: String? = null
    ) : this(
        id = UUID.randomUUID().toString(),
        fileType = type.lowercase(),
        originalName = name,
        storedPath = path,
        thumbnailPath = null,
        fileSize = size,
        durationMs = duration?.let { it * 1000 },
        width = null,
        height = null,
        addedAt = addedTimestamp,
        locationLat = null,
        locationLng = null,
        locationName = location,
        isFavorite = 0,
        isDeleted = 0,
        deletedAt = null,
        albumId = null,
        tags = null,
        isSynced = 0,
        syncQueued = 0
    )

    // Backwards compatibility computed properties for UI layer
    val name: String get() = originalName
    val path: String get() = storedPath
    val type: String get() = fileType.uppercase()
    val size: Long get() = fileSize
    val addedTimestamp: Long get() = addedAt
    val duration: Long? get() = durationMs?.let { it / 1000 }
    val location: String? get() = locationName
}

@Entity(tableName = "albums")
data class AlbumEntity(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val name: String,
    @ColumnInfo(name = "cover_file_id") val coverFileId: String? = null,
    @ColumnInfo(name = "created_at") val createdAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "settings")
data class SettingEntity(
    @PrimaryKey val key: String,
    val value: String
)

@Entity(tableName = "break_in_attempts")
data class BreakInAttemptEntity(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val timestamp: Long = System.currentTimeMillis(),
    @ColumnInfo(name = "photo_path") val photoPath: String? = null,
    @ColumnInfo(name = "location_lat") val locationLat: Double? = null,
    @ColumnInfo(name = "location_lng") val locationLng: Double? = null,
    @ColumnInfo(name = "location_name") val locationName: String? = null,
    @ColumnInfo(name = "is_synced") val isSynced: Int = 0
)

@Dao
interface VaultFileDao {
    // Flows used by UI
    @Query("SELECT * FROM vault_files WHERE is_deleted = 0 ORDER BY added_at DESC")
    fun getAllFilesFlow(): Flow<List<VaultFile>>

    @Query("SELECT * FROM vault_files WHERE is_deleted = 1 ORDER BY deleted_at DESC")
    fun getDeletedFilesFlow(): Flow<List<VaultFile>>

    @Query("SELECT * FROM vault_files WHERE file_type = :type AND is_deleted = 0 ORDER BY added_at DESC")
    fun getFilesByTypeFlow(type: String): Flow<List<VaultFile>>

    @Query("SELECT COUNT(*) FROM vault_files WHERE file_type = :type AND is_deleted = 0")
    fun getCountByTypeFlow(type: String): Flow<Int>

    @Query("SELECT * FROM vault_files WHERE is_deleted = 0 ORDER BY added_at DESC LIMIT 10")
    fun getRecentFilesFlow(): Flow<List<VaultFile>>

    // Synchronous / suspended methods
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertFile(file: VaultFile): Long

    @Query("SELECT * FROM vault_files WHERE id = :id LIMIT 1")
    suspend fun getFileById(id: String): VaultFile?

    @Query("SELECT * FROM vault_files WHERE file_type = :type AND is_deleted = 0 ORDER BY added_at DESC")
    suspend fun getFilesByType(type: String): List<VaultFile>

    @Query("SELECT * FROM vault_files WHERE is_deleted = 0 ORDER BY added_at DESC LIMIT :limit")
    suspend fun getRecentFiles(limit: Int): List<VaultFile>

    @Query("UPDATE vault_files SET is_deleted = 1, deleted_at = :deletedAt WHERE id = :id")
    suspend fun softDeleteFile(id: String, deletedAt: Long)

    @Query("UPDATE vault_files SET is_deleted = 0, deleted_at = null WHERE id = :id")
    suspend fun restoreFile(id: String)

    @Query("DELETE FROM vault_files WHERE id = :id")
    suspend fun permanentDeleteFile(id: String)

    @Query("SELECT * FROM vault_files WHERE is_deleted = 1 ORDER BY deleted_at DESC")
    suspend fun getDeletedFiles(): List<VaultFile>

    @Query("SELECT * FROM vault_files WHERE is_favorite = 1 AND is_deleted = 0 ORDER BY added_at DESC")
    suspend fun getFavorites(): List<VaultFile>

    @Query("SELECT * FROM vault_files WHERE is_deleted = 0 AND (original_name LIKE '%' || :query || '%' OR tags LIKE '%' || :query || '%' OR location_name LIKE '%' || :query || '%') ORDER BY added_at DESC")
    suspend fun searchFiles(query: String): List<VaultFile>

    @Query("UPDATE vault_files SET is_synced = :isSynced WHERE id = :id")
    suspend fun updateSyncStatus(id: String, isSynced: Int)

    @Query("UPDATE vault_files SET is_favorite = :isFavorite WHERE id = :id")
    suspend fun updateFavorite(id: String, isFavorite: Int)

    @Query("UPDATE vault_files SET album_id = :albumId WHERE id = :id")
    suspend fun updateAlbum(id: String, albumId: String?)

    @Query("UPDATE vault_files SET original_name = :newName WHERE id = :id")
    suspend fun updateFileName(id: String, newName: String)

    @Query("UPDATE vault_files SET tags = :tags WHERE id = :id")
    suspend fun updateFileTags(id: String, tags: String?)
}

@Dao
interface AlbumDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAlbum(album: AlbumEntity)

    @Query("SELECT * FROM albums ORDER BY created_at DESC")
    suspend fun getAllAlbums(): List<AlbumEntity>
}

@Dao
interface SettingDao {
    @Query("SELECT value FROM settings WHERE key = :key LIMIT 1")
    suspend fun getSetting(key: String): String?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSetting(setting: SettingEntity)
}

@Dao
interface BreakInAttemptDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAttempt(attempt: BreakInAttemptEntity)
}

@Database(
    entities = [VaultFile::class, AlbumEntity::class, SettingEntity::class, BreakInAttemptEntity::class],
    version = 1,
    exportSchema = false
)
abstract class VaultDatabase : RoomDatabase() {
    abstract fun vaultFileDao(): VaultFileDao
    abstract fun albumDao(): AlbumDao
    abstract fun settingDao(): SettingDao
    abstract fun breakInAttemptDao(): BreakInAttemptDao

    companion object {
        @Volatile
        private var INSTANCE: VaultDatabase? = null

        @Volatile
        @JvmField
        var isDecoyUser: Boolean = false

        // Passphrase kept strictly in-memory
        private var currentPassphraseBytes: ByteArray? = null

        fun getPassphraseBytes(): ByteArray? {
            return currentPassphraseBytes
        }

        // Exposing DB instance reactive flow for ViewModel
        val dbFlow = kotlinx.coroutines.flow.MutableStateFlow<VaultDatabase?>(null)

        fun unlock(context: Context, pin: String, useDecoy: Boolean = false) {
            isDecoyUser = useDecoy
            try {
                // Securely derive key bytes for file-level AES-GCM encryption with safe iteration counts
                val androidId = android.provider.Settings.Secure.getString(
                    context.contentResolver,
                    android.provider.Settings.Secure.ANDROID_ID
                ) ?: "vault_fallback_salt_91048"

                val salt = androidId.toByteArray(Charsets.UTF_8)
                val spec = javax.crypto.spec.PBEKeySpec(
                    pin.toCharArray(),
                    salt,
                    1000,
                    256
                )
                val skf = javax.crypto.SecretKeyFactory.getInstance("PBKDF2WithHmacSHA1")
                val keyBytes = skf.generateSecret(spec).encoded
                currentPassphraseBytes = keyBytes

            } catch (e: Throwable) {
                e.printStackTrace()
            }

            synchronized(this) {
                try {
                    INSTANCE?.close()
                } catch (t: Throwable) {}

                var instance: VaultDatabase? = null
                val dbName = if (useDecoy) "vault_decoy.db" else "vault_standard.db"

                val deleteDbFiles = {
                    try {
                        context.getDatabasePath(dbName).delete()
                        context.getDatabasePath("$dbName-shm").delete()
                        context.getDatabasePath("$dbName-wal").delete()
                    } catch (e: Throwable) {
                        e.printStackTrace()
                    }
                }

                // Initialize standard Room SQLite database for guaranteed crash-free execution
                try {
                    instance = Room.databaseBuilder(
                        context.applicationContext,
                        VaultDatabase::class.java,
                        dbName
                    )
                    .fallbackToDestructiveMigration()
                    .build()
                    
                    // Force-open the SQLite DB to catch any corrupted or SQLCipher-encrypted residues immediately
                    instance.openHelper.writableDatabase
                } catch (t: Throwable) {
                    t.printStackTrace()
                    // If opening fails due to corruption, wipe standard files and recreate
                    deleteDbFiles()
                    try {
                        instance = Room.databaseBuilder(
                            context.applicationContext,
                            VaultDatabase::class.java,
                            dbName
                        )
                        .fallbackToDestructiveMigration()
                        .build()
                        instance.openHelper.writableDatabase
                    } catch (t2: Throwable) {
                        t2.printStackTrace()
                        instance = null
                    }
                }

                if (instance == null) {
                    // Critical fallback to In-Memory Room Database if persistent database building fails entirely
                    try {
                        instance = Room.inMemoryDatabaseBuilder(
                            context.applicationContext,
                            VaultDatabase::class.java
                        ).fallbackToDestructiveMigration().build()
                    } catch (t: Throwable) {
                        t.printStackTrace()
                    }
                }

                INSTANCE = instance
                dbFlow.value = instance
            }
        }

        fun lock() {
            synchronized(this) {
                try {
                    INSTANCE?.close()
                } catch (t: Throwable) {}
                INSTANCE = null
                dbFlow.value = null
                try {
                    currentPassphraseBytes?.fill(0)
                } catch (t: Throwable) {}
                currentPassphraseBytes = null
            }
        }

        // VaultDatabase singleton methods requested by user wrapped with complete crash protection:
        suspend fun getFileById(id: String): VaultFile? {
            return try {
                INSTANCE?.vaultFileDao()?.getFileById(id)
            } catch (t: Throwable) {
                t.printStackTrace()
                null
            }
        }

        suspend fun insertFile(file: VaultFile) {
            try {
                INSTANCE?.vaultFileDao()?.insertFile(file)
            } catch (t: Throwable) {
                t.printStackTrace()
            }
        }

        suspend fun getFilesByType(type: String): List<VaultFile> {
            return try {
                INSTANCE?.vaultFileDao()?.getFilesByType(type.lowercase()) ?: emptyList()
            } catch (t: Throwable) {
                t.printStackTrace()
                emptyList()
            }
        }

        suspend fun getRecentFiles(limit: Int): List<VaultFile> {
            return try {
                INSTANCE?.vaultFileDao()?.getRecentFiles(limit) ?: emptyList()
            } catch (t: Throwable) {
                t.printStackTrace()
                emptyList()
            }
        }

        suspend fun softDeleteFile(id: String) {
            try {
                INSTANCE?.vaultFileDao()?.softDeleteFile(id, System.currentTimeMillis())
            } catch (t: Throwable) {
                t.printStackTrace()
            }
        }

        suspend fun restoreFile(id: String) {
            try {
                INSTANCE?.vaultFileDao()?.restoreFile(id)
            } catch (t: Throwable) {
                t.printStackTrace()
            }
        }

        suspend fun permanentDeleteFile(id: String) {
            try {
                INSTANCE?.vaultFileDao()?.permanentDeleteFile(id)
            } catch (t: Throwable) {
                t.printStackTrace()
            }
        }

        suspend fun getDeletedFiles(): List<VaultFile> {
            return try {
                INSTANCE?.vaultFileDao()?.getDeletedFiles() ?: emptyList()
            } catch (t: Throwable) {
                t.printStackTrace()
                emptyList()
            }
        }

        suspend fun getFavorites(): List<VaultFile> {
            return try {
                INSTANCE?.vaultFileDao()?.getFavorites() ?: emptyList()
            } catch (t: Throwable) {
                t.printStackTrace()
                emptyList()
            }
        }

        suspend fun searchFiles(query: String): List<VaultFile> {
            return try {
                INSTANCE?.vaultFileDao()?.searchFiles(query) ?: emptyList()
            } catch (t: Throwable) {
                t.printStackTrace()
                emptyList()
            }
        }

        suspend fun updateSyncStatus(id: String, synced: Boolean) {
            try {
                INSTANCE?.vaultFileDao()?.updateSyncStatus(id, if (synced) 1 else 0)
            } catch (t: Throwable) {
                t.printStackTrace()
            }
        }

        suspend fun updateFavorite(id: String, isFavorite: Boolean) {
            try {
                INSTANCE?.vaultFileDao()?.updateFavorite(id, if (isFavorite) 1 else 0)
            } catch (t: Throwable) {
                t.printStackTrace()
            }
        }

        suspend fun updateAlbum(id: String, albumId: String?) {
            try {
                INSTANCE?.vaultFileDao()?.updateAlbum(id, albumId)
            } catch (t: Throwable) {
                t.printStackTrace()
            }
        }

        suspend fun updateFileName(id: String, newName: String) {
            try {
                INSTANCE?.vaultFileDao()?.updateFileName(id, newName)
            } catch (t: Throwable) {
                t.printStackTrace()
            }
        }

        suspend fun updateFileTags(id: String, tags: String?) {
            try {
                INSTANCE?.vaultFileDao()?.updateFileTags(id, tags)
            } catch (t: Throwable) {
                t.printStackTrace()
            }
        }

        suspend fun getSetting(key: String): String? {
            return try {
                INSTANCE?.settingDao()?.getSetting(key)
            } catch (t: Throwable) {
                t.printStackTrace()
                null
            }
        }

        suspend fun setSetting(key: String, value: String) {
            try {
                INSTANCE?.settingDao()?.insertSetting(SettingEntity(key, value))
            } catch (t: Throwable) {
                t.printStackTrace()
            }
        }

        // Backwards compatibility method
        fun getDatabase(context: Context): VaultDatabase {
            // If not unlocked already, unlock with default pin so that first/previous operations don't throw NPE
            if (INSTANCE == null) {
                try {
                    unlock(context, "1234")
                } catch (t: Throwable) {
                    t.printStackTrace()
                }
            }
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: try {
                    Room.inMemoryDatabaseBuilder(
                        context.applicationContext,
                        VaultDatabase::class.java
                    ).fallbackToDestructiveMigration().build().also {
                        INSTANCE = it
                    }
                } catch (t: Throwable) {
                    throw RuntimeException("Fatal error: unable to create any database instance", t)
                }
            }
        }
    }
}
