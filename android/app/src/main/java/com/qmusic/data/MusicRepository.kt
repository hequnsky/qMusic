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
import com.qmusic.data.model.HotDetailItem
import com.qmusic.data.model.LoginCellphoneResponse
import com.qmusic.data.model.Toplist
import com.qmusic.data.model.UserDetailProfile
import com.qmusic.data.model.UserProfile
import com.qmusic.data.model.LyricLine
import com.qmusic.data.model.MusicSource
import com.qmusic.data.model.Playlist
import com.qmusic.data.model.VideoData
import com.qmusic.data.model.PlaylistDetail
import com.qmusic.data.model.SodaCharts
import com.qmusic.data.model.SodaPlaylist
import com.qmusic.data.model.SodaPlaylistListResponse
import com.qmusic.data.model.SodaRadio
import com.qmusic.data.model.SodaToplist
import com.qmusic.data.model.Song
import com.qmusic.data.remote.ApiClient
import com.qmusic.data.remote.MusicApiService
import com.qmusic.util.LrcParser
import kotlinx.coroutines.flow.Flow
import kotlinx.serialization.json.Json
import retrofit2.Response

/** 数据仓库：统一聚合远程音乐接口与本地收藏/历史 */
class MusicRepository(
    private val favoriteDao: FavoriteDao,
    private val historyDao: HistoryDao,
    private val playlistDao: PlaylistDao,
) {

    /**
     * 每次访问都取**当前生效**的 Retrofit service。
     *
     * 不能用构造时注入的实例：启动自愈（或用户手动切换地址）会重建 service，
     * 而注入进来的旧实例仍然指向那个失效地址，表现为「地址明明已经切好了，
     * 请求却还打进旧地址」——首页永远报「加载失败」，用户以为没修好。
     */
    private val api: MusicApiService
        get() = ApiClient.service

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
            return searchNetease(keywords, limit)
        }
        return try {
            val resp = api.multiSearch(source, keywords, limit)
            // 多音源同样做「原版优先」，否则切到 QQ/酷狗 后 DJ 版依旧霸屏
            if (resp.ok && resp.songs.isNotEmpty()) resp.songs.map { it.toSong() }.preferOriginals()
            else searchNetease(keywords, limit)
        } catch (e: Exception) {
            searchNetease(keywords, limit)
        }
    }

    /** 网易云搜索 + 封面回填 + 原版优先排序（三条路径共用） */
    private suspend fun searchNetease(keywords: String, limit: Int): List<Song> =
        enrichCovers(api.search(keywords, limit).result.songs).preferOriginals()

    /**
     * 二次创作版本（DJ 版 / 翻唱 / 加速版…）识别关键词。
     *
     * 现象：用热搜词搜索时，结果里夹杂大量「xxx(1.1xDJ版)」「xxx 翻唱」，
     * 原版反而被挤到后面（用户反馈的「搜索以 DJ 为主」即由此而来）。
     * 这里把这类版本整体后移，不删除、只是降级排序。
     */
    private val reworkKeywords = listOf("dj", "remix", "翻唱", "加速", "慢摇", "抖音", "电音版")

    /**
     * 原版优先：稳定排序把二次创作版本排到后面。
     * `sortedBy` 是稳定排序，同组内保持接口返回的原始相对顺序，不会打乱相关度。
     */
    private fun List<Song>.preferOriginals(): List<Song> {
        if (size <= 1) return this
        return sortedBy { song ->
            val name = song.name.lowercase()
            if (reworkKeywords.any { name.contains(it) }) 1 else 0
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

    /** 完整热搜榜（/search/hot/detail，约 20 条，带热度值）——「搜索页 → 热搜榜 → 更多」使用 */
    suspend fun getHotSearchDetails(limit: Int = 30): List<HotDetailItem> =
        api.searchHotDetail().data.take(limit)

    suspend fun getSearchSuggest(keywords: String, limit: Int = 6): List<Song> =
        enrichCovers(api.searchSuggest(keywords).result.songs.take(limit).map { it.toSong() })

    suspend fun getToplists(): List<Toplist> = api.toplist().list

    // ---------- 网易云账号登录 ----------

    /**
     * 手机号登录（网易云 weapi，明文密码由服务端 MD5）。
     * 成功返回 cookie；失败抛 IllegalStateException（优先带网易云返回的错误信息，
     * 如「账号或密码错误」——它在 errorBody 里，非 2xx 时 body() 为 null）。
     */
    suspend fun loginCellphone(phone: String, password: String): String {
        val resp = api.loginCellphone(phone.trim(), password)
        var cookie = resp.body()?.cookie ?: ""
        if (cookie.isBlank()) {
            cookie = resp.headers()["Set-Cookie"] ?: resp.raw().header("Set-Cookie") ?: ""
        }
        val body = resp.body()
        if (resp.isSuccessful && body != null && body.code == 200 && cookie.isNotBlank()) {
            return cookie
        }
        // 失败原因：先看 2xx body.message，再解析 errorBody（网易云把「账号或密码错误」放在这里）
        val msg = body?.message?.takeIf { it.isNotBlank() }
            ?: resp.errorBody()?.string()?.let { raw ->
                try {
                    Json { ignoreUnknownKeys = true }
                        .decodeFromString<LoginCellphoneResponse>(raw).message
                        .takeIf { it.isNotBlank() }
                } catch (_: Exception) {
                    null
                }
            }
            ?: "登录失败（HTTP ${resp.code()}）"
        throw IllegalStateException(msg)
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
        val lines = if (song.source != Song.SOURCE_NETEASE && !token.isNullOrBlank()) {
            val lrc = api.multiLyric(token).lrc
            if (lrc.isNotBlank()) LrcParser.parse(lrc) else getLyric(song.id)
        } else {
            getLyric(song.id)
        }
        return stripTitleArtistHeader(lines, song)
    }

    /**
     * 剔除歌词开头混进来的「歌名 / 歌手名」行。
     *
     * 部分音源会把 `[00:00.00]歌名` / `[00:02.00]歌手` 这种信息也写进歌词正文，
     * 它们会作为普通歌词参与时间匹配 —— 表现就是开头白占两行，
     * 而且歌手名会被高亮成「正在唱的那句」。这里按归一化文本比对开头几行剔除。
     */
    private fun stripTitleArtistHeader(lines: List<LyricLine>, song: Song): List<LyricLine> {
        if (lines.isEmpty()) return lines
        val title = song.name.normalizeForMatch()
        val artist = song.artistName.normalizeForMatch()
        if (title.isEmpty() && artist.isEmpty()) return lines
        var drop = 0
        for (line in lines.take(3)) {
            val text = line.content.normalizeForMatch()
            if (text.isNotEmpty() && (text == title || text == artist)) drop++ else break
        }
        // 至少留一行，避免整首歌词被误删干净
        return if (drop in 1 until lines.size) lines.drop(drop) else lines
    }

    /**
     * 每日推荐歌单**池**。
     *
     * 为什么一次取多个：网易云 /personalized 在同一账号/IP 下返回顺序是**固定**的
     * （实测连续 5 次首个歌单完全一致），所以「换一换」若每次都取 firstOrNull()
     * 等于什么都没换。这里一次取回一批，由上层在本地轮换/随机，
     * 既保证每次点击都有变化，也省掉重复请求。
     */
    suspend fun getPersonalizedPlaylists(limit: Int = 10): List<Playlist> =
        api.personalized(limit).result

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

    /**
     * 歌单 / 榜单详情。
     *
     * 汽水音乐榜单（id 落在 900001~900999）走 /soda 接口，其余仍走网易云歌单接口——
     * 这样榜单详情页、播放全部、收藏等下游逻辑一行都不用改。
     */
    suspend fun getPlaylistDetail(id: Long): PlaylistDetail? {
        if (SodaCharts.isSodaChartId(id)) return getSodaChartDetail(id)
        if (SodaCharts.isSodaPlaylistId(id)) return getSodaPlaylistDetail(id)
        return api.playlistDetail(id).playlist.takeIf { it.id != 0L }
    }

    // ---------------- 汽水音乐榜单 ----------------

    /** 汽水官方榜单目录；失败返回空表（不阻塞首页其它区块） */
    suspend fun getSodaToplists(): List<SodaToplist> = try {
        api.sodaToplist().list
    } catch (e: Exception) {
        emptyList()
    }

    /** 指定汽水榜单的歌曲（带可播放 token） */
    suspend fun getSodaChartSongs(id: Long, limit: Int = 0): List<Song> = try {
        api.sodaChart(id, limit).songs.map { it.toSong() }
    } catch (e: Exception) {
        emptyList()
    }

    /** 首页推荐区：汽水热歌榜前 N 首 */
    suspend fun getSodaHotSongs(limit: Int = 12): List<Song> = try {
        api.sodaHot(limit).songs.map { it.toSong() }
    } catch (e: Exception) {
        emptyList()
    }

    /** 把汽水榜单包装成与网易云歌单一致的详情结构 */
    private suspend fun getSodaChartDetail(id: Long): PlaylistDetail? {
        val resp = try {
            api.sodaChart(id)
        } catch (e: Exception) {
            return null
        }
        val info = resp.chart ?: return null
        val songs = resp.songs.map { it.toSong() }
        if (songs.isEmpty()) return null
        return PlaylistDetail(
            id = id,
            name = info.name,
            coverImgUrl = info.coverImgUrl,
            trackCount = if (info.trackCount > 0) info.trackCount else songs.size,
            playCount = 0,
            tracks = songs,
        )
    }

    // ---------------- 汽水音乐歌单 / 电台 ----------------

    /**
     * 汽水精选歌单。服务端把发现流累积成池子（60+ 个），
     * offset 用于「换一批」，失败返回空表不阻塞首页。
     */
    suspend fun getSodaPlaylists(limit: Int = 24, offset: Int = 0): List<SodaPlaylist> = try {
        api.sodaPlaylists(limit, offset).list
    } catch (e: Exception) {
        emptyList()
    }

    /** 带总量的分页版本：歌单广场要显示「共 N 个」并判断是否还有下一页 */
    suspend fun getSodaPlaylistPage(limit: Int = 30, offset: Int = 0): SodaPlaylistListResponse =
        api.sodaPlaylists(limit, offset)

    /** 汽水歌单详情 → 与网易云歌单一致的结构（详情页/播放全部均无需改动） */
    private suspend fun getSodaPlaylistDetail(id: Long): PlaylistDetail? {
        val resp = try {
            api.sodaPlaylist(id)
        } catch (e: Exception) {
            return null
        }
        val songs = resp.songs.map { it.toSong() }
        if (songs.isEmpty()) return null
        val info = resp.playlist
        return PlaylistDetail(
            id = id,
            name = info?.name?.ifBlank { null } ?: "汽水歌单",
            coverImgUrl = info?.coverImgUrl ?: songs.first().coverUrl,
            trackCount = info?.trackCount?.takeIf { it > 0 } ?: songs.size,
            playCount = info?.playCount ?: 0,
            tracks = songs,
        )
    }

    /** 汽水电台目录；失败返回空表 */
    suspend fun getSodaRadios(): List<SodaRadio> = try {
        api.sodaRadios().list
    } catch (e: Throwable) {
        // 用 Throwable 而不是 Exception：解析层偶尔抛的是 Error（如 NoClassDefFoundError），
        // 只捕 Exception 会让「电台区整体消失」且没有一行日志，排查时完全看不到线索。
        android.util.Log.w("MusicRepository", "getSodaRadios 失败: ${e.message}")
        emptyList()
    }

    /** 电台歌曲：用于「点电台即播」，不需要单独的详情页 */
    suspend fun getSodaRadioSongs(id: Long, limit: Int = 30): List<Song> = try {
        api.sodaRadio(id, limit).songs.map { it.toSong() }
    } catch (e: Exception) {
        emptyList()
    }

    // ---------------- 视频（类抖音信息流） ----------------

    /** 获取推荐视频信息流 */
    suspend fun getVideoTimeline(offset: Int = 0): List<VideoData> =
        api.videoTimelineRecommend(offset).datas.mapNotNull { it.data }

    /** 获取视频播放地址（/video/url） */
    suspend fun getVideoUrl(videoId: String): String? =
        api.videoUrl(videoId).urls.firstOrNull { !it.url.isBlank() }?.url

    // ---------- 网易云歌单导入（需登录） ----------

    /**
     * 歌单导入 - 文字导入。
     *
     * @param text 每行「歌名 歌手」的纯文本
     * @param playlistName 生成的歌单名（默认网易云自动取名）
     * @param cookie 登录态
     * @return 任务 id（用于轮询状态）
     */
    suspend fun importPlaylistByText(
        text: String,
        playlistName: String? = null,
        cookie: String,
    ): String? {
        val resp = api.playlistImportCreate(
            text = text,
            playlistName = playlistName,
            cookie = cookie,
        )
        return resp.data?.id ?: resp.data?.taskId
    }

    /**
     * 歌单导入 - 链接导入。
     *
     * @param links 外部歌单分享链接（QQ音乐等）
     * @param playlistName 生成的歌单名
     * @param cookie 登录态
     * @return 任务 id
     */
    suspend fun importPlaylistByLink(
        links: List<String>,
        playlistName: String? = null,
        cookie: String,
    ): String? {
        // 链接数组编码成 JSON 数组字符串（["url1","url2"]），网易云接口要求 link 参数是这种格式
        val encoded = buildString {
            append('[')
            links.forEachIndexed { i, url ->
                if (i > 0) append(',')
                append('"').append(url.replace("\\", "\\\\").replace("\"", "\\\"")).append('"')
            }
            append(']')
        }
        val resp = api.playlistImportCreate(
            link = encoded,
            playlistName = playlistName,
            cookie = cookie,
        )
        return resp.data?.id ?: resp.data?.taskId
    }

    /**
     * 歌单导入 - 轮询任务状态。
     *
     * @return (status, message, playlistId)。status: 0=完成 1=进行中 2=失败等（以网易云返回为准）
     */
    suspend fun importPlaylistStatus(id: String, cookie: String): Triple<Int, String, Long> {
        val resp = api.playlistImportStatus(id, cookie)
        val d = resp.data
        return Triple(d?.status ?: -1, d?.message ?: resp.message, d?.playlistId ?: 0L)
    }

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

/**
 * 文本归一化，用于「歌词行 == 歌名/歌手」这类模糊比对：
 * 去掉空格与标点、统一小写，于是 `Blowin' in the Wind` 与 `Blowin in The Wind` 视为同一串。
 */
private fun String.normalizeForMatch(): String = lowercase().filter { it.isLetterOrDigit() }