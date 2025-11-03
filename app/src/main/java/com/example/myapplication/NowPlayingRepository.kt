package com.example.myapplication

import android.content.Context
import android.net.Uri
import androidx.room.Room
import com.example.myapplication.db.AppDatabase
import com.example.myapplication.db.DbNowPlayingItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class NowPlayingRepository(context: Context) {
    private val db: AppDatabase = Room.databaseBuilder(
        context.applicationContext,
        AppDatabase::class.java,
        "app-db"
    ).fallbackToDestructiveMigration().build()

    private val dao = db.nowPlayingDao()

    suspend fun saveNowPlaying(tracks: List<AudioTrack>) = withContext(Dispatchers.IO) {
        val items = tracks.mapIndexed { index, it ->
            DbNowPlayingItem(
                orderIndex = index,
                trackId = it.id,
                title = it.title,
                artist = it.artist,
                album = it.album,
                durationMs = it.durationMs,
                contentUri = it.contentUri.toString(),
            )
        }
        dao.clear()
        if (items.isNotEmpty()) dao.insertAll(items)
    }

    suspend fun loadNowPlaying(): List<AudioTrack> = withContext(Dispatchers.IO) {
        dao.getAll().map { item ->
            AudioTrack(
                id = item.trackId,
                title = item.title,
                artist = item.artist,
                album = item.album,
                durationMs = item.durationMs,
                contentUri = Uri.parse(item.contentUri),
            )
        }
    }

    suspend fun isInNowPlaying(trackId: Long): Boolean = withContext(Dispatchers.IO) {
        dao.exists(trackId)
    }

    suspend fun removeFromNowPlaying(trackId: Long) = withContext(Dispatchers.IO) {
        // Remove and do not reindex here; callers should persist full list if needed
        dao.removeByTrackId(trackId)
    }
}