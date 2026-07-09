package com.example.quickstash.data.nas

import android.util.Log
import com.example.quickstash.data.settings.ConnectionSettings
import com.hierynomus.msdtyp.AccessMask
import com.hierynomus.msfscc.FileAttributes
import com.hierynomus.mssmb2.SMB2CreateDisposition
import com.hierynomus.mssmb2.SMB2ShareAccess
import com.hierynomus.protocol.commons.EnumWithValue
import com.hierynomus.smbj.share.DiskShare
import java.io.IOException
import java.io.InputStream
import java.util.EnumSet
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class SmbNasClient(private val connectionManager: SmbConnectionManager) : NasClient {

    override suspend fun listFiles(path: String): List<NasFile> {
        val started = System.currentTimeMillis()
        val entries = connectionManager.withShare { share ->
            share.list(toSmbPath(path)).mapNotNull { info ->
                val name = info.fileName
                if (name == "." || name == "..") return@mapNotNull null
                NasFile(
                    path = joinPath(path, name),
                    name = name,
                    size = info.endOfFile,
                    mtime = info.lastWriteTime.toEpochMillis(),
                    ctime = info.creationTime.toEpochMillis(),
                    isDirectory = EnumWithValue.EnumUtils.isSet(
                        info.fileAttributes,
                        FileAttributes.FILE_ATTRIBUTE_DIRECTORY,
                    ),
                )
            }
        }
        Log.d(TAG, "list '$path': ${entries.size} entries in ${System.currentTimeMillis() - started}ms")
        return entries
    }

    override suspend fun <T> readFile(path: String, block: (InputStream) -> T): T {
        val started = System.currentTimeMillis()
        val result = connectionManager.withShare { share ->
            openForRead(share, path).use { file ->
                file.inputStream.use(block)
            }
        }
        Log.d(TAG, "read '$path' in ${System.currentTimeMillis() - started}ms")
        return result
    }

    override suspend fun writeFile(path: String, data: ByteArray) {
        connectionManager.withShare { share ->
            share.openFile(
                toSmbPath(path),
                EnumSet.of(AccessMask.GENERIC_WRITE),
                null,
                SMB2ShareAccess.ALL,
                SMB2CreateDisposition.FILE_OVERWRITE_IF,
                null,
            ).use { file ->
                file.outputStream.use { it.write(data) }
            }
        }
        Log.d(TAG, "write '$path' (${data.size} bytes)")
    }

    override suspend fun uploadFile(destDir: String, name: String, input: InputStream): String {
        val started = System.currentTimeMillis()
        val cleanDir = destDir.trim('/')
        val destPath = connectionManager.withShare { share ->
            val target = resolveNonColliding(share, cleanDir, name)
            share.openFile(
                toSmbPath(target),
                EnumSet.of(AccessMask.GENERIC_WRITE),
                null,
                SMB2ShareAccess.ALL,
                SMB2CreateDisposition.FILE_CREATE,
                null,
            ).use { file ->
                file.outputStream.use { output ->
                    val buffer = ByteArray(UPLOAD_BUFFER_SIZE)
                    while (true) {
                        val read = input.read(buffer)
                        if (read < 0) break
                        output.write(buffer, 0, read)
                    }
                }
            }
            target
        }
        Log.d(TAG, "upload '$destPath' in ${System.currentTimeMillis() - started}ms")
        return destPath
    }

    override suspend fun createDirectory(path: String) {
        connectionManager.withShare { share ->
            share.mkdir(toSmbPath(path))
        }
        Log.d(TAG, "mkdir '$path'")
    }

    override suspend fun deleteDirectory(path: String) {
        connectionManager.withShare { share ->
            share.rmdir(toSmbPath(path), true)
        }
        Log.d(TAG, "rmdir '$path'")
    }

    override suspend fun moveFile(path: String, destDir: String): String {
        val name = path.substringAfterLast('/')
        val cleanDir = destDir.trim('/')
        val sourceDir = path.substringBeforeLast('/', "")
        if (cleanDir == sourceDir) return path
        val destPath = connectionManager.withShare { share ->
            val target = resolveNonColliding(share, cleanDir, name)
            if (target == path) return@withShare target
            share.openFile(
                toSmbPath(path),
                EnumSet.of(AccessMask.DELETE),
                null,
                SMB2ShareAccess.ALL,
                SMB2CreateDisposition.FILE_OPEN,
                null,
            ).use { file ->
                file.rename(toSmbPath(target), false)
            }
            target
        }
        Log.d(TAG, "move '$path' -> '$destPath'")
        return destPath
    }

    override suspend fun renameFile(path: String, newName: String): String {
        val dir = path.substringBeforeLast('/', "")
        val currentName = path.substringAfterLast('/')
        if (currentName == newName) return path
        val destPath = connectionManager.withShare { share ->
            val target = resolveNonColliding(share, dir, newName)
            share.openFile(
                toSmbPath(path),
                EnumSet.of(AccessMask.DELETE),
                null,
                SMB2ShareAccess.ALL,
                SMB2CreateDisposition.FILE_OPEN,
                null,
            ).use { file ->
                file.rename(toSmbPath(target), false)
            }
            target
        }
        Log.d(TAG, "rename '$path' -> '$destPath'")
        return destPath
    }

    private fun resolveNonColliding(share: DiskShare, dir: String, name: String): String {
        fun join(fileName: String) = if (dir.isEmpty()) fileName else "$dir/$fileName"
        if (!share.fileExists(toSmbPath(join(name)))) return join(name)
        val base = name.substringBeforeLast('.', name)
        val ext = name.substringAfterLast('.', "")
        var index = 1
        while (true) {
            val candidate = if (ext.isEmpty()) "${base}_$index" else "${base}_$index.$ext"
            if (!share.fileExists(toSmbPath(join(candidate)))) return join(candidate)
            index++
        }
    }

    override suspend fun deleteFile(path: String) {
        connectionManager.withShare { share ->
            share.rm(toSmbPath(path))
        }
        Log.d(TAG, "delete '$path'")
    }

    override suspend fun testConnection(settings: ConnectionSettings): Result<Int> =
        withContext(Dispatchers.IO) {
            runCatching {
                val opened = SmbConnectionManager.openShare(settings)
                try {
                    opened.share.list(toSmbPath(settings.rootPath))
                        .count { it.fileName != "." && it.fileName != ".." }
                } finally {
                    runCatching { opened.client.close() }
                }
            }
        }

    private fun openForRead(share: DiskShare, path: String) =
        share.openFile(
            toSmbPath(path),
            EnumSet.of(AccessMask.GENERIC_READ),
            null,
            SMB2ShareAccess.ALL,
            SMB2CreateDisposition.FILE_OPEN,
            null,
        )

    private fun toSmbPath(path: String): String {
        val trimmed = path.trim('/')
        val segments = trimmed.split('/', '\\')
        if (segments.any { it == ".." }) {
            throw IOException("不正なパス: $path")
        }
        return trimmed.replace('/', '\\')
    }

    private fun joinPath(parent: String, name: String): String {
        val base = parent.trim('/')
        return if (base.isEmpty()) name else "$base/$name"
    }

    companion object {
        private const val TAG = "SmbNasClient"
        private const val UPLOAD_BUFFER_SIZE = 1024 * 1024
    }
}
