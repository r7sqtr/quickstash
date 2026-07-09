package com.example.quickstash.data.local

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(tableName = "image_hash", primaryKeys = ["path"])
data class ImageHashEntity(
    val path: String,
    val size: Long,
    val mtime: Long,
    val dhashH: Long,
    val dhashV: Long,
    val histogram: ByteArray,
)

@Entity(
    tableName = "media_order",
    primaryKeys = ["folderPath", "path"],
    indices = [Index(value = ["path"])],
)
data class MediaOrderEntity(
    val folderPath: String,
    val path: String,
    val orderIndex: Int,
    val groupId: Long?,
)

@Entity(tableName = "media_meta")
data class MediaMetaEntity(
    @PrimaryKey val path: String,
    val viewCount: Int = 0,
    val viewMillis: Long = 0,
    val manualTier: String? = null,
)

@Entity(tableName = "tag", indices = [Index(value = ["name"], unique = true)])
data class TagEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
)

@Entity(
    tableName = "media_tag",
    primaryKeys = ["tagId", "path"],
    indices = [Index(value = ["path"])],
)
data class MediaTagEntity(
    val tagId: Long,
    val path: String,
    val size: Long,
    val mtime: Long,
)

@Entity(tableName = "file_index", indices = [Index(value = ["folderPath"]), Index(value = ["name"])])
data class FileIndexEntity(
    @PrimaryKey val path: String,
    val folderPath: String,
    val name: String,
    val size: Long,
    val mtime: Long,
)
