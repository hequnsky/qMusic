package com.qmusic.data

import com.qmusic.data.local.FavoriteDao
import com.qmusic.data.local.HistoryDao
import com.qmusic.data.local.PlaylistDao
import com.qmusic.data.local.entity.Favorite
import com.qmusic.data.local.entity.History
import com.qmusic.data.local.entity.LocalPlaylist
import com.qmusic.data.local.entity.PlaylistSong
import com.qmusic.data.local.entity.PlaylistWithCount
import com.qmusic.data.model.Album
import com.qmusic.data.model.Artist
import com.qmusic.data.model.Comment
import com.qmusic.data.model.Toplist
import com.qmusic.data.model.UserDetailProfile
import com.qmusic.data.model.UserProfile
import com.qmusic.data.model.LyricLine
import com.qmusic.data.model.MusicSource
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
    private val playlistDao: PlaylistDao,
) {

    // ---------- 远程 ----------

    /**
     * 搜索歌曲。
     * - source = netease（默认）时走网易云全量接口；
     * - 其它音源走多音源聚合层（multi 接口），失败时回退到网易云，保证「永远搜得到」。
     * 两条路径的结果都会做一次封面回填（见 [enrichCovers]）。
     */
    suspend fun search(
        keywords: String,
        limit: Int = 30,
        source: String = Song.SOURCE_NETEASE,
    ): List<Song> {
        if (source == Song.SOURCE_NETEASE || source.isBlank()) {
            return enrichCovers(api.search(keywords, limit).result.songs)
        }
        return try {
            val resp = api.multiSearch(source, keywords, limit)
            if (resp.ok && resp.songs.isNotEmpty()) resp.songs.map { it.toSong() }
            else enrichCovers(api.search(keywords, limit).result.songs)
        } catch (e: Exception) {
            enrichCovers(api.search(keywords, limit).result.songs)
        }
    }

    /**
     * 回填缺失的专辑封面。
     *
     * 背景：网易云的「搜索」「搜索建议」接口只返回 album.picId（数字图片 ID），
     * 不返回 album.picUrl，导致 Song.coverUrl 为 null ——
     * 播放页唱片、通知栏、系统媒体控制中心、桌面小组件都只能显示占位图。
     * 这里对缺失封面的歌曲批量调用 song/detail 拿到 al.picUrl 再回填。
     *
     * 全部有封面时直接返回，不产生额外请求；失败时静默返回原列表，不影响搜索本身。
     */
    private suspend fun enrichCovers(songs: List<Song>): List<Song> {
        if (songs.isEmpty()) return songs
        if (songs.all { !it.coverUrl.isNullOrBlank() }) return songs
        return try {
            val ids = songs.filter { it.coverUrl.isNullOrBlank() }
                .joinToString(",") { it.id.toString() }
            if (ids.isBlank()) return songs
            val coverById = api.songDetail(ids).songs
                .mapNotNull { s -> s.coverUrl?.let { url -> s.id to url } }
                .toMap()
            if (coverById.isEmpty()) return songs
            songs.map { song ->
                val url = coverById[song.id]
                if (url.isNullOrBlank() || !song.coverUrl.isNullOrBlank()) {
                    song
                } else {
                    val base = song.albumObj
                    song.copy(
                        album = base?.copy(picUrl = url) ?: Album(picUrl = url),
                    )
                }
            }
        } catch (e: Exception) {
            songs
        }
    }

    /** 可用音源列表（多音源服务不在线时返回空列表，UI 自动隐藏音源切换） */
    suspend fun getMusicSources(): List<MusicSource> = try {
        api.multiSources().takeIf { it.available }?.sources.orEmpty()
    } catch (e: Exception) {
        emptyList()
    }

    suspend fun getHotKeywords(limit: Int = 10): List<String> =
        api.searchHot().result.hots.take(limit).map { it.first }

    suspend fun getSearchSuggest(keywords: String, limit: Int = 6): List<Song> =
        enrichCovers(api.searchSuggest(keywords).result.songs.take(limit).map { it.toSong() })

    suspend fun getToplists(): List<Toplist> = api.toplist().list

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

    /**
     * 取播放地址。带 token 的非网易云歌曲走多音源接口；
     * 其余（网易云 / 本地）沿用原逻辑，行为完全不变。
     */
    suspend fun getSongUrl(song: Song): String? {
        val token = song.token
        if (song.source != Song.SOURCE_NETEASE && !token.isNullOrBlank()) {
            val resp = api.multiSongUrl(token)
            resp.url?.takeIf { it.isNotBlank() }?.let { return it }
        }
        return getSongUrl(song.id)
    }

    suspend fun getLyric(id: Long): List<LyricLine> {
        val resp = api.lyric(id)
        val lrc = LrcParser.parse(resp.lrc?.lyric.orEmpty())
        val trans = LrcParser.parse(resp.tlyric?.lyric.orEmpty())
        val transMap = trans.associate { it.timeMs to it.content }
        return lrc.map { it.copy(translation = transMap[it.timeMs] ?: "") }
    }

    /** 取歌词。带 token 的非网易云歌曲走多音源接口。 */
    suspend fun getLyric(song: Song): List<LyricLine> {
        val token = song.token
        if (song.source != Song.SOURCE_NETEASE && !token.isNullOrBlank()) {
            val lrc = api.multiLyric(token).lrc
            if (lrc.isNotBlank()) return LrcParser.parse(lrc)
        }
        return getLyric(song.id)
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
                source = song.source,
                token = song.token,
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
                source = song.source,
                token = song.token,
            ),
        )
    }

    // ---------- 本地歌单 ----------

    /** 歌单列表（含歌曲数），按创建时间倒序 */
    fun observePlaylists(): Flow<List<PlaylistWithCount>> = playlistDao.observeAllWithCount()

    fun observePlaylistSongs(playlistId: Long): Flow<List<PlaylistSong>> =
        playlistDao.observeSongs(playlistId)

    /** 该歌曲已存在于哪些歌单（用于「添加到歌单」弹窗勾选状态） */
    suspend fun playlistIdsOfSong(songId: Long): List<Long> = playlistDao.playlistIdsOfSong(songId)

    /** 新建歌单，返回新歌单 id */
    suspend fun createPlaylist(name: String, coverUrl: String? = null): Long =
        playlistDao.insertPlaylist(LocalPlaylist(name = name.trim(), coverUrl = coverUrl))

    suspend fun renamePlaylist(id: Long, name: String) =
        playlistDao.renamePlaylist(id, name.trim())

    /** 删除歌单（关联歌曲由外键级联删除） */
    suspend fun deletePlaylist(id: Long) = playlistDao.deletePlaylist(id)

    /**
     * 把歌曲加入歌单。
     * 重复加入是幂等的（复合主键 + IGNORE），返回是否真的新增。
     */
    suspend fun addSongToPlaylist(playlistId: Long, song: Song): Boolean {
        if (playlistDao.containsSong(playlistId, song.id)) return false
        playlistDao.addSong(
            PlaylistSong(
                playlistId = playlistId,
                songId = song.id,
                name = song.name,
                artist = song.artistName,
                album = song.albumName,
                picUrl = song.coverUrl,
                durationMs = song.durationMs,
                source = song.source,
                token = song.token,
            ),
        )
        return true
    }

    suspend fun removeSongFromPlaylist(playlistId: Long, songId: Long) =
        playlistDao.removeSong(playlistId, songId)

    /** 播放歌单：把 PlaylistSong 还原成可播放的 Song（保留 source/token 以解析播放地址） */
    fun playlistSongsToSongs(songs: List<PlaylistSong>): List<Song> = songs.map { it.toSong() }

    /** 新建歌单并立即加入当前歌曲，一步完成 */
    suspend fun createPlaylistWithSong(name: String, song: Song): Long {
        val id = createPlaylist(name, song.coverUrl)
        addSongToPlaylist(id, song)
        return id
    }
}

/** PlaylistSong -> Song */
fun PlaylistSong.toSong(): Song = Song(
    id = songId,
    name = name,
    artists = listOf(Artist(name = artist)),
    album = Album(name = album, picUrl = picUrl),
    dt = durationMs,
    source = source,
    token = token,
)