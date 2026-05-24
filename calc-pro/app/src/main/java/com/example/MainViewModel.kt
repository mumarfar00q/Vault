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

    init {
        // Load initial values from SharedPreferences
        val savedTheme = prefs.getString("theme_mode", AppThemeMode.Dark.name) ?: AppThemeMode.Dark.name
        currentTheme.value = if (savedTheme == AppThemeMode.Light.name) AppThemeMode.Light else AppThemeMode.Dark

        userDisplayName.value = prefs.getString("user_display_name", "Owner") ?: "Owner"
        userAvatarPath.value = prefs.getString("user_avatar_path", null)

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
            }
        }
    }
}
