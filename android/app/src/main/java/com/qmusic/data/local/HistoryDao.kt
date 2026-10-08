package com.qmusic.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.qmusic.data.local.entity.History
import kotlinx.coroutines.flow.Flow

@Dao
interface HistoryDao {

    @Query("SELECT * FROM history ORDER BY playedAt DESC")
    fun observeAll(): Flow<List<History>>

    @Query("SELECT * FROM history WHERE songId = :songId LIMIT 1")
    suspend fun findById(songId: Long): History?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(history: History)
}