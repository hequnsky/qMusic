package com.qmusic.ui.playlist

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.qmusic.data.MusicRepository
import com.qmusic.data.model.Song
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** 本地歌单 ViewModel：歌单增删改 + 歌曲增删 */
class PlaylistViewModel(
    private val repository: MusicRepository,
) : ViewModel() {

    /** 全部歌单（含歌曲数） */
    val playlists: StateFlow<List<com.qmusic.data.local.entity.PlaylistWithCount>> =
        repository.observePlaylists().stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(5_000),
            emptyList(),
        )

    /** 当前歌曲已加入的歌单 id 列表 */
    private val _songPlaylistIds = MutableStateFlow<List<Long>>(emptyList())
    val songPlaylistIds: StateFlow<List<Long>> = _songPlaylistIds.asStateFlow()

    fun refreshSongPlaylists(songId: Long) {
        viewModelScope.launch {
            _songPlaylistIds.value = repository.playlistIdsOfSong(songId)
        }
    }

    fun addToPlaylist(playlistId: Long, song: Song) {
        viewModelScope.launch {
            repository.addSongToPlaylist(playlistId, song)
            _songPlaylistIds.value = repository.playlistIdsOfSong(song.id)
        }
    }

    fun removeFromPlaylist(playlistId: Long, song: Song) {
        viewModelScope.launch {
            repository.removeSongFromPlaylist(playlistId, song.id)
            _songPlaylistIds.value = repository.playlistIdsOfSong(song.id)
        }
    }

    fun createPlaylistWithSong(name: String, song: Song) {
        viewModelScope.launch {
            repository.createPlaylistWithSong(name, song)
            _songPlaylistIds.value = repository.playlistIdsOfSong(song.id)
        }
    }
}
