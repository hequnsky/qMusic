package com.qmusic.data.local.entity

import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/** 本地歌单（用户自建） */
@Entity(tableName = "playlists")
data class LocalPlaylist(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val name: String,
    /** 歌单封面：默认取第一首歌的封面，可为空 */
    val coverUrl: String? = null,
    val createdAt: Long = System.currentTimeMillis(),
)

/** 歌单与歌曲的关联。复合主键 (playlistId, songId) 保证同一首歌不会重复加入同一歌单。 */
@Entity(
    tableName = "playlist_songs",
    primaryKeys = ["playlistId", "songId"],
    foreignKeys = [
        ForeignKey(
            entity = LocalPlaylist::class,
            parentColumns = ["id"],
            childColumns = ["playlistId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("playlistId"), Index("songId")],
)
data class PlaylistSong(
    val playlistId: Long,
    val songId: Long,
    val name: String,
    val artist: String,
    val album: String,
    val picUrl: String?,
    val durationMs: Long,
    /** 音源标识，保证重新播放时能解析到正确地址 */
    val source: String = "netease",
    val token: String? = null,
    val addedAt: Long = System.currentTimeMillis(),
)

/** 歌单 + 歌曲数（列表页直接展示，避免 N+1 查询） */
data class PlaylistWithCount(
    @Embedded val playlist: LocalPlaylist,
    val songCount: Int,
)
