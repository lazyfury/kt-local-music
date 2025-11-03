package com.example.myapplication.db

import androidx.room.Database
import androidx.room.RoomDatabase

@Database(
    entities = [DbAudioTrack::class, DbNowPlayingItem::class],
    version = 2,
    exportSchema = false,
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun playlistDao(): PlaylistDao
    abstract fun nowPlayingDao(): NowPlayingDao
}