package com.example.quickstash.ui.tags

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.quickstash.QuickStashApp
import com.example.quickstash.data.local.TagWithCount
import com.example.quickstash.data.nas.NasFile
import com.example.quickstash.data.repository.LibraryRepository
import com.example.quickstash.data.repository.MediaSequenceHolder
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class TagsViewModel(
    private val libraryRepository: LibraryRepository,
) : ViewModel() {

    private val _tags = MutableStateFlow<List<TagWithCount>?>(null)
    val tags: StateFlow<List<TagWithCount>?> = _tags.asStateFlow()

    init {
        refresh()
    }

    fun refresh() {
        viewModelScope.launch {
            _tags.value = runCatching { libraryRepository.allTags() }.getOrDefault(emptyList())
        }
    }

    companion object {
        val Factory = viewModelFactory {
            initializer {
                val app = this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY] as QuickStashApp
                TagsViewModel(app.container.libraryRepository)
            }
        }
    }
}

class TagMediaViewModel(
    private val libraryRepository: LibraryRepository,
    private val sequenceHolder: MediaSequenceHolder,
    private val tagId: Long,
) : ViewModel() {

    private val _media = MutableStateFlow<List<NasFile>?>(null)
    val media: StateFlow<List<NasFile>?> = _media.asStateFlow()

    init {
        refresh()
    }

    fun refresh() {
        viewModelScope.launch {
            val list = runCatching { libraryRepository.mediaForTag(tagId) }
                .getOrDefault(emptyList())
            _media.value = list
        }
    }

    fun prepareSequence() {
        sequenceHolder.set(_media.value.orEmpty())
    }

    companion object {
        fun factory(tagId: Long) = viewModelFactory {
            initializer {
                val app = this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY] as QuickStashApp
                TagMediaViewModel(
                    app.container.libraryRepository,
                    app.container.mediaSequenceHolder,
                    tagId,
                )
            }
        }
    }
}
