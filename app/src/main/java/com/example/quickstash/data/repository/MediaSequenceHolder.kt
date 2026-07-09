package com.example.quickstash.data.repository

import com.example.quickstash.data.nas.NasFile

class MediaSequenceHolder {
    @Volatile
    var media: List<NasFile> = emptyList()
        private set

    fun set(media: List<NasFile>) {
        this.media = media
    }
}
