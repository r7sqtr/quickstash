package com.example.quickstash.ui.browser

import android.content.ContentResolver
import android.net.Uri
import android.provider.OpenableColumns
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.quickstash.QuickStashApp
import com.example.quickstash.data.nas.NasClient
import com.example.quickstash.data.nas.NasFile
import com.example.quickstash.data.repository.FileIndexRepository
import com.example.quickstash.data.repository.FileRepository
import com.example.quickstash.data.repository.LibraryRepository
import com.example.quickstash.data.repository.MediaOrganizeRepository
import com.example.quickstash.data.repository.MediaSequenceHolder
import com.example.quickstash.data.settings.ConnectionSettingsStore
import com.example.quickstash.data.local.TagWithCount
import com.example.quickstash.domain.ManualLayout
import com.example.quickstash.domain.MediaFilter
import com.example.quickstash.domain.MediaSort
import com.example.quickstash.domain.NaturalOrderComparator
import com.example.quickstash.domain.SortContext
import com.example.quickstash.domain.SortKey
import com.example.quickstash.domain.SortSpec
import com.example.quickstash.domain.TierInfo
import com.example.quickstash.media.coil.NasImageCache
import com.example.quickstash.media.coil.NasThumb
import com.example.quickstash.ui.components.TagRowState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext

sealed interface BrowserUiState {
    data object Loading : BrowserUiState
    data class Error(val message: String) : BrowserUiState
    data class Content(
        val directories: List<NasFile>,
        val media: List<NasFile>,
        val sortLoading: Boolean = false,
    ) : BrowserUiState
}

data class FolderPreview(
    val hasContent: Boolean,
    val preview: NasFile?,
    val mediaCount: Int,
)

class BrowserViewModel(
    private val fileRepository: FileRepository,
    private val organizeRepository: MediaOrganizeRepository,
    private val libraryRepository: LibraryRepository,
    private val fileIndexRepository: FileIndexRepository,
    private val sequenceHolder: MediaSequenceHolder,
    private val imageCache: NasImageCache,
    private val nasClient: NasClient,
    private val contentResolver: ContentResolver,
    private val settingsStore: ConnectionSettingsStore,
    private val path: String,
) : ViewModel() {

    private val _uiState = MutableStateFlow<BrowserUiState>(BrowserUiState.Loading)
    val uiState: StateFlow<BrowserUiState> = _uiState.asStateFlow()

    private val _sortSpec = MutableStateFlow(SortSpec.DEFAULT)
    val sortSpec: StateFlow<SortSpec> = _sortSpec.asStateFlow()

    private val _folderInfo = MutableStateFlow<Map<String, FolderPreview>>(emptyMap())
    val folderInfo: StateFlow<Map<String, FolderPreview>> = _folderInfo.asStateFlow()

    private val _selection = MutableStateFlow<List<String>?>(null)
    val selection: StateFlow<List<String>?> = _selection.asStateFlow()

    private val _groupedPaths = MutableStateFlow<Map<String, Long>>(emptyMap())

    private val _groupBadges = MutableStateFlow<Map<String, String>>(emptyMap())
    val groupBadges: StateFlow<Map<String, String>> = _groupBadges.asStateFlow()

    private val _tierInfo = MutableStateFlow<Map<String, TierInfo>>(emptyMap())
    val tierInfo: StateFlow<Map<String, TierInfo>> = _tierInfo.asStateFlow()

    private val _tagDialog = MutableStateFlow<List<TagRowState>?>(null)
    val tagDialog: StateFlow<List<TagRowState>?> = _tagDialog.asStateFlow()

    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message.asStateFlow()

    private val _filter = MutableStateFlow(MediaFilter.EMPTY)
    val filter: StateFlow<MediaFilter> = _filter.asStateFlow()

    private val _filterInfo = MutableStateFlow<Pair<Int, Int>?>(null)
    val filterInfo: StateFlow<Pair<Int, Int>?> = _filterInfo.asStateFlow()

    private val _availableTags = MutableStateFlow<List<TagWithCount>>(emptyList())
    val availableTags: StateFlow<List<TagWithCount>> = _availableTags.asStateFlow()

    private val _uploadProgress = MutableStateFlow<Pair<Int, Int>?>(null)
    val uploadProgress: StateFlow<Pair<Int, Int>?> = _uploadProgress.asStateFlow()

    private val _gridColumns = MutableStateFlow(ConnectionSettingsStore.DEFAULT_GRID_COLUMNS)
    val gridColumns: StateFlow<Int> = _gridColumns.asStateFlow()

    private val _showTierBadges = MutableStateFlow(true)
    val showTierBadges: StateFlow<Boolean> = _showTierBadges.asStateFlow()

    fun adjustGridColumns(delta: Int) {
        val next = (_gridColumns.value + delta).coerceIn(
            ConnectionSettingsStore.MIN_GRID_COLUMNS,
            ConnectionSettingsStore.MAX_GRID_COLUMNS,
        )
        if (next == _gridColumns.value) return
        _gridColumns.value = next
        viewModelScope.launch {
            runCatching { settingsStore.saveGridColumns(next) }
        }
    }

    private var rawListing: FileRepository.Listing? = null
    private var listingKey: String? = null
    private var layoutCache: ManualLayout? = null
    private var tierInfoCache: Map<String, TierInfo>? = null
    private var tierInfoCacheKey: String? = null
    private var similarOrderCache: List<NasFile>? = null
    private var similarOrderKey: String? = null
    private val folderCheckSemaphore = Semaphore(3)
    private val folderChecksRequested = mutableSetOf<String>()

    init {
        viewModelScope.launch {
            runCatching { settingsStore.gridColumns() }
                .onSuccess { _gridColumns.value = it }
            runCatching { settingsStore.showTierBadges() }
                .onSuccess { _showTierBadges.value = it }
        }
        load()
    }

    fun load() {
        _uiState.value = BrowserUiState.Loading
        layoutCache = null
        tierInfoCache = null
        viewModelScope.launch {
            try {
                val listing = coroutineScope {
                    val sortSpecDeferred = async { fileRepository.sortSpec(path) }
                    val layoutDeferred = async { organizeRepository.manualLayout(path) }
                    val listingDeferred = async { fileRepository.list(path) }
                    _sortSpec.value = sortSpecDeferred.await()
                    val layout = layoutDeferred.await()
                    layoutCache = layout
                    _groupedPaths.value = layout.groupId
                    listingDeferred.await()
                }
                rawListing = listing
                listingKey = keyOf(listing)
                applyAndPublish(listing)
                prefetch(listing.media)
                launch { runCatching { fileIndexRepository.indexFolder(listing.media) } }
            } catch (e: Exception) {
                _uiState.value =
                    BrowserUiState.Error(e.message ?: e.javaClass.simpleName)
            }
        }
    }

    fun refreshIfLoaded() {
        if (_uiState.value !is BrowserUiState.Content) return
        viewModelScope.launch {
            runCatching { settingsStore.showTierBadges() }
                .onSuccess { _showTierBadges.value = it }
            runCatching { fileRepository.list(path) }.onSuccess { listing ->
                val key = keyOf(listing)
                if (key == listingKey) {
                    tierInfoCache = null
                    republishFiltered()
                    return@onSuccess
                }
                rawListing = listing
                listingKey = key
                layoutCache = null
                tierInfoCache = null
                applyAndPublish(listing)
            }
        }
    }

    private fun keyOf(listing: FileRepository.Listing): String = buildString {
        listing.directories.forEach { append(it.path).append(';') }
        append('#')
        listing.media.forEach {
            append(it.path).append(':').append(it.size).append(':').append(it.mtime).append(';')
        }
    }

    fun toggleSortKey(key: SortKey) {
        val next = _sortSpec.value.toggled(key)
        _sortSpec.value = next
        viewModelScope.launch {
            runCatching { fileRepository.saveSortSpec(path, next) }
        }
        rawListing?.let { applyAndPublish(it) }
    }

    fun startSelection(path: String) {
        if (_selection.value == null) _selection.value = listOf(path)
    }

    fun toggleSelect(path: String) {
        val current = _selection.value ?: return
        _selection.value = if (path in current) current - path else current + path
    }

    fun cancelSelection() {
        _selection.value = null
    }


    fun toggleSelectAll() {
        val content = _uiState.value as? BrowserUiState.Content ?: return
        val current = _selection.value ?: return
        _selection.value = if (current.size == content.media.size) {
            emptyList()
        } else {
            content.media.map { it.path }
        }
    }

    fun groupSelected() {
        val selected = _selection.value ?: return
        val content = _uiState.value as? BrowserUiState.Content ?: return
        if (selected.size < 2) {
            _selection.value = null
            return
        }
        viewModelScope.launch {
            val layout = cachedLayout()
            val nextGid = (layout.groupId.values.maxOrNull() ?: 0L) + 1
            val groupOf = layout.groupId.toMutableMap()
            selected.forEach { groupOf[it] = nextGid }
            val selectedSet = selected.toSet()
            val selectionIterator = selected.iterator()
            val orderedPaths = content.media.map { file ->
                if (file.path in selectedSet) selectionIterator.next() else file.path
            }
            organizeRepository.saveManualLayout(path, orderedPaths, groupOf)
            layoutCache = null
            _groupedPaths.value = groupOf
            _selection.value = null
            rawListing?.let { applyAndPublish(it) }
        }
    }

    fun ungroupSelected() {
        val selected = _selection.value ?: return
        val content = _uiState.value as? BrowserUiState.Content ?: return
        viewModelScope.launch {
            val layout = cachedLayout()
            val groupOf = layout.groupId.toMutableMap()
            selected.forEach { groupOf.remove(it) }
            organizeRepository.saveManualLayout(path, content.media.map { it.path }, groupOf)
            layoutCache = null
            _groupedPaths.value = groupOf
            _selection.value = null
            rawListing?.let { applyAndPublish(it) }
        }
    }

    private fun selectedFiles(): List<NasFile> {
        val selected = _selection.value ?: return emptyList()
        val content = _uiState.value as? BrowserUiState.Content ?: return emptyList()
        val byPath = content.media.associateBy { it.path }
        return selected.mapNotNull { byPath[it] }
    }

    fun openTagDialog() {
        viewModelScope.launch { refreshTagDialog() }
    }

    fun closeTagDialog() {
        _tagDialog.value = null
    }

    private suspend fun refreshTagDialog() {
        val selected = _selection.value ?: return
        val tags = libraryRepository.allTags()
        val appliedCounts = libraryRepository.tagStateFor(selected)
        _tagDialog.value = tags.map { tag ->
            TagRowState(
                id = tag.id,
                name = tag.name,
                applied = appliedCounts[tag.id] == selected.size,
            )
        }
    }

    fun toggleTag(tagId: Long, apply: Boolean) {
        viewModelScope.launch {
            runCatching {
                if (apply) {
                    libraryRepository.tagFiles(tagId, selectedFiles())
                } else {
                    libraryRepository.untagPaths(tagId, _selection.value.orEmpty())
                }
                refreshTagDialog()
            }
        }
    }

    fun createTagAndApply(name: String) {
        viewModelScope.launch {
            runCatching {
                val tagId = libraryRepository.createTag(name)
                libraryRepository.tagFiles(tagId, selectedFiles())
                refreshTagDialog()
            }.onFailure { _message.value = "タグ作成に失敗しました: ${it.message}" }
        }
    }

    fun setTierForSelection(tier: String?) {
        val selected = _selection.value ?: return
        viewModelScope.launch {
            runCatching { libraryRepository.setManualTier(selected, tier) }
            _selection.value = null
            tierInfoCache = null
            republishFiltered()
        }
    }

    private suspend fun cachedTierInfo(media: List<NasFile>): Map<String, TierInfo> {
        val key = listingKey
        tierInfoCache?.takeIf { tierInfoCacheKey == key }?.let { return it }
        val info = runCatching { libraryRepository.tierInfo(media) }.getOrDefault(emptyMap())
        tierInfoCache = info
        tierInfoCacheKey = key
        return info
    }

    private suspend fun cachedLayout(): ManualLayout =
        layoutCache ?: organizeRepository.manualLayout(path).also { layoutCache = it }

    suspend fun listDirectories(path: String): List<NasFile> =
        fileRepository.list(path).directories

    fun moveSelection(destDir: String) {
        val selected = _selection.value ?: return
        viewModelScope.launch {
            val destFull = runCatching { fileRepository.resolvePath(destDir) }.getOrNull()
            if (destFull == null) {
                _message.value = "移動先の解決に失敗しました"
                return@launch
            }
            var failed = 0
            selected.forEach { target ->
                runCatching {
                    val newPath = nasClient.moveFile(target, destFull)
                    if (newPath != target) {
                        runCatching {
                            organizeRepository.renamePath(target, newPath)
                            libraryRepository.renamePath(target, newPath)
                            fileIndexRepository.deletePath(target)
                        }
                    }
                }.onFailure { failed++ }
            }
            _selection.value = null
            _message.value = if (failed == 0) {
                "${selected.size}件を移動しました"
            } else {
                "${selected.size - failed}件移動、${failed}件失敗"
            }
            load()
        }
    }

    fun renameSelection(baseName: String) {
        val selected = _selection.value ?: return
        val trimmed = baseName.trim()
        if (trimmed.isEmpty() || trimmed.contains('/') || trimmed.contains('\\')) {
            _message.value = "ファイル名が不正です"
            return
        }
        viewModelScope.launch {
            val digits = selected.size.toString().length.coerceAtLeast(2)
            var failed = 0
            selected.forEachIndexed { index, target ->
                runCatching {
                    val ext = target.substringAfterLast('.', "")
                    val number = (index + 1).toString().padStart(digits, '0')
                    val newName =
                        if (ext.isEmpty()) "${trimmed}_$number" else "${trimmed}_$number.$ext"
                    val newPath = nasClient.renameFile(target, newName)
                    if (newPath != target) {
                        runCatching {
                            organizeRepository.renamePath(target, newPath)
                            libraryRepository.renamePath(target, newPath)
                            fileIndexRepository.deletePath(target)
                        }
                    }
                }.onFailure { failed++ }
            }
            _selection.value = null
            _message.value = if (failed == 0) {
                "${selected.size}件をリネームしました"
            } else {
                "${selected.size - failed}件リネーム、${failed}件失敗"
            }
            load()
        }
    }

    fun deleteSelection() {
        val selected = _selection.value ?: return
        viewModelScope.launch {
            var failed = 0
            val deleted = ArrayList<String>(selected.size)
            selected.forEach { target ->
                runCatching { nasClient.deleteFile(target) }
                    .onSuccess { deleted.add(target) }
                    .onFailure { failed++ }
            }
            runCatching {
                libraryRepository.deletePathsData(deleted)
                organizeRepository.deletePathsData(deleted)
                fileIndexRepository.deletePaths(deleted)
            }
            _selection.value = null
            _message.value = if (failed == 0) {
                "${selected.size}件を削除しました"
            } else {
                "${selected.size - failed}件削除、${failed}件失敗"
            }
            load()
        }
    }

    fun consumeMessage() {
        _message.value = null
    }

    fun uploadFiles(uris: List<Uri>) {
        if (uris.isEmpty() || _uploadProgress.value != null) return
        viewModelScope.launch {
            var failed = 0
            _uploadProgress.value = 0 to uris.size
            val destDir = runCatching { fileRepository.resolvePath(path) }.getOrNull()
            if (destDir == null) {
                _uploadProgress.value = null
                _message.value = "アップロード先の解決に失敗しました"
                return@launch
            }
            uris.forEachIndexed { index, uri ->
                _uploadProgress.value = index + 1 to uris.size
                runCatching {
                    val name = displayNameOf(uri) ?: "upload_${index + 1}"
                    withContext(Dispatchers.IO) {
                        contentResolver.openInputStream(uri)?.use { input ->
                            nasClient.uploadFile(destDir, name, input)
                        } ?: error("open failed")
                    }
                }.onFailure { failed++ }
            }
            _uploadProgress.value = null
            _message.value = if (failed == 0) {
                "${uris.size}件をアップロードしました"
            } else {
                "${uris.size - failed}件アップロード、${failed}件失敗"
            }
            load()
        }
    }

    fun deleteFolder(folderPath: String) {
        viewModelScope.launch {
            runCatching {
                nasClient.deleteDirectory(folderPath)
            }.onSuccess {
                runCatching {
                    libraryRepository.deleteFolderData(folderPath)
                    organizeRepository.deleteFolderData(folderPath)
                    fileIndexRepository.deleteFolder(folderPath)
                }
                folderChecksRequested.remove(folderPath)
                _folderInfo.update { it - folderPath }
                _message.value = "フォルダ「${folderPath.substringAfterLast('/')}」を削除しました"
                load()
            }.onFailure {
                _message.value =
                    "フォルダ削除に失敗しました: ${it.message ?: it.javaClass.simpleName}"
            }
        }
    }

    fun createFolder(name: String) {
        val trimmed = name.trim()
        if (trimmed.isEmpty() || trimmed.contains('/') || trimmed.contains('\\')) {
            _message.value = "フォルダ名が不正です"
            return
        }
        viewModelScope.launch {
            runCatching {
                val base = fileRepository.resolvePath(path)
                val target = if (base.isEmpty()) trimmed else "${base.trim('/')}/$trimmed"
                nasClient.createDirectory(target)
            }.onSuccess {
                _message.value = "フォルダ「$trimmed」を作成しました"
                load()
            }.onFailure {
                _message.value = "フォルダ作成に失敗しました: ${it.message ?: it.javaClass.simpleName}"
            }
        }
    }

    private fun displayNameOf(uri: Uri): String? =
        contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
            ?.use { cursor ->
                if (cursor.moveToFirst()) cursor.getString(0) else null
            }
            ?.takeIf { it.isNotBlank() }

    fun ensureFolderInfo(folderPath: String) {
        if (_folderInfo.value.containsKey(folderPath)) return
        if (!folderChecksRequested.add(folderPath)) return
        viewModelScope.launch {
            folderCheckSemaphore.withPermit {
                runCatching { nasClient.listFiles(folderPath) }
                    .onSuccess { entries ->
                        val media = entries.filter { !it.isDirectory && it.isMedia }
                        val preview = media.firstOrNull { it.isImage }
                            ?: media.firstOrNull { it.isVideo }
                        val info = FolderPreview(
                            hasContent = entries.isNotEmpty(),
                            preview = preview,
                            mediaCount = media.size,
                        )
                        _folderInfo.update { it + (folderPath to info) }
                    }
                    .onFailure { folderChecksRequested.remove(folderPath) }
            }
        }
    }

    private fun applyAndPublish(listing: FileRepository.Listing) {
        val dirComparator = compareBy(NaturalOrderComparator) { file: NasFile -> file.name }
        val sortedDirs = listing.directories.sortedWith(dirComparator)
        val spec = _sortSpec.value
        if (spec.needs(SortKey.SIMILAR) && _uiState.value !is BrowserUiState.Content) {
            _uiState.value =
                BrowserUiState.Content(sortedDirs, listing.media, sortLoading = true)
        }
        viewModelScope.launch {
            val layout = cachedLayout()
            val similarRank = if (spec.needs(SortKey.SIMILAR)) {
                similarRankFor(listing)
            } else {
                emptyMap()
            }
            val context = SortContext(
                similarRank = similarRank,
                groupIdOf = layout.groupId,
                orderIndexOf = layout.orderIndex,
            )
            val ordered = withContext(Dispatchers.Default) {
                MediaSort.clumpGroups(
                    listing.media.sortedWith(spec.comparator(context)),
                    layout,
                )
            }
            publish(sortedDirs, ordered)
        }
    }

    private suspend fun similarRankFor(listing: FileRepository.Listing): Map<String, Int> {
        val key = listingKey
        val baseOrder = similarOrderCache?.takeIf { similarOrderKey == key }
            ?: run {
                val signatures =
                    runCatching { organizeRepository.signaturesFor(listing.media) }
                        .getOrDefault(emptyMap())
                withContext(Dispatchers.Default) {
                    MediaSort.similarOrder(listing.media, signatures)
                }.also {
                    similarOrderCache = it
                    similarOrderKey = key
                }
            }
        return baseOrder.mapIndexed { index, file -> file.path to index }.toMap()
    }

    private var lastDirectories: List<NasFile> = emptyList()
    private var lastOrderedMedia: List<NasFile> = emptyList()

    private fun publish(directories: List<NasFile>, media: List<NasFile>) {
        lastDirectories = directories
        lastOrderedMedia = media
        republishFiltered()
    }

    fun setFilter(filter: MediaFilter) {
        _filter.value = filter
        republishFiltered()
    }

    fun loadAvailableTags() {
        viewModelScope.launch {
            _availableTags.value =
                runCatching { libraryRepository.allTags() }.getOrDefault(emptyList())
        }
    }

    private fun republishFiltered() {
        viewModelScope.launch {
            val filter = _filter.value
            val tiers = cachedTierInfo(lastOrderedMedia)
            val visible = if (filter.isEmpty) {
                _filterInfo.value = null
                lastOrderedMedia
            } else {
                val taggedPaths = if (filter.tagIds.isNotEmpty()) {
                    runCatching {
                        libraryRepository.pathsWithAnyTag(
                            lastOrderedMedia.map { it.path },
                            filter.tagIds,
                        )
                    }.getOrDefault(emptySet())
                } else {
                    emptySet()
                }
                val result = lastOrderedMedia.filter { file ->
                    (filter.types.isEmpty() || file.mediaType in filter.types) &&
                        (filter.manualTiers.isEmpty() || tiers[file.path]?.manual in filter.manualTiers) &&
                        (filter.autoTiers.isEmpty() || tiers[file.path]?.auto in filter.autoTiers) &&
                        (filter.tagIds.isEmpty() || file.path in taggedPaths)
                }
                _filterInfo.value = result.size to lastOrderedMedia.size
                result
            }
            sequenceHolder.set(visible)
            _groupBadges.value = buildGroupBadges(visible, _groupedPaths.value)
            _uiState.value =
                BrowserUiState.Content(lastDirectories, visible, sortLoading = false)
            _tierInfo.value = tiers
        }
    }

    private fun buildGroupBadges(
        media: List<NasFile>,
        groupOf: Map<String, Long>,
    ): Map<String, String> {
        if (groupOf.isEmpty()) return emptyMap()
        val letters = groupOf.values.distinct().sorted()
            .mapIndexed { index, gid -> gid to letterFor(index) }
            .toMap()
        val counters = mutableMapOf<Long, Int>()
        val result = mutableMapOf<String, String>()
        for (file in media) {
            val gid = groupOf[file.path] ?: continue
            val letter = letters.getValue(gid)
            val number = (counters[gid] ?: 0) + 1
            counters[gid] = number
            result[file.path] = "$letter-$number"
        }
        return result
    }

    private fun letterFor(index: Int): String {
        var value = index
        val builder = StringBuilder()
        do {
            builder.insert(0, ('A' + value % 26))
            value = value / 26 - 1
        } while (value >= 0)
        return builder.toString()
    }

    private fun prefetch(media: List<NasFile>) {
        media.filter { it.isImage }.take(PREFETCH_COUNT).forEach { image ->
            viewModelScope.launch {
                runCatching { imageCache.getThumbnail(NasThumb.from(image)) }
            }
        }
    }

    companion object {
        private const val PREFETCH_COUNT = 40

        fun factory(path: String) = viewModelFactory {
            initializer {
                val app = this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY] as QuickStashApp
                BrowserViewModel(
                    app.container.fileRepository,
                    app.container.mediaOrganizeRepository,
                    app.container.libraryRepository,
                    app.container.fileIndexRepository,
                    app.container.mediaSequenceHolder,
                    app.container.imageCache,
                    app.container.nasClient,
                    app.contentResolver,
                    app.container.settingsStore,
                    path,
                )
            }
        }
    }
}
