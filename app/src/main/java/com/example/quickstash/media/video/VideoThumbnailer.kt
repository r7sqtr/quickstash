package com.example.quickstash.media.video

import android.graphics.Bitmap
import android.media.MediaDataSource
import android.media.MediaMetadataRetriever
import android.os.Build
import com.example.quickstash.data.nas.SmbConnectionManager
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import com.hierynomus.msdtyp.AccessMask
import com.hierynomus.mssmb2.SMB2CreateDisposition
import com.hierynomus.mssmb2.SMB2ShareAccess
import com.hierynomus.smbj.share.File
import java.util.EnumSet

class VideoThumbnailer(private val connectionManager: SmbConnectionManager) {

    private fun extract(retriever: MediaMetadataRetriever, timeUs: Long): Bitmap? =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            retriever.getScaledFrameAtTime(
                timeUs,
                MediaMetadataRetriever.OPTION_CLOSEST_SYNC,
                MAX_DIMENSION,
                MAX_DIMENSION,
            )
        } else {
            retriever.getFrameAtTime(timeUs, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
        }

    suspend fun extractFrame(path: String): Bitmap {
        val job = currentCoroutineContext()[Job]
        return connectionManager.withShare { share ->
            val smbPath = path.trim('/').replace('/', '\\')
            val file = share.openFile(
                smbPath,
                EnumSet.of(AccessMask.GENERIC_READ),
                null,
                SMB2ShareAccess.ALL,
                SMB2CreateDisposition.FILE_OPEN,
                null,
            )
            try {
                val fileSize = file.fileInformation.standardInformation.endOfFile
                val dataSource = PagedSmbMediaDataSource(file, fileSize) {
                    job?.isActive != false
                }
                val retriever = MediaMetadataRetriever()
                try {
                    retriever.setDataSource(dataSource)
                    extract(retriever, 1_000_000L)
                        ?: extract(retriever, 0L)
                        ?: throw IllegalStateException("フレーム取得失敗: $path")
                } finally {
                    retriever.release()
                }
            } finally {
                runCatching { file.close() }
            }
        }
    }

    private class PagedSmbMediaDataSource(
        private val file: File,
        private val fileSize: Long,
        private val isActive: () -> Boolean,
    ) : MediaDataSource() {

        private val pages = object : LinkedHashMap<Long, ByteArray>(16, 0.75f, true) {
            override fun removeEldestEntry(
                eldest: MutableMap.MutableEntry<Long, ByteArray>,
            ): Boolean = size > MAX_PAGES
        }

        override fun readAt(position: Long, buffer: ByteArray, offset: Int, size: Int): Int {
            if (!isActive()) return -1
            if (position >= fileSize) return -1
            var total = 0
            while (total < size) {
                val absolute = position + total
                if (absolute >= fileSize) break
                val pageIndex = absolute / PAGE_SIZE
                val page = pageFor(pageIndex) ?: break
                val inPage = (absolute % PAGE_SIZE).toInt()
                val available = page.size - inPage
                if (available <= 0) break
                val toCopy = minOf(size - total, available)
                System.arraycopy(page, inPage, buffer, offset + total, toCopy)
                total += toCopy
            }
            return if (total == 0) -1 else total
        }

        override fun getSize(): Long = fileSize

        override fun close() {
            pages.clear()
        }

        private fun pageFor(pageIndex: Long): ByteArray? {
            pages[pageIndex]?.let { return it }
            if (!isActive()) return null
            val start = pageIndex * PAGE_SIZE
            if (start >= fileSize) return null
            val want = minOf(PAGE_SIZE.toLong(), fileSize - start).toInt()
            val page = ByteArray(want)
            var filled = 0
            while (filled < want) {
                val n = file.read(page, start + filled, filled, want - filled)
                if (n <= 0) break
                filled += n
            }
            if (filled == 0) return null
            val result = if (filled == want) page else page.copyOf(filled)
            pages[pageIndex] = result
            return result
        }

        companion object {
            private const val PAGE_SIZE = 512 * 1024
            private const val MAX_PAGES = 48
        }
    }

    companion object {
        private const val MAX_DIMENSION = 512
    }
}
