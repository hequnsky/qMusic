package com.qmusic.data.remote

import com.qmusic.data.model.CommentResponse
import com.qmusic.data.model.LyricResponse
import com.qmusic.data.model.PersonalizedResponse
import com.qmusic.data.model.PlaylistDetailResponse
import com.qmusic.data.model.QrCheckResponse
import com.qmusic.data.model.QrCreateResponse
import com.qmusic.data.model.QrKeyResponse
import com.qmusic.data.model.SearchHotResponse
import com.qmusic.data.model.SearchResponse
import com.qmusic.data.model.SearchSuggestResponse
import com.qmusic.data.model.SongUrlResponse
import com.qmusic.data.model.ToplistResponse
import com.qmusic.data.model.TopPlaylistResponse
import com.qmusic.data.model.TopSongResponse
import com.qmusic.data.model.UserAccountResponse
import com.qmusic.data.model.UserDetailResponse
import retrofit2.Response
import retrofit2.http.GET
import retrofit2.http.Header
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

    /** 搜索建议 */
    @GET("search/suggest")
    suspend fun searchSuggest(@Query("keywords") keywords: String): SearchSuggestResponse

    /** 排行榜列表 */
    @GET("toplist")
    suspend fun toplist(): ToplistResponse

    // ---------- 网易云账号登录 ----------

    /** 二维码 key */
    @GET("login/qr/key")
    suspend fun qrKey(): QrKeyResponse

    /** 生成二维码（返回 base64 data URI） */
    @GET("login/qr/create")
    suspend fun qrCreate(
        @Query("key") key: String,
        @Query("qrimg") qrimg: Boolean = true,
    ): QrCreateResponse

    /** 轮询扫码状态（Response 包装以读取 Set-Cookie） */
    @GET("login/qr/check")
    suspend fun qrCheck(@Query("key") key: String): Response<QrCheckResponse>

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
}