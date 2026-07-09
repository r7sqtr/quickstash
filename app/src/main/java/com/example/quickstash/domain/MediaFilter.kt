package com.example.quickstash.domain

import com.example.quickstash.data.nas.MediaType

data class MediaFilter(
    val types: Set<MediaType> = emptySet(),
    val manualTiers: Set<String> = emptySet(),
    val autoTiers: Set<String> = emptySet(),
    val tagIds: Set<Long> = emptySet(),
) {
    val isEmpty: Boolean
        get() = types.isEmpty() && manualTiers.isEmpty() && autoTiers.isEmpty() &&
            tagIds.isEmpty()

    companion object {
        val EMPTY = MediaFilter()
    }
}
