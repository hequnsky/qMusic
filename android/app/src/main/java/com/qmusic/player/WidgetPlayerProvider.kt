package com.qmusic.player

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.Intent

/** 桌面小组件：展示当前歌曲与控制按钮，事件转发给 PlayerController */
class WidgetPlayerProvider : AppWidgetProvider() {

    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        (context.applicationContext as? com.qmusic.QMusicApp)?.playerController?.updateWidget()
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        val controller = (context.applicationContext as? com.qmusic.QMusicApp)?.playerController ?: return
        when (intent.action) {
            ACTION_WIDGET_PLAY_PAUSE -> controller.toggle()
            ACTION_WIDGET_NEXT -> controller.next()
            ACTION_WIDGET_PREV -> controller.prev()
        }
    }

    companion object {
        const val ACTION_WIDGET_PLAY_PAUSE = "com.qmusic.widget.PLAY_PAUSE"
        const val ACTION_WIDGET_NEXT = "com.qmusic.widget.NEXT"
        const val ACTION_WIDGET_PREV = "com.qmusic.widget.PREV"
    }
}