package com.example.myapplication

import android.content.Context
import android.media.MediaPlayer
import android.content.Intent
import android.os.SystemClock
import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import android.content.BroadcastReceiver
import android.content.IntentFilter
import androidx.localbroadcastmanager.content.LocalBroadcastManager

class PlaybackController(private val context: Context) {
    // 本地 MediaPlayer 不再直接驱动播放，改为通过前台服务控制系统播放
    private var mediaPlayer: MediaPlayer = MediaPlayer()

    var isPlaying by mutableStateOf(false)
        private set
    var progress by mutableStateOf(0f)
        private set
    var durationMs by mutableStateOf(0)
        private set
    var currentTrack: AudioTrack? by mutableStateOf(null)
        private set

    private var playlist: List<AudioTrack> = emptyList()
    private var currentIndex: Int = -1
    var shuffleEnabled by mutableStateOf(false)
        private set
    var playlistVersion by mutableStateOf(0)
        private set

    private var positionMs: Long = 0L
    private var lastTickMs: Long = 0L
    private var isUserSeeking: Boolean = false

    // 状态变化监听器
    private var onStateChangeListener: ((PlaybackState) -> Unit)? = null

    // 播放状态数据类
    data class PlaybackState(
        val isPlaying: Boolean,
        val currentTrack: AudioTrack?,
        val progress: Float,
        val durationMs: Int,
        val positionMs: Long
    )

    // 广播接收器用于接收服务状态变化
    private val playbackStateReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            intent?.let { handlePlaybackStateUpdate(it) }
        }
    }

    init {
        // 注册广播接收器
        val filter = IntentFilter(MusicPlaybackService.BROADCAST_PLAYBACK_STATE)
        LocalBroadcastManager.getInstance(context).registerReceiver(playbackStateReceiver, filter)
        // 初始化播放模式设置
        shuffleEnabled = PlaybackSettings.isShuffleEnabled(context)
    }

    // 设置状态变化监听器
    fun setOnStateChangeListener(listener: (PlaybackState) -> Unit) {
        onStateChangeListener = listener
    }

    // 移除状态变化监听器
    fun removeOnStateChangeListener() {
        onStateChangeListener = null
    }

    // 通知状态变化
    private fun notifyStateChange() {
        onStateChangeListener?.invoke(
            PlaybackState(
                isPlaying = isPlaying,
                currentTrack = currentTrack,
                progress = progress,
                durationMs = durationMs,
                positionMs = positionMs
            )
        )
    }

    fun setPlaylist(list: List<AudioTrack>) {
        playlist = list
        // 若当前曲目不在新列表中，重置索引
        currentTrack?.let { ct ->
            currentIndex = playlist.indexOfFirst { it.id == ct.id }
        }
        if (currentIndex == -1 && playlist.isNotEmpty()) {
            currentIndex = 0
        }
        // 同步到前台服务
        sendPlaylistToService()
        playlistVersion++
    }

    fun addToPlaylist(tracks: List<AudioTrack>) {
        if (tracks.isEmpty()) return
        val existing = playlist.map { it.id }.toSet()
        val append = tracks.filter { it.id !in existing }
        if (append.isEmpty()) return
        playlist = playlist + append
        sendPlaylistToService()
        playlistVersion++
    }

    fun addToPlaylist(track: AudioTrack) {
        if (playlist.any { it.id == track.id }) return
        playlist = playlist + track
        sendPlaylistToService()
        playlistVersion++
    }

    fun clearPlaylist() {
        playlist = emptyList()
        currentIndex = -1
        currentTrack = null
        durationMs = 0
        progress = 0f
        positionMs = 0L
        // 同步空列表到服务并停止播放，确保服务端状态一致
        try {
            val intentSetEmpty = Intent(context, MusicPlaybackService::class.java).apply {
                action = MusicPlaybackService.ACTION_SET_PLAYLIST
                putExtra(MusicPlaybackService.EXTRA_IDS, LongArray(0))
                putExtra(MusicPlaybackService.EXTRA_TITLES, arrayOf<String>())
                putExtra(MusicPlaybackService.EXTRA_ARTISTS, arrayOf<String>())
                putExtra(MusicPlaybackService.EXTRA_ALBUMS, arrayOf<String>())
                putExtra(MusicPlaybackService.EXTRA_URIS, arrayOf<String>())
                putExtra(MusicPlaybackService.EXTRA_DURATIONS, LongArray(0))
            }
            context.startService(intentSetEmpty)
            val intentStop = Intent(context, MusicPlaybackService::class.java).apply {
                action = MusicPlaybackService.ACTION_STOP
            }
            context.startService(intentStop)
        } catch (_: Exception) {}
        playlistVersion++
        notifyStateChange()
    }

    fun shufflePlaylist() {
        if (playlist.isEmpty()) return
        val current = currentTrack
        playlist = playlist.shuffled()
        shuffleEnabled = true
        PlaybackSettings.setShuffleEnabled(context, true)
        // 维持当前曲目，重新对齐索引
        currentIndex = current?.let { c -> playlist.indexOfFirst { it.id == c.id } } ?: 0
        if (currentIndex < 0) currentIndex = 0
        sendPlaylistToService()
        // 通知服务按当前曲目 id 播放，以保持一致
        val keepId = playlist.getOrNull(currentIndex)?.id ?: return
        val intent = Intent(context, MusicPlaybackService::class.java).apply {
            action = MusicPlaybackService.ACTION_PLAY_TRACK_ID
            putExtra(MusicPlaybackService.EXTRA_TRACK_ID, keepId)
        }
        context.startService(intent)
        playlistVersion++
    }

    fun getPlaylist(): List<AudioTrack> = playlist

    private fun playTrackAt(index: Int) {
        if (index !in playlist.indices) return
        currentIndex = index
        val track = playlist[index]
        // 预设当前曲目信息，实际状态将通过广播同步
        currentTrack = track
        durationMs = track.durationMs.toInt()
        // 重置进度计时
        positionMs = 0L
        lastTickMs = SystemClock.elapsedRealtime()

        // 将完整播放列表推送给服务
        sendPlaylistToService()
        // 指定按 track id 播放
        val intent = Intent(context, MusicPlaybackService::class.java).apply {
            action = MusicPlaybackService.ACTION_PLAY_TRACK_ID
            putExtra(MusicPlaybackService.EXTRA_TRACK_ID, track.id)
        }
        context.startService(intent)
    }

    fun play(track: AudioTrack) {
        // 点击单曲：将该曲目插入到播放列表首位并播放，不清空原列表
        val idx = playlist.indexOfFirst { it.id == track.id }
        playlist = if (idx >= 0) {
            val newList = playlist.toMutableList()
            newList.removeAt(idx)
            listOf(track) + newList
        } else {
            listOf(track) + playlist
        }
        playlistVersion++
        playTrackAt(0)
    }

    /**
     * 若曲目已存在于当前播放列表中，则保持列表不变，仅跳转到该曲目播放；
     * 若不存在，则沿用默认的 play() 逻辑（插入到首位并播放）。
     */
    fun playIfInPlaylist(track: AudioTrack) {
        val idx = playlist.indexOfFirst { it.id == track.id }
        if (idx >= 0) {
            playTrackAt(idx)
        } else {
            play(track)
        }
    }

    fun togglePlayPause() {
        if (isPlaying) {
            // 暂停播放
            val intent = Intent(context, MusicPlaybackService::class.java).apply { action = MusicPlaybackService.ACTION_PAUSE }
            context.startService(intent)
        } else {
            // 若尚未选择曲目但有播放列表，自动选择第一首并播放
            if (currentTrack == null && playlist.isNotEmpty()) {
                playTrackAt(if (currentIndex in playlist.indices) currentIndex else 0)
            } else {
                // 已有当前曲目，直接发起播放
                val intent = Intent(context, MusicPlaybackService::class.java).apply { action = MusicPlaybackService.ACTION_PLAY }
                context.startService(intent)
            }
        }
    }

    fun setShuffleMode(enabled: Boolean) {
        shuffleEnabled = enabled
        PlaybackSettings.setShuffleEnabled(context, enabled)
        // 不改变当前播放，仅影响下一曲的选择策略
        notifyStateChange()
    }

    fun isShuffleEnabled(): Boolean = shuffleEnabled

    fun seekToFraction(fraction: Float) {
        if (durationMs > 0) {
            val seekToMs = (durationMs * fraction).toLong()
            positionMs = seekToMs
            val d = durationMs
            val p = positionMs.toInt().coerceAtMost(d)
            progress = if (d > 0) p.toFloat() / d else 0f

            // 发给服务进行真正的 seek
            val intent = Intent(context, MusicPlaybackService::class.java).apply {
                action = MusicPlaybackService.ACTION_SEEK
                putExtra(MusicPlaybackService.EXTRA_SEEK_TO, seekToMs)
            }
            context.startService(intent)

            // 立即通知本地状态变化，服务广播到达后会二次校准
            notifyStateChange()
        }
    }

    fun updateProgress() {
        if (isPlaying) {
            val now = SystemClock.elapsedRealtime()
            positionMs += (now - lastTickMs)
            lastTickMs = now
        }
        val d = durationMs
        val p = positionMs.toInt().coerceAtMost(d)
        progress = if (d > 0) p.toFloat() / d else 0f
        
        // 通知状态变化（仅在本地更新进度时）
        notifyStateChange()
    }

    fun next() {
        if (playlist.isEmpty()) return
        if (shuffleEnabled) {
            // 随机选择下一曲（避免选择到当前索引，如果列表>1）
            val size = playlist.size
            val nextIndex = if (size <= 1) currentIndex.coerceIn(playlist.indices) else {
                var idx: Int
                do {
                    idx = (0 until size).random()
                } while (idx == currentIndex && size > 1)
                idx
            }
            playTrackAt(nextIndex)
        } else {
            val nextIndex = currentIndex + 1
            if (nextIndex in playlist.indices) {
                playTrackAt(nextIndex)
            } else {
                // 通知服务尝试下一曲（例如循环或停止）
                val intent = Intent(context, MusicPlaybackService::class.java).apply { action = MusicPlaybackService.ACTION_NEXT }
                context.startService(intent)
            }
        }
    }

    fun removeFromPlaylist(track: AudioTrack) {
        removeFromPlaylistById(track.id)
    }

    fun removeFromPlaylistById(trackId: Long) {
        val idx = playlist.indexOfFirst { it.id == trackId }
        if (idx < 0) return
        val wasCurrent = currentTrack?.id == trackId
        val newList = playlist.filterNot { it.id == trackId }
        playlist = newList

        if (newList.isEmpty()) {
            // 与 clearPlaylist 保持一致：同步空列表并停止服务
            currentIndex = -1
            currentTrack = null
            durationMs = 0
            progress = 0f
            positionMs = 0L
            try {
                val intentSetEmpty = Intent(context, MusicPlaybackService::class.java).apply {
                    action = MusicPlaybackService.ACTION_SET_PLAYLIST
                    putExtra(MusicPlaybackService.EXTRA_IDS, LongArray(0))
                    putExtra(MusicPlaybackService.EXTRA_TITLES, arrayOf<String>())
                    putExtra(MusicPlaybackService.EXTRA_ARTISTS, arrayOf<String>())
                    putExtra(MusicPlaybackService.EXTRA_ALBUMS, arrayOf<String>())
                    putExtra(MusicPlaybackService.EXTRA_URIS, arrayOf<String>())
                    putExtra(MusicPlaybackService.EXTRA_DURATIONS, LongArray(0))
                }
                context.startService(intentSetEmpty)
                val intentStop = Intent(context, MusicPlaybackService::class.java).apply {
                    action = MusicPlaybackService.ACTION_STOP
                }
                context.startService(intentStop)
            } catch (_: Exception) {}
            playlistVersion++
            notifyStateChange()
            return
        }

        if (!wasCurrent) {
            // 若移除的在当前索引之前，索引左移
            if (idx >= 0 && idx < currentIndex) {
                currentIndex -= 1
            }
            // 重新对齐当前曲目索引
            currentTrack?.let { ct ->
                val newIdx = newList.indexOfFirst { it.id == ct.id }
                currentIndex = if (newIdx >= 0) newIdx else currentIndex.coerceIn(newList.indices)
            }
            // 同步新列表到服务，继续当前播放
            sendPlaylistToService()
            playlistVersion++
            notifyStateChange()
        } else {
            // 当前曲目被移除：选择“原索引”的下一首；若移除的是最后一首，则选最后一个
            var nextIndex = idx
            if (nextIndex >= newList.size) nextIndex = newList.size - 1
            currentIndex = nextIndex
            currentTrack = newList.getOrNull(nextIndex)

            // 同步列表到服务并按新当前曲目播放
            sendPlaylistToService()
            currentTrack?.let { ct ->
                val intent = Intent(context, MusicPlaybackService::class.java).apply {
                    action = MusicPlaybackService.ACTION_PLAY_TRACK_ID
                    putExtra(MusicPlaybackService.EXTRA_TRACK_ID, ct.id)
                }
                context.startService(intent)
            } ?: run {
                val intentStop = Intent(context, MusicPlaybackService::class.java).apply {
                    action = MusicPlaybackService.ACTION_STOP
                }
                context.startService(intentStop)
            }
            playlistVersion++
            notifyStateChange()
        }
    }

    fun prev() {
        val prevIndex = currentIndex - 1
        if (prevIndex in playlist.indices) {
            playTrackAt(prevIndex)
        } else {
            val intent = Intent(context, MusicPlaybackService::class.java).apply { action = MusicPlaybackService.ACTION_PREV }
            context.startService(intent)
        }
    }

    fun release() {
        try {
            mediaPlayer.release()
        } catch (_: Exception) {
        }
        // 取消注册广播接收器
        try {
            LocalBroadcastManager.getInstance(context).unregisterReceiver(playbackStateReceiver)
        } catch (_: Exception) {
        }
        // 停止前台服务
        val intent = Intent(context, MusicPlaybackService::class.java).apply { action = MusicPlaybackService.ACTION_STOP }
        context.startService(intent)
    }

    private fun resetPlayer() {
        try {
            mediaPlayer.reset()
        } catch (_: Exception) {
            mediaPlayer = MediaPlayer()
        }
        isPlaying = false
        progress = 0f
        durationMs = 0
    }

    private fun sendPlaylistToService() {
        if (playlist.isEmpty()) return
        val intent = Intent(context, MusicPlaybackService::class.java).apply {
            action = MusicPlaybackService.ACTION_SET_PLAYLIST
            putExtra(MusicPlaybackService.EXTRA_IDS, playlist.map { it.id }.toLongArray())
            putExtra(MusicPlaybackService.EXTRA_TITLES, playlist.map { it.title }.toTypedArray())
            putExtra(MusicPlaybackService.EXTRA_ARTISTS, playlist.map { it.artist }.toTypedArray())
            putExtra(MusicPlaybackService.EXTRA_ALBUMS, playlist.map { it.album }.toTypedArray())
            putExtra(MusicPlaybackService.EXTRA_DURATIONS, playlist.map { it.durationMs }.toLongArray())
            putExtra(MusicPlaybackService.EXTRA_URIS, playlist.map { it.contentUri.toString() }.toTypedArray())
        }
        context.startService(intent)
    }

    private fun handlePlaybackStateUpdate(intent: Intent) {
        val serviceIsPlaying = intent.getBooleanExtra(MusicPlaybackService.EXTRA_IS_PLAYING, false)
        val trackId = intent.getLongExtra(MusicPlaybackService.EXTRA_CURRENT_TRACK_ID, -1L)
        val position = intent.getLongExtra(MusicPlaybackService.EXTRA_CURRENT_POSITION, 0L)
        val duration = intent.getLongExtra(MusicPlaybackService.EXTRA_DURATION, 0L)

        // 同步播放状态
        isPlaying = serviceIsPlaying
        
        // 同步当前曲目
        if (trackId != -1L) {
            val track = playlist.find { it.id == trackId }
            if (track != null && track != currentTrack) {
                currentTrack = track
                currentIndex = playlist.indexOf(track)
            }
        }

        // 同步时长（无论曲目是否变化，都以服务端为准）
        if (duration > 0) {
            durationMs = duration.toInt()
        }

        // 同步播放进度（拖动进度条时跳过，以避免抖动覆盖本地值）
        if (duration > 0 && !isUserSeeking) {
            positionMs = position
            progress = position.toFloat() / duration.toFloat()
            lastTickMs = SystemClock.elapsedRealtime()
        }

        // 通知状态变化
        notifyStateChange()
    }

    fun setUserSeeking(seeking: Boolean) {
        isUserSeeking = seeking
    }
}