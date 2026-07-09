package com.example.quickstash.data.repository

import com.example.quickstash.data.nas.NasClient
import com.example.quickstash.data.nas.NasFile
import com.example.quickstash.data.settings.ConnectionSettingsStore
import com.example.quickstash.domain.NaturalOrderComparator
import com.example.quickstash.domain.SortSpec

class FileRepository(
    private val nasClient: NasClient,
    private val settingsStore: ConnectionSettingsStore,
) {

    data class Listing(
        val directories: List<NasFile>,
        val media: List<NasFile>,
    ) {
        val images: List<NasFile> get() = media.filter { it.isImage }
        val videos: List<NasFile> get() = media.filter { it.isVideo }

        fun withSortedImages(): List<NasFile> =
            images.sortedWith(compareBy(NaturalOrderComparator) { it.name })
    }

    suspend fun resolvePath(path: String): String =
        path.ifEmpty { settingsStore.current().rootPath }

    suspend fun list(path: String): Listing {
        val fullPath = resolvePath(path)
        val entries = nasClient.listFiles(fullPath)
        return Listing(
            directories = entries.filter { it.isDirectory && !it.name.startsWith(".") },
            media = entries.filter { !it.isDirectory && it.isMedia },
        )
    }

    suspend fun sortSpec(path: String): SortSpec = settingsStore.sortSpec(path)

    suspend fun saveSortSpec(path: String, spec: SortSpec) =
        settingsStore.saveSortSpec(path, spec)
}
