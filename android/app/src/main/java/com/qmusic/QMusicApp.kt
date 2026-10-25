package com.qmusic

import android.app.Application
import com.qmusic.data.MusicRepository
import com.qmusic.data.local.AppDatabase
import com.qmusic.data.local.ServerConfig
import com.qmusic.data.remote.ApiClient
import com.qmusic.player.PlayerController
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/** 应用入口：轻量依赖容器 */
class QMusicApp : Application() {

    /** 应用级作用域：承载不与界面生命周期绑定的后台任务（如启动自愈） */
    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    lateinit var repository: MusicRepository
        private set

    lateinit var playerController: PlayerController
        private set

    override fun onCreate() {
        super.onCreate()
        // 应用用户保存的服务器地址（debug 默认 127.0.0.1，真机可切局域网 IP / 线上域名）
        ApiClient.updateBaseUrl(ServerConfig.getBaseUrl(this))

        // 启动自愈：prefs 会随覆盖安装保留，若里面存的是已被回收的隧道域名或失效的
        // 局域网 IP，App 每次启动都会拿这个死地址发请求 → 表现为「连不上接口」。
        // 这里在后台探测一次，不通就自动换成可用地址（通了则完全不干预）。
        appScope.launch {
            ServerConfig.healIfUnreachable(this@QMusicApp)
        }

        val database = AppDatabase.get(this)
        repository = MusicRepository(
            favoriteDao = database.favoriteDao(),
            historyDao = database.historyDao(),
            playlistDao = database.playlistDao(),
        )
        playerController = PlayerController(this, repository)
    }
}
