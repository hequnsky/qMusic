package com.qmusic.data.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** 歌手 */
@Serializable
data class Artist(
    val id: Long = 0,
    val name: String = "",
)

/** 专辑 */
@Serializable
data class Album(
    val id: Long = 0,
    val name: String = "",
    @SerialName("picUrl") val picUrl: String? = null,
    // 注意：歌单详情中 al.pic 是数字形式的图片 ID，而不是 URL 字符串
    @SerialName("pic") val pic: Long? = null,
)

/**
 * 歌曲。
 * 说明：网易云搜索接口返回字段为 artists/album，歌单详情返回 ar/al，
 * 因此同时兼容两种字段名。
 */
@Serializable
data class Song(
    val id: Long = 0,
    val name: String = "",
    @SerialName("ar") val arList: List<Artist> = emptyList(),
    val artists: List<Artist> = emptyList(),
    @SerialName("al") val alAlbum: Album? = null,
    val album: Album? = null,
    val duration: Long? = null,
    @SerialName("dt") val dt: Long? = null,
) {
    val artistList: List<Artist> get() = if (artists.isNotEmpty()) artists else arList
    val albumObj: Album? get() = album ?: alAlbum
    val artistName: String get() = artistList.joinToString("/") { it.name }
    val albumName: String get() = albumObj?.name ?: "未知专辑"
    val coverUrl: String? get() = albumObj?.picUrl
    val durationMs: Long get() = dt ?: duration ?: 0
}

/** 搜索响应 */
@Serializable
data class SearchResponse(val result: SearchResult = SearchResult())

@Serializable
data class SearchResult(val songs: List<Song> = emptyList())

/** 播放地址响应 */
@Serializable
data class SongUrlResponse(val data: List<SongUrl> = emptyList())

@Serializable
data class SongUrl(
    val id: Long = 0,
    val url: String? = null,
    val br: Long? = null,
    val type: String? = null,
    val size: Long? = null,
    val time: Long? = null,
)

/** 歌词响应 */
@Serializable
data class LyricResponse(
    val lrc: Lrc? = null,
    val tlyric: Lrc? = null,
)

@Serializable
data class Lrc(val lyric: String = "")

/** 解析后的歌词行 */
@Serializable
data class LyricLine(
    val timeMs: Long,
    val content: String,
)

/** 歌单 */
@Serializable
data class Playlist(
    val id: Long = 0,
    val name: String = "",
    @SerialName("picUrl") val picUrl: String? = null,
    @SerialName("coverImgUrl") val coverImgUrl: String? = null,
    @SerialName("trackCount") val trackCount: Int = 0,
    @SerialName("playCount") val playCount: Long = 0,
) {
    val coverUrl: String? get() = picUrl ?: coverImgUrl
}

/** 每日推荐响应 */
@Serializable
data class PersonalizedResponse(val result: List<Playlist> = emptyList())

/** 热门歌单响应 */
@Serializable
data class TopPlaylistResponse(val playlists: List<Playlist> = emptyList())

/** 歌单详情响应 */
@Serializable
data class PlaylistDetailResponse(val playlist: PlaylistDetail = PlaylistDetail())

@Serializable
data class PlaylistDetail(
    val id: Long = 0,
    val name: String = "",
    @SerialName("coverImgUrl") val coverImgUrl: String? = null,
    @SerialName("trackCount") val trackCount: Int = 0,
    val tracks: List<Song> = emptyList(),
) {
    val coverUrl: String? get() = coverImgUrl
}