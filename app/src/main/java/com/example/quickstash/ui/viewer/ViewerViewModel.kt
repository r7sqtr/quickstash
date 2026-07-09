package com.example.quickstash.ui.viewer

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.quickstash.QuickStashApp
import com.example.quickstash.data.nas.NasClient
import com.example.quickstash.data.nas.NasFile
import com.example.quickstash.data.repository.FileRepository
import com.example.quickstash.data.repository.LibraryRepository
import com.example.quickstash.data.repository.MediaOrganizeRepository
import com.example.quickstash.data.repository.MediaSequenceHolder
import com.example.quickstash.data.settings.ConnectionSettingsStore
import com.example.quickstash.media.coil.NasFull
import com.example.quickstash.media.coil.NasImageCache
import com.example.quickstash.ui.components.TagRowState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

sealed interface ViewerUiState {
    data object Loading : ViewerUiState
    data class Error(val message: String) : ViewerUiState
    data class Content(
        val images: List<NasFile>,
        val initialIndex: Int,
    ) : ViewerUiState
}

class ViewerViewModel(
    private val fileRepository: FileRepository,
    private val nasClient: NasClient,
    private val sequenceHolder: MediaSequenceHolder,
    private val imageCache: NasImageCache,
    private val settingsStore: ConnectionSettingsStore,
    private val organizeRepository: MediaOrganizeRepository,
    private val libraryRepository: LibraryRepository,
    private val folderPath: String,
    private val imagePath: String,
) : ViewModel() {

    private val _uiState = MutableStateFlow<ViewerUiState>(ViewerUiState.Loading)
    val uiState: StateFlow<ViewerUiState> = _uiState.asStateFlow()

    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message.asStateFlow()

    private val _slideshowSeconds = MutableStateFlow(5)
    val slideshowSeconds: StateFlow<Int> = _slideshowSeconds.asStateFlow()

    private val _groupOf = MutableStateFlow<Map<String, Long>>(emptyMap())
    val groupOf: StateFlow<Map<String, Long>> = _groupOf.asStateFlow()

    private val _tagDialog = MutableStateFlow<List<TagRowState>?>(null)
    val tagDialog: StateFlow<List<TagRowState>?> = _tagDialog.asStateFlow()

    fun setTier(image: NasFile, tier: String?) {
        viewModelScope.launch {
            runCatching { libraryRepository.setManualTier(listOf(image.path), tier) }
                .onSuccess {
                    _message.value =
                        if (tier != null) "Tier $tier を設定しました" else "Tier を自動判定に戻しました"
                }
                .onFailure { _message.value = "Tier の設定に失敗しました" }
        }
    }

    fun openTagDialog(image: NasFile) {
        viewModelScope.launch {
            runCatching { refreshTagDialog(image.path) }
        }
    }

    fun closeTagDialog() {
        _tagDialog.value = null
    }

    private suspend fun refreshTagDialog(path: String) {
        val tags = libraryRepository.allTags()
        val appliedCounts = libraryRepository.tagStateFor(listOf(path))
        _tagDialog.value = tags.map { tag ->
            TagRowState(
                id = tag.id,
                name = tag.name,
                applied = appliedCounts[tag.id] == 1,
            )
        }
    }

    fun toggleTag(tagId: Long, apply: Boolean, image: NasFile) {
        viewModelScope.launch {
            runCatching {
                if (apply) {
                    libraryRepository.tagFiles(tagId, listOf(image))
                } else {
                    libraryRepository.untagPaths(tagId, listOf(image.path))
                }
                refreshTagDialog(image.path)
            }
        }
    }

    fun createTagAndApply(name: String, image: NasFile) {
        viewModelScope.launch {
            runCatching {
                val tagId = libraryRepository.createTag(name)
                libraryRepository.tagFiles(tagId, listOf(image))
                refreshTagDialog(image.path)
            }.onFailure { _message.value = "タグ作成に失敗しました: ${it.message}" }
        }
    }

    fun prefetchOriginal(file: NasFile) {
        viewModelScope.launch {
            runCatching { imageCache.getOriginal(NasFull.from(file)) }
        }
    }

    fun recordView(file: NasFile?, millis: Long) {
        if (file == null || millis < MIN_VIEW_MILLIS) return
        viewModelScope.launch {
            runCatching { libraryRepository.recordView(file.path, millis) }
        }
    }

    fun deleteImage(image: NasFile) {
        viewModelScope.launch {
            try {
                nasClient.deleteFile(image.path)
                runCatching {
                    libraryRepository.deletePathData(image.path)
                    organizeRepository.deletePathData(image.path)
                }
                _uiState.update { state ->
                    if (state !is ViewerUiState.Content) return@update state
                    val remaining = state.images.filterNot { it.path == image.path }
                    if (remaining.isEmpty()) {
                        ViewerUiState.Error("画像がありません")
                    } else {
                        state.copy(images = remaining)
                    }
                }
                _message.value = "${image.name} を削除しました"
            } catch (e: Exception) {
                Log.e("ViewerViewModel", "delete failed: ${image.path}", e)
                _message.value = "削除に失敗しました: ${e.message ?: e.javaClass.simpleName}"
            }
        }
    }

    init {
        viewModelScope.launch {
            try {
                _slideshowSeconds.value = settingsStore.current().slideshowSeconds
                _groupOf.value =
                    runCatching { organizeRepository.manualLayout(folderPath).groupId }
                        .getOrDefault(emptyMap())
                val fromHolder = sequenceHolder.media.filter { it.isImage }
                val images = if (fromHolder.any { it.path == imagePath }) {
                    fromHolder
                } else {
                    fileRepository.list(folderPath).withSortedImages()
                }
                if (images.isEmpty()) {
                    _uiState.value = ViewerUiState.Error("画像がありません")
                } else {
                    _uiState.value = ViewerUiState.Content(
                        images = images,
                        initialIndex = images.indexOfFirst { it.path == imagePath }
                            .coerceAtLeast(0),
                    )
                }
            } catch (e: Exception) {
                _uiState.value = ViewerUiState.Error(e.message ?: e.javaClass.simpleName)
            }
        }
    }

    suspend fun listDirectories(path: String): List<NasFile> =
        fileRepository.list(path).directories

    fun moveImage(image: NasFile, destDir: String) {
        viewModelScope.launch {
            try {
                val destFull = fileRepository.resolvePath(destDir)
                val newPath = nasClient.moveFile(image.path, destFull)
                runCatching {
                    organizeRepository.renamePath(image.path, newPath)
                    libraryRepository.renamePath(image.path, newPath)
                }
                _uiState.update { state ->
                    if (state !is ViewerUiState.Content) return@update state
                    val remaining = state.images.filterNot { it.path == image.path }
                    if (remaining.isEmpty()) {
                        ViewerUiState.Error("画像がありません")
                    } else {
                        state.copy(images = remaining)
                    }
                }
                _message.value = "${image.name} を移動しました"
            } catch (e: Exception) {
                Log.e("ViewerViewModel", "move failed: ${image.path} -> $destDir", e)
                _message.value = "移動に失敗しました: ${e.message ?: e.javaClass.simpleName}"
            }
        }
    }

    fun consumeMessage() {
        _message.value = null
    }

    companion object {
        private const val MIN_VIEW_MILLIS = 800L

        fun factory(folderPath: String, imagePath: String) = viewModelFactory {
            initializer {
                val app = this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY] as QuickStashApp
                ViewerViewModel(
                    app.container.fileRepository,
                    app.container.nasClient,
                    app.container.mediaSequenceHolder,
                    app.container.imageCache,
                    app.container.settingsStore,
                    app.container.mediaOrganizeRepository,
                    app.container.libraryRepository,
                    folderPath,
                    imagePath,
                )
            }
        }
    }
}
