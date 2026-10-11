package com.qmusic

import android.app.Application
import com.qmusic.data.MusicRepository
import com.qmusic.data.local.AppDatabase
import com.qmusic.data.local.ServerConfig
import com.qmusic.data.remote.ApiClient
import com.qmusic.player.PlayerController

/** 应用入口：轻量依赖容器 */
class QMusicApp : Application() {

    lateinit var repository: MusicRepository
        private set

    lateinit var playerController: PlayerController
        private set

    override fun onCreate() {
        super.onCreate()
        // 应用用户保存的服务器地址（默认 10.0.2.2:3000，真机可切换局域网 IP）
        ApiClient.updateBaseUrl(ServerConfig.getBaseUrl(this))
        val database = AppDatabase.get(this)
        repository = MusicRepository(
            api = ApiClient.service,
            favoriteDao = database.favoriteDao(),
            historyDao = database.historyDao(),
            playlistDao = database.playlistDao(),
        )
        playerController = PlayerController(this, repository)
    }
}