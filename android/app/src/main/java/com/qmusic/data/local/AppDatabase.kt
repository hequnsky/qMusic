package com.qmusic.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.qmusic.data.local.entity.Favorite
import com.qmusic.data.local.entity.History
import com.qmusic.data.local.entity.LocalPlaylist
import com.qmusic.data.local.entity.PlaylistSong

@Database(
    entities = [Favorite::class, History::class, LocalPlaylist::class, PlaylistSong::class],
    version = 3,
    exportSchema = false,
)
abstract class AppDatabase : RoomDatabase() {

    abstract fun favoriteDao(): FavoriteDao

    abstract fun historyDao(): HistoryDao

    abstract fun playlistDao(): PlaylistDao

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

        /**
         * v2 -> v3：新增本地歌单（playlists）与歌单歌曲关联（playlist_songs）两张表。
         * 关联表用复合主键 (playlistId, songId) 保证同一首歌不重复入单；
         * 外键 ON DELETE CASCADE 保证删歌单时自动清理关联行。
         */
        private val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `playlists` (" +
                        "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                        "`name` TEXT NOT NULL, " +
                        "`coverUrl` TEXT, " +
                        "`createdAt` INTEGER NOT NULL)",
                )
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `playlist_songs` (" +
                        "`playlistId` INTEGER NOT NULL, " +
                        "`songId` INTEGER NOT NULL, " +
                        "`name` TEXT NOT NULL, " +
                        "`artist` TEXT NOT NULL, " +
                        "`album` TEXT NOT NULL, " +
                        "`picUrl` TEXT, " +
                        "`durationMs` INTEGER NOT NULL, " +
                        "`source` TEXT NOT NULL, " +
                        "`token` TEXT, " +
                        "`addedAt` INTEGER NOT NULL, " +
                        "PRIMARY KEY(`playlistId`, `songId`), " +
                        "FOREIGN KEY(`playlistId`) REFERENCES `playlists`(`id`) " +
                        "ON UPDATE NO ACTION ON DELETE CASCADE )",
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_playlist_songs_playlistId` ON `playlist_songs` (`playlistId`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_playlist_songs_songId` ON `playlist_songs` (`songId`)")
            }
        }

        fun get(context: Context): AppDatabase =
            INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "qmusic.db",
                ).addMigrations(MIGRATION_1_2, MIGRATION_2_3).build().also { INSTANCE = it }
            }
    }
}