package com.example.quickstash.ui.player

import android.app.Application
import android.util.Log
import androidx.annotation.OptIn
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.SeekParameters
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import com.example.quickstash.QuickStashApp
import com.example.quickstash.data.nas.NasFile
import com.example.quickstash.data.nas.SmbConnectionManager
import com.example.quickstash.data.repository.FileRepository
import com.example.quickstash.data.settings.ConnectionSettingsStore
import com.example.quickstash.media.video.SmbDataSource
import kotlin.concurrent.thread
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class PlayerViewModel(
    private val application: Application,
    private val settingsStore: ConnectionSettingsStore,
    private val fileRepository: FileRepository,
    private val sequenceHolder: com.example.quickstash.data.repository.MediaSequenceHolder,
    private val folderPath: String,
    private val videoPath: String,
) : ViewModel() {

    private var session: SmbConnectionManager.Companion.OpenedShare? = null

    private val _player = MutableStateFlow<ExoPlayer?>(null)
    val player: StateFlow<ExoPlayer?> = _player.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    private val _skipSeconds = MutableStateFlow(10)
    val skipSeconds: StateFlow<Int> = _skipSeconds.asStateFlow()

    init {
        viewModelScope.launch {
            try {
                _skipSeconds.value = settingsStore.current().skipSeconds
                val fromHolder = sequenceHolder.media.filter { it.isVideo }
                val videos = (
                    if (fromHolder.any { it.path == videoPath }) {
                        fromHolder
                    } else {
                        runCatching { fileRepository.list(folderPath).videos }
                            .getOrDefault(emptyList())
                    }
                    )
                    .ifEmpty {
                        listOf(
                            NasFile(
                                path = videoPath,
                                name = videoPath.substringAfterLast('/'),
                                size = 0,
                                mtime = 0,
                                isDirectory = false,
                            ),
                        )
                    }
                val startIndex = videos.indexOfFirst { it.path == videoPath }.coerceAtLeast(0)
                val settings = settingsStore.current()
                val opened = withContext(Dispatchers.IO) {
                    SmbConnectionManager.openShare(settings)
                }
                session = opened
                _player.value = buildPlayer(opened, videos, startIndex)
            } catch (e: Exception) {
                Log.e(TAG, "player setup failed: $videoPath", e)
                _error.value = "再生の準備に失敗しました: ${e.message ?: e.javaClass.simpleName}"
            }
        }
    }

    @OptIn(UnstableApi::class)
    private fun buildPlayer(
        opened: SmbConnectionManager.Companion.OpenedShare,
        videos: List<NasFile>,
        startIndex: Int,
    ): ExoPlayer {
        val dataSourceFactory = DataSource.Factory { SmbDataSource(opened.share) }
        val mediaItems = videos.map { video ->
            MediaItem.Builder()
                .setUri(SmbDataSource.uriFor(video.path))
                .setMediaId(video.path)
                .setMediaMetadata(
                    MediaMetadata.Builder().setTitle(video.name).build(),
                )
                .build()
        }
        return ExoPlayer.Builder(application)
            .setMediaSourceFactory(DefaultMediaSourceFactory(dataSourceFactory))
            .setSeekParameters(SeekParameters.CLOSEST_SYNC)
            .build()
            .apply {
                addListener(object : Player.Listener {
                    override fun onPlayerError(error: PlaybackException) {
                        Log.e(TAG, "playback error", error)
                        _error.value = "再生エラー: ${error.errorCodeName}"
                    }
                })
                setMediaItems(mediaItems, startIndex, 0)
                playWhenReady = true
                prepare()
            }
    }

    override fun onCleared() {
        _player.value?.release()
        _player.value = null
        val toClose = session
        session = null
        thread(name = "smb-session-close") {
            toClose?.close()
        }
    }

    companion object {
        private const val TAG = "PlayerViewModel"

        fun factory(folderPath: String, videoPath: String) = viewModelFactory {
            initializer {
                val app = this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY] as QuickStashApp
                PlayerViewModel(
                    app,
                    app.container.settingsStore,
                    app.container.fileRepository,
                    app.container.mediaSequenceHolder,
                    folderPath,
                    videoPath,
                )
            }
        }
    }
}
