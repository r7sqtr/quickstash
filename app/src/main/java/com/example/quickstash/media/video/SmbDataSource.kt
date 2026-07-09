package com.example.quickstash.media.video

import android.net.Uri
import androidx.annotation.OptIn
import androidx.media3.common.C
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.BaseDataSource
import androidx.media3.datasource.DataSpec
import com.hierynomus.msdtyp.AccessMask
import com.hierynomus.mssmb2.SMB2CreateDisposition
import com.hierynomus.mssmb2.SMB2ShareAccess
import com.hierynomus.smbj.share.DiskShare
import com.hierynomus.smbj.share.File
import java.io.IOException
import java.util.EnumSet

@OptIn(UnstableApi::class)
class SmbDataSource(private val share: DiskShare) : BaseDataSource(true) {

    private var file: File? = null
    private var openedUri: Uri? = null
    private var cachedFileUri: Uri? = null
    private var cachedFileSize = 0L
    private var smbPosition = 0L
    private var bytesRemaining = 0L
    private var transferInProgress = false

    private val readAhead = ByteArray(READ_AHEAD_SIZE)
    private var bufPos = 0
    private var bufLen = 0

    override fun open(dataSpec: DataSpec): Long {
        openedUri = dataSpec.uri
        transferInitializing(dataSpec)
        val fileSize = if (file != null && dataSpec.uri == cachedFileUri) {
            cachedFileSize
        } else {
            file?.let { runCatching { it.close() } }
            file = null
            cachedFileUri = null
            val path = dataSpec.uri.path?.trimStart('/')?.replace('/', '\\')
                ?: throw IOException("不正なURI: ${dataSpec.uri}")
            val opened = share.openFile(
                path,
                EnumSet.of(AccessMask.GENERIC_READ),
                null,
                SMB2ShareAccess.ALL,
                SMB2CreateDisposition.FILE_OPEN,
                null,
            )
            file = opened
            cachedFileUri = dataSpec.uri
            cachedFileSize = opened.fileInformation.standardInformation.endOfFile
            cachedFileSize
        }
        smbPosition = dataSpec.position
        bytesRemaining = if (dataSpec.length != C.LENGTH_UNSET.toLong()) {
            minOf(dataSpec.length, (fileSize - dataSpec.position).coerceAtLeast(0))
        } else {
            (fileSize - dataSpec.position).coerceAtLeast(0)
        }
        bufPos = 0
        bufLen = 0
        transferStarted(dataSpec)
        transferInProgress = true
        return bytesRemaining
    }

    override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
        if (length == 0) return 0
        if (bytesRemaining <= 0L) return C.RESULT_END_OF_INPUT
        if (bufPos >= bufLen) {
            val target = file ?: throw IOException("DataSource が開かれていません")
            val want = minOf(readAhead.size.toLong(), bytesRemaining).toInt()
            val read = try {
                target.read(readAhead, smbPosition, 0, want)
            } catch (e: IOException) {
                throw e
            } catch (e: Exception) {
                throw IOException("SMB読み取り失敗 @$smbPosition", e)
            }
            if (read <= 0) return C.RESULT_END_OF_INPUT
            smbPosition += read
            bufPos = 0
            bufLen = read
        }
        val serve = minOf(
            length.toLong(),
            (bufLen - bufPos).toLong(),
            bytesRemaining,
        ).toInt()
        System.arraycopy(readAhead, bufPos, buffer, offset, serve)
        bufPos += serve
        bytesRemaining -= serve
        bytesTransferred(serve)
        return serve
    }

    override fun getUri(): Uri? = openedUri

    override fun close() {
        openedUri = null
        bufPos = 0
        bufLen = 0
        if (transferInProgress) {
            transferInProgress = false
            transferEnded()
        }
    }

    companion object {
        private const val READ_AHEAD_SIZE = 1024 * 1024

        fun uriFor(path: String): Uri = Uri.parse("smb:///${Uri.encode(path, "/")}")
    }
}
