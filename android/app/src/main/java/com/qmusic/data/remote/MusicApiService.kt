package com.qmusic.data.remote

import com.qmusic.data.model.CommentResponse
import com.qmusic.data.model.LyricResponse
import com.qmusic.data.model.LoginCellphoneResponse
import com.qmusic.data.model.MultiLyricResponse
import com.qmusic.data.model.MultiSearchResponse
import com.qmusic.data.model.MultiSongUrlResponse
import com.qmusic.data.model.MultiSourcesResponse
import com.qmusic.data.model.PersonalizedResponse
import com.qmusic.data.model.PlaylistImportResponse
import com.qmusic.data.model.PlaylistImportStatusResponse
import com.qmusic.data.model.PlaylistDetailResponse
import com.qmusic.data.model.SearchHotDetailResponse
import com.qmusic.data.model.SearchHotResponse
import com.qmusic.data.model.SearchResponse
import com.qmusic.data.model.SearchSuggestResponse
import com.qmusic.data.model.SodaChartResponse
import com.qmusic.data.model.SodaToplistResponse
import com.qmusic.data.model.SongDetailResponse
import com.qmusic.data.model.SongUrlResponse
import com.qmusic.data.model.ToplistResponse
import com.qmusic.data.model.TopPlaylistResponse
import com.qmusic.data.model.TopSongResponse
import com.qmusic.data.model.UserAccountResponse
import com.qmusic.data.model.UserDetailResponse
import com.qmusic.data.model.VideoTimelineResponse
import com.qmusic.data.model.VideoUrlResponse
import retrofit2.Response
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.Path
import retrofit2.http.Query

/** 音乐抓取服务 REST 接口 */
interface MusicApiService {

    /** 搜索歌曲 */
    @GET("search")
    suspend fun search(
        @Query("keywords") keywords: String,
        @Query("limit") limit: Int = 30,
        @Query("offset") offset: Int = 0,
    ): SearchResponse

    /** 热门搜索词 */
    @GET("search/hot")
    suspend fun searchHot(): SearchHotResponse

    /** 热搜榜详情（更全，带热度值） */
    @GET("search/hot/detail")
    suspend fun searchHotDetail(): SearchHotDetailResponse

    /** 搜索建议 */
    @GET("search/suggest")
    suspend fun searchSuggest(@Query("keywords") keywords: String): SearchSuggestResponse

    /**
     * 歌曲详情（批量）。
     * 用于回填「搜索 / 搜索建议」结果里缺失的专辑封面（这两个接口只返回 picId，不返回 picUrl）。
     */
    @GET("song/detail")
    suspend fun songDetail(@Query("ids") ids: String): SongDetailResponse

    /** 排行榜列表 */
    @GET("toplist")
    suspend fun toplist(): ToplistResponse

    // ---------- 网易云账号登录 ----------

    /**
     * 手机号登录（网易云 weapi）。
     * 明文密码由服务端 login_cellphone 模块负责 MD5；
     * Response 包装以便登录失败（非 2xx）时也能读取 body。
     */
    @GET("login/cellphone")
    suspend fun loginCellphone(
        @Query("phone") phone: String,
        @Query("password") password: String,
    ): Response<LoginCellphoneResponse>

    /** 当前登录账号 */
    @GET("user/account")
    suspend fun userAccount(@Header("Cookie") cookie: String): UserAccountResponse

    /** 用户详情（关注/粉丝/动态） */
    @GET("user/detail")
    suspend fun userDetail(
        @Query("uid") uid: Long,
        @Header("Cookie") cookie: String,
    ): UserDetailResponse

    /** 获取歌曲播放地址 */
    @GET("song/url")
    suspend fun songUrl(
        @Query("id") id: Long,
        @Query("level") level: String = "standard",
    ): SongUrlResponse

    /** 获取歌词 */
    @GET("lyric")
    suspend fun lyric(@Query("id") id: Long): LyricResponse

    /** 每日推荐（返回推荐歌单） */
    @GET("personalized")
    suspend fun personalized(@Query("limit") limit: Int = 1): PersonalizedResponse

    /** 热门歌单 */
    @GET("top/playlist")
    suspend fun topPlaylist(
        @Query("limit") limit: Int = 10,
        @Query("order") order: String = "hot",
        @Query("cat") cat: String? = null,
    ): TopPlaylistResponse

    /** 新歌速递 / 榜单歌曲（type: 0=全部 7=华语 96=欧美） */
    @GET("top/song")
    suspend fun topSong(
        @Query("type") type: Int = 0,
        @Query("limit") limit: Int = 30,
    ): TopSongResponse

    /** 歌曲评论 */
    @GET("comment/music")
    suspend fun musicComment(
        @Query("id") id: Long,
        @Query("limit") limit: Int = 20,
    ): CommentResponse

    /** 歌单详情 */
    @GET("playlist/detail")
    suspend fun playlistDetail(@Query("id") id: Long): PlaylistDetailResponse

    // ---------- 网易云歌单导入（需登录） ----------

    /** 歌单导入 - 元数据/文字/链接导入（生成任务） */
    @GET("playlist/import/name/task/create")
    suspend fun playlistImportCreate(
        @Query("text") text: String? = null,
        @Query("local") local: String? = null,
        @Query("link") link: String? = null,
        @Query("playlistName") playlistName: String? = null,
        @Query("importStarPlaylist") importStarPlaylist: Boolean = false,
        @Header("Cookie") cookie: String,
    ): PlaylistImportResponse

    /** 歌单导入 - 任务状态查询 */
    @GET("playlist/import/task/status")
    suspend fun playlistImportStatus(
        @Query("id") id: String,
        @Header("Cookie") cookie: String,
    ): PlaylistImportStatusResponse

    // ---------------- 多音源（music-lib 聚合层，/multi/*） ----------------

    /** 可用音源列表（服务端多音源是否在线） */
    @GET("multi/sources")
    suspend fun multiSources(): MultiSourcesResponse

    /** 按音源搜索歌曲 */
    @GET("multi/search")
    suspend fun multiSearch(
        @Query("source") source: String,
        @Query("keyword") keyword: String,
        @Query("limit") limit: Int = 30,
    ): MultiSearchResponse

    /** 多音源播放地址（token 由搜索接口返回） */
    @GET("multi/song/url")
    suspend fun multiSongUrl(@Query("s") token: String): MultiSongUrlResponse

    /** 多音源歌词 */
    @GET("multi/lyric")
    suspend fun multiLyric(@Query("s") token: String): MultiLyricResponse

    // ---------------- 汽水音乐榜单（soda，直连 api.qishui.com） ----------------

    /** 汽水官方榜单目录（热歌榜 / 新歌榜 / 欧美榜 / 音乐人歌曲榜） */
    @GET("soda/toplist")
    suspend fun sodaToplist(): SodaToplistResponse

    /** 汽水指定榜单的歌曲（limit=0 表示全量） */
    @GET("soda/toplist/{id}")
    suspend fun sodaChart(
        @Path("id") id: Long,
        @Query("limit") limit: Int = 0,
    ): SodaChartResponse

    /** 汽水热歌榜前 N 首（首页推荐区用） */
    @GET("soda/hot")
    suspend fun sodaHot(@Query("limit") limit: Int = 12): SodaChartResponse

    // ---------------- 视频（类抖音信息流） ----------------

    /** 推荐视频信息流 */
    @GET("video/timeline/recommend")
    suspend fun videoTimelineRecommend(@Query("offset") offset: Int = 0): VideoTimelineResponse

    /** 视频播放地址 */
    @GET("video/url")
    suspend fun videoUrl(@Query("id") id: String): VideoUrlResponse
}