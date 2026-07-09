package com.example.quickstash

import android.app.Application
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import coil3.ImageLoader
import coil3.PlatformContext
import coil3.SingletonImageLoader
import coil3.gif.AnimatedImageDecoder
import com.example.quickstash.di.AppContainer
import com.example.quickstash.media.coil.SmbFullFetcher
import com.example.quickstash.media.coil.SmbFullKeyer
import com.example.quickstash.media.coil.SmbThumbFetcher
import com.example.quickstash.media.coil.SmbThumbKeyer
import com.example.quickstash.media.coil.SmbPdfThumbFetcher
import com.example.quickstash.media.coil.SmbPdfThumbKeyer
import com.example.quickstash.media.coil.SmbVideoThumbFetcher
import com.example.quickstash.media.coil.SmbVideoThumbKeyer

class QuickStashApp : Application(), SingletonImageLoader.Factory {

    private companion object {
        const val TAG = "QuickStashApp"
        const val AUTO_BACKUP_INTERVAL_MILLIS = 24L * 60 * 60 * 1000
    }

    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            runCatching { container.imageCache.trimToBudget() }
            runCatching { autoBackupIfDue() }
        }
    }

    private suspend fun autoBackupIfDue() {
        val settings = container.settingsStore.current()
        if (!settings.isConfigured) return
        val last = container.settingsStore.lastAutoBackupMillis()
        val now = System.currentTimeMillis()
        if (now - last < AUTO_BACKUP_INTERVAL_MILLIS) return
        runCatching { container.backupRepository.export() }
            .onSuccess {
                container.settingsStore.setLastAutoBackupMillis(now)
                Log.d(TAG, "auto backup done")
            }
            .onFailure { Log.d(TAG, "auto backup skipped: ${it.message}") }
    }

    override fun newImageLoader(context: PlatformContext): ImageLoader =
        ImageLoader.Builder(context)
            .components {
                add(SmbThumbKeyer())
                add(SmbFullKeyer())
                add(SmbVideoThumbKeyer())
                add(SmbPdfThumbKeyer())
                add(SmbThumbFetcher.Factory(container.imageCache))
                add(SmbFullFetcher.Factory(container.imageCache))
                add(SmbVideoThumbFetcher.Factory(container.imageCache))
                add(SmbPdfThumbFetcher.Factory(container.imageCache))
                add(AnimatedImageDecoder.Factory())
            }
            .build()
}
