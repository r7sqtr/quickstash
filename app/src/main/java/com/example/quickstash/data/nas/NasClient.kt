package com.example.quickstash.data.nas

import com.example.quickstash.data.settings.ConnectionSettings
import java.io.InputStream

interface NasClient {
    suspend fun listFiles(path: String): List<NasFile>
    suspend fun <T> readFile(path: String, block: (InputStream) -> T): T
    suspend fun writeFile(path: String, data: ByteArray)
    suspend fun uploadFile(destDir: String, name: String, input: InputStream): String
    suspend fun createDirectory(path: String)
    suspend fun deleteDirectory(path: String)
    suspend fun renameFile(path: String, newName: String): String
    suspend fun moveFile(path: String, destDir: String): String
    suspend fun deleteFile(path: String)
    suspend fun testConnection(settings: ConnectionSettings): Result<Int>
}
