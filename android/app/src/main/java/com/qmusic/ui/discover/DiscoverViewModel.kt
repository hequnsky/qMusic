package com.qmusic.ui.discover

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.qmusic.data.MusicRepository
import com.qmusic.data.local.ServerConfig
import com.qmusic.data.model.Playlist
import com.qmusic.data.model.Song
import com.qmusic.data.remote.ApiClient
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
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

    /** 当前在跑的刷新任务：新的一次刷新先取消上一次，避免旧请求的结果回写覆盖新结果 */
    private var refreshJob: Job? = null

    /** 已消费的地址版本号：新建的 ViewModel 不会因为收到历史版本而白刷一次 */
    private var consumedUrlVersion = ServerConfig.baseUrlVersion.value

    init {
        refresh()
        // 服务地址被自动修复（启动自愈）或用户手动切换后自动重新加载一次，
        // 否则页面会僵在「加载失败」，用户以为没修好。
        viewModelScope.launch {
            ServerConfig.baseUrlVersion.collect { version ->
                if (version > consumedUrlVersion) {
                    consumedUrlVersion = version
                    heroPool = emptyList()
                    refresh()
                }
            }
        }
    }

    /**
     * @param retryOnAddressChange 请求失败且期间地址被换掉时是否自动重试一次。
     *        只允许重试一层，防止两个地址都不通时无限循环。
     */
    fun refresh(retryOnAddressChange: Boolean = true) {
        // 先取消上一次：否则「旧地址的请求」会在 5s 后超时失败，
        // 把「新地址已经加载成功」的界面重新打回「加载失败」——
        // 这正是移动数据下启动自愈成功后页面仍报错的根因。
        refreshJob?.cancel()
        refreshJob = viewModelScope.launch {
            _uiState.update { it.copy(loading = true, error = null) }
            val urlAtStart = ApiClient.baseUrl
            try {
                if (heroPool.isEmpty()) {
                    // 首次进页面偶发拿到空列表（网易云偶发返回空 result），
                    // 不重试的话「每日推荐」会一直停在占位文案上，重试一次便宜又有效。
                    heroPool = runCatching { repository.getPersonalizedPlaylists(10) }.getOrDefault(emptyList())
                    if (heroPool.isEmpty()) {
                        delay(800)
                        heroPool = runCatching { repository.getPersonalizedPlaylists(10) }.getOrDefault(emptyList())
                    }
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
            } catch (e: CancellationException) {
                // 被更新的一次刷新主动取消：保持现状，绝不能写成失败态
                throw e
            } catch (e: Exception) {
                Log.e("DiscoverViewModel", "刷新发现页失败 urlAtStart=$urlAtStart", e)
                if (retryOnAddressChange && ApiClient.baseUrl != urlAtStart) {
                    // 地址在请求途中被自愈换掉了，这次失败没有参考价值，立刻用新地址重试。
                    // 先把 job 置空，否则 refresh() 里的 cancel 会取消掉自己。
                    refreshJob = null
                    refresh(retryOnAddressChange = false)
                    return@launch
                }
                _uiState.value = DiscoverUiState(
                    loading = false,
                    error = "加载失败，请确认音乐服务已启动",
                )
            }
        }
    }
}
