package com.example.quickstash.data.nas

import com.example.quickstash.data.settings.ConnectionSettings
import com.example.quickstash.data.settings.ConnectionSettingsStore
import com.hierynomus.smbj.SMBClient
import com.hierynomus.smbj.SmbConfig
import com.hierynomus.smbj.auth.AuthenticationContext
import com.hierynomus.smbj.connection.Connection
import com.hierynomus.smbj.session.Session
import com.hierynomus.smbj.share.DiskShare
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext

class SmbConnectionManager(private val settingsStore: ConnectionSettingsStore) {

    private val permits = Semaphore(POOL_SIZE)
    private val lock = Mutex()
    private val idle = ArrayDeque<SmbConnectionManager.Companion.OpenedShare>()

    @Volatile
    private var generation = 0

    @Volatile
    private var lastGoodHost: String? = null

    suspend fun <T> withShare(block: (DiskShare) -> T): T = withContext(Dispatchers.IO) {
        permits.withPermit {
            var opened = borrow()
            try {
                val result = block(opened.share)
                giveBack(opened)
                result
            } catch (e: Exception) {
                discard(opened)
                opened = borrow(forceFresh = true)
                try {
                    val result = block(opened.share)
                    giveBack(opened)
                    result
                } catch (e2: Exception) {
                    discard(opened)
                    throw e2
                }
            }
        }
    }

    private suspend fun borrow(forceFresh: Boolean = false): OpenedShare {
        if (!forceFresh) {
            while (true) {
                val candidate = lock.withLock { idle.removeFirstOrNull() } ?: break
                if (candidate.share.isConnected) return candidate
                closeQuietly(candidate)
            }
        }
        val settings = settingsStore.current()
        val secondary = settings.secondaryHost.takeIf { it.isNotBlank() }
        val hosts = buildList {
            if (lastGoodHost == secondary && secondary != null) {
                add(secondary)
                add(settings.host)
            } else {
                add(settings.host)
                secondary?.let { add(it) }
            }
        }.distinct()
        var lastError: Exception? = null
        for (host in hosts) {
            try {
                val opened = openShare(settings.copy(host = host))
                lastGoodHost = host
                return opened.also { it.generation = generation }
            } catch (e: Exception) {
                lastError = e
            }
        }
        throw lastError ?: IllegalStateException("接続先が設定されていません")
    }

    private suspend fun giveBack(opened: OpenedShare) {
        if (opened.generation != generation || !opened.share.isConnected) {
            closeQuietly(opened)
            return
        }
        lock.withLock { idle.addLast(opened) }
    }

    private fun discard(opened: OpenedShare) {
        closeQuietly(opened)
    }

    suspend fun reset() {
        lastGoodHost = null
        val toClose = lock.withLock {
            generation++
            val snapshot = idle.toList()
            idle.clear()
            snapshot
        }
        toClose.forEach(::closeQuietly)
    }

    companion object {
        private const val POOL_SIZE = 4

        class OpenedShare(
            val client: SMBClient,
            val connection: Connection,
            val session: Session,
            val share: DiskShare,
        ) {
            var generation = 0

            fun close() {
                runCatching { share.close() }
                runCatching { session.close() }
                runCatching { connection.close() }
                runCatching { client.close() }
            }
        }

        private fun closeQuietly(opened: OpenedShare) {
            opened.close()
        }

        fun openShare(settings: ConnectionSettings): OpenedShare {
            val config = SmbConfig.builder()
                .withTimeout(10, TimeUnit.SECONDS)
                .withSoTimeout(30, TimeUnit.SECONDS)
                .withReadBufferSize(1024 * 1024)
                .build()
            val client = SMBClient(config)
            try {
                val connection = client.connect(settings.host, settings.port)
                val auth = when {
                    settings.username.isBlank() -> AuthenticationContext.anonymous()
                    settings.password.isEmpty() -> throw IllegalStateException(
                        "認証情報を復号できませんでした。設定を開き直して保存してください",
                    )
                    else ->
                        AuthenticationContext(settings.username, settings.password.toCharArray(), null)
                }
                val session = connection.authenticate(auth)
                val share = session.connectShare(settings.share) as? DiskShare
                    ?: throw IllegalStateException("共有 ${settings.share} はファイル共有ではありません")
                return OpenedShare(client, connection, session, share)
            } catch (e: Exception) {
                runCatching { client.close() }
                throw e
            }
        }
    }
}
