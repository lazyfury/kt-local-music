package com.example.myapplication.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface NowPlayingDao {
    @Query("DELETE FROM now_playing")
    suspend fun clear()

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(items: List<DbNowPlayingItem>)

    @Query("SELECT * FROM now_playing ORDER BY orderIndex ASC")
    suspend fun getAll(): List<DbNowPlayingItem>

    @Query("SELECT EXISTS(SELECT 1 FROM now_playing WHERE trackId = :trackId)")
    suspend fun exists(trackId: Long): Boolean

    @Query("DELETE FROM now_playing WHERE trackId = :trackId")
    suspend fun removeByTrackId(trackId: Long)
}