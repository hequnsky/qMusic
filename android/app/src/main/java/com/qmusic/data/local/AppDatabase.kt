package com.qmusic.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.qmusic.data.local.entity.Favorite
import com.qmusic.data.local.entity.History

@Database(
    entities = [Favorite::class, History::class],
    version = 2,
    exportSchema = false,
)
abstract class AppDatabase : RoomDatabase() {

    abstract fun favoriteDao(): FavoriteDao

    abstract fun historyDao(): HistoryDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        /**
         * v1 -> v2：收藏 / 历史新增 source、token 两列。
         * 目的：从「我喜欢的」「最近播放」重新播放多音源歌曲时，
         * 仍然能拿到音源令牌去解析播放地址（否则只能回落到网易云）。
         */
        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE favorites ADD COLUMN source TEXT NOT NULL DEFAULT 'netease'")
                db.execSQL("ALTER TABLE favorites ADD COLUMN token TEXT")
                db.execSQL("ALTER TABLE history ADD COLUMN source TEXT NOT NULL DEFAULT 'netease'")
                db.execSQL("ALTER TABLE history ADD COLUMN token TEXT")
            }
        }

        fun get(context: Context): AppDatabase =
            INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "qmusic.db",
                ).addMigrations(MIGRATION_1_2).build().also { INSTANCE = it }
            }
    }
}