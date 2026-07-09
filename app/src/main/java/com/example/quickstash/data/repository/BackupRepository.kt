package com.example.quickstash.data.repository

import com.example.quickstash.data.local.BackupDao
import com.example.quickstash.data.local.MediaMetaEntity
import com.example.quickstash.data.local.MediaOrderEntity
import com.example.quickstash.data.local.MediaTagEntity
import com.example.quickstash.data.local.TagDao
import com.example.quickstash.data.local.TagEntity
import com.example.quickstash.data.nas.NasClient
import com.example.quickstash.data.settings.ConnectionSettingsStore
import java.io.ByteArrayOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

@Serializable
private data class TagDto(val id: Long, val name: String)

@Serializable
private data class MediaTagDto(val tagId: Long, val path: String, val size: Long, val mtime: Long)

@Serializable
private data class MediaMetaDto(
    val path: String,
    val viewCount: Int,
    val viewMillis: Long,
    val manualTier: String?,
)

@Serializable
private data class MediaOrderDto(
    val folderPath: String,
    val path: String,
    val orderIndex: Int,
    val groupId: Long?,
)

@Serializable
private data class BackupData(
    val version: Int = 1,
    val exportedAt: Long,
    val tags: List<TagDto>,
    val mediaTags: List<MediaTagDto>,
    val mediaMeta: List<MediaMetaDto>,
    val mediaOrder: List<MediaOrderDto>,
    val sortSettings: Map<String, String>,
)

class BackupRepository(
    private val nasClient: NasClient,
    private val settingsStore: ConnectionSettingsStore,
    private val backupDao: BackupDao,
    private val tagDao: TagDao,
) {

    private val json = Json {
        ignoreUnknownKeys = true
        prettyPrint = false
    }

    private fun isSafePath(path: String): Boolean =
        path.isNotBlank() &&
            !path.startsWith("/") &&
            !path.startsWith("\\") &&
            !path.contains(':') &&
            path.split('/', '\\').none { it == ".." }

    private suspend fun rootDir(): String = settingsStore.current().rootPath.trim('/')

    private fun joinRoot(root: String, name: String): String =
        if (root.isEmpty()) name else "$root/$name"

    private suspend fun backupFiles(): List<String> {
        val root = rootDir()
        return nasClient.listFiles(root)
            .filter { !it.isDirectory && BACKUP_NAME_PATTERN.matches(it.name) }
            .map { it.name }
            .sortedDescending()
    }

    suspend fun export(): String = withContext(Dispatchers.IO) {
        val data = BackupData(
            exportedAt = System.currentTimeMillis(),
            tags = tagDao.dumpTags().map { TagDto(it.id, it.name) },
            mediaTags = tagDao.dumpMediaTags()
                .map { MediaTagDto(it.tagId, it.path, it.size, it.mtime) },
            mediaMeta = backupDao.dumpMediaMeta()
                .map { MediaMetaDto(it.path, it.viewCount, it.viewMillis, it.manualTier) },
            mediaOrder = backupDao.dumpMediaOrder()
                .map { MediaOrderDto(it.folderPath, it.path, it.orderIndex, it.groupId) },
            sortSettings = settingsStore.allSortSettings(),
        )
        val bytes = json.encodeToString(BackupData.serializer(), data)
            .toByteArray(Charsets.UTF_8)
        val root = rootDir()
        val stamp = SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US)
            .format(Date(data.exportedAt))
        val path = joinRoot(root, "$BACKUP_PREFIX$stamp.json")
        nasClient.writeFile(path, bytes)
        runCatching {
            backupFiles().drop(KEEP_GENERATIONS).forEach { old ->
                nasClient.deleteFile(joinRoot(root, old))
            }
        }
        "バックアップを保存しました（$path、タグ${data.tags.size}件・メタ${data.mediaMeta.size}件・並び順${data.mediaOrder.size}件）"
    }

    suspend fun restore(): String = withContext(Dispatchers.IO) {
        val root = rootDir()
        val newest = backupFiles().firstOrNull()
        val path = if (newest != null) {
            joinRoot(root, newest)
        } else {
            joinRoot(root, LEGACY_BACKUP_FILE_NAME)
        }
        val bytes = nasClient.readFile(path) { input ->
            val buffer = ByteArrayOutputStream()
            input.copyTo(buffer)
            buffer.toByteArray()
        }
        val data = json.decodeFromString(
            BackupData.serializer(),
            bytes.toString(Charsets.UTF_8),
        )
        val safeTags = data.mediaTags.filter { isSafePath(it.path) }
        val safeMeta = data.mediaMeta.filter { isSafePath(it.path) }
        val safeOrder = data.mediaOrder.filter { isSafePath(it.path) && isSafePath(it.folderPath) }
        tagDao.clearMediaTags()
        tagDao.clearTags()
        backupDao.clearMediaMeta()
        backupDao.clearMediaOrder()
        tagDao.restoreTags(data.tags.map { TagEntity(it.id, it.name) })
        tagDao.addMediaTags(
            safeTags.map { MediaTagEntity(it.tagId, it.path, it.size, it.mtime) },
        )
        backupDao.restoreMediaMeta(
            safeMeta.map { MediaMetaEntity(it.path, it.viewCount, it.viewMillis, it.manualTier) },
        )
        backupDao.restoreMediaOrder(
            safeOrder.map { MediaOrderEntity(it.folderPath, it.path, it.orderIndex, it.groupId) },
        )
        settingsStore.restoreSortSettings(data.sortSettings)
        "バックアップから復元しました（タグ${data.tags.size}件・メタ${data.mediaMeta.size}件・並び順${data.mediaOrder.size}件）"
    }

    companion object {
        private const val BACKUP_PREFIX = "quickstash-backup-"
        private const val LEGACY_BACKUP_FILE_NAME = "quickstash-backup.json"
        private const val KEEP_GENERATIONS = 5
        private val BACKUP_NAME_PATTERN =
            Regex("""quickstash-backup-\d{8}-\d{6}\.json""")
    }
}
