package com.example.quickstash.ui.pdf

import android.app.Application
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.quickstash.QuickStashApp
import com.example.quickstash.data.repository.FileRepository
import com.example.quickstash.media.coil.NasFull
import com.example.quickstash.media.coil.NasImageCache
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

sealed interface PdfUiState {
    data object Loading : PdfUiState
    data class Error(val message: String) : PdfUiState
    data class Ready(val pageCount: Int) : PdfUiState
}

class PdfViewModel(
    application: Application,
    private val fileRepository: FileRepository,
    private val imageCache: NasImageCache,
    private val pdfPath: String,
) : ViewModel() {

    private val _uiState = MutableStateFlow<PdfUiState>(PdfUiState.Loading)
    val uiState: StateFlow<PdfUiState> = _uiState.asStateFlow()

    private val renderLock = Mutex()
    private var renderer: PdfRenderer? = null
    private var descriptor: ParcelFileDescriptor? = null

    init {
        viewModelScope.launch {
            try {
                val meta = fileRepository.list(parentOf(pdfPath)).media
                    .firstOrNull { it.path == pdfPath }
                val file: File = withContext(Dispatchers.IO) {
                    imageCache.getOriginal(
                        NasFull(pdfPath, meta?.size ?: 0, meta?.mtime ?: 0),
                    )
                }
                withContext(Dispatchers.IO) {
                    val fd = ParcelFileDescriptor.open(
                        file,
                        ParcelFileDescriptor.MODE_READ_ONLY,
                    )
                    descriptor = fd
                    renderer = PdfRenderer(fd)
                }
                _uiState.value = PdfUiState.Ready(renderer?.pageCount ?: 0)
            } catch (e: Exception) {
                Log.e(TAG, "pdf open failed: $pdfPath", e)
                _uiState.value = PdfUiState.Error(e.message ?: e.javaClass.simpleName)
            }
        }
    }

    suspend fun renderPage(index: Int, targetWidth: Int): Bitmap? = renderLock.withLock {
        withContext(Dispatchers.IO) {
            val r = renderer ?: return@withContext null
            if (index !in 0 until r.pageCount) return@withContext null
            val page = r.openPage(index)
            try {
                val scale = targetWidth.toFloat() / page.width
                val width = targetWidth.coerceAtLeast(1)
                val height = (page.height * scale).toInt().coerceAtLeast(1)
                val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
                bitmap.eraseColor(Color.WHITE)
                page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                bitmap
            } finally {
                page.close()
            }
        }
    }

    override fun onCleared() {
        viewModelScope.launch(Dispatchers.IO) {
            renderLock.withLock {
                runCatching { renderer?.close() }
                runCatching { descriptor?.close() }
            }
        }
    }

    private fun parentOf(path: String): String = path.substringBeforeLast('/', "")

    companion object {
        private const val TAG = "PdfViewModel"

        fun factory(pdfPath: String) = viewModelFactory {
            initializer {
                val app = this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY] as QuickStashApp
                PdfViewModel(app, app.container.fileRepository, app.container.imageCache, pdfPath)
            }
        }
    }
}
