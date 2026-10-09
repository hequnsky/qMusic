package com.qmusic.ui.playlist

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.qmusic.data.MusicRepository
import com.qmusic.data.model.PlaylistDetail
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** 歌单详情页 UI 状态 */
data class PlaylistDetailUiState(
    val loading: Boolean = false,
    val detail: PlaylistDetail? = null,
    val error: String? = null,
)

class PlaylistDetailViewModel(
    private val playlistId: Long,
    private val repository: MusicRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(PlaylistDetailUiState())
    val uiState: StateFlow<PlaylistDetailUiState> = _uiState.asStateFlow()

    init {
        refresh()
    }

    fun refresh() {
        if (playlistId <= 0) {
            _uiState.value = PlaylistDetailUiState(
                loading = false,
                error = "歌单 ID 无效",
            )
            return
        }
        viewModelScope.launch {
            _uiState.update { it.copy(loading = true, error = null) }
            try {
                val detail = repository.getPlaylistDetail(playlistId)
                if (detail == null) {
                    _uiState.value = PlaylistDetailUiState(error = "歌单不存在或已下架")
                } else {
                    _uiState.value = PlaylistDetailUiState(loading = false, detail = detail)
                }
            } catch (e: Exception) {
                android.util.Log.e("PlaylistDetail", "加载歌单失败 id=$playlistId", e)
                _uiState.value = PlaylistDetailUiState(error = "加载失败，请确认音乐服务已启动")
            }
        }
    }
}