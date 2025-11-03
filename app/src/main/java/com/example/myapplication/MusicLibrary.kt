package com.example.myapplication

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.clickable
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.Divider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.text.style.TextOverflow
import android.content.Context
import kotlinx.coroutines.launch

@Composable
fun MusicLibrary(controller: PlaybackController) {
    val context = LocalContext.current
    val repo = remember { PlaylistRepository(context) }
    val nowRepo = remember { NowPlayingRepository(context) }
    val scope = rememberCoroutineScope()
    var tracks by remember { mutableStateOf<List<AudioTrack>>(emptyList()) }
    var artistQuery by remember { mutableStateOf("") }
    var permissionGranted by remember { mutableStateOf(false) }

    // 自动扫描间隔（毫秒）。例如：6 小时
    val autoScanIntervalMs = 6 * 60 * 60 * 1000L
    val prefs = remember { context.getSharedPreferences("music_prefs", Context.MODE_PRIVATE) }
    fun getLastScanTime(): Long = prefs.getLong("last_scan_time", 0L)
    fun setLastScanTime(ts: Long) { prefs.edit().putLong("last_scan_time", ts).apply() }

    val permission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        Manifest.permission.READ_MEDIA_AUDIO
    } else {
        Manifest.permission.READ_EXTERNAL_STORAGE
    }

    fun checkPermission(): Boolean {
        val state = ContextCompat.checkSelfPermission(context, permission)
        return state == PackageManager.PERMISSION_GRANTED
    }

    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        permissionGranted = granted
        if (granted) {
            tracks = scanLocalAudio(context)
            scope.launch { repo.savePlaylist(tracks) }
            setLastScanTime(System.currentTimeMillis())
        }
    }

    // 初始从数据库加载已保存的播放列表
    LaunchedEffect(Unit) {
        // 初始化 now-playing（从数据库持久化状态恢复，而非默认填充）
        val savedNow = nowRepo.loadNowPlaying()
        if (savedNow.isNotEmpty()) {
            controller.setPlaylist(savedNow)
        }

        val saved = repo.loadPlaylist()
        if (saved.isNotEmpty()) {
            tracks = saved
        }
        // 进入页面时：若已授权并且距上次扫描超过设定间隔，则自动扫描一次
        val now = System.currentTimeMillis()
        val last = getLastScanTime()
        val shouldAutoScan = (now - last) >= autoScanIntervalMs
        if (shouldAutoScan && checkPermission()) {
            val newTracks = scanLocalAudio(context)
            tracks = newTracks
            scope.launch { repo.savePlaylist(newTracks) }
            setLastScanTime(now)
        }
    }

    // 过滤后的列表：按标题/专辑/作者关键词（不区分大小写）
    val filteredTracks = remember(artistQuery, tracks) {
        if (artistQuery.isBlank()) tracks
        else tracks.filter {
            it.title.contains(artistQuery, ignoreCase = true) ||
            it.album.contains(artistQuery, ignoreCase = true) ||
            it.artist.contains(artistQuery, ignoreCase = true)
        }
    }

    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(text = "本地音乐库", style = MaterialTheme.typography.titleMedium)
            Text(text = "${tracks.size} 首", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }

        

        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Button(onClick = {
            if (checkPermission()) {
                    permissionGranted = true
                    tracks = scanLocalAudio(context)
                    scope.launch { repo.savePlaylist(tracks) }
                    setLastScanTime(System.currentTimeMillis())
                } else {
                    launcher.launch(permission)
                }
            }) {
                Text(text = "扫描本地音乐")
            }

            Button(onClick = {
                val playList = filteredTracks
                controller.setPlaylist(playList)
                if (playList.isNotEmpty()) {
                    controller.play(playList.first())
                }
            }) { Text(text = "播放全部") }
        }

        

        // 搜索输入框（标题/专辑/作者）
        androidx.compose.material3.OutlinedTextField(
            value = artistQuery,
            onValueChange = { artistQuery = it },
            label = { Text(text = "搜索标题/专辑/作者") },
            placeholder = { Text(text = "输入关键词（标题/专辑/作者）") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )


        if (tracks.isEmpty()) {
            Text(
                text = "暂无音乐，点击上方按钮扫描",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        } else if (artistQuery.isNotBlank() && filteredTracks.isEmpty()) {
            Text(
                text = "未找到匹配结果",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        } else {
            LazyColumn(contentPadding = PaddingValues(bottom = 24.dp)) {
                items(filteredTracks.take(50)) { t ->
                    val inNow = remember(controller.playlistVersion, t.id) { controller.getPlaylist().any { it.id == t.id } }
                    TrackListItem(
                        track = t,
                        onClick = {
                            controller.play(t)
                            scope.launch { nowRepo.saveNowPlaying(controller.getPlaylist()) }
                        },
                        onAddToPlaylist = { at ->
                            controller.addToPlaylist(at)
                            scope.launch { nowRepo.saveNowPlaying(controller.getPlaylist()) }
                        },
                        isInNowPlaying = inNow,
                        onRemoveFromPlaylist = { at ->
                            controller.removeFromPlaylist(at)
                            scope.launch { nowRepo.saveNowPlaying(controller.getPlaylist()) }
                        }
                    )
                    Divider()
                }
            }
        }

    }
}