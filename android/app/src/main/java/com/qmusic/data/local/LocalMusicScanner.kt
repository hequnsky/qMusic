package com.qmusic.data.local

import android.content.Context
import android.os.Build
import android.provider.MediaStore
import com.qmusic.data.model.Album
import com.qmusic.data.model.Artist
import com.qmusic.data.model.Song
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** MediaStore 本地音频扫描器 */
object LocalMusicScanner {

    /** 本地歌曲 id 偏移，避免与在线歌曲 id（Room 主键）冲突 */
    const val LOCAL_ID_OFFSET = 1_000_000_000_000L

    suspend fun scan(context: Context): List<Song> = withContext(Dispatchers.IO) {
        val songs = mutableListOf<Song>()
        val projection = arrayOf(
            MediaStore.Audio.Media._ID,
            MediaStore.Audio.Media.TITLE,
            MediaStore.Audio.Media.ARTIST,
            MediaStore.Audio.Media.ALBUM,
            MediaStore.Audio.Media.ALBUM_ID,
            MediaStore.Audio.Media.DURATION,
            MediaStore.Audio.Media.DATA,
        )
        val collection = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            MediaStore.Audio.Media.getContentUri(MediaStore.VOLUME_EXTERNAL)
        } else {
            MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
        }
        val sortOrder = "${MediaStore.Audio.Media.TITLE} ASC"

        context.contentResolver.query(collection, projection, null, null, sortOrder)?.use { cursor ->
            val idCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media._ID)
            val titleCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.TITLE)
            val artistCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ARTIST)
            val albumCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM)
            val albumIdCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM_ID)
            val durationCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DURATION)
            val dataCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DATA)

            while (cursor.moveToNext()) {
                val mediaId = cursor.getLong(idCol)
                val path = cursor.getString(dataCol) ?: ""
                if (path.isBlank()) continue
                val albumId = cursor.getLong(albumIdCol)
                songs.add(
                    Song(
                        id = LOCAL_ID_OFFSET + mediaId,
                        name = cursor.getString(titleCol)?.takeIf { it.isNotBlank() } ?: "未知歌曲",
                        arList = listOf(Artist(name = cursor.getString(artistCol)?.takeIf { it.isNotBlank() } ?: "未知歌手")),
                        alAlbum = Album(
                            name = cursor.getString(albumCol)?.takeIf { it.isNotBlank() } ?: "未知专辑",
                            picUrl = "content://media/external/audio/albumart/$albumId",
                        ),
                        dt = cursor.getLong(durationCol),
                        localPath = path,
                    ),
                )
            }
        }
        songs
    }
}