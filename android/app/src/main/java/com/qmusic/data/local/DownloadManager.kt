package com.qmusic.data.local

import android.content.Context
import com.qmusic.data.model.Song
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.util.concurrent.ConcurrentHashMap

/**
 * 歌曲下载管理：下载到应用私有目录（无需存储权限），元数据持久化到 SharedPreferences。
 * 仅供个人学习使用。
 */
object DownloadManager {
    private const val PREFS = "qmusic_downloads"
    private const val KEY_ITEMS = "items"
    private val json = Json { ignoreUnknownKeys = true }
    private val downloadingIds = ConcurrentHashMap.newKeySet<Long>()

    fun isDownloading(songId: Long): Boolean = songId in downloadingIds

    fun isDownloaded(context: Context, songId: Long): Boolean =
        getDownloadedSongs(context).any { it.id == songId }

    fun getDownloadedSongs(context: Context): List<Song> {
        val raw = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY_ITEMS, null)
            ?: return emptyList()
        return try {
            json.decodeFromString<List<Song>>(raw)
        } catch (e: Exception) {
            emptyList()
        }
    }

    fun remove(context: Context, songId: Long) {
        val songs = getDownloadedSongs(context).filterNot { it.id == songId }
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putString(KEY_ITEMS, json.encodeToString(songs))
            .apply()
    }

    /** 下载歌曲音频到 filesDir/music/{songId}，成功后保存元数据，返回是否成功。 */
    suspend fun download(context: Context, song: Song, url: String): Boolean = withContext(Dispatchers.IO) {
        if (!downloadingIds.add(song.id)) return@withContext false
        try {
            val client = OkHttpClient()
            val request = Request.Builder().url(url).build()
            val target = client.newCall(request).execute().use { resp ->
                if (!resp.isSuccessful) return@withContext false
                val dir = File(context.filesDir, "music").apply { mkdirs() }
                val file = File(dir, song.id.toString())
                resp.body?.byteStream()?.use { input ->
                    file.outputStream().use { output -> input.copyTo(output) }
                }
                file
            }
            val songs = getDownloadedSongs(context).filterNot { it.id == song.id } +
                song.copy(localPath = target.absolutePath)
            context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
                .putString(KEY_ITEMS, json.encodeToString(songs))
                .apply()
            true
        } catch (e: Exception) {
            false
        } finally {
            downloadingIds.remove(song.id)
        }
    }
}