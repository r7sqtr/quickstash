package com.example.quickstash.media.coil

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import android.util.Log
import com.example.quickstash.data.nas.NasClient
import com.example.quickstash.media.video.VideoThumbnailer
import java.io.File
import java.security.MessageDigest
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit

class NasImageCache(
    context: Context,
    private val nasClient: NasClient,
    private val videoThumbnailer: VideoThumbnailer,
) {

    private val thumbDir = File(context.filesDir, "thumbs").apply { mkdirs() }
    private val originalDir = File(context.cacheDir, "originals").apply { mkdirs() }
    private val tmpDir = File(context.cacheDir, "smb-tmp").apply { mkdirs() }
    private val semaphore = Semaphore(8)
    private val inFlight = ConcurrentHashMap<String, CompletableDeferred<File>>()

    suspend fun getThumbnail(model: NasThumb): File {
        val out = File(thumbDir, "${keyFor(model.path, model.size, model.mtime)}.jpg")
        return getOrProduce(out) {
            downloadTo(model.path) { tmp -> writeThumbnail(tmp, out) }
        }
    }

    suspend fun computeSignature(model: NasThumb): com.example.quickstash.domain.ImageSignature {
        val file = getThumbnail(model)
        val bitmap = BitmapFactory.decodeFile(file.absolutePath)
            ?: throw IllegalStateException("デコード失敗: ${model.path}")
        try {
            val width = bitmap.width
            val height = bitmap.height
            val pixels = IntArray(width * height)
            bitmap.getPixels(pixels, 0, width, 0, 0, width, height)
            return com.example.quickstash.domain.ImageSignatures.compute(pixels, width, height)
        } finally {
            bitmap.recycle()
        }
    }

    suspend fun getOriginal(model: NasFull): File {
        val out = File(originalDir, keyFor(model.path, model.size, model.mtime))
        val result = getOrProduce(out) {
            downloadTo(model.path) { tmp ->
                if (!tmp.renameTo(out)) {
                    tmp.copyTo(out, overwrite = true)
                }
            }
        }
        trimDir(originalDir, ORIGINAL_CACHE_MAX_BYTES, exclude = out)
        return result
    }

    suspend fun getVideoThumbnail(model: NasVideoThumb): File {
        val out = File(thumbDir, "${keyFor(model.path, model.size, model.mtime)}_v.jpg")
        return getOrProduce(out) {
            val bitmap = videoThumbnailer.extractFrame(model.path)
            try {
                writeScaledJpeg(bitmap, out)
            } finally {
                bitmap.recycle()
            }
        }
    }

    suspend fun getPdfThumbnail(model: NasPdfThumb): File {
        val out = File(thumbDir, "${keyFor(model.path, model.size, model.mtime)}_p.jpg")
        if (out.exists()) return out
        val pdf = getOriginal(NasFull(model.path, model.size, model.mtime))
        return getOrProduce(out) {
            val bitmap = renderPdfPage(pdf, 0)
            try {
                writeScaledJpeg(bitmap, out)
            } finally {
                bitmap.recycle()
            }
        }
    }

    private fun renderPdfPage(pdf: File, pageIndex: Int): Bitmap {
        ParcelFileDescriptor.open(pdf, ParcelFileDescriptor.MODE_READ_ONLY).use { descriptor ->
            PdfRenderer(descriptor).use { renderer ->
                val page = renderer.openPage(pageIndex.coerceIn(0, renderer.pageCount - 1))
                try {
                    val scale = MAX_THUMB_DIMENSION.toFloat() / maxOf(page.width, page.height)
                    val width = (page.width * scale).toInt().coerceAtLeast(1)
                    val height = (page.height * scale).toInt().coerceAtLeast(1)
                    val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
                    bitmap.eraseColor(Color.WHITE)
                    page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                    return bitmap
                } finally {
                    page.close()
                }
            }
        }
    }

    private suspend fun getOrProduce(out: File, produce: suspend () -> Unit): File {
        if (out.exists()) {
            out.setLastModified(System.currentTimeMillis())
            return out
        }
        while (true) {
            inFlight[out.name]?.let { existing ->
                try {
                    return existing.await()
                } catch (e: CancellationException) {
                    currentCoroutineContext().ensureActive()
                }
            }
            val mine = CompletableDeferred<File>()
            if (inFlight.putIfAbsent(out.name, mine) != null) continue
            try {
                if (!out.exists()) {
                    semaphore.withPermit {
                        val started = System.currentTimeMillis()
                        produce()
                        Log.d(TAG, "produce ${out.name} in ${System.currentTimeMillis() - started}ms")
                    }
                }
                mine.complete(out)
                return out
            } catch (e: Exception) {
                mine.completeExceptionally(e)
                throw e
            } finally {
                inFlight.remove(out.name)
            }
        }
    }

    private suspend fun downloadTo(path: String, finalize: (File) -> Unit) {
        val tmp = File(tmpDir, UUID.randomUUID().toString())
        try {
            nasClient.readFile(path) { input ->
                tmp.outputStream().use { input.copyTo(it, DOWNLOAD_BUFFER_SIZE) }
            }
            finalize(tmp)
        } finally {
            tmp.delete()
        }
    }

    private fun writeThumbnail(src: File, out: File) {
        val bitmap = decodeScaled(src, MAX_THUMB_DIMENSION)
            ?: throw IllegalStateException("デコード失敗: ${src.name}")
        try {
            writeScaledJpeg(bitmap, out)
        } finally {
            bitmap.recycle()
        }
    }

    private fun writeScaledJpeg(bitmap: Bitmap, out: File) {
        val scaled = scaleDown(bitmap, MAX_THUMB_DIMENSION)
        val partial = File(out.parentFile, "${out.name}.part")
        try {
            partial.outputStream().use {
                scaled.compress(Bitmap.CompressFormat.JPEG, 85, it)
            }
            if (!partial.renameTo(out)) {
                throw IllegalStateException("サムネイル保存失敗: ${out.name}")
            }
        } finally {
            partial.delete()
            if (scaled !== bitmap) scaled.recycle()
        }
    }

    private fun scaleDown(bitmap: Bitmap, maxDimension: Int): Bitmap {
        val largest = maxOf(bitmap.width, bitmap.height)
        if (largest <= maxDimension) return bitmap
        val ratio = maxDimension.toFloat() / largest
        return Bitmap.createScaledBitmap(
            bitmap,
            (bitmap.width * ratio).toInt().coerceAtLeast(1),
            (bitmap.height * ratio).toInt().coerceAtLeast(1),
            true,
        )
    }

    private fun decodeScaled(file: File, maxDimension: Int): Bitmap? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.absolutePath, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
        var sampleSize = 1
        while (
            bounds.outWidth / (sampleSize * 2) >= maxDimension ||
            bounds.outHeight / (sampleSize * 2) >= maxDimension
        ) {
            sampleSize *= 2
        }
        val options = BitmapFactory.Options().apply { inSampleSize = sampleSize }
        return BitmapFactory.decodeFile(file.absolutePath, options)
    }

    fun trimToBudget() {
        tmpDir.listFiles()?.forEach { it.delete() }
        trimDir(thumbDir, THUMB_CACHE_MAX_BYTES)
        trimDir(originalDir, ORIGINAL_CACHE_MAX_BYTES)
    }

    fun cacheSizeBytes(): Long =
        dirSize(thumbDir) + dirSize(originalDir) + dirSize(tmpDir)

    fun clearCaches() {
        tmpDir.listFiles()?.forEach { it.delete() }
        originalDir.listFiles()?.forEach { it.delete() }
        thumbDir.listFiles()?.forEach { it.delete() }
    }

    private fun dirSize(dir: File): Long =
        dir.listFiles()?.sumOf { it.length() } ?: 0L

    @Synchronized
    private fun trimDir(dir: File, maxBytes: Long, exclude: File? = null) {
        val files = dir.listFiles() ?: return
        var total = files.sumOf { it.length() }
        if (total <= maxBytes) return
        val target = maxBytes * 9 / 10
        files.sortedBy { it.lastModified() }.forEach { file ->
            if (total <= target) return
            if (file.name == exclude?.name) return@forEach
            if (inFlight.containsKey(file.name)) return@forEach
            val len = file.length()
            if (file.delete()) total -= len
        }
    }

    private fun keyFor(path: String, size: Long, mtime: Long): String {
        val digest = MessageDigest.getInstance("SHA-1")
            .digest(path.toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }
        return "${digest}_${size}_$mtime"
    }

    companion object {
        private const val MAX_THUMB_DIMENSION = 512
        private const val DOWNLOAD_BUFFER_SIZE = 1024 * 1024
        private const val THUMB_CACHE_MAX_BYTES = 512L * 1024 * 1024
        private const val ORIGINAL_CACHE_MAX_BYTES = 256L * 1024 * 1024
        private const val TAG = "NasImageCache"
    }
}
