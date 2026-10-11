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
    /** 音源标识（netease/qq/kugou/.../local），v2 新增 */
    val source: String = "netease",
    /** 多音源令牌，v2 新增；非网易云歌曲靠它才能重新解析播放地址 */
    val token: String? = null,
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
    /** 音源标识，v2 新增 */
    val source: String = "netease",
    /** 多音源令牌，v2 新增 */
    val token: String? = null,
    val playedAt: Long = System.currentTimeMillis(),
)