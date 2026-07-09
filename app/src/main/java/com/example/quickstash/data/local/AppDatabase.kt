package com.example.quickstash.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [
        ImageHashEntity::class,
        MediaOrderEntity::class,
        MediaMetaEntity::class,
        TagEntity::class,
        MediaTagEntity::class,
        FileIndexEntity::class,
    ],
    version = 7,
    exportSchema = false,
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun imageHashDao(): ImageHashDao
    abstract fun mediaOrderDao(): MediaOrderDao
    abstract fun mediaMetaDao(): MediaMetaDao
    abstract fun tagDao(): TagDao
    abstract fun fileIndexDao(): FileIndexDao
    abstract fun backupDao(): BackupDao

    companion object {
        private val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `media_meta` (" +
                        "`path` TEXT NOT NULL, " +
                        "`viewCount` INTEGER NOT NULL, " +
                        "`viewMillis` INTEGER NOT NULL, " +
                        "`manualTier` TEXT, " +
                        "PRIMARY KEY(`path`))",
                )
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `tag` (" +
                        "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                        "`name` TEXT NOT NULL)",
                )
                db.execSQL(
                    "CREATE UNIQUE INDEX IF NOT EXISTS `index_tag_name` ON `tag` (`name`)",
                )
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `media_tag` (" +
                        "`tagId` INTEGER NOT NULL, " +
                        "`path` TEXT NOT NULL, " +
                        "`size` INTEGER NOT NULL, " +
                        "`mtime` INTEGER NOT NULL, " +
                        "PRIMARY KEY(`tagId`, `path`))",
                )
            }
        }

        private val MIGRATION_5_6 = object : Migration(5, 6) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `file_index` (" +
                        "`path` TEXT NOT NULL, " +
                        "`folderPath` TEXT NOT NULL, " +
                        "`name` TEXT NOT NULL, " +
                        "`size` INTEGER NOT NULL, " +
                        "`mtime` INTEGER NOT NULL, " +
                        "PRIMARY KEY(`path`))",
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS `index_file_index_folderPath` " +
                        "ON `file_index` (`folderPath`)",
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS `index_file_index_name` " +
                        "ON `file_index` (`name`)",
                )
            }
        }

        private val MIGRATION_6_7 = object : Migration(6, 7) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS `index_media_order_path` " +
                        "ON `media_order` (`path`)",
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS `index_media_tag_path` " +
                        "ON `media_tag` (`path`)",
                )
            }
        }

        fun create(context: Context): AppDatabase =
            Room.databaseBuilder(context, AppDatabase::class.java, "nas_gallery.db")
                .addMigrations(MIGRATION_4_5, MIGRATION_5_6, MIGRATION_6_7)
                .fallbackToDestructiveMigration(dropAllTables = true)
                .build()
    }
}
