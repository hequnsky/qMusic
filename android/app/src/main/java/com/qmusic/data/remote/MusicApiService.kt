package com.qmusic.data.remote

import com.qmusic.data.model.CommentResponse
import com.qmusic.data.model.LyricResponse
import com.qmusic.data.model.PersonalizedResponse
import com.qmusic.data.model.PlaylistDetailResponse
import com.qmusic.data.model.SearchResponse
import com.qmusic.data.model.SongUrlResponse
import com.qmusic.data.model.TopPlaylistResponse
import com.qmusic.data.model.TopSongResponse
import retrofit2.http.GET
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