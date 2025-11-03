package com.example.myapplication.db

import androidx.room.ColumnInfo

data class AlbumEntry(
    @ColumnInfo(name = "album") val album: String,
    @ColumnInfo(name = "count") val count: Int,
)