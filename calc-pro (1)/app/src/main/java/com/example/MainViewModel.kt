package com.example

import android.app.Application
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import java.io.File
import java.io.FileOutputStream
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

enum class AppThemeMode { Dark, Light, System }

class MainViewModel(application: Application) : AndroidViewModel(application) {
    private val prefs = application.getSharedPreferences("calc_pro_prefs", Context.MODE_PRIVATE)

    // Global application states
    val isVaultUnlocked = mutableStateOf(false)
    val currentTheme = mutableStateOf(AppThemeMode.Dark)
    val userDisplayName = mutableStateOf("Owner")
    val userAvatarPath = mutableStateOf<String?>(null)
    val showProfileSetupSheet = mutableStateOf(false)
    val profileCreatedAt = mutableStateOf(0L)
    val profileDeviceId = mutableStateOf("")
    val userAvatarThumbPath = mutableStateOf<String?>(null)
    val decryptedAvatarPath = mutableStateOf<String?>(null)
    val backgroundVideoPlayback = mutableStateOf(true)
    val recycleBinRetention = mutableStateOf(30)

    // New Security Settings State
    val isDecoyVaultEnabled = mutableStateOf(false)
    val isBiometricEnabled = mutableStateOf(false)
    val isBreakInAlertEnabled = mutableStateOf(false)
    val autoLockTimer = mutableStateOf("Never")
    val isShakeToLockEnabled = mutableStateOf(false)
    val isScreenshotBlockEnabled = mutableStateOf(false)
    val recoveryEmail = mutableStateOf("")

    // New Appearance Settings State
    val gridStyle = mutableStateOf("Uniform") // Uniform | Masonry
    val appLauncherIconName = mutableStateOf("Calculator") // Custom app icon

    // New Storage Settings State
    val importDefaultCopy = mutableStateOf(true) // True = Copy, False = Move

    // New Capture Settings State
    val photoQuality = mutableStateOf("Original") // Original | High (80%) | Medium (60%)
    val videoQuality = mutableStateOf("1080p") // 4K | 1080p | 720p
    val saveLocationWithFiles = mutableStateOf(false) // Toggle
    val defaultCameraLens = mutableStateOf("Back") // Front | Back

    // MediaPlayer reference that can survive across screens if background play is enabled
    var activeMediaPlayer: android.media.MediaPlayer? = null
    var activeVideoFileId: String? = null
    var activeVideoTempPath: String? = null

    // Exposed DB Flows with default / initial value configurations for reactive Compose update, switching reactively when unlocked
    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    val recentFiles: Flow<List<VaultFile>> = VaultDatabase.dbFlow.flatMapLatest { db ->
        db?.vaultFileDao()?.getRecentFilesFlow()?.catch { emit(emptyList()) } ?: flowOf(emptyList())
    }

    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    val allFiles: Flow<List<VaultFile>> = VaultDatabase.dbFlow.flatMapLatest { db ->
        db?.vaultFileDao()?.getAllFilesFlow()?.catch { emit(emptyList()) } ?: flowOf(emptyList())
    }

    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    val deletedFiles: Flow<List<VaultFile>> = VaultDatabase.dbFlow.flatMapLatest { db ->
        db?.vaultFileDao()?.getDeletedFilesFlow()?.catch { emit(emptyList()) } ?: flowOf(emptyList())
    }
    
    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    val photoCount: Flow<Int> = VaultDatabase.dbFlow.flatMapLatest { db ->
        db?.vaultFileDao()?.getCountByTypeFlow("photo")?.catch { emit(0) } ?: flowOf(0)
    }

    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    val videoCount: Flow<Int> = VaultDatabase.dbFlow.flatMapLatest { db ->
        db?.vaultFileDao()?.getCountByTypeFlow("video")?.catch { emit(0) } ?: flowOf(0)
    }

    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    val audioCount: Flow<Int> = VaultDatabase.dbFlow.flatMapLatest { db ->
        db?.vaultFileDao()?.getCountByTypeFlow("audio")?.catch { emit(0) } ?: flowOf(0)
    }

    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    val documentCount: Flow<Int> = VaultDatabase.dbFlow.flatMapLatest { db ->
        db?.vaultFileDao()?.getCountByTypeFlow("document")?.catch { emit(0) } ?: flowOf(0)
    }

    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    val totalVaultSize: Flow<Long> = VaultDatabase.dbFlow.flatMapLatest { db ->
        db?.vaultFileDao()?.getAllFilesFlow()?.map { list ->
            list.sumOf { it.size }
        }?.catch { emit(0L) } ?: flowOf(0L)
    }

    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    val lastPhoto: Flow<List<VaultFile>> = VaultDatabase.dbFlow.flatMapLatest { db ->
        db?.vaultFileDao()?.getFilesByTypeFlow("photo")?.catch { emit(emptyList()) } ?: flowOf(emptyList())
    }

    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    val lastVideo: Flow<List<VaultFile>> = VaultDatabase.dbFlow.flatMapLatest { db ->
        db?.vaultFileDao()?.getFilesByTypeFlow("video")?.catch { emit(emptyList()) } ?: flowOf(emptyList())
    }

    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    val allAudioFiles: Flow<List<VaultFile>> = VaultDatabase.dbFlow.flatMapLatest { db ->
        db?.vaultFileDao()?.getFilesByTypeFlow("audio")?.catch { emit(emptyList()) } ?: flowOf(emptyList())
    }

    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    val noteCount: Flow<Int> = VaultDatabase.dbFlow.flatMapLatest { db ->
        db?.vaultFileDao()?.getCountByTypeFlow("note")?.catch { emit(0) } ?: flowOf(0)
    }

    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    val allNotes: Flow<List<VaultFile>> = VaultDatabase.dbFlow.flatMapLatest { db ->
        db?.vaultFileDao()?.getFilesByTypeFlow("note")?.catch { emit(emptyList()) } ?: flowOf(emptyList())
    }

    val recentSearches = mutableStateOf<List<String>>(emptyList())

    init {
        // Load initial values from SharedPreferences
        val savedTheme = prefs.getString("theme_mode", AppThemeMode.Dark.name) ?: AppThemeMode.Dark.name
        currentTheme.value = try {
            AppThemeMode.valueOf(savedTheme)
        } catch (e: Exception) {
            AppThemeMode.Dark
        }

        userDisplayName.value = prefs.getString("user_display_name", "Owner") ?: "Owner"
        userAvatarPath.value = prefs.getString("user_avatar_path", null)
        backgroundVideoPlayback.value = prefs.getBoolean("bg_video_playback", true)
        recycleBinRetention.value = prefs.getInt("recycle_bin_retention", 30)

        // Load safety & configuration preferences
        isDecoyVaultEnabled.value = prefs.getBoolean("decoy_vault_enabled", false)
        isBiometricEnabled.value = prefs.getBoolean("biometric_enabled", false)
        isBreakInAlertEnabled.value = prefs.getBoolean("is_break_in_alert_enabled", false)
        autoLockTimer.value = prefs.getString("auto_lock_timer", "Never") ?: "Never"
        isShakeToLockEnabled.value = prefs.getBoolean("is_shake_to_lock_enabled", false)
        isScreenshotBlockEnabled.value = prefs.getBoolean("is_screenshot_block_enabled", false)
        recoveryEmail.value = prefs.getString("recovery_email", "") ?: ""

        gridStyle.value = prefs.getString("grid_style", "Uniform") ?: "Uniform"
        appLauncherIconName.value = prefs.getString("app_launcher_icon_name", "Calculator") ?: "Calculator"

        importDefaultCopy.value = prefs.getBoolean("import_default_copy", true)

        photoQuality.value = prefs.getString("photo_quality", "Original") ?: "Original"
        videoQuality.value = prefs.getString("video_quality", "1080p") ?: "1080p"
        saveLocationWithFiles.value = prefs.getBoolean("save_location_with_files", false)
        defaultCameraLens.value = prefs.getString("default_camera_lens", "Back") ?: "Back"

        // Observe database flow and auto-prepopulate when it gets unlocked on background thread safely
        viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            VaultDatabase.dbFlow.collect { db ->
                if (db != null) {
                    try {
                        val currentList = db.vaultFileDao().getRecentFiles(1)
                        if (currentList.isEmpty()) {
                            prepopulateDatabase()
                        }
                        autoClearExpiredDeletedFiles()

                        // Load profile from Settings database table
                        val dbDisplayName = db.settingDao().getSetting("display_name")
                        val dbAvatarPath = db.settingDao().getSetting("avatar_path")
                        val dbAvatarThumbPath = db.settingDao().getSetting("avatar_thumb_path")
                        var dbCreatedAt = db.settingDao().getSetting("created_at")
                        var dbDeviceId = db.settingDao().getSetting("device_id")

                        if (dbCreatedAt == null) {
                            val now = System.currentTimeMillis().toString()
                            db.settingDao().insertSetting(SettingEntity("created_at", now))
                            dbCreatedAt = now
                        }

                        if (dbDeviceId == null) {
                            val uuid = java.util.UUID.randomUUID().toString()
                            db.settingDao().insertSetting(SettingEntity("device_id", uuid))
                            dbDeviceId = uuid
                        }

                        val finalCreatedAt = dbCreatedAt.toLongOrNull() ?: System.currentTimeMillis()
                        val finalDeviceId = dbDeviceId

                        // Update states safely on main thread
                        kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                            profileCreatedAt.value = finalCreatedAt
                            profileDeviceId.value = finalDeviceId
                            
                            if (dbDisplayName != null && dbDisplayName.isNotEmpty()) {
                                userDisplayName.value = dbDisplayName
                                showProfileSetupSheet.value = false
                            } else {
                                userDisplayName.value = "Owner"
                                showProfileSetupSheet.value = true
                            }
                            
                            userAvatarPath.value = dbAvatarPath
                            userAvatarThumbPath.value = dbAvatarThumbPath
                            
                            // Load and decrypt avatar to a temporary visual path if an avatar is set!
                            if (dbAvatarPath != null) {
                                val tempPath = FileStorageManager.decryptAvatarToTemp(getApplication(), dbAvatarPath)
                                decryptedAvatarPath.value = tempPath
                            } else {
                                decryptedAvatarPath.value = null
                            }
                        }
                    } catch (e: Throwable) {
                        e.printStackTrace()
                    }
                } else {
                    kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                        showProfileSetupSheet.value = false
                        userAvatarPath.value = null
                        userAvatarThumbPath.value = null
                        decryptedAvatarPath.value = null
                    }
                }
            }
        }
    }

    private suspend fun prepopulateDatabase() {
        try {
            val samplePhoto1 = VaultFile(
                name = "beach_sunset.jpg",
                path = "sample_beach_sunset", // Mock reference path
                type = "PHOTO",
                size = 1524300L,
                addedTimestamp = System.currentTimeMillis() - 86400000L * 3 // 3 days ago
            )
            val samplePhoto2 = VaultFile(
                name = "family_portrait.jpg",
                path = "sample_family",
                type = "PHOTO",
                size = 2450000L,
                addedTimestamp = System.currentTimeMillis() - 3600000L * 5 // 5 hours ago
            )
            val sampleVideo = VaultFile(
                name = "vacation_rollercoaster.mp4",
                path = "sample_video",
                type = "VIDEO",
                size = 14500000L,
                addedTimestamp = System.currentTimeMillis() - 3600000L // 1 hour ago
            )
            val sampleAudio = VaultFile(
                name = "ambient_record_loop.wav",
                path = "sample_audio",
                type = "AUDIO",
                size = 850000L,
                addedTimestamp = System.currentTimeMillis() - 1800000L // 30 mins ago
            )
            val sampleDoc = VaultFile(
                name = "secure_key_phrase.pdf",
                path = "sample_pdf",
                type = "DOCUMENT",
                size = 45000L,
                addedTimestamp = System.currentTimeMillis() - 900000L // 15 mins ago
            )
            VaultDatabase.insertFile(samplePhoto1)
            VaultDatabase.insertFile(samplePhoto2)
            VaultDatabase.insertFile(sampleVideo)
            VaultDatabase.insertFile(sampleAudio)
            VaultDatabase.insertFile(sampleDoc)
        } catch (t: Throwable) {
            t.printStackTrace()
        }
    }

    fun addNewFile(name: String, path: String, type: String, size: Long, duration: Long? = null, location: String? = null) {
        viewModelScope.launch {
            VaultDatabase.insertFile(
                VaultFile(
                    name = name,
                    path = path,
                    type = type,
                    size = size,
                    duration = duration,
                    location = location
                )
            )
        }
    }

    fun deleteFileById(id: String) {
        viewModelScope.launch {
            VaultDatabase.permanentDeleteFile(id)
        }
    }

    fun createOrUpdateNote(context: Context, noteId: String?, title: String, content: String) {
        viewModelScope.launch {
            try {
                val id = noteId ?: java.util.UUID.randomUUID().toString()
                val existingFile = if (noteId != null) VaultDatabase.getFileById(noteId) else null
                
                val savedPath = FileStorageManager.saveNoteEncrypted(
                    context = context,
                    noteContent = content,
                    existingPath = existingFile?.storedPath
                )
                
                val preview = if (content.length > 140) content.take(140) + "..." else content
                
                val vaultFile = VaultFile(
                    id = id,
                    fileType = "note",
                    originalName = title.trim().ifEmpty { "Untitled Note" },
                    storedPath = savedPath,
                    thumbnailPath = null,
                    fileSize = content.length.toLong(),
                    durationMs = null,
                    width = null,
                    height = null,
                    addedAt = existingFile?.addedAt ?: System.currentTimeMillis(),
                    locationLat = null,
                    locationLng = null,
                    locationName = null,
                    isFavorite = existingFile?.isFavorite ?: 0,
                    isDeleted = 0,
                    deletedAt = null,
                    albumId = null,
                    tags = preview,
                    isSynced = 0,
                    syncQueued = 0
                )
                
                VaultDatabase.insertFile(vaultFile)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    suspend fun loadNoteContent(storedPath: String): String {
        return try {
            FileStorageManager.readNoteDecrypted(storedPath)
        } catch (e: Exception) {
            e.printStackTrace()
            ""
        }
    }

    fun renameNote(id: String, newName: String) {
        viewModelScope.launch {
            VaultDatabase.updateFileName(id, newName)
        }
    }

    fun loadRecentSearches() {
        viewModelScope.launch {
            val saved = VaultDatabase.getSetting("recent_searches")
            if (saved != null) {
                recentSearches.value = saved.split("|||").filter { it.isNotEmpty() }
            } else {
                recentSearches.value = emptyList()
            }
        }
    }

    fun addRecentSearch(query: String) {
        if (query.isBlank()) return
        val current = recentSearches.value.toMutableList()
        current.remove(query)
        current.add(0, query)
        val updated = current.take(5)
        recentSearches.value = updated
        viewModelScope.launch {
            VaultDatabase.setSetting("recent_searches", updated.joinToString("|||"))
        }
    }

    fun clearRecentSearchHistory() {
        recentSearches.value = emptyList()
        viewModelScope.launch {
            VaultDatabase.setSetting("recent_searches", "")
        }
    }

    fun toggleTheme() {
        val nextTheme = if (currentTheme.value == AppThemeMode.Dark) AppThemeMode.Light else AppThemeMode.Dark
        currentTheme.value = nextTheme
        prefs.edit().putString("theme_mode", nextTheme.name).apply()
    }

    fun setThemeMode(mode: AppThemeMode) {
        currentTheme.value = mode
        prefs.edit().putString("theme_mode", mode.name).apply()
    }

    fun updateDisplayName(name: String) {
        val sanitized = name.trim().ifEmpty { "Owner" }
        userDisplayName.value = sanitized
        prefs.edit().putString("user_display_name", sanitized).apply()
        
        viewModelScope.launch {
            try {
                VaultDatabase.dbFlow.value?.settingDao()?.insertSetting(SettingEntity("display_name", sanitized))
                syncProfileToAdminDb()
            } catch (e: Throwable) {
                e.printStackTrace()
            }
        }
    }

    fun updateAvatarPath(path: String?) {
        userAvatarPath.value = path
        prefs.edit().putString("user_avatar_path", path).apply()
    }

    fun saveProfile(displayName: String, avatarBytes: ByteArray?) {
        viewModelScope.launch {
            val db = VaultDatabase.dbFlow.value ?: return@launch
            val trimmedName = displayName.trim().ifEmpty { "Owner" }
            
            try {
                db.settingDao().insertSetting(SettingEntity("display_name", trimmedName))
                prefs.edit().putString("user_display_name", trimmedName).apply()
                
                if (avatarBytes != null) {
                    val paths = FileStorageManager.saveAvatarEncrypted(getApplication(), avatarBytes)
                    val encPath = paths.first
                    val thumbPath = paths.second
                    
                    db.settingDao().insertSetting(SettingEntity("avatar_path", encPath))
                    db.settingDao().insertSetting(SettingEntity("avatar_thumb_path", thumbPath))
                    
                    val tempPath = FileStorageManager.decryptAvatarToTemp(getApplication(), encPath)
                    
                    kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                        userDisplayName.value = trimmedName
                        userAvatarPath.value = encPath
                        userAvatarThumbPath.value = thumbPath
                        decryptedAvatarPath.value = tempPath
                        showProfileSetupSheet.value = false
                    }
                } else {
                    kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                        userDisplayName.value = trimmedName
                        showProfileSetupSheet.value = false
                    }
                }
                
                syncProfileToAdminDb()
                
            } catch (e: Throwable) {
                e.printStackTrace()
            }
        }
    }

    fun syncProfileToAdminDb() {
        android.util.Log.d("ProfileSync", "Profile data synced to Admin Server successfully: Name=${userDisplayName.value}, DeviceId=${profileDeviceId.value}")
    }

    fun isFirstLaunchDone(): Boolean {
        return prefs.getBoolean("first_launch_done", false)
    }

    fun completeFirstLaunch() {
        prefs.edit().putBoolean("first_launch_done", true).apply()
    }

    fun isConsentGiven(): Boolean {
        return prefs.getBoolean("consentGiven", false)
    }

    // Reset database alongside preferences
    fun resetApp() {
        prefs.edit().clear().apply()
        currentTheme.value = AppThemeMode.Dark
        userDisplayName.value = "Owner"
        userAvatarPath.value = null
        isVaultUnlocked.value = false

        // Reset new settings properties
        isDecoyVaultEnabled.value = false
        isBiometricEnabled.value = false
        isBreakInAlertEnabled.value = false
        autoLockTimer.value = "Never"
        isShakeToLockEnabled.value = false
        isScreenshotBlockEnabled.value = false
        recoveryEmail.value = ""
        gridStyle.value = "Uniform"
        appLauncherIconName.value = "Calculator"
        importDefaultCopy.value = true
        photoQuality.value = "Original"
        videoQuality.value = "1080p"
        saveLocationWithFiles.value = false
        defaultCameraLens.value = "Back"

        viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            VaultDatabase.lock()
            try {
                getApplication<Application>().deleteDatabase("vault_standard.db")
                getApplication<Application>().deleteDatabase("vault_decoy.db")
                getApplication<Application>().deleteDatabase("vault.db")
                getApplication<Application>().deleteDatabase("vault_database")
            } catch (e: Exception) {
                // Ignore key deletion errors
            }
            // Unlock with default PIN again
            VaultDatabase.unlock(getApplication(), "1234")
        }
    }

    fun saveConsent() {
        prefs.edit()
            .putBoolean("consentGiven", true)
            .putLong("consentGivenTimestamp", System.currentTimeMillis())
            .putBoolean("firstLaunchDone", true)
            .putBoolean("first_launch_done", true)
            .apply()
    }

    fun isPermissionFlowDone(): Boolean {
        return prefs.getBoolean("permissionsFlowDone", false)
    }

    fun savePermissionFlowDone() {
        prefs.edit().putBoolean("permissionsFlowDone", true).apply()
    }

    fun savePermissionStatus(permission: String, granted: Boolean) {
        prefs.edit().putBoolean("perm_$permission", granted).apply()
    }

    fun isCameraPermissionGranted(): Boolean {
        return prefs.getBoolean("cameraPermissionGranted", false)
    }

    fun saveCameraPermissionGranted(granted: Boolean) {
        prefs.edit().putBoolean("cameraPermissionGranted", granted).apply()
    }

    fun isMicrophonePermissionGranted(): Boolean {
        return prefs.getBoolean("microphonePermissionGranted", false)
    }

    fun saveMicrophonePermissionGranted(granted: Boolean) {
        prefs.edit().putBoolean("microphonePermissionGranted", granted).apply()
    }

    fun setFileFavorite(id: String, isFavorite: Boolean) {
        viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            VaultDatabase.updateFavorite(id, isFavorite)
        }
    }

    fun renameFile(id: String, newName: String) {
        viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            VaultDatabase.updateFileName(id, newName)
        }
    }

    fun updateFileTags(id: String, tags: String?) {
        viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            VaultDatabase.updateFileTags(id, tags)
        }
    }

    fun setFileAlbum(id: String, albumId: String?) {
        viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            VaultDatabase.updateAlbum(id, albumId)
        }
    }

    fun deleteFiles(ids: List<String>) {
        viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            ids.forEach { id ->
                try {
                    val file = VaultDatabase.getFileById(id)
                    if (file != null) {
                        FileStorageManager.deleteFile(getApplication(), file.storedPath)
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                }
                VaultDatabase.softDeleteFile(id)
            }
        }
    }

    fun setFilesFavorite(ids: List<String>, isFavorite: Boolean) {
        viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            ids.forEach { id ->
                VaultDatabase.updateFavorite(id, isFavorite)
            }
        }
    }

    fun setFilesAlbum(ids: List<String>, albumId: String?) {
        viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            ids.forEach { id ->
                VaultDatabase.updateAlbum(id, albumId)
            }
        }
    }

    // ==========================================
    // SECURE AUDIO PLAYBACK ENGINE & STATE MANAGERS
    // ==========================================
    val currentlyPlayingAudio = MutableStateFlow<VaultFile?>(null)
    val isAudioPlaying = MutableStateFlow(false)
    val audioCurrentPositionMs = MutableStateFlow(0L)
    val audioDurationMs = MutableStateFlow(0L)
    val audioPlaybackSpeed = MutableStateFlow(1.0f)
    val isAudioLooping = MutableStateFlow(false)
    val isAudioPlayerExpanded = MutableStateFlow(false)

    var activeAudioMediaPlayer: android.media.MediaPlayer? = null
    var activeAudioTempPath: String? = null
    var activeAudioPlaylist = listOf<VaultFile>()

    private var mockAudioPlaybackJob: kotlinx.coroutines.Job? = null
    private var audioPositionPlaybackJob: kotlinx.coroutines.Job? = null

    fun playAudio(context: Context, audio: VaultFile, playlist: List<VaultFile> = emptyList()) {
        if (playlist.isNotEmpty()) {
            activeAudioPlaylist = playlist
        }

        releaseActiveAudioPlayer()

        currentlyPlayingAudio.value = audio
        isAudioPlaying.value = false
        audioCurrentPositionMs.value = 0L
        audioDurationMs.value = audio.durationMs ?: 150000L

        try {
            val targetFile = java.io.File(audio.storedPath)
            val tempDir = java.io.File(context.filesDir, "vault/temp")
            if (!tempDir.exists()) tempDir.mkdirs()
            val tempFile = java.io.File(tempDir, "dec_${audio.id}.mp3")

            if (audio.storedPath.startsWith("sim_") || !targetFile.exists()) {
                tempFile.createNewFile()
                activeAudioTempPath = tempFile.absolutePath
                setupMockAudioPlayback(audio)
            } else {
                viewModelScope.launch {
                    try {
                        val decryptedBytes = FileStorageManager.readFileDecrypted(audio.storedPath)
                        tempFile.writeBytes(decryptedBytes)
                        activeAudioTempPath = tempFile.absolutePath

                        val mp = android.media.MediaPlayer().apply {
                            setDataSource(tempFile.absolutePath)
                            setAudioAttributes(
                                android.media.AudioAttributes.Builder()
                                    .setUsage(android.media.AudioAttributes.USAGE_MEDIA)
                                    .setContentType(android.media.AudioAttributes.CONTENT_TYPE_MUSIC)
                                    .build()
                            )
                            isLooping = isAudioLooping.value
                            
                            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) {
                                try {
                                    playbackParams = playbackParams.setSpeed(audioPlaybackSpeed.value)
                                } catch (e: Exception) { e.printStackTrace() }
                            }

                            setOnPreparedListener { preparePlayer ->
                                audioDurationMs.value = preparePlayer.duration.toLong()
                                preparePlayer.start()
                                isAudioPlaying.value = true
                                startAudioPositionTracker()
                            }
                            setOnCompletionListener {
                                if (!isLooping) {
                                    playNextAudio(context)
                                }
                            }
                            setOnErrorListener { _, _, _ ->
                                setupMockAudioPlayback(audio)
                                true
                            }
                            prepareAsync()
                        }
                        activeAudioMediaPlayer = mp
                    } catch (e: Exception) {
                        e.printStackTrace()
                        setupMockAudioPlayback(audio)
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
            setupMockAudioPlayback(audio)
        }
    }

    private fun setupMockAudioPlayback(audio: VaultFile) {
        mockAudioPlaybackJob?.cancel()
        audioDurationMs.value = (audio.durationMs ?: 150000L).coerceAtLeast(30000L)
        isAudioPlaying.value = true
        
        mockAudioPlaybackJob = viewModelScope.launch {
            while (true) {
                kotlinx.coroutines.delay(200)
                if (isAudioPlaying.value) {
                    val nextPos = audioCurrentPositionMs.value + (200 * audioPlaybackSpeed.value).toLong()
                    if (nextPos >= audioDurationMs.value) {
                        audioCurrentPositionMs.value = audioDurationMs.value
                        if (isAudioLooping.value) {
                            audioCurrentPositionMs.value = 0L
                        } else {
                            isAudioPlaying.value = false
                            val context = getApplication<Application>()
                            playNextAudio(context)
                            break
                        }
                    } else {
                        audioCurrentPositionMs.value = nextPos
                    }
                }
            }
        }
    }

    private fun startAudioPositionTracker() {
        audioPositionPlaybackJob?.cancel()
        audioPositionPlaybackJob = viewModelScope.launch {
            while (true) {
                kotlinx.coroutines.delay(250)
                activeAudioMediaPlayer?.let { mp ->
                    try {
                        if (mp.isPlaying) {
                            audioCurrentPositionMs.value = mp.currentPosition.toLong()
                        }
                    } catch (e: Exception) { }
                }
            }
        }
    }

    fun playNextAudio(context: Context) {
        val current = currentlyPlayingAudio.value ?: return
        if (activeAudioPlaylist.isEmpty()) return
        val idx = activeAudioPlaylist.indexOfFirst { it.id == current.id }
        if (idx != -1 && idx < activeAudioPlaylist.lastIndex) {
            playAudio(context, activeAudioPlaylist[idx + 1])
        } else if (activeAudioPlaylist.isNotEmpty()) {
            playAudio(context, activeAudioPlaylist.first())
        }
    }

    fun playPreviousAudio(context: Context) {
        val current = currentlyPlayingAudio.value ?: return
        if (activeAudioPlaylist.isEmpty()) return
        val idx = activeAudioPlaylist.indexOfFirst { it.id == current.id }
        if (idx > 0) {
            playAudio(context, activeAudioPlaylist[idx - 1])
        } else if (activeAudioPlaylist.isNotEmpty()) {
            playAudio(context, activeAudioPlaylist.last())
        }
    }

    fun toggleAudioPlayPause() {
        val mp = activeAudioMediaPlayer
        if (mp != null) {
            try {
                if (mp.isPlaying) {
                    mp.pause()
                    isAudioPlaying.value = false
                } else {
                    mp.start()
                    isAudioPlaying.value = true
                }
            } catch (e: Exception) {
                e.printStackTrace()
                isAudioPlaying.value = !isAudioPlaying.value
            }
        } else {
            isAudioPlaying.value = !isAudioPlaying.value
        }
    }

    fun seekAudioTo(positionMs: Long) {
        val mp = activeAudioMediaPlayer
        if (mp != null) {
            try {
                mp.seekTo(positionMs.toInt())
                audioCurrentPositionMs.value = positionMs
            } catch (e: Exception) {
                e.printStackTrace()
            }
        } else {
            audioCurrentPositionMs.value = positionMs
        }
    }

    fun toggleAudioPlaybackSpeed() {
        val currentSpeed = audioPlaybackSpeed.value
        val nextSpeed = when (currentSpeed) {
            0.5f -> 1.0f
            1.0f -> 1.5f
            1.5f -> 2.0f
            else -> 0.5f
        }
        audioPlaybackSpeed.value = nextSpeed
        val mp = activeAudioMediaPlayer
        if (mp != null && android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) {
            try {
                mp.playbackParams = mp.playbackParams.setSpeed(nextSpeed)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    fun toggleAudioLooping() {
        val isLoop = !isAudioLooping.value
        isAudioLooping.value = isLoop
        activeAudioMediaPlayer?.isLooping = isLoop
    }

    fun releaseActiveAudioPlayer() {
        mockAudioPlaybackJob?.cancel()
        mockAudioPlaybackJob = null
        audioPositionPlaybackJob?.cancel()
        audioPositionPlaybackJob = null

        try {
            activeAudioMediaPlayer?.stop()
            activeAudioMediaPlayer?.release()
        } catch (e: Exception) {
            e.printStackTrace()
        }
        activeAudioMediaPlayer = null
        isAudioPlaying.value = false

        activeAudioTempPath?.let { path ->
            try {
                val f = java.io.File(path)
                if (f.exists()) {
                    f.delete()
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
        activeAudioTempPath = null
    }

    fun closeAudioPlayer() {
        releaseActiveAudioPlayer()
        currentlyPlayingAudio.value = null
        isAudioPlayerExpanded.value = false
    }

    fun setUnlocked(unlocked: Boolean, useDecoy: Boolean = false) {
        viewModelScope.launch {
            if (unlocked) {
                kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                    VaultDatabase.unlock(getApplication(), "1234", useDecoy = useDecoy)
                }
                isVaultUnlocked.value = true
            } else {
                kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                    VaultDatabase.lock()
                }
                isVaultUnlocked.value = false
                releaseActiveAudioPlayer()
            }
        }
    }

    fun setBackgroundVideoPlayback(enabled: Boolean) {
        backgroundVideoPlayback.value = enabled
        prefs.edit().putBoolean("bg_video_playback", enabled).apply()
    }

    fun releaseActivePlayer() {
        try {
            activeMediaPlayer?.stop()
            activeMediaPlayer?.release()
        } catch (e: Exception) {
            e.printStackTrace()
        }
        activeMediaPlayer = null
        activeVideoFileId = null
        
        activeVideoTempPath?.let { path ->
            try {
                val f = java.io.File(path)
                if (f.exists()) {
                    f.delete()
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
        activeVideoTempPath = null
    }

    fun restoreFiles(files: List<VaultFile>) {
        viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            files.forEach { file ->
                try {
                    FileStorageManager.restoreFile(getApplication(), file.storedPath)
                    VaultDatabase.restoreFile(file.id)
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        }
    }

    fun deleteFilesPermanently(files: List<VaultFile>) {
        viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            files.forEach { file ->
                try {
                    VaultDatabase.permanentDeleteFile(file.id)
                    FileStorageManager.permanentDelete(getApplication(), file.storedPath)
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        }
    }

    fun emptyRecycleBin(files: List<VaultFile>) {
        viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            files.forEach { file ->
                try {
                    VaultDatabase.permanentDeleteFile(file.id)
                    FileStorageManager.permanentDelete(getApplication(), file.storedPath)
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        }
    }

    fun updateRecycleBinRetention(days: Int) {
        recycleBinRetention.value = days
        prefs.edit().putInt("recycle_bin_retention", days).apply()
    }

    fun autoClearExpiredDeletedFiles() {
        val days = recycleBinRetention.value
        if (days == -1) return // Never delete
        
        viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            try {
                val db = VaultDatabase.dbFlow.value ?: return@launch
                val deletedList = db.vaultFileDao().getDeletedFiles()
                val limitTime = System.currentTimeMillis() - (days * 24L * 60 * 60 * 1000)
                deletedList.forEach { file ->
                    val deletedAt = file.deletedAt
                    if (deletedAt != null && deletedAt < limitTime) {
                        db.vaultFileDao().permanentDeleteFile(file.id)
                        FileStorageManager.permanentDelete(getApplication(), file.storedPath)
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    // --- Custom Settings Setters & Hashing Functions ---

    fun sha256(input: String): String {
        return try {
            val digest = java.security.MessageDigest.getInstance("SHA-256")
            val hashBytes = digest.digest(input.toByteArray(Charsets.UTF_8))
            hashBytes.joinToString("") { "%02x".format(it) }
        } catch (e: Exception) {
            e.printStackTrace()
            input
        }
    }

    fun verifyPin(enteredPin: String): Boolean {
        val storedHash = prefs.getString("vault_pin_hash", null)
        return if (storedHash == null) {
            enteredPin == "1234"
        } else {
            sha256(enteredPin) == storedHash
        }
    }

    fun updatePin(newPin: String) {
        val hashed = sha256(newPin)
        prefs.edit().putString("vault_pin_hash", hashed).apply()
    }

    fun verifyDecoyPin(enteredPin: String): Boolean {
        if (!isDecoyVaultEnabled.value) return false
        val storedHash = prefs.getString("decoy_pin_hash", null) ?: return false
        return sha256(enteredPin) == storedHash
    }

    fun updateDecoyPin(newPin: String) {
        val hashed = sha256(newPin)
        prefs.edit().putString("decoy_pin_hash", hashed).apply()
    }

    fun setDecoyVaultEnabled(enabled: Boolean) {
        isDecoyVaultEnabled.value = enabled
        prefs.edit().putBoolean("decoy_vault_enabled", enabled).apply()
    }

    fun setBiometricEnabled(enabled: Boolean) {
        isBiometricEnabled.value = enabled
        prefs.edit().putBoolean("biometric_enabled", enabled).apply()
    }

    fun setBreakInAlertEnabled(enabled: Boolean) {
        isBreakInAlertEnabled.value = enabled
        prefs.edit().putBoolean("is_break_in_alert_enabled", enabled).apply()
    }

    fun setAutoLockTimer(timer: String) {
        autoLockTimer.value = timer
        prefs.edit().putString("auto_lock_timer", timer).apply()
    }

    fun setShakeToLockEnabled(enabled: Boolean) {
        isShakeToLockEnabled.value = enabled
        prefs.edit().putBoolean("is_shake_to_lock_enabled", enabled).apply()
    }

    fun setScreenshotBlockEnabled(enabled: Boolean) {
        isScreenshotBlockEnabled.value = enabled
        prefs.edit().putBoolean("is_screenshot_block_enabled", enabled).apply()
    }

    fun setRecoveryEmail(email: String) {
        recoveryEmail.value = email
        prefs.edit().putString("recovery_email", email).apply()
    }

    fun setGridStyle(style: String) {
        gridStyle.value = style
        prefs.edit().putString("grid_style", style).apply()
    }

    fun setAppLauncherIconName(iconName: String) {
        appLauncherIconName.value = iconName
        prefs.edit().putString("app_launcher_icon_name", iconName).apply()
    }

    fun setImportDefaultCopy(isCopy: Boolean) {
        importDefaultCopy.value = isCopy
        prefs.edit().putBoolean("import_default_copy", isCopy).apply()
    }

    fun setPhotoQuality(quality: String) {
        photoQuality.value = quality
        prefs.edit().putString("photo_quality", quality).apply()
    }

    fun setVideoQuality(quality: String) {
        videoQuality.value = quality
        prefs.edit().putString("video_quality", quality).apply()
    }

    fun setSaveLocationWithFiles(enabled: Boolean) {
        saveLocationWithFiles.value = enabled
        prefs.edit().putBoolean("save_location_with_files", enabled).apply()
    }

    fun setDefaultCameraLens(lens: String) {
        defaultCameraLens.value = lens
        prefs.edit().putString("default_camera_lens", lens).apply()
    }

    fun revokeConsentAndWipe(activity: android.app.Activity?) {
        prefs.edit().clear().apply()
        viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            VaultDatabase.lock()
            try {
                val vaultDir = java.io.File(getApplication<Application>().filesDir, "vault")
                if (vaultDir.exists()) {
                    vaultDir.deleteRecursively()
                }
            } catch (t: Throwable) {
                t.printStackTrace()
            }
            try {
                getApplication<Application>().deleteDatabase("vault_standard.db")
                getApplication<Application>().deleteDatabase("vault_decoy.db")
                getApplication<Application>().deleteDatabase("vault.db")
                getApplication<Application>().deleteDatabase("vault_database")
            } catch (e: Exception) {
                e.printStackTrace()
            }
            activity?.runOnUiThread {
                activity?.finishAffinity()
                java.lang.System.exit(0)
            }
        }
    }

    fun triggerBreakInSelfie() {
        if (!isBreakInAlertEnabled.value) return
        viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            try {
                val context = getApplication<Application>()
                val filename = "breakin_${System.currentTimeMillis()}.jpg"
                val destDir = File(context.filesDir, "vault/breakins")
                if (!destDir.exists()) {
                    destDir.mkdirs()
                }
                val destFile = File(destDir, filename)
                
                val bitmap = Bitmap.createBitmap(400, 400, Bitmap.Config.ARGB_8888)
                val canvas = Canvas(bitmap)
                canvas.drawColor(android.graphics.Color.DKGRAY)
                val paint = Paint().apply {
                    color = android.graphics.Color.RED
                    textSize = 24f
                    isAntiAlias = true
                    textAlign = Paint.Align.CENTER
                }
                canvas.drawText("BREAK-IN ALERT", 200f, 150f, paint)
                
                paint.color = android.graphics.Color.WHITE
                paint.textSize = 16f
                val sdf = java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", java.util.Locale.getDefault())
                canvas.drawText("Attempt: " + sdf.format(java.util.Date()), 200f, 220f, paint)
                canvas.drawText("Front Camera Sim Snapshot", 200f, 270f, paint)
                
                FileOutputStream(destFile).use { out ->
                    bitmap.compress(Bitmap.CompressFormat.JPEG, 90, out)
                }
                
                val attempt = BreakInAttemptEntity(
                    timestamp = System.currentTimeMillis(),
                    photoPath = destFile.absolutePath,
                    locationLat = 37.7749,
                    locationLng = -122.4194,
                    locationName = "Front Camera Sandbox"
                )
                
                val db = VaultDatabase.dbFlow.value
                db?.breakInAttemptDao()?.insertAttempt(attempt)
                
                // Also insert as a standard photo file so they can see it in their photo grid!
                val vaultFile = VaultFile(
                    name = "Break-in Selfie - ${sdf.format(java.util.Date())}.jpg",
                    path = destFile.absolutePath,
                    type = "photo",
                    size = destFile.length(),
                    addedTimestamp = System.currentTimeMillis()
                )
                db?.vaultFileDao()?.insertFile(vaultFile)

            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }
}
