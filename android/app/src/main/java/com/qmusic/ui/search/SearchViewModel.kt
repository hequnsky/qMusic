package com.qmusic.ui.search

import android.app.Application
import android.content.Context
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.qmusic.data.MusicRepository
import com.qmusic.data.local.SearchHistoryStore
import com.qmusic.data.model.MusicSource
import com.qmusic.data.model.Song
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class SearchViewModel(
    private val repository: MusicRepository,
    application: Application,
) : AndroidViewModel(application) {

    private val prefs = application.getSharedPreferences("qmusic_search", Context.MODE_PRIVATE)

    private val _query = MutableStateFlow("")
    val query: StateFlow<String> = _query.asStateFlow()

    private val _results = MutableStateFlow<List<Song>>(emptyList())
    val results: StateFlow<List<Song>> = _results.asStateFlow()

    private val _suggestions = MutableStateFlow<List<Song>>(emptyList())
    val suggestions: StateFlow<List<Song>> = _suggestions.asStateFlow()

    private val _searching = MutableStateFlow(false)
    val searching: StateFlow<Boolean> = _searching.asStateFlow()

    private val _history = MutableStateFlow<List<String>>(emptyList())
    val history: StateFlow<List<String>> = _history.asStateFlow()

    private val _hotKeywords = MutableStateFlow<List<String>>(emptyList())
    val hotKeywords: StateFlow<List<String>> = _hotKeywords.asStateFlow()

    /** 服务端可用的音源（多音源服务不在线时为空，界面自动隐藏切换栏） */
    private val _sources = MutableStateFlow<List<MusicSource>>(emptyList())
    val sources: StateFlow<List<MusicSource>> = _sources.asStateFlow()

    /** 当前音源（持久化，默认网易云，保证老用户行为不变） */
    private val _selectedSource = MutableStateFlow(
        prefs.getString(KEY_SOURCE, Song.SOURCE_NETEASE) ?: Song.SOURCE_NETEASE,
    )
    val selectedSource: StateFlow<String> = _selectedSource.asStateFlow()

    private var searchJob: Job? = null

    init {
        _history.value = SearchHistoryStore.get(getApplication())
        loadHotKeywords()
        loadSources()
    }

    /** 拉取可用音源列表（失败静默降级为「只有网易云」） */
    fun loadSources() {
        viewModelScope.launch {
            val list = try {
                repository.getMusicSources()
            } catch (e: Exception) {
                emptyList()
            }
            _sources.value = list
            // 之前选中的音源若已不可用，回退到网易云
            if (list.isNotEmpty() && list.none { it.id == _selectedSource.value }) {
                selectSource(Song.SOURCE_NETEASE)
            }
        }
    }

    /** 切换音源：切换后立刻用当前关键词重搜 */
    fun selectSource(sourceId: String) {
        if (_selectedSource.value == sourceId) return
        _selectedSource.value = sourceId
        prefs.edit().putString(KEY_SOURCE, sourceId).apply()
        val kw = _query.value.trim()
        if (kw.isNotEmpty()) submitSearch(kw)
    }

    /** 加载真实热门搜索词（/search/hot） */
    fun loadHotKeywords() {
        viewModelScope.launch {
            _hotKeywords.value = try {
                repository.getHotKeywords(10)
            } catch (e: Exception) {
                emptyList()
            }
        }
    }

    fun onQueryChange(value: String) {
        _query.value = value
        searchJob?.cancel()
        if (value.isBlank()) {
            _results.value = emptyList()
            _suggestions.value = emptyList()
            _searching.value = false
            return
        }
        searchJob = viewModelScope.launch {
            delay(300) // 防抖
            _searching.value = true
            _suggestions.value = try {
                repository.getSearchSuggest(value)
            } catch (e: Exception) {
                emptyList()
            }
            _results.value = try {
                repository.search(value, 30, _selectedSource.value)
            } catch (e: Exception) {
                emptyList()
            }
            _searching.value = false
        }
    }

    /** 提交搜索（键盘搜索键 / 点击热搜或历史词）：设置关键词、记录历史并立即搜索 */
    fun submitSearch(keyword: String) {
        val kw = keyword.trim()
        if (kw.isEmpty()) return
        _query.value = kw
        addHistory(kw)
        searchJob?.cancel()
        searchJob = viewModelScope.launch {
            _searching.value = true
            _results.value = try {
                repository.search(kw, 30, _selectedSource.value)
            } catch (e: Exception) {
                emptyList()
            }
            _searching.value = false
        }
    }

    fun addHistory(keyword: String) {
        val app = getApplication<Application>()
        SearchHistoryStore.add(app, keyword)
        _history.value = SearchHistoryStore.get(app)
    }

    fun removeHistory(keyword: String) {
        val app = getApplication<Application>()
        SearchHistoryStore.remove(app, keyword)
        _history.value = SearchHistoryStore.get(app)
    }

    fun clearHistory() {
        val app = getApplication<Application>()
        SearchHistoryStore.clear(app)
        _history.value = emptyList()
    }

    private companion object {
        const val KEY_SOURCE = "selected_source"
    }
}