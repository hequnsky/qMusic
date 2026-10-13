package com.qmusic.ui.video

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.qmusic.data.MusicRepository
import com.qmusic.data.model.VideoData
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class VideoFeedUiState(
    val videos: List<VideoData> = emptyList(),
    val loading: Boolean = true,
    val error: String? = null,
)

/**
 * 类抖音视频信息流 ViewModel。
 * 每次进入页面拉取一批推荐视频，上滑接近底部时追加下一页。
 */
class VideoFeedViewModel(private val repository: MusicRepository) : ViewModel() {

    private val _uiState = MutableStateFlow(VideoFeedUiState())
    val uiState: StateFlow<VideoFeedUiState> = _uiState.asStateFlow()

    private var loadedOffset = 0

    init {
        refresh()
    }

    fun refresh() {
        viewModelScope.launch {
            _uiState.update { it.copy(loading = true, error = null) }
            try {
                val videos = repository.getVideoTimeline(0)
                loadedOffset = videos.size
                _uiState.value = VideoFeedUiState(videos = videos, loading = false)
            } catch (e: Exception) {
                android.util.Log.e("VideoFeedViewModel", "加载推荐视频失败", e)
                _uiState.value = VideoFeedUiState(loading = false, error = "加载失败，请确认音乐服务已启动")
            }
        }
    }

    /** 上滑接近底部时追加下一页 */
    fun loadMore() {
        if (_uiState.value.loading) return
        viewModelScope.launch {
            try {
                val more = repository.getVideoTimeline(loadedOffset)
                if (more.isEmpty()) return@launch
                loadedOffset += more.size
                _uiState.update { it.copy(videos = it.videos + more, loading = false) }
            } catch (_: Exception) {
                // 静默失败，下次滑动再试
            }
        }
    }
}