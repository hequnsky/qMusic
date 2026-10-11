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

    /**
     * 推荐歌单池。
     *
     * 网易云 /personalized 在同一账号/IP 下返回顺序固定，所以「换一换」不能靠重新请求，
     * 必须在本地换一个——这里缓存一批，每次 refresh 随机切换到一个**不同于当前**的歌单。
     */
    private var heroPool: List<Playlist> = emptyList()

    init {
        refresh()
    }

    fun refresh() {
        viewModelScope.launch {
            _uiState.update { it.copy(loading = true, error = null) }
            try {
                if (heroPool.isEmpty()) {
                    heroPool = repository.getPersonalizedPlaylists(10)
                }
                val currentId = _uiState.value.hero?.id
                // 优先换到另一个歌单；池里只有一个（或首次）时就用第一个
                val next = heroPool.filter { it.id != currentId }.randomOrNull()
                    ?: heroPool.firstOrNull()

                val playlists = repository.getTopPlaylists(10)
                val songs = next
                    ?.let { repository.getPlaylistDetail(it.id)?.tracks?.take(20) }
                    .orEmpty()

                _uiState.value = DiscoverUiState(
                    hero = next,
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
