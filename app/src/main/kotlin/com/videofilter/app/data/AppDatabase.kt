package com.videofilter.app.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [VideoEntity::class, WhitelistEntity::class, ScanRecordEntity::class],
    version = 3,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {

    abstract fun videoDao(): VideoDao

    abstract fun whitelistDao(): WhitelistDao

    abstract fun scanRecordDao(): ScanRecordDao

    companion object {
        private const val DB_NAME = "video_filter.db"

        /**
         * v1 -> v2：新增白名单表。
         * 白名单是**用户资产**，必须显式迁移，绝不能被破坏性迁移清空。
         */
        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `whitelist` (
                        `path` TEXT NOT NULL,
                        `name` TEXT NOT NULL,
                        `sizeBytes` INTEGER NOT NULL,
                        `lastModified` INTEGER NOT NULL,
                        `durationMs` INTEGER NOT NULL,
                        `bitrateKbps` INTEGER NOT NULL,
                        `width` INTEGER NOT NULL,
                        `height` INTEGER NOT NULL,
                        `containerFormat` TEXT NOT NULL,
                        `codec` TEXT NOT NULL,
                        `rawMimeType` TEXT,
                        `addedAt` INTEGER NOT NULL,
                        PRIMARY KEY(`path`)
                    )
                    """.trimIndent()
                )
            }
        }

        /** v2 -> v3：新增每目录扫描时间表（同样是需要保留的用户侧信息） */
        private val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `scan_records` (
                        `folder` TEXT NOT NULL,
                        `lastScannedAt` INTEGER NOT NULL,
                        PRIMARY KEY(`folder`)
                    )
                    """.trimIndent()
                )
            }
        }

        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun get(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    DB_NAME
                )
                    .addMigrations(MIGRATION_1_2, MIGRATION_2_3)
                    // 兜底：万一将来出现未编写迁移的版本跃迁，宁可重建派生数据也不要崩溃。
                    // 注意：此兜底会清空白名单与扫描时间，因此每次改结构都必须补 Migration。
                    .fallbackToDestructiveMigration(dropAllTables = true)
                    .build()
                    .also { INSTANCE = it }
            }
        }
    }
}
