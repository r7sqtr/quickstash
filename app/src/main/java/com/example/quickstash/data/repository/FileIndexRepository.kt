package com.example.quickstash.data.repository

import com.example.quickstash.data.local.FileIndexDao
import com.example.quickstash.data.local.FileIndexEntity
import com.example.quickstash.data.nas.NasFile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class FileIndexRepository(private val fileIndexDao: FileIndexDao) {

    suspend fun indexFolder(media: List<NasFile>) = withContext(Dispatchers.IO) {
        if (media.isEmpty()) return@withContext
        val byFolder = media.groupBy { it.path.substringBeforeLast('/', "") }
        byFolder.forEach { (folder, files) ->
            fileIndexDao.replaceFolder(
                folder,
                files.map {
                    FileIndexEntity(
                        path = it.path,
                        folderPath = folder,
                        name = it.name,
                        size = it.size,
                        mtime = it.mtime,
                    )
                },
            )
        }
    }

    suspend fun search(query: String): List<NasFile> = withContext(Dispatchers.IO) {
        val trimmed = query.trim()
        if (trimmed.isEmpty()) return@withContext emptyList()
        val escaped = trimmed
            .replace("\\", "\\\\")
            .replace("%", "\\%")
            .replace("_", "\\_")
        fileIndexDao.search("%$escaped%").map {
            NasFile(
                path = it.path,
                name = it.name,
                size = it.size,
                mtime = it.mtime,
                isDirectory = false,
            )
        }
    }

    suspend fun deletePath(path: String) {
        fileIndexDao.deletePath(path)
    }

    suspend fun deletePaths(paths: List<String>) {
        paths.chunked(900).forEach { fileIndexDao.deletePaths(it) }
    }

    suspend fun deleteFolder(folderPath: String) {
        val prefix = LibraryRepository.escapeLike(folderPath.trimEnd('/')) + "/"
        fileIndexDao.deleteByPrefix(prefix)
    }
}
