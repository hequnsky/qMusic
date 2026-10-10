package com.qmusic.ui.mine

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.qmusic.data.MusicRepository
import com.qmusic.data.local.LoginStore
import com.qmusic.data.local.entity.Favorite
import com.qmusic.data.local.entity.History
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class LoginUiState(
    val loggedIn: Boolean = false,
    val nickname: String = "",
    val avatarUrl: String? = null,
    val follows: Long = 0,
    val followeds: Long = 0,
    val eventCount: Long = 0,
)

class MineViewModel(
    private val repository: MusicRepository,
    application: Application,
) : AndroidViewModel(application) {

    val favorites: StateFlow<List<Favorite>> = repository.observeFavorites()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val history: StateFlow<List<History>> = repository.observeHistory()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val _loginState = MutableStateFlow(LoginUiState())
    val loginState: StateFlow<LoginUiState> = _loginState.asStateFlow()

    init {
        refreshLoginState()
    }

    /** 从本地存储读取登录状态 */
    fun refreshLoginState() {
        val app = getApplication<Application>()
        if (!LoginStore.isLoggedIn(app)) {
            _loginState.value = LoginUiState()
            return
        }
        val u = LoginStore.getUser(app)
        _loginState.value = LoginUiState(
            loggedIn = true,
            nickname = u.nickname,
            avatarUrl = u.avatarUrl,
            follows = u.follows,
            followeds = u.followeds,
            eventCount = u.eventCount,
        )
    }

    /** 登录成功后保存用户资料并更新状态 */
    fun applyLogin(cookie: String, userId: Long) {
        viewModelScope.launch {
            try {
                val profile = repository.getLoginAccount(cookie) ?: return@launch
                val detail = repository.getLoginUserDetail(userId, cookie)
                if (detail != null) {
                    LoginStore.saveLogin(getApplication(), cookie, detail)
                } else {
                    LoginStore.saveLogin(
                        getApplication(),
                        cookie,
                        com.qmusic.data.model.UserDetailProfile(
                            userId = profile.userId,
                            nickname = profile.nickname,
                            avatarUrl = profile.avatarUrl,
                        ),
                    )
                }
            } catch (_: Exception) {
            }
            refreshLoginState()
        }
    }

    fun logout() {
        LoginStore.clear(getApplication())
        refreshLoginState()
    }
}