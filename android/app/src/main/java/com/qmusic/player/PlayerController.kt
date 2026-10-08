package com.qmusic.player

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import coil.imageLoader
import coil.request.ImageRequest
import com.qmusic.MainActivity
import com.qmusic.QMusicApp
import com.qmusic.R
import com.qmusic.data.MusicRepository
import com.qmusic.data.model.LyricLine
import com.qmusic.data.model.Song
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.lang.ref.WeakReference

/** 播放器 UI 状态 */
data class PlayerUiState(
    val song: Song? = null,
    val queue: List<Song> = emptyList(),
    val isPlaying: Boolean = false,
    val positionMs: Long = 0L,
    val durationMs: Long = 0L,
    val lyrics: List<LyricLine> = emptyList(),
    val currentLyricIndex: Int = -1,
    val isFavorite: Boolean = false,
    val error: String? = null,
)

/** 播放控制器：持有 ExoPlayer，向 UI 暴露 StateFlow，并驱动前台服务与通知 */
class PlayerController(
    private val context: Context,
    private val repository: MusicRepository,
) {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    private val _uiState = MutableStateFlow(PlayerUiState())
    val uiState: StateFlow<PlayerUiState> = _uiState.asStateFlow()

    private var player: ExoPlayer? = null
    private var serviceRef: WeakReference<PlayerService>? = null
    private var positionJob: Job? = null

    fun attachService(service: PlayerService) {
        serviceRef = WeakReference(service)
        updateNotification()
    }

    fun detachService(service: PlayerService) {
        if (serviceRef?.get() === service) serviceRef = null
    }

    /** 从队列的 index 位置开始播放 */
    fun play(queue: List<Song>, index: Int) {
        val song = queue.getOrNull(index) ?: return
        _uiState.update {
            it.copy(
                song = song,
                queue = queue,
                isPlaying = false,
                positionMs = 0,
                durationMs = song.durationMs,
                currentLyricIndex = -1,
                error = null,
            )
        }
        scope.launch {
            try {
                val url = repository.getSongUrl(song.id)
                if (url.isNullOrBlank()) {
                    _uiState.update { it.copy(error = "该歌曲暂不支持播放（可能受版权保护）") }
                    return@launch
                }
                repository.addHistory(song)

                val exo = getPlayer()
                val mediaItem = MediaItem.Builder()
                    .setMediaId(song.id.toString())
                    .setUri(url)
                    .setMediaMetadata(
                        MediaMetadata.Builder()
                            .setTitle(song.name)
                            .setArtist(song.artistName)
                            .setArtworkUri(song.coverUrl?.let { Uri.parse(it) })
                            .build(),
                    )
                    .build()
                exo.setMediaItem(mediaItem)
                exo.prepare()
                exo.playWhenReady = true

                _uiState.update { it.copy(durationMs = song.durationMs, isPlaying = true) }
                startForegroundService()
                startPositionLoop()
                loadLyrics(song.id)
            } catch (e: Exception) {
                _uiState.update { it.copy(error = "获取播放地址失败，请检查网络") }
            }
        }
    }

    fun toggle() {
        val exo = player ?: return
        if (exo.isPlaying) exo.pause() else exo.play()
        updateNotification()
    }

    fun next() {
        val state = _uiState.value
        val queue = state.queue
        if (queue.isEmpty()) return
        val idx = queue.indexOfFirst { it.id == state.song?.id }
        val next = if (idx >= 0) (idx + 1) % queue.size else 0
        play(queue, next)
    }

    fun prev() {
        val state = _uiState.value
        val queue = state.queue
        if (queue.isEmpty()) return
        val idx = queue.indexOfFirst { it.id == state.song?.id }
        val prev = if (idx > 0) idx - 1 else queue.lastIndex
        play(queue, prev)
    }

    fun seekTo(ms: Long) {
        player?.seekTo(ms)
    }

    fun stop() {
        positionJob?.cancel()
        player?.stop()
        player?.clearMediaItems()
        _uiState.value = PlayerUiState()
    }

    fun release() {
        positionJob?.cancel()
        player?.release()
        player = null
    }

    private fun getPlayer(): ExoPlayer =
        player ?: ExoPlayer.Builder(context).build().also { exo ->
            player = exo
            exo.addListener(object : Player.Listener {
                override fun onIsPlayingChanged(isPlaying: Boolean) {
                    _uiState.update { it.copy(isPlaying = isPlaying) }
                    updateNotification()
                }

                override fun onPlaybackStateChanged(playbackState: Int) {
                    if (playbackState == Player.STATE_READY) {
                        val d = exo.duration
                        if (d > 0) _uiState.update { it.copy(durationMs = d) }
                    }
                }

                override fun onPlayerError(error: PlaybackException) {
                    _uiState.update {
                        it.copy(error = "播放出错：${error.errorCodeName}", isPlaying = false)
                    }
                }
            })
        }

    private fun startPositionLoop() {
        positionJob?.cancel()
        positionJob = scope.launch {
            while (isActive) {
                val exo = player ?: break
                val pos = exo.currentPosition
                val state = _uiState.value
                val lyricIndex = state.lyrics.indexOfLast { it.timeMs <= pos }
                _uiState.update {
                    it.copy(positionMs = pos, currentLyricIndex = lyricIndex)
                }
                delay(500)
            }
        }
    }

    private fun loadLyrics(songId: Long) {
        scope.launch {
            val lyrics = try {
                repository.getLyric(songId)
            } catch (e: Exception) {
                emptyList()
            }
            _uiState.update { it.copy(lyrics = lyrics, currentLyricIndex = -1) }
        }
    }

    private fun startForegroundService() {
        val intent = Intent(context, PlayerService::class.java)
        context.startForegroundService(intent)
    }

    // ---------- 通知栏 ----------

    fun updateNotification() {
        val service = serviceRef?.get() ?: return
        val state = _uiState.value
        val song = state.song ?: return
        val notification = buildNotification(state, song, null)
        service.startForeground(PlayerService.NOTIFICATION_ID, notification)
        loadNotificationArtwork(song) { bitmap ->
            if (bitmap != null) {
                try {
                    val updated = buildNotification(state, song, bitmap)
                    NotificationManagerCompat.from(context)
                        .notify(PlayerService.NOTIFICATION_ID, updated)
                } catch (_: SecurityException) {
                    // 未授予通知权限，忽略
                }
            }
        }
    }

    private fun buildNotification(
        state: PlayerUiState,
        song: Song,
        bitmap: Bitmap?,
    ): android.app.Notification {
        val contentIntent = PendingIntent.getActivity(
            context,
            0,
            Intent(context, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val prevIntent = PendingIntent.getService(
            context,
            1,
            Intent(context, PlayerService::class.java).setAction(PlayerService.ACTION_PREV),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val playPauseIntent = PendingIntent.getService(
            context,
            2,
            Intent(context, PlayerService::class.java).setAction(PlayerService.ACTION_PLAY_PAUSE),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val nextIntent = PendingIntent.getService(
            context,
            3,
            Intent(context, PlayerService::class.java).setAction(PlayerService.ACTION_NEXT),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val stopIntent = PendingIntent.getService(
            context,
            4,
            Intent(context, PlayerService::class.java).setAction(PlayerService.ACTION_STOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        val builder = NotificationCompat.Builder(context, PlayerService.CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(song.name)
            .setContentText(song.artistName)
            .setContentIntent(contentIntent)
            .setOngoing(true)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setOnlyAlertOnce(true)
            .setDeleteIntent(stopIntent)
            .setStyle(
                androidx.media.app.NotificationCompat.MediaStyle()
                    .setShowActionsInCompactView(0, 1, 2),
            )
            .addAction(R.drawable.ic_notification, "上一首", prevIntent)
            .addAction(
                R.drawable.ic_notification,
                if (state.isPlaying) "暂停" else "播放",
                playPauseIntent,
            )
            .addAction(R.drawable.ic_notification, "下一首", nextIntent)
        if (bitmap != null) builder.setLargeIcon(bitmap)
        return builder.build()
    }

    private fun loadNotificationArtwork(song: Song, onLoaded: (Bitmap?) -> Unit) {
        val url = song.coverUrl ?: return onLoaded(null)
        scope.launch {
            val bitmap = try {
                val imageLoader = context.imageLoader
                val result = imageLoader.execute(
                    ImageRequest.Builder(context)
                        .data(url)
                        .allowHardware(false)
                        .build(),
                )
                (result.drawable as? android.graphics.drawable.BitmapDrawable)?.bitmap
            } catch (e: Exception) {
                null
            }
            onLoaded(bitmap)
        }
    }
}