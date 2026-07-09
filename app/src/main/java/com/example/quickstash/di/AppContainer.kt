package com.example.quickstash.di

import android.content.Context
import com.example.quickstash.data.local.AppDatabase
import com.example.quickstash.data.nas.NasClient
import com.example.quickstash.data.nas.SmbConnectionManager
import com.example.quickstash.data.nas.SmbNasClient
import com.example.quickstash.data.repository.BackupRepository
import com.example.quickstash.data.repository.FileIndexRepository
import com.example.quickstash.data.repository.FileRepository
import com.example.quickstash.data.repository.LibraryRepository
import com.example.quickstash.data.repository.MediaOrganizeRepository
import com.example.quickstash.data.repository.MediaSequenceHolder
import com.example.quickstash.data.settings.ConnectionSettingsStore
import com.example.quickstash.media.coil.NasImageCache
import com.example.quickstash.media.video.VideoThumbnailer

class AppContainer(context: Context) {
    val settingsStore = ConnectionSettingsStore(context)
    val connectionManager = SmbConnectionManager(settingsStore)
    val nasClient: NasClient = SmbNasClient(connectionManager)
    val fileRepository = FileRepository(nasClient, settingsStore)
    private val videoThumbnailer = VideoThumbnailer(connectionManager)
    val imageCache = NasImageCache(context, nasClient, videoThumbnailer)
    private val database = AppDatabase.create(context)
    val mediaOrganizeRepository = MediaOrganizeRepository(
        database.imageHashDao(),
        database.mediaOrderDao(),
        imageCache,
    )
    val libraryRepository = LibraryRepository(
        database.mediaMetaDao(),
        database.tagDao(),
    )
    val fileIndexRepository = FileIndexRepository(database.fileIndexDao())
    val backupRepository = BackupRepository(
        nasClient,
        settingsStore,
        database.backupDao(),
        database.tagDao(),
    )
    val mediaSequenceHolder = MediaSequenceHolder()
}
