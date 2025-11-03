package com.example.myapplication

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch

@Composable
fun AlbumTracksPage(album: String, controller: PlaybackController, onBack: () -> Unit) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val repo = remember { PlaylistRepository(context) }
    val nowRepo = remember { NowPlayingRepository(context) }
    val scope = rememberCoroutineScope()
    var tracks by remember { mutableStateOf<List<AudioTrack>>(emptyList()) }

    // 初始化 now-playing（从数据库恢复）
    LaunchedEffect(Unit) {
        val savedNow = nowRepo.loadNowPlaying()
        if (savedNow.isNotEmpty()) {
            controller.setPlaylist(savedNow)
        }
    }

    LaunchedEffect(album) {
        val list = repo.loadTracksByAlbum(album)
        tracks = list
    }

    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(text = album, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(text = "${tracks.size} 首", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Button(
                onClick = {
                    controller.setPlaylist(tracks)
                    if (tracks.isNotEmpty()) {
                        controller.play(tracks.first())
                    }
                },
                enabled = tracks.isNotEmpty()
            ) {
                Icon(Icons.Filled.PlayArrow, contentDescription = null)
                Spacer(Modifier.width(4.dp))
                Text(text = "播放全部")
            }
     
        }
        HorizontalDivider()

        

        if (tracks.isEmpty()) {
            Text(text = "该专辑暂无歌曲或未扫描", modifier = Modifier.padding(16.dp), color = MaterialTheme.colorScheme.onSurfaceVariant)
        } else {
            LazyColumn(contentPadding = PaddingValues(bottom = 24.dp)) {
                items(tracks) { t ->
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
                    HorizontalDivider()
                }
            }
        }
    }
}