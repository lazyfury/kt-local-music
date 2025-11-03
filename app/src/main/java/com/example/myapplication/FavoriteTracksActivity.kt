package com.example.myapplication

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Divider
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Button
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.myapplication.ui.theme.MyApplicationTheme
import kotlinx.coroutines.launch

class FavoriteTracksActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val listName = intent.getStringExtra("favorite_list_name") ?: ""
        setContent {
            MyApplicationTheme {
                val context = LocalContext.current
                val controller = remember { PlaybackController(context) }

                FavoriteTracksPage(listName = listName, controller = controller) { finish() }
            }
        }
    }
}

@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
private fun FavoriteTracksPage(listName: String, controller: PlaybackController, onBack: () -> Unit) {
    val context = LocalContext.current
    val manager = remember { FavoriteManager(context) }
    val repo = remember { PlaylistRepository(context) }
    val nowRepo = remember { NowPlayingRepository(context) }
    val scope = rememberCoroutineScope()

    // 初始化 now-playing（从数据库恢复）
    LaunchedEffect(Unit) {
        val savedNow = nowRepo.loadNowPlaying()
        if (savedNow.isNotEmpty()) {
            controller.setPlaylist(savedNow)
        }
    }

    var showPlayer by remember { mutableStateOf(false) }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var allTracks by remember { mutableStateOf<List<AudioTrack>>(emptyList()) }
    var listTracks by remember { mutableStateOf<List<AudioTrack>>(emptyList()) }

    LaunchedEffect(Unit) {
        allTracks = repo.loadPlaylist()
    }

    LaunchedEffect(listName, allTracks) {
        val ids = manager.getListTrackIds(listName)
        listTracks = allTracks.filter { it.id in ids }
    }

    if (showPlayer) {
        ModalBottomSheet(onDismissRequest = { showPlayer = false }, sheetState = sheetState) {
            Player(controller)
        }
    }

    Scaffold(
        floatingActionButton = {
            FloatingActionButton(onClick = { showPlayer = true }) {
                Icon(Icons.Default.PlayArrow, contentDescription = "打开播放器")
            }
        }
    ) { innerPadding ->
        Column(modifier = Modifier.padding(innerPadding).padding(16.dp)) {
            Row(modifier = Modifier.fillMaxWidth()) {
                IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, contentDescription = "返回") }
                Column(modifier = Modifier
                    .weight(1f)
                    .padding(start = 8.dp)) {
                    Text(text = listName, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(text = "${listTracks.size} 首", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Button(
                    onClick = {
                        controller.setPlaylist(listTracks)
                        if (listTracks.isNotEmpty()) {
                            controller.play(listTracks.first())
                        }
                    },
                    enabled = listTracks.isNotEmpty()
                ) { Text(text = "播放全部") }
                Spacer(modifier = Modifier.width(8.dp))
                Button(
                    onClick = { controller.addToPlaylist(listTracks) },
                    enabled = listTracks.isNotEmpty()
                ) { Text(text = "添加到播放列表（全部）") }
            }

            Spacer(modifier = Modifier.height(8.dp))

            if (listTracks.isEmpty()) {
                Text(text = "该列表暂无歌曲或数据库未同步", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            } else {
                LazyColumn(contentPadding = PaddingValues(bottom = 24.dp)) {
                    items(listTracks) { t ->
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
}