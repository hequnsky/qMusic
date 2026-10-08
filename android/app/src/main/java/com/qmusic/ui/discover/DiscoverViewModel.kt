package com.qmusic.ui.discover

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.qmusic.data.MusicRepository
import com.qmusic.data.model.Playlist
import com.qmusic.data.model.Song
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class DiscoverUiState(
    val hero: Playlist? = null,
    val playlists: List<Playlist> = emptyList(),
    val songs: List<Song> = emptyList(),
    val loading: Boolean = true,
    val error: String? = null,
)

class DiscoverViewModel(private val repository: MusicRepository) : ViewModel() {

    private val _uiState = MutableStateFlow(DiscoverUiState())
    val uiState: StateFlow<DiscoverUiState> = _uiState.asStateFlow()

    init {
        refresh()
    }

    fun refresh() {
        viewModelScope.launch {
            _uiState.update { it.copy(loading = true, error = null) }
            try {
                val hero = repository.getPersonalizedPlaylist()
                val playlists = repository.getTopPlaylists(10)
                val songs = if (hero != null) {
                    repository.getPlaylistDetail(hero.id)?.tracks?.take(20) ?: emptyList()
                } else {
                    emptyList()
                }
                _uiState.value = DiscoverUiState(
                    hero = hero,
                    playlists = playlists,
                    songs = songs,
                    loading = false,
                )
            } catch (e: Exception) {
                android.util.Log.e("DiscoverViewModel", "刷新发现页失败", e)
                _uiState.value = DiscoverUiState(
                    loading = false,
                    error = "加载失败，请确认音乐服务已启动",
                )
            }
        }
    }
}