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
        const val SOURCE_SODA = "soda"
        const val SOURCE_LOCAL = "local"
    }
}

/**
 * 汽水榜单 ID 约定：服务端把 19 位真实 chart id 映射成 900001 起的连续小整数，
 * 只有落在这个区间才走 /soda 接口，其它一律按网易云歌单处理。
 */
object SodaCharts {
    const val ID_MIN = 900001L
    const val ID_MAX = 900999L

    /** 首页推荐区取热歌榜前 N 首 */
    const val HOT_CHART_ID = 900001L

    fun isSodaChartId(id: Long): Boolean = id in ID_MIN..ID_MAX

    /**
     * 汽水歌单/电台的真实 id 是 19 位雪花号（约 7.0e18），
     * 网易云歌单 id 最大也就 10 位（约 1.8e9），两者不会撞。
     * 用「≥ 1e15」做分界，既能覆盖汽水所有 id，也不会误伤其它平台。
     */
    const val PLAYLIST_ID_MIN = 1_000_000_000_000_000L

    fun isSodaPlaylistId(id: Long): Boolean = id >= PLAYLIST_ID_MIN
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

/**
 * 网易云手机号登录（/login/cellphone）。
 * 服务端 login_cellphone 模块在 code=200 时把 cookie join 后放在 body.cookie，
 * profile 含 userId / nickname / avatarUrl。
 */
@Serializable
data class LoginCellphoneResponse(
    val code: Int = 0,
    val message: String = "",
    val cookie: String? = null,
    val profile: UserProfile? = null,
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
    /** 榜单来源：netease / soda。用于区分点击后走哪个接口 */
    val source: String = "netease",
)

// ---------------- 汽水音乐榜单（soda） ----------------

/**
 * 汽水音乐榜单目录。
 *
 * 注意：服务端下发的 id 是 900001 起的小整数（真实 chart id 是 19 位数字，
 * 超过 JSON 数字安全范围会被截断），chartId 才是汽水的原始 ID。
 */
@Serializable
data class SodaToplistResponse(
    val ok: Boolean = false,
    val list: List<SodaToplist> = emptyList(),
)

@Serializable
data class SodaToplist(
    val id: Long = 0,
    val chartId: String = "",
    val name: String = "",
    val coverImgUrl: String? = null,
    val description: String = "",
    val updateFrequency: String = "",
    val trackCount: Int = 0,
) {
    fun toToplist(): Toplist = Toplist(
        id = id,
        name = name,
        coverImgUrl = coverImgUrl,
        updateFrequency = updateFrequency,
        description = description,
        source = Song.SOURCE_SODA,
    )
}

/** 汽水榜单详情（/soda/toplist/{id} 与 /soda/hot 共用） */
@Serializable
data class SodaChartResponse(
    val ok: Boolean = false,
    val chart: SodaChartInfo? = null,
    val songs: List<MultiSong> = emptyList(),
    val rankings: List<SodaRank> = emptyList(),
    val error: String? = null,
)

@Serializable
data class SodaChartInfo(
    val id: Long = 0,
    val chartId: String = "",
    val name: String = "",
    val description: String = "",
    val coverImgUrl: String? = null,
    val updateFrequency: String = "",
    val trackCount: Int = 0,
)

@Serializable
data class SodaRank(
    val rank: Int = 0,
    val rankType: String = "",
    val rankDelta: Int = 0,
)

// ---------------- 汽水音乐歌单 / 电台（soda discover） ----------------

/** 汽水精选歌单列表（/soda/playlists） */
@Serializable
data class SodaPlaylistListResponse(
    val ok: Boolean = false,
    val total: Int = 0,
    val list: List<SodaPlaylist> = emptyList(),
)

/**
 * 汽水歌单。id 是 19 位雪花号，服务端以**字符串**下发（JSON 数字会丢精度），
 * `longId` 负责转成页面主键（19 位仍在 Long 范围内）。
 */
@Serializable
data class SodaPlaylist(
    val id: String = "",
    val name: String = "",
    val coverUrl: String? = null,
    val description: String = "",
    val trackCount: Int = 0,
    val playCount: Long = 0,
    val collectCount: Long = 0,
    val creator: String = "",
    val source: String = Song.SOURCE_SODA,
) {
    val longId: Long get() = id.toLongOrNull() ?: 0L
}

/** 汽水歌单详情（/soda/playlist/{id} 与 /soda/radio/{id} 共用） */
@Serializable
data class SodaPlaylistResponse(
    val ok: Boolean = false,
    val playlist: SodaPlaylistInfo? = null,
    val songs: List<MultiSong> = emptyList(),
    val error: String? = null,
)

@Serializable
data class SodaPlaylistInfo(
    val id: String = "",
    val name: String = "",
    val coverImgUrl: String? = null,
    val description: String = "",
    val trackCount: Int = 0,
    val playCount: Long = 0,
    val creator: String = "",
)

/** 汽水电台目录（/soda/radios） */
@Serializable
data class SodaRadioListResponse(
    val ok: Boolean = false,
    val list: List<SodaRadio> = emptyList(),
)

@Serializable
data class SodaRadio(
    val id: String = "",
    val name: String = "",
    val description: String = "",
    val coverUrl: String? = null,
    val source: String = Song.SOURCE_SODA,
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

// ---------- 网易云歌单导入 ----------

/** 歌单导入 - 创建任务响应 */
@Serializable
data class PlaylistImportResponse(
    val code: Int = 0,
    val message: String = "",
    val data: PlaylistImportData? = null,
)

@Serializable
data class PlaylistImportData(
    val id: String? = null,
    val taskId: String? = null,
    /** 部分实现把任务 id 放在 data.id / data.taskId，兼容两者 */
    val playlistId: Long = 0,
)

/** 歌单导入 - 任务状态响应 */
@Serializable
data class PlaylistImportStatusResponse(
    val code: Int = 0,
    val message: String = "",
    val data: PlaylistImportStatusData? = null,
)

@Serializable
data class PlaylistImportStatusData(
    val status: Int = 0,
    val message: String = "",
    val playlistId: Long = 0,
    val name: String = "",
    @SerialName("songCount") val songCount: Int = 0,
)

// ---------------- 视频（类抖音信息流） ----------------

/** 推荐视频信息流响应（/video/timeline/recommend） */
@Serializable
data class VideoTimelineResponse(
    val code: Int = 0,
    val datas: List<VideoTimelineItem> = emptyList(),
)

/** 信息流中的一项：data 里是视频完整信息 */
@Serializable
data class VideoTimelineItem(
    val type: Int = 0,
    val data: VideoData? = null,
)

/** 推荐视频数据 */
@Serializable
data class VideoData(
    /** 视频唯一 id（在 threadId 尾部） */
    val vid: String = "",
    /** 完整 threadId，形如 R_VI_62_<vid>；部分实现只在 threadId 里有 vid */
    @SerialName("threadId") val threadId: String = "",
    val title: String = "",
    val coverUrl: String = "",
    val description: String? = null,
    val durationms: Long = 0,
    @SerialName("commentCount") val commentCount: Long = 0,
    @SerialName("shareCount") val shareCount: Long = 0,
    @SerialName("playTime") val playTime: Long = 0,
    val creator: VideoCreator? = null,
) {
    val videoId: String
        get() = if (vid.isNotBlank()) vid else threadId.substringAfterLast('_')
    val durationMs: Long get() = durationms
}

/** 视频作者 */
@Serializable
data class VideoCreator(
    val userId: Long = 0,
    val nickname: String = "",
    val avatarUrl: String = "",
    val signature: String = "",
)

/** 视频播放地址响应（/video/url） */
@Serializable
data class VideoUrlResponse(
    val code: Int = 0,
    val urls: List<VideoUrl> = emptyList(),
)

@Serializable
data class VideoUrl(
    val id: String = "",
    val url: String = "",
)