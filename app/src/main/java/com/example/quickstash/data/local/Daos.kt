package com.example.quickstash.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction

@Dao
interface ImageHashDao {
    @Query("SELECT * FROM image_hash WHERE path IN (:paths)")
    suspend fun getByPaths(paths: List<String>): List<ImageHashEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: ImageHashEntity)

    @Query("DELETE FROM image_hash WHERE path = :path")
    suspend fun delete(path: String)

    @Query("DELETE FROM image_hash WHERE path IN (:paths)")
    suspend fun deletePaths(paths: List<String>)

    @Query("DELETE FROM image_hash WHERE path LIKE :prefix || '%' ESCAPE '\\'")
    suspend fun deleteByPrefix(prefix: String)
}

@Dao
interface BackupDao {
    @Query("SELECT * FROM media_meta")
    suspend fun dumpMediaMeta(): List<MediaMetaEntity>

    @Query("SELECT * FROM media_order")
    suspend fun dumpMediaOrder(): List<MediaOrderEntity>

    @Query("DELETE FROM media_meta")
    suspend fun clearMediaMeta()

    @Query("DELETE FROM media_order")
    suspend fun clearMediaOrder()

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun restoreMediaMeta(entries: List<MediaMetaEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun restoreMediaOrder(entries: List<MediaOrderEntity>)
}

@Dao
interface MediaOrderDao {
    @Query("SELECT * FROM media_order WHERE folderPath = :folderPath ORDER BY orderIndex")
    suspend fun getForFolder(folderPath: String): List<MediaOrderEntity>

    @Transaction
    suspend fun replaceFolder(folderPath: String, entries: List<MediaOrderEntity>) {
        clearFolder(folderPath)
        insertAll(entries)
    }

    @Query("DELETE FROM media_order WHERE folderPath = :folderPath")
    suspend fun clearFolder(folderPath: String)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(entries: List<MediaOrderEntity>)

    @Query("UPDATE media_order SET path = :newPath WHERE path = :oldPath")
    suspend fun renamePath(oldPath: String, newPath: String)

    @Query("DELETE FROM media_order WHERE path = :path")
    suspend fun deletePath(path: String)

    @Query("DELETE FROM media_order WHERE path IN (:paths)")
    suspend fun deletePaths(paths: List<String>)

    @Query("DELETE FROM media_order WHERE path LIKE :prefix || '%' ESCAPE '\\'")
    suspend fun deleteByPrefix(prefix: String)
}

@Dao
interface MediaMetaDao {
    @Query("SELECT * FROM media_meta WHERE path = :path")
    suspend fun get(path: String): MediaMetaEntity?

    @Query("SELECT * FROM media_meta WHERE path IN (:paths)")
    suspend fun getByPaths(paths: List<String>): List<MediaMetaEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: MediaMetaEntity)

    @Transaction
    suspend fun recordView(path: String, millis: Long) {
        val current = get(path)
        if (current == null) {
            upsert(MediaMetaEntity(path = path, viewCount = 1, viewMillis = millis))
        } else {
            upsert(
                current.copy(
                    viewCount = current.viewCount + 1,
                    viewMillis = current.viewMillis + millis,
                ),
            )
        }
    }

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertAllIgnore(entities: List<MediaMetaEntity>)

    @Query("UPDATE media_meta SET manualTier = :tier WHERE path IN (:paths)")
    suspend fun updateManualTier(paths: List<String>, tier: String?)

    @Transaction
    suspend fun setManualTier(paths: List<String>, tier: String?) {
        insertAllIgnore(paths.map { MediaMetaEntity(path = it) })
        updateManualTier(paths, tier)
    }

    @Query("UPDATE media_meta SET path = :newPath WHERE path = :oldPath")
    suspend fun renamePath(oldPath: String, newPath: String)

    @Query("DELETE FROM media_meta WHERE path = :path")
    suspend fun deletePath(path: String)

    @Query("DELETE FROM media_meta WHERE path IN (:paths)")
    suspend fun deletePaths(paths: List<String>)

    @Query("DELETE FROM media_meta WHERE path LIKE :prefix || '%' ESCAPE '\\'")
    suspend fun deleteByPrefix(prefix: String)
}

data class TagWithCount(
    val id: Long,
    val name: String,
    val count: Int,
)

@Dao
interface TagDao {
    @Query("SELECT * FROM tag ORDER BY name")
    suspend fun allTags(): List<TagEntity>

    @Query(
        "SELECT t.id AS id, t.name AS name, COUNT(mt.path) AS count " +
            "FROM tag t LEFT JOIN media_tag mt ON mt.tagId = t.id " +
            "GROUP BY t.id, t.name ORDER BY t.name",
    )
    suspend fun tagsWithCount(): List<TagWithCount>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertTag(tag: TagEntity): Long

    @Query("SELECT id FROM tag WHERE name = :name")
    suspend fun tagIdByName(name: String): Long?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun addMediaTags(entries: List<MediaTagEntity>)

    @Query("DELETE FROM media_tag WHERE tagId = :tagId AND path IN (:paths)")
    suspend fun removeMediaTags(tagId: Long, paths: List<String>)

    @Query("SELECT * FROM media_tag WHERE tagId = :tagId ORDER BY path")
    suspend fun mediaForTag(tagId: Long): List<MediaTagEntity>

    @Query("SELECT * FROM media_tag WHERE path IN (:paths)")
    suspend fun tagsForPaths(paths: List<String>): List<MediaTagEntity>

    @Query("UPDATE media_tag SET path = :newPath WHERE path = :oldPath")
    suspend fun renamePath(oldPath: String, newPath: String)

    @Query("DELETE FROM media_tag WHERE path = :path")
    suspend fun deletePath(path: String)

    @Query("DELETE FROM media_tag WHERE path IN (:paths)")
    suspend fun deletePaths(paths: List<String>)

    @Query("DELETE FROM media_tag WHERE path LIKE :prefix || '%' ESCAPE '\\'")
    suspend fun deleteByPrefix(prefix: String)

    @Query("SELECT * FROM tag")
    suspend fun dumpTags(): List<TagEntity>

    @Query("SELECT * FROM media_tag")
    suspend fun dumpMediaTags(): List<MediaTagEntity>

    @Query("DELETE FROM tag")
    suspend fun clearTags()

    @Query("DELETE FROM media_tag")
    suspend fun clearMediaTags()

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun restoreTags(tags: List<TagEntity>)
}

@Dao
interface FileIndexDao {
    @Transaction
    suspend fun replaceFolder(folderPath: String, entries: List<FileIndexEntity>) {
        clearFolder(folderPath)
        insertAll(entries)
    }

    @Query("DELETE FROM file_index WHERE folderPath = :folderPath")
    suspend fun clearFolder(folderPath: String)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(entries: List<FileIndexEntity>)

    @Query(
        "SELECT * FROM file_index WHERE name LIKE :pattern ESCAPE '\\' ORDER BY name LIMIT 500",
    )
    suspend fun search(pattern: String): List<FileIndexEntity>

    @Query("DELETE FROM file_index WHERE path = :path")
    suspend fun deletePath(path: String)

    @Query("DELETE FROM file_index WHERE path IN (:paths)")
    suspend fun deletePaths(paths: List<String>)

    @Query("DELETE FROM file_index WHERE path LIKE :prefix || '%' ESCAPE '\\'")
    suspend fun deleteByPrefix(prefix: String)
}
