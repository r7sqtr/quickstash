package com.example.quickstash.data.repository

import com.example.quickstash.data.local.ImageHashDao
import com.example.quickstash.data.local.ImageHashEntity
import com.example.quickstash.data.local.MediaOrderDao
import com.example.quickstash.data.local.MediaOrderEntity
import com.example.quickstash.data.nas.NasFile
import com.example.quickstash.domain.ImageSignature
import com.example.quickstash.domain.ManualLayout
import com.example.quickstash.media.coil.NasImageCache
import com.example.quickstash.media.coil.NasThumb
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext

class MediaOrganizeRepository(
    private val imageHashDao: ImageHashDao,
    private val mediaOrderDao: MediaOrderDao,
    private val imageCache: NasImageCache,
) {

    suspend fun signaturesFor(images: List<NasFile>): Map<String, ImageSignature> =
        withContext(Dispatchers.IO) {
            val targets = images.filter { it.isImage }
            if (targets.isEmpty()) return@withContext emptyMap()
            val cached = targets.map { it.path }
                .chunked(SQL_CHUNK)
                .flatMap { imageHashDao.getByPaths(it) }
                .associateBy { it.path }
            val result = HashMap<String, ImageSignature>(targets.size)
            val toCompute = ArrayList<NasFile>()
            for (image in targets) {
                val hit = cached[image.path]
                if (hit != null && hit.size == image.size && hit.mtime == image.mtime) {
                    result[image.path] = ImageSignature(hit.dhashH, hit.dhashV, hit.histogram)
                } else {
                    toCompute.add(image)
                }
            }
            coroutineScope {
                val permits = Semaphore(HASH_PARALLELISM)
                toCompute.map { image ->
                    async {
                        permits.withPermit {
                            runCatching {
                                val signature =
                                    imageCache.computeSignature(NasThumb.from(image))
                                imageHashDao.upsert(
                                    ImageHashEntity(
                                        image.path,
                                        image.size,
                                        image.mtime,
                                        signature.hashH,
                                        signature.hashV,
                                        signature.histogram,
                                    ),
                                )
                                image.path to signature
                            }.getOrNull()
                        }
                    }
                }.awaitAll().filterNotNull().forEach { (path, sig) -> result[path] = sig }
            }
            result
        }

    suspend fun manualLayout(folderPath: String): ManualLayout {
        val rows = mediaOrderDao.getForFolder(folderPath)
        return ManualLayout(
            orderIndex = rows.associate { it.path to it.orderIndex },
            groupId = rows.mapNotNull { row -> row.groupId?.let { row.path to it } }.toMap(),
        )
    }

    suspend fun saveManualLayout(
        folderPath: String,
        orderedPaths: List<String>,
        groupOf: Map<String, Long>,
    ) {
        val entries = orderedPaths.mapIndexed { index, path ->
            MediaOrderEntity(folderPath, path, index, groupOf[path])
        }
        mediaOrderDao.replaceFolder(folderPath, entries)
    }

    suspend fun renamePath(oldPath: String, newPath: String) {
        mediaOrderDao.renamePath(oldPath, newPath)
        imageHashDao.delete(oldPath)
    }

    suspend fun deletePathData(path: String) {
        mediaOrderDao.deletePath(path)
        imageHashDao.delete(path)
    }

    suspend fun deleteFolderData(folderPath: String) {
        val prefix = LibraryRepository.escapeLike(folderPath.trimEnd('/')) + "/"
        mediaOrderDao.deleteByPrefix(prefix)
        imageHashDao.deleteByPrefix(prefix)
    }

    suspend fun deletePathsData(paths: List<String>) {
        paths.chunked(SQL_CHUNK).forEach {
            mediaOrderDao.deletePaths(it)
            imageHashDao.deletePaths(it)
        }
    }

    companion object {
        private const val HASH_PARALLELISM = 6
        private const val SQL_CHUNK = 900
    }
}
