package com.qmusic.ui.player

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.qmusic.data.MusicRepository
import com.qmusic.player.PlayerController
import com.qmusic.player.PlayerUiState
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class PlayerViewModel(
    private val playerController: PlayerController,
    private val repository: MusicRepository,
) : ViewModel() {

    /** 合并播放状态与收藏状态，实时反映「是否喜欢当前歌曲」 */
    val uiState: StateFlow<PlayerUiState> = combine(
        playerController.uiState,
        repository.observeFavorites(),
    ) { player, favorites ->
        player.copy(isFavorite = favorites.any { it.songId == player.song?.id })
    }.stateIn(
        viewModelScope,
        SharingStarted.Eagerly,
        playerController.uiState.value,
    )

    fun toggle() = playerController.toggle()

    fun next() = playerController.next()

    fun prev() = playerController.prev()

    /** 播放队列中指定位置的歌曲 */
    fun playAt(index: Int) = playerController.playAt(index)

    fun seekTo(ms: Long) = playerController.seekTo(ms)

    /** 循环切换播放模式（顺序播放 / 随机播放 / 单曲循环） */
    fun cyclePlayMode() = playerController.cyclePlayMode()

    fun toggleFavorite() {
        val state = uiState.value
        val song = state.song ?: return
        viewModelScope.launch {
            if (state.isFavorite) {
                repository.removeFavorite(song.id)
            } else {
                repository.addFavorite(song)
            }
        }
    }
}