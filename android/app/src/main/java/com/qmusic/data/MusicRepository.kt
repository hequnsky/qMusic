package com.qmusic.data

import com.qmusic.data.local.FavoriteDao
import com.qmusic.data.local.HistoryDao
import com.qmusic.data.local.entity.Favorite
import com.qmusic.data.local.entity.History
import com.qmusic.data.model.Comment
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

    suspend fun getSongUrl(id: Long): String? =
        api.songUrl(id).data.firstOrNull { !it.url.isNullOrBlank() }?.url

    suspend fun getLyric(id: Long): List<LyricLine> =
        LrcParser.parse(api.lyric(id).lrc?.lyric.orEmpty())

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