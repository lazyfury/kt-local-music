package com.example.myapplication

import android.content.Context
import android.net.Uri
import androidx.room.Room
import com.example.myapplication.db.AppDatabase
import com.example.myapplication.db.DbAudioTrack
import com.example.myapplication.db.ArtistEntry
import com.example.myapplication.db.AlbumEntry
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import androidx.core.net.toUri

class PlaylistRepository(context: Context) {
    private val db: AppDatabase = Room.databaseBuilder(
        context.applicationContext,
        AppDatabase::class.java,
        "app-db"
    ).fallbackToDestructiveMigration().build()

    private val dao = db.playlistDao()

    suspend fun savePlaylist(tracks: List<AudioTrack>) = withContext(Dispatchers.IO) {
        val items = tracks.map {
            DbAudioTrack(
                id = it.id,
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

    suspend fun loadPlaylist(): List<AudioTrack> = withContext(Dispatchers.IO) {
        dao.getAll().map {
            AudioTrack(
                id = it.id,
                title = it.title,
                artist = it.artist,
                album = it.album,
                durationMs = it.durationMs,
                contentUri = it.contentUri.toUri(),
            )
        }
    }

    suspend fun loadAuthors(): List<ArtistEntry> = withContext(Dispatchers.IO) {
        dao.getArtists()
    }

    suspend fun searchAuthors(query: String): List<ArtistEntry> = withContext(Dispatchers.IO) {
        if (query.isBlank()) dao.getArtists() else dao.searchArtists(query)
    }

    suspend fun loadTracksByArtist(artist: String): List<AudioTrack> = withContext(Dispatchers.IO) {
        dao.getByArtist(artist).map {
            AudioTrack(
                id = it.id,
                title = it.title,
                artist = it.artist,
                album = it.album,
                durationMs = it.durationMs,
                contentUri = it.contentUri.toUri(),
            )
        }
    }

    suspend fun loadAlbums(): List<AlbumEntry> = withContext(Dispatchers.IO) {
        dao.getAlbums()
    }

    suspend fun searchAlbums(query: String): List<AlbumEntry> = withContext(Dispatchers.IO) {
        if (query.isBlank()) dao.getAlbums() else dao.searchAlbums(query)
    }

    suspend fun loadTracksByAlbum(album: String): List<AudioTrack> = withContext(Dispatchers.IO) {
        dao.getByAlbum(album).map {
            AudioTrack(
                id = it.id,
                title = it.title,
                artist = it.artist,
                album = it.album,
                durationMs = it.durationMs,
                contentUri = it.contentUri.toUri(),
            )
        }
    }
}