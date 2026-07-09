package com.example.quickstash.media.coil

import com.example.quickstash.data.nas.NasFile

data class NasPdfThumb(
    val path: String,
    val size: Long,
    val mtime: Long,
) {
    companion object {
        fun from(file: NasFile) = NasPdfThumb(file.path, file.size, file.mtime)
    }
}
