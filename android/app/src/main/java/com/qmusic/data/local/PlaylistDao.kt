package com.qmusic.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.qmusic.data.local.entity.LocalPlaylist
import com.qmusic.data.local.entity.PlaylistSong
import com.qmusic.data.local.entity.PlaylistWithCount
import kotlinx.coroutines.flow.Flow

/** 本地歌单 DAO：歌单增删改查 + 歌曲关联管理 */
@Dao
interface PlaylistDao {

    // ---------- 歌单 ----------

    @Query(
        "SELECT p.*, (SELECT COUNT(*) FROM playlist_songs s WHERE s.playlistId = p.id) AS songCount " +
            "FROM playlists p ORDER BY p.createdAt DESC",
    )
    fun observeAllWithCount(): Flow<List<PlaylistWithCount>>

    @Query("SELECT * FROM playlists WHERE id = :id LIMIT 1")
    suspend fun findById(id: Long): LocalPlaylist?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPlaylist(playlist: LocalPlaylist): Long

    @Update
    suspend fun updatePlaylist(playlist: LocalPlaylist)

    @Query("UPDATE playlists SET name = :name WHERE id = :id")
    suspend fun renamePlaylist(id: Long, name: String)

    /** 删除歌单；关联歌曲由外键 ON DELETE CASCADE 一并清理 */
    @Query("DELETE FROM playlists WHERE id = :id")
    suspend fun deletePlaylist(id: Long)

    // ---------- 歌单内歌曲 ----------

    @Query("SELECT * FROM playlist_songs WHERE playlistId = :playlistId ORDER BY addedAt ASC")
    fun observeSongs(playlistId: Long): Flow<List<PlaylistSong>>

    @Query("SELECT playlistId FROM playlist_songs WHERE songId = :songId")
    suspend fun playlistIdsOfSong(songId: Long): List<Long>

    @Query("SELECT EXISTS(SELECT 1 FROM playlist_songs WHERE playlistId = :playlistId AND songId = :songId)")
    suspend fun containsSong(playlistId: Long, songId: Long): Boolean

    /** 已在歌单中则忽略（复合主键 + IGNORE），因此重复添加是幂等的 */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun addSong(song: PlaylistSong)

    @Query("DELETE FROM playlist_songs WHERE playlistId = :playlistId AND songId = :songId")
    suspend fun removeSong(playlistId: Long, songId: Long)
}
