package com.example.quickstash.media.coil

import com.example.quickstash.data.nas.NasFile

data class NasVideoThumb(
    val path: String,
    val size: Long,
    val mtime: Long,
) {
    companion object {
        fun from(file: NasFile) = NasVideoThumb(file.path, file.size, file.mtime)
    }
}
