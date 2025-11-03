package com.example.myapplication

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.Divider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch

@Composable
fun AuthorTracksPage(
    author: String,
    controller: PlaybackController,
    onBack: () -> Unit,
) {
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

    LaunchedEffect(author) {
        val list = repo.loadTracksByArtist(author)
        tracks = list
    }

    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            IconButton(onClick = onBack) {
                Icon(Icons.Default.ArrowBack, contentDescription = "返回")
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(text = author, style = MaterialTheme.typography.titleMedium)
                Text(
                    text = "${tracks.size} 首",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Button(
                onClick = {
                    controller.setPlaylist(tracks)
                    if (tracks.isNotEmpty()) {
                        controller.play(tracks.first())
                    }
                },
                enabled = tracks.isNotEmpty()
            ) { Text(text = "播放全部") }

        }

        

        if (tracks.isEmpty()) {
            Text(
                text = "暂无该作者的歌曲或数据库未同步",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
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
                    Divider()
                }
            }
        }
    }
}