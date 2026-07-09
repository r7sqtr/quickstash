package com.example.quickstash.media.coil

import coil3.ImageLoader
import coil3.decode.DataSource
import coil3.decode.ImageSource
import coil3.fetch.FetchResult
import coil3.fetch.Fetcher
import coil3.fetch.SourceFetchResult
import coil3.key.Keyer
import coil3.request.Options
import java.io.File
import okio.FileSystem
import okio.Path.Companion.toOkioPath

private fun fileResult(file: File, mimeType: String?): FetchResult =
    SourceFetchResult(
        source = ImageSource(
            file = file.toOkioPath(),
            fileSystem = FileSystem.SYSTEM,
        ),
        mimeType = mimeType,
        dataSource = DataSource.DISK,
    )

class SmbThumbFetcher(
    private val model: NasThumb,
    private val imageCache: NasImageCache,
) : Fetcher {

    override suspend fun fetch(): FetchResult =
        fileResult(imageCache.getThumbnail(model), "image/jpeg")

    class Factory(private val imageCache: NasImageCache) : Fetcher.Factory<NasThumb> {
        override fun create(data: NasThumb, options: Options, imageLoader: ImageLoader): Fetcher =
            SmbThumbFetcher(data, imageCache)
    }
}

class SmbFullFetcher(
    private val model: NasFull,
    private val imageCache: NasImageCache,
) : Fetcher {

    override suspend fun fetch(): FetchResult =
        fileResult(imageCache.getOriginal(model), null)

    class Factory(private val imageCache: NasImageCache) : Fetcher.Factory<NasFull> {
        override fun create(data: NasFull, options: Options, imageLoader: ImageLoader): Fetcher =
            SmbFullFetcher(data, imageCache)
    }
}

class SmbVideoThumbFetcher(
    private val model: NasVideoThumb,
    private val imageCache: NasImageCache,
) : Fetcher {

    override suspend fun fetch(): FetchResult =
        fileResult(imageCache.getVideoThumbnail(model), "image/jpeg")

    class Factory(private val imageCache: NasImageCache) : Fetcher.Factory<NasVideoThumb> {
        override fun create(data: NasVideoThumb, options: Options, imageLoader: ImageLoader): Fetcher =
            SmbVideoThumbFetcher(data, imageCache)
    }
}

class SmbPdfThumbFetcher(
    private val model: NasPdfThumb,
    private val imageCache: NasImageCache,
) : Fetcher {

    override suspend fun fetch(): FetchResult =
        fileResult(imageCache.getPdfThumbnail(model), "image/jpeg")

    class Factory(private val imageCache: NasImageCache) : Fetcher.Factory<NasPdfThumb> {
        override fun create(data: NasPdfThumb, options: Options, imageLoader: ImageLoader): Fetcher =
            SmbPdfThumbFetcher(data, imageCache)
    }
}

class SmbThumbKeyer : Keyer<NasThumb> {
    override fun key(data: NasThumb, options: Options): String = key(data)

    companion object {
        fun key(data: NasThumb): String = "smb:${data.path}:${data.size}:${data.mtime}"
    }
}

class SmbFullKeyer : Keyer<NasFull> {
    override fun key(data: NasFull, options: Options): String =
        "smbfull:${data.path}:${data.size}:${data.mtime}"
}

class SmbVideoThumbKeyer : Keyer<NasVideoThumb> {
    override fun key(data: NasVideoThumb, options: Options): String =
        "smbvideo:${data.path}:${data.size}:${data.mtime}"
}

class SmbPdfThumbKeyer : Keyer<NasPdfThumb> {
    override fun key(data: NasPdfThumb, options: Options): String =
        "smbpdf:${data.path}:${data.size}:${data.mtime}"
}
