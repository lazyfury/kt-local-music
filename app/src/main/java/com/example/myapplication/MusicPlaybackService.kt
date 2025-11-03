package com.example.myapplication

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.media.MediaPlayer
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.media.app.NotificationCompat.MediaStyle
import android.net.Uri
import androidx.media.session.MediaButtonReceiver
import android.support.v4.media.MediaMetadataCompat
import android.support.v4.media.session.MediaSessionCompat
import android.support.v4.media.session.PlaybackStateCompat
import androidx.localbroadcastmanager.content.LocalBroadcastManager

class MusicPlaybackService : Service() {

    companion object {
        const val ACTION_SET_PLAYLIST = "com.example.myapplication.action.SET_PLAYLIST"
        const val ACTION_PLAY_TRACK_ID = "com.example.myapplication.action.PLAY_TRACK_ID"
        const val ACTION_PLAY = "com.example.myapplication.action.PLAY"
        const val ACTION_PAUSE = "com.example.myapplication.action.PAUSE"
        const val ACTION_NEXT = "com.example.myapplication.action.NEXT"
        const val ACTION_PREV = "com.example.myapplication.action.PREV"
        const val ACTION_STOP = "com.example.myapplication.action.STOP"
        const val ACTION_SEEK = "com.example.myapplication.action.SEEK"

        const val EXTRA_IDS = "extra_ids"
        const val EXTRA_TITLES = "extra_titles"
        const val EXTRA_ARTISTS = "extra_artists"
        const val EXTRA_ALBUMS = "extra_albums"
        const val EXTRA_URIS = "extra_uris"
        const val EXTRA_DURATIONS = "extra_durations"
        const val EXTRA_TRACK_ID = "extra_track_id"
        const val EXTRA_SEEK_TO = "extra_seek_to"

        // 状态广播相关常量
        const val BROADCAST_PLAYBACK_STATE = "com.example.myapplication.broadcast.PLAYBACK_STATE"
        const val EXTRA_IS_PLAYING = "extra_is_playing"
        const val EXTRA_CURRENT_TRACK_ID = "extra_current_track_id"
        const val EXTRA_CURRENT_POSITION = "extra_current_position"
        const val EXTRA_DURATION = "extra_duration"

        private const val NOTIF_CHANNEL_ID = "music_playback"
        private const val NOTIF_ID = 1001
    }

    private lateinit var mediaSession: MediaSessionCompat
    private var mediaPlayer: MediaPlayer = MediaPlayer()
    private var playlist: List<AudioTrack> = emptyList()
    private var currentIndex: Int = -1
    private var isForeground = false

    override fun onCreate() {
        super.onCreate()
        mediaSession = MediaSessionCompat(this, "MusicPlaybackSession").apply {
            setCallback(object : MediaSessionCompat.Callback() {
                override fun onPlay() { handlePlay() }
                override fun onPause() { handlePause() }
                override fun onSkipToNext() { handleNext() }
                override fun onSkipToPrevious() { handlePrev() }
            })
            isActive = true
        }
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // 处理来自系统或通知的媒体按钮
        intent?.let { MediaButtonReceiver.handleIntent(mediaSession, it) }
        when (intent?.action) {
            ACTION_SET_PLAYLIST -> {
                val ids = intent.getLongArrayExtra(EXTRA_IDS) ?: LongArray(0)
                val titles = intent.getStringArrayExtra(EXTRA_TITLES) ?: emptyArray()
                val artists = intent.getStringArrayExtra(EXTRA_ARTISTS) ?: emptyArray()
                val albums = intent.getStringArrayExtra(EXTRA_ALBUMS) ?: emptyArray()
                val uris = intent.getStringArrayExtra(EXTRA_URIS) ?: emptyArray()
                val durations = intent.getLongArrayExtra(EXTRA_DURATIONS) ?: LongArray(0)
                val list = mutableListOf<AudioTrack>()
                val size = listOf(ids.size, titles.size, artists.size, albums.size, uris.size, durations.size).minOrNull() ?: 0
                for (i in 0 until size) {
                    list.add(
                        AudioTrack(
                            id = ids[i],
                            title = titles[i],
                            artist = artists[i],
                            album = albums[i],
                            durationMs = durations[i],
                            contentUri = Uri.parse(uris[i])
                        )
                    )
                }
                playlist = list
                if (currentIndex == -1 && playlist.isNotEmpty()) currentIndex = 0
                updateNotification()
            }
            ACTION_PLAY_TRACK_ID -> {
                val trackId = intent.getLongExtra(EXTRA_TRACK_ID, -1)
                val idx = playlist.indexOfFirst { it.id == trackId }
                if (idx >= 0) playAt(idx) else if (playlist.isNotEmpty()) playAt(0)
            }
            ACTION_PLAY -> handlePlay()
            ACTION_PAUSE -> handlePause()
            ACTION_NEXT -> handleNext()
            ACTION_PREV -> handlePrev()
            ACTION_STOP -> stopPlayback()
            ACTION_SEEK -> {
                val seekTo = intent.getLongExtra(EXTRA_SEEK_TO, -1L)
                if (seekTo >= 0) {
                    try {
                        // 记住 seek 前的播放状态，避免 seek 导致短暂暂停被误判
                        val wasPlaying = try { mediaPlayer.isPlaying } catch (_: Exception) { false }
                        mediaPlayer.seekTo(seekTo.toInt())
                        // 若之前在播放，保持开始状态
                        if (wasPlaying) {
                            try { mediaPlayer.start() } catch (_: Exception) {}
                        }
                        updatePlaybackState(wasPlaying)
                        updateNotification()
                    } catch (_: Exception) {}
                }
            }
        }
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        try { mediaPlayer.release() } catch (_: Exception) {}
        mediaSession.release()
        super.onDestroy()
    }

    private fun playAt(index: Int) {
        if (index !in playlist.indices) return
        currentIndex = index
        val track = playlist[index]
        resetPlayer()
        mediaPlayer.setDataSource(this, track.contentUri)
        mediaPlayer.setOnPreparedListener { mp ->
            mp.start()
            updateSessionMetadata(track)
            updatePlaybackState(isPlaying = true)
            updateNotification()
            startForegroundIfNeeded()
        }
        mediaPlayer.setOnCompletionListener {
            val nextIdx = currentIndex + 1
            if (nextIdx in playlist.indices) {
                playAt(nextIdx)
            } else {
                updatePlaybackState(isPlaying = false)
                stopForegroundIfNeeded()
            }
        }
        mediaPlayer.prepareAsync()
    }

    private fun handlePlay() {
        if (!mediaPlayer.isPlaying) {
            try { mediaPlayer.start(); updatePlaybackState(true); startForegroundIfNeeded(); updateNotification() } catch (_: Exception) {}
        }
    }

    private fun handlePause() {
        if (mediaPlayer.isPlaying) {
            try { mediaPlayer.pause(); updatePlaybackState(false); updateNotification() } catch (_: Exception) {}
        }
    }

    private fun handleNext() {
        // 默认行为：按播放列表顺序下一首
        playAt(currentIndex + 1)
    }
    private fun handlePrev() { playAt(currentIndex - 1) }

    private fun stopPlayback() {
        try { mediaPlayer.stop() } catch (_: Exception) {}
        updatePlaybackState(false)
        stopForegroundIfNeeded()
        stopSelf()
    }

    private fun resetPlayer() {
        try { mediaPlayer.reset() } catch (_: Exception) { mediaPlayer = MediaPlayer() }
    }

    private fun updateSessionMetadata(track: AudioTrack) {
        val metadata = MediaMetadataCompat.Builder()
            .putString(MediaMetadataCompat.METADATA_KEY_TITLE, track.title)
            .putString(MediaMetadataCompat.METADATA_KEY_ARTIST, track.artist)
            .putString(MediaMetadataCompat.METADATA_KEY_ALBUM, track.album)
            .putLong(MediaMetadataCompat.METADATA_KEY_DURATION, track.durationMs)
            .build()
        mediaSession.setMetadata(metadata)
    }

    private fun updatePlaybackState(isPlaying: Boolean) {
        val stateBuilder = PlaybackStateCompat.Builder()
            .setActions(
                PlaybackStateCompat.ACTION_PLAY or
                    PlaybackStateCompat.ACTION_PAUSE or
                    PlaybackStateCompat.ACTION_SKIP_TO_NEXT or
                    PlaybackStateCompat.ACTION_SKIP_TO_PREVIOUS or
                    PlaybackStateCompat.ACTION_STOP
            )
        val pos = try { mediaPlayer.currentPosition.toLong() } catch (_: Exception) { 0L }
        stateBuilder.setState(
            if (isPlaying) PlaybackStateCompat.STATE_PLAYING else PlaybackStateCompat.STATE_PAUSED,
            pos,
            1.0f
        )
        mediaSession.setPlaybackState(stateBuilder.build())
        
        // 广播状态变化给PlaybackController
        broadcastPlaybackState(isPlaying, pos)
    }

    private fun broadcastPlaybackState(isPlaying: Boolean, position: Long) {
        val currentTrack = playlist.getOrNull(currentIndex)
        val intent = Intent(BROADCAST_PLAYBACK_STATE).apply {
            putExtra(EXTRA_IS_PLAYING, isPlaying)
            putExtra(EXTRA_CURRENT_TRACK_ID, currentTrack?.id ?: -1L)
            putExtra(EXTRA_CURRENT_POSITION, position)
            putExtra(EXTRA_DURATION, currentTrack?.durationMs ?: 0L)
        }
        LocalBroadcastManager.getInstance(this).sendBroadcast(intent)
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                NOTIF_CHANNEL_ID,
                "音乐播放",
                NotificationManager.IMPORTANCE_LOW
            )
            val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            nm.createNotificationChannel(channel)
        }
    }

    private fun buildNotification(): Notification {
        val context = this
        val title = playlist.getOrNull(currentIndex)?.title ?: "正在播放"
        val artist = playlist.getOrNull(currentIndex)?.artist ?: ""

        val playIntent = Intent(context, MusicPlaybackService::class.java).setAction(ACTION_PLAY)
        val pauseIntent = Intent(context, MusicPlaybackService::class.java).setAction(ACTION_PAUSE)
        val nextIntent = Intent(context, MusicPlaybackService::class.java).setAction(ACTION_NEXT)
        val prevIntent = Intent(context, MusicPlaybackService::class.java).setAction(ACTION_PREV)
        val stopIntent = Intent(context, MusicPlaybackService::class.java).setAction(ACTION_STOP)

        val playPending = PendingIntent.getService(context, 1, playIntent, PendingIntent.FLAG_UPDATE_CURRENT or pendingMutability())
        val pausePending = PendingIntent.getService(context, 2, pauseIntent, PendingIntent.FLAG_UPDATE_CURRENT or pendingMutability())
        val nextPending = PendingIntent.getService(context, 3, nextIntent, PendingIntent.FLAG_UPDATE_CURRENT or pendingMutability())
        val prevPending = PendingIntent.getService(context, 4, prevIntent, PendingIntent.FLAG_UPDATE_CURRENT or pendingMutability())
        val stopPending = PendingIntent.getService(context, 5, stopIntent, PendingIntent.FLAG_UPDATE_CURRENT or pendingMutability())

        // 点击通知主体返回到App（打开MainActivity）
        val contentIntent = Intent(context, MainActivity::class.java).apply {
            // 前台已有Activity时仅将其前置，不重启/不新建
            addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        }
        val contentPending = PendingIntent.getActivity(context, 100, contentIntent, PendingIntent.FLAG_UPDATE_CURRENT or pendingMutability())

        val builder = NotificationCompat.Builder(context, NOTIF_CHANNEL_ID)
            .setContentTitle(title)
            .setContentText(artist)
            .setSmallIcon(android.R.drawable.ic_media_play)
            .setStyle(MediaStyle().setMediaSession(mediaSession.sessionToken))
            .setContentIntent(contentPending)
            .addAction(NotificationCompat.Action(android.R.drawable.ic_media_previous, "上一首", prevPending))
            .addAction(NotificationCompat.Action(android.R.drawable.ic_media_pause, "暂停", pausePending))
            .addAction(NotificationCompat.Action(android.R.drawable.ic_media_play, "播放", playPending))
            .addAction(NotificationCompat.Action(android.R.drawable.ic_media_next, "下一首", nextPending))
            .addAction(NotificationCompat.Action(android.R.drawable.ic_menu_close_clear_cancel, "停止", stopPending))
            .setOngoing(mediaPlayer.isPlaying)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)

        return builder.build()
    }

    private fun updateNotification() {
        val notification = buildNotification()
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.notify(NOTIF_ID, notification)
    }

    private fun startForegroundIfNeeded() {
        if (!isForeground) {
            isForeground = true
            startForeground(NOTIF_ID, buildNotification())
        }
    }

    private fun stopForegroundIfNeeded() {
        if (isForeground) {
            isForeground = false
            stopForeground(STOP_FOREGROUND_DETACH)
        }
    }

    private fun pendingMutability(): Int {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) PendingIntent.FLAG_MUTABLE else 0
    }
}