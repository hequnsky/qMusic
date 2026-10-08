package com.qmusic.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/** 我喜欢的歌曲 */
@Entity(tableName = "favorites")
data class Favorite(
    @PrimaryKey val songId: Long,
    val name: String,
    val artist: String,
    val album: String,
    val picUrl: String?,
    val durationMs: Long,
    val addedAt: Long = System.currentTimeMillis(),
)

/** 最近播放历史 */
@Entity(tableName = "history")
data class History(
    @PrimaryKey val songId: Long,
    val name: String,
    val artist: String,
    val album: String,
    val picUrl: String?,
    val durationMs: Long,
    val playedAt: Long = System.currentTimeMillis(),
)