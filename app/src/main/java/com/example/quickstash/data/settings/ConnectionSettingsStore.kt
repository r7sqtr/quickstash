package com.example.quickstash.data.settings

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.example.quickstash.domain.SortSpec
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.dataStore by preferencesDataStore(name = "connection_settings")

class ConnectionSettingsStore(private val context: Context) {

    private object Keys {
        val HOST = stringPreferencesKey("host")
        val SECONDARY_HOST = stringPreferencesKey("secondary_host")
        val PORT = intPreferencesKey("port")
        val SHARE = stringPreferencesKey("share")
        val USERNAME = stringPreferencesKey("username")
        val PASSWORD = stringPreferencesKey("password_encrypted")
        val ROOT_PATH = stringPreferencesKey("root_path")
        val SKIP_SECONDS = intPreferencesKey("skip_seconds")
        val SLIDESHOW_SECONDS = intPreferencesKey("slideshow_seconds")
        val APP_LOCK = booleanPreferencesKey("app_lock_enabled")
        val GRID_COLUMNS = intPreferencesKey("grid_columns")
        val SHOW_TIER_BADGES = booleanPreferencesKey("show_tier_badges")
        val LAST_AUTO_BACKUP = longPreferencesKey("last_auto_backup")
    }

    suspend fun lastAutoBackupMillis(): Long =
        context.dataStore.data.first()[Keys.LAST_AUTO_BACKUP] ?: 0L

    suspend fun setLastAutoBackupMillis(millis: Long) {
        context.dataStore.edit { it[Keys.LAST_AUTO_BACKUP] = millis }
    }

    suspend fun showTierBadges(): Boolean =
        context.dataStore.data.first()[Keys.SHOW_TIER_BADGES] ?: true

    suspend fun setShowTierBadges(show: Boolean) {
        context.dataStore.edit { it[Keys.SHOW_TIER_BADGES] = show }
    }

    suspend fun gridColumns(): Int =
        context.dataStore.data.first()[Keys.GRID_COLUMNS] ?: DEFAULT_GRID_COLUMNS

    suspend fun saveGridColumns(columns: Int) {
        context.dataStore.edit { it[Keys.GRID_COLUMNS] = columns }
    }

    suspend fun appLockEnabled(): Boolean =
        context.dataStore.data.first()[Keys.APP_LOCK] ?: false

    suspend fun setAppLockEnabled(enabled: Boolean) {
        context.dataStore.edit { it[Keys.APP_LOCK] = enabled }
    }

    suspend fun allSortSettings(): Map<String, String> {
        val prefs = context.dataStore.data.first()
        return prefs.asMap().entries
            .filter { it.key.name.startsWith("sort:") }
            .associate { it.key.name.removePrefix("sort:") to it.value.toString() }
    }

    suspend fun restoreSortSettings(settings: Map<String, String>) {
        context.dataStore.edit { prefs ->
            settings.forEach { (path, value) ->
                prefs[stringPreferencesKey("sort:$path")] = value
            }
        }
    }

    val settings: Flow<ConnectionSettings> = context.dataStore.data.map { prefs ->
        ConnectionSettings(
            host = prefs[Keys.HOST] ?: "",
            secondaryHost = prefs[Keys.SECONDARY_HOST] ?: "",
            port = prefs[Keys.PORT] ?: 445,
            share = prefs[Keys.SHARE] ?: "",
            username = prefs[Keys.USERNAME] ?: "",
            password = CredentialCipher.decrypt(prefs[Keys.PASSWORD] ?: ""),
            rootPath = prefs[Keys.ROOT_PATH] ?: "",
            skipSeconds = prefs[Keys.SKIP_SECONDS] ?: 10,
            slideshowSeconds = prefs[Keys.SLIDESHOW_SECONDS] ?: 5,
        )
    }

    suspend fun current(): ConnectionSettings = settings.first()

    suspend fun sortSpec(path: String): SortSpec {
        val prefs = context.dataStore.data.first()
        return SortSpec.parse(prefs[sortKey(path)])
    }

    suspend fun saveSortSpec(path: String, spec: SortSpec) {
        context.dataStore.edit { prefs ->
            if (spec == SortSpec.DEFAULT || spec.entries.isEmpty()) {
                prefs.remove(sortKey(path))
            } else {
                prefs[sortKey(path)] = spec.serialize()
            }
        }
    }

    private fun sortKey(path: String) = stringPreferencesKey("sort:$path")

    companion object {
        const val DEFAULT_GRID_COLUMNS = 3
        const val MIN_GRID_COLUMNS = 2
        const val MAX_GRID_COLUMNS = 6
    }

    suspend fun save(settings: ConnectionSettings) {
        context.dataStore.edit { prefs ->
            prefs[Keys.HOST] = settings.host.trim()
            prefs[Keys.SECONDARY_HOST] = settings.secondaryHost.trim()
            prefs[Keys.PORT] = settings.port
            prefs[Keys.SHARE] = settings.share.trim()
            prefs[Keys.USERNAME] = settings.username.trim()
            prefs[Keys.PASSWORD] = CredentialCipher.encrypt(settings.password)
            prefs[Keys.ROOT_PATH] = settings.rootPath.trim()
            prefs[Keys.SKIP_SECONDS] = settings.skipSeconds
            prefs[Keys.SLIDESHOW_SECONDS] = settings.slideshowSeconds
        }
    }
}
