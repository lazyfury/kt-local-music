package com.example.myapplication.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface PlaylistDao {
    @Query("DELETE FROM playlist")
    suspend fun clear()

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(items: List<DbAudioTrack>)

    @Query("SELECT * FROM playlist ORDER BY title ASC")
    suspend fun getAll(): List<DbAudioTrack>

    // 作者聚合
    @Query("SELECT artist AS artist, COUNT(*) AS count FROM playlist GROUP BY artist ORDER BY artist ASC")
    suspend fun getArtists(): List<ArtistEntry>

    // 按作者搜索（不区分大小写）
    @Query("SELECT artist AS artist, COUNT(*) AS count FROM playlist WHERE artist LIKE '%' || :query || '%' COLLATE NOCASE GROUP BY artist ORDER BY artist ASC")
    suspend fun searchArtists(query: String): List<ArtistEntry>

    // 按作者获取歌曲列表（不区分大小写）
    @Query("SELECT * FROM playlist WHERE artist = :artist COLLATE NOCASE ORDER BY title ASC")
    suspend fun getByArtist(artist: String): List<DbAudioTrack>

    // 专辑聚合
    @Query("SELECT album AS album, COUNT(*) AS count FROM playlist GROUP BY album ORDER BY album ASC")
    suspend fun getAlbums(): List<com.example.myapplication.db.AlbumEntry>

    // 按专辑搜索（不区分大小写）
    @Query("SELECT album AS album, COUNT(*) AS count FROM playlist WHERE album LIKE '%' || :query || '%' COLLATE NOCASE GROUP BY album ORDER BY album ASC")
    suspend fun searchAlbums(query: String): List<com.example.myapplication.db.AlbumEntry>

    // 按专辑获取歌曲列表（不区分大小写）
    @Query("SELECT * FROM playlist WHERE album = :album COLLATE NOCASE ORDER BY title ASC")
    suspend fun getByAlbum(album: String): List<DbAudioTrack>
}