package com.example.myapplication.db

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "now_playing")
data class DbNowPlayingItem(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val orderIndex: Int,
    val trackId: Long,
    val title: String,
    val artist: String,
    val album: String,
    val durationMs: Long,
    val contentUri: String,
)