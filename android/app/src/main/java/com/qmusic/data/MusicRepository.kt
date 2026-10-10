package com.qmusic.data

import com.qmusic.data.local.FavoriteDao
import com.qmusic.data.local.HistoryDao
import com.qmusic.data.local.entity.Favorite
import com.qmusic.data.local.entity.History
import com.qmusic.data.model.Comment
import com.qmusic.data.model.UserDetailProfile
import com.qmusic.data.model.UserProfile
import com.qmusic.data.model.LyricLine
import com.qmusic.data.model.Playlist
import com.qmusic.data.model.PlaylistDetail
import com.qmusic.data.model.Song
import com.qmusic.data.remote.MusicApiService
import com.qmusic.util.LrcParser
import kotlinx.coroutines.flow.Flow

/** 数据仓库：统一聚合远程音乐接口与本地收藏/历史 */
class MusicRepository(
    private val api: MusicApiService,
    private val favoriteDao: FavoriteDao,
    private val historyDao: HistoryDao,
) {

    // ---------- 远程 ----------

    suspend fun search(keywords: String, limit: Int = 30): List<Song> =
        api.search(keywords, limit).result.songs

    suspend fun getHotKeywords(limit: Int = 10): List<String> =
        api.searchHot().result.hots.take(limit).map { it.first }

    // ---------- 网易云账号登录 ----------

    /** 获取二维码 key */
    suspend fun getQrKey(): String = api.qrKey().data.unikey

    /** 生成登录二维码，返回 (base64 图片, 二维码链接) */
    suspend fun createQr(key: String): Pair<String, String> {
        val d = api.qrCreate(key, true).data
        return d.qrimg to d.qrurl
    }

    /** 轮询二维码状态，返回 (code, cookie)。code: 801=等待扫码 802=待确认 803=登录成功 800=过期 */
    suspend fun checkQr(key: String): Pair<Int, String> {
        val resp = api.qrCheck(key)
        var cookie = resp.body()?.cookie ?: ""
        if (cookie.isBlank()) {
            cookie = resp.headers()["Set-Cookie"] ?: resp.raw().header("Set-Cookie") ?: ""
        }
        return (resp.body()?.code ?: -1) to cookie
    }

    /** 登录后获取账号信息 */
    suspend fun getLoginAccount(cookie: String): UserProfile? = api.userAccount(cookie).profile

    /** 获取用户详情（关注/粉丝/动态） */
    suspend fun getLoginUserDetail(uid: Long, cookie: String): UserDetailProfile? =
        api.userDetail(uid, cookie).profile

    suspend fun getSongUrl(id: Long): String? =
        api.songUrl(id).data.firstOrNull { !it.url.isNullOrBlank() }?.url

    suspend fun getLyric(id: Long): List<LyricLine> {
        val resp = api.lyric(id)
        val lrc = LrcParser.parse(resp.lrc?.lyric.orEmpty())
        val trans = LrcParser.parse(resp.tlyric?.lyric.orEmpty())
        val transMap = trans.associate { it.timeMs to it.content }
        return lrc.map { it.copy(translation = transMap[it.timeMs] ?: "") }
    }

    suspend fun getPersonalizedPlaylist(): Playlist? =
        api.personalized(1).result.firstOrNull()

    suspend fun getTopPlaylists(limit: Int = 10): List<Playlist> =
        api.topPlaylist(limit).playlists

    suspend fun getTopPlaylistsByCat(cat: String, limit: Int = 50): List<Playlist> =
        api.topPlaylist(limit, cat = cat).playlists

    /** 新歌速递/榜单歌曲（type: 0=全部 7=华语 96=欧美） */
    suspend fun getTopSongs(type: Int = 0, limit: Int = 50): List<Song> =
        api.topSong(type, limit).data

    /** 歌曲评论 */
    suspend fun getMusicComments(id: Long, limit: Int = 30): List<Comment> =
        api.musicComment(id, limit).comments

    suspend fun getPlaylistDetail(id: Long): PlaylistDetail? =
        api.playlistDetail(id).playlist.takeIf { it.id != 0L }

    // ---------- 收藏 ----------

    fun observeFavorites(): Flow<List<Favorite>> = favoriteDao.observeAll()

    suspend fun isFavorite(songId: Long): Boolean =
        favoriteDao.findById(songId) != null

    suspend fun addFavorite(song: Song) {
        favoriteDao.insert(
            Favorite(
                songId = song.id,
                name = song.name,
                artist = song.artistName,
                album = song.albumName,
                picUrl = song.coverUrl,
                durationMs = song.durationMs,
            ),
        )
    }

    suspend fun removeFavorite(songId: Long) = favoriteDao.deleteById(songId)

    // ---------- 播放历史 ----------

    fun observeHistory(): Flow<List<History>> = historyDao.observeAll()

    suspend fun addHistory(song: Song) {
        historyDao.insert(
            History(
                songId = song.id,
                name = song.name,
                artist = song.artistName,
                album = song.albumName,
                picUrl = song.coverUrl,
                durationMs = song.durationMs,
            ),
        )
    }
}