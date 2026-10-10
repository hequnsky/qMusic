package com.qmusic.player

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.Intent
import com.qmusic.MainActivity
import com.qmusic.QMusicApp

/**
 * 桌面小组件：展示当前歌曲与控制按钮，事件转发给 PlayerController。
 *
 * 修复要点：
 * 1. 播放/暂停在「当前没有加载任何歌曲」时改为打开 App，避免点了没反应；
 * 2. onUpdate / onEnabled 都主动刷新一次，保证添加小组件后立刻显示正确状态；
 * 3. 使用 applicationContext 获取控制器，避免持有 Activity 上下文。
 */
class WidgetPlayerProvider : AppWidgetProvider() {

    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        controller(context)?.updateWidget()
    }

    override fun onEnabled(context: Context) {
        super.onEnabled(context)
        controller(context)?.updateWidget()
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        val controller = controller(context)
        when (intent.action) {
            ACTION_WIDGET_PLAY_PAUSE -> {
                val state = controller?.uiState?.value
                if (controller == null || state?.song == null) {
                    openApp(context)
                } else {
                    controller.toggle()
                }
            }
            ACTION_WIDGET_NEXT -> {
                val state = controller?.uiState?.value
                if (controller == null || state?.song == null) openApp(context) else controller.next()
            }
            ACTION_WIDGET_PREV -> {
                val state = controller?.uiState?.value
                if (controller == null || state?.song == null) openApp(context) else controller.prev()
            }
        }
    }

    private fun controller(context: Context): PlayerController? =
        (context.applicationContext as? QMusicApp)?.playerController

    private fun openApp(context: Context) {
        runCatching {
            context.startActivity(
                Intent(context, MainActivity::class.java)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
            )
        }
    }

    companion object {
        const val ACTION_WIDGET_PLAY_PAUSE = "com.qmusic.widget.PLAY_PAUSE"
        const val ACTION_WIDGET_NEXT = "com.qmusic.widget.NEXT"
        const val ACTION_WIDGET_PREV = "com.qmusic.widget.PREV"
    }
}
