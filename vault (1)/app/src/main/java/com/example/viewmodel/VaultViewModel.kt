package com.example.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.db.DeviceRegistration
import com.example.db.EmergencyEvent
import com.example.db.SyncQueueItem
import com.example.db.VaultFile
import com.example.repository.VaultRepository
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

class VaultViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = VaultRepository(application)

    // Data streams from repository
    val vaultFiles: StateFlow<List<VaultFile>> = repository.allVaultFiles
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val activeQueue: StateFlow<List<SyncQueueItem>> = repository.activeQueue
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val deviceRegistration: StateFlow<DeviceRegistration?> = repository.deviceRegistration
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    val emergencyEvents: StateFlow<List<EmergencyEvent>> = repository.emergencyEvents
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // UI state flows
    private val _isLocked = MutableStateFlow(true)
    val isLocked: StateFlow<Boolean> = _isLocked.asStateFlow()

    private val _wrongPinAttempts = MutableStateFlow(0)
    val wrongPinAttempts: StateFlow<Int> = _wrongPinAttempts.asStateFlow()

    private val _apiBaseUrl = MutableStateFlow(repository.apiBaseUrl)
    val apiBaseUrl: StateFlow<String> = _apiBaseUrl.asStateFlow()

    private val _toastMessage = MutableStateFlow<String?>(null)
    val toastMessage: StateFlow<String?> = _toastMessage.asStateFlow()

    init {
        // Run first vault initialization and periodic scheduler
        viewModelScope.launch {
            repository.getOrCreateDeviceRegistration()
            repository.schedulePeriodicSync()
        }
    }

    fun verifyPin(enteredPin: String) {
        viewModelScope.launch {
            val correctPin = "9999" // Mock Secure Pin Code
            if (enteredPin == correctPin) {
                _isLocked.value = false
                _wrongPinAttempts.value = 0
                _toastMessage.value = "Vault Unlocked Successfully"
            } else {
                val attempts = _wrongPinAttempts.value + 1
                _wrongPinAttempts.value = attempts
                _toastMessage.value = "Incorrect PIN! Attempt $attempts/3"

                // Under 3 incorrect attempts we log standard warnings.
                // At 3 or more attempts, we trigger an emergency security capture
                if (attempts >= 3) {
                    _toastMessage.value = "SECURITY BREACH TRIGGERED! Capture synced to cloud."
                    repository.logEmergencyEvent("PIN brute-force compromise detected: 3 failed entry attempts (PIN tried: $enteredPin).")
                } else {
                    repository.logEmergencyEvent("Failed PIN authorization attempt #$attempts (PIN: $enteredPin).")
                }
            }
        }
    }

    fun lockVault() {
        _isLocked.value = true
        _wrongPinAttempts.value = 0
    }

    fun importSecureItem(filename: String, textContent: String) {
        viewModelScope.launch {
            repository.importFileToVault(filename, textContent, "text/plain")
            _toastMessage.value = "Document Encrypted & Queued for Backup"
        }
    }

    fun simulateEmergencyAttack() {
        viewModelScope.launch {
            _wrongPinAttempts.value += 1
            repository.logEmergencyEvent("Manual Security Threat capture event triggered by supervisor.")
            _toastMessage.value = "Emergency log captured & cloud-broadcast queued!"
        }
    }

    fun forceNetworkSyncNow() {
        viewModelScope.launch {
            repository.triggerImmediateSync()
            repository.registerDeviceOnBackend() // Ensure backend is registered
            _toastMessage.value = "Immediate backup execution scheduled"
        }
    }

    fun updateApiUrl(newUrl: String) {
        repository.apiBaseUrl = newUrl
        _apiBaseUrl.value = repository.apiBaseUrl
        _toastMessage.value = "Sync endpoint updated to: $newUrl"
    }

    fun clearToast() {
        _toastMessage.value = null
    }
}
