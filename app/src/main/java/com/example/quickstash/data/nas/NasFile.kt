package com.example.quickstash.data.nas

data class NasFile(
    val path: String,
    val name: String,
    val size: Long,
    val mtime: Long,
    val ctime: Long = 0,
    val isDirectory: Boolean,
) {
    val extension: String
        get() = name.substringAfterLast('.', "").lowercase()

    val isImage: Boolean
        get() = extension in IMAGE_EXTENSIONS

    val isVideo: Boolean
        get() = extension in VIDEO_EXTENSIONS

    val isPdf: Boolean
        get() = extension == "pdf"

    val isMedia: Boolean
        get() = isImage || isVideo || isPdf

    val mediaType: MediaType
        get() = when {
            isImage -> MediaType.IMAGE
            isVideo -> MediaType.VIDEO
            isPdf -> MediaType.PDF
            else -> MediaType.OTHER
        }

    companion object {
        private val IMAGE_EXTENSIONS =
            setOf("jpg", "jpeg", "png", "gif", "webp", "bmp", "heic", "heif")
        private val VIDEO_EXTENSIONS =
            setOf("mp4", "m4v", "mkv", "mov", "webm", "avi", "3gp", "ts", "wmv")
    }
}

enum class MediaType { IMAGE, VIDEO, PDF, OTHER }
