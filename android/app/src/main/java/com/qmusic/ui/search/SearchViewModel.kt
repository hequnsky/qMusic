package com.qmusic.ui.search

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.qmusic.data.MusicRepository
import com.qmusic.data.local.SearchHistoryStore
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

    private val _query = MutableStateFlow("")
    val query: StateFlow<String> = _query.asStateFlow()

    private val _results = MutableStateFlow<List<Song>>(emptyList())
    val results: StateFlow<List<Song>> = _results.asStateFlow()

    private val _searching = MutableStateFlow(false)
    val searching: StateFlow<Boolean> = _searching.asStateFlow()

    private val _history = MutableStateFlow<List<String>>(emptyList())
    val history: StateFlow<List<String>> = _history.asStateFlow()

    private val _hotKeywords = MutableStateFlow<List<String>>(emptyList())
    val hotKeywords: StateFlow<List<String>> = _hotKeywords.asStateFlow()

    private var searchJob: Job? = null

    init {
        _history.value = SearchHistoryStore.get(getApplication())
        loadHotKeywords()
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
            _searching.value = false
            return
        }
        searchJob = viewModelScope.launch {
            delay(300) // 防抖
            _searching.value = true
            _results.value = try {
                repository.search(value, 30)
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
                repository.search(kw, 30)
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
}