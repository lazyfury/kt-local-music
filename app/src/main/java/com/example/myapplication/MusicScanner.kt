package com.example.myapplication

import android.content.ContentUris
import android.content.Context
import android.net.Uri
import android.provider.MediaStore

data class AudioTrack(
    val id: Long,
    val title: String,
    val artist: String,
    val album: String,
    val durationMs: Long,
    val contentUri: Uri,
)

/**
 * 扫描本地媒体库中的音频文件。
 * 返回简易的 [AudioTrack] 列表，已过滤极短音频（< 5s）。
 */
fun scanLocalAudio(context: Context): List<AudioTrack> {
    val resolver = context.contentResolver
    val collection = MediaStore.Audio.Media.EXTERNAL_CONTENT_URI

    val projection = arrayOf(
        MediaStore.Audio.Media._ID,
        MediaStore.Audio.Media.TITLE,
        MediaStore.Audio.Media.ARTIST,
        MediaStore.Audio.Media.ALBUM,
        MediaStore.Audio.Media.DURATION,
    )

    val sortOrder = MediaStore.Audio.Media.TITLE + " ASC"

    val tracks = mutableListOf<AudioTrack>()

    resolver.query(collection, projection, null, null, sortOrder)?.use { cursor ->
        val idCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media._ID)
        val titleCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.TITLE)
        val artistCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ARTIST)
        val albumCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM)
        val durationCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DURATION)

        while (cursor.moveToNext()) {
            val id = cursor.getLong(idCol)
            val title = cursor.getString(titleCol) ?: "未知标题"
            val artist = cursor.getString(artistCol) ?: "未知演唱者"
            val album = cursor.getString(albumCol) ?: "未知专辑"
            val duration = cursor.getLong(durationCol)

            // 过滤过短的音频（如提示音）
            if (duration < 5_000) continue

            val contentUri = ContentUris.withAppendedId(collection, id)
            tracks.add(
                AudioTrack(
                    id = id,
                    title = title,
                    artist = artist,
                    album = album,
                    durationMs = duration,
                    contentUri = contentUri,
                )
            )
        }
    }

    return tracks
}