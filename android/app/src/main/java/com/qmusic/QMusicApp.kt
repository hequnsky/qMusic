package com.qmusic

import android.app.Application
import com.qmusic.data.MusicRepository
import com.qmusic.data.local.AppDatabase
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
        val database = AppDatabase.get(this)
        repository = MusicRepository(
            api = ApiClient.service,
            favoriteDao = database.favoriteDao(),
            historyDao = database.historyDao(),
        )
        playerController = PlayerController(this, repository)
    }
}