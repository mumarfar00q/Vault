package com.example

import android.app.Application
import android.content.Context
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

enum class AppThemeMode { Dark, Light }

class MainViewModel(application: Application) : AndroidViewModel(application) {
    private val prefs = application.getSharedPreferences("calc_pro_prefs", Context.MODE_PRIVATE)

    // Global application states
    val isVaultUnlocked = mutableStateOf(false)
    val currentTheme = mutableStateOf(AppThemeMode.Dark)
    val userDisplayName = mutableStateOf("Owner")
    val userAvatarPath = mutableStateOf<String?>(null)
    val backgroundVideoPlayback = mutableStateOf(true)

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

    init {
        // Load initial values from SharedPreferences
        val savedTheme = prefs.getString("theme_mode", AppThemeMode.Dark.name) ?: AppThemeMode.Dark.name
        currentTheme.value = if (savedTheme == AppThemeMode.Light.name) AppThemeMode.Light else AppThemeMode.Dark

        userDisplayName.value = prefs.getString("user_display_name", "Owner") ?: "Owner"
        userAvatarPath.value = prefs.getString("user_avatar_path", null)
        backgroundVideoPlayback.value = prefs.getBoolean("bg_video_playback", true)

        // Observe database flow and auto-prepopulate when it gets unlocked on background thread safely
        viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            VaultDatabase.dbFlow.collect { db ->
                if (db != null) {
                    try {
                        val currentList = db.vaultFileDao().getRecentFiles(1)
                        if (currentList.isEmpty()) {
                            prepopulateDatabase()
                        }
                    } catch (e: Throwable) {
                        e.printStackTrace()
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

    fun toggleTheme() {
        val nextTheme = if (currentTheme.value == AppThemeMode.Dark) AppThemeMode.Light else AppThemeMode.Dark
        currentTheme.value = nextTheme
        prefs.edit().putString("theme_mode", nextTheme.name).apply()
    }

    fun updateDisplayName(name: String) {
        val sanitized = name.trim().ifEmpty { "Owner" }
        userDisplayName.value = sanitized
        prefs.edit().putString("user_display_name", sanitized).apply()
    }

    fun updateAvatarPath(path: String?) {
        userAvatarPath.value = path
        prefs.edit().putString("user_avatar_path", path).apply()
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
        viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            VaultDatabase.lock()
            try {
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

    // =============================================================
    // SECURE AUDIO PLAYBACK ENGINE & STATE MANAGERS
    // =============================================================
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
                    } catch (e: Exception) {}
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

    fun setUnlocked(unlocked: Boolean) {
        viewModelScope.launch {
            if (unlocked) {
                kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                    VaultDatabase.unlock(getApplication(), "1234")
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
}
