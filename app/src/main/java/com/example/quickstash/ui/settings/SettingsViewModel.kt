package com.example.quickstash.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.quickstash.QuickStashApp
import com.example.quickstash.data.nas.NasClient
import com.example.quickstash.data.nas.SmbConnectionManager
import com.example.quickstash.data.repository.BackupRepository
import com.example.quickstash.data.settings.ConnectionSettings
import com.example.quickstash.data.settings.ConnectionSettingsStore
import com.example.quickstash.media.coil.NasImageCache
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

data class SettingsUiState(
    val host: String = "",
    val secondaryHost: String = "",
    val port: String = "445",
    val share: String = "",
    val username: String = "",
    val password: String = "",
    val rootPath: String = "",
    val skipSeconds: String = "10",
    val slideshowSeconds: String = "5",
    val appLockEnabled: Boolean = false,
    val showTierBadges: Boolean = true,
    val loaded: Boolean = false,
    val testing: Boolean = false,
    val testResult: String? = null,
    val backupBusy: Boolean = false,
    val backupResult: String? = null,
    val cacheSizeBytes: Long = -1,
    val lastAutoBackupMillis: Long = 0,
    val saved: Boolean = false,
) {
    val canSubmit: Boolean
        get() = host.isNotBlank() && share.isNotBlank() && port.toIntOrNull() != null

    fun toSettings() = ConnectionSettings(
        host = host.trim(),
        secondaryHost = secondaryHost.trim(),
        port = port.toIntOrNull() ?: 445,
        share = share.trim(),
        username = username.trim(),
        password = password,
        rootPath = rootPath.trim().trim('/'),
        skipSeconds = skipSeconds.toIntOrNull()?.coerceIn(1, 300) ?: 10,
        slideshowSeconds = slideshowSeconds.toIntOrNull()?.coerceIn(1, 600) ?: 5,
    )
}

class SettingsViewModel(
    private val settingsStore: ConnectionSettingsStore,
    private val nasClient: NasClient,
    private val connectionManager: SmbConnectionManager,
    private val backupRepository: BackupRepository,
    private val imageCache: NasImageCache,
) : ViewModel() {

    private val _uiState = MutableStateFlow(SettingsUiState())
    val uiState: StateFlow<SettingsUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            val current = settingsStore.current()
            _uiState.update {
                it.copy(
                    host = current.host,
                    secondaryHost = current.secondaryHost,
                    port = current.port.toString(),
                    share = current.share,
                    username = current.username,
                    password = current.password,
                    rootPath = current.rootPath,
                    skipSeconds = current.skipSeconds.toString(),
                    slideshowSeconds = current.slideshowSeconds.toString(),
                    appLockEnabled = settingsStore.appLockEnabled(),
                    showTierBadges = settingsStore.showTierBadges(),
                    loaded = true,
                )
            }
        }
    }

    fun onHostChange(value: String) = _uiState.update { it.copy(host = value, testResult = null) }
    fun onSecondaryHostChange(value: String) =
        _uiState.update { it.copy(secondaryHost = value, testResult = null) }
    fun onPortChange(value: String) = _uiState.update { it.copy(port = value, testResult = null) }
    fun onShareChange(value: String) = _uiState.update { it.copy(share = value, testResult = null) }
    fun onUsernameChange(value: String) = _uiState.update { it.copy(username = value, testResult = null) }
    fun onPasswordChange(value: String) = _uiState.update { it.copy(password = value, testResult = null) }
    fun onRootPathChange(value: String) = _uiState.update { it.copy(rootPath = value, testResult = null) }
    fun onSkipSecondsChange(value: String) = _uiState.update { it.copy(skipSeconds = value) }
    fun onSlideshowSecondsChange(value: String) = _uiState.update { it.copy(slideshowSeconds = value) }

    fun onAppLockChange(enabled: Boolean) {
        _uiState.update { it.copy(appLockEnabled = enabled) }
        viewModelScope.launch { settingsStore.setAppLockEnabled(enabled) }
    }

    fun onShowTierBadgesChange(show: Boolean) {
        _uiState.update { it.copy(showTierBadges = show) }
        viewModelScope.launch { settingsStore.setShowTierBadges(show) }
    }

    fun exportBackup() {
        if (_uiState.value.backupBusy) return
        _uiState.update { it.copy(backupBusy = true, backupResult = null) }
        viewModelScope.launch {
            val result = runCatching { backupRepository.export() }
                .getOrElse { "バックアップに失敗しました: ${it.message ?: it.javaClass.simpleName}" }
            _uiState.update { it.copy(backupBusy = false, backupResult = result) }
        }
    }

    fun refreshCacheSize() {
        viewModelScope.launch(Dispatchers.IO) {
            val size = runCatching { imageCache.cacheSizeBytes() }.getOrDefault(0L)
            val lastBackup =
                runCatching { settingsStore.lastAutoBackupMillis() }.getOrDefault(0L)
            _uiState.update {
                it.copy(cacheSizeBytes = size, lastAutoBackupMillis = lastBackup)
            }
        }
    }

    fun clearCache() {
        viewModelScope.launch(Dispatchers.IO) {
            runCatching { imageCache.clearCaches() }
            val size = runCatching { imageCache.cacheSizeBytes() }.getOrDefault(0L)
            _uiState.update { it.copy(cacheSizeBytes = size) }
        }
    }

    fun restoreBackup() {
        if (_uiState.value.backupBusy) return
        _uiState.update { it.copy(backupBusy = true, backupResult = null) }
        viewModelScope.launch {
            val result = runCatching { backupRepository.restore() }
                .getOrElse { "復元に失敗しました: ${it.message ?: it.javaClass.simpleName}" }
            _uiState.update { it.copy(backupBusy = false, backupResult = result) }
        }
    }

    fun test() {
        val state = _uiState.value
        if (!state.canSubmit || state.testing) return
        _uiState.update { it.copy(testing = true, testResult = null) }
        viewModelScope.launch {
            val settings = state.toSettings()
            val primary = nasClient.testConnection(settings).fold(
                onSuccess = { count -> "接続成功（$count 件のエントリ）" },
                onFailure = { e -> "接続失敗: ${e.message ?: e.javaClass.simpleName}" },
            )
            val secondary = settings.secondaryHost.takeIf { it.isNotBlank() }?.let { host ->
                nasClient.testConnection(settings.copy(host = host)).fold(
                    onSuccess = { count -> "接続成功（$count 件のエントリ）" },
                    onFailure = { e -> "接続失敗: ${e.message ?: e.javaClass.simpleName}" },
                )
            }
            _uiState.update {
                it.copy(
                    testing = false,
                    testResult = if (secondary == null) {
                        primary
                    } else {
                        "プライマリ: $primary\nセカンダリ: $secondary"
                    },
                )
            }
        }
    }

    fun save() {
        val state = _uiState.value
        if (!state.canSubmit) return
        viewModelScope.launch {
            settingsStore.save(state.toSettings())
            connectionManager.reset()
            _uiState.update { it.copy(saved = true) }
        }
    }

    companion object {
        val Factory = viewModelFactory {
            initializer {
                val app = this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY] as QuickStashApp
                SettingsViewModel(
                    app.container.settingsStore,
                    app.container.nasClient,
                    app.container.connectionManager,
                    app.container.backupRepository,
                    app.container.imageCache,
                )
            }
        }
    }
}
