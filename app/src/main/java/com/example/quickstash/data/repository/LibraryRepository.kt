package com.example.quickstash.data.repository

import com.example.quickstash.data.local.MediaMetaDao
import com.example.quickstash.data.local.MediaTagEntity
import com.example.quickstash.data.local.TagDao
import com.example.quickstash.data.local.TagEntity
import com.example.quickstash.data.local.TagWithCount
import com.example.quickstash.data.nas.NasFile
import com.example.quickstash.domain.TierInfo
import kotlin.math.ceil

class LibraryRepository(
    private val mediaMetaDao: MediaMetaDao,
    private val tagDao: TagDao,
) {

    suspend fun recordView(path: String, millis: Long) {
        mediaMetaDao.recordView(path, millis.coerceIn(0, MAX_VIEW_MILLIS))
    }

    suspend fun setManualTier(paths: List<String>, tier: String?) {
        paths.chunked(SQL_CHUNK).forEach { mediaMetaDao.setManualTier(it, tier) }
    }

    suspend fun tierInfo(media: List<NasFile>): Map<String, TierInfo> {
        if (media.isEmpty()) return emptyMap()
        val metas = media.map { it.path }
            .chunked(SQL_CHUNK)
            .flatMap { mediaMetaDao.getByPaths(it) }
        if (metas.isEmpty()) return emptyMap()
        val manualOf = mutableMapOf<String, String>()
        val autoCandidates = ArrayList<Pair<String, Long>>()
        for (meta in metas) {
            meta.manualTier?.let { manualOf[meta.path] = it }
            val score = meta.viewCount * 10L + meta.viewMillis / 3000L
            if (score > 0) autoCandidates.add(meta.path to score)
        }
        val autoOf = mutableMapOf<String, String>()
        if (autoCandidates.isNotEmpty()) {
            autoCandidates.sortByDescending { it.second }
            val total = autoCandidates.size
            val sCut = ceil(total * 0.10).toInt().coerceAtLeast(1)
            val aCut = sCut + ceil(total * 0.20).toInt()
            val bCut = aCut + ceil(total * 0.30).toInt()
            autoCandidates.forEachIndexed { index, (path, _) ->
                autoOf[path] = when {
                    index < sCut -> "S"
                    index < aCut -> "A"
                    index < bCut -> "B"
                    else -> "C"
                }
            }
        }
        return (manualOf.keys + autoOf.keys).associateWith { path ->
            TierInfo(manual = manualOf[path], auto = autoOf[path])
        }
    }

    suspend fun tierBadges(media: List<NasFile>): Map<String, String> =
        tierInfo(media).mapNotNull { (path, info) ->
            info.effective?.let { path to it }
        }.toMap()

    suspend fun allTags(): List<TagWithCount> = tagDao.tagsWithCount()

    suspend fun createTag(name: String): Long {
        val trimmed = name.trim()
        require(trimmed.isNotEmpty()) { "タグ名が空です" }
        val inserted = tagDao.insertTag(TagEntity(name = trimmed))
        return if (inserted != -1L) inserted else requireNotNull(tagDao.tagIdByName(trimmed))
    }

    suspend fun tagFiles(tagId: Long, files: List<NasFile>) {
        tagDao.addMediaTags(
            files.map { MediaTagEntity(tagId, it.path, it.size, it.mtime) },
        )
    }

    suspend fun untagPaths(tagId: Long, paths: List<String>) {
        tagDao.removeMediaTags(tagId, paths)
    }

    suspend fun tagStateFor(paths: List<String>): Map<Long, Int> =
        tagsForPathsChunked(paths).groupingBy { it.tagId }.eachCount()

    suspend fun pathsWithAnyTag(paths: List<String>, tagIds: Set<Long>): Set<String> =
        tagsForPathsChunked(paths)
            .filter { it.tagId in tagIds }
            .map { it.path }
            .toSet()

    private suspend fun tagsForPathsChunked(paths: List<String>): List<MediaTagEntity> =
        paths.chunked(SQL_CHUNK).flatMap { tagDao.tagsForPaths(it) }

    suspend fun mediaForTag(tagId: Long): List<NasFile> =
        tagDao.mediaForTag(tagId).map { row ->
            NasFile(
                path = row.path,
                name = row.path.substringAfterLast('/'),
                size = row.size,
                mtime = row.mtime,
                isDirectory = false,
            )
        }

    suspend fun renamePath(oldPath: String, newPath: String) {
        mediaMetaDao.renamePath(oldPath, newPath)
        tagDao.renamePath(oldPath, newPath)
    }

    suspend fun deletePathData(path: String) {
        mediaMetaDao.deletePath(path)
        tagDao.deletePath(path)
    }

    suspend fun deleteFolderData(folderPath: String) {
        val prefix = escapeLike(folderPath.trimEnd('/')) + "/"
        mediaMetaDao.deleteByPrefix(prefix)
        tagDao.deleteByPrefix(prefix)
    }

    suspend fun deletePathsData(paths: List<String>) {
        paths.chunked(SQL_CHUNK).forEach {
            mediaMetaDao.deletePaths(it)
            tagDao.deletePaths(it)
        }
    }

    companion object {
        private const val MAX_VIEW_MILLIS = 300_000L
        private const val SQL_CHUNK = 900

        internal fun escapeLike(value: String): String =
            value.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_")
    }
}
