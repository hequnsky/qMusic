package com.qmusic.player

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.os.IBinder
import com.qmusic.QMusicApp

/** 播放前台服务：保证后台播放，并提供通知栏控制 */
class PlayerService : Service() {

    companion object {
        const val CHANNEL_ID = "qmusic_playback"
        const val NOTIFICATION_ID = 1
        const val ACTION_PLAY_PAUSE = "com.qmusic.action.PLAY_PAUSE"
        const val ACTION_NEXT = "com.qmusic.action.NEXT"
        const val ACTION_PREV = "com.qmusic.action.PREV"
        const val ACTION_STOP = "com.qmusic.action.STOP"
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        createChannel()
        controller().attachService(this)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val controller = controller()
        when (intent?.action) {
            ACTION_PLAY_PAUSE -> controller.toggle()
            ACTION_NEXT -> controller.next()
            ACTION_PREV -> controller.prev()
            ACTION_STOP -> {
                controller.stop()
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
            }
            else -> {
                if (controller.uiState.value.song != null) {
                    controller.updateNotification()
                } else {
                    stopSelf()
                }
            }
        }
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        controller().detachService(this)
        super.onDestroy()
    }

    private fun controller(): PlayerController =
        (application as QMusicApp).playerController

    private fun createChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            "音乐播放",
            NotificationManager.IMPORTANCE_LOW,
        ).apply {
            description = "控制音乐播放、显示当前歌曲"
        }
        getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }
}