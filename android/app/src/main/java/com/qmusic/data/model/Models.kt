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
    /** 本地音乐文件路径（MediaStore 扫描），非空时表示本地歌曲 */
    val localPath: String? = null,
    /** 音源标识：netease / qq / kugou / kuwo / migu / qianqian / soda / bilibili / apple；local 表示本地歌曲 */
    val source: String = SOURCE_NETEASE,
    /** 多音源令牌：完整歌曲元数据的 base64 载荷，播放/歌词请求时原样回传给服务端 */
    val token: String? = null,
) {
    val artistList: List<Artist> get() = if (artists.isNotEmpty()) artists else arList
    val albumObj: Album? get() = album ?: alAlbum
    val artistName: String get() = artistList.joinToString("/") { it.name }
    val albumName: String get() = albumObj?.name ?: "未知专辑"
    val coverUrl: String? get() = albumObj?.picUrl
    val durationMs: Long get() = dt ?: duration ?: 0
    val isLocal: Boolean get() = !localPath.isNullOrBlank()

    companion object {
        const val SOURCE_NETEASE = "netease"
        const val SOURCE_LOCAL = "local"
    }
}

/**
 * 由「音源 + 平台原始 id」派生出稳定的 Long 主键。
 * 多音源的 id 是字符串（如 QQ 的 songmid、B站的 BV 号），
 * 而收藏/历史表的主键是 Long，因此做一次 FNV-1a 64 位哈希，
 * 保证同一首歌每次都得到相同的主键、且不同音源不会互相撞车。
 */
internal fun stableSongId(source: String, rawId: String): Long {
    var h = -3750763034362895579L // FNV-1a 64 offset basis
    for (ch in "$source:$rawId") {
        h = h xor ch.code.toLong()
        h *= 1099511628211L
    }
    return if (h == 0L) 1L else h
}

/** 多音源列表中的一项 */
@Serializable
data class MusicSource(
    val id: String = "",
    val name: String = "",
    val requiresProxy: Boolean = false,
)

/** 多音源可用性 + 音源列表 */
@Serializable
data class MultiSourcesResponse(
    val ok: Boolean = false,
    val available: Boolean = false,
    val sources: List<MusicSource> = emptyList(),
    val error: String? = null,
)

/** 多音源歌曲（服务端统一结构） */
@Serializable
data class MultiSong(
    val id: String = "",
    val name: String = "",
    val artist: String = "",
    val album: String = "",
    val duration: Int = 0,
    val cover: String? = null,
    val source: String = Song.SOURCE_NETEASE,
    val ext: String? = null,
    val link: String? = null,
    val token: String? = null,
) {
    fun toSong(): Song = Song(
        id = stableSongId(source.ifBlank { Song.SOURCE_NETEASE }, id),
        name = name,
        artists = listOf(Artist(name = artist)),
        album = Album(name = album, picUrl = cover),
        dt = duration * 1000L,
        source = source.ifBlank { Song.SOURCE_NETEASE },
        token = token,
    )
}

@Serializable
data class MultiSearchResponse(
    val ok: Boolean = false,
    val source: String = Song.SOURCE_NETEASE,
    val songs: List<MultiSong> = emptyList(),
    val error: String? = null,
)

@Serializable
data class MultiSongUrlResponse(
    val ok: Boolean = false,
    val url: String? = null,
    val proxied: Boolean = false,
    val error: String? = null,
)

@Serializable
data class MultiLyricResponse(
    val ok: Boolean = false,
    val lrc: String = "",
    val error: String? = null,
)

/**
 * 歌曲详情响应。
 * 用途：网易云「搜索」「搜索建议」接口返回的歌曲只有 album.picId（数字），
 * 没有 picUrl，导致播放页/通知栏/桌面小组件拿不到封面。
 * 通过 song/detail 批量查询可拿到 al.picUrl，用于回填封面。
 */
@Serializable
data class SongDetailResponse(
    val songs: List<Song> = emptyList(),
    val code: Int = 0,
)

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
    val translation: String = "",
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

/** 新歌速递 / 榜单歌曲响应（/top/song） */
@Serializable
data class TopSongResponse(val data: List<Song> = emptyList())

/** 歌曲评论响应（/comment/music） */
@Serializable
data class CommentResponse(val comments: List<Comment> = emptyList())

@Serializable
data class Comment(
    val user: CommentUser = CommentUser(),
    val content: String = "",
    val time: Long = 0,
    val likedCount: Int = 0,
)

@Serializable
data class CommentUser(
    val nickname: String = "",
    val avatarUrl: String? = null,
)

/** 热门搜索响应（/search/hot） */
@Serializable
data class SearchHotResponse(val result: SearchHotResult = SearchHotResult())

@Serializable
data class SearchHotResult(val hots: List<SearchHotItem> = emptyList())

@Serializable
data class SearchHotItem(val first: String = "")

/** 热搜榜详情（/search/hot/detail）：比 /search/hot 更全，且带热度值 */
@Serializable
data class SearchHotDetailResponse(val data: List<HotDetailItem> = emptyList())

@Serializable
data class HotDetailItem(
    val searchWord: String = "",
    val score: Long = 0,
    /** 榜单上的一句话推荐语，可能为空 */
    val content: String = "",
)

/** 网易云二维码登录 */
@Serializable
data class QrKeyResponse(val data: QrKeyData = QrKeyData())

@Serializable
data class QrKeyData(val unikey: String = "")

@Serializable
data class QrCreateResponse(val data: QrCreateData = QrCreateData())

@Serializable
data class QrCreateData(val qrimg: String = "", val qrurl: String = "")

@Serializable
data class QrCheckResponse(
    val code: Int = 0,
    val message: String = "",
    val cookie: String? = null,
)

/** 用户账号 */
@Serializable
data class UserAccountResponse(val profile: UserProfile? = null)

@Serializable
data class UserProfile(
    val userId: Long = 0,
    val nickname: String = "",
    val avatarUrl: String? = null,
)

@Serializable
data class UserDetailResponse(val profile: UserDetailProfile? = null)

@Serializable
data class UserDetailProfile(
    val userId: Long = 0,
    val nickname: String = "",
    val avatarUrl: String? = null,
    val follows: Long = 0,
    val followeds: Long = 0,
    val eventCount: Long = 0,
)

/** 搜索建议响应（/search/suggest） */
@Serializable
data class SearchSuggestResponse(val result: SearchSuggestResult = SearchSuggestResult())

@Serializable
data class SearchSuggestResult(val songs: List<SuggestSong> = emptyList())

@Serializable
data class SuggestSong(
    val id: Long = 0,
    val name: String = "",
    val artists: List<SuggestArtist> = emptyList(),
    val album: SuggestAlbum? = null,
    val duration: Long = 0,
) {
    fun toSong(): Song = Song(
        id = id,
        name = name,
        artists = artists.map { Artist(id = it.id, name = it.name) },
        album = album?.let { Album(name = it.name) },
        duration = duration,
    )
}

@Serializable
data class SuggestArtist(
    val id: Long = 0,
    val name: String = "",
    val img1v1Url: String? = null,
)

@Serializable
data class SuggestAlbum(val name: String = "")

/** 排行榜列表响应（/toplist） */
@Serializable
data class ToplistResponse(val list: List<Toplist> = emptyList())

@Serializable
data class Toplist(
    val id: Long = 0,
    val name: String = "",
    val coverImgUrl: String? = null,
    val updateFrequency: String = "",
    val description: String = "",
)

/** 歌单详情响应 */
@Serializable
data class PlaylistDetailResponse(val playlist: PlaylistDetail = PlaylistDetail())

@Serializable
data class PlaylistDetail(
    val id: Long = 0,
    val name: String = "",
    @SerialName("coverImgUrl") val coverImgUrl: String? = null,
    @SerialName("trackCount") val trackCount: Int = 0,
    @SerialName("playCount") val playCount: Long = 0,
    val tracks: List<Song> = emptyList(),
) {
    val coverUrl: String? get() = coverImgUrl
}